package com.killclog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import javax.inject.Inject;
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
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;

public class KillClogPanel extends PluginPanel
	implements LookupSession.Listener, ComparisonController.Listener,
	ComparisonController.CellRenderTarget
{
	private static final Color TEXT_DIM = new Color(160, 160, 160);
	private static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);
	static final Color KC_COLOR = new Color(215, 215, 215);
	private static final String SETUP_NOTICE =
		"Open your Collection Log to start setup automatically";

	/** Info bar text color - only applies when highlighter is active AND clog data exists. */
	@Override
	public Color getInfoColor()
	{
		return config.completionistHighlighter() && lookupSession.getClogResult() != null
			? config.infoBarColor() : KC_COLOR;
	}

	/** Combat and total level take the info color together; the Clues cell follows the highlighter. */
	private void colorStatsRow()
	{
		Color infoColor = getInfoColor();
		combatCell.setForeground(infoColor);
		totalLvlCell.setForeground(infoColor);
	}

	private final ClogService clogService;
	private final RuneProfileService runeProfileService;
	private final PlayerPortraits portraits;
	private final KillClogConfig config;
	private final ConfigManager configManager;
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final Client client;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final CaRewardSprites caRewardSprites;
	private final TooltipItemNameResolver itemNameResolver;
	private final PanelIconCache iconCache;
	private ClogIndex clogIndex;
	private final PanelAccountTypes accountTypes;
	private final ActivitySummaryTooltips activityTooltips;
	private final SkillCellGrid skillCellGrid;
	private ProgressHighlighter highlighter;
	private JPanel infoRow;

	private final PanelStatusRow statusRow;
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
			SummaryTooltip tip = buildPlayerSummaryTooltip(this,
				lookupSession.getHiscoreResult(), lookupSession.getClogResult(),
				playerName.getText().trim(), rowIdentity);
			// Without this the tooltip dies when the mouse leaves the name
			// label, and the pet gallery can never be hovered at all.
			if (this.getParent() instanceof JPanel)
			{
				tooltipController.keepTooltipOnHover(tip, (JPanel) this.getParent());
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
			return buildClogSummaryTooltip(this, lookupSession.getHiscoreResult(),
				lookupSession.getClogResult(), rsn, lookupSession.getClogLastChanged());
		}

	};
	private final JPanel clogNotice = new JPanel();

	private JLabel compareLabel;
	private SearchRowController searchRowController;
	private JTextField searchTextField;
	private JPanel searchRow;
	private CompareClogTotalsBar compareClogTotals;
	// Activities tray
	private JLabel combatCell;
	private JLabel totalLvlCell;
	private ActivitiesTray activitiesTray;
	private JPanel traySkillsHost;
	private JPanel fixedSkillsHost;
	// Boss view: grid and list share one container; the hamburger switches them.
	private BossListView bossListView;
	private JPanel bossViewContainer;
	private JPanel bossGridPanel;

	// Current lookup state lives on lookupSession; rsn here is a separate
	// display-name field set by fetchRsn for the playerName label.
	private String rsn;
	private String localRsn;
	private AccountType localAccountType;

	private final TooltipController tooltipController;
	private final LookupSession lookupSession;
	private final ComparisonController comparison;
	private final RankSelector rankSelector;
	// The Leagues switch: shown while a League runs, lit while the panel reads one. A League
	// world reads its League and any other world the main game; a click flips that until the
	// world changes. Every name in a League view wears the Leagues badge.
	private final JLabel leagueSwitch = new JLabel();
	private final ImageIcon leagueBadge = new ImageIcon(ImageUtil.resizeImage(
		ImageUtil.loadImageResource(HiscorePanel.class, "seasonal.png"), 15, 15));
	private final ImageIcon leagueOff = RankSelector.icon((BufferedImage) leagueBadge.getImage(), 0.35f);
	private String worldLeague;
	private String activeLeague;
	private LocalClogCache worldLeagueLog;
	private boolean flipped;
	private java.util.function.BiFunction<String, String, String> selfPb = (league, boss) -> null;
	private final Cells cells;
	private final ClogPages clogPages;

	// Comparison mode widgets (state fields all live on the controller)

	// No-data copy stays quiet: another player simply has no synced collection log.
	// No provider names, no "missing data" framing - just a calm statement.
	private String noClogNotice(String rsn, @Nullable HiscoreResult hiscore)
	{
		return LookupSession.frozen(hiscore) ? frozenLogNotice(hiscore)
			: rsn != null && !rsn.isEmpty()
			? rsn + " hasn't synced a collection log"
			: "No collection log synced";
	}

	/** A frozen row's log wasn't recorded; a board with no row at all says why it's empty. */
	private String frozenLogNotice(HiscoreResult view)
	{
		String blank = rankSelector.blankNotice(view);
		return blank != null ? blank : rankSelector.active().label.replace(" Ironman", "") + " collection log not recorded";
	}

	@Inject
	public KillClogPanel(HiscoreService hiscoreService, ClogService clogService,
		RuneProfileService runeProfileService, KillclogService killclogService,
		KillClogConfig config, ConfigManager configManager,
		SpriteManager spriteManager,
		ItemManager itemManager, ClientThread clientThread,
		SkillIconManager skillIconManager, Client client, PlayerPortraits portraits)
	{
		super(true); // wrap in JScrollPane
		this.clogService = clogService;
		this.runeProfileService = runeProfileService;
		this.portraits = portraits;
		this.config = config;
		this.configManager = configManager;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.client = client;
		this.tooltipDataBuilder = new TooltipDataBuilder(itemManager);
		this.caRewardSprites = new CaRewardSprites(itemManager, clientThread, this::repaint);
		this.iconCache = new PanelIconCache(itemManager, clientThread, spriteManager);
		this.accountTypes = new PanelAccountTypes(runeProfileService);
		this.tooltipController = new TooltipController(config);
		this.statusRow = new PanelStatusRow(config, spriteManager, tooltipController, TEXT_DIM);
		this.lookupSession = new LookupSession(hiscoreService, clogService, runeProfileService,
			killclogService, config, this);
		this.comparison = new ComparisonController(hiscoreService, clogService, runeProfileService,
			killclogService, lookupSession, config, tooltipController, tooltipDataBuilder, this);
		this.comparison.setRenderTarget(this);
		this.rankSelector = new RankSelector(hiscoreService::lookupRanks, hiscoreService::notOnBoard, this::refreshRankDisplay);
		this.lookupSession.setRankView(rankSelector::view);
		this.comparison.setVirtualTotalLevel(
			() -> ClogHelper.virtualTotalLevelEnabled(configManager));
		this.skillCellGrid = new SkillCellGrid(skillIconManager, tooltipController, comparison,
			config, itemManager);
		this.cells = new Cells(spriteManager, itemManager, tooltipController, comparison, tooltipDataBuilder, lookupSession, clogService, killclogService, (league, boss) -> selfPb.apply(league, boss), config);
		this.clogPages = new ClogPages(tooltipController, cells, comparison, lookupSession, tooltipDataBuilder);
		this.activityTooltips = new ActivitySummaryTooltips(
			lookupSession, comparison, cells, tooltipController, itemManager,
			caRewardSprites, config::wikiItemLinks);
		this.itemNameResolver = new TooltipItemNameResolver(clientThread, itemManager,
			this::onTooltipItemNamesResolved);
		this.cells.setUnsyncedCatalogResolver(itemNameResolver::resolve);
		this.comparison.setBlueNameSupplier(this::comparisonBlueName);
		this.comparison.setUnsyncedCatalogResolver(itemNameResolver::resolve);
		this.comparison.setCells(this.cells);
		this.cells.setSinglePlayerTooltipBuilder(new Cells.SinglePlayerTooltipBuilder()
		{
			@Override
			public JToolTip build(JLabel owner, TooltipData data, int gridCols, String name)
			{
				return makeSpriteTooltip(owner, data, gridCols, name,
					lookupSession.getHiscoreResult(), lookupSession.getCurrentLookupRsn());
			}

			@Override
			public JToolTip buildCompared(JLabel owner, TooltipData data, int gridCols, String name)
			{
				return makeSpriteTooltip(owner, data, gridCols, name,
					comparison.getCompareHiscoreResult(), comparison.getCompareRsn());
			}
		});

		reloadTooltipSprites();

		// Top border 6 not 10: the missing 4px live inside the status row
		// (taller row + compensating label inset), which lets the sync
		// chalice center truly in the panel-top-to-search-bar band while
		// the search bar and status text keep their exact positions.
		setBorder(new EmptyBorder(6, 10, 0, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 0;

		clogNotice.setLayout(new BoxLayout(clogNotice, BoxLayout.Y_AXIS));
		clogNotice.setOpaque(false);
		JLabel noticeOpen = new JLabel("Open Collection Log");
		JLabel noticeSearch = new JLabel("Setup starts automatically");
		for (JLabel label : new JLabel[]{noticeOpen, noticeSearch})
		{
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setAlignmentX(Component.CENTER_ALIGNMENT);
			ClogHelper.antialias(label);
			clogNotice.add(label);
		}
		clogNotice.setVisible(false);
		c.insets = new Insets(0, 0, 5, 0);
		add(clogNotice, c);

		c.gridy++;
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
		fixedSkillsHost = buildSkillHost();
		add(fixedSkillsHost, c);

		c.gridy++;
		bossGridPanel = cells.buildBossGrid();
		bossListView = new BossListView(tooltipController, cells,
			this::bossListAvailable);
		bossViewContainer = new JPanel(new BorderLayout());
		bossViewContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		add(bossViewContainer, c);
		applyBossViewStyle();

		// Compare entry controls live in the search row.
		getWrappedPanel().add(rankSelector, BorderLayout.SOUTH);
		rankSelector.setVisible(config.showLeaderboardSelector());

		JScrollPane sp = getScrollPane();
		if (sp != null)
		{
			sp.setBorder(null);
			sp.setViewportBorder(null);
			sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
			sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
			sp.getVerticalScrollBar().setUI(new MinimalScrollBarUI());
			sp.getVerticalScrollBar().setPreferredSize(new Dimension(MinimalScrollBarUI.WIDTH, 0));
			sp.getVerticalScrollBar().setUnitIncrement(16);
		}

		highlighter = new ProgressHighlighter(
			cells.getBossLabels(), cells.getActivityLabels(), cells.getClueTierLabels(), config);
		refreshSkillDisplay();

		// Cold start: warm the catalog so every cell previews the log's shape
		// (dimmed grids, --/Y slot counts) before any player has been searched.
		clogService.warmCatalog().thenRun(() -> SwingUtilities.invokeLater(() ->
		{
			cells.rebuildPrimaryTooltips(localRsn);
			refreshSkillDisplay();
		}));
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

		panel.add(statusRow.component());

		searchTextField = PanelSearchBox.configureSearchBar(searchBar);
		compareLabel = new JLabel();
		leagueSwitch.setOpaque(true);
		leagueSwitch.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		leagueSwitch.setVisible(false);
		leagueSwitch.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				flipped = !flipped;
				showGame();
			}
		});
		searchRow = PanelSearchBox.buildSearchRow(
			searchBar, leagueSwitch, compareLabel,
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
		searchRowController.setComparisonEnabled(config.enableComparison());

		compareClogTotals = new CompareClogTotalsBar(
			comparison::isComparisonMode,
			tooltipController,
			this::buildCompareClogSummary);
		panel.add(compareClogTotals.component());

		panel.add(searchRow);
		panel.add(Box.createVerticalStrut(4));

		infoRow = PanelInfoBar.build(
			playerName,
			clogInfoLabel,
			tooltipController,
			comparison::isComparisonMode,
			this::toggleBossViewStyle);
		panel.add(infoRow);

		iconCache.loadRuntimeIcons(cells, caRewardSprites);
		return panel;
	}

	// Boss view style (grid / list).

	/**
	 * The single rule for when the list may show: never during comparison -
	 * it is a single-player hiscores surface. applyBossViewStyle, the
	 * hamburger guard, and the row mirror's pause all consult THIS method,
	 * so if the rule ever relaxes, all three move together.
	 */
	private boolean bossListAvailable()
	{
		return !comparison.isComparisonMode();
	}

	/**
	 * Show the boss view the config asks for. Comparison mode always shows
	 * the grid; the stored preference comes back on exit.
	 */
	private void applyBossViewStyle()
	{
		boolean list = config.bossListView() && bossListAvailable();
		if (list)
		{
			// Opening the list always starts from grid truth; anything the
			// paused mirror skipped during compare is re-copied here.
			bossListView.resyncAll();
		}
		bossViewContainer.removeAll();
		bossViewContainer.add(list ? bossListView.component() : bossGridPanel, BorderLayout.CENTER);
		bossViewContainer.revalidate();
		bossViewContainer.repaint();
	}

	private static final String COMPARE_VIEW_STATUS = "exit compare to switch views";

	/** Hamburger click: flip the persisted style and re-apply. */
	private void toggleBossViewStyle()
	{
		if (!bossListAvailable())
		{
			setSearchStatus(COMPARE_VIEW_STATUS, TEXT_DIM);
			// Transient refusal: clears itself unless something else has
			// already written over it. Every other status here is cleared by
			// a follow-up path; this one has none.
			Timer clear = new Timer(2500, e ->
			{
				if (COMPARE_VIEW_STATUS.equals(statusRow.statusText()))
				{
					setSearchStatus(" ", TEXT_DIM);
				}
			});
			clear.setRepeats(false);
			clear.start();
			return;
		}
		configManager.setConfiguration("killclog", "bossListView", !config.bossListView());
		applyBossViewStyle();
	}

	// Activities tray.

	private JPanel buildActivitiesGrid()
	{
		JPanel grid = new JPanel();
		grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Row 0: [Combat Summary] [Skill Summary] [Clue Summary]
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
		// The three summaries: Combat, Skills, Clues.
		statsRow.add(cells.buildClueSummaryCell());
		grid.add(statsRow);

		JPanel statsSep = new JPanel();
		statsSep.setBackground(ColorScheme.DARK_GRAY_COLOR);
		statsSep.setPreferredSize(new Dimension(0, 7));
		statsSep.setAlignmentX(0f);
		grid.add(statsSep);

		traySkillsHost = buildSkillHost();
		grid.add(traySkillsHost);

		// The six clue tiers. The rare collections open from the Clue Summary.
		JPanel clueRow1 = new JPanel(new GridLayout(1, 3));
		clueRow1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow1.setAlignmentX(0f);
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[0], PanelData.CLUE_TIER_ITEM_IDS[0]));
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[1], PanelData.CLUE_TIER_ITEM_IDS[1]));
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[2], PanelData.CLUE_TIER_ITEM_IDS[2]));
		grid.add(clueRow1);

		JPanel clueRow2 = new JPanel(new GridLayout(1, 3));
		clueRow2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow2.setAlignmentX(0f);
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[3], PanelData.CLUE_TIER_ITEM_IDS[3]));
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[4], PanelData.CLUE_TIER_ITEM_IDS[4]));
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[5], PanelData.CLUE_TIER_ITEM_IDS[5]));
		grid.add(clueRow2);

		return grid;
	}

	private static JPanel buildSkillHost()
	{
		JPanel host = new JPanel(new BorderLayout());
		host.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		host.setAlignmentX(0f);
		host.setVisible(false);
		return host;
	}

	/**
	 * Move one live skill-cell grid between its configured panel locations.
	 */
	private void refreshSkillDisplay()
	{
		if (totalLvlCell == null || traySkillsHost == null || fixedSkillsHost == null)
		{
			return;
		}

		// Rebuilding the skill grid invalidates skill tooltips, but passive clog
		// refreshes must not dismiss a boss tooltip the player is reading.
		tooltipController.hidePinnedTooltipIfOwnedBy(totalLvlCell);
		for (JLabel skillLabel : skillCellGrid.labels().values())
		{
			tooltipController.hidePinnedTooltipIfOwnedBy(skillLabel);
		}
		traySkillsHost.removeAll();
		fixedSkillsHost.removeAll();
		traySkillsHost.setVisible(false);
		fixedSkillsHost.setVisible(false);

		HiscoreResult result = lookupSession.getHiscoreResult();
		tooltipController.setTooltipText(totalLvlCell, result != null ? " " : null);

		if (result != null)
		{
			HiscoreResult compared = comparison.isComparisonMode()
				? comparison.getCompareHiscoreResult() : null;
			skillCellGrid.render(result, compared, config.virtualLevels(),
				lookupSession.getClogResult(), comparison.getCompareClogResult(),
				cells.unsyncedCatalogResult());
		}
		else
		{
			skillCellGrid.clear(cells.unsyncedCatalogResult());
		}
		JPanel host = config.skillDisplay() == SkillDisplay.TRAY ? traySkillsHost : fixedSkillsHost;
		host.add(skillCellGrid.component(), BorderLayout.CENTER);
		host.setVisible(true);

		traySkillsHost.revalidate();
		fixedSkillsHost.revalidate();
		if (activitiesTray != null)
		{
			activitiesTray.getClip().revalidate();
		}
		revalidate();
		repaint();
	}

	// Cell factory helpers.

	/**
	 * Build a sprite tooltip for a cell - always ImgTooltip, with contextual notice when no data.
	 *
	 * @param owner     the label whose parent cell drives hover tint
	 * @param data      tooltip data, or null for notice-only display
	 * @param gridCols  min columns for the sprite grid
	 * @param name      display name shown as title when data is null
	 * @param result    with {@code rsn}, picks whose card this is
	 */
	private JToolTip makeSpriteTooltip(JLabel owner, TooltipData data, int gridCols,
		String name, @Nullable HiscoreResult result, @Nullable String rsn)
	{
		JPanel parentCell = (JPanel) owner.getParent();
		ImgTooltip tip = new ImgTooltip(gridCols);
		tip.setComponent(owner);
		tip.setWikiLinksEnabled(config.wikiItemLinks());
		boolean isSolHeredit = ColosseumGlory.replacesKc(name);
		int glory = ColosseumGlory.score(result);

		// Synced clog data with real item counts.
		if (data != null && data.obtainedCount >= 0)
		{
			tip.setTitle(data.name);
			tip.setObtained(data.obtainedCount, data.totalItems);
			boolean hasHeaderScore = isSolHeredit
				? ColosseumGlory.hasHeaderScore(glory, data.kc) : data.kc >= 0;
			if (hasHeaderScore && config.showTooltipKc())
			{
				tip.setInfoLine(isSolHeredit ? ColosseumGlory.headerLabel(glory) : data.kcLabel != null ? data.kcLabel : "KC: ",
					isSolHeredit ? ColosseumGlory.headerValue(glory, data.kc) : ClogHelper.formatKc(data.kc),
					Color.WHITE);
				if (data.pb != null && config.showTooltipPb())
				{
					tip.setInfoLinePair("PB: ", data.pb, Color.WHITE);
				}
			}
			else if (data.pb != null && config.showTooltipPb())
			{
				tip.setInfoLine("PB: ", data.pb, Color.WHITE);
			}
			ClogHelper.addHardModeLine(tip, data, config.showTooltipKc(), config.showTooltipPb());
			if (data.allItemIds != null)
			{
				tip.setItems(data.totalItems, data.allItemIds, data.obtainedIds,
					data.obtainedCounts, data.itemNames, itemManager);
			}
		}
		else if (!ClogHelper.configureNotSynced(tip, data, itemManager, config.showTooltipKc()))
		{
			tip.setTitle(data != null ? data.name : name);
			boolean isSelfNoCache = result != null && RsnInputPolicy.sameName(localRsn, rsn) && lookupSession.readsOwnLog();
			if (isSelfNoCache)
			{
				tip.setNotice(SETUP_NOTICE);
			}
			else if (result != null)
			{
				tip.setNotice(noClogNotice(rsn, result));
			}
			else
			{
				// Cold and the catalog has not arrived yet; the warm-up
				// rebuild replaces this with the dimmed preview.
				tip.setNotice("Loading catalog...");
			}
		}
		ClogHelper.configureRank(tip, data, result, config.showTooltipRank());
		if (isSolHeredit && ColosseumGlory.isVisible(glory)
			&& config.showTooltipKc() && (data == null || data.obtainedCount < 0))
		{
			tip.setInfoLine(ColosseumGlory.LABEL, ColosseumGlory.format(glory), Color.WHITE);
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
		if (!RsnInputPolicy.isValid(player))
		{
			showInvalidName();
			return;
		}
		// Spinner must go up BEFORE the lookup: a same-player compare resolves
		// synchronously and fires onComparisonEnter, which resets the active icon.
		// Setting LOADING afterward would strand the spinner with no callback to clear it.
		searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);
		comparison.doCompareLookup(player, localRsn, localAccountType);
	}

	/** Update the clog totals bar above the search bar for both players. */
	private void updateClogTotalsBar()
	{
		compareClogTotals.update(
			ClogHelper.summaryTotals(lookupSession.getClogResult(), lookupSession.getHiscoreResult(),
				cells.unsyncedCatalogResult()),
			ClogHelper.summaryTotals(comparison.getCompareClogResult(), comparison.getCompareHiscoreResult(),
				cells.unsyncedCatalogResult()), iconCache);
	}

	@Override
	public void onComparisonExit()
	{
		// Clear first: the board's own notice, set while its players change, must survive.
		setSearchStatus(" ", TEXT_DIM);
		updateRankPlayers();
		searchRowController.onComparisonExit();
		compareClogTotals.setVisible(false);
		comparison.updateAllCells();
		comparison.updateInfoBar();
		// The comparison's badges come down; the name wears its kept identity again.
		rivalIdentity = null;
		if (isIdentityRowShowing())
		{
			showIdentity();
		}
		applyBossViewStyle();
		renderResults();
		searchRow.revalidate();
	}

	@Override
	public void onComparisonEnter(String redRsn)
	{
		setSearchStatus(" ", TEXT_DIM);
		updateRankPlayers();
		searchRowController.onComparisonEnter();
		applyBossViewStyle();
		updateClogTotalsBar();
		cells.rebuildPrimaryTooltips(localRsn);
		comparison.updateAllCells();
		comparison.updateInfoBar();
		refreshSkillDisplay();
	}

	/** Apply account type badge to any label. */
	@Override
	public void applyBadge(JLabel label, AccountDisplay display)
	{
		AccountDisplay before = label == playerName ? rowIdentity : label == clogInfoLabel ? rivalIdentity : null;
		if (label == playerName)
		{
			rowIdentity = display;
		}
		else if (label == clogInfoLabel)
		{
			rivalIdentity = display;
		}
		// A pinned name card shows the identity it was built with; when that changes, it closes.
		if (before != null && !before.equals(display))
		{
			tooltipController.hidePinnedTooltipIfOwnedBy(playerName);
			tooltipController.hidePinnedTooltipIfOwnedBy(clogInfoLabel);
		}
		label.setIcon(lookupSession.league() != null ? leagueBadge : AccountBadgeResolver.labelIcon(display));
	}

	@Override
	@Nullable
	public AccountDisplay primaryIdentity()
	{
		return keptIdentity;
	}

	// ── killclog.com one-click controls: the plugin's view of the status row ──

	void setKillclogSyncHandler(Runnable handler)
	{
		statusRow.setKillclogSyncHandler(handler);
	}

	void setCharacterPublishHandler(Runnable handler)
	{
		statusRow.setCharacterPublishHandler(handler);
	}

	void setSyncArrowEnabled(boolean enabled)
	{
		statusRow.setSyncArrowEnabled(enabled);
	}

	void setSyncArrowHasData(boolean hasData)
	{
		statusRow.setSyncArrowHasData(hasData);
	}

	void setCharacterPublishEnabled(boolean enabled)
	{
		statusRow.setCharacterPublishEnabled(enabled);
	}

	void showSyncProgress(boolean manual, String text, boolean autoClear)
	{
		statusRow.showSyncProgress(manual, text, autoClear);
	}

	void showSyncResult(boolean manual, boolean ok, String message)
	{
		statusRow.showSyncResult(manual, ok, message);
	}

	void resetSyncFeedback()
	{
		statusRow.resetSyncFeedback();
	}

	void showCharacterPublishStatus(String text, boolean ok, boolean autoClear, String detail)
	{
		statusRow.showCharacterPublishStatus(text, ok, autoClear, detail);
	}

	void refreshSyncFeedbackSettings()
	{
		statusRow.refreshSyncFeedbackSettings();
	}

	// Lookup flow.

	/** Lookup messages share the status row with the controls above; the row arbitrates. */
	private void setSearchStatus(String text, Color color)
	{
		statusRow.setSearchStatus(text, color);
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
		if (lookupSession.isLookupInFlight())
		{
			return;
		}
		if (player.isEmpty())
		{
			setSearchStatus("Enter RSN", TEXT_DIM);
			return;
		}
		if (!RsnInputPolicy.isValid(player))
		{
			showInvalidName();
			return;
		}
		lookupSession.start(player, localRsn, localAccountType);
	}

	private void showInvalidName()
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setText("");
		setSearchStatus(SearchMessages.INVALID_NAME, NOT_FOUND);
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
	 * Return the header and every cell to the pre-lookup state. Called at the
	 * start of every lookup, and after a miss or failure, for a clean slate.
	 */
	private void resetForLookup()
	{
		tooltipController.hidePinnedTooltip();
		searchRowController.exitIfActive();
		rsn = null;
		setClogSetupNoticeVisible(false);
		cells.reset();

		playerName.setText(" ");
		playerName.setIcon(null);
		selfType = null;
		keptIdentity = null;
		rowIdentity = null;
		tooltipController.setTooltipText(playerName, null);
		searchRowController.setCompareVisible(false);

		clogInfoLabel.setIcon(null);
		clogInfoLabel.setText("");
		tooltipController.setTooltipText(clogInfoLabel, null);

		combatCell.setText(ClogHelper.pad("--"));
		combatCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalLvlCell.setText(ClogHelper.pad("--"));
		totalLvlCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tooltipController.setTooltipText(totalLvlCell, null);
		refreshSkillDisplay();
	}

	/**
	 * Render hiscore data to the panel (extracted for cache/SWR reuse).
	 */
	private void renderHiscoreResult(HiscoreResult result, String player, AccountType knownType)
	{
		selfType = knownType;
		updateRankPlayers();
		setSearchStatus(" ", TEXT_DIM);
		playerName.setText(rsn != null ? rsn : player);
		playerName.setForeground(getInfoColor());
		updateInfoIcon(currentInfoAccountDisplay(knownType != null ? knownType : result.getAccountType()));
		searchRowController.setCompareVisible(true);
		colorStatsRow();

		// The search box is free again while a hiscore waits for its clog:
		// leave a name typed in the meantime.
		if (searchBar.getText().trim().equalsIgnoreCase(player))
		{
			searchBar.setText("");
		}
		renderResults();
		cells.rebuildPrimaryTooltips(localRsn);
		updateClogCell(lookupSession.getClogResult());
		if (comparison.isComparisonMode()) updateClogTotalsBar();
	}

	/**
	 * Render clog data to the panel (extracted for SWR reuse).
	 */
	private void renderClogResult(ClogResult result)
	{
		// Each lookup re-checks a syncer's character, so a withdrawn one is gone from this lookup on.
		portraits.lookedUp(result);
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
			cells.renderClog(result);
			updateClogCell(result);
		}
		cells.rebuildPrimaryTooltips(localRsn);
		renderResults();
		if (comparison.isComparisonMode()) updateClogTotalsBar();
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
		// During a comparison this label is the red name; the log cell returns when it ends.
		if (comparison.isComparisonMode())
		{
			return;
		}
		int[] totals = ClogHelper.summaryTotals(result, lookupSession.getHiscoreResult(),
			cells.unsyncedCatalogResult());
		if (totals[0] >= 0)
		{
			String tierName = totals[1] > 0 ? ClogHelper.getClogTierName(totals[0], totals[1]) : null;
			ImageIcon icon = iconCache.clogTierIcon(tierName);
			clogInfoLabel.setIcon(icon);
			clogInfoLabel.setText(ClogHelper.pad(ClogHelper.formatKc(totals[0])));
			clogInfoLabel.setForeground(getInfoColor());

			tooltipController.setTooltipText(clogInfoLabel, " ");
		}
	}

	// Result rendering.

	/**
	 * Repaint the header and every cell from the session results under the
	 * current settings: the completion highlighter and any comparison.
	 */
	private void renderResults()
	{
		if (lookupSession.getHiscoreResult() == null) return;
		tooltipController.clearHoveredCell();

		// Info bar follows highlighter state
		Color infoColor = getInfoColor();
		playerName.setForeground(infoColor);
		clogInfoLabel.setForeground(infoColor);
		colorStatsRow();
		renderLevels(lookupSession.getHiscoreResult());

		cells.renderHiscore(lookupSession.getHiscoreResult());
		if (lookupSession.getClogResult() != null)
		{
			cells.renderClog(lookupSession.getClogResult());
			if (config.completionistHighlighter())
			{
				highlighter.colorCellsByCompletion(lookupSession.getHiscoreResult(), lookupSession.getClogResult());
				highlighter.colorEmptyCells();
			}
		}

		if (comparison.isComparisonMode())
		{
			comparison.updateAllCells();
			comparison.updateInfoBar();
		}
		refreshSkillDisplay();
	}

	/** Combat and total level follow the board on show; a board without a row shows dashes. */
	private void renderLevels(HiscoreResult result)
	{
		int combatLevel = result.getCombatLevel();
		combatCell.setText(ClogHelper.pad(combatLevel > 0 ? String.valueOf(combatLevel) : "--"));
		int totalLevel = ClogHelper.displayTotalLevel(result, ClogHelper.virtualTotalLevelEnabled(configManager));
		totalLvlCell.setText(ClogHelper.pad(totalLevel > 0 ? String.valueOf(totalLevel) : "--"));
		tooltipController.setTooltipText(totalLvlCell, totalLevel > 0 ? " " : null);
	}

	// Public interface.

	public void setPlayerName(String name)
	{
		searchBar.setText(name);
	}

	/** The world's League (null: none), your log for it, and the League running now (null: none). */
	public void followWorld(String league, LocalClogCache leagueLog, String active)
	{
		worldLeague = league;
		worldLeagueLog = leagueLog;
		activeLeague = active;
		flipped = false;
		showGame();
	}

	private void showGame()
	{
		String league = worldLeague != null ? (flipped ? null : worldLeague) : (flipped ? activeLeague : null);
		boolean changed = !java.util.Objects.equals(league, lookupSession.league());
		// A main-game view ignores the log, and only a League world has one.
		lookupSession.readLeague(league, worldLeagueLog);
		comparison.readLeague(league, worldLeagueLog);
		leagueSwitch.setVisible(worldLeague != null || activeLeague != null);
		leagueSwitch.setIcon(league != null ? leagueBadge : leagueOff);
		tooltipController.setTooltipText(leagueSwitch, league != null ? "League stats" : "Main game stats");
		searchRow.revalidate();
		// Shown or still loading, the player is read again in the new game.
		String shown = lookupSession.getCurrentLookupRsn();
		if (changed && shown != null)
		{
			// The new lookup also ends any comparison: its other side is the last game's.
			lookupSession.cancelInFlight();
			searchBar.setText(shown);
			doLookup();
		}
	}

	/** Your own PB reader: panel boss in the viewed game (null: the main game) to its text, or null. */
	public void setSelfPb(java.util.function.BiFunction<String, String, String> selfPb)
	{
		this.selfPb = selfPb;
	}

	public void setLoggedInPlayer(String name, AccountType accountType)
	{
		this.localRsn = name;
		this.localAccountType = accountType;
	}

	/** The RSN the panel shows, or the one it looked up when no provider named them; null with no lookup. */
	public String getDisplayedRsn()
	{
		return rsn != null ? rsn : lookupSession.getCurrentLookupRsn();
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
		if (!RsnInputPolicy.sameName(localRsn, name)) return;
		if (lookupSession.getCurrentLookupRsn() == null)
		{
			// First setup can populate an empty panel through the normal lookup.
			searchBar.setText(name);
			doLookup();
			return;
		}
		lookupSession.refreshLocalClog(name, localRsn);
	}

	public void setNameAutocompleter(NameAutocompleter autocompleter)
	{
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
		this.clogIndex = clogIndex;
		cells.setClogIndex(clogIndex);
		clogPages.setClogIndex(clogIndex);
		comparison.setClogIndex(clogIndex);
		skillCellGrid.setClogIndex(clogIndex);
		tooltipDataBuilder.setClogIndex(clogIndex);
	}

	public void onConfigChanged(String key)
	{
		switch (key)
		{
			case "showLeaderboardSelector":
				updateRankPlayers();
				getWrappedPanel().revalidate();
				break;
			case "enableComparison":
				searchRowController.setComparisonEnabled(config.enableComparison());
				break;
			case "completionistHighlighter":
			case "completedClogColor":
			case "missing1Color":
			case "inProgressClogColor":
			case "emptyClogColor":
			case "infoBarColor":
				renderResults();
				cells.rebuildPrimaryTooltips(localRsn);
				break;
			case "hoverStyle":
				tooltipController.hidePinnedTooltip();
				tooltipController.clearHoveredCell();
				break;
			case "tooltipMode":
				tooltipController.onTooltipModeChanged();
				break;
			case "skillDisplay":
			case "skillLevelColor":
			case "skillColorMode":
			case "enableSkillClogs":
				refreshSkillDisplay();
				break;
			case "virtualLevels":
				// A full render keeps a comparison's two-player level cells.
				renderResults();
				refreshSkillDisplay();
				break;
		}
	}

	public void reloadTooltipSprites()
	{
		clientThread.invokeLater(() ->
		{
			NativeTooltip.loadSprites(client, spriteManager);
			statusRow.requestCharacterIcon();
		});
	}

	@Override
	public void onActivate()
	{
		tooltipController.activate(this);
	}

	@Override
	public void onDeactivate()
	{
		tooltipController.deactivate();
	}

	/** Safety net - clears transient tooltip state if the plugin is disabled. */
	public void shutdown()
	{
		lookupSession.reset();
		comparison.reset();
		searchRowController.onComparisonExit();
		rankSelector.reset();
		statusRow.shutdown();
		tooltipController.deactivate();
	}

	@Override
	public void removeNotify()
	{
		super.removeNotify();
		tooltipController.hidePinnedTooltip();
	}

	private BufferedImage getCapeImage(@Nullable HiscoreResult result)
	{
		return iconCache.capeFor(result);
	}

	private void updateInfoIcon(AccountDisplay display)
	{
		keptIdentity = display;
		showIdentity();
		tooltipController.setTooltipText(playerName, " ");
	}

	/** The name row: the kept identity, marked when the shown board is a frozen one the lookup proves. */
	private void showIdentity()
	{
		applyBadge(playerName, keptIdentity != null
			? keptIdentity.shownOn(lookupSession.getHiscoreResult(), rankSelector.active()) : null);
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

	/** Side-by-side player summary for comparison mode (used by both name labels). */
	private JToolTip buildComparePlayerSummary(JLabel owner)
	{
		String blueName = comparisonBlueName();
		String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";
		JToolTip tip = comparison.wrapSideBySide(owner,
			buildPlayerSummaryTooltip(owner, lookupSession.getHiscoreResult(),
				lookupSession.getClogResult(), blueName, rowIdentity),
			buildPlayerSummaryTooltip(owner, comparison.getCompareHiscoreResult(),
				comparison.getCompareClogResult(), redName, rivalIdentity));
		if (owner.getParent() instanceof JPanel)
		{
			tooltipController.keepTooltipOnHover(tip, (JPanel) owner.getParent());
		}
		return tip;
	}

	/** One player's summary card: solo mode shows it alone, comparison pairs two. */
	private SummaryTooltip buildPlayerSummaryTooltip(JComponent owner,
		@Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		String shownName, @Nullable AccountDisplay display)
	{
		SummaryTooltip tip = new SummaryTooltip();
		tip.setComponent(owner);
		// A Kill Clog syncer who published their character stands where the prestige cape would.
		BufferedImage character = portraits.forSummary(clog);
		tip.setData(
			shownName.isEmpty() ? "Player" : shownName,
			hiscore != null ? hiscore.getOverallRank() : -1,
			character != null ? character : getCapeImage(hiscore),
			AccountBadgeResolver.badge(display),
			AccountBadgeResolver.label(display),
			LookupQueries.getPrestige(hiscore)
		);
		tip.setWikiLinksEnabled(config.wikiItemLinks());
		if (clog != null)
		{
			List<Integer> allPets = clog.getCategoryItems().get("all_pets");
			tip.setPets(allPets, LookupQueries.getObtainedPetCounts(clog), itemManager, clog::getItemName);
		}
		return tip;
	}

	private JToolTip buildCompareClogSummary(JComponent owner)
	{
		ClogResult redClog = comparison.getCompareClogResult();
		return comparison.wrapSideBySide(owner,
			buildClogSummaryTooltip(owner, lookupSession.getHiscoreResult(),
				lookupSession.getClogResult(), comparisonBlueName(),
				lookupSession.getClogLastChanged()),
			buildClogSummaryTooltip(owner, comparison.getCompareHiscoreResult(),
				redClog, comparison.getCompareRsn(),
				redClog != null ? redClog.getLastChanged() : null));
	}

	/** One player's clog summary card: solo mode shows it alone, comparison pairs two. */
	private ClogSummaryTooltip buildClogSummaryTooltip(JComponent owner,
		@Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		@Nullable String playerRsn, @Nullable String lastChanged)
	{
		ClogSummaryTooltip tip = new ClogSummaryTooltip();
		tip.setComponent(owner);
		tip.setWikiLinksEnabled(config.wikiItemLinks());
		if (clog != null)
		{
			int[] totals = clogIndex != null
				? ClogHelper.sumClogTotals(clog, clogIndex::canonicalItemId)
				: ClogHelper.sumClogTotals(clog);
			tip.setTierData(totals[0], totals[1], iconCache.clogTierImages(), itemManager);
			if (clogIndex != null)
			{
				tip.setTabs(clogIndex.tabProgress(clog, totals));
				tip.setOnOpenTab((press, tab) -> clogPages.openTab(owner, press, tab));
			}
			tip.setClogSources(clog, runeProfileService.hasProfile(playerRsn));
			if (hiscore != null && hiscore.isRankDataAvailable())
			{
				int clogRank = hiscore.getActivityRank("Collections Logged");
				tip.setRank(clogRank);
			}
			boolean stale = LookupQueries.isSyncStale(lastChanged, 90);
			String sync = LookupQueries.syncLine(lastChanged, stale);
			if (sync != null) tip.setSyncData(sync, stale);
			tip.setRareItems(ClogHelper.rareItems(clog), clog, itemManager);
			tip.setRecentItems(LookupQueries.getRecentItems(clog, 4), clog, itemManager);
		}
		else
		{
			// Setup is only offered for a log this client keeps.
			boolean isSelf = RsnInputPolicy.sameName(localRsn, playerRsn) && lookupSession.readsOwnLog();
			if (isSelf)
			{
				tip.setFirstTimeSetup();
			}
			else if (hiscore != null)
			{
				tip.setNotice(noClogNotice(playerRsn, hiscore));
			}
			else
			{
				// No player yet: preview the log's shape from the catalog.
				ClogResult catalog = cells.unsyncedCatalogResult();
				if (catalog != null)
				{
					tip.setTitle("Collection Log");
					tip.setObtainedPlaceholder(clogIndex != null
						? ClogHelper.sumClogTotals(catalog, clogIndex::canonicalItemId)[1]
						: ClogHelper.sumClogTotals(catalog)[1]);
				}
				else
				{
					tip.setNotice("Loading catalog...");
				}
			}
		}
		if (clog == null && hiscore != null)
		{
			int[] totals = ClogHelper.summaryTotals(null, hiscore, cells.unsyncedCatalogResult());
			if (totals[0] >= 0) tip.setObtained(totals[0], totals[1]);
			if (hiscore.isRankDataAvailable()) tip.setRank(hiscore.getActivityRank("Collections Logged"));
		}
		return tip;
	}

	private AccountDisplay currentInfoAccountDisplay()
	{
		// A self lookup keeps RuneLite's own type, like a group ironman the hiscores read as regular.
		return selfType != null ? currentInfoAccountDisplay(selfType)
			: accountTypes.currentDisplay(lookupSession.getHiscoreResult(),
			lookupSession.getNativeClogResult(), lookupSession.getCurrentLookupRsn());
	}

	private AccountDisplay currentInfoAccountDisplay(@Nullable AccountType fallback)
	{
		return accountTypes.currentDisplay(fallback, lookupSession.getHiscoreResult(),
			lookupSession.getNativeClogResult(), lookupSession.getCurrentLookupRsn());
	}

	// LookupSession.Listener

	@Override
	public void onLookupStart(String player, boolean isSelf, boolean isFirstSelfGreeting)
	{
		rankSelector.reset();
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
		resetForLookup();
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
		renderHiscoreResult(hiscore, player, knownType);
		if (clog != null)
		{
			renderClogResult(clog);
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
		renderHiscoreResult(hiscore, player, knownType);
		// Clog may have arrived first with a GIM type the hiscores can't detect.
		ClogResult clog = lookupSession.getClogResult();
		if (clog != null)
		{
			updateDisplayedInfoIcon();
			cells.renderClog(clog);
		}
	}

	@Override
	public void onClogResult(String player, @Nullable ClogResult clog, boolean isSelf, int lookupVersionAtFire)
	{
		if (clog != null)
		{
			setClogSetupNoticeVisible(false);
			renderClogResult(clog);
		}
		else
		{
			if (isSelf && lookupSession.readsOwnLog())
			{
				setClogSetupNoticeVisible(true);
				if (!comparison.isComparisonMode())
				{
					BufferedImage icon = KillClogIcons.resizedPluginIcon(15, 15, itemManager);
					clogInfoLabel.setIcon(icon != null ? new ImageIcon(icon) : null);
					clogInfoLabel.setText(ClogHelper.pad("Setup"));
					clogInfoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
					tooltipController.setTooltipText(clogInfoLabel, " ");
				}
			}
			else
			{
				setClogSetupNoticeVisible(false);
			}
			updateClogCell(null);
			fetchRsn(player, lookupVersionAtFire);
		}
	}

	private void setClogSetupNoticeVisible(boolean visible)
	{
		clogNotice.setVisible(visible);
		revalidate();
		repaint();
	}

	@Override
	public void onCaResult(String player, @Nullable CombatAchievementResult ca, boolean isSelf, int lookupVersionAtFire)
	{
		if (lookupVersionAtFire == lookupSession.getLookupVersion())
		{
			preloadCaReward(ca);
			// Late evidence, like RuneProfile's group type, renews the kept identity in either view.
			if (comparison.isComparisonMode() && isIdentityRowShowing())
			{
				keptIdentity = currentInfoAccountDisplay();
				comparison.updateInfoBar();
			}
			else
			{
				updateDisplayedInfoIcon();
			}
		}
	}

	@Override
	public void onNotFound(String player)
	{
		resetForLookup();
		cells.rebuildPrimaryTooltips(localRsn);
		int notFoundIdx = ThreadLocalRandom.current().nextInt(SearchMessages.NOT_FOUND.length);
		setSearchStatus(String.format(SearchMessages.NOT_FOUND[notFoundIdx], player), NOT_FOUND);
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setText("");
	}

	@Override
	public void onError(String player, Throwable error)
	{
		resetForLookup();
		cells.rebuildPrimaryTooltips(localRsn);
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setText("");
		setSearchStatus(HiscoreService.failureText(error), TEXT_DIM);
	}

	// ComparisonController.Listener

	@Override
	public void onCompareDataReady()
	{
		updateRankPlayers();
		comparison.updateInfoBar();
		updateClogTotalsBar();
		refreshSkillDisplay();
	}

	private void updateRankPlayers()
	{
		// Leaderboards are main-game tables for one player: a League view keeps its own ranks,
		// and a comparison reads each player's own board, so an Ironman still meets a main.
		rankSelector.update(config.showLeaderboardSelector() && lookupSession.league() == null
			&& !comparison.isComparisonMode(), lookupSession.getCurrentLookupRsn(), lookupSession.getNativeHiscoreResult());
	}

	private void refreshRankDisplay()
	{
		tooltipController.hidePinnedTooltip();
		comparison.rebuildTooltipData();
		renderResults();
		// The board changes the mark, never who the account is; a comparison owns its own badges.
		if (!comparison.isComparisonMode() && isIdentityRowShowing())
		{
			showIdentity();
		}
		cells.rebuildPrimaryTooltips(localRsn);
		// A frozen row without a Collections Logged score must not keep today's count.
		if (comparison.isComparisonMode()) updateClogTotalsBar();
		else if (LookupSession.frozen(lookupSession.getHiscoreResult())) restoreClogCellForCompare(null);
		else updateClogCell(lookupSession.getClogResult());
		showFrozenLogNotice();
		getWrappedPanel().revalidate();
		getWrappedPanel().repaint();
	}

	/** What the board last put on the status line, so it clears only its own text. */
	private String boardStatus;

	/**
	 * RuneLite's own type on a self lookup, the identity the name row keeps, and what the name
	 * labels show: the hover cards repeat exactly these.
	 */
	@Nullable
	private AccountType selfType;
	@Nullable
	private AccountDisplay keptIdentity;
	@Nullable
	private AccountDisplay rowIdentity;
	@Nullable
	private AccountDisplay rivalIdentity;

	/** A frozen or empty board shows no log, so the status line says why; it clears only its own line. */
	private void showFrozenLogNotice()
	{
		HiscoreResult view = lookupSession.getHiscoreResult();
		if (LookupSession.frozen(view))
		{
			setSearchStatus(boardStatus = frozenLogNotice(view), TEXT_DIM);
		}
		else if (statusRow.statusText().equals(boardStatus))
		{
			setSearchStatus(" ", TEXT_DIM);
		}
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
			setSearchStatus(HiscoreService.failureText(err), ComparisonController.COMPARE_RED);
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
		portraits.lookedUp(clog);
	}

	@Override
	public void restoreClogCellForCompare(ClogResult clog)
	{
		clogInfoLabel.setText("");
		clogInfoLabel.setIcon(null);
		tooltipController.setTooltipText(clogInfoLabel, null);
		updateClogCell(clog);
	}
}
