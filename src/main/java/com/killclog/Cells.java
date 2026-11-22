/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 */
package com.killclog;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.*;
import javax.annotation.Nullable;
import javax.swing.*;
import lombok.Getter;
import net.runelite.client.game.*;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.*;
import net.runelite.client.util.*;

/**
 * The panel's cells: it builds them, holds their labels, keeps each one's card data and icons, writes the looked-up
 * player's values into them, and chooses between one player's card and a comparison's pair. KillClogPanel lays them out
 * and ComparisonController writes a comparison's values.
 */
public class Cells
{
	private static final String SETUP_NOTICE = "Open your Collection Log to start setup automatically";
	// A card with no player and no item list yet: the list is on its way.
	static final String CATALOG_NOTICE = "Loading catalog...";

	// Deps
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final TooltipController tooltipController;
	private final ComparisonController comparison;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final LookupSession lookupSession;
	private final KillclogService killclogService;
	// Your own PB reader for the viewed game (null: the main game).
	private final Function<String, UnaryOperator<String>> selfPbs;
	private final KillClogConfig config;
	private final UnsyncedClogCatalog unsyncedCatalog;
	private final RankSelector ranks;
	private final Supplier<String> localRsn;

	// Tooltip data caches
	@Getter
	private final Map<HiscoreSkill, TooltipData> tooltipDataMap = new LinkedHashMap<>();
	@Getter
	private final Map<String, TooltipData> rareTooltips = new LinkedHashMap<>();

	// Icon caches
	@Getter
	private final BufferedImage[] pvpActivityIcons = new BufferedImage[5];

	// Cell labels
	private final Map<HiscoreSkill, JLabel> bossLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, JLabel> activityLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, JLabel> clueTierLabels = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> originalIcons = new LinkedHashMap<>();
	private final Map<HiscoreSkill, ImageIcon> dimmedIcons = new LinkedHashMap<>();

	/**
	 * Boss PBs in the viewed game: yours from RuneLite's profiles, read once for the whole grid, anyone else's from
	 * their Kill Clog sync.
	 */
	UnaryOperator<String> pbs(boolean self)
	{
		ClogResult lookupClog = lookupSession.getClogResult();
		String league = lookupSession.league();
		return self ? selfPbs.apply(league)
			: lookupClog == null ? boss -> null
			: boss -> killclogService.pbText(league == null ? lookupClog.getPlayerName()
				: KillclogService.modeKey(league, lookupClog.getPlayerName()), boss);
	}

	public Cells(SpriteManager spriteManager, ItemManager itemManager,
		TooltipController tooltipController, ComparisonController comparison,
		TooltipDataBuilder tooltipDataBuilder, LookupSession lookupSession,
		UnsyncedClogCatalog unsyncedCatalog, KillclogService killclogService,
		Function<String, UnaryOperator<String>> selfPbs, KillClogConfig config, RankSelector ranks,
		Supplier<String> localRsn)
	{
		this.config = config;
		this.ranks = ranks;
		this.localRsn = localRsn;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		this.tooltipController = tooltipController;
		this.comparison = comparison;
		this.tooltipDataBuilder = tooltipDataBuilder;
		this.lookupSession = lookupSession;
		this.killclogService = killclogService;
		this.selfPbs = selfPbs;
		loadPvpActivityIcons();
		this.unsyncedCatalog = unsyncedCatalog;
	}

	// Cell builders

	/** Build the boss grid (3-wide GridLayout) populated with one cell per boss. */
	public JPanel buildBossGrid()
	{
		JPanel grid = new JPanel(new GridLayout(0, 3));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			grid.add(buildBossCell(boss));
		}
		return grid;
	}

	/** Build a single boss cell. Caller hooks listeners onto {@link #getBossLabel} after construction if needed. */
	public JPanel buildBossCell(HiscoreSkill boss)
	{
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildBossTooltip(this, boss);
			}
		};
		styleLabel(label, boss.getName());

		loadBossIcon(label, boss.getSpriteId(), icon ->
		{
			originalIcons.put(boss, icon);
			dimmedIcons.put(boss, new ImageIcon(PanelIconCache.createDimmedImage(icon)));
		});

		bossLabels.put(boss, label);
		return wrapInCell(label);
	}

	/** Load a 25x25 game sprite into a boss label at cell size, then hand it to the icon sink. */
	private void loadBossIcon(JLabel label, int spriteId, Consumer<ImageIcon> sink)
	{
		PanelIconCache.cellSprite(spriteManager, spriteId, 20, image ->
		{
			ImageIcon icon = new ImageIcon(image);
			label.setIcon(icon);
			sink.accept(icon);
		});
	}

	/** Build the All Clues cell, which opens the Clue Summary. */
	public JPanel buildClueSummaryCell()
	{
		HiscoreSkill activity = HiscoreSkill.CLUE_SCROLL_ALL;
		JLabel label = new JLabel(activity.getName())
		{
			@Override
			public JToolTip createToolTip()
			{
				return comparison.held(this,
					clueSummaryTooltip(this, lookupSession.getHiscoreResult(), lookupSession.getClogResult(), false),
					() -> clueSummaryTooltip(this, comparison.getCompareHiscoreResult(),
						comparison.getCompareClogResult(), true));
			}
		};
		styleLabel(label, activity.getName());

		PanelIconCache.cellSprite(spriteManager, activity.getSpriteId(), 16,
			image -> label.setIcon(new ImageIcon(ImageUtil.resizeCanvas(image, 20, 20))));

		activityLabels.put(activity, label);
		return wrapInCell(label);
	}

	/** Icons for the PvP rows inside the Combat Summary, loaded once. */
	private void loadPvpActivityIcons()
	{
		for (int i = 0; i < PanelData.PVP_ACTIVITIES.length; i++)
		{
			int spriteId = PanelData.PVP_ACTIVITIES[i].getSpriteId();
			if (spriteId == -1)
			{
				continue;
			}
			final int idx = i;
			PanelIconCache.cellSprite(spriteManager, spriteId, 20, icon -> pvpActivityIcons[idx] = icon);
		}
	}

	public JPanel buildClueTierCell(HiscoreSkill tier, int itemId)
	{
		String displayName = capitalizeTier(tier);
		JLabel label = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				return buildClueTierTooltip(this, tier, displayName);
			}
		};
		styleLabel(label, tier.getName());
		setItemIcon(label, itemId);
		clueTierLabels.put(tier, label);
		return wrapInCell(label);
	}

	// Primary-side rendering


	/**
	 * Write the hiscores' values into every cell for the looked-up player: bosses (text, color and the dimmed icon),
	 * activities and clue tiers. {@link ComparisonController#updateAllCells()} does the same for a comparison.
	 */
	public void renderHiscore(HiscoreResult result)
	{
		// Boss cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			renderBossValue(entry.getKey(), entry.getValue(), result);
		}
		// Activity cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : activityLabels.entrySet())
		{
			HiscoreSkill activity = entry.getKey();
			JLabel label = entry.getValue();
			int score = result.getActivityScore(activity.getName());
			showCount(label, score, KillClogColors.KC_COLOR);
			tooltipController.setTooltipText(label, " ");
		}

		// Clue tier cells
		for (Map.Entry<HiscoreSkill, JLabel> entry : clueTierLabels.entrySet())
		{
			HiscoreSkill tier = entry.getKey();
			JLabel label = entry.getValue();
			int score = result.getActivityScore(tier.getName());
			showCount(label, score, KillClogColors.KC_COLOR);

			String shortName = capitalizeTier(tier);
			int rank = result.getActivityRank(tier.getName());
			tooltipController.setTooltipText(label, rank > 0
				? shortName + "\nRank: {w}" + TitleTooltip.grouped(rank)
				: shortName);
		}
	}

	/** Build the rare clue collections from the clog; the Clue Summary lists and opens them. */
	public void renderClog(ClogResult result)
	{
		putRareRows(result, null);
	}

	/**
	 * Every cell back to how it rests before a lookup: dashes in the resting color, the resting card, each boss's own
	 * icon, and no card data kept. The panel resets its own header alongside.
	 */
	public void reset()
	{
		tooltipDataMap.clear();
		rareTooltips.clear();
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			JLabel label = entry.getValue();
			showCount(label, 0, null);
			tooltipController.setTooltipText(label, " ");
			ImageIcon orig = originalIcons.get(entry.getKey());
			if (orig != null) label.setIcon(orig);
		}
		resetLabels(activityLabels);
		resetLabels(clueTierLabels);
	}


	/** A count cell's number in its color, or a grey "--" without one: every count cell on the panel. */
	static void showCount(JLabel label, int count, @Nullable Color color)
	{
		label.setText(ClogHelper.pad(ClogHelper.countText(count)));
		label.setForeground(count > 0 ? color : ColorScheme.LIGHT_GRAY_COLOR);
	}

	private void resetLabels(Map<HiscoreSkill, JLabel> labels)
	{
		for (Map.Entry<HiscoreSkill, JLabel> entry : labels.entrySet())
		{
			JLabel label = entry.getValue();
			showCount(label, 0, null);
			tooltipController.setTooltipText(label, entry.getKey().getName());
		}
	}

	/**
	 * Build each cell's card data again for the looked-up player from the lookup's results, as {@link
	 * ComparisonController#enter()} does for the rival.
	 */
	public void rebuildPrimaryTooltips()
	{
		tooltipDataMap.clear();
		if (lookupSession.getClogResult() == null)
		{
			rareTooltips.clear();
		}
		boolean self = RsnInputPolicy.sameName(localRsn.get(), lookupSession.getCurrentLookupRsn());
		boolean selfNoCache = awaitsOwnSetup();
		ClogResult catalog = lookupSession.getClogResult() == null && !selfNoCache
			? unsyncedCatalog.result() : null;

		// Boss cells, none for the sync-prompt self-lookup. RuneLite records pbs on the local profile only; for looked-up
		// players the killclog.com sync is the one source that serves them, cached by the same fetch that raced the clog
		// providers.
		UnaryOperator<String> pbs = pbs(self);
		for (Map.Entry<HiscoreSkill, JLabel> entry : bossLabels.entrySet())
		{
			TooltipData data = selfNoCache ? null : tooltipDataBuilder.bossCard(entry.getKey(),
				lookupSession.getHiscoreResult(), lookupSession.getClogResult(), pbs, unsyncedCatalog.result());
			if (data != null)
			{
				tooltipDataMap.put(entry.getKey(), data);
			}
			tooltipController.setTooltipText(entry.getValue(), " ");
		}
		// Clue tier cells
		if (lookupSession.getClogResult() == null)
		{
			if (!selfNoCache)
			{
				rebuildClueTooltips(null, catalog);
				putRareRows(null, catalog);
			}
			return;
		}
		rebuildClueTooltips(lookupSession.getClogResult(), null);
	}

	/** Each clue tier cell's card, from the synced log or, without one, the catalog's preview. */
	private void rebuildClueTooltips(@Nullable ClogResult clog, @Nullable ClogResult catalog)
	{
		for (HiscoreSkill skill : PanelData.CLUE_CATEGORIES.keySet())
		{
			JLabel label = clueTierLabels.get(skill);
			// A synced log without the tier shows no card, never the preview.
			TooltipData data = label == null ? null
				: tooltipDataBuilder.buildClueTierCard(skill, lookupSession.getHiscoreResult(), clog, catalog);
			if (data != null)
			{
				tooltipDataMap.put(skill, data);
				if (clog != null)
				{
					tooltipDataBuilder.preloadItemImages(data);
				}
			}
			if (label != null && (data != null || clog == null))
			{
				tooltipController.setTooltipText(label, " ");
			}
		}
	}

	private void putRareRows(@Nullable ClogResult clog, @Nullable ClogResult catalog)
	{
		for (int i = 0; i < PanelData.RARE_ROWS.size(); i++)
		{
			TooltipData data = tooltipDataBuilder.rareRow(i, clog, catalog);
			if (data != null)
			{
				rareTooltips.put(PanelData.RARE_ROWS.get(i).getKey(), data);
			}
		}
	}

	// Tooltip routing

	/**
	 * A panel cell's own card, for whatever else shows it (a list row, a summary, a Collection Log page): a
	 * boss's or a clue tier's. Owner is the anchor label.
	 */
	public JToolTip buildCellTooltipFor(JLabel owner, HiscoreSkill cell)
	{
		return PanelData.CLUE_CATEGORIES.containsKey(cell) ? buildClueTierTooltip(owner, cell, capitalizeTier(cell))
			: buildBossTooltip(owner, cell);
	}

	/**
	 * A Collection Log page's popup: its grid for the player, or both players' side by side. Its title opens the
	 * page's section of the wiki's Collection log, where a boss's title opens the boss.
	 */
	public JToolTip buildPageTooltip(JLabel owner, String name, @Nullable TooltipData blue, @Nullable TooltipData red)
	{
		return gridTooltip(owner, blue, () -> red, name, PanelData.CLOG_WIKI_PAGE + "#" + name);
	}

	private JToolTip buildBossTooltip(JLabel owner, HiscoreSkill boss)
	{
		HiscoreSkill card = PanelData.card(boss);
		TooltipData red = comparison.getCompareTooltipData(boss);
		return gridTooltip(owner, tooltipDataMap.get(boss), () -> red, card.getName(), PanelData.bossWikiPage(card));
	}

	/**
	 * A cell's card, with a notice where there is no data. {@code owner} is the label whose cell takes the hover,
	 * {@code data} the card's data (null for the notice alone), {@code gridCols} the fewest columns its items take and
	 * {@code name} the title when there is no data; {@code result} with {@code rsn} says whose card it is.
	 */
	JToolTip cellCard(JLabel owner, TooltipData data, int gridCols,
		String name, @Nullable HiscoreResult result, @Nullable String rsn)
	{
		ImgTooltip tip = new ImgTooltip(gridCols);
		tip.setComponent(owner);
		tip.setLinks(config.cardLinks());

		if (!configure(tip, data, itemManager, config.showTooltipKc(), config.showTooltipPb()))
		{
			tip.setTitle(data != null ? data.name : name);
			boolean isSelfNoCache = result != null && lookupSession.offersSetup(localRsn.get(), rsn);
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
				tip.setNotice(CATALOG_NOTICE);
			}
		}
		configureRank(tip, data, result, config.showTooltipRank());
		return tip;
	}

	// The words where there is no log to show: another player hasn't synced one, or theirs hasn't loaded. Plainly said,
	// naming no site and blaming no one.
	String noClogNotice(String rsn, @Nullable HiscoreResult hiscore)
	{
		return LookupSession.frozen(hiscore) ? ranks.logNotice(hiscore)
			: rsn == null || rsn.isEmpty() ? "No Collection Log synced"
			: lookupSession.logUnanswered(rsn) ? rsn + "'s Collection Log hasn't loaded"
			: rsn + " hasn't synced a Collection Log";
	}

	/** One log's card: the player's grid, or both players' side by side, the title opening the wiki. */
	private JToolTip gridTooltip(JLabel owner, @Nullable TooltipData blue, Supplier<TooltipData> red, String name,
		@Nullable String wikiPage)
	{
		JToolTip tip = cellCard(owner, blue, CardBody.MIN_COLS, name, lookupSession.getHiscoreResult(), lookupSession.getCurrentLookupRsn());
		linkTitle(tip, wikiPage);
		return comparison.held(owner, tip, () ->
		{
			JToolTip rival = cellCard(owner, red.get(), CardBody.MIN_COLS, name, comparison.getCompareHiscoreResult(),
				comparison.getCompareRsn());
			linkTitle(rival, wikiPage);
			return rival;
		});
	}

	private static void linkTitle(JToolTip tip, @Nullable String wikiPage)
	{
		if (tip instanceof TitleTooltip)
		{
			((TitleTooltip) tip).setTitleWikiPage(wikiPage);
		}
	}

	/** A boss's icon as its cell shows it with kills, never the dimmed one: a rival's Most Killed tile wears it. */
	@Nullable
	Image bossIcon(HiscoreSkill boss)
	{
		ImageIcon icon = originalIcons.get(boss);
		return icon != null ? icon.getImage() : null;
	}

	/** Write one boss cell's KC text, color, and icon dim state from the name-keyed hiscore map. */
	private void renderBossValue(HiscoreSkill boss, JLabel label, HiscoreResult result)
	{
		int kc = result.getBossKills().getOrDefault(PanelData.hiscoreName(boss.getName()), -1);
		showCount(label, kc, KillClogColors.KC_COLOR);
		ImageIcon orig = originalIcons.get(boss);
		if (orig != null)
		{
			label.setIcon(kc > 0 ? orig : dimmedIcons.get(boss));
		}
	}

	private ClueSummaryTooltip clueSummaryTooltip(JLabel owner, @Nullable HiscoreResult result,
		@Nullable ClogResult clog, boolean rival)
	{
		ClueSummaryTooltip tip = new ClueSummaryTooltip();
		tip.setSprites(itemManager);
		for (int i = 0; i < PanelData.CLUE_TIERS.length; i++)
		{
			tip.setProgress(i + 1, ClogHelper.clogCounts(
				PanelData.CLUE_CATEGORIES.get(PanelData.CLUE_TIERS[i]), clog));
		}
		// The Mimic's one log slot is the 3rd age ring.
		tip.setProgress(7, clog == null ? null : counts(tooltipDataBuilder.buildPageData(
			"Mimic", Collections.singletonList(PanelData.THIRD_AGE_RING_ITEM_ID), clog)));
		for (int i = 0; i < PanelData.RARE_ROWS.size(); i++)
		{
			tip.setRare(i, counts(rareData(i, rival)));
		}
		// A rare row, a tier's line or Mimic's opens that card where the summary was, its back the summary.
		tip.setOnOpenRare((press, row) -> openFromClueSummary(owner, press, buildRareTooltipFor(owner, row)));
		tip.setOnOpenLine((press, line) -> openFromClueSummary(owner, press, buildCellTooltipFor(owner,
			line == ClueSummaryTooltip.MIMIC ? HiscoreSkill.MIMIC : PanelData.CLUE_TIERS[line - 1])));
		tip.setComponent(owner);
		tip.setData(result, config.showTooltipRank());
		return tip;
	}

	private void openFromClueSummary(JLabel owner, MouseEvent press, JToolTip card)
	{
		tooltipController.openInPlace(owner, press, card, "< Clue Summary");
	}

	private JToolTip buildClueTierTooltip(JLabel owner, HiscoreSkill tier, String displayName)
	{
		return gridTooltip(owner, cluesCompleted(tooltipDataMap.get(tier), tier, lookupSession.getHiscoreResult()),
			() -> cluesCompleted(comparison.buildCompareClueTierData(tier), tier, comparison.getCompareHiscoreResult()),
			displayName, PanelData.clueWikiPage(tier));
	}

	/** A clue tier's completed count, labeled as the game's own Collection Log page labels it. */
	static String cluesCompletedLabel(HiscoreSkill tier)
	{
		return capitalizeTier(tier) + " clues completed: ";
	}

	/** A clue card's data reading its tier's completed clues where a boss card reads its KC. */
	@Nullable
	private static TooltipData cluesCompleted(@Nullable TooltipData data, @Nullable HiscoreSkill tier,
		@Nullable HiscoreResult result)
	{
		return data == null || tier == null || result == null ? data
			: data.toBuilder().kcLabel(cluesCompletedLabel(tier)).kc(result.getActivityScore(tier.getName())).build();
	}

	/** Progress for a Clue Summary row; unsynced or unknown data shows none. */
	@Nullable
	private static int[] counts(@Nullable TooltipData data)
	{
		return data == null || data.obtainedCount < 0 ? null
			: new int[]{data.obtainedCount, data.totalItems};
	}

	private TooltipData rivalRare(int row)
	{
		return tooltipDataBuilder.rareRow(row, comparison.getCompareClogResult(), unsyncedCatalog.result());
	}

	/** A rare row's data for either player, as its card shows it. */
	@Nullable
	TooltipData rareData(int row, boolean rival)
	{
		return rival ? rivalRare(row) : rareTooltips.get(PanelData.RARE_ROWS.get(row).getKey());
	}

	/**
	 * Your own account before its log is set up: its cards say how to set it up, a page's opened from a tile too, never
	 * the catalog's preview.
	 */
	boolean awaitsOwnSetup()
	{
		return lookupSession.getClogResult() == null
			&& lookupSession.offersSetup(localRsn.get(), lookupSession.getCurrentLookupRsn());
	}

	/** A Collection Log page's card data: its log's, else the catalog's dimmed preview, as a boss's page. */
	@Nullable
	TooltipData pageData(String name, java.util.List<Integer> items, @Nullable ClogResult clog)
	{
		return clog != null ? tooltipDataBuilder.buildPageData(name, items, clog)
			: tooltipDataBuilder.buildUnsyncedItemData(name, items, unsyncedCatalog.result());
	}

	/** A Clue Summary rare row's card, for the Collection Log page it stands for. */
	JToolTip buildRareTooltipFor(JLabel owner, int row)
	{
		RareRow rare = PanelData.RARE_ROWS.get(row);
		HiscoreSkill tier = rare.getTier();
		return gridTooltip(owner, cluesCompleted(rareTooltips.get(rare.getKey()), tier,
			lookupSession.getHiscoreResult()), () -> cluesCompleted(rivalRare(row), tier, comparison.getCompareHiscoreResult()),
			rare.getName(), rare.getWikiPage());
	}

	// Helpers

	/** Standard grid-cell label styling (used by every cell on the panel). */
	public static void styleLabel(JLabel label, String tooltipText)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		showCount(label, 0, null);
		label.setIconTextGap(4);
		label.setToolTipText(tooltipText);
		ClogHelper.antialias(label);
	}

	/** Wrap a label in a standard grid cell panel with hover effect wired. */
	public JPanel wrapInCell(JLabel label)
	{
		return cell(label, tooltipController, comparison);
	}

	/** One panel cell around its label, with the grid's hover and the comparison's: every grid builds them here. */
	static JPanel cell(JLabel label, TooltipController tooltipController, ComparisonController comparison)
	{
		JPanel cell = new JPanel();
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(TooltipController.CELL_BORDER);
		cell.add(label);
		tooltipController.addCellHoverEffect(cell, label);
		comparison.installCompareCellHover(cell, label);
		return cell;
	}

	/** A cell's own icon, for a card that repeats what the panel shows (a skill's, the combat level's); null with none. */
	@Nullable
	static Image icon(JLabel cell)
	{
		return cell.getIcon() instanceof ImageIcon ? ((ImageIcon) cell.getIcon()).getImage() : null;
	}

	/** An item's sprite in a label at cell size, swapped in again once a still-loading image arrives. */
	void setItemIcon(JLabel label, int itemId)
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

	/** "Clue Scrolls (hard)" -> "Hard". */
	public static String capitalizeTier(HiscoreSkill tier)
	{
		String name = tier.getName().replace("Clue Scrolls (", "").replace(")", "");
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	// Read-only accessors

	public Map<HiscoreSkill, JLabel> getBossLabels()
	{
		return Collections.unmodifiableMap(bossLabels);
	}

	public Map<HiscoreSkill, JLabel> getActivityLabels()
	{
		return Collections.unmodifiableMap(activityLabels);
	}

	public Map<HiscoreSkill, JLabel> getClueTierLabels()
	{
		return Collections.unmodifiableMap(clueTierLabels);
	}

	@Nullable
	public JLabel getBossLabel(HiscoreSkill boss)
	{
		return bossLabels.get(boss);
	}

	@Nullable
	public TooltipData getTooltipData(HiscoreSkill skill)
	{
		return tooltipDataMap.get(skill);
	}

	/**
	 * A card from its data, synced log or not: its Obtained line ("--/Y" with no synced log, since the card has the
	 * room for its counts too), its counts and its items, those not held dimmed so wiki links remain explorable; a PB
	 * with a synced log only. False when the data holds neither a log nor the catalog's preview.
	 */
	static boolean configure(ImgTooltip tip, @Nullable TooltipData data, ItemManager itemManager, boolean showKc,
		boolean showPb)
	{
		if (data == null || data.obtainedCount < 0 && (data.allItemIds == null || data.allItemIds.isEmpty()))
		{
			return false;
		}
		tip.setTitle(data.name);
		tip.setObtained(data.obtainedCount, data.totalItems);
		addCountLines(tip, data, showKc, showPb && data.obtainedCount >= 0);
		if (data.allItemIds != null)
		{
			tip.setItems(data.totalItems, data.allItemIds, data.obtainedIds, data.obtainedCounts, data.itemNames, itemManager);
		}
		return true;
	}

	/**
	 * A card's counts under its Obtained line, synced log or not: its lead (KC, or the count its page leads with) with
	 * the PB beside it, else the PB alone; a raid's hard mode under that, by the name the Combat Summary pairs it by; then
	 * a page count on its own line (a raid's Entry, Wintertodt's rewards; PageCounts).
	 */
	static void addCountLines(TitleTooltip tip, TooltipData data, boolean showKc, boolean showPb)
	{
		countLine(tip, data.kcLabel != null ? data.kcLabel : "KC: ", "", data.kc, data.pb, showKc, showPb);
		if (data.hardLabel != null)
		{
			countLine(tip, data.hardLabel + ": ", data.hardLabel + " ", data.hardKc, data.hardPb, showKc, showPb);
		}
		if (data.entryKc >= 0 && showKc)
		{
			tip.addInfoLine(data.entryLabel + ": ", TitleTooltip.grouped(data.entryKc), NativeTooltip.valueColor);
		}
	}

	/** A count with its PB beside it, else the PB alone under the count's own name. */
	private static void countLine(TitleTooltip tip, String label, String pbName, int count, @Nullable String pb,
		boolean showKc, boolean showPb)
	{
		if (count >= 0 && showKc)
		{
			tip.addInfoLine(label, TitleTooltip.grouped(count), NativeTooltip.valueColor);
			if (pb != null && showPb)
			{
				tip.setInfoLinePair(pbLabel(pb), pbTime(pb), NativeTooltip.valueColor);
			}
		}
		else if (pb != null && showPb)
		{
			tip.addInfoLine(pbName + pbLabel(pb), pbTime(pb), NativeTooltip.valueColor);
		}
	}

	/** "PB: ", or a team size's "PB (5 players): " ({@link PersonalBests#labeled}). */
	static String pbLabel(String pb)
	{
		return pb.startsWith("(") ? "PB " + pb.substring(0, pb.indexOf(')') + 1) + ": " : "PB: ";
	}

	static String pbTime(String pb)
	{
		return pb.substring(pb.indexOf(')') + 1).trim();
	}

	/** Missing entries on a loaded leaderboard are unranked, not failed lookups. */
	static void configureRank(TitleTooltip tip, TooltipData data, HiscoreResult result, boolean showRank)
	{
		if (showRank && result != null && result.isRankDataAvailable()
			&& data != null && data.rankTracked
			&& (data.obtainedCount >= 0 || data.kc >= 0 || data.rank > 0 || data.hardKc >= 0 || data.hardRank > 0))
		{
			tip.setRank(data.rank);
			if (data.hardLabel != null)
			{
				tip.setRankPair(data.hardLabel + ": ", data.hardRank);
			}
		}
	}
}
