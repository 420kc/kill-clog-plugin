package com.killclog;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
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
import javax.swing.border.MatteBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KillClogPanel extends PluginPanel
{
    private static final Color TEXT_DIM = new Color(160, 160, 160);
    private static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);
    private static final Color KC_COLOR = new Color(215, 215, 215);
    private static final Color FOUR_TWENTY_GREEN = new Color(30, 200, 30);

    /** Info bar text color — follows highlighter state. */
    private Color getInfoColor()
    {
        return config.completionistHighlighter() ? config.infoBarColor() : KC_COLOR;
    }

    private static final String[] SEARCH_MSGS = {
        "Throwing a search party for %s...",
        "Moving mountains to find %s...",
        "Deliberating on %s's whereabouts...",
        "Searching high and low for %s...",
    };

    private static final String[] NOT_FOUND_MSGS = {
        "WANTED: %s",
        "%s has gone AWOL",
        "%s is touching grass",
        "%s? Never heard of 'em.",
        "%s who?",
        "Have you seen %s? I haven't...",
    };

    // Boss display order matching vanilla RuneLite hiscores.
    // Must stay in sync with BOSS_NAMES in HiscoreService.
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

    // HiscoreSkill.getName() -> boss name used in hiscore CSV data.
    // Only entries where the two differ are needed.
    private static final Map<String, String> NAME_OVERRIDES = new LinkedHashMap<>();
    static
    {
        NAME_OVERRIDES.put("Calvar'ion", "Cal'varion");
    }

    // Activities shown in the collapsible tray.
    // Clue tiers (spriteId == -1) are excluded here and shown in the clue sub-tray instead.
    private static final HiscoreSkill[] ACTIVITIES = {
        HiscoreSkill.CLUE_SCROLL_ALL,
        HiscoreSkill.LEAGUE_POINTS,
        HiscoreSkill.LAST_MAN_STANDING,
        HiscoreSkill.SOUL_WARS_ZEAL,
        HiscoreSkill.RIFTS_CLOSED,
        HiscoreSkill.COLOSSEUM_GLORY,
        HiscoreSkill.COLLECTIONS_LOGGED,
        HiscoreSkill.BOUNTY_HUNTER_ROGUE,
        HiscoreSkill.BOUNTY_HUNTER_HUNTER,
        HiscoreSkill.PVP_ARENA_RANK,
    };

    private static final HiscoreSkill[] CLUE_TIERS = {
        HiscoreSkill.CLUE_SCROLL_BEGINNER, HiscoreSkill.CLUE_SCROLL_EASY,
        HiscoreSkill.CLUE_SCROLL_MEDIUM, HiscoreSkill.CLUE_SCROLL_HARD,
        HiscoreSkill.CLUE_SCROLL_ELITE, HiscoreSkill.CLUE_SCROLL_MASTER,
    };

    // Activities without collection log categories — dimmed when highlighter is active
    private static final Set<HiscoreSkill> NO_CLOG_ACTIVITIES = Set.of(
        HiscoreSkill.LEAGUE_POINTS, HiscoreSkill.PVP_ARENA_RANK,
        HiscoreSkill.BOUNTY_HUNTER_ROGUE, HiscoreSkill.BOUNTY_HUNTER_HUNTER
    );

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

    // Activity -> Temple clog category for completionist highlighting
    private static final Map<HiscoreSkill, String> ACTIVITY_CATEGORIES = new LinkedHashMap<>();
    static
    {
        ACTIVITY_CATEGORIES.put(HiscoreSkill.SOUL_WARS_ZEAL, "soul_wars");
        ACTIVITY_CATEGORIES.put(HiscoreSkill.RIFTS_CLOSED, "guardians_of_the_rift");
        ACTIVITY_CATEGORIES.put(HiscoreSkill.LAST_MAN_STANDING, "last_man_standing");
        ACTIVITY_CATEGORIES.put(HiscoreSkill.COLOSSEUM_GLORY, "fortis_colosseum");
    }

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

    private final JLabel searchStatus = new JLabel(" ");
    private final IconTextField searchBar = new IconTextField();
    private final JLabel playerName = new JLabel(" ")
    {
        @Override
        public JToolTip createToolTip()
        {
            SummaryTooltip tip = new SummaryTooltip();
            tip.setComponent(this);
            String rsn = playerName.getText().trim();
            tip.setData(rsn.isEmpty() ? "Player" : rsn);
            return tip;
        }
    };
    private final JLabel clogInfoLabel = new JLabel()
    {
        @Override
        public JToolTip createToolTip()
        {
            TextTooltip tip = new TextTooltip();
            tip.setComponent(this);
            for (Map.Entry<String, ImageIcon> entry : clogTierIcons.entrySet())
            {
                tip.putIcon(entry.getKey(), iconToImage(entry.getValue()));
            }
            return tip;
        }
    };
    private final JLabel clogNotice = new JLabel();

    private ImageIcon skillsIcon;
    private ImageIcon maxCapeIcon;
    private ImageIcon infernalCapeIcon;
    private ImageIcon infernalMaxCapeIcon;
    private BufferedImage combatLevelImage;
    private static final String[] CLOG_TIERS = {
        "bronze", "iron", "steel", "black", "mithril", "adamant", "rune", "dragon", "gilded"
    };
    private static final int[] CLOG_TIER_ITEM_IDS = {
        30579, 30581, 30583, 30585, 30587, 30589, 30591, 30593, 30595
    };
    private static final int[] CLOG_TIER_THRESHOLDS = {100, 300, 500, 700, 900, 1000, 1100, 1200};
    private final Map<String, ImageIcon> clogTierIcons = new LinkedHashMap<>();
    private final Map<String, ImageIcon> clogTierIconsLarge = new LinkedHashMap<>();

    private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();
    private final Map<HiscoreSkill, JLabel> activityLabels = new LinkedHashMap<>();
    private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();
    private final Map<HiscoreSkill, ImageIcon> dimmedIcons = new LinkedHashMap<>();

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

    private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
    private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

    // Captured in onActivate(), restored in onDeactivate()/shutdown()
    private int defaultDismissDelay;
    private int defaultInitialDelay = -1;

    // Click-to-reveal tooltip state
    private Popup activeClickPopup;
    private JLabel activeClickLabel;

    // Hover state — 1px border outline
    private JPanel hoveredCell;
    private Timer hoverExitTimer;
    private static final Border CELL_BORDER = new EmptyBorder(1, 1, 1, 1);
    private static final Color HOVER_OUTLINE_DIM = new Color(90, 90, 90);

    // 420 mode — unlocked when the 420 KC plugin is loaded
    private PluginManager pluginManager;
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

        NativeTooltip.loadSprites(spriteManager);
        SkillsTooltip.loadIcons(skillIconManager);

        // Combat level icon (crossed swords, sprite 168)
        spriteManager.getSpriteAsync(168, 0, sprite ->
        {
            if (sprite != null)
            {
                combatLevelImage = ImageUtil.resizeImage(
                    ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
            }
        });

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

        styleSearchBar(searchBar);
        for (Component c : searchBar.getComponents())
        {
            if (c instanceof net.runelite.client.ui.components.FlatTextField)
            {
                javax.swing.JTextField tf =
                    ((net.runelite.client.ui.components.FlatTextField) c).getTextField();
                tf.setFont(FontManager.getRunescapeFont());
                tf.setForeground(Color.WHITE);
                tf.setCaretColor(Color.WHITE);
                tf.putClientProperty(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            else if (c instanceof Container)
            {
                styleSearchBar((Container) c);
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

        trayToggle = new JLabel();
        ImageIcon hamburgerIcon = new ImageIcon(makeHamburgerIcon(HAMBURGER_COLOR));
        ImageIcon hamburgerHoverIcon = new ImageIcon(makeHamburgerIcon(HAMBURGER_HOVER_COLOR));
        trayToggle.setIcon(hamburgerIcon);
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
        // RuneLite's own Apache 2.0 resource — safe to bundle-load
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, "overall.png");
            skillsIcon = new ImageIcon(ImageUtil.resizeImage(img, 13, 13));
        }
        catch (Exception e)
        {
            skillsIcon = null;
        }

        // Cape icons + clog tier icons via ItemManager (game items, loaded at runtime)
        clientThread.invokeLater(() ->
        {
            loadItemIcon(13280, 26, 26, icon -> maxCapeIcon = icon);
            loadItemIcon(21295, 26, 26, icon -> infernalCapeIcon = icon);
            loadItemIcon(21284, 26, 26, icon -> infernalMaxCapeIcon = icon);

            for (int i = 0; i < CLOG_TIERS.length; i++)
            {
                final String tier = CLOG_TIERS[i];
                final int itemId = CLOG_TIER_ITEM_IDS[i];
                loadItemIcon(itemId, 13, 13, icon ->
                    clogTierIcons.put(tier, icon));
                loadItemIcon(itemId, 18, 18, icon ->
                    clogTierIconsLarge.put(tier, icon));
            }
        });
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
        JPanel statsRow = new JPanel(new GridLayout(1, 2));
        statsRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statsRow.setAlignmentX(0f);

        combatCell = new JLabel();
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
                String rsn = playerName.getText().trim();
                tip.setData(rsn.isEmpty() ? "Player" : rsn, hiscoreResult);
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
        grid.add(statsRow);

        JPanel statsSep = new JPanel();
        statsSep.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statsSep.setPreferredSize(new Dimension(0, 7));
        statsSep.setAlignmentX(0f);
        grid.add(statsSep);

        // Row 1: [Clue All] [3rd Age] [Gilded]
        JPanel row1 = new JPanel(new GridLayout(1, 3));
        row1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row1.setAlignmentX(0f);
        row1.add(makeActivityCell(HiscoreSkill.CLUE_SCROLL_ALL));
        row1.add(makeClueRareCell("3rd Age", THIRD_AGE_ITEM_ID, CLOG_THIRD_AGE, true));
        row1.add(makeClueRareCell("Gilded", GILDED_ITEM_ID, CLOG_GILDED, false));
        grid.add(row1);

        // Rows 2-3: Clue tiers
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

        // Row 4: Custom rare cells
        JPanel rareRow = new JPanel(new GridLayout(1, 3));
        rareRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rareRow.setAlignmentX(0f);
        rareRow.add(makeCustomRareCell("Hard (Rare)", CLUE_TIER_ITEM_IDS[3], RARE_HARD, HARD_RARE_ITEMS));
        rareRow.add(makeCustomRareCell("Elite (Rare)", CLUE_TIER_ITEM_IDS[4], RARE_ELITE, ELITE_RARE_ITEMS));
        rareRow.add(makeCustomRareCell("Master (Rare)", CLUE_TIER_ITEM_IDS[5], RARE_MASTER, MASTER_RARE_ITEMS));
        grid.add(rareRow);

        JPanel clueSep = new JPanel();
        clueSep.setBackground(ColorScheme.DARK_GRAY_COLOR);
        clueSep.setPreferredSize(new Dimension(0, 7));
        clueSep.setAlignmentX(0f);
        grid.add(clueSep);

        // Rows 5-7: Remaining activities
        JPanel rest = new JPanel(new GridLayout(0, 3));
        rest.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rest.setAlignmentX(0f);
        for (HiscoreSkill activity : new HiscoreSkill[]{
            HiscoreSkill.PVP_ARENA_RANK, HiscoreSkill.LEAGUE_POINTS,
            HiscoreSkill.LAST_MAN_STANDING,
            HiscoreSkill.SOUL_WARS_ZEAL, HiscoreSkill.RIFTS_CLOSED,
            HiscoreSkill.COLOSSEUM_GLORY,
            HiscoreSkill.BOUNTY_HUNTER_ROGUE, HiscoreSkill.BOUNTY_HUNTER_HUNTER})
        {
            rest.add(makeActivityCell(activity));
        }
        grid.add(rest);

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

                // Leaving a different cell — clear its outline first
                if (hoveredCell != null) clearCellOutline();

                hoveredCell = cell;
                Color fg = label.getForeground();
                Color outline = (fg.equals(KC_COLOR) || fg.equals(ColorScheme.LIGHT_GRAY_COLOR))
                    ? HOVER_OUTLINE_DIM : fg;
                cell.setBorder(new MatteBorder(1, 1, 1, 1, outline));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                if (hoverExitTimer != null) hoverExitTimer.stop();
                hoverExitTimer = new Timer(150, evt ->
                {
                    if (hoveredCell == cell)
                    {
                        clearCellOutline();
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

    private void clearCellOutline()
    {
        if (hoveredCell != null)
        {
            hoveredCell.setBorder(CELL_BORDER);
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
            clearCellOutline();
            hoveredCell = null;
        }
    }

    private void showClickTooltip(JLabel label, JPanel cell)
    {
        // Toggle off if clicking same cell
        if (label == activeClickLabel && activeClickPopup != null)
        {
            hideClickTooltip();
            return;
        }

        hideClickTooltip();

        JToolTip tip = label.createToolTip();
        tip.setTipText(label.getToolTipText());

        // Close button is now painted by NativeTooltip itself
        if (tip instanceof NativeTooltip)
        {
            ((NativeTooltip) tip).setCloseAction(() -> hideClickTooltip());
        }

        Dimension tipSize = tip.getPreferredSize();

        // Position below cell, screen-bounds aware
        Point loc = cell.getLocationOnScreen();
        Rectangle screen = cell.getGraphicsConfiguration().getBounds();

        int x = loc.x;
        int y = loc.y + cell.getHeight();

        if (x + tipSize.width > screen.x + screen.width)
        {
            x = screen.x + screen.width - tipSize.width;
        }
        if (y + tipSize.height > screen.y + screen.height)
        {
            y = loc.y - tipSize.height;
        }

        activeClickPopup = PopupFactory.getSharedInstance().getPopup(cell, tip, x, y);
        activeClickLabel = label;
        activeClickPopup.show();
    }

    private void hideClickTooltip()
    {
        if (activeClickPopup != null)
        {
            activeClickPopup.hide();
            activeClickPopup = null;
            activeClickLabel = null;
        }
    }

    /**
     * Build a sprite tooltip for a cell, or fall back to TextTooltip if no data available.
     *
     * @param owner     the label whose parent cell drives hover tint
     * @param data      tooltip data, or null for text fallback
     * @param gridCols  5 for standard, 10 for wide clue grids
     */
    private JToolTip makeSpriteTooltip(JLabel owner, TooltipData data, int gridCols)
    {
        JPanel parentCell = (JPanel) owner.getParent();
        if (data != null)
        {
            ImgTooltip tip = new ImgTooltip(gridCols);
            tip.setComponent(owner);
            tip.setTitle(data.name);
            tip.setObtained(data.obtainedCount, data.totalItems);
            tip.setRank(data.rank);
            tip.setItems(data.totalItems, data.allItemIds, data.obtainedIds,
                data.obtainedCounts, itemManager);
            keepTooltipOnHover(tip, parentCell);
            return tip;
        }
        TextTooltip fallback = new TextTooltip();
        fallback.setComponent(owner);
        return fallback;
    }

    /** Apply standard grid cell styling — font, placeholder text, color, gap, AA hint. */
    private static void styleLabel(JLabel label, String tooltipText)
    {
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setText(pad("--"));
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
        if (!Boolean.TRUE.equals(label.getClientProperty("noClog")))
        {
            addCellHoverEffect(cell, label);
        }
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
                return makeSpriteTooltip(this, tooltipDataMap.get(activity), 5);
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
                        if (NO_CLOG_ACTIVITIES.contains(activity))
                        {
                            originalIcons.put(activity, icon);
                            dimmedIcons.put(activity, new ImageIcon(createDimmedImage(icon)));
                        }
                    }
                }));
        }

        if (NO_CLOG_ACTIVITIES.contains(activity))
        {
            label.putClientProperty("noClog", Boolean.TRUE);
        }

        activityLabels.put(activity, label);
        return wrapInCell(label);
    }

    private JPanel makeClueTierCell(HiscoreSkill tier, int itemId, boolean wide)
    {
        JLabel label = new JLabel()
        {
            @Override
            public JToolTip createToolTip()
            {
                return makeSpriteTooltip(this, tooltipDataMap.get(tier), wide ? 10 : 5);
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
                return makeSpriteTooltip(this, data, 5);
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
                return makeSpriteTooltip(this, rareTooltips.get(rareKey), 5);
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
                {
                    BufferedImage loaded = itemManager.getImage(itemId, 1, false);
                    if (loaded != null)
                    {
                        label.setIcon(new ImageIcon(ImageUtil.resizeImage(loaded, 20, 20)));
                    }
                }));
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
                return makeSpriteTooltip(this, tooltipDataMap.get(boss), 5);
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
                dimmedIcons.put(boss, new ImageIcon(createDimmedImage(icon)));
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
        if (player.isEmpty())
        {
            setSearchStatus("Enter RSN", TEXT_DIM);
            return;
        }

        final int thisLookup = ++lookupVersion;
        int searchIdx = ThreadLocalRandom.current().nextInt(SEARCH_MSGS.length);
        setSearchStatus(String.format(SEARCH_MSGS[searchIdx], player), TEXT_DIM);
        searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);

        resetAllLabels();

        // Hiscore lookup
        hiscoreService.lookup(player).thenAccept(result ->
            SwingUtilities.invokeLater(() ->
            {
                if (thisLookup != lookupVersion) return;
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
                    combatCell.setText(pad(String.valueOf(combatLevel)));
                    combatCell.setForeground(getInfoColor());
                }

                int totalLevel = result.getTotalLevel();
                if (totalLevel > 0)
                {
                    totalLvlCell.setText(pad(String.valueOf(totalLevel)));
                    totalLvlCell.setForeground(getInfoColor());
                    totalLvlCell.setToolTipText(" ");
                }

                if (clogResult != null)
                {
                    updateRares(clogResult);
                    updateClogCell(clogResult);
                }

                searchBar.setText("");
                colorCompletedCells();
                updateTooltips();
            })
        ).exceptionally(ex ->
        {
            SwingUtilities.invokeLater(() ->
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
                colorCompletedCells();
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
                    clogNotice.setText(localRsn != null
                        && localRsn.equalsIgnoreCase(player)
                        ? "Open your Collection Log"
                        : " ");
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
        tooltipDataMap.clear();
        rareTooltips.clear();
        clogNotice.setText(" ");

        for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
        {
            JLabel label = entry.getValue();
            label.setText(pad("--"));
            label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            label.setToolTipText(null);
            ImageIcon orig = originalIcons.get(entry.getKey());
            if (orig != null) label.setIcon(orig);
        }

        resetLabelMap(activityLabels);

        clogInfoLabel.setIcon(null);
        clogInfoLabel.setText("");
        clogInfoLabel.setToolTipText(null);

        combatCell.setText(pad("--"));
        combatCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        totalLvlCell.setText(pad("--"));
        totalLvlCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        totalLvlCell.setToolTipText(null);

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
            label.setText(pad("--"));
            label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            label.setToolTipText(name);
        }
    }

    private static void resetLabelMap(Map<HiscoreSkill, JLabel> labels)
    {
        for (Map.Entry<HiscoreSkill, JLabel> entry : labels.entrySet())
        {
            JLabel label = entry.getValue();
            label.setText(pad("--"));
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

            label.setText(pad(score <= 0 ? "--" : formatKc(score)));
            label.setForeground(score > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

            // Restore original icon for no-clog activities (may have been dimmed)
            ImageIcon orig = originalIcons.get(activity);
            if (orig != null && NO_CLOG_ACTIVITIES.contains(activity))
            {
                label.setIcon(orig);
            }

            if (activity == HiscoreSkill.CLUE_SCROLL_ALL)
            {
                label.setToolTipText(buildClueTooltip(result));
            }
            else
            {
                int rank = result.getActivityRank(activity.getName());
                label.setToolTipText(rank > 0
                    ? activity.getName() + "\nRank: {w}" + String.format("%,d", rank)
                    : activity.getName());
            }
        }

        for (Map.Entry<HiscoreSkill, JLabel> entry : clueTierLabels.entrySet())
        {
            HiscoreSkill tier = entry.getKey();
            JLabel label = entry.getValue();
            int score = result.getActivityScore(tier.getName());
            label.setText(pad(score <= 0 ? "--" : formatKc(score)));
            label.setForeground(score > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

            String shortName = capitalizeTier(tier);
            int rank = result.getActivityRank(tier.getName());
            label.setToolTipText(rank > 0
                ? shortName + "\nRank: {w}" + String.format("%,d", rank)
                : shortName);
        }
    }

    private String buildClueTooltip(HiscoreResult result)
    {
        StringBuilder sb = new StringBuilder("Clue Scrolls");
        int allScore = result.getActivityScore(HiscoreSkill.CLUE_SCROLL_ALL.getName());
        if (allScore > 0)
        {
            sb.append(": {w}").append(String.format("%,d", allScore));
        }

        int rank = result.getActivityRank(HiscoreSkill.CLUE_SCROLL_ALL.getName());
        if (rank > 0)
        {
            sb.append("\nRank: {w}").append(String.format("%,d", rank));
        }
        return sb.toString();
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

            label.setText(pad(kc <= 0 ? "--" : formatKc(kc)));
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
                        label.setText(pad(formatKc(display)));
                        if (display == 420) label.setForeground(FOUR_TWENTY_GREEN);
                    }
                    break;
                case ALL_420:
                    if (kc > 0)
                    {
                        label.setText(pad("420"));
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

        List<Integer> allItems = result.getCategoryItems().get(clogCategory);
        List<ClogResult.ClogItem> obtained = result.getObtainedItems().get(clogCategory);

        if (allItems == null || allItems.isEmpty())
        {
            label.setToolTipText(name);
            return;
        }

        Set<Integer> obtainedIds = new HashSet<>();
        Map<Integer, Integer> obtainedCounts = new LinkedHashMap<>();
        if (obtained != null)
        {
            for (ClogResult.ClogItem item : obtained)
            {
                obtainedIds.add(item.getId());
                obtainedCounts.put(item.getId(), item.getCount());
            }
        }

        int obtainedCount = countObtained(allItems, obtainedIds);
        label.setText(pad(obtainedCount > 0 ? formatKc(obtainedCount) : "--"));
        label.setForeground(obtainedCount > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

        TooltipData data = new TooltipData(name, -1, obtainedCount,
            allItems.size(), allItems, obtainedIds, obtainedCounts);

        if (isThirdAge) thirdAgeTooltipData = data;
        else gildedTooltipData = data;

        label.setToolTipText(" ");
    }

    /**
     * Update a custom rare cell by scanning obtained items across ALL Temple categories.
     * These categories don't exist in Temple, so we match hardcoded item IDs against
     * whatever the player has obtained anywhere in their clog.
     */
    private void updateCustomRare(JLabel label, String name, String rareKey,
                                      int[] itemIds, ClogResult result)
    {
        if (label == null) return;

        // Build a flat set of all obtained item IDs across every Temple category
        Set<Integer> allObtainedGlobal = new HashSet<>();
        Map<Integer, Integer> allCountsGlobal = new HashMap<>();
        for (List<ClogResult.ClogItem> catObtained : result.getObtainedItems().values())
        {
            for (ClogResult.ClogItem item : catObtained)
            {
                allObtainedGlobal.add(item.getId());
                allCountsGlobal.merge(item.getId(), item.getCount(), Integer::max);
            }
        }

        List<Integer> allItemsList = new ArrayList<>();
        Set<Integer> obtainedIds = new HashSet<>();
        Map<Integer, Integer> obtainedCounts = new LinkedHashMap<>();
        for (int id : itemIds)
        {
            allItemsList.add(id);
            if (allObtainedGlobal.contains(id))
            {
                obtainedIds.add(id);
                obtainedCounts.put(id, allCountsGlobal.getOrDefault(id, 1));
            }
        }

        int obtainedCount = obtainedIds.size();
        label.setText(pad(obtainedCount > 0 ? formatKc(obtainedCount) : "--"));
        label.setForeground(obtainedCount > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

        rareTooltips.put(rareKey, new TooltipData(name, -1, obtainedCount,
            allItemsList.size(), allItemsList, obtainedIds, obtainedCounts));

        label.setToolTipText(" ");
    }

    private void updateClogCell(ClogResult result)
    {
        int[] totals = sumClogTotals(result);
        if (totals[0] > 0)
        {
            String tierName = getClogTierName(totals[0], totals[1]);
            ImageIcon largeIcon = tierName != null ? clogTierIconsLarge.get(tierName) : null;
            if (largeIcon != null)
            {
                clogInfoLabel.setIcon(new ImageIcon(createBoostedImage(largeIcon, 1.10f)));
            }
            clogInfoLabel.setText(pad(formatKc(totals[0])));
            clogInfoLabel.setForeground(getInfoColor());

            String tooltip = getClogTierTooltip(totals[0], totals[1]);
            String syncLine = syncLine(clogLastChanged);
            clogInfoLabel.setToolTipText(syncLine != null ? tooltip + "\n" + syncLine : tooltip);
        }
    }

    // -------------------------------------------------------------------------
    // Tooltip data building
    // -------------------------------------------------------------------------

    private void updateTooltips()
    {
        try
        {
            rebuildTooltips();
        }
        catch (Exception e)
        {
            log.warn("Failed to update tooltips", e);
        }
    }

    private void rebuildTooltips()
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
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptySet(),
                    java.util.Collections.emptyMap()));
                label.setToolTipText(" ");
                continue;
            }

            TooltipData data = buildTooltipData(bossName, category, rank);
            if (data == null)
            {
                int total = clogService.getCategoryItemCount(category);
                tooltipDataMap.put(skill, new TooltipData(
                    bossName, rank, -1, Math.max(total, 0),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptySet(),
                    java.util.Collections.emptyMap()));
                label.setToolTipText(" ");
                continue;
            }

            tooltipDataMap.put(skill, data);
            preloadItemImages(data);
            label.setToolTipText(" ");
        }

        // Activity and clue tier cells with clog categories
        rebuildActivityTooltips(ACTIVITY_CATEGORIES, activityLabels, HiscoreSkill::getName);
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
            TooltipData data = buildTooltipData(nameOf.apply(skill), entry.getValue(), rank);
            if (data == null) continue;

            tooltipDataMap.put(skill, data);
            preloadItemImages(data);
            label.setToolTipText(" ");
        }
    }

    /**
     * Build TooltipData for a clog category from the current clogResult.
     * Returns null if no item data exists for the category.
     */
    private TooltipData buildTooltipData(String displayName, String category, int rank)
    {
        if (clogResult == null) return null;

        List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(category);
        List<Integer> allItems = clogResult.getCategoryItems().get(category);

        if ((obtained == null || obtained.isEmpty()) && (allItems == null || allItems.isEmpty()))
        {
            return null;
        }

        Set<Integer> obtainedIds = getObtainedIds(category);
        Map<Integer, Integer> obtainedCounts = new LinkedHashMap<>();
        if (obtained != null)
        {
            for (ClogResult.ClogItem item : obtained)
            {
                obtainedCounts.put(item.getId(), item.getCount());
            }
        }

        int totalItems = allItems != null ? allItems.size() : obtainedIds.size();
        int obtainedCount = allItems != null
            ? countObtained(allItems, obtainedIds) : obtainedIds.size();

        List<Integer> itemList = allItems != null ? allItems : new ArrayList<>(obtainedIds);
        return new TooltipData(displayName, rank, obtainedCount, totalItems,
            itemList, obtainedIds, obtainedCounts);
    }

    /** Trigger async loads for all items in a tooltip — so they're cached by hover time. */
    private void preloadItemImages(TooltipData data)
    {
        for (int itemId : data.allItemIds)
        {
            int count = data.obtainedIds.contains(itemId)
                ? data.obtainedCounts.getOrDefault(itemId, 1) : 1;
            itemManager.getImage(itemId, count, false);
        }
    }

    // -------------------------------------------------------------------------
    // Completionist highlighter
    // -------------------------------------------------------------------------

    private void colorCompletedCells()
    {
        toggleHighlighter(config.completionistHighlighter());
    }

    private void toggleHighlighter(boolean enabled)
    {
        if (hiscoreResult == null) return;
        clearCellOutline();
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

        updateBosses(hiscoreResult);
        updateActivities(hiscoreResult);
        if (enabled)
        {
            dimNoClogActivities();
        }
        if (clogResult != null)
        {
            updateRares(clogResult);
            if (enabled)
            {
                colorCellsByCompletion();
            }
        }
        if (enabled)
        {
            colorEmptyCells();
        }
    }

    private void colorCellsByCompletion()
    {
        for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
        {
            HiscoreSkill skill = entry.getKey();
            String hiscoreName = NAME_OVERRIDES.getOrDefault(skill.getName(), skill.getName());
            colorBossCell(entry.getValue(), hiscoreName);
        }

        colorActivityCategories(ACTIVITY_CATEGORIES, activityLabels);
        colorActivityCategories(CLUE_CATEGORIES, clueTierLabels);

        // Clue All — aggregate across all 6 tier categories
        JLabel clueAllLabel = activityLabels.get(HiscoreSkill.CLUE_SCROLL_ALL);
        if (clueAllLabel != null
            && hiscoreResult.getActivityScore(HiscoreSkill.CLUE_SCROLL_ALL.getName()) > 0)
        {
            int totalItems = 0;
            int totalObtained = 0;
            for (String cat : CLUE_CATEGORIES.values())
            {
                List<Integer> items = clogResult.getCategoryItems().get(cat);
                if (items != null)
                {
                    totalItems += items.size();
                    totalObtained += countObtained(items, getObtainedIds(cat));
                }
            }
            if (totalItems > 0)
            {
                clueAllLabel.setForeground(clogColor(totalObtained, totalItems));
            }
        }

        if (thirdAgeCell != null) colorByCompletion(thirdAgeCell, CLOG_THIRD_AGE);
        if (gildedCell != null) colorByCompletion(gildedCell, CLOG_GILDED);
        colorCustomRare(hardRare, RARE_HARD);
        colorCustomRare(eliteRare, RARE_ELITE);
        colorCustomRare(masterRare, RARE_MASTER);
    }

    /** Dim activities without clog categories when highlighter is active. */
    private void dimNoClogActivities()
    {
        for (HiscoreSkill noClog : NO_CLOG_ACTIVITIES)
        {
            JLabel label = activityLabels.get(noClog);
            if (label == null) continue;

            int score = hiscoreResult.getActivityScore(noClog.getName());
            label.setForeground(score > 0 ? config.inProgressClogColor() : config.emptyClogColor());

            ImageIcon dimmed = dimmedIcons.get(noClog);
            if (dimmed != null)
            {
                label.setIcon(dimmed);
            }
        }
    }

    /** Recolor "--" cells to emptyClogColor when highlighter is active. */
    private void colorEmptyCells()
    {
        for (JLabel label : bossLabels.values())
        {
            if (ColorScheme.LIGHT_GRAY_COLOR.equals(label.getForeground()))
            {
                label.setForeground(config.emptyClogColor());
            }
        }
        for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
        {
            if (!NO_CLOG_ACTIVITIES.contains(entry.getKey())
                && ColorScheme.LIGHT_GRAY_COLOR.equals(entry.getValue().getForeground()))
            {
                entry.getValue().setForeground(config.emptyClogColor());
            }
        }
        for (JLabel label : clueTierLabels.values())
        {
            if (ColorScheme.LIGHT_GRAY_COLOR.equals(label.getForeground()))
            {
                label.setForeground(config.emptyClogColor());
            }
        }
    }

    private void colorActivityCategories(Map<HiscoreSkill, String> categories, Map<HiscoreSkill, JLabel> labels)
    {
        for (Map.Entry<HiscoreSkill, String> entry : categories.entrySet())
        {
            JLabel label = labels.get(entry.getKey());
            if (label != null)
            {
                colorClogCell(label, entry.getValue(),
                    hiscoreResult.getActivityScore(entry.getKey().getName()));
            }
        }
    }

    private void colorBossCell(JLabel label, String hiscoreName)
    {
        // 420 mode green wins over highlighter colors
        if (fourTwentyMode != FourTwentyMode.OFF && FOUR_TWENTY_GREEN.equals(label.getForeground()))
        {
            return;
        }

        int kc = hiscoreResult.getKc(hiscoreName);
        if (kc <= 0) return;

        colorClogCell(label,
            ClogService.bossToCategory(hiscoreName), kc);
    }

    private void colorClogCell(JLabel label, String category, int score)
    {
        if (score <= 0) return;
        colorByCompletion(label, category);
    }

    private Color clogColor(int obtained, int total)
    {
        if (obtained == total) return config.completedClogColor();
        if (obtained == total - 1 && total > 1) return config.missing1Color();
        if (obtained == 0) return config.emptyClogColor();
        return config.inProgressClogColor();
    }

    private void colorByCompletion(JLabel label, String category)
    {
        List<Integer> allItems = clogResult.getCategoryItems().get(category);
        if (allItems == null || allItems.isEmpty()) return;

        label.setForeground(clogColor(
            countObtained(allItems, getObtainedIds(category)), allItems.size()));
    }

    private void colorCustomRare(JLabel label, String rareKey)
    {
        if (label == null) return;
        TooltipData data = rareTooltips.get(rareKey);
        if (data == null) return;

        label.setForeground(clogColor(data.obtainedCount, data.totalItems));
    }

    // -------------------------------------------------------------------------
    // Public interface
    // -------------------------------------------------------------------------

    public void setPlayerName(String name)
    {
        searchBar.setText(name);
    }

    public void setLoggedInPlayer(String name)
    {
        this.localRsn = name;
    }

    public void setNameAutocompleter(NameAutocompleter autocompleter)
    {
        this.nameAutocompleter = autocompleter;
        for (Component c : searchBar.getComponents())
        {
            if (c instanceof net.runelite.client.ui.components.FlatTextField)
            {
                ((net.runelite.client.ui.components.FlatTextField) c).getTextField()
                    .addKeyListener(autocompleter);
                break;
            }
        }
    }

    public void setPluginManager(PluginManager pluginManager)
    {
        this.pluginManager = pluginManager;
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
        ToolTipManager.sharedInstance().setDismissDelay(defaultDismissDelay);
        if (defaultInitialDelay >= 0)
        {
            ToolTipManager.sharedInstance().setInitialDelay(defaultInitialDelay);
            defaultInitialDelay = -1;
        }
        hideClickTooltip();
    }

    /** Safety net — restores tooltip delay if plugin is disabled while panel is active. */
    public void shutdown()
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

    private String syncLine(String lastChanged)
    {
        if (lastChanged == null || lastChanged.isEmpty()) return null;
        try
        {
            LocalDateTime syncTime = LocalDateTime.parse(lastChanged,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return "Last update: " + syncTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }

    private ImageIcon getClogTierIcon(int obtained, int totalSlots)
    {
        String tier = getClogTierName(obtained, totalSlots);
        return tier != null ? clogTierIcons.get(tier) : null;
    }

    static String getClogTierName(int obtained, int totalSlots)
    {
        int gildedThreshold = (int) (totalSlots * 0.9) / 25 * 25;
        if (obtained >= gildedThreshold) return "gilded";
        for (int i = CLOG_TIER_THRESHOLDS.length - 1; i >= 0; i--)
        {
            if (obtained >= CLOG_TIER_THRESHOLDS[i]) return CLOG_TIERS[i];
        }
        return null;
    }

    static String getClogTierTooltip(int obtained, int totalSlots)
    {
        int gildedThreshold = (int) (totalSlots * 0.9) / 25 * 25;
        String currentTier = getClogTierName(obtained, totalSlots);
        String line1 = "Obtained: {w}" + obtained + "/" + totalSlots;

        if (currentTier == null)
        {
            return line1 + "\n" + (CLOG_TIER_THRESHOLDS[0] - obtained) + " more until {bronze}";
        }
        if ("gilded".equals(currentTier))
        {
            return line1 + "\n" + gildedThreshold + "+: {gilded}";
        }

        int tierIndex = -1;
        for (int i = 0; i < CLOG_TIERS.length; i++)
        {
            if (CLOG_TIERS[i].equals(currentTier)) { tierIndex = i; break; }
        }

        int currentThreshold = CLOG_TIER_THRESHOLDS[tierIndex];
        int nextThreshold;
        String nextTier;
        if (tierIndex + 1 < CLOG_TIER_THRESHOLDS.length)
        {
            nextThreshold = CLOG_TIER_THRESHOLDS[tierIndex + 1];
            nextTier = CLOG_TIERS[tierIndex + 1];
        }
        else
        {
            nextThreshold = gildedThreshold;
            nextTier = "gilded";
        }

        return line1
            + "\n" + currentThreshold + "-" + (nextThreshold - 1) + ": {" + currentTier + "}"
            + "\n" + (nextThreshold - obtained) + " more until {" + nextTier + "}";
    }

    // -------------------------------------------------------------------------
    // Info bar
    // -------------------------------------------------------------------------

    private void updateInfoIcon(AccountType type)
    {
        String resource;
        switch (type)
        {
            case IRONMAN:
                resource = "ironman.png";
                break;
            case HARDCORE_IRONMAN:
                resource = "hardcore_ironman.png";
                break;
            case ULTIMATE_IRONMAN:
                resource = "ultimate_ironman.png";
                break;
            default:
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
            if (!result.hasItemName(id)) missing.add(id);
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
                        result.putItemName(id, name);
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

    // -------------------------------------------------------------------------
    // Clog data helpers
    // -------------------------------------------------------------------------

    private Set<Integer> getObtainedIds(String category)
    {
        Set<Integer> ids = new HashSet<>();
        List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(category);
        if (obtained != null)
        {
            for (ClogResult.ClogItem item : obtained) ids.add(item.getId());
        }
        return ids;
    }

    private static int countObtained(List<Integer> allItems, Set<Integer> obtainedIds)
    {
        int count = 0;
        for (int id : allItems) if (obtainedIds.contains(id)) count++;
        return count;
    }

    private static int[] sumClogTotals(ClogResult result)
    {
        Set<Integer> allItems = new HashSet<>();
        Set<Integer> allObtained = new HashSet<>();
        for (Map.Entry<String, List<Integer>> entry : result.getCategoryItems().entrySet())
        {
            allItems.addAll(entry.getValue());
            List<ClogResult.ClogItem> obtained = result.getObtainedItems().get(entry.getKey());
            if (obtained != null)
            {
                for (ClogResult.ClogItem item : obtained) allObtained.add(item.getId());
            }
        }
        return new int[]{allObtained.size(), allItems.size()};
    }

    // -------------------------------------------------------------------------
    // Image utilities
    // -------------------------------------------------------------------------

    private static String pad(String text)
    {
        return StringUtils.leftPad(text, 4);
    }

    private static BufferedImage iconToImage(ImageIcon icon)
    {
        if (icon == null) return null;
        BufferedImage img = new BufferedImage(
            icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    private static String formatKc(int kc)
    {
        if (kc >= 1_000_000) return kc / 1_000_000 + "m";
        if (kc >= 10_000) return kc / 1_000 + "k";
        return String.valueOf(kc);
    }

    private static BufferedImage createDimmedImage(ImageIcon icon)
    {
        BufferedImage original = new BufferedImage(
            icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = original.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        BufferedImage dimmed = new BufferedImage(
            original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dimmed.createGraphics();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g2.drawImage(original, 0, 0, null);
        g2.dispose();
        return dimmed;
    }


    /**
     * Boosts RGB channels by a multiplier (e.g. 1.10 = 10% brighter).
     * Preserves hue and alpha — no white wash, just more vivid color.
     */
    private static BufferedImage createBoostedImage(ImageIcon icon, float factor)
    {
        BufferedImage src = new BufferedImage(
            icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = src.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        int[] pixels = src.getRGB(0, 0, src.getWidth(), src.getHeight(), null, 0, src.getWidth());
        for (int i = 0; i < pixels.length; i++)
        {
            int a = (pixels[i] >> 24) & 0xFF;
            int r = Math.min(255, (int) (((pixels[i] >> 16) & 0xFF) * factor));
            int gr = Math.min(255, (int) (((pixels[i] >> 8) & 0xFF) * factor));
            int b = Math.min(255, (int) ((pixels[i] & 0xFF) * factor));
            pixels[i] = (a << 24) | (r << 16) | (gr << 8) | b;
        }
        src.setRGB(0, 0, src.getWidth(), src.getHeight(), pixels, 0, src.getWidth());
        return src;
    }

    private static final Color HAMBURGER_COLOR = new Color(70, 70, 70);
    private static final Color HAMBURGER_HOVER_COLOR = new Color(83, 83, 83);

    /** Paints a 12x10 hamburger icon — three 2px-thick horizontal lines on transparent. */
    private static BufferedImage makeHamburgerIcon(Color barColor)
    {
        int w = 12, h = 10;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(barColor);
        g.fillRect(1, 0, w - 2, 2);   // top line
        g.fillRect(1, 4, w - 2, 2);   // middle line (1px gap)
        g.fillRect(1, 8, w - 2, 2);   // bottom line (1px gap)
        g.dispose();
        return img;
    }

    private static void styleSearchBar(Container container)
    {
        for (Component c : container.getComponents())
        {
            if (c instanceof JButton)
            {
                JButton btn = (JButton) c;
                btn.setOpaque(false);
                btn.setContentAreaFilled(false);
                btn.setBorderPainted(false);
            }
            else if (c instanceof Container)
            {
                styleSearchBar((Container) c);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Data holders
    // -------------------------------------------------------------------------

    /** Immutable data holder for sprite tooltip content. */
    private static final class TooltipData
    {
        final String name;
        final int rank;
        final int obtainedCount;
        final int totalItems;
        final List<Integer> allItemIds;
        final Set<Integer> obtainedIds;
        final Map<Integer, Integer> obtainedCounts;

        TooltipData(String name, int rank, int obtainedCount, int totalItems,
                    List<Integer> allItemIds, Set<Integer> obtainedIds,
                    Map<Integer, Integer> obtainedCounts)
        {
            this.name = name;
            this.rank = rank;
            this.obtainedCount = obtainedCount;
            this.totalItems = totalItems;
            this.allItemIds = allItemIds;
            this.obtainedIds = obtainedIds;
            this.obtainedCounts = obtainedCounts;
        }
    }

    /** Minimal scrollbar — slim thumb, no arrow buttons, dark theme. */
    private static class MinimalScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI
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
