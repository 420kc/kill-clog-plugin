package com.killclog;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KillClogPanel extends PluginPanel
{
    private static final Color TEXT_DIM = new Color(160, 160, 160);
    private static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);
    private static final Color KC_COLOR = new Color(215, 215, 215);
    private static final Color CELL_HOVER = new Color(41, 41, 41);
    private static final Color SYNC_COLOR = new Color(102, 102, 102);
    private static final Color FOUR_TWENTY_GREEN = new Color(30, 200, 30);
    private static final long STALE_DAYS = 90;

    private static final String[] SEARCHING_MESSAGES = {
        "Throwing a search party for %s...",
        "Moving mountains to find %s...",
        "Deliberating on %s's whereabouts...",
        "Searching high and low for %s...",
    };

    private static final String[] NOT_FOUND_MESSAGES = {
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
    // See BOSS_NAMES comment in HiscoreService for full playbook.
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

    // Map HiscoreSkill.getName() -> boss name as it appears in hiscore CSV data
    private static final Map<String, String> NAME_OVERRIDES = new LinkedHashMap<>();
    static
    {
        NAME_OVERRIDES.put("Calvar'ion", "Cal'varion");
    }

    // Activities shown in the collapsible grid (10 cells, matching native RuneLite order).
    // Individual clue tiers have spriteId -1, so they're excluded from the grid
    // and instead shown in the Clue Scrolls (all) tooltip.
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

    private final HiscoreService hiscoreService;
    private final ClogService clogService;
    private final KillClogConfig config;
    private final ConfigManager configManager;
    private final SpriteManager spriteManager;
    private final ItemManager itemManager;
    private final ClientThread clientThread;

    private final IconTextField searchBar = new IconTextField();
    private final JLabel infoNameLabel = new JLabel(" ")
    {
        @Override
        public javax.swing.JToolTip createToolTip()
        {
            ParchmentTooltip tip = new ParchmentTooltip();
            tip.setComponent(this);
            return tip;
        }
    };
    private final JLabel infoTotalLabel = new JLabel()
    {
        @Override
        public javax.swing.JToolTip createToolTip()
        {
            ParchmentTooltip tip = new ParchmentTooltip();
            tip.setComponent(this);
            for (Map.Entry<String, ImageIcon> entry : clogTierIcons.entrySet())
            {
                tip.putIcon(entry.getKey(), iconToImage(entry.getValue()));
            }
            tip.putIcon("rigour", iconToImage(rigourIcon));
            tip.putIcon("eagle_eye", iconToImage(eagleEyeIcon));
            return tip;
        }
    };
    private final JLabel infoKcLabel = new JLabel()
    {
        @Override
        public javax.swing.JToolTip createToolTip()
        {
            ParchmentTooltip tip = new ParchmentTooltip();
            tip.setComponent(this);
            if (combatLevelImage != null)
            {
                tip.putIcon("combat", combatLevelImage);
            }
            return tip;
        }
    };
    private final JLabel clogNotice = new JLabel();
    private final JLabel syncLabel = new JLabel()
    {
        @Override
        public javax.swing.JToolTip createToolTip()
        {
            ParchmentTooltip tip = new ParchmentTooltip();
            tip.setComponent(this);
            return tip;
        }
    };
    private ImageIcon skillsIcon;
    private ImageIcon maxCapeIcon;
    private ImageIcon infernalCapeIcon;
    private ImageIcon infernalMaxCapeIcon;
    private ImageIcon clogBookIcon;
    private ImageIcon staleBaguetteIcon;
    private BufferedImage staleBaguetteImage;
    private BufferedImage combatLevelImage;
    private ImageIcon rigourIcon;
    private ImageIcon sharpEyeIcon;
    private ImageIcon eagleEyeIcon;

    // Collection log tier icons — bronze through gilded
    private static final String[] CLOG_TIERS = {"bronze", "iron", "steel", "black", "mithril", "adamant", "rune", "dragon", "gilded"};
    private static final int[] CLOG_TIER_THRESHOLDS = {100, 300, 500, 700, 900, 1000, 1100, 1200};
    private final Map<String, ImageIcon> clogTierIcons = new LinkedHashMap<>();

    // Track labels for updating after lookup
    private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();
    private final Map<HiscoreSkill, JLabel> activityLabels = new LinkedHashMap<>();

    // Store original and dimmed icons
    private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();
    private final Map<HiscoreSkill, ImageIcon> dimmedIcons = new LinkedHashMap<>();

    // Activities toggle bar
    private JPanel activitiesGrid;
    private JLabel toggleArrow;
    private ImageIcon arrowUpIcon;
    private ImageIcon arrowDownIcon;
    private boolean activitiesExpanded;

    // Current lookup state
    private HiscoreResult hiscoreResult;
    private ClogResult clogResult;
    private boolean clogLookupDone;
    private String canonicalPlayerName;
    private String clogLastChanged;
    private boolean showingNotFound;
    private String loggedInPlayerName;

    // Sprite tooltip data per boss (populated when clog data is available)
    private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();

    // Original tooltip dismiss delay to restore when panel deactivates
    private int originalDismissDelay = ToolTipManager.sharedInstance().getDismissDelay();

    // Hover tint lock — keeps cell highlighted while its tooltip is showing
    private JPanel hoveredCell;
    private javax.swing.Timer hoverExitTimer;

    // 420 mode — unlocked when 420 kc plugin is loaded
    private PluginManager pluginManager;
    private NameAutocompleter nameAutocompleter;
    private FourTwentyMode fourTwentyMode = FourTwentyMode.OFF;
    private boolean has420Plugin;
    private JLabel thermyLabel;

    @Inject
    public KillClogPanel(HiscoreService hiscoreService, ClogService clogService,
                         KillClogConfig config, ConfigManager configManager,
                         SpriteManager spriteManager,
                         ItemManager itemManager, ClientThread clientThread)
    {
        super(true); // wrap in JScrollPane — RuneLite applies FlatLaf scrollbar
        this.hiscoreService = hiscoreService;
        this.clogService = clogService;
        this.config = config;
        this.configManager = configManager;
        this.spriteManager = spriteManager;
        this.itemManager = itemManager;
        this.clientThread = clientThread;

        ParchmentTooltip.loadBorderSprites(spriteManager);

        // Combat level icon (crossed swords, sprite 168)
        spriteManager.getSpriteAsync(168, 0, sprite ->
        {
            if (sprite != null)
            {
                combatLevelImage = ImageUtil.resizeImage(
                    ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
            }
        });

        // Sort arrow sprites for activities toggle
        spriteManager.getSpriteAsync(1050, 0, sprite ->
            SwingUtilities.invokeLater(() ->
            {
                if (sprite != null)
                {
                    arrowUpIcon = new ImageIcon(sprite);
                    if (activitiesExpanded && toggleArrow != null)
                    {
                        toggleArrow.setIcon(arrowUpIcon);
                    }
                }
            }));
        spriteManager.getSpriteAsync(1051, 0, sprite ->
            SwingUtilities.invokeLater(() ->
            {
                if (sprite != null)
                {
                    arrowDownIcon = new ImageIcon(sprite);
                    if (!activitiesExpanded && toggleArrow != null)
                    {
                        toggleArrow.setIcon(arrowDownIcon);
                    }
                }
            }));

        activitiesExpanded = config.activitiesExpanded();

        // Match native HiscorePanel structure
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

        // Toggle bar for activities section
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        add(buildToggleBar(), c);

        // Activities grid (starts hidden unless config says expanded)
        c.gridy++;
        activitiesGrid = buildActivitiesGrid();
        activitiesGrid.setVisible(activitiesExpanded);
        add(activitiesGrid, c);

        c.gridy++;
        add(buildBossGrid(), c);

        // Collection log sync notice — below boss grid
        c.gridy++;
        c.insets = new Insets(5, 0, 0, 0);
        clogNotice.setFont(FontManager.getRunescapeSmallFont());
        clogNotice.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        clogNotice.setHorizontalAlignment(JLabel.CENTER);
        clogNotice.setText(" ");
        clogNotice.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        add(clogNotice, c);

        // Configure PluginPanel's scroll pane with custom scrollbar
        JScrollPane sp = getScrollPane();
        if (sp != null)
        {
            sp.setBorder(null);
            sp.setViewportBorder(null);
            sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
            sp.getVerticalScrollBar().setUI(new MinimalScrollBarUI());
            sp.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(7, 0));
            sp.getVerticalScrollBar().setUnitIncrement(16);
        }
    }

    private JPanel buildSearchPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(null);

        // Search bar — matches native HiscorePanel (IconTextField with magnifying glass)
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.setPreferredSize(new java.awt.Dimension(0, 30));
        searchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchBar.addActionListener(e -> doLookup());

        // Configure internals: white text + greyscale AA, transparent clear button
        styleSearchButtons(searchBar);
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
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            else if (c instanceof java.awt.Container)
            {
                styleSearchButtons((java.awt.Container) c);
            }
        }

        panel.add(searchBar);
        panel.add(Box.createVerticalStrut(4));

        // Info bar: [badge+name LEFT] [total CENTER] [kc RIGHT]
        JPanel infoRow = new JPanel(new BorderLayout());
        infoRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoRow.setPreferredSize(new java.awt.Dimension(0, 18));

        infoNameLabel.setFont(FontManager.getRunescapeSmallFont());
        infoNameLabel.setForeground(TEXT_DIM);
        infoNameLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
        infoNameLabel.setIconTextGap(3);
        infoNameLabel.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        infoTotalLabel.setFont(FontManager.getRunescapeSmallFont());
        infoTotalLabel.setHorizontalAlignment(JLabel.CENTER);
        infoTotalLabel.setIconTextGap(3);
        infoTotalLabel.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        infoKcLabel.setFont(FontManager.getRunescapeSmallFont());
        infoKcLabel.setHorizontalAlignment(JLabel.RIGHT);
        infoKcLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
        infoKcLabel.setIconTextGap(3);
        infoKcLabel.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Sync icon sits beside clog count (or alone when no data)
        syncLabel.setFont(FontManager.getRunescapeSmallFont());
        syncLabel.setForeground(SYNC_COLOR);
        syncLabel.setIconTextGap(2);
        syncLabel.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        JPanel clogPanel = new JPanel();
        clogPanel.setLayout(new BoxLayout(clogPanel, BoxLayout.X_AXIS));
        clogPanel.setOpaque(false);
        clogPanel.add(Box.createHorizontalGlue());
        clogPanel.add(infoTotalLabel);
        clogPanel.add(Box.createHorizontalStrut(4));
        clogPanel.add(syncLabel);
        clogPanel.add(Box.createHorizontalGlue());

        infoRow.add(infoNameLabel, BorderLayout.WEST);
        infoRow.add(clogPanel, BorderLayout.CENTER);
        infoRow.add(infoKcLabel, BorderLayout.EAST);
        panel.add(infoRow);

        // Cache icons for info bar display
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, "overall.png");
            skillsIcon = new ImageIcon(ImageUtil.resizeImage(img, 13, 13));
        }
        catch (Exception e)
        {
            skillsIcon = null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "clan_maxed.png");
            maxCapeIcon = new ImageIcon(img);
        }
        catch (Exception e)
        {
            maxCapeIcon = null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "clan_tzkal.png");
            infernalCapeIcon = new ImageIcon(img);
        }
        catch (Exception e)
        {
            infernalCapeIcon = null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "clan_infernal_max.png");
            infernalMaxCapeIcon = new ImageIcon(img);
        }
        catch (Exception e)
        {
            infernalMaxCapeIcon = null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "clog_book.png");
            clogBookIcon = new ImageIcon(img);
        }
        catch (Exception e)
        {
            clogBookIcon = null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "stale_baguette.png");
            staleBaguetteImage = ImageUtil.resizeImage(img, 13, 12);
            staleBaguetteIcon = new ImageIcon(staleBaguetteImage);
        }
        catch (Exception e)
        {
            staleBaguetteIcon = null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "rigour.png");
            rigourIcon = new ImageIcon(ImageUtil.resizeImage(img, 13, 13));
        }
        catch (Exception e)
        {
            rigourIcon = null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "sharp_eye.png");
            sharpEyeIcon = new ImageIcon(ImageUtil.resizeImage(img, 13, 13));
        }
        catch (Exception e)
        {
            sharpEyeIcon = null;
        }
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "eagle_eye.png");
            eagleEyeIcon = new ImageIcon(ImageUtil.resizeImage(img, 13, 13));
        }
        catch (Exception e)
        {
            eagleEyeIcon = null;
        }
        for (String tier : CLOG_TIERS)
        {
            try
            {
                BufferedImage img = ImageUtil.loadImageResource(KillClogPanel.class, "clog_" + tier + ".png");
                clogTierIcons.put(tier, new ImageIcon(ImageUtil.resizeImage(img, 13, 13)));
            }
            catch (Exception e)
            {
                // Tier icon missing — fall back to clogBookIcon at display time
            }
        }

        return panel;
    }

    private JPanel buildToggleBar()
    {
        JPanel bar = new JPanel();
        bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bar.setLayout(new GridBagLayout());
        bar.setPreferredSize(new Dimension(0, 14));
        bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        toggleArrow = new JLabel();
        toggleArrow.setIcon(activitiesExpanded ? arrowUpIcon : arrowDownIcon);
        bar.add(toggleArrow);

        bar.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                activitiesExpanded = !activitiesExpanded;
                activitiesGrid.setVisible(activitiesExpanded);
                toggleArrow.setIcon(activitiesExpanded ? arrowUpIcon : arrowDownIcon);
                configManager.setConfiguration("killclog", "activitiesExpanded", activitiesExpanded);
                revalidate();
            }
        });

        return bar;
    }

    private JPanel buildActivitiesGrid()
    {
        JPanel grid = new JPanel(new GridLayout(0, 3));
        grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        for (HiscoreSkill activity : ACTIVITIES)
        {
            grid.add(makeActivityCell(activity));
        }

        return grid;
    }

    private JPanel makeActivityCell(HiscoreSkill activity)
    {
        JLabel label = new JLabel()
        {
            @Override
            public javax.swing.JToolTip createToolTip()
            {
                ParchmentTooltip tip = new ParchmentTooltip();
                tip.setComponent(this);
                return tip;
            }
        };
        label.setToolTipText(activity.getName());
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setText(pad("--"));
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setIconTextGap(4);
        label.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Load activity sprite asynchronously
        if (activity.getSpriteId() != -1)
        {
            spriteManager.getSpriteAsync(activity.getSpriteId(), 0, sprite ->
                SwingUtilities.invokeLater(() ->
                {
                    if (sprite != null)
                    {
                        BufferedImage scaled = ImageUtil.resizeImage(
                            ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20);
                        label.setIcon(new ImageIcon(scaled));
                    }
                }));
        }

        activityLabels.put(activity, label);

        JPanel cell = new JPanel();
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setBorder(new EmptyBorder(2, 0, 2, 0));
        cell.add(label);

        label.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (hoverExitTimer != null) hoverExitTimer.stop();
                if (hoveredCell != null && hoveredCell != cell)
                {
                    hoveredCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                }
                hoveredCell = cell;
                cell.setBackground(CELL_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                if (hoverExitTimer != null) hoverExitTimer.stop();
                hoverExitTimer = new javax.swing.Timer(150, evt ->
                {
                    if (hoveredCell == cell)
                    {
                        hoveredCell = null;
                        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    }
                });
                hoverExitTimer.setRepeats(false);
                hoverExitTimer.start();
            }
        });

        return cell;
    }

    private void updateActivityLabels(HiscoreResult result)
    {
        for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
        {
            HiscoreSkill activity = entry.getKey();
            JLabel label = entry.getValue();

            int score = result.getActivityScore(activity.getName());
            label.setText(pad(score <= 0 ? "--" : formatKc(score)));
            label.setForeground(score > 0 ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

            // Clue Scrolls (all) gets a special per-tier tooltip
            if (activity == HiscoreSkill.CLUE_SCROLL_ALL)
            {
                label.setToolTipText(buildClueTooltip(result));
            }
            else
            {
                int rank = result.getActivityRank(activity.getName());
                label.setToolTipText(rank > 0
                    ? activity.getName() + "\nRank: " + String.format("%,d", rank)
                    : activity.getName());
            }
        }
    }

    private String buildClueTooltip(HiscoreResult result)
    {
        StringBuilder sb = new StringBuilder("Clue Scrolls");
        int allScore = result.getActivityScore("Clue Scrolls (all)");
        if (allScore > 0)
        {
            sb.append(": ").append(String.format("%,d", allScore));
        }

        String[] tiers = {"Beginner", "Easy", "Medium", "Hard", "Elite", "Master"};
        String[] keys = {
            "Clue Scrolls (beginner)", "Clue Scrolls (easy)", "Clue Scrolls (medium)",
            "Clue Scrolls (hard)", "Clue Scrolls (elite)", "Clue Scrolls (master)"
        };

        for (int i = 0; i < tiers.length; i++)
        {
            int tierScore = result.getActivityScore(keys[i]);
            sb.append("\n").append(tiers[i]).append(": ");
            sb.append(tierScore > 0 ? String.format("%,d", tierScore) : "--");
        }

        int rank = result.getActivityRank("Clue Scrolls (all)");
        if (rank > 0)
        {
            sb.append("\nRank: ").append(String.format("%,d", rank));
        }

        return sb.toString();
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
            public javax.swing.JToolTip createToolTip()
            {
                TooltipData data = tooltipDataMap.get(boss);
                if (data != null)
                {
                    BossTooltip tip = new BossTooltip();
                    tip.setComponent(this);
                    tip.setData(data.bossName, data.rank, data.obtainedCount,
                        data.totalItems, data.allItemIds, data.obtainedIds,
                        data.obtainedCounts, itemManager);

                    // Keep cell hover tint while tooltip is active
                    JPanel parentCell = (JPanel) this.getParent();
                    tip.addMouseListener(new java.awt.event.MouseAdapter()
                    {
                        @Override
                        public void mouseEntered(java.awt.event.MouseEvent e)
                        {
                            if (hoverExitTimer != null) hoverExitTimer.stop();
                        }

                        @Override
                        public void mouseExited(java.awt.event.MouseEvent e)
                        {
                            if (hoveredCell == parentCell)
                            {
                                hoveredCell = null;
                                parentCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                            }
                        }
                    });

                    // Handle tooltip auto-dismiss (15s timeout)
                    tip.addHierarchyListener(e ->
                    {
                        if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                            && !tip.isShowing())
                        {
                            if (hoveredCell == parentCell)
                            {
                                hoveredCell = null;
                                parentCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                            }
                        }
                    });

                    return tip;
                }
                ParchmentTooltip fallback = new ParchmentTooltip();
                fallback.setComponent(this);
                return fallback;
            }
        };
        label.setToolTipText(boss.getName());
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setText(pad("--"));
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setIconTextGap(4);

        // Force greyscale AA — GASP resolution is broken on Windows,
        // resolves to LCD subpixel instead of greyscale despite font GASP table
        label.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Load boss sprite asynchronously
        spriteManager.getSpriteAsync(boss.getSpriteId(), 0, sprite ->
            SwingUtilities.invokeLater(() ->
            {
                if (sprite == null)
                {
                    return;
                }
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
            thermyLabel = label;
            label.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e)
                {
                    if (has420Plugin)
                    {
                        cycleFourTwentyMode();
                    }
                }
            });
        }

        JPanel cell = new JPanel();
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setBorder(new EmptyBorder(2, 0, 2, 0));
        cell.add(label);

        label.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e)
            {
                if (hoverExitTimer != null) hoverExitTimer.stop();
                if (hoveredCell != null && hoveredCell != cell)
                {
                    hoveredCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                }
                hoveredCell = cell;
                cell.setBackground(CELL_HOVER);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e)
            {
                if (hoverExitTimer != null) hoverExitTimer.stop();
                hoverExitTimer = new javax.swing.Timer(150, evt ->
                {
                    if (hoveredCell == cell)
                    {
                        hoveredCell = null;
                        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    }
                });
                hoverExitTimer.setRepeats(false);
                hoverExitTimer.start();
            }
        });

        return cell;
    }

    private static String pad(String text)
    {
        return StringUtils.leftPad(text, 4);
    }

    private static BufferedImage iconToImage(ImageIcon icon)
    {
        if (icon == null)
        {
            return null;
        }
        BufferedImage img = new BufferedImage(
            icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    private static String formatKc(int kc)
    {
        if (kc >= 1_000_000)
        {
            return kc / 1_000_000 + "m";
        }
        if (kc >= 10_000)
        {
            return kc / 1_000 + "k";
        }
        return String.valueOf(kc);
    }

    public void setPlayerName(String name)
    {
        searchBar.setText(name);
    }

    public void setLoggedInPlayer(String name)
    {
        this.loggedInPlayerName = name;
    }

    private boolean isLocalPlayer(String lookupName)
    {
        return loggedInPlayerName != null && lookupName != null
            && loggedInPlayerName.equalsIgnoreCase(lookupName);
    }

    public void setNameAutocompleter(NameAutocompleter autocompleter)
    {
        this.nameAutocompleter = autocompleter;
        // Register on the inner JTextField so e.getSource() returns a JTextComponent
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
        if (has420Plugin && thermyLabel != null)
        {
            thermyLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        }
    }

    public void setFourTwentyVisible(boolean visible)
    {
        has420Plugin = visible;
        if (thermyLabel != null)
        {
            thermyLabel.setCursor(visible
                ? new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)
                : java.awt.Cursor.getDefaultCursor());
        }
        if (!visible)
        {
            fourTwentyMode = FourTwentyMode.OFF;
            if (hiscoreResult != null)
            {
                applyHighlighterState(config.completionistHighlighter());
            }
        }
    }

    private volatile int lookupVersion = 0;

    public void doLookup()
    {
        String player = searchBar.getText().trim();
        if (player.isEmpty())
        {
            infoNameLabel.setIcon(null);
            infoNameLabel.setToolTipText(null);
            infoNameLabel.setText("Enter RSN");
            infoNameLabel.setForeground(TEXT_DIM);
            infoTotalLabel.setIcon(null);
            infoTotalLabel.setText("");
            infoKcLabel.setIcon(null);
            infoKcLabel.setText("");
            syncLabel.setIcon(null);
            syncLabel.setToolTipText(null);
            return;
        }

        final int thisLookup = ++lookupVersion;
        int searchIdx = ThreadLocalRandom.current().nextInt(SEARCHING_MESSAGES.length);
        infoNameLabel.setText(String.format(SEARCHING_MESSAGES[searchIdx], player));
        infoNameLabel.setForeground(TEXT_DIM);
        infoNameLabel.setIcon(null);
        infoNameLabel.setToolTipText(null);
        infoTotalLabel.setIcon(null);
        infoTotalLabel.setText("");
        infoKcLabel.setIcon(null);
        infoKcLabel.setText("");
        syncLabel.setIcon(null);
        syncLabel.setToolTipText(null);
        searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);

        // Clear previous results
        hiscoreResult = null;
        clogResult = null;
        clogLookupDone = false;
        clogLastChanged = null;
        canonicalPlayerName = null;
        showingNotFound = false;
        tooltipDataMap.clear();
        clogNotice.setText(" ");
        syncLabel.setText("");
        syncLabel.setIcon(null);
        syncLabel.setToolTipText(null);

        // Reset all labels to "--" and restore original icons
        for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
        {
            JLabel label = entry.getValue();
            label.setText(pad("--"));
            label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            label.setToolTipText(null);
            ImageIcon orig = originalIcons.get(entry.getKey());
            if (orig != null)
            {
                label.setIcon(orig);
            }
        }
        for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
        {
            JLabel label = entry.getValue();
            label.setText(pad("--"));
            label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            label.setToolTipText(entry.getKey().getName());
        }
        // Fire hiscore lookup
        hiscoreService.lookup(player).thenAccept(result ->
            SwingUtilities.invokeLater(() ->
            {
                if (thisLookup != lookupVersion) return; // stale result
                searchBar.setIcon(IconTextField.Icon.SEARCH);

                if (result == null)
                {
                    infoNameLabel.setIcon(null);
                    int notFoundIdx = ThreadLocalRandom.current().nextInt(NOT_FOUND_MESSAGES.length);
                    infoNameLabel.setText(String.format(NOT_FOUND_MESSAGES[notFoundIdx], player));
                    infoNameLabel.setForeground(NOT_FOUND);
                    showingNotFound = true;
                    searchBar.setText("");
                    return;
                }

                hiscoreResult = result;
                if (nameAutocompleter != null)
                {
                    nameAutocompleter.addToSearchHistory(player);
                }

                int totalLevel = result.getTotalLevel();

                infoNameLabel.setText(canonicalPlayerName != null ? canonicalPlayerName : player);
                infoNameLabel.setForeground(config.infoBarColor());
                updateInfoIcon(result.getAccountType());

                // RIGHT zone: total level icon progression
                // skills → infernal cape (Zuk KC) → max cape (2376) → infernal max cape (2376 + Zuk)
                if (totalLevel > 0)
                {
                    int zukKc = result.getKc("TzKal-Zuk");
                    int combatLevel = result.getCombatLevel();
                    ImageIcon levelIcon;
                    String levelTooltip;
                    if (totalLevel >= 2376 && zukKc >= 1 && infernalMaxCapeIcon != null)
                    {
                        levelIcon = infernalMaxCapeIcon;
                        levelTooltip = "Infernally Maxed";
                    }
                    else if (totalLevel >= 2376 && maxCapeIcon != null)
                    {
                        levelIcon = maxCapeIcon;
                        levelTooltip = "Maxed";
                    }
                    else if (zukKc >= 1 && infernalCapeIcon != null)
                    {
                        levelIcon = infernalCapeIcon;
                        levelTooltip = "Infernal";
                    }
                    else
                    {
                        levelIcon = skillsIcon;
                        levelTooltip = null;
                    }
                    // Append combat level to tooltip ({combat} icon drawn by ParchmentTooltip)
                    if (combatLevel > 0)
                    {
                        levelTooltip = (levelTooltip != null ? levelTooltip + "\n" : "")
                            + "{combat}" + combatLevel;
                    }
                    infoKcLabel.setIcon(levelIcon);
                    infoKcLabel.setToolTipText(levelTooltip);
                    infoKcLabel.setText(String.valueOf(totalLevel));
                    infoKcLabel.setForeground(config.infoBarColor());
                }
                else
                {
                    infoKcLabel.setIcon(null);
                    infoKcLabel.setText("");
                }

                // CENTER zone: clog X/Y + sync icon (populated when clog data arrives, or now if already here)
                if (clogResult != null)
                {
                    updateClogInfo(clogResult);
                    updateSyncLabel(clogResult.getLastChanged());
                }
                else if (clogLookupDone)
                {
                    updateSyncLabel(null);
                }

                searchBar.setText("");

                updateActivityLabels(result);
                applyCompletionistColors();
                updateTooltips();
            })
        ).exceptionally(ex ->
        {
            SwingUtilities.invokeLater(() ->
            {
                searchBar.setIcon(IconTextField.Icon.SEARCH);
                searchBar.setText("");
                infoNameLabel.setText("Lookup failed");
                infoNameLabel.setForeground(TEXT_DIM);
                infoNameLabel.setIcon(null);
                infoTotalLabel.setIcon(null);
                infoTotalLabel.setText("");
                infoKcLabel.setIcon(null);
                infoKcLabel.setText("");
            });
            return null;
        });

        // Fire clog lookup in parallel (if enabled)
        if (config.showCollectionLog())
        {
            clogService.lookup(player, isLocalPlayer(player)).thenAccept(result ->
                SwingUtilities.invokeLater(() ->
                {
                    if (thisLookup != lookupVersion) return; // stale result
                    clogResult = result;
                    clogLookupDone = true;
                    applyCompletionistColors();
                    updateTooltips();
                    if (result != null)
                    {
                        // Store canonical name from TempleOSRS; apply only if hiscore already loaded
                        String canonicalName = result.getPlayerName();
                        if (canonicalName != null && !canonicalName.isEmpty())
                        {
                            canonicalPlayerName = canonicalName;
                            if (hiscoreResult != null)
                            {
                                infoNameLabel.setText(canonicalName);
                            }
                        }

                        // Resolve untradeable item names via game cache on client thread
                        resolveUntradeableNames(result);

                        // Populate CENTER zone with total clog X/Y (only if hiscore already loaded)
                        if (hiscoreResult != null)
                        {
                            updateClogInfo(result);
                        }

                        // Only show sync icon once hiscore has loaded (searching is done)
                        if (hiscoreResult != null)
                        {
                            updateSyncLabel(result.getLastChanged());
                        }
                    }
                    else
                    {
                        if (isLocalPlayer(player))
                        {
                            clogNotice.setText("Open your Collection Log");
                        }
                        else
                        {
                            clogNotice.setText(" ");
                        }
                        if (hiscoreResult != null)
                        {
                            updateSyncLabel(null);
                        }
                        // Fallback: fetch canonical name from Temple player stats
                        fetchCanonicalName(player, thisLookup);
                    }
                })
            ).exceptionally(ex ->
            {
                log.warn("Clog lookup failed", ex);
                return null;
            });
        }
        else
        {
            // Clog disabled — still fetch canonical name
            fetchCanonicalName(player, thisLookup);
        }
    }

    private void fetchCanonicalName(String player, int thisLookup)
    {
        clogService.lookupCanonicalName(player).thenAccept(name ->
            SwingUtilities.invokeLater(() ->
            {
                if (thisLookup != lookupVersion) return;
                if (name != null && !name.isEmpty())
                {
                    canonicalPlayerName = name;
                    if (hiscoreResult != null)
                    {
                        infoNameLabel.setText(name);
                    }
                }
            })
        );
    }

    private void updateInfoIcon(AccountType type)
    {
        String resource;
        String tooltip;
        switch (type)
        {
            case IRONMAN:
                resource = "ironman.png";
                tooltip = "Ironman";
                break;
            case HARDCORE_IRONMAN:
                resource = "hardcore_ironman.png";
                tooltip = "Hardcore Ironman";
                break;
            case ULTIMATE_IRONMAN:
                resource = "ultimate_ironman.png";
                tooltip = "Ultimate Ironman";
                break;
            default:
                infoNameLabel.setIcon(null);
                infoNameLabel.setToolTipText(null);
                return;
        }

        try
        {
            BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, resource);
            infoNameLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 15, 15)));
            infoNameLabel.setToolTipText(tooltip);
        }
        catch (Exception e)
        {
            infoNameLabel.setIcon(null);
            infoNameLabel.setToolTipText(null);
        }
    }

    /**
     * Resolve item names missing from the Wiki API (untradeables like pets, jars)
     * by looking them up via ItemManager on the client thread.
     */
    private void resolveUntradeableNames(ClogResult result)
    {
        Set<Integer> allIds = new HashSet<>();
        for (List<ClogResult.ClogItem> items : result.getObtainedItems().values())
        {
            for (ClogResult.ClogItem item : items)
            {
                allIds.add(item.getId());
            }
        }
        for (List<Integer> ids : result.getCategoryItems().values())
        {
            allIds.addAll(ids);
        }

        List<Integer> missing = new ArrayList<>();
        for (int id : allIds)
        {
            if (!result.hasItemName(id))
            {
                missing.add(id);
            }
        }

        if (missing.isEmpty())
        {
            return;
        }

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
                    // Item not in cache, skip
                }
            }
            SwingUtilities.invokeLater(this::updateTooltips);
        });
    }

    private void updateBossLabels(HiscoreResult result)
    {
        Map<String, Integer> bosses = result.getBossKills();

        for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
        {
            HiscoreSkill skill = entry.getKey();
            JLabel label = entry.getValue();

            String hiscoreName = NAME_OVERRIDES.getOrDefault(skill.getName(), skill.getName());
            int kc = bosses.getOrDefault(hiscoreName, -1);
            boolean hasKc = kc > 0;

            label.setText(pad(kc <= 0 ? "--" : formatKc(kc)));
            label.setForeground(hasKc ? KC_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

            // Dim icon for bosses with no KC
            ImageIcon orig = originalIcons.get(skill);
            if (orig != null)
            {
                label.setIcon(hasKc ? orig : dimmedIcons.get(skill));
            }

            // Apply 420 mode on top of base state
            switch (fourTwentyMode)
            {
                case GREEN_420S:
                    if (kc == 420)
                    {
                        label.setForeground(FOUR_TWENTY_GREEN);
                    }
                    break;
                case CAP_420:
                    if (kc > 0)
                    {
                        int display = Math.min(kc, 420);
                        label.setText(pad(formatKc(display)));
                        if (display == 420)
                        {
                            label.setForeground(FOUR_TWENTY_GREEN);
                        }
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

    private void applyCompletionistColors()
    {
        applyHighlighterState(config.completionistHighlighter());
    }

    /**
     * Reset boss labels to their base state, then optionally apply completionist colors.
     * Used both for live updates and for hover preview on the toggle button.
     */
    private void applyHighlighterState(boolean enabled)
    {
        if (hiscoreResult == null)
        {
            return;
        }
        updateBossLabels(hiscoreResult);
        if (enabled && clogResult != null)
        {
            applyCompletionistColorsInner();
        }
    }

    /**
     * Build the set of obtained item IDs for a clog category.
     */
    private Set<Integer> getObtainedIds(String category)
    {
        Set<Integer> ids = new HashSet<>();
        List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(category);
        if (obtained != null)
        {
            for (ClogResult.ClogItem item : obtained)
            {
                ids.add(item.getId());
            }
        }
        return ids;
    }

    /**
     * Count how many items in allItems are in the obtained set.
     */
    private static int countObtained(List<Integer> allItems, Set<Integer> obtainedIds)
    {
        int count = 0;
        for (int id : allItems)
        {
            if (obtainedIds.contains(id))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Apply completionist highlighter colors — assumes hiscoreResult and clogResult are non-null.
     */
    private void applyCompletionistColorsInner()
    {
        for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
        {
            HiscoreSkill skill = entry.getKey();
            JLabel label = entry.getValue();

            String hiscoreName = NAME_OVERRIDES.getOrDefault(skill.getName(), skill.getName());
            applyCompletionistColorToLabel(label, hiscoreName);
        }

    }

    private void applyCompletionistColorToLabel(JLabel label, String hiscoreName)
    {
        // 420 mode green wins over highlighter colors
        if (fourTwentyMode != FourTwentyMode.OFF && FOUR_TWENTY_GREEN.equals(label.getForeground()))
        {
            return;
        }

        int kc = hiscoreResult.getKc(hiscoreName);
        if (kc <= 0)
        {
            return;
        }

        String category = ClogService.bossToCategory(hiscoreName);
        List<Integer> allItems = clogResult.getCategoryItems().get(category);

        if (allItems == null || allItems.isEmpty())
        {
            return;
        }

        Set<Integer> obtainedIds = getObtainedIds(category);
        int obtainedCount = countObtained(allItems, obtainedIds);

        if (obtainedCount == allItems.size())
        {
            label.setForeground(config.completedClogColor());
        }
        else if (obtainedCount == 0)
        {
            label.setForeground(config.emptyClogColor());
        }
        else
        {
            label.setForeground(config.inProgressClogColor());
        }
    }

    /**
     * Create a dimmed version of an icon at ~30% opacity.
     */
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

    private void updateTooltips()
    {
        try
        {
            updateTooltipsInner();
        }
        catch (Exception e)
        {
            log.warn("Failed to update tooltips", e);
        }
    }

    private void updateTooltipsInner()
    {
        tooltipDataMap.clear();

        for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
        {
            HiscoreSkill skill = entry.getKey();
            JLabel label = entry.getValue();

            String bossName = skill.getName();
            String hiscoreName = NAME_OVERRIDES.getOrDefault(bossName, bossName);

            int kc = -1;
            int rank = -1;
            if (hiscoreResult != null)
            {
                kc = hiscoreResult.getKc(hiscoreName);
                rank = hiscoreResult.getRank(hiscoreName);
            }

            // If no clog data or config disabled, show simple text tooltip
            if (clogResult == null || !config.showCollectionLog())
            {
                String tooltip = rank > 0
                    ? bossName + "\nRank: " + String.format("%,d", rank)
                    : bossName;
                label.setToolTipText(tooltip);
                continue;
            }

            // Check for clog data
            String category = ClogService.bossToCategory(hiscoreName);
            List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(category);
            List<Integer> allItems = clogResult.getCategoryItems().get(category);

            // No clog data for this boss — simple text tooltip
            if ((obtained == null || obtained.isEmpty()) && (allItems == null || allItems.isEmpty()))
            {
                String tooltip = rank > 0
                    ? bossName + "\nRank: " + String.format("%,d", rank)
                    : bossName;
                label.setToolTipText(tooltip);
                continue;
            }

            // Build sprite tooltip data
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
                ? countObtained(allItems, obtainedIds)
                : obtainedIds.size();

            TooltipData data = new TooltipData();
            data.bossName = bossName;
            data.rank = rank;
            data.obtainedCount = obtainedCount;
            data.totalItems = totalItems;
            data.allItemIds = allItems != null ? allItems : new ArrayList<>(obtainedIds);
            data.obtainedIds = obtainedIds;
            data.obtainedCounts = obtainedCounts;

            tooltipDataMap.put(skill, data);

            // Pre-load item images so they're cached by hover time
            for (int itemId : data.allItemIds)
            {
                int count = obtainedIds.contains(itemId)
                    ? obtainedCounts.getOrDefault(itemId, 1) : 1;
                itemManager.getImage(itemId, count, false);
            }

            // Non-null tooltip text activates ToolTipManager hover behavior
            label.setToolTipText(" ");
        }

    }

    private void cycleFourTwentyMode()
    {
        FourTwentyMode[] modes = FourTwentyMode.values();
        fourTwentyMode = modes[(fourTwentyMode.ordinal() + 1) % modes.length];

        // Reapply labels with new mode
        applyHighlighterState(config.completionistHighlighter());
    }

    /**
     * Toggle the Completionist's Highlighter on/off (called by keybind).
     * Persists the new state to config.
     */
    public void toggleHighlighter()
    {
        boolean newState = !config.completionistHighlighter();
        configManager.setConfiguration("killclog", "completionistHighlighter", newState);
    }

    /**
     * Called when Kill Clog config changes at runtime.
     */
    public void onConfigChanged(String key)
    {
        switch (key)
        {
            case "completionistHighlighter":
            case "completedClogColor":
            case "inProgressClogColor":
            case "emptyClogColor":
                applyHighlighterState(config.completionistHighlighter());
                updateTooltips();
                break;
            case "infoBarColor":
                if (hiscoreResult != null)
                {
                    infoNameLabel.setForeground(config.infoBarColor());
                    infoTotalLabel.setForeground(config.infoBarColor());
                    infoKcLabel.setForeground(config.infoBarColor());
                }
                break;
            case "showCollectionLog":
                applyHighlighterState(config.completionistHighlighter());
                updateTooltips();
                break;
        }
    }

    @Override
    public void onActivate()
    {
        originalDismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
        ToolTipManager.sharedInstance().setDismissDelay(15000);
    }

    @Override
    public void onDeactivate()
    {
        ToolTipManager.sharedInstance().setDismissDelay(originalDismissDelay);
    }

    /**
     * Safety net — restore tooltip delay if plugin is disabled while panel is active.
     */
    public void shutdown()
    {
        ToolTipManager.sharedInstance().setDismissDelay(originalDismissDelay);
    }

    private void updateSyncLabel(String lastChanged)
    {
        clogLastChanged = lastChanged;

        if (lastChanged == null || lastChanged.isEmpty())
        {
            // No Temple data — sharp eye stays visible in info bar
            syncLabel.setText("");
            syncLabel.setIcon(sharpEyeIcon);
            syncLabel.setToolTipText("Not synced to TempleOSRS");
            return;
        }

        // Has Temple data — sync info moves into clog tier tooltip, clear info bar icon
        syncLabel.setText("");
        syncLabel.setIcon(null);
        syncLabel.setToolTipText(null);
    }

    private void updateClogInfo(ClogResult result)
    {
        int[] totals = calculateTotalClog(result);
        if (totals[0] > 0)
        {
            ImageIcon tierIcon = getClogTierIcon(totals[0], totals[1]);
            infoTotalLabel.setIcon(tierIcon != null ? tierIcon : clogBookIcon);
            infoTotalLabel.setText(String.valueOf(totals[0]));
            infoTotalLabel.setForeground(config.infoBarColor());

            String tooltip = getClogTierTooltip(totals[0], totals[1]);
            String syncLine = buildSyncTooltipLine(clogLastChanged);
            if (syncLine != null)
            {
                tooltip = tooltip + "\n" + syncLine;
            }
            infoTotalLabel.setToolTipText(tooltip);
        }
    }

    /**
     * Build the sync freshness line for the clog tier tooltip.
     * Returns "{rigour}Last update: date" or "{eagle_eye}Last update: date", or null.
     */
    private String buildSyncTooltipLine(String lastChanged)
    {
        if (lastChanged == null || lastChanged.isEmpty())
        {
            return null;
        }

        try
        {
            DateTimeFormatter parser = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime syncTime = LocalDateTime.parse(lastChanged, parser);
            long daysAgo = ChronoUnit.DAYS.between(syncTime, LocalDateTime.now());

            DateTimeFormatter display = DateTimeFormatter.ofPattern("MMM d, yyyy");
            String icon = daysAgo > STALE_DAYS ? "{eagle_eye}" : "{rigour}";
            return icon + "Last update: " + syncTime.format(display);
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }

    /**
     * Get the collection log tier icon for a given obtained count.
     */
    private ImageIcon getClogTierIcon(int obtained, int totalSlots)
    {
        String tier = getClogTierName(obtained, totalSlots);
        return tier != null ? clogTierIcons.get(tier) : null;
    }

    /**
     * Resolve the collection log tier name for a given obtained count.
     * Bronze through Dragon are fixed thresholds; Gilded is 90% of total slots
     * rounded down to the nearest 25.
     */
    static String getClogTierName(int obtained, int totalSlots)
    {
        // Gilded: 90% of total slots rounded down to nearest 25
        int gildedThreshold = (int) (totalSlots * 0.9) / 25 * 25;
        if (obtained >= gildedThreshold)
        {
            return "gilded";
        }

        // Dragon down to Bronze — find highest qualifying tier
        for (int i = CLOG_TIER_THRESHOLDS.length - 1; i >= 0; i--)
        {
            if (obtained >= CLOG_TIER_THRESHOLDS[i])
            {
                return CLOG_TIERS[i];
            }
        }

        return null;
    }

    /**
     * Build tooltip text for the collection log tier icon.
     * Uses {tierName} placeholders for inline tier icons.
     * Format: "1100-1199: {rune} 18 until {dragon}"
     */
    static String getClogTierTooltip(int obtained, int totalSlots)
    {
        int gildedThreshold = (int) (totalSlots * 0.9) / 25 * 25;
        String currentTier = getClogTierName(obtained, totalSlots);

        if (currentTier == null)
        {
            // Below Bronze
            int needed = CLOG_TIER_THRESHOLDS[0] - obtained;
            return needed + " until {bronze}";
        }

        if ("gilded".equals(currentTier))
        {
            return gildedThreshold + "+: {gilded}";
        }

        // Find current tier index and compute next tier threshold
        int tierIndex = -1;
        for (int i = 0; i < CLOG_TIERS.length; i++)
        {
            if (CLOG_TIERS[i].equals(currentTier))
            {
                tierIndex = i;
                break;
            }
        }

        int currentThreshold = CLOG_TIER_THRESHOLDS[tierIndex];

        // Next tier
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

        int needed = nextThreshold - obtained;
        String range = currentThreshold + "-" + (nextThreshold - 1);
        return range + ": {" + currentTier + "} " + needed + " until {" + nextTier + "}";
    }

    /**
     * Recursively style all JButtons in a container to be transparent
     * (removes white background from IconTextField clear/suggest buttons).
     */
    private static void styleSearchButtons(java.awt.Container container)
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
            else if (c instanceof java.awt.Container)
            {
                styleSearchButtons((java.awt.Container) c);
            }
        }
    }

    /**
     * Calculate total collection log progress from category data.
     * Returns [obtained, total]. Obtained items are deduped by ID across categories.
     */
    private static int[] calculateTotalClog(ClogResult result)
    {
        int totalItems = 0;
        Set<Integer> allObtained = new HashSet<>();

        for (Map.Entry<String, List<Integer>> entry : result.getCategoryItems().entrySet())
        {
            String category = entry.getKey();
            totalItems += entry.getValue().size();

            List<ClogResult.ClogItem> obtained = result.getObtainedItems().get(category);
            if (obtained != null)
            {
                for (ClogResult.ClogItem item : obtained)
                {
                    allObtained.add(item.getId());
                }
            }
        }

        return new int[]{allObtained.size(), totalItems};
    }

    /**
     * Data holder for sprite tooltip content, populated during updateTooltips().
     */
    private static class TooltipData
    {
        String bossName;
        int rank;
        int obtainedCount;
        int totalItems;
        List<Integer> allItemIds;
        Set<Integer> obtainedIds;
        Map<Integer, Integer> obtainedCounts;
    }

    /**
     * Minimal scrollbar — slim thumb, no arrow buttons, dark theme.
     */
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
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled())
            {
                return;
            }
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
            java.awt.Dimension d = new java.awt.Dimension(0, 0);
            btn.setPreferredSize(d);
            btn.setMinimumSize(d);
            btn.setMaximumSize(d);
            return btn;
        }
    }

}
