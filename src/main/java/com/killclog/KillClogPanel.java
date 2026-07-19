package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
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
import net.runelite.client.util.ImageUtil;

@Slf4j
public class KillClogPanel extends PluginPanel
	implements LookupSession.Listener, ComparisonController.Listener,
	ComparisonController.CellRenderTarget
{
	private static final Color TEXT_DIM = new Color(160, 160, 160);
	private static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);
	static final Color KC_COLOR = new Color(215, 215, 215);
	private static final String SYNC_NOTICE = "Open Collection Log and click";

	/** Info bar text color - only applies when highlighter is active AND clog data exists. */
	@Override
	public Color getInfoColor()
	{
		return config.completionistHighlighter() && lookupSession.getClogResult() != null
			? config.infoBarColor() : KC_COLOR;
	}

	private BufferedImage getSyncIcon()
	{
		return iconCache.syncNoticeIcon();
	}

	/**
	 * The stats row (combat, total level, pvp summary) takes the info color as
	 * one unit, dashes included: a "--" left in another color next to k-colored
	 * siblings reads as a broken cell, not an empty one.
	 */
	private void colorStatsRow()
	{
		Color infoColor = getInfoColor();
		combatCell.setForeground(infoColor);
		totalLvlCell.setForeground(infoColor);
		if (cells.getPvpSummaryCell() != null)
		{
			cells.getPvpSummaryCell().setForeground(infoColor);
		}
	}

	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final KillClogConfig config;
	private final ConfigManager configManager;
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final Client client;
	private final AccountBadgeResolver accountBadges;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final CaRewardSprites caRewardSprites;
	private final TooltipItemNameResolver itemNameResolver;
	private final PanelIconCache iconCache;
	private final PanelAccountTypes accountTypes;
	private final ActivitySummaryTooltips activityTooltips;
	private ProgressHighlighter highlighter;
	private JPanel infoRow;

	private final JLabel searchStatus = new JLabel(" ");
	private final IconTextField searchBar = new IconTextField();
	private final JLabel playerName = new UnderlineLabel(true)
	{
		@Override
		public JToolTip createToolTip()
		{
			if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
			{
				return buildComparePlayerSummary(this);
			}
			// Single player: standard player summary
			SummaryTooltip tip = new SummaryTooltip();
			tip.setComponent(this);
			String name = playerName.getText().trim();
			AccountDisplay display = accountTypes.displayIdentity(lookupSession.getHiscoreResult(),
				lookupSession.getClogResult(), lookupSession.getCurrentLookupRsn());
			tip.setData(
				name.isEmpty() ? "Player" : name,
				lookupSession.getHiscoreResult() != null ? lookupSession.getHiscoreResult().getOverallRank() : -1,
				getCapeImage(),
				accountBadges.badge(display),
				AccountBadgeResolver.label(display),
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

	};
	private final JLabel clogInfoLabel = new UnderlineLabel(false)
	{
		@Override
		public JToolTip createToolTip()
		{
			// Comparison mode: this label is the red RSN.
			if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
			{
				return buildComparePlayerSummary(this);
			}

			// Single player: standard clog summary
			ClogSummaryTooltip tip = new ClogSummaryTooltip();
			tip.setComponent(this);
			tip.setWikiLinksEnabled(config.wikiItemLinks());
			if (lookupSession.getClogResult() != null)
			{
				ClogResult clog = lookupSession.getClogResult();
				int[] totals = ClogHelper.sumClogTotals(clog);
				tip.setTierData(totals[0], totals[1], iconCache.clogTierImages());
				tip.setClogSources(clog.isFromTemple(), clog.isFromRuneProfile());
				if (lookupSession.getHiscoreResult() != null)
				{
					int clogRank = lookupSession.getHiscoreResult().getActivityRank("Collections Logged");
					tip.setRank(clogRank);
				}
				boolean stale = LookupQueries.isSyncStale(lookupSession.getClogLastChanged(), 90);
				String sync = LookupQueries.syncLine(lookupSession.getClogLastChanged(), stale);
				if (sync != null) tip.setSyncData(sync, stale);
				tip.setSpecialItems(
					ClogHelper.obtainedSpecialItems(PanelData.SPECIAL_ITEM_IDS, clog),
					clog, itemManager);
				tip.setRecentItems(LookupQueries.getRecentItems(clog, 4), clog, itemManager);
			}
			else
			{
				boolean isSelf = localRsn != null && rsn != null
					&& localRsn.equalsIgnoreCase(rsn);
				if (isSelf)
				{
					tip.setNotice(SYNC_NOTICE, getSyncIcon());
				}
				else if (lookupSession.getHiscoreResult() != null)
				{
					tip.setNotice(noClogNotice(rsn));
				}
				else
				{
					// No player yet: preview the log's shape from the catalog.
					ClogResult catalog = cells.unsyncedCatalogResult();
					if (catalog != null)
					{
						tip.setTitle("Clog Summary");
						tip.setObtainedPlaceholder(ClogHelper.sumClogTotals(catalog)[1]);
					}
					else
					{
						tip.setNotice("Loading catalog...");
					}
				}
			}
			return tip;
		}

	};
	private final JLabel clogNotice = new JLabel();

	private JLabel compareLabel;
	private SearchRowController searchRowController;
	private JTextField searchTextField;
	private JPanel searchRow;
	private CompareClogTotalsBar compareClogTotals;
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

	// 420 mode - unlocked when the 420 KC plugin is loaded
	private NameAutocompleter nameAutocompleter;
	private FourTwentyMode fourTwentyMode = FourTwentyMode.OFF;
	private boolean has420Plugin;

	// No-data copy stays quiet: another player simply has no synced collection log.
	// No provider names, no "missing data" framing - just a calm statement.
	private static String noClogNotice(String rsn)
	{
		return rsn != null && !rsn.isEmpty()
			? rsn + " hasn't synced a collection log"
			: "No collection log synced";
	}

	@Inject
	public KillClogPanel(HiscoreService hiscoreService, ClogService clogService,
		RuneProfileService runeProfileService,
		KillClogConfig config, ConfigManager configManager,
		SpriteManager spriteManager,
		ItemManager itemManager, ClientThread clientThread,
		SkillIconManager skillIconManager, Client client)
	{
		super(true); // wrap in JScrollPane
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.config = config;
		this.configManager = configManager;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.client = client;
		this.accountBadges = new AccountBadgeResolver(client);
		this.tooltipDataBuilder = new TooltipDataBuilder(itemManager);
		this.caRewardSprites = new CaRewardSprites(itemManager, clientThread, this::repaint);
		this.iconCache = new PanelIconCache(itemManager, clientThread, spriteManager);
		this.accountTypes = new PanelAccountTypes(runeProfileService);
		this.tooltipController = new TooltipController(config);
		this.lookupSession = new LookupSession(hiscoreService, clogService, runeProfileService, config, null, this);
		this.comparison = new ComparisonController(hiscoreService, clogService, runeProfileService,
			lookupSession, itemManager, config, tooltipController, tooltipDataBuilder, this);
		this.comparison.setRenderTarget(this);
		this.cells = new Cells(spriteManager, itemManager, tooltipController, comparison, tooltipDataBuilder, lookupSession, clogService);
		this.activityTooltips = new ActivitySummaryTooltips(
			lookupSession, comparison, cells, tooltipController, itemManager,
			caRewardSprites, iconCache, this::comparisonBlueName, config::wikiItemLinks);
		this.itemNameResolver = new TooltipItemNameResolver(clientThread, itemManager,
			this::onTooltipItemNamesResolved);
		this.cells.setUnsyncedCatalogResolver(itemNameResolver::resolve);
		this.comparison.setUnsyncedCatalogResolver(itemNameResolver::resolve);
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

		reloadTooltipSprites();
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

		// Compare entry controls live in the search row.

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

		// Cold start: warm the catalog so every cell previews the log's shape
		// (dimmed grids, --/Y slot counts) before any player has been searched.
		clogService.warmCatalog().thenRun(() ->
			SwingUtilities.invokeLater(() -> cells.rebuildPrimaryTooltips(localRsn)));
	}

	private void onTooltipItemNamesResolved()
	{
		cells.rebuildPrimaryTooltips(localRsn);
		if (comparison.isComparisonMode())
		{
			comparison.rebuildTooltipData();
			comparison.updateAllCells();
		}
	}

	// Panel construction.

	private JPanel buildSearchPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(null);

		PanelSearchBox.configureStatus(searchStatus, TEXT_DIM);
		panel.add(searchStatus);

		searchTextField = PanelSearchBox.configureSearchBar(searchBar);
		compareLabel = new JLabel();
		searchRow = PanelSearchBox.buildSearchRow(
			searchBar, compareLabel,
			() -> searchRowController != null ? searchRowController.compareIconWidth() : 0);
		searchRowController = new SearchRowController(
			searchRow,
			searchBar,
			compareLabel,
			searchTextField,
			TEXT_DIM,
			comparison::isComparisonMode,
			comparison::isCompareLookupInFlight,
			this::doLookup,
			this::doCompareLookupFromSearchBar,
			comparison::exit,
			() -> setSearchStatus(" ", TEXT_DIM),
			this::lookupSelfFromSearchIcon,
			this::revalidate);
		searchRowController.install();

		compareClogTotals = new CompareClogTotalsBar(
			comparison::isComparisonMode,
			config::tooltipMode,
			tooltipController,
			this::buildCompareClogSummary);
		panel.add(compareClogTotals.component());

		panel.add(searchRow);
		panel.add(Box.createVerticalStrut(4));

		infoRow = PanelInfoBar.build(
			playerName,
			clogInfoLabel,
			tooltipController,
			config::tooltipMode,
			comparison::isComparisonMode,
			() -> activitiesTray.toggle());
		panel.add(infoRow);

		iconCache.loadRuntimeIcons(cells, caRewardSprites);
		return panel;
	}

	// Activities tray.

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
				JPanel parentCell = (JPanel) this.getParent();
				return activityTooltips.buildPvm(this, parentCell);
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
				return activityTooltips.buildSkills(this);
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

	// Cell factory helpers.

	/**
	 * Build a sprite tooltip for a cell - always ImgTooltip, with contextual notice when no data.
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
		tip.setWikiLinksEnabled(config.wikiItemLinks());

		// Synced clog data with real item counts.
		if (data != null && data.obtainedCount >= 0)
		{
			tip.setTitle(data.name);
			tip.setObtained(data.obtainedCount, data.totalItems);
			if (data.kc >= 0)
			{
				tip.setInfoLine("KC: ", ClogHelper.formatKc(data.kc), Color.WHITE);
				if (data.pb != null)
				{
					tip.setInfoLinePair("PB: ", data.pb, Color.WHITE);
				}
			}
			else if (data.pb != null)
			{
				tip.setInfoLine("PB: ", data.pb, Color.WHITE);
			}
			if (data.rankTracked && data.rank > 0)
			{
				tip.setRank(data.rank);
			}
			tip.setItems(data.totalItems, data.allItemIds, data.obtainedIds,
				data.obtainedCounts, data.itemNames, itemManager);
		}
		else if (!ClogHelper.configureNotSynced(tip, data, itemManager))
		{
			tip.setTitle(data != null ? data.name : name);
			boolean isSelfNoCache = lookupSession.getHiscoreResult() != null && localRsn != null
				&& localRsn.equalsIgnoreCase(lookupSession.getCurrentLookupRsn());
			if (isSelfNoCache)
			{
				tip.setNotice(SYNC_NOTICE, getSyncIcon());
			}
			else if (lookupSession.getHiscoreResult() != null)
			{
				tip.setNotice(noClogNotice(lookupSession.getCurrentLookupRsn()));
			}
			else
			{
				// Cold and the catalog has not arrived yet; the warm-up
				// rebuild replaces this with the dimmed preview.
				tip.setNotice("Loading catalog...");
			}
		}

		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	// Comparison mode.

	/** Redirect the search bar action to the comparison controller when in compare entry mode. */
	private void doCompareLookupFromSearchBar()
	{
		String player = searchBar.getText().trim();
		if (player.isEmpty())
		{
			return;
		}
		// Spinner must go up BEFORE the lookup: a same-player compare resolves
		// synchronously and fires onComparisonEnter, which resets the active icon.
		// Setting LOADING afterward would strand the spinner with no callback to clear it.
		searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);
		comparison.doCompareLookup(player, localRsn);
	}

	/** Update the clog totals bar above the search bar for both players. */
	private void updateClogTotalsBar()
	{
		compareClogTotals.update(
			lookupSession.getClogResult(), comparison.getCompareClogResult(), iconCache);
	}

	@Override
	public void onComparisonExit()
	{
		searchRowController.onComparisonExit();
		compareClogTotals.setVisible(false);
		setSearchStatus(" ", TEXT_DIM);
		comparison.updateAllCells();
		comparison.updateInfoBar();
		toggleHighlighter(config.completionistHighlighter());
		searchRow.revalidate();
	}

	@Override
	public void onSwapToRedPlayer(String newPrimaryRsn)
	{
		rsn = newPrimaryRsn;
		HiscoreResult swapHiscore = lookupSession.getHiscoreResult();
		ClogResult swapClog = lookupSession.getClogResult();
		AccountDisplay swapDisplay = accountTypes.displayIdentity(swapHiscore, swapClog, newPrimaryRsn);

		playerName.setText(newPrimaryRsn != null ? newPrimaryRsn : "");
		playerName.setForeground(getInfoColor());
		updateInfoIcon(swapDisplay);
		if (swapHiscore != null)
		{
			int combatLevel = swapHiscore.getCombatLevel();
			if (combatLevel > 0)
			{
				combatCell.setText(ClogHelper.pad(String.valueOf(combatLevel)));
			}
			int totalLevel = swapHiscore.getTotalLevel();
			if (totalLevel > 0)
			{
				totalLvlCell.setText(ClogHelper.pad(String.valueOf(totalLevel)));
				totalLvlCell.setToolTipText(" ");
			}
		}
		colorStatsRow();
		if (swapClog != null)
		{
			itemNameResolver.resolve(swapClog);
			cells.renderClog(swapClog, config);
			updateClogCell(swapClog);
		}

		searchRowController.setCompareVisible(true);
		setSearchStatus(" ", TEXT_DIM);
		toggleHighlighter(config.completionistHighlighter());
		cells.rebuildPrimaryTooltips(localRsn);
	}


	@Override
	public void onComparisonEnter(String redRsn)
	{
		searchRowController.onComparisonEnter();
		setSearchStatus(" ", TEXT_DIM);
		updateClogTotalsBar();
		cells.rebuildPrimaryTooltips(localRsn);
		comparison.updateAllCells();
		comparison.updateInfoBar();
	}


	/** Apply account type badge to any label. */
	@Override
	public void applyBadge(JLabel label, AccountDisplay display)
	{
		label.setIcon(accountBadges.labelIcon(display));
	}

	// Lookup flow.

	/**
	 * Single point of control for the search-status text.
	 */
	private void setSearchStatus(String text, Color color)
	{
		searchStatus.setIcon(null);
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

	private void lookupSelfFromSearchIcon()
	{
		if (localRsn == null || localRsn.isEmpty())
		{
			return;
		}
		searchRowController.exitIfActive();
		searchBar.setText(localRsn);
		doLookup();
	}

	/**
	 * Reset all labels, maps, and fields to their pre-lookup state.
	 * Called at the start of every lookup to ensure a clean slate.
	 */
	private void resetAllLabels()
	{
		tooltipController.hideClickTooltip();
		searchRowController.exitIfActive();
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
		searchRowController.setCompareVisible(false);

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
			cells.getPvpSummaryCell().setText(ClogHelper.pad("--"));
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
		setSearchStatus(" ", TEXT_DIM);
		playerName.setText(rsn != null ? rsn : player);
		playerName.setForeground(getInfoColor());
		updateInfoIcon(currentInfoAccountDisplay(knownType != null ? knownType : result.getAccountType()));
		searchRowController.setCompareVisible(true);

		int combatLevel = result.getCombatLevel();
		if (combatLevel > 0)
		{
			combatCell.setText(ClogHelper.pad(String.valueOf(combatLevel)));
		}

		int totalLevel = result.getTotalLevel();
		if (totalLevel > 0)
		{
			totalLvlCell.setText(ClogHelper.pad(String.valueOf(totalLevel)));
			totalLvlCell.setToolTipText(" ");
		}
		colorStatsRow();

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
			if (lookupSession.getHiscoreResult() != null)
			{
				playerName.setText(name);
			}
		}
		AccountType providerType = result.getProviderAccountType();
		if (providerType != null && providerType.isGroupIronman())
		{
			updateDisplayedInfoIcon(accountTypes.displayIdentity(lookupSession.getHiscoreResult(), result, rsn));
		}
		itemNameResolver.resolve(result);
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
					if (lookupSession.getHiscoreResult() != null)
					{
						playerName.setText(name);
					}
				}
			})
		);
	}

	// Label update methods.

	private void updateClogCell(ClogResult result)
	{
		int[] totals = ClogHelper.sumClogTotals(result);
		if (totals[0] > 0)
		{
			String tierName = ClogHelper.getClogTierName(totals[0], totals[1]);
			ImageIcon icon = iconCache.clogTierIcon(tierName);
			if (icon != null)
			{
				clogInfoLabel.setIcon(icon);
			}
			clogInfoLabel.setText(ClogHelper.pad(ClogHelper.formatKc(totals[0])));
			clogInfoLabel.setForeground(getInfoColor());

			clogInfoLabel.setToolTipText(" ");
		}
	}

	// Progress highlighter.

	private void toggleHighlighter(boolean enabled)
	{
		if (lookupSession.getHiscoreResult() == null) return;
		tooltipController.clearHoveredCell();

		// Info bar follows highlighter state
		Color infoColor = getInfoColor();
		playerName.setForeground(infoColor);
		clogInfoLabel.setForeground(infoColor);
		colorStatsRow();

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

	// Public interface.

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

	/**
	 * The blue side of a comparison always has a name: the resolved rsn when
	 * the lookup landed, else whatever the user searched. An unsynced player
	 * must never render as "--" in identity rows; only their data goes absent.
	 */
	private String comparisonBlueName()
	{
		if (rsn != null)
		{
			return rsn;
		}
		String typed = playerName.getText();
		return typed != null && !typed.trim().isEmpty() ? typed.trim() : "--";
	}

	public void onBulkCaptureComplete(String name)
	{
		searchBar.setText(name);
		// Current lookup will finish soon - version it out and start fresh
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
				// fires the autocomplete twice - suggestion gets inserted as both
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

	public void setClogIndex(ClogIndex clogIndex)
	{
		cells.setClogIndex(clogIndex);
		comparison.setClogIndex(clogIndex);
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
				tooltipController.captureDefaults(this);
				tooltipController.hideClickTooltip();
				tooltipController.clearHoveredCell();
				break;
		}
	}

	public void reloadTooltipSprites()
	{
		clientThread.invokeLater(() -> NativeTooltip.loadSprites(client, spriteManager));
	}

	@Override
	public void onActivate()
	{
		tooltipController.captureDefaults(this);
	}

	@Override
	public void onDeactivate()
	{
		tooltipController.restoreDefaults();
	}

	/** Safety net - restores tooltip delay if plugin is disabled while panel is active. */
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
		return iconCache.capeFor(lookupSession.getHiscoreResult());
	}

	private BufferedImage getCapeImage(@Nullable HiscoreResult result)
	{
		return iconCache.capeFor(result);
	}

	@Override
	public void updateInfoIcon(AccountDisplay display)
	{
		applyBadge(playerName, display);
		playerName.setToolTipText(" ");
	}

	@Override
	public void preloadCaReward(@Nullable CombatAchievementResult ca)
	{
		if (ca != null)
		{
			caRewardSprites.request(ca.getReward());
		}
	}

	private void updateDisplayedInfoIcon()
	{
		updateDisplayedInfoIcon(currentInfoAccountDisplay());
	}

	private void updateDisplayedInfoIcon(@Nullable AccountDisplay display)
	{
		if (isIdentityRowShowing())
		{
			updateInfoIcon(display);
		}
	}

	private boolean isIdentityRowShowing()
	{
		String text = playerName.getText();
		return lookupSession.getHiscoreResult() != null
			&& text != null
			&& !text.trim().isEmpty();
	}

	/** Build a ComparePlayerSummaryTooltip for comparison mode (used by both name labels). */
	private ComparePlayerSummaryTooltip buildComparePlayerSummary(JLabel owner)
	{
		ComparePlayerSummaryTooltip cmp = new ComparePlayerSummaryTooltip();
		cmp.setComponent(owner);
		HiscoreResult blueHs = lookupSession.getHiscoreResult();
		HiscoreResult redHs = comparison.getCompareHiscoreResult();
		ClogResult blueClog = lookupSession.getClogResult();
		ClogResult redClog = comparison.getCompareClogResult();
		String blueName = comparisonBlueName();
		String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";
		AccountDisplay blueDisplay = accountTypes.displayIdentity(blueHs, blueClog, blueName);
		AccountDisplay redDisplay = accountTypes.displayIdentity(redHs, redClog, redName);

		cmp.setBlueData(blueName,
			blueHs != null ? blueHs.getOverallRank() : -1,
			accountBadges.badge(blueDisplay),
			AccountBadgeResolver.label(blueDisplay),
			LookupQueries.getPrestige(blueHs),
			getCapeImage(blueHs));
		cmp.setRedData(redName,
			redHs.getOverallRank(),
			accountBadges.badge(redDisplay),
			AccountBadgeResolver.label(redDisplay),
			LookupQueries.getPrestige(redHs),
			getCapeImage(redHs));

		if (blueClog != null)
		{
			List<Integer> allPets = blueClog.getCategoryItems().get("all_pets");
			Set<Integer> obtainedPets = LookupQueries.getObtainedPetIds(blueClog);
			cmp.setBluePets(allPets, obtainedPets, itemManager);
		}
		if (redClog != null)
		{
			List<Integer> allPets = redClog.getCategoryItems().get("all_pets");
			Set<Integer> obtainedPets = LookupQueries.getObtainedPetIds(redClog);
			cmp.setRedPets(allPets, obtainedPets, itemManager);
		}
		return cmp;
	}

	private CompareClogSummaryTooltip buildCompareClogSummary(JComponent owner)
	{
		CompareClogSummaryTooltip cmp = new CompareClogSummaryTooltip();
		cmp.setComponent(owner);
		cmp.setWikiLinksEnabled(config.wikiItemLinks());
		ClogResult blueClog = lookupSession.getClogResult();
		ClogResult redClog = comparison.getCompareClogResult();
		String blueName = comparisonBlueName();
		String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";
		Map<String, BufferedImage> icons = iconCache.clogTierImages();

		if (blueClog != null)
		{
			int[] bt = ClogHelper.sumClogTotals(blueClog);
			cmp.setBlueData(blueName, bt[0], bt[1], icons);
			boolean stale = LookupQueries.isSyncStale(lookupSession.getClogLastChanged(), 90);
			String sync = LookupQueries.syncLine(lookupSession.getClogLastChanged(), stale);
			if (sync != null) cmp.setBlueSync(sync, stale);
			cmp.setBlueSpecial(
				ClogHelper.obtainedSpecialItems(PanelData.SPECIAL_ITEM_IDS, blueClog),
				blueClog, itemManager);
			cmp.setBlueRecent(LookupQueries.getRecentItems(blueClog, 4), blueClog, itemManager);
		}
		else
		{
			cmp.setBlueData(blueName, 0, 0, icons);
			cmp.setBlueNotice("--");
		}

		if (redClog != null)
		{
			int[] rt = ClogHelper.sumClogTotals(redClog);
			cmp.setRedData(redName, rt[0], rt[1], icons);
			boolean stale = LookupQueries.isSyncStale(redClog.getLastChanged(), 90);
			String sync = LookupQueries.syncLine(redClog.getLastChanged(), stale);
			if (sync != null) cmp.setRedSync(sync, stale);
			cmp.setRedSpecial(
				ClogHelper.obtainedSpecialItems(PanelData.SPECIAL_ITEM_IDS, redClog),
				redClog, itemManager);
			cmp.setRedRecent(LookupQueries.getRecentItems(redClog, 4), redClog, itemManager);
		}
		else
		{
			cmp.setRedData(redName, 0, 0, icons);
			cmp.setRedNotice("--");
		}

		return cmp;
	}

	private AccountDisplay currentInfoAccountDisplay()
	{
		return accountTypes.currentDisplay(lookupSession.getHiscoreResult(),
			lookupSession.getClogResult(), lookupSession.getCurrentLookupRsn());
	}

	private AccountDisplay currentInfoAccountDisplay(@Nullable AccountType fallback)
	{
		return accountTypes.currentDisplay(fallback, lookupSession.getHiscoreResult(),
			lookupSession.getClogResult(), lookupSession.getCurrentLookupRsn());
	}

	// LookupSession.Listener

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
		// Clog may have arrived first with a GIM type the hiscores can't detect.
		ClogResult clog = lookupSession.getClogResult();
		if (clog != null)
		{
			updateDisplayedInfoIcon();
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
				BufferedImage icon = KillClogIcons.resizedPluginIcon(15, 15, itemManager);
				clogInfoLabel.setIcon(icon != null ? new ImageIcon(icon) : null);
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
	public void onCaResult(String player, @Nullable CombatAchievementResult ca, boolean isSelf, int lookupVersionAtFire)
	{
		if (lookupVersionAtFire == lookupSession.getLookupVersion())
		{
			preloadCaReward(ca);
			if (!comparison.isComparisonMode())
			{
				updateDisplayedInfoIcon();
			}
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

	// ComparisonController.Listener

	@Override
	public void onCompareDataReady()
	{
		comparison.updateInfoBar();
		updateClogTotalsBar();
	}

	@Override
	public void onCompareStatus(String msg, Color color)
	{
		setSearchStatus(msg, color);
	}

	@Override
	public void onCompareError(String player, Throwable err)
	{
		if (err != null)
		{
			setSearchStatus("Lookup failed", ComparisonController.COMPARE_RED);
		}
		searchRowController.refreshIcon();
	}

	// ComparisonController.CellRenderTarget
	// Read-only accessors the controller uses for panel-side summary-bar
	// widgets (combat + totalLvl cells, playerName + clogInfoLabel).
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
		itemNameResolver.resolve(clog);
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
