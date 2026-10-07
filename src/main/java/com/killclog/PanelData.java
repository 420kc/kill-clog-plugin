package com.killclog;

import java.util.ArrayList;
import java.util.Arrays;
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

	// HiscoreSkill.getName() -> boss name used in hiscore CSV data.
	// Only entries where the two differ are needed.
	static final Map<String, String> NAME_OVERRIDES = new LinkedHashMap<>();
	static
	{
		NAME_OVERRIDES.put("Calvar'ion", "Cal'varion");
	}

	// Hiscore display name -> OSRS Wiki page where the page title differs.
	private static final Map<HiscoreSkill, String> BOSS_WIKI_PAGES = new LinkedHashMap<>();
	static
	{
		BOSS_WIKI_PAGES.put(HiscoreSkill.BARROWS_CHESTS, "Barrows");
		// MAD_ANGEL -> "The Mad Angel" rides the same 1.12.34 fold-in as BOSSES.
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

	// Native clog rare categories. Public provider APIs do not provide these.
	static final String RARE_HARD = "hard_rare";
	static final String RARE_ELITE = "elite_rare";
	static final String RARE_MASTER = "master_rare";

	static final int[] HARD_RARE_ITEMS = itemIds("hard_rare_items");

	static final int[] ELITE_RARE_ITEMS = itemIds("elite_rare_items");

	static final int[] MASTER_RARE_ITEMS = itemIds("master_rare_items");

	// The rare collections in Clue Summary order: the two clue-wide sets, then the tier rares.
	static final String[] RARE_KEYS = {CLOG_THIRD_AGE, CLOG_GILDED, RARE_HARD, RARE_ELITE, RARE_MASTER};
	static final String[] RARE_NAMES = {
		"3rd Age", "Gilded", "Hard Treasure (Rare)", "Elite Treasure (Rare)", "Master Treasure (Rare)",
	};
	// Each rare row's wiki page: the equipment set, or for a tier's rares their section of the Collection log.
	static final String[] RARE_WIKI_PAGES = {
		"3rd Age equipment", "Gilded equipment", CLOG_WIKI_PAGE + "#Hard Treasure Trail Rewards (Rare)",
		CLOG_WIKI_PAGE + "#Elite Treasure Trail Rewards (Rare)", CLOG_WIKI_PAGE + "#Master Treasure Trail Rewards (Rare)",
	};
	// The game's Collection Log page each rare row stands for, where one does: its page opens the row's card.
	static final String[] RARE_PAGES = {
		null, null, "hard_treasure_trails_rare", "elite_treasure_trails_rare", "master_treasure_trails_rare",
	};
	static final int[][] RARE_ITEMS = {
		THIRD_AGE_ITEMS, GILDED_ITEMS, HARD_RARE_ITEMS, ELITE_RARE_ITEMS, MASTER_RARE_ITEMS,
	};
	static final int[] RARE_ICON_ITEM_IDS = {THIRD_AGE_ITEM_ID, GILDED_ITEM_ID, 20544, 20543, 19836};

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

	// Clog tier trophy item IDs, bronze through gilded.
	static final int[] CLOG_TIER_ITEM_IDS = itemIds("clog_tier_items");

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

	static final int[] MEGARARE_ITEM_IDS = {TWISTED_BOW_ITEM_ID, SCYTHE_ITEM_ID, SHADOW_ITEM_ID};

	// Hover names for the fixed pvm-summary sprites. Static because the pvm
	// summary renders these rows with or without a synced clog result; display
	// names use their full display form; TitleTooltip ellipsizes them when a
	// narrow summary cannot fit the whole hover title.
	static final String[] MEGARARE_ITEM_NAMES = {
		"Twisted Bow", "Scythe of Vitur", "Tumeken's Shadow",
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
			try
			{
				bosses.add(HiscoreSkill.valueOf(name));
			}
			catch (IllegalArgumentException e)
			{
				// Skip: enum not present in this client build.
			}
		}
		return bosses.toArray(new HiscoreSkill[0]);
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
