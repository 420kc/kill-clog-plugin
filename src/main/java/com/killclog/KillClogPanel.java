package com.killclog;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.border.MatteBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import java.awt.event.AWTEventListener;
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
	private static final int MAX_TOTAL_LEVEL = 2376;

	/** Info bar text color — only applies when highlighter is active AND clog data exists. */
	private Color getInfoColor()
	{
		return config.completionistHighlighter() && clogResult != null
			? config.infoBarColor() : KC_COLOR;
	}

	private static final String[] SEARCH_MSGS = {
		"Throwing a search party for %s...",
		"Moving mountains to find %s...",
		"Deliberating on %s's whereabouts...",
		"Searching high and low for %s...",
		"Leaving no stone unturned for %s...",
		"Hot on the trail of %s...",
		"Scouring Gielinor for %s...",
		"Putting out an APB on %s...",
	};

	private static final String[] NOT_FOUND_MSGS = {
		"WANTED: %s",
		"%s has gone AWOL",
		"%s is touching grass",
		"%s? Never heard of 'em.",
		"%s who?",
		"Have you seen %s? I haven't...",
		"404: %s not found",
		"%s remains at large",
		"Couldn't find %s. Tragic.",
	};

	// Boss display order matching vanilla RuneLite hiscores.
	// Must contain the same bosses as BOSS_NAMES in HiscoreService (order differs — this is display order, not CSV order).
	// New boss? Add HiscoreSkill.BOSS_NAME here alphabetically once RuneLite adds the enum.
	// See BOSS_NAMES comment in HiscoreService for the full update playbook.
	private static final HiscoreSkill[] BOSSES = {
		HiscoreSkill.ABYSSAL_SIRE,
		HiscoreSkill.ALCHEMICAL_HYDRA,
		HiscoreSkill.AMOXLIATL,
		HiscoreSkill.ARAXXOR,
		HiscoreSkill.ARTIO,
		HiscoreSkill.BARROWS_CHESTS,
		HiscoreSkill.BRUTUS,
		HiscoreSkill.BRYOPHYTA,
		HiscoreSkill.CALLISTO,
		HiscoreSkill.CALVARION,
		HiscoreSkill.CERBERUS,
		HiscoreSkill.CHAMBERS_OF_XERIC,
		HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE,
		HiscoreSkill.CHAOS_ELEMENTAL,
		HiscoreSkill.CHAOS_FANATIC,
		HiscoreSkill.COMMANDER_ZILYANA,
		HiscoreSkill.CORPOREAL_BEAST,
		HiscoreSkill.CRAZY_ARCHAEOLOGIST,
		HiscoreSkill.DAGANNOTH_PRIME,
		HiscoreSkill.DAGANNOTH_REX,
		HiscoreSkill.DAGANNOTH_SUPREME,
		HiscoreSkill.DERANGED_ARCHAEOLOGIST,
		HiscoreSkill.DOOM_OF_MOKHAIOTL,
		HiscoreSkill.DUKE_SUCELLUS,
		HiscoreSkill.GENERAL_GRAARDOR,
		HiscoreSkill.GIANT_MOLE,
		HiscoreSkill.GROTESQUE_GUARDIANS,
		HiscoreSkill.HESPORI,
		HiscoreSkill.THE_HUEYCOATL,
		HiscoreSkill.KALPHITE_QUEEN,
		HiscoreSkill.KING_BLACK_DRAGON,
		HiscoreSkill.KRAKEN,
		HiscoreSkill.KREEARRA,
		HiscoreSkill.KRIL_TSUTSAROTH,
		HiscoreSkill.LUNAR_CHESTS,
		HiscoreSkill.MIMIC,
		HiscoreSkill.NEX,
		HiscoreSkill.NIGHTMARE,
		HiscoreSkill.PHOSANIS_NIGHTMARE,
		HiscoreSkill.OBOR,
		HiscoreSkill.PHANTOM_MUSPAH,
		HiscoreSkill.THE_ROYAL_TITANS,
		HiscoreSkill.SARACHNIS,
		HiscoreSkill.SCORPIA,
		HiscoreSkill.SCURRIUS,
		HiscoreSkill.SHELLBANE_GRYPHON,
		HiscoreSkill.SKOTIZO,
		HiscoreSkill.SOL_HEREDIT,
		HiscoreSkill.SPINDEL,
		HiscoreSkill.TEMPOROSS,
		HiscoreSkill.THE_GAUNTLET,
		HiscoreSkill.THE_CORRUPTED_GAUNTLET,
		HiscoreSkill.THE_LEVIATHAN,
		HiscoreSkill.THE_WHISPERER,
		HiscoreSkill.THEATRE_OF_BLOOD,
		HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE,
		HiscoreSkill.THERMONUCLEAR_SMOKE_DEVIL,
		HiscoreSkill.TOMBS_OF_AMASCUT,
		HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT,
		HiscoreSkill.TZKAL_ZUK,
		HiscoreSkill.TZTOK_JAD,
		HiscoreSkill.VARDORVIS,
		HiscoreSkill.VENENATIS,
		HiscoreSkill.VETION,
		HiscoreSkill.VORKATH,
		HiscoreSkill.WINTERTODT,
		HiscoreSkill.YAMA,
		HiscoreSkill.ZALCANO,
		HiscoreSkill.ZULRAH,
	};

	/** Number of boss cells in the panel grid. Used by tests to detect drift. */
	static int bossCount()
	{
		return BOSSES.length;
	}

	// HiscoreSkill.getName() -> boss name used in hiscore CSV data.
	// Only entries where the two differ are needed.
	private static final Map<String, String> NAME_OVERRIDES = new LinkedHashMap<>();
	static
	{
		NAME_OVERRIDES.put("Calvar'ion", "Cal'varion");
	}

	private static final HiscoreSkill[] CLUE_TIERS = {
		HiscoreSkill.CLUE_SCROLL_BEGINNER, HiscoreSkill.CLUE_SCROLL_EASY,
		HiscoreSkill.CLUE_SCROLL_MEDIUM, HiscoreSkill.CLUE_SCROLL_HARD,
		HiscoreSkill.CLUE_SCROLL_ELITE, HiscoreSkill.CLUE_SCROLL_MASTER,
	};


	private static final String CLOG_THIRD_AGE = "third_age";
	private static final String CLOG_GILDED = "gilded";
	private static final int[] CLUE_TIER_ITEM_IDS = {23182, 2677, 2801, 2722, 12073, 19835};
	private static final int THIRD_AGE_ITEM_ID = 10348;
	private static final int GILDED_ITEM_ID = 3481;

	// Native clog rare categories — hardcoded item IDs (Temple doesn't have these)
	private static final String RARE_HARD = "hard_rare";
	private static final String RARE_ELITE = "elite_rare";
	private static final String RARE_MASTER = "master_rare";

	private static final int[] HARD_RARE_ITEMS = {
		// 3rd age melee + range + mage + amulet (13)
		10350, 10348, 10346, 23242, 10352,
		10334, 10330, 10332, 10336,
		10342, 10338, 10340, 10344,
		// Gilded melee (11)
		3486, 3481, 3483, 3485, 3488,
		20146, 20149, 20152, 20155, 20158, 20161
	};

	private static final int[] ELITE_RARE_ITEMS = {
		// 3rd age melee + range + mage + amulet + weapons + cloak (17)
		10350, 10348, 10346, 23242, 10352,
		10334, 10330, 10332, 10336,
		10342, 10338, 10340, 10344,
		12426, 12422, 12437, 12424,
		// All gilded (20)
		3486, 3481, 3483, 3485, 3488,
		20146, 20149, 20152, 20155, 20158, 20161,
		12389, 12391, 23258, 23261, 23264, 23267,
		23276, 23279, 23282,
		// Lava dragon mask, Ring of nature
		12371, 20005
	};

	private static final int[] MASTER_RARE_ITEMS = {
		// All 3rd age (23)
		10350, 10348, 10346, 23242, 10352,
		10334, 10330, 10332, 10336,
		10342, 10338, 10340, 10344,
		12426, 12422, 12437, 12424,
		23336, 23339, 23345, 23342,
		20014, 20011,
		// All gilded (20)
		3486, 3481, 3483, 3485, 3488,
		20146, 20149, 20152, 20155, 20158, 20161,
		12389, 12391, 23258, 23261, 23264, 23267,
		23276, 23279, 23282,
		// Bucket helm (g), Ring of coins
		20059, 20017
	};

	// Clue tier -> Temple clog category
	private static final Map<HiscoreSkill, String> CLUE_CATEGORIES = new LinkedHashMap<>();
	static
	{
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_BEGINNER, "beginner_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_EASY, "easy_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_MEDIUM, "medium_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_HARD, "hard_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_ELITE, "elite_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_MASTER, "master_treasure_trails");
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
				getAccountBadge(),
				getAccountLabel(),
				getPrestige()
			);
			if (clogResult != null)
			{
				List<Integer> allPets = clogResult.getCategoryItems().get("all_pets");
				Set<Integer> obtainedPets = getObtainedPetIds();
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
			java.awt.FontMetrics fm = g.getFontMetrics();
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
				boolean stale = isSyncStale(clogLastChanged, 90);
				String sync = syncLine(clogLastChanged, stale);
				if (sync != null) tip.setSyncData(sync, stale);
				tip.setRecentItems(getRecentItems(4), itemManager);
			}
			else
			{
				boolean isSelf = localRsn != null && rsn != null
					&& localRsn.equalsIgnoreCase(rsn)
					&& config.clogSource() != ClogSource.TEMPLE;
				if (isSelf)
				{
					tip.setNotice("Open your Collection Log in-game to continue.");
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
			java.awt.FontMetrics fm = g.getFontMetrics();
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
	private static final int[] CLOG_TIER_ITEM_IDS = {
		30579, 30581, 30583, 30585, 30587, 30589, 30591, 30593, 30595
	};
	private final Map<String, ImageIcon> clogTierIcons = new LinkedHashMap<>();
	private final Map<String, ImageIcon> clogTierIconsLarge = new LinkedHashMap<>();

	private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, JLabel> activityLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> dimmedIcons = new LinkedHashMap<>();
	private JLabel pvpSummaryCell;
	private final BufferedImage[] pvpActivityIcons = new BufferedImage[5];
	private final BufferedImage[] clueIcons = new BufferedImage[8];
	private static final HiscoreSkill[] PVP_ACTIVITIES = {
		HiscoreSkill.LAST_MAN_STANDING,
		HiscoreSkill.SOUL_WARS_ZEAL,
		HiscoreSkill.PVP_ARENA_RANK,
		HiscoreSkill.BOUNTY_HUNTER_HUNTER,
		HiscoreSkill.BOUNTY_HUNTER_ROGUE,
	};

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

	// Captured in onActivate(), restored in onDeactivate()/shutdown()
	private int defaultDismissDelay;
	private int defaultInitialDelay = -1;

	// Click-to-reveal tooltip state
	private Popup activeClickPopup;
	private JLabel activeClickLabel;
	private AWTEventListener clickDismissListener;
	private JLabel clickDismissedLabel;

	// Hover state — 1px border outline
	private JPanel hoveredCell;
	private Timer hoverExitTimer;
	private static final Border CELL_BORDER = new EmptyBorder(1, 1, 1, 1);
	private static final Color HOVER_OUTLINE_DIM = new Color(90, 90, 90);
	private static final Color HOVER_TINT_BG = new Color(41, 41, 41);

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
			NAME_OVERRIDES, CLUE_CATEGORIES, config);
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
				javax.swing.JTextField tf =
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
		panel.add(searchBar);
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
						showClickTooltip(label, infoRow);
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
				final int itemId = CLOG_TIER_ITEM_IDS[i];
				loadItemIcon(itemId, 13, 13, icon ->
					clogTierIcons.put(tier, icon));
				loadItemIcon(itemId, 18, 18, icon ->
					clogTierIconsLarge.put(tier, icon));
			}

			// Clue summary tooltip icons: 1-6=tier scrolls, 7=Mimic (0=All loaded via spriteManager below)
			for (int i = 0; i < CLUE_TIER_ITEM_IDS.length; i++)
			{
				final int idx = i + 1;
				loadItemImage(CLUE_TIER_ITEM_IDS[i], img ->
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

	private void loadItemIcon(int itemId, int w, int h, java.util.function.Consumer<ImageIcon> setter)
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

	private void loadItemImage(int itemId, java.util.function.Consumer<BufferedImage> setter)
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
					sumBossKills(),
					countBossesWithKc(),
					BOSSES.length,
					getMostKilledBoss(),
					getMostKilledKc()
				);
				if (clogResult != null)
				{
					tip.setCompletion(countBossesCompleted(), countBossesWithClog());
				}
				tip.setMegarares(
					getClogItemCount("chambers_of_xeric", 20997),
					getClogItemCount("theatre_of_blood", 22486),
					getClogItemCount("tombs_of_amascut", 27277),
					itemManager
				);
				if (hiscoreResult != null)
				{
					tip.setRaids(hiscoreResult, clogResult);
				}
				JPanel parentCell = (JPanel) this.getParent();
				keepTooltipOnHover(tip, parentCell);
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
		row1.add(makeClueRareCell("3rd Age", THIRD_AGE_ITEM_ID, CLOG_THIRD_AGE, true));
		row1.add(makeActivityCell(HiscoreSkill.CLUE_SCROLL_ALL));
		row1.add(makeClueRareCell("Gilded", GILDED_ITEM_ID, CLOG_GILDED, false));
		grid.add(row1);

		// Clue row 2: Custom rare cells (casket icons)
		JPanel rareRow = new JPanel(new GridLayout(1, 3));
		rareRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rareRow.setAlignmentX(0f);
		rareRow.add(makeCustomRareCell("Hard (Rare)", 20544, RARE_HARD, HARD_RARE_ITEMS));
		rareRow.add(makeCustomRareCell("Elite (Rare)", 20543, RARE_ELITE, ELITE_RARE_ITEMS));
		rareRow.add(makeCustomRareCell("Master (Rare)", 19836, RARE_MASTER, MASTER_RARE_ITEMS));
		grid.add(rareRow);

		// Clue rows 3-4: Clue tiers
		JPanel clueRow1 = new JPanel(new GridLayout(1, 3));
		clueRow1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow1.setAlignmentX(0f);
		clueRow1.add(makeClueTierCell(CLUE_TIERS[0], CLUE_TIER_ITEM_IDS[0], false));
		clueRow1.add(makeClueTierCell(CLUE_TIERS[1], CLUE_TIER_ITEM_IDS[1], true));
		clueRow1.add(makeClueTierCell(CLUE_TIERS[2], CLUE_TIER_ITEM_IDS[2], true));
		grid.add(clueRow1);

		JPanel clueRow2 = new JPanel(new GridLayout(1, 3));
		clueRow2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow2.setAlignmentX(0f);
		clueRow2.add(makeClueTierCell(CLUE_TIERS[3], CLUE_TIER_ITEM_IDS[3], true));
		clueRow2.add(makeClueTierCell(CLUE_TIERS[4], CLUE_TIER_ITEM_IDS[4], false));
		clueRow2.add(makeClueTierCell(CLUE_TIERS[5], CLUE_TIER_ITEM_IDS[5], false));
		grid.add(clueRow2);

		return grid;
	}

	// -------------------------------------------------------------------------
	// Cell factory helpers — extracted to eliminate duplication
	// -------------------------------------------------------------------------

	/**
	 * Wire hover effect onto a cell panel.
	 * Shows a 1px border outline in the label's foreground color on hover.
	 * 150ms debounced exit keeps the outline while tooltip is open.
	 * Listeners on the cell (not the label) for full-cell hitbox.
	 */
	private void addCellHoverEffect(JPanel cell, JLabel label)
	{
		MouseAdapter hoverAdapter = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (config.tooltipMode() == TooltipMode.CLICK && label.getToolTipText() != null)
				{
					showClickTooltip(label, cell);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (hoverExitTimer != null) hoverExitTimer.stop();

				// Re-entering the same cell — already outlined, just cancelled the exit timer
				if (hoveredCell == cell) return;

				// Leaving a different cell — clear its hover first
				if (hoveredCell != null) resetCellHover();

				hoveredCell = cell;
				switch (config.hoverStyle())
				{
					case OUTLINE:
						Color fg = label.getForeground();
						Color outline = (fg.equals(KC_COLOR) || fg.equals(ColorScheme.LIGHT_GRAY_COLOR))
							? HOVER_OUTLINE_DIM : fg;
						cell.setBorder(new MatteBorder(1, 1, 1, 1, outline));
						break;
					case TINT:
						cell.setBackground(HOVER_TINT_BG);
						break;
					case NONE:
						break;
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (hoverExitTimer != null) hoverExitTimer.stop();
				hoverExitTimer = new Timer(150, evt ->
				{
					if (hoveredCell == cell)
					{
						resetCellHover();
						hoveredCell = null;
					}
				});
				hoverExitTimer.setRepeats(false);
				hoverExitTimer.start();
			}
		};
		cell.addMouseListener(hoverAdapter);
		label.addMouseListener(hoverAdapter);
	}

	private void resetCellHover()
	{
		if (hoveredCell != null)
		{
			hoveredCell.setBorder(CELL_BORDER);
			hoveredCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		}
	}

	/**
	 * Wire mouse + hierarchy listeners on a tooltip to clear the parent cell's hover tint
	 * when the tooltip hides. Called inside every {@code createToolTip()} override.
	 */
	private void keepTooltipOnHover(JToolTip tip, JPanel parentCell)
	{
		tip.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (hoverExitTimer != null) hoverExitTimer.stop();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				clearCellHover(parentCell);
			}
		});
		tip.addHierarchyListener(e ->
		{
			if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !tip.isShowing())
			{
				clearCellHover(parentCell);
			}
		});
	}

	private void clearCellHover(JPanel cell)
	{
		if (hoveredCell == cell)
		{
			// Don't unhover if mouse is still inside the cell (click-to-dismiss case)
			Point mouse = cell.getMousePosition();
			if (mouse != null)
			{
				return;
			}
			resetCellHover();
			hoveredCell = null;
		}
	}

	private void showClickTooltip(JLabel label, JPanel cell)
	{
		// AWTEventListener dismissed this label on the same click — treat as toggle-off
		if (label == clickDismissedLabel)
		{
			clickDismissedLabel = null;
			return;
		}
		clickDismissedLabel = null;

		hideClickTooltip();

		JToolTip tip = label.createToolTip();
		tip.setTipText(label.getToolTipText());

		if (tip instanceof NativeTooltip)
		{
			((NativeTooltip) tip).setCloseAction(() -> hideClickTooltip());
		}

		Dimension tipSize = tip.getPreferredSize();

		// Position below cell, aligned to label's x, screen-bounds aware
		Point labelLoc = label.getLocationOnScreen();
		Point cellLoc = cell.getLocationOnScreen();
		Rectangle screen = cell.getGraphicsConfiguration().getBounds();

		int x = labelLoc.x;
		int y = cellLoc.y + cell.getHeight();

		if (x + tipSize.width > screen.x + screen.width)
		{
			x = screen.x + screen.width - tipSize.width;
		}
		if (y + tipSize.height > screen.y + screen.height)
		{
			y = cellLoc.y - tipSize.height;
		}

		activeClickPopup = PopupFactory.getSharedInstance().getPopup(cell, tip, x, y);
		activeClickLabel = label;
		activeClickPopup.show();

		// Dismiss on next click anywhere
		clickDismissListener = event ->
		{
			if (event.getID() == MouseEvent.MOUSE_PRESSED)
			{
				clickDismissedLabel = activeClickLabel;
				hideClickTooltip();
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(
			clickDismissListener, AWTEvent.MOUSE_EVENT_MASK);
	}

	private void hideClickTooltip()
	{
		if (clickDismissListener != null)
		{
			Toolkit.getDefaultToolkit().removeAWTEventListener(clickDismissListener);
			clickDismissListener = null;
		}
		if (activeClickPopup != null)
		{
			activeClickPopup.hide();
			activeClickPopup = null;
			activeClickLabel = null;
		}
	}

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
			tip.setNotice(hiscoreResult != null
				? "No TempleOSRS Data" : "Nothing to see here! (Search for a player)");
		}

		keepTooltipOnHover(tip, parentCell);
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
		cell.setBorder(CELL_BORDER);
		cell.add(label);
		addCellHoverEffect(cell, label);
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
					keepTooltipOnHover(tip, parentCell);
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
				keepTooltipOnHover(tip, parentCell);
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
		for (int i = 0; i < PVP_ACTIVITIES.length; i++)
		{
			int spriteId = PVP_ACTIVITIES[i].getSpriteId();
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

		if (RARE_HARD.equals(rareKey)) hardRare = label;
		else if (RARE_ELITE.equals(rareKey)) eliteRare = label;
		else if (RARE_MASTER.equals(rareKey)) masterRare = label;

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
		for (HiscoreSkill boss : BOSSES)
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

	private volatile int lookupVersion = 0;
	private volatile boolean lookupInFlight = false;

	/**
	 * Single point of control for the info bar when no result data is available.
	 * Hides all data components; shows only the message text.
	 */
	private void setSearchStatus(String text, Color color)
	{
		searchStatus.setText(text);
		searchStatus.setForeground(color);
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
		int searchIdx = ThreadLocalRandom.current().nextInt(SEARCH_MSGS.length);
		setSearchStatus(String.format(SEARCH_MSGS[searchIdx], player), TEXT_DIM);
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
					int notFoundIdx = ThreadLocalRandom.current().nextInt(NOT_FOUND_MSGS.length);
					setSearchStatus(String.format(NOT_FOUND_MSGS[notFoundIdx], player), NOT_FOUND);
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

				setSearchStatus(" ", TEXT_DIM);
				playerName.setText(rsn != null ? rsn : player);
				playerName.setForeground(getInfoColor());
				updateInfoIcon(result.getAccountType());

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
					boolean isSelf = localRsn != null
						&& localRsn.equalsIgnoreCase(player)
						&& config.clogSource() != ClogSource.TEMPLE;
					clogNotice.setText(isSelf
						? "Open your Collection Log"
						: " ");
					if (isSelf)
					{
						BufferedImage icon = ImageUtil.loadImageResource(
							KillClogPlugin.class, "icon.png");
						clogInfoLabel.setIcon(new ImageIcon(
							ImageUtil.resizeImage(icon, 15, 15)));
						clogInfoLabel.setText(ClogHelper.pad("Sync"));
						clogInfoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
						clogInfoLabel.setToolTipText(" ");
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
		hideClickTooltip();
		hiscoreResult = null;
		clogResult = null;
		clogLastChanged = null;
		rsn = null;
		clogNotice.setText(" ");
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
		resetRareCell(hardRare, "Hard (Rare)");
		resetRareCell(eliteRare, "Elite (Rare)");
		resetRareCell(masterRare, "Master (Rare)");
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
			String hiscoreName = NAME_OVERRIDES.getOrDefault(skill.getName(), skill.getName());
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
		updateClueRare(thirdAgeCell, "3rd Age", CLOG_THIRD_AGE, result, true);
		updateClueRare(gildedCell, "Gilded", CLOG_GILDED, result, false);
		updateCustomRare(hardRare, "Hard (Rare)", RARE_HARD, HARD_RARE_ITEMS, result);
		updateCustomRare(eliteRare, "Elite (Rare)", RARE_ELITE, ELITE_RARE_ITEMS, result);
		updateCustomRare(masterRare, "Master (Rare)", RARE_MASTER, MASTER_RARE_ITEMS, result);
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
			String hiscoreName = NAME_OVERRIDES.getOrDefault(bossName, bossName);

			int kc = hiscoreResult != null ? hiscoreResult.getKc(hiscoreName) : -1;
			int rank = hiscoreResult != null ? hiscoreResult.getRank(hiscoreName) : -1;

			String category = ClogService.bossToCategory(hiscoreName);

			if (clogResult == null)
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
		rebuildActivityTooltips(CLUE_CATEGORIES, clueTierLabels, KillClogPanel::capitalizeTier);
	}

	private void rebuildActivityTooltips(Map<HiscoreSkill, String> categories,
		Map<HiscoreSkill, JLabel> labels,
		java.util.function.Function<HiscoreSkill, String> nameOf)
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
		resetCellHover();
		hoveredCell = null;

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
				highlighter.colorCellsByCompletion(hiscoreResult, clogResult,
					rareTooltips, fourTwentyMode, FOUR_TWENTY_GREEN,
					thirdAgeCell, CLOG_THIRD_AGE,
					gildedCell, CLOG_GILDED,
					hardRare, RARE_HARD,
					eliteRare, RARE_ELITE,
					masterRare, RARE_MASTER);
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
			case "clogSource":
				toggleHighlighter(config.completionistHighlighter());
				updateTooltips();
				break;
			case "hoverStyle":
			case "tooltipMode":
				hideClickTooltip();
				resetCellHover();
				break;
		}
	}

	@Override
	public void onActivate()
	{
		// Capture current delay here, not at construction, to handle plugins that modify it
		defaultDismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
		ToolTipManager.sharedInstance().setDismissDelay(15000);

		if (config.tooltipMode() == TooltipMode.CLICK)
		{
			defaultInitialDelay = ToolTipManager.sharedInstance().getInitialDelay();
			ToolTipManager.sharedInstance().setInitialDelay(Integer.MAX_VALUE);
		}
	}

	@Override
	public void onDeactivate()
	{
		restoreTooltipDefaults();
	}

	/** Safety net — restores tooltip delay if plugin is disabled while panel is active. */
	public void shutdown()
	{
		restoreTooltipDefaults();
	}

	@Override
	public void removeNotify()
	{
		super.removeNotify();
		hideClickTooltip();
	}

	private void restoreTooltipDefaults()
	{
		ToolTipManager.sharedInstance().setDismissDelay(defaultDismissDelay);
		if (defaultInitialDelay >= 0)
		{
			ToolTipManager.sharedInstance().setInitialDelay(defaultInitialDelay);
			defaultInitialDelay = -1;
		}
		hideClickTooltip();
	}

	private void cycleFourTwentyMode()
	{
		FourTwentyMode[] modes = FourTwentyMode.values();
		fourTwentyMode = modes[(fourTwentyMode.ordinal() + 1) % modes.length];
		toggleHighlighter(config.completionistHighlighter());
	}

	// -------------------------------------------------------------------------
	// Clog tier logic
	// -------------------------------------------------------------------------

	private String syncLine(String lastChanged, boolean stale)
	{
		if (lastChanged == null || lastChanged.isEmpty()) return null;
		try
		{
			LocalDateTime syncTime = LocalDateTime.parse(lastChanged,
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			String pattern = stale ? "MMM d ''yy" : "MMM d";
			return syncTime.format(DateTimeFormatter.ofPattern(pattern));
		}
		catch (DateTimeParseException e)
		{
			return null;
		}
	}

	private boolean isSyncStale(String lastChanged, int days)
	{
		if (lastChanged == null || lastChanged.isEmpty()) return true;
		try
		{
			LocalDateTime syncTime = LocalDateTime.parse(lastChanged,
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			return syncTime.isBefore(LocalDateTime.now().minusDays(days));
		}
		catch (DateTimeParseException e)
		{
			return true;
		}
	}

	// -------------------------------------------------------------------------
	// Info bar
	// -------------------------------------------------------------------------

	private int sumBossKills()
	{
		if (hiscoreResult == null) return 0;
		int total = 0;
		for (int kc : hiscoreResult.getBossKills().values())
		{
			if (kc > 0) total += kc;
		}
		return total;
	}

	private int countBossesWithKc()
	{
		if (hiscoreResult == null) return 0;
		int count = 0;
		for (int kc : hiscoreResult.getBossKills().values())
		{
			if (kc > 0) count++;
		}
		return count;
	}

	private String getMostKilledBoss()
	{
		if (hiscoreResult == null) return null;
		String best = null;
		int bestKc = 0;
		for (Map.Entry<String, Integer> entry : hiscoreResult.getBossKills().entrySet())
		{
			if (entry.getValue() > bestKc)
			{
				bestKc = entry.getValue();
				best = entry.getKey();
			}
		}
		return best;
	}

	private int getMostKilledKc()
	{
		if (hiscoreResult == null) return 0;
		int best = 0;
		for (int kc : hiscoreResult.getBossKills().values())
		{
			if (kc > best) best = kc;
		}
		return best;
	}

	private int countBossesCompleted()
	{
		int count = 0;
		for (HiscoreSkill skill : bossLabels.keySet())
		{
			TooltipData data = tooltipDataMap.get(skill);
			if (data != null && data.totalItems > 0 && data.obtainedCount >= data.totalItems)
			{
				count++;
			}
		}
		return count;
	}

	private int countBossesWithClog()
	{
		int count = 0;
		for (HiscoreSkill skill : bossLabels.keySet())
		{
			TooltipData data = tooltipDataMap.get(skill);
			if (data != null && data.totalItems > 0) count++;
		}
		return count;
	}

	private List<ClogResult.ClogItem> getRecentItems(int limit)
	{
		List<ClogResult.ClogItem> all = new ArrayList<>();
		if (clogResult == null)
		{
			return all;
		}
		for (List<ClogResult.ClogItem> items : clogResult.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : items)
			{
				if (item.getDate() != null)
				{
					all.add(item);
				}
			}
		}
		all.sort((a, b) -> b.getDate().compareTo(a.getDate()));
		List<ClogResult.ClogItem> unique = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (ClogResult.ClogItem item : all)
		{
			if (seen.add(item.getId()) && unique.size() < limit)
			{
				unique.add(item);
			}
		}
		return unique;
	}

	private int getClogItemCount(String category, int itemId)
	{
		if (clogResult == null) return 0;
		List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(category);
		if (obtained == null) return 0;
		for (ClogResult.ClogItem item : obtained)
		{
			if (item.getId() == itemId) return item.getCount();
		}
		return 0;
	}

	private BufferedImage getCapeImage()
	{
		if (hiscoreResult == null) return null;
		boolean maxed = hiscoreResult.getTotalLevel() >= MAX_TOTAL_LEVEL;
		boolean infernal = hiscoreResult.getKc("TzKal-Zuk") > 0;
		if (maxed && infernal) return infernalMaxCapeTip;
		if (maxed) return maxCapeTip;
		if (infernal) return infernalCapeTip;
		return null;
	}

	private BufferedImage getAccountBadge()
	{
		if (hiscoreResult == null) return null;
		String resource = ClogHelper.accountBadgeResource(hiscoreResult.getAccountType());
		if (resource == null) return null;
		try
		{
			return ImageUtil.loadImageResource(HiscorePanel.class, resource);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private String getAccountLabel()
	{
		if (hiscoreResult == null) return null;
		return ClogHelper.accountLabel(hiscoreResult.getAccountType());
	}

	private String getPrestige()
	{
		if (hiscoreResult == null) return null;
		boolean maxed = hiscoreResult.getTotalLevel() >= MAX_TOTAL_LEVEL;
		boolean infernal = hiscoreResult.getKc("TzKal-Zuk") > 0;
		if (maxed && infernal) return "Maxed Infernal";
		if (maxed) return "Maxed";
		if (infernal) return "Infernal";
		return null;
	}

	private Set<Integer> getObtainedPetIds()
	{
		if (clogResult == null) return new HashSet<>();
		List<ClogResult.ClogItem> pets = clogResult.getObtainedItems().get("all_pets");
		Set<Integer> ids = new HashSet<>();
		if (pets != null)
		{
			for (ClogResult.ClogItem item : pets)
			{
				ids.add(item.getId());
			}
		}
		return ids;
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

	/** Minimal scrollbar — slim thumb, no arrow buttons, dark theme. */
	private static class MinimalScrollBarUI extends BasicScrollBarUI
	{
		@Override
		protected void configureScrollBarColors()
		{
			thumbColor = new Color(70, 70, 70);
			trackColor = ColorScheme.DARKER_GRAY_COLOR;
		}

		@Override
		protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds)
		{
			if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
			g.setColor(isThumbRollover() ? new Color(110, 110, 110) : thumbColor);
			g.fillRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);
		}

		@Override
		protected JButton createDecreaseButton(int orientation)
		{
			return makeZeroButton();
		}

		@Override
		protected JButton createIncreaseButton(int orientation)
		{
			return makeZeroButton();
		}

		private static JButton makeZeroButton()
		{
			JButton btn = new JButton();
			Dimension d = new Dimension(0, 0);
			btn.setPreferredSize(d);
			btn.setMinimumSize(d);
			btn.setMaximumSize(d);
			return btn;
		}
	}

}
