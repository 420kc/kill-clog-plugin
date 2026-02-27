package com.killclog;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
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
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KillClogPanel extends PluginPanel
{
    private static final Color TEXT_DIM = new Color(160, 160, 160);

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

    private final JTextField playerInput = new JTextField();
    private final JButton lookupButton = new JButton("\uD83D\uDD0D");
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel clogNotice = new JLabel();
    private final JPanel resultsPanel = new JPanel();

    // Track labels for updating after lookup
    private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();

    // Store original icons for dimming/restoring
    private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();

    // Current lookup state
    private HiscoreResult hiscoreResult;
    private ClogResult clogResult;

    // Original tooltip dismiss delay to restore on shutdown
    private final int originalDismissDelay;

    @Inject
    public KillClogPanel(HiscoreService hiscoreService, ClogService clogService,
                         KillClogConfig config, ConfigManager configManager,
                         SpriteManager spriteManager,
                         ItemManager itemManager, ClientThread clientThread)
    {
        super(false);
        this.hiscoreService = hiscoreService;
        this.clogService = clogService;
        this.config = config;
        this.configManager = configManager;
        this.spriteManager = spriteManager;
        this.itemManager = itemManager;
        this.clientThread = clientThread;

        // Keep tooltips visible longer for reading item lists
        originalDismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
        ToolTipManager.sharedInstance().setDismissDelay(15000);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(null);

        add(buildSearchPanel(), BorderLayout.NORTH);

        JScrollPane scroll = buildResultsScroll();
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel buildSearchPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(10, 10, 5, 10));

        // Title
        JLabel title = new JLabel("Kill Clog");
        title.setFont(FontManager.getRunescapeFont().deriveFont(16f));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(8));

        // Search row
        JPanel searchRow = new JPanel(new BorderLayout(5, 0));
        searchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Dark search bar
        playerInput.setToolTipText("Enter RSN");
        playerInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        playerInput.setForeground(Color.WHITE);
        playerInput.setCaretColor(Color.WHITE);
        playerInput.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        playerInput.setPreferredSize(new java.awt.Dimension(0, 30));
        playerInput.addActionListener(e -> doLookup());
        searchRow.add(playerInput, BorderLayout.CENTER);

        lookupButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        lookupButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        lookupButton.setBorder(BorderFactory.createRaisedBevelBorder());
        lookupButton.setFocusPainted(false);
        lookupButton.setContentAreaFilled(true);
        lookupButton.setPreferredSize(new java.awt.Dimension(30, 30));
        lookupButton.addActionListener(e -> doLookup());
        lookupButton.getModel().addChangeListener(e ->
        {
            if (lookupButton.getModel().isPressed())
            {
                lookupButton.setBorder(BorderFactory.createLoweredBevelBorder());
            }
            else
            {
                lookupButton.setBorder(BorderFactory.createRaisedBevelBorder());
            }
        });
        searchRow.add(lookupButton, BorderLayout.EAST);

        panel.add(searchRow);
        panel.add(Box.createVerticalStrut(4));

        // Status
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setIconTextGap(3);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(statusLabel);

        // Collection log sync notice (hidden by default)
        clogNotice.setFont(FontManager.getRunescapeSmallFont());
        clogNotice.setForeground(new Color(180, 180, 100));
        clogNotice.setAlignmentX(Component.CENTER_ALIGNMENT);
        clogNotice.setVisible(false);
        panel.add(clogNotice);

        return panel;
    }

    private JScrollPane buildResultsScroll()
    {
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        resultsPanel.setBorder(new EmptyBorder(5, 5, 10, 5));

        // Build the static boss grid immediately (shows "--" until lookup)
        buildBossGrid();

        JScrollPane scroll = new JScrollPane(resultsPanel);
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private void buildBossGrid()
    {
        JPanel grid = new JPanel(new GridLayout(0, 3));
        grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        for (HiscoreSkill boss : BOSSES)
        {
            JPanel cell = makeBossCell(boss);
            grid.add(cell);
        }

        resultsPanel.add(grid);
    }

    private JPanel makeBossCell(HiscoreSkill boss)
    {
        JLabel label = new JLabel();
        label.setToolTipText(boss.getName());
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setText(pad("--"));
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setIconTextGap(4);

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
        playerInput.setText(name);
    }

    private volatile int lookupVersion = 0;

    public void doLookup()
    {
        String player = playerInput.getText().trim();
        if (player.isEmpty())
        {
            statusLabel.setIcon(null);
            statusLabel.setText("Enter RSN");
            statusLabel.setForeground(TEXT_DIM);
            return;
        }

        final int thisLookup = ++lookupVersion;
        statusLabel.setText("Looking up " + player + "...");
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setIcon(null);
        lookupButton.setEnabled(false);

        // Clear previous results
        hiscoreResult = null;
        clogResult = null;
        clogNotice.setVisible(false);

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
                lookupButton.setEnabled(true);

                if (result == null)
                {
                    statusLabel.setIcon(null);
                    statusLabel.setText("Player not found");
                    statusLabel.setForeground(TEXT_DIM);
                    return;
                }

                hiscoreResult = result;

                int totalBossKc = calculateTotalBossKc(result);
                String kcColor = totalBossKc == 0 ? "#ff4444" : "#ffffff";
                statusLabel.setText("<html><font color='" + kcColor + "'>" + escapeHtml(player)
                    + "</font><font color='#555555'> | </font><font color='" + kcColor + "'>"
                    + formatKc(totalBossKc) + " kc</font></html>");
                statusLabel.setForeground(Color.WHITE);
                updateStatusIcon(result.getAccountType());
                playerInput.setText("");

                updateBossLabels(result);
                updateTooltips();
            })
        ).exceptionally(ex ->
        {
            SwingUtilities.invokeLater(() ->
            {
                lookupButton.setEnabled(true);
                statusLabel.setText("Lookup failed");
                statusLabel.setForeground(TEXT_DIM);
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
                    updateTooltips();
                    if (result != null)
                    {
                        // Resolve untradeable item names via game cache on client thread
                        resolveUntradeableNames(result);
                    }
                    else
                    {
                        clogNotice.setText("No collection log \u2014 sync at templeosrs.com");
                        clogNotice.setVisible(true);
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
                statusLabel.setIcon(null);
                return;
        }

        try
        {
            BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, resource);
            statusLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 15, 15)));
        }
        catch (Exception e)
        {
            statusLabel.setIcon(null);
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
            label.setForeground(hasKc ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);

            // Dim icon for bosses with no KC
            ImageIcon orig = originalIcons.get(skill);
            if (orig != null)
            {
                label.setIcon(hasKc ? orig : new ImageIcon(createDimmedImage(orig)));
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

            int totalItems = allItems != null ? allItems.size() : obtainedIds.size();
            int obtainedCount = 0;

            if (allItems != null)
            {
                for (int itemId : allItems)
                {
                    if (obtainedIds.contains(itemId))
                    {
                        obtainedCount++;
                    }
                }
            }
            else
            {
                obtainedCount = obtainedIds.size();
            }

            boolean isComplete = totalItems > 0 && obtainedCount == totalItems;

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
                        html.append("<span style='color:#4caf6e;'>\u2713 ");
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
                    html.append("<span style='color:#4caf6e;'>\u2713 ");
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
     * Restore global state modified by this panel.
     */
    public void shutdown()
    {
        ToolTipManager.sharedInstance().setDismissDelay(originalDismissDelay);
    }

    private int calculateTotalBossKc(HiscoreResult result)
    {
        int total = 0;
        for (HiscoreSkill boss : BOSSES)
        {
            String hiscoreName = NAME_OVERRIDES.getOrDefault(boss.getName(), boss.getName());
            int kc = result.getKc(hiscoreName);
            if (kc > 0)
            {
                total += kc;
            }
        }
        return total;
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

}
