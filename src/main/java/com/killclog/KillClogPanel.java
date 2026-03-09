package com.killclog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;
import javax.swing.JTextField;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
{
	private static final Color TEXT_DIM = new Color(160, 160, 160);
	private static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);
	private static final Color KC_COLOR = new Color(215, 215, 215);
	private static final Color FOUR_TWENTY_GREEN = new Color(30, 200, 30);
	private static final Color HAMBURGER_COLOR = new Color(70, 70, 70);
	private static final Color HAMBURGER_HOVER_COLOR = new Color(96, 96, 96);
	private static final String SYNC_NOTICE = "Open Collection Log and click";
	private static final int SYNC_ICON_SIZE = 12;

	/** Info bar text color — only applies when highlighter is active AND clog data exists. */
	private Color getInfoColor()
	{
		return config.completionistHighlighter() && clogResult != null
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
				hiscoreResult != null ? hiscoreResult.getOverallRank() : -1,
				getCapeImage(),
				LookupQueries.getAccountBadge(hiscoreResult),
				LookupQueries.getAccountLabel(hiscoreResult),
				LookupQueries.getPrestige(hiscoreResult)
			);
			if (clogResult != null)
			{
				List<Integer> allPets = clogResult.getCategoryItems().get("all_pets");
				Set<Integer> obtainedPets = LookupQueries.getObtainedPetIds(clogResult);
				tip.setPets(allPets, obtainedPets, itemManager);
			}
			return tip;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (!Boolean.TRUE.equals(getClientProperty("underlined")))
			{
				return;
			}
			String text = getText();
			if (text == null || text.isBlank())
			{
				return;
			}
			FontMetrics fm = g.getFontMetrics();
			int iconOffset = getIcon() != null ? getIcon().getIconWidth() + getIconTextGap() : 0;
			int textStart = getInsets().left + iconOffset;
			int textWidth = fm.stringWidth(text.trim());
			int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 + 1;
			g.setColor(getForeground());
			g.drawLine(textStart, y, textStart + textWidth, y);
		}
	};
	private final JLabel clogInfoLabel = new JLabel()
	{
		@Override
		public JToolTip createToolTip()
		{
			ClogSummaryTooltip tip = new ClogSummaryTooltip();
			tip.setComponent(this);
			if (clogResult != null)
			{
				int[] totals = ClogHelper.sumClogTotals(clogResult);
				Map<String, BufferedImage> icons = new LinkedHashMap<>();
				for (Map.Entry<String, ImageIcon> entry : clogTierIcons.entrySet())
				{
					icons.put(entry.getKey(), ClogHelper.iconToImage(entry.getValue()));
				}
				tip.setTierData(totals[0], totals[1], icons);
				boolean stale = LookupQueries.isSyncStale(clogLastChanged, 90);
				String sync = LookupQueries.syncLine(clogLastChanged, stale);
				if (sync != null) tip.setSyncData(sync, stale);
				tip.setRecentItems(LookupQueries.getRecentItems(clogResult, 4), itemManager);
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
					tip.setNotice(hiscoreResult != null
						? "No TempleOSRS Data" : "Nothing to see here! (Search for a player)");
				}
			}
			return tip;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (!Boolean.TRUE.equals(getClientProperty("underlined")))
			{
				return;
			}
			String text = getText();
			if (text == null || text.isBlank())
			{
				return;
			}
			FontMetrics fm = g.getFontMetrics();
			int textWidth = fm.stringWidth(text.trim());
			int textEnd = getWidth() - getInsets().right;
			int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2 + 1;
			g.setColor(getForeground());
			g.drawLine(textEnd - textWidth, y, textEnd, y);
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
	private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, JLabel> activityLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> dimmedIcons = new LinkedHashMap<>();
	private JLabel pvpSummaryCell;
	private final BufferedImage[] pvpActivityIcons = new BufferedImage[5];
	private final BufferedImage[] clueIcons = new BufferedImage[8];
	// Activities tray
	private JPanel activitiesGrid;
	private JPanel activitiesClip;
	private JPanel activitySeparator;
	private JLabel trayToggle;
	private JLabel combatCell;
	private JLabel totalLvlCell;
	private boolean activitiesExpanded;
	private Timer slideTimer;

	private final Map<HiscoreSkill, JLabel> clueTierLabels = new LinkedHashMap<>();
	private JLabel thirdAgeCell;
	private JLabel gildedCell;
	private JLabel hardRare;
	private JLabel eliteRare;
	private JLabel masterRare;
	private TooltipData thirdAgeTooltipData;
	private TooltipData gildedTooltipData;
	// Current lookup state
	private HiscoreResult hiscoreResult;
	private ClogResult clogResult;
	private String rsn;
	private String clogLastChanged;
	private String localRsn;
	private AccountType localAccountType;

	private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
	private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

	// Lookup versioning — prevents stale results from overwriting fresher ones
	private volatile int lookupVersion = 0;
	private volatile boolean lookupInFlight = false;

	private final TooltipController tooltipController;

	// 420 mode — unlocked when the 420 KC plugin is loaded
	private NameAutocompleter nameAutocompleter;
	private FourTwentyMode fourTwentyMode = FourTwentyMode.OFF;
	private boolean has420Plugin;
	private JLabel thermy;

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

		NativeTooltip.loadSprites(spriteManager);
		SkillsTooltip.loadIcons(skillIconManager);

		activitiesExpanded = config.activitiesExpanded();

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
		activitiesGrid = buildActivitiesGrid();
		activitiesClip = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getPreferredSize()
			{
				int h;
				if (slideTimer != null && slideTimer.isRunning())
				{
					h = super.getPreferredSize().height;
				}
				else if (activitiesExpanded)
				{
					h = activitiesGrid.getPreferredSize().height;
				}
				else
				{
					h = 0;
				}
				return new Dimension(activitiesGrid.getPreferredSize().width, h);
			}
		};
		activitiesClip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		activitiesClip.add(activitiesGrid, BorderLayout.NORTH);
		activitiesClip.setPreferredSize(new Dimension(0,
			activitiesExpanded ? activitiesGrid.getPreferredSize().height : 0));
		activitiesClip.setVisible(activitiesExpanded);
		add(activitiesClip, c);

		// Separator between activities and boss grid
		c.gridy++;
		activitySeparator = new JPanel();
		activitySeparator.setBackground(ColorScheme.DARK_GRAY_COLOR);
		activitySeparator.setPreferredSize(new Dimension(0, 7));
		activitySeparator.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		Color sepNormal = ColorScheme.DARK_GRAY_COLOR;
		Color sepHover = new Color(46, 46, 46);
		activitySeparator.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggleActivities();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				activitySeparator.setBackground(sepHover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				activitySeparator.setBackground(sepNormal);
			}
		});
		activitySeparator.setVisible(activitiesExpanded);
		add(activitySeparator, c);

		c.gridy++;
		add(buildBossGrid(), c);

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
			bossLabels, activityLabels, clueTierLabels,
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
		refreshLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

		// searchBar hover → show dim refresh icon
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
		playerName.addMouseListener(infoBarClickHandler);
		clogInfoLabel.addMouseListener(infoBarClickHandler);

		for (JLabel barLabel : new JLabel[]{playerName, clogInfoLabel})
		{
			barLabel.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					if (barLabel.getToolTipText() != null)
					{
						barLabel.putClientProperty("underlined", true);
						barLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
						barLabel.repaint();
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					barLabel.putClientProperty("underlined", null);
					barLabel.setCursor(Cursor.getDefaultCursor());
					barLabel.repaint();
				}
			});
		}

		trayToggle = new JLabel();
		ImageIcon hamburgerIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(HAMBURGER_COLOR));
		ImageIcon hamburgerHoverIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(HAMBURGER_HOVER_COLOR));
		trayToggle.setIcon(hamburgerIcon);
		trayToggle.setHorizontalAlignment(JLabel.CENTER);
		trayToggle.setVerticalAlignment(JLabel.CENTER);
		trayToggle.setPreferredSize(new Dimension(30, 18));
		trayToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		trayToggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggleActivities();
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
					clueIcons[idx] = ImageUtil.resizeImage(
						ImageUtil.resizeCanvas(img, 25, 25), 13, 13));
			}
			loadItemImage(23184, img ->
				clueIcons[7] = ImageUtil.resizeImage(
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
						clueIcons[0] = ImageUtil.resizeImage(
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

	private void toggleActivities()
	{
		if (slideTimer != null && slideTimer.isRunning())
		{
			slideTimer.stop();
		}

		int startHeight = activitiesClip.getPreferredSize().height;
		activitiesExpanded = !activitiesExpanded;
		configManager.setConfiguration("killclog", "activitiesExpanded", activitiesExpanded);

		int targetHeight = activitiesExpanded ? activitiesGrid.getPreferredSize().height : 0;
		activitySeparator.setVisible(activitiesExpanded);
		if (activitiesExpanded)
		{
			activitiesClip.setVisible(true);
		}

		long duration = 150;
		long startTime = System.currentTimeMillis();

		slideTimer = new Timer(12, null);
		slideTimer.addActionListener(e ->
		{
			long elapsed = System.currentTimeMillis() - startTime;
			float progress = Math.min(1f, (float) elapsed / duration);
			// Ease-out: 1 - (1-t)^2
			float eased = 1f - (1f - progress) * (1f - progress);
			int height = startHeight + Math.round((targetHeight - startHeight) * eased);
			activitiesClip.setPreferredSize(new Dimension(0, height));
			revalidate();

			if (progress >= 1f)
			{
				slideTimer.stop();
				if (!activitiesExpanded)
				{
					activitiesClip.setVisible(false);
					activitySeparator.setVisible(false);
				}
			}
		});
		slideTimer.start();
	}

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
					hiscoreResult != null ? hiscoreResult.getCombatLevel() : 0,
					LookupQueries.sumBossKills(hiscoreResult),
					LookupQueries.countBossesWithKc(hiscoreResult),
					PanelData.BOSSES.length,
					LookupQueries.getMostKilledBoss(hiscoreResult),
					LookupQueries.getMostKilledKc(hiscoreResult)
				);
				if (clogResult != null)
				{
					tip.setCompletion(
						LookupQueries.countBossesCompleted(tooltipDataMap, bossLabels.keySet()),
						LookupQueries.countBossesWithClog(tooltipDataMap, bossLabels.keySet()));
				}
				tip.setMegarares(
					LookupQueries.getClogItemCount(clogResult, "chambers_of_xeric", 20997),
					LookupQueries.getClogItemCount(clogResult, "theatre_of_blood", 22486),
					LookupQueries.getClogItemCount(clogResult, "tombs_of_amascut", 27277),
					itemManager
				);
				if (hiscoreResult != null)
				{
					tip.setRaids(hiscoreResult, clogResult);
				}
				JPanel parentCell = (JPanel) this.getParent();
				tooltipController.keepTooltipOnHover(tip, parentCell);
				return tip;
			}
		};
		styleLabel(combatCell, "Combat");
		spriteManager.getSpriteAsync(168, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite != null)
				{
					combatCell.setIcon(new ImageIcon(ImageUtil.resizeImage(
						ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20)));
				}
			}));
		statsRow.add(wrapInCell(combatCell));

		totalLvlCell = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				SkillsTooltip tip = new SkillsTooltip();
				tip.setComponent(this);
				tip.setData(hiscoreResult);
				return tip;
			}
		};
		styleLabel(totalLvlCell, "Total");
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
		statsRow.add(wrapInCell(totalLvlCell));
		statsRow.add(makePvpSummaryCell());
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
		row1.add(makeClueRareCell("3rd Age", PanelData.THIRD_AGE_ITEM_ID, PanelData.CLOG_THIRD_AGE, true));
		row1.add(makeActivityCell(HiscoreSkill.CLUE_SCROLL_ALL));
		row1.add(makeClueRareCell("Gilded", PanelData.GILDED_ITEM_ID, PanelData.CLOG_GILDED, false));
		grid.add(row1);

		// Clue row 2: Custom rare cells (casket icons)
		JPanel rareRow = new JPanel(new GridLayout(1, 3));
		rareRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rareRow.setAlignmentX(0f);
		rareRow.add(makeCustomRareCell("Hard Treasure (Rare)", 20544, PanelData.RARE_HARD, PanelData.HARD_RARE_ITEMS));
		rareRow.add(makeCustomRareCell("Elite Treasure (Rare)", 20543, PanelData.RARE_ELITE, PanelData.ELITE_RARE_ITEMS));
		rareRow.add(makeCustomRareCell("Master Treasure (Rare)", 19836, PanelData.RARE_MASTER, PanelData.MASTER_RARE_ITEMS));
		grid.add(rareRow);

		// Clue rows 3-4: Clue tiers
		JPanel clueRow1 = new JPanel(new GridLayout(1, 3));
		clueRow1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow1.setAlignmentX(0f);
		clueRow1.add(makeClueTierCell(PanelData.CLUE_TIERS[0], PanelData.CLUE_TIER_ITEM_IDS[0], false));
		clueRow1.add(makeClueTierCell(PanelData.CLUE_TIERS[1], PanelData.CLUE_TIER_ITEM_IDS[1], true));
		clueRow1.add(makeClueTierCell(PanelData.CLUE_TIERS[2], PanelData.CLUE_TIER_ITEM_IDS[2], true));
		grid.add(clueRow1);

		JPanel clueRow2 = new JPanel(new GridLayout(1, 3));
		clueRow2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow2.setAlignmentX(0f);
		clueRow2.add(makeClueTierCell(PanelData.CLUE_TIERS[3], PanelData.CLUE_TIER_ITEM_IDS[3], true));
		clueRow2.add(makeClueTierCell(PanelData.CLUE_TIERS[4], PanelData.CLUE_TIER_ITEM_IDS[4], false));
		clueRow2.add(makeClueTierCell(PanelData.CLUE_TIERS[5], PanelData.CLUE_TIER_ITEM_IDS[5], false));
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
			String lookupPlayer = searchBar.getText().trim();
			boolean isSelfNoCache = hiscoreResult != null && localRsn != null
				&& localRsn.equalsIgnoreCase(lookupPlayer);
			if (isSelfNoCache)
			{
				tip.setNotice(SYNC_NOTICE, getSyncIcon());
			}
			else
			{
				tip.setNotice(hiscoreResult != null
					? "No TempleOSRS Data"
					: "Nothing to see here! (Search for a player)");
			}
		}

		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	/** Apply standard grid cell styling — font, placeholder text, color, gap, AA hint. */
	private static void styleLabel(JLabel label, String tooltipText)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setText(ClogHelper.pad("--"));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setIconTextGap(4);
		label.setToolTipText(tooltipText);
		label.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	/** Wrap a label in a standard grid cell panel. */
	private JPanel wrapInCell(JLabel label)
	{
		JPanel cell = new JPanel();
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(TooltipController.CELL_BORDER);
		cell.add(label);
		tooltipController.addCellHoverEffect(cell, label);
		return cell;
	}

	// -------------------------------------------------------------------------
	// Cell factories
	// -------------------------------------------------------------------------

	private JPanel makeActivityCell(HiscoreSkill activity)
	{
		JLabel label = new JLabel(activity.getName())
		{
			@Override
			public JToolTip createToolTip()
			{
				if (activity == HiscoreSkill.CLUE_SCROLL_ALL)
				{
					ClueSummaryTooltip tip = new ClueSummaryTooltip();
					tip.setComponent(this);
					tip.setIcons(clueIcons);
					if (hiscoreResult != null)
					{
						tip.setData(hiscoreResult);
					}
					else
					{
						tip.setNotice("Nothing to see here! (Search for a player)");
					}
					JPanel parentCell = (JPanel) this.getParent();
					tooltipController.keepTooltipOnHover(tip, parentCell);
					return tip;
				}
				return makeSpriteTooltip(this, tooltipDataMap.get(activity), 5, activity.getName());
			}
		};
		styleLabel(label, activity.getName());

		if (activity.getSpriteId() != -1)
		{
			spriteManager.getSpriteAsync(activity.getSpriteId(), 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						ImageIcon icon = new ImageIcon(ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20));
						label.setIcon(icon);
					}
				}));
		}

		activityLabels.put(activity, label);
		return wrapInCell(label);
	}

	private JPanel makePvpSummaryCell()
	{
		pvpSummaryCell = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				PvpSummaryTooltip tip = new PvpSummaryTooltip();
				tip.setComponent(this);
				tip.setIcons(pvpActivityIcons);
				if (hiscoreResult != null)
				{
					tip.setData(hiscoreResult, clogResult);
				}
				else
				{
					tip.setNotice("Nothing to see here! (Search for a player)");
				}
				JPanel parentCell = (JPanel) this.getParent();
				tooltipController.keepTooltipOnHover(tip, parentCell);
				return tip;
			}
		};
		styleLabel(pvpSummaryCell, "PvP Summary");

		// Cell icon — PK skull
		spriteManager.getSpriteAsync(439, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite != null)
				{
					pvpSummaryCell.setIcon(new ImageIcon(ImageUtil.resizeCanvas(
						ImageUtil.resizeImage(sprite, 16, 16), 20, 20)));
				}
			}));

		// Tooltip icons — one per PvP activity
		for (int i = 0; i < PanelData.PVP_ACTIVITIES.length; i++)
		{
			int spriteId = PanelData.PVP_ACTIVITIES[i].getSpriteId();
			if (spriteId == -1) continue;
			final int idx = i;
			spriteManager.getSpriteAsync(spriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						pvpActivityIcons[idx] = ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
					}
				}));
		}

		return wrapInCell(pvpSummaryCell);
	}

	private JPanel makeClueTierCell(HiscoreSkill tier, int itemId, boolean compact)
	{
		String displayName = capitalizeTier(tier);
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return makeSpriteTooltip(this, tooltipDataMap.get(tier), compact ? 10 : 5, displayName, compact);
			}
		};
		styleLabel(label, tier.getName());

		setItemIcon(label, itemId);
		clueTierLabels.put(tier, label);
		return wrapInCell(label);
	}

	private JPanel makeClueRareCell(String name, int itemId, String clogCategory, boolean isThirdAge)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				TooltipData data = isThirdAge ? thirdAgeTooltipData : gildedTooltipData;
				return makeSpriteTooltip(this, data, 5, name);
			}
		};
		styleLabel(label, name);

		setItemIcon(label, itemId);

		if (isThirdAge) thirdAgeCell = label;
		else gildedCell = label;

		return wrapInCell(label);
	}

	/**
	 * Create a cell for a custom rare category (Hard/Elite/Master Rare).
	 * These categories don't exist in Temple — item IDs are hardcoded from the native clog.
	 */
	private JPanel makeCustomRareCell(String name, int iconItemId, String rareKey, int[] itemIds)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return makeSpriteTooltip(this, rareTooltips.get(rareKey), 5, name);
			}
		};
		styleLabel(label, name);

		setItemIcon(label, iconItemId);

		if (PanelData.RARE_HARD.equals(rareKey)) hardRare = label;
		else if (PanelData.RARE_ELITE.equals(rareKey)) eliteRare = label;
		else if (PanelData.RARE_MASTER.equals(rareKey)) masterRare = label;

		return wrapInCell(label);
	}

	/** Set a label's icon from an item image, with async repaint on load. */
	private void setItemIcon(JLabel label, int itemId)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		if (img == null)
		{
			return;
		}
		label.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 20, 20)));
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() ->
					label.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 20, 20)))));
		}
	}

	private JPanel buildBossGrid()
	{
		JPanel grid = new JPanel(new GridLayout(0, 3));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			grid.add(makeBossCell(boss));
		}
		return grid;
	}

	private JPanel makeBossCell(HiscoreSkill boss)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return makeSpriteTooltip(this, tooltipDataMap.get(boss), 5, boss.getName());
			}
		};
		// Force greyscale AA — resolves to LCD subpixel on Windows without this
		styleLabel(label, boss.getName());

		spriteManager.getSpriteAsync(boss.getSpriteId(), 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite == null) return;
				BufferedImage scaled = ImageUtil.resizeImage(
					ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20);
				ImageIcon icon = new ImageIcon(scaled);
				label.setIcon(icon);
				originalIcons.put(boss, icon);
				dimmedIcons.put(boss, new ImageIcon(ClogHelper.createDimmedImage(icon)));
			}));

		bossLabels.put(boss, label);

		// Secret 420 mode toggle on Thermonuclear Smoke Devil
		if (boss == HiscoreSkill.THERMONUCLEAR_SMOKE_DEVIL)
		{
			thermy = label;
			label.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (has420Plugin) cycleFourTwentyMode();
				}
			});
		}

		return wrapInCell(label);
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
		if (player.isEmpty() || lookupInFlight)
		{
			if (!lookupInFlight)
			{
				setSearchStatus("Enter RSN", TEXT_DIM);
			}
			return;
		}

		lookupInFlight = true;
		final int thisLookup = ++lookupVersion;
		final boolean isSelf = localRsn != null && localRsn.equalsIgnoreCase(player);
		final boolean isFirstSelfGreeting = isSelf && !config.seenSelfGreeting();

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

		// Hiscore lookup — pass known account type for self-lookups
		AccountType knownType = (localRsn != null && localRsn.equalsIgnoreCase(player))
			? localAccountType : null;
		hiscoreService.lookup(player, knownType).thenAccept(result ->
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupVersion) return;
				lookupInFlight = false;
				searchBar.setIcon(IconTextField.Icon.SEARCH);

				if (result == null)
				{
					int notFoundIdx = ThreadLocalRandom.current().nextInt(SearchMessages.NOT_FOUND.length);
					setSearchStatus(String.format(SearchMessages.NOT_FOUND[notFoundIdx], player), NOT_FOUND);
					playerName.setText(" ");
					playerName.setIcon(null);
					playerName.setToolTipText(null);
					clogInfoLabel.setText("");
					clogInfoLabel.setIcon(null);
					clogInfoLabel.setToolTipText(null);
					searchBar.setText("");
					return;
				}

				hiscoreResult = result;
				if (nameAutocompleter != null)
				{
					nameAutocompleter.addToSearchHistory(player);
				}

				if (!isFirstSelfGreeting)
				{
					setSearchStatus(" ", TEXT_DIM);
				}
				playerName.setText(rsn != null ? rsn : player);
				playerName.setForeground(getInfoColor());
				updateInfoIcon(result.getAccountType());
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

				if (clogResult != null)
				{
					updateRares(clogResult);
					updateClogCell(clogResult);
				}

				searchBar.setText("");
				toggleHighlighter(config.completionistHighlighter());
				updateTooltips();
			})
		).exceptionally(ex ->
		{
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupVersion) return;
				lookupInFlight = false;
				searchBar.setIcon(IconTextField.Icon.SEARCH);
				searchBar.setText("");
				setSearchStatus("Lookup failed", TEXT_DIM);
				playerName.setText(" ");
				playerName.setIcon(null);
				playerName.setToolTipText(null);
				clogInfoLabel.setText("");
				clogInfoLabel.setIcon(null);
				clogInfoLabel.setToolTipText(null);
			});
			return null;
		});

		// Clog lookup in parallel (ClogService handles source routing)
		clogService.lookup(player).thenAccept(result ->
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupVersion) return;
				clogResult = result;
				clogLastChanged = result != null ? result.getLastChanged() : null;
				toggleHighlighter(config.completionistHighlighter());
				updateTooltips();

				if (result != null)
				{
					String name = result.getPlayerName();
					if (name != null && !name.isEmpty())
					{
						rsn = name;
						if (hiscoreResult != null)
						{
							playerName.setText(name);
						}
					}
					lookupItemNames(result);
					if (hiscoreResult != null)
					{
						updateRares(result);
						updateClogCell(result);
					}
				}
				else
				{
					if (isSelf)
					{
						clogNotice.setText(SYNC_NOTICE);
						clogNotice.setIcon(new ImageIcon(getSyncIcon()));
						BufferedImage icon = ImageUtil.loadImageResource(
							KillClogPlugin.class, "icon.png");
						clogInfoLabel.setIcon(new ImageIcon(
							ImageUtil.resizeImage(icon, 15, 15)));
						clogInfoLabel.setText(ClogHelper.pad("Sync"));
						clogInfoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
						clogInfoLabel.setToolTipText(" ");
					}
					else
					{
						clogNotice.setText(" ");
						clogNotice.setIcon(null);
					}
					fetchRsn(player, thisLookup);
				}
			})
		).exceptionally(ex ->
		{
			log.warn("Clog lookup failed", ex);
			return null;
		});
	}

	/**
	 * Reset all labels, maps, and fields to their pre-lookup state.
	 * Called at the start of every lookup to ensure a clean slate.
	 */
	private void resetAllLabels()
	{
		tooltipController.hideClickTooltip();
		hiscoreResult = null;
		clogResult = null;
		clogLastChanged = null;
		rsn = null;
		clogNotice.setText(" ");
		clogNotice.setIcon(null);
		tooltipDataMap.clear();
		rareTooltips.clear();
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			JLabel label = entry.getValue();
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setToolTipText(" ");
			ImageIcon orig = originalIcons.get(entry.getKey());
			if (orig != null) label.setIcon(orig);
		}

		resetLabelMap(activityLabels);

		playerName.setText(" ");
		playerName.setIcon(null);
		playerName.setToolTipText(null);
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
		if (pvpSummaryCell != null)
		{
			pvpSummaryCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}

		resetLabelMap(clueTierLabels);

		resetRareCell(thirdAgeCell, "3rd Age");
		resetRareCell(gildedCell, "Gilded");
		resetRareCell(hardRare, "Hard Treasure (Rare)");
		resetRareCell(eliteRare, "Elite Treasure (Rare)");
		resetRareCell(masterRare, "Master Treasure (Rare)");
		thirdAgeTooltipData = null;
		gildedTooltipData = null;
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
				if (thisLookup != lookupVersion) return;
				if (name != null && !name.isEmpty())
				{
					rsn = name;
					if (hiscoreResult != null)
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

	private void updateActivities(HiscoreResult result)
	{
		for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
		{
			HiscoreSkill activity = entry.getKey();
			JLabel label = entry.getValue();
			int score = result.getActivityScore(activity.getName());

			label.setText(ClogHelper.pad(score <= 0 ? "--" : ClogHelper.formatKc(score)));
			label.setForeground(score > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

			if (activity == HiscoreSkill.CLUE_SCROLL_ALL)
			{
				label.setToolTipText(" ");
			}
			else
			{
				int rank = result.getActivityRank(activity.getName());
				label.setToolTipText(rank > 0
					? activity.getName() + "\nRank: {w}" + String.format("%,d", rank)
					: activity.getName());
			}
		}

		// PvP summary cell — BH Hunter + BH Rogue total
		if (pvpSummaryCell != null)
		{
			int bhTotal = Math.max(0, result.getActivityScore("Bounty Hunter - Hunter"))
				+ Math.max(0, result.getActivityScore("Bounty Hunter - Rogue"));
			pvpSummaryCell.setText(ClogHelper.pad(bhTotal > 0 ? ClogHelper.formatKc(bhTotal) : "--"));
		}

		for (Map.Entry<HiscoreSkill, JLabel> entry : clueTierLabels.entrySet())
		{
			HiscoreSkill tier = entry.getKey();
			JLabel label = entry.getValue();
			int score = result.getActivityScore(tier.getName());
			label.setText(ClogHelper.pad(score <= 0 ? "--" : ClogHelper.formatKc(score)));
			label.setForeground(score > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

			String shortName = capitalizeTier(tier);
			int rank = result.getActivityRank(tier.getName());
			label.setToolTipText(rank > 0
				? shortName + "\nRank: {w}" + String.format("%,d", rank)
				: shortName);
		}
	}

	/** "Clue Scrolls (hard)" → "Hard" */
	private static String capitalizeTier(HiscoreSkill tier)
	{
		String name = tier.getName().replace("Clue Scrolls (", "").replace(")", "");
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private void updateBosses(HiscoreResult result)
	{
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			JLabel label = entry.getValue();
			String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(skill.getName(), skill.getName());
			int kc = result.getBossKills().getOrDefault(hiscoreName, -1);
			boolean hasKc = kc > 0;

			label.setText(ClogHelper.pad(kc <= 0 ? "--" : ClogHelper.formatKc(kc)));
			label.setForeground(hasKc ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

			ImageIcon orig = originalIcons.get(skill);
			if (orig != null)
			{
				label.setIcon(hasKc ? orig : dimmedIcons.get(skill));
			}

			// 420 mode overrides
			switch (fourTwentyMode)
			{
				case GREEN_420S:
					if (kc == 420) label.setForeground(FOUR_TWENTY_GREEN);
					break;
				case CAP_420:
					if (kc > 0)
					{
						int display = Math.min(kc, 420);
						label.setText(ClogHelper.pad(ClogHelper.formatKc(display)));
						if (display == 420) label.setForeground(FOUR_TWENTY_GREEN);
					}
					break;
				case ALL_420:
					if (kc > 0)
					{
						label.setText(ClogHelper.pad("420"));
						label.setForeground(FOUR_TWENTY_GREEN);
					}
					break;
			}
		}
	}

	private void updateRares(ClogResult result)
	{
		updateClueRare(thirdAgeCell, "3rd Age", PanelData.CLOG_THIRD_AGE, result, true);
		updateClueRare(gildedCell, "Gilded", PanelData.CLOG_GILDED, result, false);
		updateCustomRare(hardRare, "Hard Treasure (Rare)", PanelData.RARE_HARD, PanelData.HARD_RARE_ITEMS, result);
		updateCustomRare(eliteRare, "Elite Treasure (Rare)", PanelData.RARE_ELITE, PanelData.ELITE_RARE_ITEMS, result);
		updateCustomRare(masterRare, "Master Treasure (Rare)", PanelData.RARE_MASTER, PanelData.MASTER_RARE_ITEMS, result);
	}

	private void updateClueRare(JLabel label, String name, String clogCategory,
									ClogResult result, boolean isThirdAge)
	{
		if (label == null) return;

		TooltipData data = tooltipDataBuilder.buildClueRareData(name, clogCategory, result);
		if (data == null)
		{
			label.setToolTipText(name);
			return;
		}

		label.setText(ClogHelper.pad(data.obtainedCount > 0 ? ClogHelper.formatKc(data.obtainedCount) : "--"));
		label.setForeground(data.obtainedCount > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

		if (isThirdAge) thirdAgeTooltipData = data;
		else gildedTooltipData = data;

		label.setToolTipText(" ");
	}

	private void updateCustomRare(JLabel label, String name, String rareKey,
		int[] itemIds, ClogResult result)
	{
		if (label == null) return;

		TooltipData data = tooltipDataBuilder.buildCustomRareData(name, itemIds, result);

		label.setText(ClogHelper.pad(data.obtainedCount > 0 ? ClogHelper.formatKc(data.obtainedCount) : "--"));
		label.setForeground(data.obtainedCount > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

		rareTooltips.put(rareKey, data);

		label.setToolTipText(" ");
	}

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

	private void updateTooltips()
	{
		tooltipDataMap.clear();

		// Boss cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			JLabel label = entry.getValue();
			String bossName = skill.getName();
			String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(bossName, bossName);

			int kc = hiscoreResult != null ? hiscoreResult.getKc(hiscoreName) : -1;
			int rank = hiscoreResult != null ? hiscoreResult.getRank(hiscoreName) : -1;

			String category = ClogService.bossToCategory(hiscoreName);

			if (clogResult == null)
			{
				boolean selfNoCache = localRsn != null
					&& localRsn.equalsIgnoreCase(searchBar.getText().trim());
				if (!selfNoCache)
				{
					int total = clogService.getCategoryItemCount(category);
					tooltipDataMap.put(skill, new TooltipData(
						bossName, rank, -1, Math.max(total, 0),
						Collections.emptyList(),
						Collections.emptySet(),
						Collections.emptyMap()));
				}
				label.setToolTipText(" ");
				continue;
			}

			TooltipData data = tooltipDataBuilder.buildTooltipData(bossName, category, rank, clogResult);
			if (data == null)
			{
				int total = clogService.getCategoryItemCount(category);
				tooltipDataMap.put(skill, new TooltipData(
					bossName, rank, -1, Math.max(total, 0),
					Collections.emptyList(),
					Collections.emptySet(),
					Collections.emptyMap()));
				label.setToolTipText(" ");
				continue;
			}

			tooltipDataMap.put(skill, data);
			tooltipDataBuilder.preloadItemImages(data);
			label.setToolTipText(" ");
		}

		// Clue tier cells with clog categories
		rebuildActivityTooltips(PanelData.CLUE_CATEGORIES, clueTierLabels, KillClogPanel::capitalizeTier);
	}

	private void rebuildActivityTooltips(Map<HiscoreSkill, String> categories,
		Map<HiscoreSkill, JLabel> labels,
		Function<HiscoreSkill, String> nameOf)
	{
		if (clogResult == null) return;
		for (Map.Entry<HiscoreSkill, String> entry : categories.entrySet())
		{
			HiscoreSkill skill = entry.getKey();
			JLabel label = labels.get(skill);
			if (label == null) continue;

			int rank = hiscoreResult != null ? hiscoreResult.getActivityRank(skill.getName()) : -1;
			TooltipData data = tooltipDataBuilder.buildTooltipData(nameOf.apply(skill), entry.getValue(), rank, clogResult);
			if (data == null) continue;

			tooltipDataMap.put(skill, data);
			tooltipDataBuilder.preloadItemImages(data);
			label.setToolTipText(" ");
		}
	}

	// -------------------------------------------------------------------------
	// Progress highlighter
	// -------------------------------------------------------------------------

	private void toggleHighlighter(boolean enabled)
	{
		if (hiscoreResult == null) return;
		tooltipController.clearHoveredCell();

		// Info bar follows highlighter state
		Color infoColor = getInfoColor();
		playerName.setForeground(infoColor);
		clogInfoLabel.setForeground(infoColor);
		if (hiscoreResult.getCombatLevel() > 0)
		{
			combatCell.setForeground(infoColor);
		}
		if (hiscoreResult.getTotalLevel() > 0)
		{
			totalLvlCell.setForeground(infoColor);
		}
		if (pvpSummaryCell != null)
		{
			int bhTotal = Math.max(0, hiscoreResult.getActivityScore("Bounty Hunter - Hunter"))
				+ Math.max(0, hiscoreResult.getActivityScore("Bounty Hunter - Rogue"));
			if (bhTotal > 0)
			{
				pvpSummaryCell.setForeground(infoColor);
			}
		}

		updateBosses(hiscoreResult);
		updateActivities(hiscoreResult);
		if (clogResult != null)
		{
			updateRares(clogResult);
			if (enabled)
			{
				Map<String, JLabel> rareCells = new LinkedHashMap<>();
				rareCells.put(PanelData.CLOG_THIRD_AGE, thirdAgeCell);
				rareCells.put(PanelData.CLOG_GILDED, gildedCell);
				rareCells.put(PanelData.RARE_HARD, hardRare);
				rareCells.put(PanelData.RARE_ELITE, eliteRare);
				rareCells.put(PanelData.RARE_MASTER, masterRare);
				highlighter.colorCellsByCompletion(hiscoreResult, clogResult,
					rareTooltips, rareCells, fourTwentyMode, FOUR_TWENTY_GREEN);
				highlighter.colorEmptyCells();
			}
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

	public void onBulkCaptureComplete(String playerName)
	{
		searchBar.setText(playerName);
		doLookup();
	}

	public void setNameAutocompleter(NameAutocompleter autocompleter)
	{
		this.nameAutocompleter = autocompleter;
		for (Component c : searchBar.getComponents())
		{
			if (c instanceof FlatTextField)
			{
				((FlatTextField) c).getTextField()
					.addKeyListener(autocompleter);
				break;
			}
		}
	}

	public void setPluginManager(PluginManager pluginManager)
	{
		has420Plugin = pluginManager.getPlugins().stream()
			.anyMatch(p -> p.getClass().getSimpleName().equals("FourTwentyKcPlugin"));
		if (has420Plugin && thermy != null)
		{
			thermy.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}
	}

	public void setFourTwentyVisible(boolean visible)
	{
		has420Plugin = visible;
		if (thermy != null)
		{
			thermy.setCursor(visible
				? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
				: Cursor.getDefaultCursor());
		}
		if (!visible)
		{
			fourTwentyMode = FourTwentyMode.OFF;
			if (hiscoreResult != null) toggleHighlighter(config.completionistHighlighter());
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
				updateTooltips();
				break;
			case "hoverStyle":
			case "tooltipMode":
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
		if (hiscoreResult == null) return null;
		boolean maxed = hiscoreResult.getTotalLevel() >= PanelData.MAX_TOTAL_LEVEL;
		boolean infernal = hiscoreResult.getKc("TzKal-Zuk") > 0;
		if (maxed && infernal) return infernalMaxCapeTip;
		if (maxed) return maxCapeTip;
		if (infernal) return infernalCapeTip;
		return null;
	}

	private void updateInfoIcon(AccountType type)
	{
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
			SwingUtilities.invokeLater(this::updateTooltips);
		});
	}

}
