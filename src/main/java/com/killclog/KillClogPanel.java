package com.killclog;

import java.awt.BorderLayout;
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
	static final Color KC_COLOR = new Color(215, 215, 215);
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
			if (clogResult != null)
			{
				int[] totals = ClogHelper.sumClogTotals(clogResult);
				Map<String, BufferedImage> icons = new LinkedHashMap<>();
				for (Map.Entry<String, ImageIcon> entry : clogTierIcons.entrySet())
				{
					icons.put(entry.getKey(), ClogHelper.iconToImage(entry.getValue()));
				}
				tip.setTierData(totals[0], totals[1], icons);
				if (hiscoreResult != null)
				{
					int clogRank = hiscoreResult.getActivityRank("Collections Logged");
					tip.setRank(clogRank);
				}
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
	// Current lookup state
	private HiscoreResult hiscoreResult;
	private ClogResult clogResult;
	private String rsn;
	private String currentLookupRsn;
	private String clogLastChanged;
	private String localRsn;
	private AccountType localAccountType;

	private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
	private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

	// Lookup versioning — prevents stale results from overwriting fresher ones
	private volatile int lookupVersion = 0;
	private volatile boolean lookupInFlight = false;

	private final TooltipController tooltipController;

	// Comparison mode
	static final Color COMPARE_BLUE = new Color(91, 164, 207);
	static final Color COMPARE_RED = new Color(224, 86, 86);
	private static final String COMPARE_BLUE_HEX = String.format("#%06x", COMPARE_BLUE.getRGB() & 0xFFFFFF);
	private static final String COMPARE_RED_HEX = String.format("#%06x", COMPARE_RED.getRGB() & 0xFFFFFF);
	private boolean comparisonMode;
	private HiscoreResult compareHiscoreResult;
	private ClogResult compareClogResult;
	private String compareRsn;
	private volatile int compareLookupVersion = 0;
	private volatile boolean compareLookupInFlight = false;
	private final Map<HiscoreSkill, TooltipData> compareTooltipDataMap = new LinkedHashMap<>();
	private final IconTextField compareSearchBar = new IconTextField();
	private JTextField compareTextField;
	private String comparePlaceholder = "Comparison";
	private JPanel comparePanel;
	private final JLabel compareStatus = new JLabel(" ");
	private JLabel compareToggle;

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

		// Compare toggle — split blue/red magnifying glass, hidden until a player is looked up
		c.gridy++;
		c.insets = new Insets(4, 0, 0, 0);
		ImageIcon compareOff = new ImageIcon(ClogHelper.makeCompareIcon(COMPARE_BLUE, COMPARE_RED, 0.55f));
		ImageIcon compareOn = new ImageIcon(ClogHelper.makeCompareIcon(COMPARE_BLUE, COMPARE_RED, 1.0f));
		compareToggle = new JLabel(compareOff);
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
				boolean show = !comparePanel.isVisible();
				comparePanel.setVisible(show);
				if (!show && comparisonMode)
				{
					exitComparisonMode();
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
		comparePanel = buildCompareSearch();
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
		playerName.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (comparisonMode)
				{
					exitComparisonMode();
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
				if (comparisonMode && compareRsn != null)
				{
					swapToComparePlayer();
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
						|| (comparisonMode && (barLabel == clogInfoLabel || barLabel == playerName)))
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

		trayToggle = new JLabel();
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
			boolean isSelfNoCache = hiscoreResult != null && localRsn != null
				&& localRsn.equalsIgnoreCase(currentLookupRsn);
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

	/**
	 * Build a comparison tooltip showing both players' sprite grids stacked.
	 * Falls back to single-player tooltip when not in comparison mode.
	 */
	private JToolTip makeCompareSpriteTooltip(JLabel owner, TooltipData blueData,
		TooltipData redData, String name)
	{
		JPanel parentCell = (JPanel) owner.getParent();
		CompareImgTooltip tip = new CompareImgTooltip();
		tip.setComponent(owner);
		tip.setTitle(name);

		String blueName = playerName.getText().trim();
		if (blueName.isEmpty()) blueName = "Blue";
		String redName = compareRsn != null ? compareRsn : "Red";

		boolean blueHas = blueData != null && blueData.allItemIds != null
			&& !blueData.allItemIds.isEmpty();
		boolean redHas = redData != null && redData.allItemIds != null
			&& !redData.allItemIds.isEmpty();

		tip.setBluePlayer(blueName,
			blueData != null ? blueData.obtainedCount : -1,
			blueData != null ? blueData.totalItems : 0,
			blueData != null ? blueData.rank : 0);
		tip.setRedPlayer(redName,
			redData != null ? redData.obtainedCount : -1,
			redData != null ? redData.totalItems : 0,
			redData != null ? redData.rank : 0);
		tip.setBlueHasData(blueHas);
		tip.setRedHasData(redHas);

		// Use whichever data source has items for the sprite grid
		List<Integer> allItemIds = blueHas ? blueData.allItemIds
			: (redHas ? redData.allItemIds : null);
		tip.setItems(allItemIds,
			blueData != null ? blueData.obtainedIds : Collections.emptySet(),
			blueData != null ? blueData.obtainedCounts : Collections.emptyMap(),
			redData != null ? redData.obtainedIds : Collections.emptySet(),
			redData != null ? redData.obtainedCounts : Collections.emptyMap(),
			itemManager);

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
				if (comparisonMode)
				{
					String category = PanelData.CLUE_CATEGORIES.get(tier);
					int redRank = compareHiscoreResult != null
						? compareHiscoreResult.getActivityRank(tier.getName()) : -1;
					TooltipData redData = compareClogResult != null
						? tooltipDataBuilder.buildTooltipData(displayName, category, redRank, compareClogResult)
						: null;
					return makeCompareSpriteTooltip(this,
						tooltipDataMap.get(tier), redData, displayName);
				}
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
				TooltipData data = rareTooltips.get(isThirdAge ? PanelData.CLOG_THIRD_AGE : PanelData.CLOG_GILDED);
				if (comparisonMode)
				{
					TooltipData redData = buildCompareClueRare(name, clogCategory);
					return makeCompareSpriteTooltip(this, data, redData, name);
				}
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
				if (comparisonMode)
				{
					TooltipData redData = buildCompareCustomRare(name, itemIds);
					return makeCompareSpriteTooltip(this,
						rareTooltips.get(rareKey), redData, name);
				}
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
				if (comparisonMode)
				{
					return makeCompareSpriteTooltip(this,
						tooltipDataMap.get(boss),
						compareTooltipDataMap.get(boss),
						boss.getName());
				}
				JToolTip tip = makeSpriteTooltip(this, tooltipDataMap.get(boss), 5, boss.getName());
				if (boss == HiscoreSkill.SOL_HEREDIT && hiscoreResult != null && tip instanceof ImgTooltip)
				{
					int glory = hiscoreResult.getActivityScore("Colosseum Glory");
					if (glory > 0)
					{
						((ImgTooltip) tip).setInfoLine("Glory: ",
							String.format("%,d", glory), Color.WHITE);
					}
				}
				return tip;
			}
		};
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
			label.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (has420Plugin && !comparisonMode) cycleFourTwentyMode();
				}
			});
		}

		return wrapInCell(label);
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

		compareStatus.setFont(FontManager.getRunescapeSmallFont());
		compareStatus.setForeground(TEXT_DIM);
		compareStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		compareStatus.setBorder(new EmptyBorder(0, 4, 2, 0));
		compareStatus.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		panel.add(compareStatus);

		compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
		compareSearchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		compareSearchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		compareSearchBar.setPreferredSize(new Dimension(0, 30));
		compareSearchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		compareSearchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		compareSearchBar.addActionListener(e -> doCompareLookup());

		ClogHelper.styleSearchBar(compareSearchBar);

		// Recolor the clear X to match comparison red
		recolorClearButton(compareSearchBar, COMPARE_RED);

		// Style inner text field: red text, placeholder
		for (Component c : compareSearchBar.getComponents())
		{
			if (c instanceof FlatTextField)
			{
				JTextField tf = ((FlatTextField) c).getTextField();
				compareTextField = tf;
				tf.setFont(FontManager.getRunescapeFont());
				tf.setCaretColor(COMPARE_RED);
				tf.putClientProperty(
					RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

				// Placeholder via focus listeners
				Color placeholderColor = ColorScheme.MEDIUM_GRAY_COLOR;
				tf.setForeground(placeholderColor);
				tf.setText(comparePlaceholder);
				tf.addFocusListener(new java.awt.event.FocusAdapter()
				{
					@Override
					public void focusGained(java.awt.event.FocusEvent e)
					{
						if (tf.getText().equals(comparePlaceholder))
						{
							tf.setText("");
						}
						tf.setForeground(COMPARE_RED);
					}

					@Override
					public void focusLost(java.awt.event.FocusEvent e)
					{
						if (tf.getText().isEmpty())
						{
							tf.setForeground(placeholderColor);
							tf.setText(comparePlaceholder);
						}
					}
				});
			}
		}

		panel.add(compareSearchBar);
		return panel;
	}

	private void exitComparisonMode()
	{
		comparisonMode = false;
		compareLookupVersion++;
		compareLookupInFlight = false;
		compareHiscoreResult = null;
		compareClogResult = null;
		compareRsn = null;
		compareTooltipDataMap.clear();
		compareSearchBar.setText("");
		compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
		compareStatus.setText(" ");
		updateAllCellsForComparison();
		updateInfoBarForComparison();
		toggleHighlighter(config.completionistHighlighter());
	}

	/** Click red name in comparison mode — swap red player into solo view instantly. */
	private void swapToComparePlayer()
	{
		HiscoreResult swapHiscore = compareHiscoreResult;
		ClogResult swapClog = compareClogResult;
		String swapName = compareRsn;

		exitComparisonMode();

		hiscoreResult = swapHiscore;
		clogResult = swapClog;
		rsn = swapName;
		currentLookupRsn = swapName;
		clogLastChanged = swapClog != null ? swapClog.getLastChanged() : null;

		playerName.setText(swapName != null ? swapName : "");
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
			updateRares(swapClog);
			updateClogCell(swapClog);
		}

		compareToggle.setVisible(true);
		refreshLabel.setVisible(true);
		setSearchStatus(" ", TEXT_DIM);
		toggleHighlighter(config.completionistHighlighter());
		updateTooltips();
	}

	private void doCompareLookup()
	{
		String player = compareSearchBar.getText().trim();
		if (player.isEmpty() || player.equals(comparePlaceholder) || compareLookupInFlight)
		{
			return;
		}

		if (hiscoreResult == null)
		{
			return;
		}

		compareLookupInFlight = true;
		final int thisLookup = ++compareLookupVersion;
		compareSearchBar.setIcon(IconTextField.Icon.LOADING_DARKER);
		String blueName = playerName.getText().trim();
		boolean blueIsSelf = localRsn != null && localRsn.equalsIgnoreCase(blueName);
		boolean redIsSelf = localRsn != null && localRsn.equalsIgnoreCase(player);
		boolean samePlayer = blueName.equalsIgnoreCase(player);

		if (samePlayer)
		{
			// Mirror: reuse existing data, no API calls
			compareLookupInFlight = false;
			compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
			compareSearchBar.setText("");
			compareHiscoreResult = hiscoreResult;
			compareClogResult = clogResult;
			compareRsn = blueName;
			if (blueIsSelf)
			{
				setCompareStatus(SearchMessages.COMPARE_SELF_MIRROR, blueName, SearchMessages.SELF_COLOR);
			}
			else
			{
				setCompareStatus(SearchMessages.COMPARE_MIRROR, blueName, TEXT_DIM);
			}
			activateComparisonMode();
			return;
		}

		if (blueIsSelf || redIsSelf)
		{
			String[] pool = blueIsSelf
				? SearchMessages.COMPARE_SELF_BLUE
				: SearchMessages.COMPARE_SELF_RED;
			String msg = pool[ThreadLocalRandom.current().nextInt(pool.length)];
			if (msg.contains("%s") && msg.indexOf("%s") != msg.lastIndexOf("%s"))
			{
				msg = String.format(msg, blueName, player);
			}
			else
			{
				msg = String.format(msg, blueIsSelf ? player : blueName);
			}
			setCompareStatus(msg, SearchMessages.SELF_COLOR);
		}
		else
		{
			setCompareStatus(SearchMessages.COMPARE_SEARCH, blueName, TEXT_DIM);
		}

		hiscoreService.lookup(player, null).thenAccept(result ->
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != compareLookupVersion) return;
				compareLookupInFlight = false;

				if (result == null)
				{
					compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
					compareSearchBar.setText("");
					setCompareStatus(SearchMessages.COMPARE_NOT_FOUND,
						playerName.getText().trim(), COMPARE_RED);
					return;
				}

				compareHiscoreResult = result;

				clogService.lookup(player).thenAccept(clogRes ->
					SwingUtilities.invokeLater(() ->
					{
						if (thisLookup != compareLookupVersion) return;
						compareClogResult = clogRes;
						if (clogRes != null)
						{
							lookupItemNames(clogRes);
						}
						compareRsn = clogRes != null && clogRes.getPlayerName() != null
							? clogRes.getPlayerName() : player;
						activateComparisonMode();
					})
				).exceptionally(ex ->
				{
					SwingUtilities.invokeLater(() ->
					{
						if (thisLookup != compareLookupVersion) return;
						compareRsn = player;
						activateComparisonMode();
					});
					return null;
				});
			})
		).exceptionally(ex ->
		{
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != compareLookupVersion) return;
				compareLookupInFlight = false;
				compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
				compareSearchBar.setText("");
				setCompareStatus("Lookup failed", COMPARE_RED);
			});
			return null;
		});
	}

	private void activateComparisonMode()
	{
		comparisonMode = true;
		compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
		compareSearchBar.setText("");

		compareStatus.setText(" ");

		// Build tooltip data for comparison player
		compareTooltipDataMap.clear();
		if (compareHiscoreResult != null)
		{
			for (HiscoreSkill boss : PanelData.BOSSES)
			{
				String bossName = boss.getName();
				String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(bossName, bossName);
				String category = ClogService.bossToCategory(hiscoreName);
				int rank = compareHiscoreResult.getRank(hiscoreName);
				TooltipData data = tooltipDataBuilder.buildTooltipData(bossName, category, rank, compareClogResult);
				if (data != null)
				{
					compareTooltipDataMap.put(boss, data);
					tooltipDataBuilder.preloadItemImages(data);
				}
				else if (rank > 0)
				{
					int total = clogService.getCategoryItemCount(category);
					compareTooltipDataMap.put(boss, new TooltipData(
						bossName, rank, -1, Math.max(total, 0),
						Collections.emptyList(),
						Collections.emptySet(),
						Collections.emptyMap()));
				}
			}
		}

		updateTooltips();
		updateAllCellsForComparison();
		updateInfoBarForComparison();
	}

	/** Swap info bar to show blue name (left) and red name (right) with badges. */
	private void updateInfoBarForComparison()
	{
		if (comparisonMode)
		{
			// Blue player — left side
			playerName.setForeground(COMPARE_BLUE);

			// Red player — replaces clog label on right side, clickable to swap
			clogInfoLabel.setText(compareRsn != null ? compareRsn : "");
			clogInfoLabel.setForeground(COMPARE_RED);
			clogInfoLabel.setToolTipText(null);
			clogInfoLabel.setHorizontalAlignment(JLabel.RIGHT);

			// Red player badge — prefer Temple-derived type (catches GIM)
			AccountType redType = compareClogResult != null
				? compareClogResult.getTempleAccountType() : null;
			if (redType == null && compareHiscoreResult != null)
			{
				redType = compareHiscoreResult.getAccountType();
			}
			applyBadge(clogInfoLabel, redType);
		}
		else
		{
			// Restore normal state
			playerName.setForeground(getInfoColor());
			clogInfoLabel.setHorizontalAlignment(JLabel.RIGHT);

			// Clog cell will be restored by updateClogCell if data exists
			if (clogResult != null)
			{
				updateClogCell(clogResult);
			}
			else
			{
				clogInfoLabel.setText("");
				clogInfoLabel.setIcon(null);
				clogInfoLabel.setToolTipText(null);
			}
		}
	}

	/** Apply account type badge to any label. */
	private void applyBadge(JLabel label, AccountType type)
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

	/** Set a cell to dual blue/red values, or restore to solo. */
	private void setCompareCell(JLabel label, int blueVal, int redVal)
	{
		String blueText = blueVal > 0 ? ClogHelper.formatKc(blueVal) : "--";
		String redText = redVal > 0 ? ClogHelper.formatKc(redVal) : "--";
		label.setText("<html><div style='text-align:center;'>"
			+ "<span style='color:" + COMPARE_BLUE_HEX + ";'>" + blueText + "</span><br>"
			+ "<span style='color:" + COMPARE_RED_HEX + ";'>" + redText + "</span>"
			+ "</div></html>");
		label.setForeground(null);
		label.setHorizontalAlignment(JLabel.CENTER);
	}

	private void restoreSoloCell(JLabel label, int val)
	{
		label.setHorizontalAlignment(JLabel.LEADING);
		if (val > 0)
		{
			label.setText(ClogHelper.pad(ClogHelper.formatKc(val)));
			label.setForeground(getInfoColor());
		}
		else
		{
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
	}

	private void compareOrRestore(JLabel label, int blueVal, int redVal)
	{
		if (label == null) return;
		if (comparisonMode)
		{
			setCompareCell(label, blueVal, redVal);
		}
		else
		{
			restoreSoloCell(label, blueVal);
		}
	}

	private int hiscoreKc(HiscoreResult r, String hiscoreName)
	{
		return r != null ? r.getKc(hiscoreName) : -1;
	}

	private int activityScore(HiscoreResult r, String name)
	{
		return r != null ? r.getActivityScore(name) : -1;
	}

	private int pvpTotal(HiscoreResult r)
	{
		if (r == null) return -1;
		int total = Math.max(0, r.getActivityScore("Bounty Hunter - Hunter"))
			+ Math.max(0, r.getActivityScore("Bounty Hunter - Rogue"));
		return total > 0 ? total : -1;
	}

	private int rareCount(TooltipData data)
	{
		return data != null && data.obtainedCount > 0 ? data.obtainedCount : -1;
	}

	private void updateAllCellsForComparison()
	{
		// Boss cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			String name = PanelData.NAME_OVERRIDES.getOrDefault(entry.getKey().getName(), entry.getKey().getName());
			compareOrRestore(entry.getValue(), hiscoreKc(hiscoreResult, name), hiscoreKc(compareHiscoreResult, name));
		}

		// Activity cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
		{
			String name = entry.getKey().getName();
			compareOrRestore(entry.getValue(), activityScore(hiscoreResult, name), activityScore(compareHiscoreResult, name));
		}

		// Clue tier cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : clueTierLabels.entrySet())
		{
			String name = entry.getKey().getName();
			compareOrRestore(entry.getValue(), activityScore(hiscoreResult, name), activityScore(compareHiscoreResult, name));
		}

		// Combat level
		compareOrRestore(combatCell,
			hiscoreResult != null ? hiscoreResult.getCombatLevel() : -1,
			compareHiscoreResult != null ? compareHiscoreResult.getCombatLevel() : -1);

		// Total level
		compareOrRestore(totalLvlCell,
			hiscoreResult != null ? hiscoreResult.getTotalLevel() : -1,
			compareHiscoreResult != null ? compareHiscoreResult.getTotalLevel() : -1);

		// PvP summary
		compareOrRestore(pvpSummaryCell, pvpTotal(hiscoreResult), pvpTotal(compareHiscoreResult));

		// Clue rares — 3rd Age, Gilded
		compareOrRestore(thirdAgeCell,
			rareCount(rareTooltips.get(PanelData.CLOG_THIRD_AGE)),
			rareCount(buildCompareClueRare("3rd Age", PanelData.CLOG_THIRD_AGE)));
		compareOrRestore(gildedCell,
			rareCount(rareTooltips.get(PanelData.CLOG_GILDED)),
			rareCount(buildCompareClueRare("Gilded", PanelData.CLOG_GILDED)));

		// Custom rares — Hard, Elite, Master
		compareOrRestore(hardRare,
			rareCount(rareTooltips.get(PanelData.RARE_HARD)),
			rareCount(buildCompareCustomRare("Hard Treasure (Rare)", PanelData.HARD_RARE_ITEMS)));
		compareOrRestore(eliteRare,
			rareCount(rareTooltips.get(PanelData.RARE_ELITE)),
			rareCount(buildCompareCustomRare("Elite Treasure (Rare)", PanelData.ELITE_RARE_ITEMS)));
		compareOrRestore(masterRare,
			rareCount(rareTooltips.get(PanelData.RARE_MASTER)),
			rareCount(buildCompareCustomRare("Master Treasure (Rare)", PanelData.MASTER_RARE_ITEMS)));
	}

	private TooltipData buildCompareClueRare(String name, String clogCategory)
	{
		return compareClogResult != null
			? tooltipDataBuilder.buildClueRareData(name, clogCategory, compareClogResult)
			: null;
	}

	private TooltipData buildCompareCustomRare(String name, int[] itemIds)
	{
		return compareClogResult != null
			? tooltipDataBuilder.buildCustomRareData(name, itemIds, compareClogResult)
			: null;
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

	private void setCompareStatus(String[] pool, String player, Color color)
	{
		String msg = String.format(pool[ThreadLocalRandom.current().nextInt(pool.length)], player, player);
		compareStatus.setText(msg);
		compareStatus.setForeground(color);
	}

	private void setCompareStatus(String msg, Color color)
	{
		compareStatus.setText(msg);
		compareStatus.setForeground(color);
	}

	private void updateComparePlaceholder(String name)
	{
		String old = comparePlaceholder;
		comparePlaceholder = name + " vs...";
		if (compareTextField == null || compareTextField.hasFocus())
		{
			return;
		}
		String current = compareTextField.getText();
		if (current.isEmpty() || current.equals(old) || current.equals("Comparison"))
		{
			compareTextField.setText(comparePlaceholder);
			compareTextField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}
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
		if (player.isEmpty() || lookupInFlight)
		{
			if (!lookupInFlight)
			{
				setSearchStatus("Enter RSN", TEXT_DIM);
			}
			return;
		}

		lookupInFlight = true;
		currentLookupRsn = player;
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
		// GIMs only appear on regular hiscores, so tell hiscore service to skip the cascade
		AccountType knownType = isSelf ? localAccountType : null;

		// Cache check: if hiscore cache is fresh, show cached data after a short
		// delay to preserve the search feel. No API calls. If stale or missing,
		// fire the full lookup.
		HiscoreResult cachedHiscore = hiscoreService.getCached(player);
		ClogResult cachedClog = clogService.getCachedResult(player);
		if (cachedHiscore != null && !hiscoreService.isStale(player))
		{
			Timer revealTimer = new Timer(600, e ->
			{
				if (thisLookup != lookupVersion) return;
				hiscoreResult = cachedHiscore;
				lookupInFlight = false;
				searchBar.setIcon(IconTextField.Icon.SEARCH);
				if (nameAutocompleter != null)
				{
					nameAutocompleter.addToSearchHistory(player);
				}
				if (!isFirstSelfGreeting)
				{
					setSearchStatus(" ", TEXT_DIM);
				}
				renderHiscoreResult(cachedHiscore, player, isSelf, knownType);
				if (cachedClog != null)
				{
					clogResult = cachedClog;
					renderClogResult(cachedClog, isSelf, thisLookup);
				}
			});
			revealTimer.setRepeats(false);
			revealTimer.start();
			return;
		}

		// Full API lookup — cache miss or stale
		AccountType hiscoreType = knownType != null && knownType.isGroupIronman()
			? AccountType.REGULAR : knownType;
		hiscoreService.lookup(player, hiscoreType).thenAccept(result ->
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupVersion) return;
				lookupInFlight = false;
				searchBar.setIcon(IconTextField.Icon.SEARCH);

				if (result == null)
				{
					lookupVersion++;
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
				renderHiscoreResult(result, player, isSelf, knownType);
				// Clog may have arrived first with a GIM type hiscores can't detect
				if (clogResult != null)
				{
					AccountType templeType = clogResult.getTempleAccountType();
					if (templeType != null && templeType.isGroupIronman())
					{
						updateInfoIcon(templeType);
					}
					updateRares(clogResult);
					updateClogCell(clogResult);
				}
			})
		).exceptionally(ex ->
		{
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupVersion) return;
				lookupVersion++;
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

				if (result != null)
				{
					renderClogResult(result, isSelf, thisLookup);
				}
				else
				{
					clogLastChanged = null;
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
		if (comparisonMode) exitComparisonMode();
		comparePanel.setVisible(false);
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
		compareToggle.setVisible(false);
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
		rareTooltips.remove(PanelData.CLOG_THIRD_AGE);
		rareTooltips.remove(PanelData.CLOG_GILDED);
	}

	/**
	 * Render hiscore data to the panel (extracted for cache/SWR reuse).
	 */
	private void renderHiscoreResult(HiscoreResult result, String player,
		boolean isSelf, AccountType knownType)
	{
		compareStatus.setText(" ");
		playerName.setText(rsn != null ? rsn : player);
		playerName.setForeground(getInfoColor());
		updateComparePlaceholder(rsn != null ? rsn : player);
		updateInfoIcon(knownType != null ? knownType : result.getAccountType());
		compareToggle.setVisible(true);
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
		updateTooltips();
	}

	/**
	 * Render clog data to the panel (extracted for SWR reuse).
	 */
	private void renderClogResult(ClogResult result, boolean isSelf, int thisLookup)
	{
		clogLastChanged = result.getLastChanged();
		String name = result.getPlayerName();
		if (name != null && !name.isEmpty())
		{
			rsn = name;
			updateComparePlaceholder(name);
			if (hiscoreResult != null)
			{
				playerName.setText(name);
			}
		}
		AccountType templeType = result.getTempleAccountType();
		if (templeType != null && templeType.isGroupIronman() && hiscoreResult != null)
		{
			updateInfoIcon(templeType);
		}
		lookupItemNames(result);
		if (hiscoreResult != null)
		{
			updateRares(result);
			updateClogCell(result);
		}
		toggleHighlighter(config.completionistHighlighter());
		updateTooltips();
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
					updateComparePlaceholder(name);
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

	private Color rareColor(TooltipData data)
	{
		if (data.obtainedCount <= 0)
		{
			return config.completionistHighlighter()
				? config.emptyClogColor() : ColorScheme.LIGHT_GRAY_COLOR;
		}
		return config.completionistHighlighter()
			? ClogHelper.clogColor(data.obtainedCount, data.totalItems, config) : KC_COLOR;
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
		rareTooltips.put(isThirdAge ? PanelData.CLOG_THIRD_AGE : PanelData.CLOG_GILDED, data);
		label.setForeground(rareColor(data));
		label.setToolTipText(" ");
	}

	private void updateCustomRare(JLabel label, String name, String rareKey,
		int[] itemIds, ClogResult result)
	{
		if (label == null) return;

		TooltipData data = tooltipDataBuilder.buildCustomRareData(name, itemIds, result);

		label.setText(ClogHelper.pad(data.obtainedCount > 0 ? ClogHelper.formatKc(data.obtainedCount) : "--"));
		rareTooltips.put(rareKey, data);
		label.setForeground(rareColor(data));
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
					&& localRsn.equalsIgnoreCase(currentLookupRsn);
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

		if (comparisonMode)
		{
			updateAllCellsForComparison();
			updateInfoBarForComparison();
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
		if (lookupInFlight)
		{
			// Current lookup will finish soon — version it out and start fresh
			lookupVersion++;
			lookupInFlight = false;
		}
		doLookup();
	}

	public void setNameAutocompleter(NameAutocompleter autocompleter)
	{
		this.nameAutocompleter = autocompleter;
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
			SwingUtilities.invokeLater(this::updateTooltips);
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

}
