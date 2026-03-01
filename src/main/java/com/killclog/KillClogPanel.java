package com.killclog;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.swing.BorderFactory;
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
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KillClogPanel extends PluginPanel
{
    private static final Color TEXT_DIM = new Color(160, 160, 160);
    private static final Color KC_COLOR = new Color(215, 215, 215);

    private static final String[] SEARCHING_MESSAGES = {
        "Throwing a search party for %s...",
        "Moving mountains to find %s...",
        "Have you seen %s? I haven't...",
        "Deliberating on %s's whereabouts...",
        "Searching high and low for %s...",
    };

    private static final String[] NOT_FOUND_MESSAGES = {
        "WANTED: %s",
        "%s has gone AWOL",
        "%s must be touching grass",
        "%s? Never heard of 'em.",
        "%s has left the building",
        "%s who?",
    };

    // Boss display order matching vanilla RuneLite hiscores
    private static final HiscoreSkill[] BOSSES = {
        HiscoreSkill.ABYSSAL_SIRE,
        HiscoreSkill.ALCHEMICAL_HYDRA,
        HiscoreSkill.AMOXLIATL,
        HiscoreSkill.ARAXXOR,
        HiscoreSkill.ARTIO,
        HiscoreSkill.BARROWS_CHESTS,
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

    private final HiscoreService hiscoreService;
    private final ClogService clogService;
    private final KillClogConfig config;
    private final ConfigManager configManager;
    private final SpriteManager spriteManager;
    private final ItemManager itemManager;
    private final ClientThread clientThread;

    private final IconTextField searchBar = new IconTextField();
    private final JLabel statusNameLabel = new JLabel(" ");
    private final JLabel statusTotalLabel = new JLabel();
    private final JLabel statusKcLabel = new JLabel();
    private final JLabel clogNotice = new JLabel();
    private ImageIcon skillsIcon;

    // Track labels for updating after lookup
    private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();

    // Store original and dimmed icons
    private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();
    private final Map<HiscoreSkill, ImageIcon> dimmedIcons = new LinkedHashMap<>();

    // Current lookup state
    private HiscoreResult hiscoreResult;
    private ClogResult clogResult;
    private boolean showingNotFound;

    // Original tooltip dismiss delay to restore when panel deactivates
    private int originalDismissDelay;

    @Inject
    public KillClogPanel(HiscoreService hiscoreService, ClogService clogService,
                         KillClogConfig config, ConfigManager configManager,
                         SpriteManager spriteManager,
                         ItemManager itemManager, ClientThread clientThread)
    {
        super(); // PluginPanel wraps in JScrollPane with RuneLite-styled scrollbar
        this.hiscoreService = hiscoreService;
        this.clogService = clogService;
        this.config = config;
        this.configManager = configManager;
        this.spriteManager = spriteManager;
        this.itemManager = itemManager;
        this.clientThread = clientThread;

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

        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
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

        // Explicitly configure PluginPanel's scroll pane with RuneLite scrollbar UI
        JScrollPane sp = getScrollPane();
        if (sp != null)
        {
            sp.setBorder(null);
            sp.setViewportBorder(null);
            sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
            sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI()
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
                private JButton makeZeroButton()
                {
                    JButton btn = new JButton();
                    java.awt.Dimension d = new java.awt.Dimension(0, 0);
                    btn.setPreferredSize(d);
                    btn.setMinimumSize(d);
                    btn.setMaximumSize(d);
                    return btn;
                }
            });
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

        // Configure internals: white text + greyscale AA, remove clear/suggest buttons
        removeClearButtons(searchBar);
        for (Component c : searchBar.getComponents())
        {
            if (c instanceof net.runelite.client.ui.components.FlatTextField)
            {
                javax.swing.JTextField tf =
                    ((net.runelite.client.ui.components.FlatTextField) c).getTextField();
                tf.setForeground(Color.WHITE);
                tf.setCaretColor(Color.WHITE);
                tf.putClientProperty(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
        }

        panel.add(searchBar);
        panel.add(Box.createVerticalStrut(4));

        // Status row: [badge+name LEFT] [total CENTER] [kc RIGHT]
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusNameLabel.setFont(FontManager.getRunescapeSmallFont());
        statusNameLabel.setForeground(TEXT_DIM);
        statusNameLabel.setIconTextGap(3);
        statusNameLabel.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        statusTotalLabel.setFont(FontManager.getRunescapeSmallFont());
        statusTotalLabel.setHorizontalAlignment(JLabel.CENTER);
        statusTotalLabel.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        statusKcLabel.setFont(FontManager.getRunescapeSmallFont());
        statusKcLabel.setHorizontalAlignment(JLabel.RIGHT);
        statusKcLabel.setIconTextGap(3);
        statusKcLabel.putClientProperty(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        statusRow.add(statusNameLabel, BorderLayout.WEST);
        statusRow.add(statusTotalLabel, BorderLayout.CENTER);
        statusRow.add(statusKcLabel, BorderLayout.EAST);
        panel.add(statusRow);

        // Cache skills icon for total level display
        try
        {
            BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, "overall.png");
            skillsIcon = new ImageIcon(ImageUtil.resizeImage(img, 13, 13));
        }
        catch (Exception e)
        {
            skillsIcon = null;
        }

        return panel;
    }

    private JPanel buildBossGrid()
    {
        JPanel grid = new JPanel(new GridLayout(0, 3));
        grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        for (HiscoreSkill boss : BOSSES)
        {
            JPanel cell = makeBossCell(boss);
            grid.add(cell);
        }

        return grid;
    }

    private JPanel makeBossCell(HiscoreSkill boss)
    {
        JLabel label = new JLabel();
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

        JPanel cell = new JPanel();
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setBorder(new EmptyBorder(2, 0, 2, 0));
        cell.add(label);

        return cell;
    }

    private static String pad(String text)
    {
        return StringUtils.leftPad(text, 4);
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

    private volatile int lookupVersion = 0;

    public void doLookup()
    {
        String player = searchBar.getText().trim();
        if (player.isEmpty())
        {
            statusNameLabel.setIcon(null);
            statusNameLabel.setText("Enter RSN");
            statusNameLabel.setForeground(TEXT_DIM);
            statusTotalLabel.setText("");
            statusKcLabel.setIcon(null);
            statusKcLabel.setText("");
            return;
        }

        final int thisLookup = ++lookupVersion;
        int searchIdx = ThreadLocalRandom.current().nextInt(SEARCHING_MESSAGES.length);
        statusNameLabel.setText(String.format(SEARCHING_MESSAGES[searchIdx], player));
        statusNameLabel.setForeground(TEXT_DIM);
        statusNameLabel.setIcon(null);
        statusTotalLabel.setText("");
        statusKcLabel.setIcon(null);
        statusKcLabel.setText("");
        searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);

        // Clear previous results
        hiscoreResult = null;
        clogResult = null;
        showingNotFound = false;
        clogNotice.setText(" ");

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

        // Fire hiscore lookup
        hiscoreService.lookup(player).thenAccept(result ->
            SwingUtilities.invokeLater(() ->
            {
                if (thisLookup != lookupVersion) return; // stale result
                searchBar.setIcon(IconTextField.Icon.SEARCH);

                if (result == null)
                {
                    statusNameLabel.setIcon(null);
                    int notFoundIdx = ThreadLocalRandom.current().nextInt(NOT_FOUND_MESSAGES.length);
                    statusNameLabel.setText(String.format(NOT_FOUND_MESSAGES[notFoundIdx], player));
                    statusNameLabel.setForeground(config.notFoundColor());
                    showingNotFound = true;
                    searchBar.setText("");
                    return;
                }

                hiscoreResult = result;

                int totalLevel = result.getTotalLevel();
                boolean isMaxed = totalLevel >= 2376;

                statusNameLabel.setText(player);
                statusNameLabel.setForeground(config.statusBarColor());
                updateStatusIcon(result.getAccountType());

                // RIGHT zone: skills icon + total level (2376 = maxed, colored when highlighter on)
                if (totalLevel > 0)
                {
                    statusKcLabel.setIcon(skillsIcon);
                    statusKcLabel.setText(String.valueOf(totalLevel));
                    statusKcLabel.setForeground(isMaxed && config.completionistHighlighter()
                        ? config.completedClogColor() : config.statusBarColor());
                }
                else
                {
                    statusKcLabel.setIcon(null);
                    statusKcLabel.setText("");
                }

                // CENTER zone: clog X/Y (populated when clog data arrives, or now if already here)
                if (clogResult != null)
                {
                    updateClogStatus(clogResult);
                }

                searchBar.setText("");

                applyCompletionistColors();
                updateTooltips();
            })
        ).exceptionally(ex ->
        {
            SwingUtilities.invokeLater(() ->
            {
                searchBar.setIcon(IconTextField.Icon.SEARCH);
                searchBar.setText("");
                statusNameLabel.setText("Lookup failed");
                statusNameLabel.setForeground(TEXT_DIM);
                statusNameLabel.setIcon(null);
                statusTotalLabel.setText("");
                statusKcLabel.setIcon(null);
                statusKcLabel.setText("");
            });
            return null;
        });

        // Fire clog lookup in parallel (if enabled)
        if (config.showCollectionLog())
        {
            clogService.lookup(player).thenAccept(result ->
                SwingUtilities.invokeLater(() ->
                {
                    if (thisLookup != lookupVersion) return; // stale result
                    clogResult = result;
                    applyCompletionistColors();
                    updateTooltips();
                    if (result != null)
                    {
                        // Resolve untradeable item names via game cache on client thread
                        resolveUntradeableNames(result);

                        // Populate CENTER zone with total clog X/Y (only if hiscore already loaded)
                        if (hiscoreResult != null)
                        {
                            updateClogStatus(result);
                        }
                    }
                    else
                    {
                        clogNotice.setText("Sync at TempleOSRS");
                    }
                })
            ).exceptionally(ex ->
            {
                log.warn("Clog lookup failed", ex);
                return null;
            });
        }
    }

    private void updateStatusIcon(AccountType type)
    {
        String resource;
        switch (type)
        {
            case IRONMAN:
            case DE_IRONED:
                resource = "ironman.png";
                break;
            case HARDCORE_IRONMAN:
                resource = "hardcore_ironman.png";
                break;
            case ULTIMATE_IRONMAN:
                resource = "ultimate_ironman.png";
                break;
            default:
                statusNameLabel.setIcon(null);
                return;
        }

        try
        {
            BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, resource);
            statusNameLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 15, 15)));
        }
        catch (Exception e)
        {
            statusNameLabel.setIcon(null);
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

        // Update maxed (2376) color in status bar (now in RIGHT zone)
        if ("2376".equals(statusKcLabel.getText()))
        {
            statusKcLabel.setForeground(enabled
                ? config.completedClogColor() : config.statusBarColor());
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
            int kc = hiscoreResult.getKc(hiscoreName);

            if (kc <= 0)
            {
                continue;
            }

            String category = ClogService.bossToCategory(hiscoreName);
            List<Integer> allItems = clogResult.getCategoryItems().get(category);

            if (allItems == null || allItems.isEmpty())
            {
                continue;
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

    /**
     * Format a rank as colored HTML.
     * Tiers: #1 gold+WOW, top 10 pink, top 25 red, top 50 blue, top 1000 green, beyond gray.
     */
    private static String formatRankHtml(int rank)
    {
        if (rank <= 0)
        {
            return "";
        }

        String color;
        String suffix = "";

        if (rank == 1)
        {
            color = "#c9a84c";
            suffix = " WOW!";
        }
        else if (rank <= 10)
        {
            color = "#e87acc";
        }
        else if (rank <= 25)
        {
            color = "#c05050";
        }
        else if (rank <= 50)
        {
            color = "#4a9ee5";
        }
        else if (rank <= 1000)
        {
            color = "#4caf6e";
        }
        else
        {
            color = "#666666";
        }

        return " <span style='color:" + color + ";'>#" + rank + suffix + "</span>";
    }

    private void updateTooltipsInner()
    {
        String obtainedColor = "#4caf6e";
        if (config.completionistHighlighter())
        {
            Color c = config.completedClogColor();
            obtainedColor = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        }

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

            // If no clog data or config disabled, show simple tooltip
            if (clogResult == null || !config.showCollectionLog())
            {
                StringBuilder tooltip = new StringBuilder("<html>");
                tooltip.append(escapeHtml(bossName));
                tooltip.append(formatRankHtml(rank));
                if (kc > 0)
                {
                    tooltip.append(" \u2014 ").append(kc).append(" kc");
                }
                tooltip.append("</html>");
                label.setToolTipText(tooltip.toString());
                continue;
            }

            // Build rich HTML tooltip with collection log items
            String category = ClogService.bossToCategory(hiscoreName);

            List<ClogResult.ClogItem> obtained = clogResult.getObtainedItems().get(category);
            List<Integer> allItems = clogResult.getCategoryItems().get(category);

            // No clog data for this boss
            if ((obtained == null || obtained.isEmpty()) && (allItems == null || allItems.isEmpty()))
            {
                StringBuilder tooltip = new StringBuilder("<html>");
                tooltip.append(escapeHtml(bossName));
                tooltip.append(formatRankHtml(rank));
                if (kc > 0)
                {
                    tooltip.append(" \u2014 ").append(kc).append(" kc");
                }
                tooltip.append("</html>");
                label.setToolTipText(tooltip.toString());
                continue;
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
                ? countObtained(allItems, obtainedIds)
                : obtainedIds.size();

            StringBuilder html = new StringBuilder();
            html.append("<html><body style='padding:4px;'>");

            // Header: Boss Name (obtained/total) — gold when complete
            String headerColor = "#ffffff";
            html.append("<b style='color:").append(headerColor).append(";'>");
            html.append(escapeHtml(bossName));
            html.append(" (").append(obtainedCount).append("/").append(totalItems).append(")");
            html.append("</b>");

            html.append(formatRankHtml(rank));

            if (kc > 0)
            {
                html.append("<span style='color:#ffffff;'> \u2014 ").append(kc).append(" kc</span>");
            }

            html.append("<br>");

            // Item list
            if (allItems != null)
            {
                for (int itemId : allItems)
                {
                    boolean hasItem = obtainedIds.contains(itemId);
                    String itemName = clogResult.getItemName(itemId);

                    if (hasItem)
                    {
                        int count = obtainedCounts.getOrDefault(itemId, 1);
                        html.append("<span style='color:").append(obtainedColor).append(";'>\u2713 ");
                        html.append(escapeHtml(itemName));
                        if (count > 1)
                        {
                            html.append(" (x").append(count).append(")");
                        }
                        html.append("</span><br>");
                    }
                    else
                    {
                        html.append("<span style='color:#888888;'>\u25a1 ");
                        html.append(escapeHtml(itemName));
                        html.append("</span><br>");
                    }
                }
            }
            else if (obtained != null)
            {
                for (ClogResult.ClogItem item : obtained)
                {
                    String itemName = clogResult.getItemName(item.getId());
                    html.append("<span style='color:").append(obtainedColor).append(";'>\u2713 ");
                    html.append(escapeHtml(itemName));
                    if (item.getCount() > 1)
                    {
                        html.append(" (x").append(item.getCount()).append(")");
                    }
                    html.append("</span><br>");
                }
            }

            html.append("</body></html>");
            label.setToolTipText(html.toString());
        }
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
            case "statusBarColor":
                if (hiscoreResult != null)
                {
                    statusNameLabel.setForeground(config.statusBarColor());
                    statusTotalLabel.setForeground(config.statusBarColor());
                    if (!"2376".equals(statusKcLabel.getText()))
                    {
                        statusKcLabel.setForeground(config.statusBarColor());
                    }
                }
                break;
            case "notFoundColor":
                if (showingNotFound)
                {
                    statusNameLabel.setForeground(config.notFoundColor());
                }
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

    private void updateClogStatus(ClogResult result)
    {
        int[] totals = calculateTotalClog(result);
        if (totals[1] > 0)
        {
            statusTotalLabel.setText(totals[0] + "/" + totals[1]);
            statusTotalLabel.setForeground(config.statusBarColor());
        }
    }

    /**
     * Recursively remove all JButtons from a container (strips IconTextField clear/suggest buttons).
     * Removal prevents updateContextButton() from re-showing them on text entry.
     */
    private static void removeClearButtons(java.awt.Container container)
    {
        for (Component c : container.getComponents())
        {
            if (c instanceof JButton)
            {
                container.remove(c);
            }
            else if (c instanceof java.awt.Container)
            {
                removeClearButtons((java.awt.Container) c);
            }
        }
    }

    /**
     * Calculate total collection log progress from category data.
     * Returns [obtained, total] — no dedup needed, categories are unique in Temple data.
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



    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

}
