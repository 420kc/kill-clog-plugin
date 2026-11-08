package com.killclog;

import java.util.*;
import javax.annotation.Nullable;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Static reference data for the panel grid.
 * Pure constants, no state.
 */
final class PanelData
{
	private PanelData()
	{
	}

	static final int MAX_TOTAL_LEVEL = 2376;

	// Boss display order matching vanilla RuneLite hiscores: enum declaration
	// order. Same bosses as HiscoreService.bossNames() - name-keyed lookups
	// bridge the two lists when order diverges.
	// New boss? Add it here in enum order once RuneLite adds it.
	// See BOSS_NAMES comment in HiscoreService for the full update playbook.
	static final HiscoreSkill[] BOSSES = bosses();

	static int bossCount()
	{
		return BOSSES.length;
	}

	// RuneLite's or Jagex's spelling -> the name Kill Clog keys a boss on, where the key predates the spelling.
	// Only entries where the two differ are needed.
	private static final Map<String, String> NAME_OVERRIDES = Map.of("Calvar'ion", "Cal'varion");

	/** The name Kill Clog keys this boss on, for a HiscoreSkill name or a hiscore feed name. */
	static String hiscoreName(String name)
	{
		return NAME_OVERRIDES.getOrDefault(name, name);
	}

	// Hiscore display name -> OSRS Wiki page where the page title differs.
	private static final Map<HiscoreSkill, String> BOSS_WIKI_PAGES = new LinkedHashMap<>();
	static
	{
		BOSS_WIKI_PAGES.put(HiscoreSkill.BARROWS_CHESTS, "Barrows");
		BOSS_WIKI_PAGES.put(HiscoreSkill.MIMIC, "The Mimic");
		BOSS_WIKI_PAGES.put(HiscoreSkill.NIGHTMARE, "The Nightmare");
	}

	// The wiki's Collection log article holds a section for every page, under the page's own name.
	static final String CLOG_WIKI_PAGE = "Collection log";

	static String bossWikiPage(HiscoreSkill boss)
	{
		String page = BOSS_WIKI_PAGES.get(boss);
		return page != null ? page : boss.getName();
	}

	static final HiscoreSkill[] CLUE_TIERS = {
		HiscoreSkill.CLUE_SCROLL_BEGINNER, HiscoreSkill.CLUE_SCROLL_EASY,
		HiscoreSkill.CLUE_SCROLL_MEDIUM, HiscoreSkill.CLUE_SCROLL_HARD,
		HiscoreSkill.CLUE_SCROLL_ELITE, HiscoreSkill.CLUE_SCROLL_MASTER,
	};

	static final String CLOG_THIRD_AGE = "third_age";
	static final String CLOG_GILDED = "gilded";
	static final int[] CLUE_TIER_ITEM_IDS = itemIds("clue_tier_items");
	static final int THIRD_AGE_RING_ITEM_ID = 23185;
	static final int[] THIRD_AGE_ITEMS = itemIds("third_age_items");
	static final int[] GILDED_ITEMS = itemIds("gilded_items");
	// The log items the game counts past 250, up to 65,535; every other item stops at 250.
	static final int[] COUNTS_PAST_250_ITEMS = itemIds("counts_past_250_items");


	// The Clue Summary's rare rows in its order, one per collection (clue-rare-rows.tsv).
	static final List<RareRow> RARE_ROWS = rareRows(CatalogTsv.rows(PanelData.class, "clue-rare-rows.tsv", 8));

	// Clue tier -> collection-log category key, derived from the enum name:
	// CLUE_SCROLL_BEGINNER -> beginner_treasure_trails.
	static final Map<HiscoreSkill, String> CLUE_CATEGORIES = new LinkedHashMap<>();
	static
	{
		for (HiscoreSkill tier : CLUE_TIERS)
		{
			CLUE_CATEGORIES.put(tier, tier.name()
				.substring("CLUE_SCROLL_".length()).toLowerCase(Locale.US)
				+ "_treasure_trails");
		}
	}

	/** A clue tier's scroll article on the wiki, as a boss card's title opens the boss's: "Clue scroll (beginner)". */
	static String clueWikiPage(HiscoreSkill tier)
	{
		return tier.getName().replace("Clue Scrolls", "Clue scroll");
	}

	// Collection log book item, used for chat icons and plugin icon fallbacks.
	static final int COLLECTION_LOG_ITEM_ID = 22711;

	static final HiscoreSkill[] PVP_ACTIVITIES = {
		HiscoreSkill.LAST_MAN_STANDING,
		HiscoreSkill.SOUL_WARS_ZEAL,
		HiscoreSkill.PVP_ARENA_RANK,
		HiscoreSkill.BOUNTY_HUNTER_HUNTER,
		HiscoreSkill.BOUNTY_HUNTER_ROGUE,
	};

	static final String GOTR_CATEGORY = "guardians_of_the_rift";
	static final String RIFTS_CLOSED_ACTIVITY = "Rifts closed";

	static final String SLAYER_CATEGORY = "slayer";
	static final int IMBUED_HEART_ITEM_ID = 20724;
	static final int ETERNAL_GEM_ITEM_ID = 21270;
	static final int[] SUPERIOR_ITEMS = {IMBUED_HEART_ITEM_ID, ETERNAL_GEM_ITEM_ID};

	/** The raids, in the Combat Summary's order (raids.tsv). */
	static final List<Raid> RAIDS = raids(CatalogTsv.rows(PanelData.class, "raids.tsv", 9));
	static final int[] MEGARARE_ITEM_IDS = RAIDS.stream().mapToInt(Raid::getMegarare).toArray();

	// The Fractured Archive (Raids 4, 2026-10-20): its hiscore row before the catalog listed it, and its popup.
	static final String TFA_HISCORE = "The Fractured Archive"; // the live hiscore name
	/** The raid's popup once the running client's enum carries it; null before, and its row opens nothing. */
	@Nullable
	static final HiscoreSkill TFA = skillNamed("THE_FRACTURED_ARCHIVE"); // RuneLite's enum name (HiscoreSkill, 1.13.6)

	/**
	 * Whether a hiscore row is The Fractured Archive's: the guess, or the name with or without "The". Nothing more,
	 * so a mode or a boss of its own whose row also names the archive can never stand in for its completions.
	 */
	static boolean isTfaRow(String name)
	{
		return name.equalsIgnoreCase(TFA_HISCORE)
			|| name.trim().toLowerCase(Locale.ROOT).replaceFirst("^the ", "").equals("fractured archive");
	}

	/**
	 * True once the panel's boss list carries the raid (its tile, popup and boss count), which the launch-day
	 * update adds. Before that its row is the Combat Summary's alone: it opens nothing and counts beside the list.
	 */
	static boolean tfaListed()
	{
		return listed(TFA) != null;
	}

	/** The boss when the panel's list carries it (its tile, popup and count), else null. */
	@Nullable
	static HiscoreSkill listed(@Nullable HiscoreSkill boss)
	{
		return boss != null && Arrays.asList(BOSSES).contains(boss) ? boss : null;
	}

	/** The card a boss cell opens: its own, or for a raid's hard mode, the raid's. */
	static HiscoreSkill card(HiscoreSkill boss)
	{
		for (Raid raid : RAIDS)
		{
			if (boss != null && boss == raid.getHard())
			{
				return raid.getSkill();
			}
		}
		return boss;
	}

	/** The raid a boss is, null for anything else. */
	@Nullable
	static Raid raid(HiscoreSkill boss)
	{
		return RAIDS.stream().filter(raid -> boss != null && boss == raid.getSkill()).findFirst().orElse(null);
	}

	/** raids.tsv's rows as Raids. */
	static List<Raid> raids(List<String[]> table)
	{
		List<Raid> raids = new ArrayList<>();
		for (String[] f : table)
		{
			raids.add(new Raid(f[0], skillNamed(f[1]), f[2], f[3].equals("-") ? null : skillNamed(f[3]), f[4],
				f[5].equals("wait"), f[6], Integer.parseInt(f[7]), f[8]));
		}
		return Collections.unmodifiableList(raids);
	}

	static final String[] SUPERIOR_ITEM_NAMES = {"Imbued heart", "Eternal gem"};

	// Clog-summary trophy items, first on the Rare shelf when obtained. An unobtained
	// special never renders; the shelf holds trophies, not a checklist.
	static final int STALE_BAGUETTE_ITEM_ID = 20590;
	static final int HELMET_OF_THE_MOON_ITEM_ID = 30111;
	static final int[] SPECIAL_ITEM_IDS = {STALE_BAGUETTE_ITEM_ID, HELMET_OF_THE_MOON_ITEM_ID};

	// Prestige capes shown beside the player summary account line.
	static final int MAX_CAPE_ITEM_ID = 13280;
	static final int INFERNAL_CAPE_ITEM_ID = 21295;
	static final int INFERNAL_MAX_CAPE_ITEM_ID = 21284;
	/** Bundled panel-catalog rows of this kind; the parity test pins contents. */
	private static List<String> catalogValues(String kind)
	{
		return CatalogTsv.values(PanelData.class, "panel-catalog.tsv", kind);
	}

	private static HiscoreSkill[] bosses()
	{
		// A name the running client's enum does not carry yet is skipped, not
		// fatal: one missing boss beats a dead panel. The parity test still
		// compiles every expected constant, so drift fails the build.
		List<HiscoreSkill> bosses = new ArrayList<>();
		for (String name : catalogValues("boss"))
		{
			HiscoreSkill boss = skillNamed(name);
			if (boss != null)
			{
				bosses.add(boss);
			}
		}
		return bosses.toArray(new HiscoreSkill[0]);
	}

	/** The running client's enum entry by name, or null when this client build doesn't carry it yet. */
	@Nullable
	static HiscoreSkill skillNamed(String name)
	{
		try
		{
			return HiscoreSkill.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			// Not in this client build: a later RuneLite renamed or dropped it, and its one row goes, not the panel.
			return null;
		}
	}

	/** clue-rare-rows.tsv's rows as RareRows. */
	static List<RareRow> rareRows(List<String[]> table)
	{
		List<RareRow> rows = new ArrayList<>();
		for (String[] f : table)
		{
			// A tier this client build doesn't carry skips its row, as an unknown boss does: a row short beats a dead panel.
			HiscoreSkill tier = f[3].equals("-") ? null : skillNamed(f[3]);
			if (tier != null || f[3].equals("-"))
			{
				rows.add(new RareRow(f[0], f[1], f[2], tier, f[4], f[5].equals("-") ? null : f[5], itemIds(f[6]),
					Integer.parseInt(f[7])));
			}
		}
		return Collections.unmodifiableList(rows);
	}

	private static int[] itemIds(String kind)
	{
		List<String> values = catalogValues(kind);
		if (values.isEmpty())
		{
			return new int[0];
		}
		String[] fields = values.get(0).split(",");
		int[] itemIds = new int[fields.length];
		for (int i = 0; i < itemIds.length; i++)
		{
			itemIds[i] = Integer.parseInt(fields[i].trim());
		}
		return itemIds;
	}

}
