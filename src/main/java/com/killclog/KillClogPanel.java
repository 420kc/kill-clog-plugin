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
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
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
	static final Color TEXT_DIM = new Color(160, 160, 160);
	static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);

	/** Info bar text color - only applies when highlighter is active AND clog data exists. */
	@Override
	public Color getInfoColor()
	{
		return config.completionistHighlighter() && lookupSession.getClogResult() != null
			? config.infoBarColor() : Cells.KC_COLOR;
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
	// The Player Summary's clan and its title icon; set by the plugin at startup.
	private PlayerClans clans;
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
	private final JLabel playerName = new HoverLabel()
	{
		@Override
		public JToolTip createToolTip()
		{
			if (comparison.showsPair())
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
	private final JLabel clogInfoLabel = new HoverLabel()
	{
		@Override
		public JToolTip createToolTip()
		{
			// Comparison mode: this label is the red RSN.
			if (comparison.showsPair())
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
	// Who is logged in now, null once logged out (a lookup of them is your own); your last login's name for the search icon.
	private String localRsn;
	private AccountType localAccountType;
	private String lastLocalRsn;

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

	private final TooltipController tooltipController;
	// Middle-button autoscroll; null only if RuneLite ever hands the panel no scroll pane.
	private PanelAutoscroll autoscroll;
	// The panel's top, which stays put while everything under it scrolls: the setup notice, the status row, search
	// and the info bar.
	private final JPanel anchoredTop = new JPanel();
	private final LookupSession lookupSession;
	private final ComparisonController comparison;
	// The empty log behind cold and unsynced previews, one copy for both sides.
	private final UnsyncedClogCatalog unsyncedCatalog;
	private final RankSelector rankSelector;
	// The Leagues switch: shown while a League runs, lit while the panel reads one. A League
	// world reads its League and any other world the main game; a click flips that until the
	// world changes. Every name in a League view wears the Leagues badge.
	private final JLabel leagueSwitch = new JLabel();
	private final ImageIcon leagueBadge = new ImageIcon(ImageUtil.resizeImage(
		AccountBadgeResolver.hiscoreIcon("seasonal.png"), 15, 15));
	private final ImageIcon leagueOff = RankSelector.icon((BufferedImage) leagueBadge.getImage(), 0.35f);
	private String worldLeague;
	private String activeLeague;
	private LocalClogCache worldLeagueLog;
	private boolean flipped;
	private java.util.function.Function<String, java.util.function.UnaryOperator<String>> selfPbs = league -> boss -> null;
	private final Cells cells;
	private final ClogPages clogPages;

	// Comparison mode widgets (state fields all live on the controller)

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
		tooltipController.setCardCopied(statusRow::flashCardCopy);
		this.unsyncedCatalog = new UnsyncedClogCatalog(clogService);
		this.lookupSession = new LookupSession(hiscoreService, clogService, runeProfileService,
			killclogService, config, this);
		this.comparison = new ComparisonController(hiscoreService, clogService, runeProfileService,
			killclogService, lookupSession, config, tooltipController, tooltipDataBuilder, unsyncedCatalog, this);
		this.comparison.setRenderTarget(this);
		this.rankSelector = new RankSelector(hiscoreService::lookupRanks, hiscoreService::notOnBoard, this::refreshRankDisplay);
		this.lookupSession.setRankView(rankSelector::view);
		this.comparison.setVirtualTotalLevel(
			() -> ClogHelper.virtualTotalLevelEnabled(configManager));
		this.skillCellGrid = new SkillCellGrid(skillIconManager, tooltipController, comparison,
			config, itemManager);
		this.cells = new Cells(spriteManager, itemManager, tooltipController, comparison, tooltipDataBuilder, lookupSession, unsyncedCatalog, killclogService, league -> selfPbs.apply(league), config,
			rankSelector, () -> localRsn);
		this.clogPages = new ClogPages(tooltipController, cells, comparison, lookupSession, tooltipDataBuilder, config);
		this.activityTooltips = new ActivitySummaryTooltips(
			lookupSession, comparison, cells, tooltipController, itemManager,
			caRewardSprites, config::cardLinks);
		this.itemNameResolver = new TooltipItemNameResolver(clientThread, itemManager,
			this::onTooltipItemNamesResolved);
		this.unsyncedCatalog.setResolver(itemNameResolver::resolve);
		this.comparison.setBlueNameSupplier(this::comparisonBlueName);
		this.comparison.setCells(this.cells);

		reloadTooltipSprites();

		// The anchored top's border is 6 not 10: the missing 4px live inside the status row
		// (taller row + compensating label inset), which lets the sync
		// chalice center truly in the panel-top-to-search-bar band while
		// the search bar and status text keep their exact positions.
		setBorder(new EmptyBorder(0, 10, 0, 10));
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
		clogNotice.setBorder(new EmptyBorder(0, 0, 5, 0));
		clogNotice.setAlignmentX(Component.LEFT_ALIGNMENT);

		// The top stays on screen down the whole boss list (Dylan, 2026-10-20): above RuneLite's scroll pane, inset on
		// the right by the scrollbar's width so it lines up with the content beside the bar.
		anchoredTop.setLayout(new BoxLayout(anchoredTop, BoxLayout.Y_AXIS));
		anchoredTop.setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Its 5 under the info bar is the gap the boss list always started below.
		anchoredTop.setBorder(new EmptyBorder(6, 10, 5, 10 + MinimalScrollBarUI.WIDTH));
		anchoredTop.add(clogNotice);
		JPanel search = buildSearchPanel();
		search.setAlignmentX(Component.LEFT_ALIGNMENT);
		anchoredTop.add(search);
		getWrappedPanel().add(anchoredTop, BorderLayout.NORTH);

		// Activities tray with slide animation
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
			autoscroll = new PanelAutoscroll(sp, tooltipController::suspendHover, tooltipController::resumeHover);
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
			statusRow::clearSearchStatus,
			this::lookupSelfFromSearchIcon,
			anchoredTop::revalidate);
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
			// Every other status here is cleared by a follow-up path; this one clears itself.
			statusRow.flashSearchStatus(COMPARE_VIEW_STATUS, TEXT_DIM, 2500);
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
					combatCell.setIcon(new ImageIcon(PanelIconCache.cellIcon(sprite, 20)));
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
		totalLvlCell.setIcon(new ImageIcon(PanelIconCache.cellIcon(AccountBadgeResolver.hiscoreIcon("overall.png"), 20)));
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

		// The six clue tiers, three to a row. The rare collections open from the Clue Summary.
		for (int first = 0; first < 6; first += 3)
		{
			JPanel clueRow = new JPanel(new GridLayout(1, 3));
			clueRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			clueRow.setAlignmentX(0f);
			for (int tier = first; tier < first + 3; tier++)
			{
				clueRow.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[tier], PanelData.CLUE_TIER_ITEM_IDS[tier]));
			}
			grid.add(clueRow);
		}

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
				unsyncedCatalog.result());
		}
		else
		{
			skillCellGrid.clear(unsyncedCatalog.result());
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
				unsyncedCatalog.result()),
			ClogHelper.summaryTotals(comparison.getCompareClogResult(), comparison.getCompareHiscoreResult(),
				unsyncedCatalog.result()), iconCache);
	}

	@Override
	public void onComparisonExit()
	{
		// Clear first: the board's own notice, set while its players change, must survive.
		statusRow.clearSearchStatus();
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
		statusRow.clearSearchStatus();
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

	void setCharacterDeleteHandler(Runnable handler)
	{
		statusRow.setCharacterDeleteHandler(handler);
	}

	void setCharacterPublishHandler(Runnable handler)
	{
		statusRow.setCharacterPublishHandler(handler);
	}

	void setSyncArrowEnabled(boolean enabled)
	{
		statusRow.setSyncArrowEnabled(enabled);
	}

	void setFirstParty(PanelStatusRow.FirstParty state)
	{
		statusRow.setFirstParty(state);
	}

	void setFirstPartyLockedHandler(Runnable handler)
	{
		statusRow.setLockedHandler(handler);
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

	void showCharacterPublishStatus(CharacterStatus status, boolean autoClear, String detail)
	{
		statusRow.showCharacterPublishStatus(status, autoClear, detail);
		if (status == CharacterStatus.PUBLISHED || status == CharacterStatus.DELETED)
		{
			// The player's own summary asks again at once: the character just published shows, a deleted one goes.
			portraits.published(localRsn);
		}
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

	private String selfSearchMessage(String player, boolean firstGreeting)
	{
		if (firstGreeting)
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
		clearSearch(SearchMessages.INVALID_NAME, NOT_FOUND);
	}

	/** The search box empties, its icon back to the magnifier, and the status line says why. */
	private void clearSearch(String status, Color color)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setText("");
		setSearchStatus(status, color);
	}

	private void lookupSelfFromSearchIcon()
	{
		if (lastLocalRsn == null || lastLocalRsn.isEmpty())
		{
			return;
		}
		searchRowController.exitIfActive();
		searchBar.setText(lastLocalRsn);
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
		bossListView.clearSearch();

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
		statusRow.clearSearchStatus();
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
		adoptName(result.getPlayerName());
		AccountType providerType = result.getProviderAccountType();
		if (providerType != null && providerType.isGroupIronman())
		{
			// Through the one badge rule, so your own account keeps RuneLite's type (a hardcore group ironman stays one).
			updateDisplayedInfoIcon(currentInfoAccountDisplay());
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
				if (thisLookup == lookupSession.getLookupVersion()) adoptName(name);
			})
		);
	}

	/** A provider's spelling of the name becomes the lookup's, shown once the hiscores have drawn the card. */
	private void adoptName(@Nullable String name)
	{
		if (name != null && !name.isEmpty())
		{
			rsn = name;
			if (lookupSession.getHiscoreResult() != null)
			{
				playerName.setText(name);
			}
		}
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
			unsyncedCatalog.result());
		if (totals[0] >= 0)
		{
			clogInfoLabel.setIcon(iconCache.clogTierIcon(totals));
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

	/** Your own PB reader for the viewed game (null: the main game): panel boss to its text, or null. */
	public void setSelfPbs(java.util.function.Function<String, java.util.function.UnaryOperator<String>> selfPbs)
	{
		this.selfPbs = selfPbs;
	}

	public void setLoggedInPlayer(String name, AccountType accountType)
	{
		this.localRsn = name;
		this.localAccountType = accountType;
		if (name != null)
		{
			this.lastLocalRsn = name;
		}
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

	void setClans(PlayerClans clans)
	{
		this.clans = clans;
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
		unsyncedCatalog.setClogIndex(clogIndex);
		clogPages.setClogIndex(clogIndex);
		comparison.setClogIndex(clogIndex);
		lookupSession.setClogIndex(clogIndex);
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
			case "cardLabelColor":
			case "cardValueColor":
			case "cardDividerColor":
				NativeTooltip.cardColors(config.cardLabelColor(), config.cardValueColor(), config.cardDividerColor());
				tooltipController.hidePinnedTooltip();
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
		NativeTooltip.cardColors(config.cardLabelColor(), config.cardValueColor(), config.cardDividerColor());
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
		if (autoscroll != null)
		{
			autoscroll.install();
		}
	}

	@Override
	public void onDeactivate()
	{
		if (autoscroll != null)
		{
			autoscroll.uninstall();
		}
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
		if (autoscroll != null)
		{
			autoscroll.uninstall();
		}
		tooltipController.deactivate();
	}

	@Override
	public void removeNotify()
	{
		super.removeNotify();
		tooltipController.hidePinnedTooltip();
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
			character != null ? character : iconCache.capeFor(hiscore),
			AccountBadgeResolver.badge(display),
			AccountBadgeResolver.label(display),
			LookupQueries.getPrestige(hiscore)
		);
		if (character != null)
		{
			tip.setCape(iconCache.capeFor(hiscore));
		}
		if (clans != null)
		{
			tip.setClan(clans.name(shownName, clog), clans.icon(shownName, clog));
		}
		tip.setLinks(config.cardLinks());
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
		tip.setLinks(config.cardLinks());
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
			tip.setClogSources(clog, clogService.onTemple(playerRsn), runeProfileService.hasProfile(playerRsn),
				lookupSession.onKillclog(playerRsn));
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
			if (lookupSession.offersSetup(localRsn, playerRsn))
			{
				tip.setFirstTimeSetup();
			}
			else if (hiscore != null)
			{
				tip.setNotice(cells.noClogNotice(playerRsn, hiscore));
			}
			else
			{
				// No player yet: preview the log's shape from the catalog.
				ClogResult catalog = unsyncedCatalog.result();
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
			int[] totals = ClogHelper.summaryTotals(null, hiscore, unsyncedCatalog.result());
			if (totals[0] >= 0) tip.setObtained(totals[0], totals[1]);
			if (hiscore.isRankDataAvailable()) tip.setRank(hiscore.getActivityRank("Collections Logged"));
		}
		return tip;
	}

	private AccountDisplay currentInfoAccountDisplay()
	{
		HiscoreResult hiscore = lookupSession.getHiscoreResult();
		return currentInfoAccountDisplay(hiscore != null ? hiscore.getAccountType() : null);
	}

	private AccountDisplay currentInfoAccountDisplay(@Nullable AccountType fallback)
	{
		HiscoreResult hiscore = lookupSession.getHiscoreResult();
		return selfType != null ? accountTypes.own(selfType, hiscore) : accountTypes.currentDisplay(fallback, hiscore,
			lookupSession.getNativeClogResult(), lookupSession.getCurrentLookupRsn());
	}

	// LookupSession.Listener

	@Override
	public void onLookupStart(String player, boolean isSelf, boolean isFirstSelfGreeting)
	{
		rankSelector.reset();
		if (isSelf)
		{
			setSearchStatus(selfSearchMessage(player, isFirstSelfGreeting), SearchMessages.SELF_COLOR);
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
		showHiscore(player, hiscore, knownType, isFirstSelfGreeting);
		if (clog != null)
		{
			renderClogResult(clog);
		}
	}

	@Override
	public void onHiscoreResult(String player, HiscoreResult hiscore,
		boolean isSelf, @Nullable AccountType knownType, boolean isFirstSelfGreeting)
	{
		showHiscore(player, hiscore, knownType, isFirstSelfGreeting);
		// Clog may have arrived first with a GIM type the hiscores can't detect.
		ClogResult clog = lookupSession.getClogResult();
		if (clog != null)
		{
			updateDisplayedInfoIcon();
			cells.renderClog(clog);
		}
	}

	/** A lookup's hiscores, cached or fresh: the search settles and the panel shows them. */
	private void showHiscore(String player, HiscoreResult hiscore, @Nullable AccountType knownType,
		boolean isFirstSelfGreeting)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		if (!isFirstSelfGreeting)
		{
			statusRow.clearSearchStatus();
		}
		renderHiscoreResult(hiscore, player, knownType);
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
		anchoredTop.revalidate();
		anchoredTop.repaint();
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
		clearSearch(String.format(SearchMessages.NOT_FOUND[notFoundIdx], player), NOT_FOUND);
	}

	@Override
	public void onError(String player, Throwable error)
	{
		resetForLookup();
		cells.rebuildPrimaryTooltips(localRsn);
		clearSearch(HiscoreService.failureText(error), TEXT_DIM);
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

	/** A frozen or empty board shows no log, so the status line says why; it clears only its own line. */
	private void showFrozenLogNotice()
	{
		HiscoreResult view = lookupSession.getHiscoreResult();
		if (LookupSession.frozen(view))
		{
			statusRow.setBoardStatus(rankSelector.logNotice(view));
		}
		else
		{
			statusRow.clearBoardStatus();
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
