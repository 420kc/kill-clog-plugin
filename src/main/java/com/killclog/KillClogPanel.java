package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class KillClogPanel extends PluginPanel
	implements LookupSession.Listener, ComparisonController.Listener,
	ComparisonController.CellRenderTarget
{
	private static final Color TEXT_DIM = new Color(160, 160, 160);
	private static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);
	static final Color KC_COLOR = new Color(215, 215, 215);
	private static final Color HAMBURGER_COLOR = new Color(70, 70, 70);
	private static final Color HAMBURGER_HOVER_COLOR = new Color(96, 96, 96);
	private static final String SYNC_NOTICE = "Open Collection Log and click";
	private static final int SYNC_ICON_SIZE = 12;

	/** Info bar text color — only applies when highlighter is active AND clog data exists. */
	@Override
	public Color getInfoColor()
	{
		return config.completionistHighlighter() && lookupSession.getClogResult() != null
			? config.infoBarColor() : KC_COLOR;
	}

	private BufferedImage getSyncIcon()
	{
		if (syncNoticeIcon == null)
		{
			BufferedImage raw = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
			syncNoticeIcon = ImageUtil.resizeImage(raw, SYNC_ICON_SIZE, SYNC_ICON_SIZE);
		}
		return syncNoticeIcon;
	}

	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final KillClogConfig config;
	private final ConfigManager configManager;
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final TooltipDataBuilder tooltipDataBuilder;
	private ProgressHighlighter highlighter;

	private final JLabel searchStatus = new JLabel(" ");
	private final IconTextField searchBar = new IconTextField();
	private final JLabel playerName = new JLabel(" ")
	{
		@Override
		public JToolTip createToolTip()
		{
			SummaryTooltip tip = new SummaryTooltip();
			tip.setComponent(this);
			String name = playerName.getText().trim();
			tip.setData(
				name.isEmpty() ? "Player" : name,
				lookupSession.getHiscoreResult() != null ? lookupSession.getHiscoreResult().getOverallRank() : -1,
				getCapeImage(),
				LookupQueries.getAccountBadge(lookupSession.getHiscoreResult()),
				LookupQueries.getAccountLabel(lookupSession.getHiscoreResult()),
				LookupQueries.getPrestige(lookupSession.getHiscoreResult())
			);
			if (lookupSession.getClogResult() != null)
			{
				List<Integer> allPets = lookupSession.getClogResult().getCategoryItems().get("all_pets");
				Set<Integer> obtainedPets = LookupQueries.getObtainedPetIds(lookupSession.getClogResult());
				tip.setPets(allPets, obtainedPets, itemManager);
			}
			return tip;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			paintUnderline(this, g, true);
		}
	};
	private final JLabel clogInfoLabel = new JLabel()
	{
		@Override
		public JToolTip createToolTip()
		{
			ClogSummaryTooltip tip = new ClogSummaryTooltip();
			tip.setComponent(this);
			if (lookupSession.getClogResult() != null)
			{
				int[] totals = ClogHelper.sumClogTotals(lookupSession.getClogResult());
				Map<String, BufferedImage> icons = new LinkedHashMap<>();
				for (Map.Entry<String, ImageIcon> entry : clogTierIcons.entrySet())
				{
					icons.put(entry.getKey(), ClogHelper.iconToImage(entry.getValue()));
				}
				tip.setTierData(totals[0], totals[1], icons);
				if (lookupSession.getHiscoreResult() != null)
				{
					int clogRank = lookupSession.getHiscoreResult().getActivityRank("Collections Logged");
					tip.setRank(clogRank);
				}
				boolean stale = LookupQueries.isSyncStale(lookupSession.getClogLastChanged(), 90);
				String sync = LookupQueries.syncLine(lookupSession.getClogLastChanged(), stale);
				if (sync != null) tip.setSyncData(sync, stale);
				tip.setRecentItems(LookupQueries.getRecentItems(lookupSession.getClogResult(), 4), itemManager);
			}
			else
			{
				boolean isSelf = localRsn != null && rsn != null
					&& localRsn.equalsIgnoreCase(rsn);
				if (isSelf)
				{
					tip.setNotice(SYNC_NOTICE, getSyncIcon());
				}
				else
				{
					tip.setNotice(lookupSession.getHiscoreResult() != null
						? "No TempleOSRS Data" : "Nothing to see here! (Search for a player)");
				}
			}
			return tip;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			paintUnderline(this, g, false);
		}
	};
	private final JLabel clogNotice = new JLabel();
	private BufferedImage maxCapeTip;
	private BufferedImage infernalCapeTip;
	private BufferedImage infernalMaxCapeTip;
	private final Map<String, ImageIcon> clogTierIcons = new LinkedHashMap<>();
	private final Map<String, ImageIcon> clogTierIconsLarge = new LinkedHashMap<>();

	private JLabel refreshLabel;
	private BufferedImage syncNoticeIcon;
	// Activities tray
	private JLabel combatCell;
	private JLabel totalLvlCell;
	private ActivitiesTray activitiesTray;

	// Current lookup state lives on lookupSession; rsn here is a separate
	// display-name field set by fetchRsn for the playerName label.
	private String rsn;
	private String localRsn;
	private AccountType localAccountType;


	private final TooltipController tooltipController;
	private final LookupSession lookupSession;
	private final ComparisonController comparison;
	private final Cells cells;

	// Comparison mode widgets (state fields all live on the controller)

	// 420 mode — unlocked when the 420 KC plugin is loaded
	private NameAutocompleter nameAutocompleter;
	private FourTwentyMode fourTwentyMode = FourTwentyMode.OFF;
	private boolean has420Plugin;

	@Inject
	public KillClogPanel(HiscoreService hiscoreService, ClogService clogService,
		KillClogConfig config, ConfigManager configManager,
		SpriteManager spriteManager,
		ItemManager itemManager, ClientThread clientThread,
		SkillIconManager skillIconManager)
	{
		super(true); // wrap in JScrollPane
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.config = config;
		this.configManager = configManager;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.tooltipDataBuilder = new TooltipDataBuilder(itemManager);
		this.tooltipController = new TooltipController(config);
		this.lookupSession = new LookupSession(hiscoreService, clogService, config, null, this);
		this.comparison = new ComparisonController(hiscoreService, clogService, config, lookupSession,
			itemManager, tooltipController, tooltipDataBuilder, this);
		this.comparison.setRenderTarget(this);
		this.cells = new Cells(spriteManager, itemManager, tooltipController, comparison, tooltipDataBuilder, lookupSession, clogService);
		this.comparison.setCells(this.cells);
		this.cells.setSinglePlayerTooltipBuilder(new Cells.SinglePlayerTooltipBuilder()
		{
			@Override
			public JToolTip build(JLabel owner, TooltipData data, int gridCols, String name)
			{
				return makeSpriteTooltip(owner, data, gridCols, name);
			}

			@Override
			public JToolTip build(JLabel owner, TooltipData data, int gridCols, String name, boolean compact)
			{
				return makeSpriteTooltip(owner, data, gridCols, name, compact);
			}
		});

		NativeTooltip.loadSprites(spriteManager);
		SkillsTooltip.loadIcons(skillIconManager);


		setBorder(new EmptyBorder(10, 10, 0, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 0;
		c.insets = new Insets(0, 0, 5, 0);
		add(buildSearchPanel(), c);

		// Activities tray with slide animation
		c.gridy++;
		c.insets = new Insets(0, 0, 0, 0);
		activitiesTray = new ActivitiesTray(buildActivitiesGrid(), config.activitiesExpanded(),
			expanded -> configManager.setConfiguration("killclog", "activitiesExpanded", expanded));
		add(activitiesTray.getClip(), c);

		// Separator between activities and boss grid (also the click target to toggle the tray)
		c.gridy++;
		add(activitiesTray.getSeparator(), c);

		c.gridy++;
		add(cells.buildBossGrid(), c);
		// 420 mode easter egg: secret cycle on Thermonuclear Smoke Devil click.
		JLabel thermoLabel = cells.getBossLabel(HiscoreSkill.THERMONUCLEAR_SMOKE_DEVIL);
		if (thermoLabel != null)
		{
			thermoLabel.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (has420Plugin && !comparison.isComparisonMode())
					{
						cycleFourTwentyMode();
					}
				}
			});
		}

		// Collection log sync notice below boss grid
		c.gridy++;
		c.insets = new Insets(5, 0, 0, 0);
		clogNotice.setFont(FontManager.getRunescapeSmallFont());
		clogNotice.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		clogNotice.setHorizontalAlignment(JLabel.CENTER);
		clogNotice.setText(" ");
		clogNotice.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		add(clogNotice, c);

		// Compare toggle — split blue/red magnifying glass, hidden until a player is looked up
		c.gridy++;
		c.insets = new Insets(4, 0, 0, 0);
		ImageIcon compareOff = new ImageIcon(ClogHelper.makeCompareIcon(ComparisonController.COMPARE_BLUE, ComparisonController.COMPARE_RED, 0.55f));
		ImageIcon compareOn = new ImageIcon(ClogHelper.makeCompareIcon(ComparisonController.COMPARE_BLUE, ComparisonController.COMPARE_RED, 1.0f));
		JLabel compareToggle = new JLabel(compareOff);
		comparison.setCompareToggle(compareToggle);
		compareToggle.setHorizontalAlignment(JLabel.CENTER);
		compareToggle.setPreferredSize(new Dimension(15, 15));
		compareToggle.setOpaque(false);
		compareToggle.setToolTipText("Compare Player");
		compareToggle.setVisible(false);
		compareToggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				boolean show = !comparison.getComparePanel().isVisible();
				comparison.getComparePanel().setVisible(show);
				if (!show && comparison.isComparisonMode())
				{
					comparison.exit();
				}
				revalidate();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				compareToggle.setIcon(compareOn);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				compareToggle.setIcon(compareOff);
			}
		});
		add(compareToggle, c);

		// Compare search bar — hidden until toggle is clicked
		c.gridy++;
		c.insets = new Insets(2, 0, 0, 0);
		JPanel comparePanel = buildCompareSearch();
		comparison.setComparePanel(comparePanel);
		comparePanel.setVisible(false);
		add(comparePanel, c);

		JScrollPane sp = getScrollPane();
		if (sp != null)
		{
			sp.setBorder(null);
			sp.setViewportBorder(null);
			sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
			sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
			sp.getVerticalScrollBar().setUI(new MinimalScrollBarUI());
			sp.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
			sp.getVerticalScrollBar().setUnitIncrement(16);
		}

		highlighter = new ProgressHighlighter(
			cells.getBossLabels(), cells.getActivityLabels(), cells.getClueTierLabels(),
			PanelData.NAME_OVERRIDES, PanelData.CLUE_CATEGORIES, config);
	}

	// -------------------------------------------------------------------------
	// Panel construction
	// -------------------------------------------------------------------------

	private JPanel buildSearchPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(null);

		// Search status — lives above the search bar, never in the info bar
		searchStatus.setFont(FontManager.getRunescapeSmallFont());
		searchStatus.setForeground(TEXT_DIM);
		searchStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchStatus.setBorder(new EmptyBorder(0, 4, 2, 0));
		searchStatus.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		panel.add(searchStatus);

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setPreferredSize(new Dimension(0, 30));
		searchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchBar.addActionListener(e -> doLookup());

		ClogHelper.styleSearchBar(searchBar);
		for (Component c : searchBar.getComponents())
		{
			if (c instanceof FlatTextField)
			{
				JTextField tf =
					((FlatTextField) c).getTextField();
				tf.setFont(FontManager.getRunescapeFont());
				tf.setForeground(Color.WHITE);
				tf.setCaretColor(Color.WHITE);
				tf.putClientProperty(
					RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			}
			else if (c instanceof Container)
			{
				ClogHelper.styleSearchBar((Container) c);
			}
		}
		// Refresh icon floats over the search bar's right edge.
		// Icon = null when not hovered (invisible), shown on search area hover.
		refreshLabel = new JLabel();
		ImageIcon refreshOff = new ImageIcon(ClogHelper.makeRefreshIcon(HAMBURGER_COLOR));
		ImageIcon refreshOn = new ImageIcon(ClogHelper.makeRefreshIcon(HAMBURGER_HOVER_COLOR));
		refreshLabel.setHorizontalAlignment(JLabel.CENTER);
		refreshLabel.setVerticalAlignment(JLabel.CENTER);
		refreshLabel.setOpaque(false);
		refreshLabel.setVisible(false);

		JPanel searchRow = new JPanel(null)
		{
			@Override
			public void doLayout()
			{
				int w = getWidth(), h = getHeight();
				searchBar.setBounds(0, 0, w, h);
				refreshLabel.setBounds(w - 22, 0, 22, h);
			}
		};
		searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchRow.setPreferredSize(new Dimension(0, 30));
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		searchRow.add(refreshLabel);
		searchRow.add(searchBar);

		// searchBar hover → show dim refresh icon. Double-click on the magnifying-glass icon → look up self.
		searchBar.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (refreshLabel.isVisible())
				{
					refreshLabel.setIcon(refreshOff);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (searchRow.getMousePosition(true) == null)
					{
						refreshLabel.setIcon(null);
					}
				});
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				// Match vanilla Hiscores: double-click the magnifying-glass icon to look up the logged-in player.
				if (e.getClickCount() == 2 && e.getX() < 25 && localRsn != null && !localRsn.isEmpty())
				{
					searchBar.setText(localRsn);
					doLookup();
				}
			}
		});

		// refreshLabel hover → brighten, click → re-lookup
		refreshLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				String name = playerName.getText().trim();
				if (!name.isEmpty())
				{
					searchBar.setText(name);
					doLookup();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				refreshLabel.setIcon(refreshOn);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (searchRow.getMousePosition(true) != null)
					{
						refreshLabel.setIcon(refreshOff);
					}
					else
					{
						refreshLabel.setIcon(null);
					}
				});
			}
		});

		panel.add(searchRow);
		panel.add(Box.createVerticalStrut(4));

		// Info bar: [badge+name LEFT] [hamburger CENTER] [tierIcon+clogCount RIGHT]
		// GridBagLayout ensures playerName/clogInfoLabel fill remaining space
		// while the hamburger always keeps its fixed center slot.
		JPanel infoRow = new JPanel(new GridBagLayout());
		infoRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		infoRow.setPreferredSize(new Dimension(0, 18));

		configureBarLabel(playerName, JLabel.LEFT);
		playerName.setBorder(new EmptyBorder(0, 4, 0, 0));
		playerName.setMinimumSize(new Dimension(0, 0));
		playerName.setPreferredSize(new Dimension(0, 18));

		configureBarLabel(clogInfoLabel, JLabel.RIGHT);
		clogInfoLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
		clogInfoLabel.setMinimumSize(new Dimension(0, 0));
		clogInfoLabel.setPreferredSize(new Dimension(0, 18));

		// Info bar labels use click-to-show in click mode (same as boss cells).
		// No special hover override — avoids ToolTipManager showImmediately cascade.
		MouseAdapter infoBarClickHandler = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (config.tooltipMode() == TooltipMode.CLICK)
				{
					JLabel label = (JLabel) e.getSource();
					if (label.getToolTipText() != null)
					{
						tooltipController.showClickTooltip(label, infoRow);
					}
				}
			}
		};
		playerName.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (comparison.isComparisonMode())
				{
					comparison.exit();
					return;
				}
				infoBarClickHandler.mousePressed(e);
			}
		});
		clogInfoLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (comparison.isComparisonMode() && comparison.getCompareRsn() != null)
				{
					comparison.swapToComparePlayer();
					return;
				}
				infoBarClickHandler.mousePressed(e);
			}
		});

		for (JLabel barLabel : new JLabel[]{playerName, clogInfoLabel})
		{
			barLabel.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					if (barLabel.getToolTipText() != null
						|| (comparison.isComparisonMode() && (barLabel == clogInfoLabel || barLabel == playerName)))
					{
						barLabel.putClientProperty("underlined", true);
						barLabel.repaint();
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					barLabel.putClientProperty("underlined", null);
					barLabel.repaint();
				}
			});
		}

		JLabel trayToggle = new JLabel();
		ImageIcon hamburgerIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(HAMBURGER_COLOR));
		ImageIcon hamburgerHoverIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(HAMBURGER_HOVER_COLOR));
		trayToggle.setIcon(hamburgerIcon);
		trayToggle.setHorizontalAlignment(JLabel.CENTER);
		trayToggle.setVerticalAlignment(JLabel.CENTER);
		trayToggle.setPreferredSize(new Dimension(30, 18));
		trayToggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				activitiesTray.toggle();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				trayToggle.setIcon(hamburgerHoverIcon);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				trayToggle.setIcon(hamburgerIcon);
			}
		});

		GridBagConstraints ibc = new GridBagConstraints();
		ibc.gridy = 0;
		ibc.fill = GridBagConstraints.HORIZONTAL;
		ibc.anchor = GridBagConstraints.WEST;

		// Left: player name — takes remaining space, clips long text
		ibc.gridx = 0;
		ibc.weightx = 1.0;
		infoRow.add(playerName, ibc);

		// Center: hamburger — fixed width, never displaced
		ibc.gridx = 1;
		ibc.weightx = 0;
		ibc.fill = GridBagConstraints.NONE;
		ibc.anchor = GridBagConstraints.CENTER;
		infoRow.add(trayToggle, ibc);

		// Right: clog info — takes remaining space, clips long text
		ibc.gridx = 2;
		ibc.weightx = 1.0;
		ibc.fill = GridBagConstraints.HORIZONTAL;
		ibc.anchor = GridBagConstraints.EAST;
		infoRow.add(clogInfoLabel, ibc);
		panel.add(infoRow);

		loadRuntimeIcons();
		return panel;
	}

	private void configureBarLabel(JLabel label, int alignment)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setHorizontalAlignment(alignment);
		label.setIconTextGap(3);
		label.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	private void loadRuntimeIcons()
	{
		// Cape + clog tier icons via ItemManager (game items, loaded at runtime)
		clientThread.invokeLater(() ->
		{
			// Tooltip cape icons — native aspect ratio, taller
			loadItemImage(13280, img -> maxCapeTip = img);
			loadItemImage(21295, img -> infernalCapeTip = img);
			loadItemImage(21284, img -> infernalMaxCapeTip = img);

			for (int i = 0; i < ClogHelper.CLOG_TIERS.length; i++)
			{
				final String tier = ClogHelper.CLOG_TIERS[i];
				final int itemId = PanelData.CLOG_TIER_ITEM_IDS[i];
				loadItemIcon(itemId, 13, 13, icon ->
					clogTierIcons.put(tier, icon));
				loadItemIcon(itemId, 18, 18, icon ->
					clogTierIconsLarge.put(tier, icon));
			}

			// Clue summary tooltip icons: 1-6=tier scrolls, 7=Mimic (0=All loaded via spriteManager below)
			for (int i = 0; i < PanelData.CLUE_TIER_ITEM_IDS.length; i++)
			{
				final int idx = i + 1;
				loadItemImage(PanelData.CLUE_TIER_ITEM_IDS[i], img ->
					cells.getClueIcons()[idx] = ImageUtil.resizeImage(
						ImageUtil.resizeCanvas(img, 25, 25), 13, 13));
			}
			loadItemImage(23184, img ->
				cells.getClueIcons()[7] = ImageUtil.resizeImage(
					ImageUtil.resizeCanvas(img, 25, 25), 13, 13));
		});

		// Clue All icon via spriteManager (game sprite, not item)
		int allSpriteId = HiscoreSkill.CLUE_SCROLL_ALL.getSpriteId();
		if (allSpriteId != -1)
		{
			spriteManager.getSpriteAsync(allSpriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						cells.getClueIcons()[0] = ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
					}
				}));
		}
	}

	private void loadItemIcon(int itemId, int w, int h, Consumer<ImageIcon> setter)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() ->
					setter.accept(new ImageIcon(ImageUtil.resizeImage(img, w, h)))));
		}
		else
		{
			SwingUtilities.invokeLater(() ->
				setter.accept(new ImageIcon(ImageUtil.resizeImage(img, w, h))));
		}
	}

	private void loadItemImage(int itemId, Consumer<BufferedImage> setter)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() -> setter.accept(img)));
		}
		else
		{
			SwingUtilities.invokeLater(() -> setter.accept(img));
		}
	}

	// -------------------------------------------------------------------------
	// Activities tray
	// -------------------------------------------------------------------------

	private JPanel buildActivitiesGrid()
	{
		JPanel grid = new JPanel();
		grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Row 0: [Combat Level] [Total Level]
		JPanel statsRow = new JPanel(new GridLayout(1, 3));
		statsRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsRow.setAlignmentX(0f);

		combatCell = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				PvmSummaryTooltip tip = new PvmSummaryTooltip();
				tip.setComponent(this);
				tip.setData(
					lookupSession.getHiscoreResult() != null ? lookupSession.getHiscoreResult().getCombatLevel() : 0,
					LookupQueries.sumBossKills(lookupSession.getHiscoreResult()),
					LookupQueries.countBossesWithKc(lookupSession.getHiscoreResult()),
					PanelData.BOSSES.length,
					LookupQueries.getMostKilledBoss(lookupSession.getHiscoreResult()),
					LookupQueries.getMostKilledKc(lookupSession.getHiscoreResult())
				);
				if (lookupSession.getClogResult() != null)
				{
					tip.setCompletion(
						LookupQueries.countBossesCompleted(cells.getTooltipDataMap(), cells.getBossLabels().keySet()),
						LookupQueries.countBossesWithClog(cells.getTooltipDataMap(), cells.getBossLabels().keySet()));
				}
				tip.setMegarares(
					LookupQueries.getClogItemCount(lookupSession.getClogResult(), "chambers_of_xeric", 20997),
					LookupQueries.getClogItemCount(lookupSession.getClogResult(), "theatre_of_blood", 22486),
					LookupQueries.getClogItemCount(lookupSession.getClogResult(), "tombs_of_amascut", 27277),
					itemManager
				);
				if (lookupSession.getHiscoreResult() != null)
				{
					tip.setRaids(lookupSession.getHiscoreResult(), lookupSession.getClogResult());
				}
				JPanel parentCell = (JPanel) this.getParent();
				tooltipController.keepTooltipOnHover(tip, parentCell);
				return tip;
			}
		};
		Cells.styleLabel(combatCell, "Combat");
		spriteManager.getSpriteAsync(168, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite != null)
				{
					combatCell.setIcon(new ImageIcon(ImageUtil.resizeImage(
						ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20)));
				}
			}));
		statsRow.add(cells.wrapInCell(combatCell));

		totalLvlCell = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				SkillsTooltip tip = new SkillsTooltip();
				tip.setComponent(this);
				tip.setData(lookupSession.getHiscoreResult());
				return tip;
			}
		};
		Cells.styleLabel(totalLvlCell, "Total");
		try
		{
			BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, "overall.png");
			totalLvlCell.setIcon(new ImageIcon(ImageUtil.resizeImage(
				ImageUtil.resizeCanvas(img, 25, 25), 20, 20)));
		}
		catch (Exception e)
		{
			// overall.png not available
		}
		statsRow.add(cells.wrapInCell(totalLvlCell));
		statsRow.add(cells.buildPvpSummaryCell());
		grid.add(statsRow);

		JPanel statsSep = new JPanel();
		statsSep.setBackground(ColorScheme.DARK_GRAY_COLOR);
		statsSep.setPreferredSize(new Dimension(0, 7));
		statsSep.setAlignmentX(0f);
		grid.add(statsSep);

		// Clue row 1: [3rd Age] [Clue Summary] [Gilded]
		JPanel row1 = new JPanel(new GridLayout(1, 3));
		row1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row1.setAlignmentX(0f);
		row1.add(cells.buildClueRareCell("3rd Age", PanelData.THIRD_AGE_ITEM_ID, PanelData.CLOG_THIRD_AGE, true));
		row1.add(cells.buildActivityCell(HiscoreSkill.CLUE_SCROLL_ALL));
		row1.add(cells.buildClueRareCell("Gilded", PanelData.GILDED_ITEM_ID, PanelData.CLOG_GILDED, false));
		grid.add(row1);

		// Clue row 2: Custom rare cells (casket icons)
		JPanel rareRow = new JPanel(new GridLayout(1, 3));
		rareRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rareRow.setAlignmentX(0f);
		rareRow.add(cells.buildCustomRareCell("Hard Treasure (Rare)", 20544, PanelData.RARE_HARD, PanelData.HARD_RARE_ITEMS));
		rareRow.add(cells.buildCustomRareCell("Elite Treasure (Rare)", 20543, PanelData.RARE_ELITE, PanelData.ELITE_RARE_ITEMS));
		rareRow.add(cells.buildCustomRareCell("Master Treasure (Rare)", 19836, PanelData.RARE_MASTER, PanelData.MASTER_RARE_ITEMS));
		grid.add(rareRow);

		// Clue rows 3-4: Clue tiers
		JPanel clueRow1 = new JPanel(new GridLayout(1, 3));
		clueRow1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow1.setAlignmentX(0f);
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[0], PanelData.CLUE_TIER_ITEM_IDS[0], false));
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[1], PanelData.CLUE_TIER_ITEM_IDS[1], true));
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[2], PanelData.CLUE_TIER_ITEM_IDS[2], true));
		grid.add(clueRow1);

		JPanel clueRow2 = new JPanel(new GridLayout(1, 3));
		clueRow2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow2.setAlignmentX(0f);
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[3], PanelData.CLUE_TIER_ITEM_IDS[3], true));
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[4], PanelData.CLUE_TIER_ITEM_IDS[4], false));
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[5], PanelData.CLUE_TIER_ITEM_IDS[5], false));
		grid.add(clueRow2);

		return grid;
	}

	// -------------------------------------------------------------------------
	// Cell factory helpers — extracted to eliminate duplication
	// -------------------------------------------------------------------------


	/**
	 * Build a sprite tooltip for a cell — always ImgTooltip, with contextual notice when no data.
	 *
	 * @param owner     the label whose parent cell drives hover tint
	 * @param data      tooltip data, or null for notice-only display
	 * @param gridCols  min columns for the sprite grid
	 * @param name      display name shown as title when data is null
	 */
	private JToolTip makeSpriteTooltip(JLabel owner, TooltipData data, int gridCols, String name)
	{
		return makeSpriteTooltip(owner, data, gridCols, name, false);
	}

	private JToolTip makeSpriteTooltip(JLabel owner, TooltipData data, int gridCols,
										String name, boolean compact)
	{
		JPanel parentCell = (JPanel) owner.getParent();
		ImgTooltip tip = compact ? new ImgTooltip(gridCols, 15) : new ImgTooltip(gridCols);
		tip.setComponent(owner);

		if (data != null)
		{
			tip.setTitle(data.name);
			tip.setObtained(data.obtainedCount, data.totalItems);
			if (data.rank > 0)
			{
				tip.setRank(data.rank);
			}
			tip.setItems(data.totalItems, data.allItemIds, data.obtainedIds,
				data.obtainedCounts, itemManager);
		}
		else
		{
			tip.setTitle(name);
			boolean isSelfNoCache = lookupSession.getHiscoreResult() != null && localRsn != null
				&& localRsn.equalsIgnoreCase(lookupSession.getCurrentLookupRsn());
			if (isSelfNoCache)
			{
				tip.setNotice(SYNC_NOTICE, getSyncIcon());
			}
			else
			{
				tip.setNotice(lookupSession.getHiscoreResult() != null
					? "No TempleOSRS Data"
					: "Nothing to see here! (Search for a player)");
			}
		}

		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	// -------------------------------------------------------------------------
	// Comparison mode
	// -------------------------------------------------------------------------

	private JPanel buildCompareSearch()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(null);

		comparison.getCompareStatusLabel().setFont(FontManager.getRunescapeSmallFont());
		comparison.getCompareStatusLabel().setForeground(TEXT_DIM);
		comparison.getCompareStatusLabel().setAlignmentX(Component.LEFT_ALIGNMENT);
		comparison.getCompareStatusLabel().setBorder(new EmptyBorder(0, 4, 2, 0));
		comparison.getCompareStatusLabel().putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		panel.add(comparison.getCompareStatusLabel());

		comparison.getCompareSearchBar().setIcon(IconTextField.Icon.SEARCH);
		comparison.getCompareSearchBar().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		comparison.getCompareSearchBar().setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		comparison.getCompareSearchBar().setPreferredSize(new Dimension(0, 30));
		comparison.getCompareSearchBar().setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		comparison.getCompareSearchBar().setAlignmentX(Component.LEFT_ALIGNMENT);
		comparison.getCompareSearchBar().addActionListener(e -> comparison.doCompareLookup(localRsn));

		ClogHelper.styleSearchBar(comparison.getCompareSearchBar());

		// Recolor the clear X to match comparison red
		recolorClearButton(comparison.getCompareSearchBar(), ComparisonController.COMPARE_RED);

		// Style inner text field: red text, placeholder
		for (Component c : comparison.getCompareSearchBar().getComponents())
		{
			if (c instanceof FlatTextField)
			{
				JTextField tf = ((FlatTextField) c).getTextField();
				comparison.setCompareTextField(tf);
				tf.setFont(FontManager.getRunescapeFont());
				tf.setCaretColor(ComparisonController.COMPARE_RED);
				tf.putClientProperty(
					RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

				// Placeholder via focus listeners
				Color placeholderColor = ColorScheme.MEDIUM_GRAY_COLOR;
				tf.setForeground(placeholderColor);
				tf.setText(comparison.getComparePlaceholder());
				tf.addFocusListener(new java.awt.event.FocusAdapter()
				{
					@Override
					public void focusGained(java.awt.event.FocusEvent e)
					{
						if (tf.getText().equals(comparison.getComparePlaceholder()))
						{
							tf.setText("");
						}
						tf.setForeground(ComparisonController.COMPARE_RED);
					}

					@Override
					public void focusLost(java.awt.event.FocusEvent e)
					{
						if (tf.getText().isEmpty())
						{
							tf.setForeground(placeholderColor);
							tf.setText(comparison.getComparePlaceholder());
						}
					}
				});
			}
		}

		panel.add(comparison.getCompareSearchBar());
		return panel;
	}

	@Override
	public void onComparisonExit()
	{
		comparison.getCompareSearchBar().setText("");
		comparison.getCompareSearchBar().setIcon(IconTextField.Icon.SEARCH);
		comparison.getCompareStatusLabel().setText(" ");
		comparison.updateAllCells();
		comparison.updateInfoBar();
		toggleHighlighter(config.completionistHighlighter());
	}

	@Override
	public void onSwapToRedPlayer(String newPrimaryRsn)
	{
		rsn = newPrimaryRsn;
		HiscoreResult swapHiscore = lookupSession.getHiscoreResult();
		ClogResult swapClog = lookupSession.getClogResult();

		playerName.setText(newPrimaryRsn != null ? newPrimaryRsn : "");
		playerName.setForeground(getInfoColor());
		if (swapHiscore != null)
		{
			updateInfoIcon(swapHiscore.getAccountType());
			int combatLevel = swapHiscore.getCombatLevel();
			if (combatLevel > 0)
			{
				combatCell.setText(ClogHelper.pad(String.valueOf(combatLevel)));
				combatCell.setForeground(getInfoColor());
			}
			int totalLevel = swapHiscore.getTotalLevel();
			if (totalLevel > 0)
			{
				totalLvlCell.setText(ClogHelper.pad(String.valueOf(totalLevel)));
				totalLvlCell.setForeground(getInfoColor());
				totalLvlCell.setToolTipText(" ");
			}
		}
		if (swapClog != null)
		{
			AccountType templeType = swapClog.getTempleAccountType();
			if (templeType != null && templeType.isGroupIronman())
			{
				updateInfoIcon(templeType);
			}
			lookupItemNames(swapClog);
			cells.renderClog(swapClog, config);
			updateClogCell(swapClog);
		}

		comparison.getCompareToggle().setVisible(true);
		refreshLabel.setVisible(true);
		setSearchStatus(" ", TEXT_DIM);
		toggleHighlighter(config.completionistHighlighter());
		cells.rebuildPrimaryTooltips(localRsn);
	}


	@Override
	public void onComparisonEnter(@SuppressWarnings("unused") String redRsn)
	{
		comparison.getCompareSearchBar().setIcon(IconTextField.Icon.SEARCH);
		comparison.getCompareSearchBar().setText("");
		comparison.getCompareStatusLabel().setText(" ");
		cells.rebuildPrimaryTooltips(localRsn);
		comparison.updateAllCells();
		comparison.updateInfoBar();
	}


	/** Apply account type badge to any label. */
	@Override
	public void applyBadge(JLabel label, AccountType type)
	{
		if (type == null)
		{
			label.setIcon(null);
			return;
		}
		BufferedImage gimBadge = ClogHelper.getGimBadge(type);
		if (gimBadge != null)
		{
			int h = 15;
			int w = (int) Math.round((double) gimBadge.getWidth() / gimBadge.getHeight() * h);
			label.setIcon(new ImageIcon(ImageUtil.resizeImage(gimBadge, w, h)));
			return;
		}
		String resource = ClogHelper.accountBadgeResource(type);
		if (resource == null)
		{
			label.setIcon(null);
			return;
		}
		try
		{
			BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, resource);
			label.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 15, 15)));
		}
		catch (Exception e)
		{
			label.setIcon(null);
		}
	}

	// -------------------------------------------------------------------------
	// Lookup flow
	// -------------------------------------------------------------------------

	/**
	 * Single point of control for the info bar when no result data is available.
	 * Hides all data components; shows only the message text.
	 */
	private void setSearchStatus(String text, Color color)
	{
		searchStatus.setText(text);
		searchStatus.setForeground(color);
	}

	private static void recolorClearButton(Container container, Color color)
	{
		for (Component c : container.getComponents())
		{
			if (c instanceof AbstractButton)
			{
				AbstractButton btn = (AbstractButton) c;
				if (btn.getIcon() instanceof ImageIcon)
				{
					ImageIcon icon = (ImageIcon) btn.getIcon();
					btn.setIcon(new ImageIcon(ImageUtil.recolorImage(icon.getImage(), color)));
				}
			}
			else if (c instanceof Container)
			{
				recolorClearButton((Container) c, color);
			}
		}
	}

	private String selfSearchMessage(String player)
	{
		if (!config.seenSelfGreeting())
		{
			configManager.setConfiguration("killclog", "seenSelfGreeting", true);
			return "Hey, that's you!";
		}

		int roll = ThreadLocalRandom.current().nextInt(100);
		String[] pool;
		if (roll < 1)
		{
			pool = SearchMessages.SELF_ULTRA;
		}
		else if (roll < 10)
		{
			pool = SearchMessages.SELF_RARE;
		}
		else
		{
			pool = SearchMessages.SELF;
		}
		return String.format(pool[ThreadLocalRandom.current().nextInt(pool.length)], player);
	}

	public void doLookup()
	{
		String player = searchBar.getText().trim();
		if (player.isEmpty() || lookupSession.isLookupInFlight())
		{
			if (!lookupSession.isLookupInFlight())
			{
				setSearchStatus("Enter RSN", TEXT_DIM);
			}
			return;
		}
		lookupSession.start(player, localRsn, localAccountType);
	}

	/**
	 * Reset all labels, maps, and fields to their pre-lookup state.
	 * Called at the start of every lookup to ensure a clean slate.
	 */
	private void resetAllLabels()
	{
		tooltipController.hideClickTooltip();
		if (comparison.isComparisonMode()) comparison.exit();
		comparison.getComparePanel().setVisible(false);
		rsn = null;
		clogNotice.setText(" ");
		clogNotice.setIcon(null);
		cells.getTooltipDataMap().clear();
		cells.getRareTooltips().clear();
		for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getBossLabels().entrySet())
		{
			JLabel label = entry.getValue();
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setToolTipText(" ");
			ImageIcon orig = cells.getOriginalIcons().get(entry.getKey());
			if (orig != null) label.setIcon(orig);
		}

		resetLabelMap(cells.getActivityLabels());

		playerName.setText(" ");
		playerName.setIcon(null);
		playerName.setToolTipText(null);
		comparison.getCompareToggle().setVisible(false);
		refreshLabel.setVisible(false);
		refreshLabel.setIcon(null);

		clogInfoLabel.setIcon(null);
		clogInfoLabel.setText("");
		clogInfoLabel.setToolTipText(null);

		combatCell.setText(ClogHelper.pad("--"));
		combatCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalLvlCell.setText(ClogHelper.pad("--"));
		totalLvlCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalLvlCell.setToolTipText(null);
		if (cells.getPvpSummaryCell() != null)
		{
			cells.getPvpSummaryCell().setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}

		resetLabelMap(cells.getClueTierLabels());

		resetRareCell(cells.getThirdAgeCell(), "3rd Age");
		resetRareCell(cells.getGildedCell(), "Gilded");
		resetRareCell(cells.getHardRare(), "Hard Treasure (Rare)");
		resetRareCell(cells.getEliteRare(), "Elite Treasure (Rare)");
		resetRareCell(cells.getMasterRare(), "Master Treasure (Rare)");
		cells.getRareTooltips().remove(PanelData.CLOG_THIRD_AGE);
		cells.getRareTooltips().remove(PanelData.CLOG_GILDED);
	}

	/**
	 * Render hiscore data to the panel (extracted for cache/SWR reuse).
	 */
	private void renderHiscoreResult(HiscoreResult result, String player,
		boolean isSelf, AccountType knownType)
	{
		comparison.getCompareStatusLabel().setText(" ");
		playerName.setText(rsn != null ? rsn : player);
		playerName.setForeground(getInfoColor());
		comparison.updateComparePlaceholder(rsn != null ? rsn : player);
		updateInfoIcon(knownType != null ? knownType : result.getAccountType());
		comparison.getCompareToggle().setVisible(true);
		refreshLabel.setVisible(true);

		int combatLevel = result.getCombatLevel();
		if (combatLevel > 0)
		{
			combatCell.setText(ClogHelper.pad(String.valueOf(combatLevel)));
			combatCell.setForeground(getInfoColor());
		}

		int totalLevel = result.getTotalLevel();
		if (totalLevel > 0)
		{
			totalLvlCell.setText(ClogHelper.pad(String.valueOf(totalLevel)));
			totalLvlCell.setForeground(getInfoColor());
			totalLvlCell.setToolTipText(" ");
		}

		searchBar.setText("");
		toggleHighlighter(config.completionistHighlighter());
		cells.rebuildPrimaryTooltips(localRsn);
	}

	/**
	 * Render clog data to the panel (extracted for SWR reuse).
	 */
	private void renderClogResult(ClogResult result, boolean isSelf, int thisLookup)
	{
		String name = result.getPlayerName();
		if (name != null && !name.isEmpty())
		{
			rsn = name;
			comparison.updateComparePlaceholder(name);
			if (lookupSession.getHiscoreResult() != null)
			{
				playerName.setText(name);
			}
		}
		AccountType templeType = result.getTempleAccountType();
		if (templeType != null && templeType.isGroupIronman() && lookupSession.getHiscoreResult() != null)
		{
			updateInfoIcon(templeType);
		}
		lookupItemNames(result);
		if (lookupSession.getHiscoreResult() != null)
		{
			cells.renderClog(result, config);
			updateClogCell(result);
		}
		toggleHighlighter(config.completionistHighlighter());
		cells.rebuildPrimaryTooltips(localRsn);
	}

	private static void resetRareCell(JLabel label, String name)
	{
		if (label != null)
		{
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setToolTipText(name);
		}
	}

	private static void resetLabelMap(Map<HiscoreSkill, JLabel> labels)
	{
		for (Map.Entry<HiscoreSkill, JLabel> entry : labels.entrySet())
		{
			JLabel label = entry.getValue();
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setToolTipText(entry.getKey().getName());
		}
	}

	private void fetchRsn(String player, int thisLookup)
	{
		clogService.lookupRsn(player).thenAccept(name ->
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupSession.getLookupVersion()) return;
				if (name != null && !name.isEmpty())
				{
					rsn = name;
					comparison.updateComparePlaceholder(name);
					if (lookupSession.getHiscoreResult() != null)
					{
						playerName.setText(name);
					}
				}
			})
		);
	}

	// -------------------------------------------------------------------------
	// Label update methods
	// -------------------------------------------------------------------------


	private void updateClogCell(ClogResult result)
	{
		int[] totals = ClogHelper.sumClogTotals(result);
		if (totals[0] > 0)
		{
			String tierName = ClogHelper.getClogTierName(totals[0], totals[1]);
			ImageIcon largeIcon = tierName != null ? clogTierIconsLarge.get(tierName) : null;
			if (largeIcon != null)
			{
				clogInfoLabel.setIcon(new ImageIcon(ClogHelper.createBoostedImage(largeIcon, 1.10f)));
			}
			clogInfoLabel.setText(ClogHelper.pad(ClogHelper.formatKc(totals[0])));
			clogInfoLabel.setForeground(getInfoColor());

			clogInfoLabel.setToolTipText(" ");
		}
	}

	// -------------------------------------------------------------------------
	// Tooltip data building
	// -------------------------------------------------------------------------


	// -------------------------------------------------------------------------
	// Progress highlighter
	// -------------------------------------------------------------------------

	private void toggleHighlighter(boolean enabled)
	{
		if (lookupSession.getHiscoreResult() == null) return;
		tooltipController.clearHoveredCell();

		// Info bar follows highlighter state
		Color infoColor = getInfoColor();
		playerName.setForeground(infoColor);
		clogInfoLabel.setForeground(infoColor);
		if (lookupSession.getHiscoreResult().getCombatLevel() > 0)
		{
			combatCell.setForeground(infoColor);
		}
		if (lookupSession.getHiscoreResult().getTotalLevel() > 0)
		{
			totalLvlCell.setForeground(infoColor);
		}
		if (cells.getPvpSummaryCell() != null)
		{
			int bhTotal = Math.max(0, lookupSession.getHiscoreResult().getActivityScore("Bounty Hunter - Hunter"))
				+ Math.max(0, lookupSession.getHiscoreResult().getActivityScore("Bounty Hunter - Rogue"));
			if (bhTotal > 0)
			{
				cells.getPvpSummaryCell().setForeground(infoColor);
			}
		}

		cells.renderHiscore(lookupSession.getHiscoreResult(), fourTwentyMode);
		if (lookupSession.getClogResult() != null)
		{
			cells.renderClog(lookupSession.getClogResult(), config);
			if (enabled)
			{
				Map<String, JLabel> rareCells = new LinkedHashMap<>();
				rareCells.put(PanelData.CLOG_THIRD_AGE, cells.getThirdAgeCell());
				rareCells.put(PanelData.CLOG_GILDED, cells.getGildedCell());
				rareCells.put(PanelData.RARE_HARD, cells.getHardRare());
				rareCells.put(PanelData.RARE_ELITE, cells.getEliteRare());
				rareCells.put(PanelData.RARE_MASTER, cells.getMasterRare());
				highlighter.colorCellsByCompletion(lookupSession.getHiscoreResult(), lookupSession.getClogResult(),
					cells.getRareTooltips(), rareCells, fourTwentyMode, FourTwentyMode.GREEN);
				highlighter.colorEmptyCells();
			}
		}

		if (comparison.isComparisonMode())
		{
			comparison.updateAllCells();
			comparison.updateInfoBar();
		}
	}

	// -------------------------------------------------------------------------
	// Public interface
	// -------------------------------------------------------------------------

	public void setPlayerName(String name)
	{
		searchBar.setText(name);
	}

	public void setLoggedInPlayer(String name, AccountType accountType)
	{
		this.localRsn = name;
		this.localAccountType = accountType;
	}

	/** Returns the RSN currently displayed in the panel, or null if no lookup is shown. */
	public String getDisplayedRsn()
	{
		return rsn;
	}

	public void onBulkCaptureComplete(String name)
	{
		searchBar.setText(name);
		// Current lookup will finish soon — version it out and start fresh
		lookupSession.cancelInFlight();
		doLookup();
	}

	public void setNameAutocompleter(NameAutocompleter autocompleter)
	{
		this.nameAutocompleter = autocompleter;
		lookupSession.setNameAutocompleter(autocompleter);
		for (Component c : searchBar.getComponents())
		{
			if (c instanceof FlatTextField)
			{
				JTextField textField = ((FlatTextField) c).getTextField();
				// Idempotent: strip any prior NameAutocompleter before attaching.
				// Plugin reload (toggle off/on, hub auto-update) re-runs startUp on the
				// same panel singleton; without this, listeners stack and each keystroke
				// fires the autocomplete twice — suggestion gets inserted as both
				// highlighted suggestion AND committed text.
				for (KeyListener kl : textField.getKeyListeners())
				{
					if (kl instanceof NameAutocompleter)
					{
						textField.removeKeyListener(kl);
					}
				}
				textField.addKeyListener(autocompleter);
				break;
			}
		}
	}

	public void setPluginManager(PluginManager pluginManager)
	{
		has420Plugin = pluginManager.getPlugins().stream()
			.anyMatch(p -> p.getClass().getSimpleName().equals("FourTwentyKcPlugin"));
	}

	public void setFourTwentyVisible(boolean visible)
	{
		has420Plugin = visible;
		if (!visible)
		{
			fourTwentyMode = FourTwentyMode.OFF;
			if (lookupSession.getHiscoreResult() != null) toggleHighlighter(config.completionistHighlighter());
		}
	}

	public void toggleHighlighter()
	{
		boolean newState = !config.completionistHighlighter();
		configManager.setConfiguration("killclog", "completionistHighlighter", newState);
	}

	public void onConfigChanged(String key)
	{
		switch (key)
		{
			case "completionistHighlighter":
			case "completedClogColor":
			case "missing1Color":
			case "inProgressClogColor":
			case "emptyClogColor":
			case "infoBarColor":
				toggleHighlighter(config.completionistHighlighter());
				cells.rebuildPrimaryTooltips(localRsn);
				break;
			case "hoverStyle":
				tooltipController.hideClickTooltip();
				tooltipController.clearHoveredCell();
				break;
			case "tooltipMode":
				tooltipController.restoreDefaults();
				tooltipController.captureDefaults();
				tooltipController.hideClickTooltip();
				tooltipController.clearHoveredCell();
				break;
		}
	}

	@Override
	public void onActivate()
	{
		tooltipController.captureDefaults();
	}

	@Override
	public void onDeactivate()
	{
		tooltipController.restoreDefaults();
	}

	/** Safety net — restores tooltip delay if plugin is disabled while panel is active. */
	public void shutdown()
	{
		tooltipController.restoreDefaults();
	}

	@Override
	public void removeNotify()
	{
		super.removeNotify();
		tooltipController.hideClickTooltip();
	}

	private void cycleFourTwentyMode()
	{
		FourTwentyMode[] modes = FourTwentyMode.values();
		fourTwentyMode = modes[(fourTwentyMode.ordinal() + 1) % modes.length];
		toggleHighlighter(config.completionistHighlighter());
	}

	private BufferedImage getCapeImage()
	{
		if (lookupSession.getHiscoreResult() == null) return null;
		boolean maxed = lookupSession.getHiscoreResult().getTotalLevel() >= PanelData.MAX_TOTAL_LEVEL;
		boolean infernal = lookupSession.getHiscoreResult().getKc("TzKal-Zuk") > 0;
		if (maxed && infernal) return infernalMaxCapeTip;
		if (maxed) return maxCapeTip;
		if (infernal) return infernalCapeTip;
		return null;
	}

	@Override
	public void updateInfoIcon(AccountType type)
	{
		// GIM badges loaded from game modicons at runtime
		BufferedImage gimBadge = ClogHelper.getGimBadge(type);
		if (gimBadge != null)
		{
			int h = 15;
			int w = (int) Math.round((double) gimBadge.getWidth() / gimBadge.getHeight() * h);
			playerName.setIcon(new ImageIcon(ImageUtil.resizeImage(gimBadge, w, h)));
			playerName.setToolTipText(" ");
			return;
		}

		String resource = ClogHelper.accountBadgeResource(type);
		if (resource == null)
		{
			playerName.setIcon(null);
			playerName.setToolTipText(" ");
			return;
		}
		try
		{
			BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, resource);
			playerName.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 15, 15)));
		}
		catch (Exception e)
		{
			playerName.setIcon(null);
		}
		playerName.setToolTipText(" ");
	}

	private void lookupItemNames(ClogResult result)
	{
		Set<Integer> allIds = new HashSet<>();
		for (List<ClogResult.ClogItem> items : result.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : items) allIds.add(item.getId());
		}
		for (List<Integer> ids : result.getCategoryItems().values()) allIds.addAll(ids);

		List<Integer> missing = new ArrayList<>();
		for (int id : allIds)
		{
			if (!result.isItemResolved(id)) missing.add(id);
		}
		if (missing.isEmpty()) return;

		log.debug("Resolving {} untradeable item names via game cache", missing.size());
		clientThread.invokeLater(() ->
		{
			for (int id : missing)
			{
				try
				{
					String name = itemManager.getItemComposition(id).getName();
					if (name != null && !name.isEmpty() && !name.equals("null") && !name.equals("Null"))
					{
						result.markItemResolved(id);
					}
				}
				catch (Exception e)
				{
					// Item not in cache — skip
				}
			}
			SwingUtilities.invokeLater(() -> cells.rebuildPrimaryTooltips(localRsn));
		});
	}

	private static void paintUnderline(JLabel label, Graphics g, boolean leftAligned)
	{
		if (!Boolean.TRUE.equals(label.getClientProperty("underlined")))
		{
			return;
		}
		String text = label.getText();
		if (text == null || text.isBlank())
		{
			return;
		}
		FontMetrics fm = g.getFontMetrics();
		int textWidth = fm.stringWidth(text.trim());
		int y = (label.getHeight() + fm.getAscent() - fm.getDescent()) / 2 + 1;
		g.setColor(label.getForeground());
		if (leftAligned)
		{
			int iconOffset = label.getIcon() != null ? label.getIcon().getIconWidth() + label.getIconTextGap() : 0;
			int textStart = label.getInsets().left + iconOffset;
			g.drawLine(textStart, y, textStart + textWidth, y);
		}
		else
		{
			int textEnd = label.getWidth() - label.getInsets().right;
			g.drawLine(textEnd - textWidth, y, textEnd, y);
		}
	}

	// ── LookupSession.Listener ───────────────────────────────────────────────
	// Each method mirrors the matching chunk of the legacy doLookup body.

	@Override
	public void onLookupStart(String player, boolean isSelf, boolean isFirstSelfGreeting)
	{
		if (isSelf)
		{
			setSearchStatus(selfSearchMessage(player), SearchMessages.SELF_COLOR);
		}
		else
		{
			int searchIdx = ThreadLocalRandom.current().nextInt(SearchMessages.SEARCH.length);
			setSearchStatus(String.format(SearchMessages.SEARCH[searchIdx], player), TEXT_DIM);
		}
		searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);
		resetAllLabels();
	}

	@Override
	public void onCachedResult(String player, HiscoreResult hiscore, @Nullable ClogResult clog,
		boolean isSelf, @Nullable AccountType knownType, boolean isFirstSelfGreeting)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		if (!isFirstSelfGreeting)
		{
			setSearchStatus(" ", TEXT_DIM);
		}
		renderHiscoreResult(hiscore, player, isSelf, knownType);
		if (clog != null)
		{
			renderClogResult(clog, isSelf, lookupSession.getLookupVersion());
		}
	}

	@Override
	public void onHiscoreResult(String player, HiscoreResult hiscore,
		boolean isSelf, @Nullable AccountType knownType, boolean isFirstSelfGreeting)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		if (!isFirstSelfGreeting)
		{
			setSearchStatus(" ", TEXT_DIM);
		}
		renderHiscoreResult(hiscore, player, isSelf, knownType);
		// Clog may have arrived first with a GIM type the hiscores can't detect
		ClogResult clog = lookupSession.getClogResult();
		if (clog != null)
		{
			AccountType templeType = clog.getTempleAccountType();
			if (templeType != null && templeType.isGroupIronman())
			{
				updateInfoIcon(templeType);
			}
			cells.renderClog(clog, config);
			updateClogCell(clog);
		}
	}

	@Override
	public void onClogResult(String player, @Nullable ClogResult clog, boolean isSelf, int lookupVersionAtFire)
	{
		if (clog != null)
		{
			renderClogResult(clog, isSelf, lookupVersionAtFire);
		}
		else
		{
			if (isSelf)
			{
				clogNotice.setText(SYNC_NOTICE);
				clogNotice.setIcon(new ImageIcon(getSyncIcon()));
				BufferedImage icon = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
				clogInfoLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(icon, 15, 15)));
				clogInfoLabel.setText(ClogHelper.pad("Sync"));
				clogInfoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				clogInfoLabel.setToolTipText(" ");
			}
			else
			{
				clogNotice.setText(" ");
				clogNotice.setIcon(null);
			}
			fetchRsn(player, lookupVersionAtFire);
		}
	}

	@Override
	public void onNotFound(String player)
	{
		int notFoundIdx = ThreadLocalRandom.current().nextInt(SearchMessages.NOT_FOUND.length);
		setSearchStatus(String.format(SearchMessages.NOT_FOUND[notFoundIdx], player), NOT_FOUND);
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		playerName.setText(" ");
		playerName.setIcon(null);
		playerName.setToolTipText(null);
		clogInfoLabel.setText("");
		clogInfoLabel.setIcon(null);
		clogInfoLabel.setToolTipText(null);
		searchBar.setText("");
	}

	@Override
	public void onError(String player, Throwable error)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setText("");
		setSearchStatus("Lookup failed", TEXT_DIM);
		playerName.setText(" ");
		playerName.setIcon(null);
		playerName.setToolTipText(null);
		clogInfoLabel.setText("");
		clogInfoLabel.setIcon(null);
		clogInfoLabel.setToolTipText(null);
	}

	// ── ComparisonController.Listener ────────────────────────────────────────

	@Override
	public void onCompareDataReady()
	{
	}

	@Override
	public void onCompareError(String player, Throwable err)
	{
		comparison.setCompareStatus("Lookup failed", ComparisonController.COMPARE_RED);
	}

	// ── ComparisonController.CellRenderTarget ────────────────────────────────
	// Read-only accessors the controller uses for the panel-side info-bar
	// widgets (combat + totalLvl cells, playerName + clogInfoLabel info bar).
	// Cell maps + per-cell labels live on Cells; ComparisonController reads
	// those directly.

	@Override
	public JLabel combatCell()
	{
		return combatCell;
	}

	@Override
	public JLabel totalLvlCell()
	{
		return totalLvlCell;
	}

	@Override
	public JLabel playerName()
	{
		return playerName;
	}

	@Override
	public JLabel clogInfoLabel()
	{
		return clogInfoLabel;
	}

	@Override
	public void preloadClogItemNames(ClogResult clog)
	{
		lookupItemNames(clog);
	}

	@Override
	public void restoreClogCellForCompare(ClogResult clog)
	{
		if (clog != null)
		{
			updateClogCell(clog);
		}
		else
		{
			clogInfoLabel.setText("");
			clogInfoLabel.setIcon(null);
			clogInfoLabel.setToolTipText(null);
		}
	}
}
