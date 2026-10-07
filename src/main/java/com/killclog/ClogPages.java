package com.killclog;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import lombok.Setter;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Every Collection Log page, reached from the Clog Summary: a tab's row opens the tab's pages in the
 * game's order, and a page opens its popup, each where the summary was. A page that is one panel boss's
 * opens that boss's own popup; any other shows the game's own items for it, held from anywhere in the
 * log. A row's count always comes from the popup it opens. A comparison pairs both players at every step.
 */
final class ClogPages
{
	// Pages past the longest normal grid (Elite clues, 59 items) draw small, like the dense clue tiers.
	private static final int DENSE_PAGE = 60;
	// Each page's panel bosses, keyed as the game's pages are.
	private static final Map<String, List<HiscoreSkill>> PAGE_BOSSES = new HashMap<>();

	static
	{
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			PAGE_BOSSES.computeIfAbsent(ClogService.bossToCategory(
				PanelData.NAME_OVERRIDES.getOrDefault(boss.getName(), boss.getName())), k -> new ArrayList<>()).add(boss);
		}
	}

	private final TooltipController tooltipController;
	private final Cells cells;
	private final ComparisonController comparison;
	private final LookupSession lookupSession;
	private final TooltipDataBuilder tooltipDataBuilder;
	@Setter
	@Nullable
	private ClogIndex clogIndex;

	ClogPages(TooltipController tooltipController, Cells cells, ComparisonController comparison,
		LookupSession lookupSession, TooltipDataBuilder tooltipDataBuilder)
	{
		this.tooltipController = tooltipController;
		this.cells = cells;
		this.comparison = comparison;
		this.lookupSession = lookupSession;
		this.tooltipDataBuilder = tooltipDataBuilder;
	}

	/** A tab row's press: the tab's pages replace the Clog Summary it came from. */
	void openTab(JComponent owner, MouseEvent press, String tab)
	{
		pin(owner, press, tabCard(owner, tab));
	}

	JToolTip tabCard(JComponent owner, String tab)
	{
		ClogTabTooltip blue = side(owner, tab, lookupSession.getClogResult(), false);
		if (!comparison.isComparisonMode())
		{
			return blue;
		}
		ClogTabTooltip red = side(owner, tab, comparison.getCompareClogResult(), true);
		blue.scrollWith(red);
		return comparison.wrapSideBySide(owner, blue, red);
	}

	/** A page's popup, as its row opens it, leading back to its tab. */
	JToolTip pageCard(JComponent owner, String tab, String key)
	{
		return TitleTooltip.withBack(page(owner, key), "< " + tab, press -> pin(owner, press, tabCard(owner, tab)));
	}

	private JToolTip page(JComponent owner, String key)
	{
		JLabel anchor = anchor(owner);
		String name = name(key);
		HiscoreSkill boss = boss(key, name);
		if (boss != null)
		{
			return cells.buildBossTooltipFor(anchor, boss);
		}
		List<Integer> items = items(key);
		TooltipData blue = tooltipDataBuilder.buildPageData(name, items, lookupSession.getClogResult());
		TooltipData red = comparison.isComparisonMode()
			? tooltipDataBuilder.buildPageData(name, items, comparison.getCompareClogResult()) : null;
		for (TooltipData data : new TooltipData[]{blue, red})
		{
			if (data != null)
			{
				tooltipDataBuilder.preloadItemImages(data);
			}
		}
		return cells.buildPageTooltip(anchor, name, distinct(items).size() > DENSE_PAGE, blue, red);
	}

	/** One player's side of a tab: each page's count from the popup its row opens. */
	private ClogTabTooltip side(JComponent owner, String tab, @Nullable ClogResult clog, boolean red)
	{
		ClogIndex index = clogIndex;
		List<String> keys = index != null ? index.tabPages().getOrDefault(tab, Collections.emptyList())
			: Collections.emptyList();
		String[] names = new String[keys.size()];
		int[] obtained = new int[keys.size()];
		int[] total = new int[keys.size()];
		Set<Integer> slots = new HashSet<>();
		for (int i = 0; i < keys.size(); i++)
		{
			String key = keys.get(i);
			names[i] = name(key);
			Set<Integer> items = distinct(items(key));
			slots.addAll(items);
			HiscoreSkill boss = boss(key, names[i]);
			TooltipData data = boss != null
				? (red ? comparison.getCompareTooltipData(boss) : cells.getTooltipDataMap().get(boss))
				: tooltipDataBuilder.buildPageData(names[i], items(key), clog);
			obtained[i] = data != null ? data.obtainedCount : -1;
			total[i] = data != null ? data.totalItems : items.size();
		}
		Map<String, int[]> progress = clog == null || index == null ? Collections.emptyMap()
			: index.tabProgress(clog, ClogHelper.sumClogTotals(clog, index::canonicalItemId));

		ClogTabTooltip card = new ClogTabTooltip();
		card.setComponent(owner);
		card.setTab(tab, progress.get(tab), slots.size());
		card.setPages(names, obtained, total);
		card.setBack("< Clog Summary", press -> pin(owner, press, owner.createToolTip()));
		card.setOnOpenPage((press, page) -> pin(owner, press, pageCard(owner, tab, keys.get(page))));
		return card;
	}

	/**
	 * The panel boss whose popup is this page's: the one boss on the page, or the one the page is named
	 * for (Chambers of Xeric, not its Challenge Mode; The Nightmare, not Phosani's). A page shared by
	 * bosses, like the Dagannoth Kings, has its own popup.
	 */
	@Nullable
	static HiscoreSkill pageBoss(String key, String name)
	{
		List<HiscoreSkill> bosses = PAGE_BOSSES.getOrDefault(key, Collections.emptyList());
		HiscoreSkill boss = bosses.size() == 1 ? bosses.get(0) : null;
		for (HiscoreSkill candidate : bosses)
		{
			if (candidate.getName().replaceFirst("(?i)^the ", "").equalsIgnoreCase(name.replaceFirst("(?i)^the ", "")))
			{
				boss = candidate;
			}
		}
		return boss;
	}

	/** The page's boss while that boss has a popup to open. */
	@Nullable
	private HiscoreSkill boss(String key, String name)
	{
		HiscoreSkill boss = pageBoss(key, name);
		return boss != null && cells.getTooltipDataMap().get(boss) != null ? boss : null;
	}

	private String name(String key)
	{
		String name = clogIndex != null ? clogIndex.pageName(key) : null;
		return name != null ? name : key;
	}

	private List<Integer> items(String key)
	{
		List<Integer> items = clogIndex != null ? clogIndex.categoryItems().get(key) : null;
		return items != null ? items : Collections.emptyList();
	}

	private Set<Integer> distinct(List<Integer> items)
	{
		Set<Integer> distinct = new HashSet<>();
		for (int item : items)
		{
			distinct.add(clogIndex != null ? clogIndex.canonicalItemId(item) : item);
		}
		return distinct;
	}

	/** The new card where the summary was: under the same cell, from the same source. */
	private void pin(JComponent owner, MouseEvent press, JToolTip tip)
	{
		tooltipController.pinTooltipFromPress(owner,
			owner instanceof JPanel ? (JPanel) owner : (JPanel) owner.getParent(), press, tip);
	}

	/** The label popups hang from: the summary's own, or the first label of the comparison's totals bar. */
	private static JLabel anchor(JComponent owner)
	{
		if (owner instanceof JLabel)
		{
			return (JLabel) owner;
		}
		for (Component child : owner.getComponents())
		{
			if (child instanceof JLabel)
			{
				return (JLabel) child;
			}
		}
		throw new IllegalStateException("no label to anchor a page popup");
	}
}
