package com.killclog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
	static final int THIRD_AGE_ITEM_ID = 10348;
	static final int GILDED_ITEM_ID = 3481;
	static final int THIRD_AGE_RING_ITEM_ID = 23185;
	static final int[] THIRD_AGE_ITEMS = itemIds("third_age_items");
	static final int[] GILDED_ITEMS = itemIds("gilded_items");
	// The log items the game counts past 250, up to 65,535; every other item stops at 250.
	static final int[] COUNTS_PAST_250_ITEMS = itemIds("counts_past_250_items");

	// Native clog rare categories. Public provider APIs do not provide these.
	static final String RARE_HARD = "hard_rare";
	static final String RARE_ELITE = "elite_rare";
	static final String RARE_MASTER = "master_rare";

	static final int[] HARD_RARE_ITEMS = itemIds("hard_rare_items");

	static final int[] ELITE_RARE_ITEMS = itemIds("elite_rare_items");

	static final int[] MASTER_RARE_ITEMS = itemIds("master_rare_items");

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

	// The raids and their hard modes. A hard mode shares its raid's log, so it reads on the raid's own card:
	// its cell opens that card, where its KC and PB are a second row under the name the Combat Summary pairs it by.
	static final HiscoreSkill[] RAIDS = {HiscoreSkill.CHAMBERS_OF_XERIC, HiscoreSkill.THEATRE_OF_BLOOD,
		HiscoreSkill.TOMBS_OF_AMASCUT};
	static final HiscoreSkill[] RAID_HARD_MODES = {HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE,
		HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE, HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT};
	static final String[] RAID_HARD_LABELS = {"CM", "HM", "Expert"};

	// The Fractured Archive (Raids 4, 2026-10-20) is the fourth raid row, with no hard mode.
	static final String TFA_HISCORE = "The Fractured Archive"; // the live hiscore name
	static final String TFA_CATEGORY = "the_fractured_archive"; // the game's log page key
	/** The raid's popup once the running client's enum carries it; null before, and its row opens nothing. */
	@Nullable
	static final HiscoreSkill TFA = skillNamed("FRACTURED_ARCHIVE"); // RuneLite's enum name (HiscoreSkill, 1.13.3)

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
		int i = Arrays.asList(RAID_HARD_MODES).indexOf(boss);
		return i >= 0 ? RAIDS[i] : boss;
	}

	/** A raid's hard mode, null for anything else. */
	@Nullable
	static HiscoreSkill hardMode(HiscoreSkill raid)
	{
		int i = Arrays.asList(RAIDS).indexOf(raid);
		return i >= 0 ? RAID_HARD_MODES[i] : null;
	}

	/** The name a raid's hard mode goes by beside the raid: CM, HM or Expert. */
	static String hardLabel(HiscoreSkill raid)
	{
		return RAID_HARD_LABELS[Arrays.asList(RAIDS).indexOf(raid)];
	}

	// Each raid's log and the megarare drop that headlines the Mega Rares row.
	static final String COX_CATEGORY = "chambers_of_xeric";
	static final int TWISTED_BOW_ITEM_ID = 20997;
	static final String TOB_CATEGORY = "theatre_of_blood";
	static final int SCYTHE_ITEM_ID = 22486;
	static final String TOA_CATEGORY = "tombs_of_amascut";
	static final int SHADOW_ITEM_ID = 27277;

	static final int OBLIGATOR_ITEM_ID = 34717; // uncharged, the raid's log slot
	static final int[] MEGARARE_ITEM_IDS = {TWISTED_BOW_ITEM_ID, SCYTHE_ITEM_ID, SHADOW_ITEM_ID, OBLIGATOR_ITEM_ID};

	// Hover names for the fixed pvm-summary sprites. Static because the pvm
	// summary renders these rows with or without a synced clog result; display
	// names use their full display form; TitleTooltip ellipsizes them when a
	// narrow summary cannot fit the whole hover title.
	static final String[] MEGARARE_ITEM_NAMES = {
		"Twisted Bow", "Scythe of Vitur", "Tumeken's Shadow", "Obligator",
	};
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
	private static HiscoreSkill skillNamed(String name)
	{
		try
		{
			return HiscoreSkill.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
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
