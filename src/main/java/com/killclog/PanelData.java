package com.killclog;

import java.util.LinkedHashMap;
import java.util.Map;
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
	// order. Same bosses as HiscoreService.bossNames(), but the ORDER can
	// diverge from Jagex's CSV rows (Maggot King renders before Mimic, parses
	// after it) - name-keyed lookups bridge the two lists.
	// New boss? Add it here in enum order once RuneLite adds it.
	// See BOSS_NAMES comment in HiscoreService for the full update playbook.
	static final HiscoreSkill[] BOSSES = {
		HiscoreSkill.ABYSSAL_SIRE,
		HiscoreSkill.ALCHEMICAL_HYDRA,
		HiscoreSkill.AMOXLIATL,
		HiscoreSkill.ARAXXOR,
		HiscoreSkill.ARTIO,
		HiscoreSkill.BARROWS_CHESTS,
		HiscoreSkill.BRUTUS,
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
		HiscoreSkill.KALPHITE_QUEEN,
		HiscoreSkill.KING_BLACK_DRAGON,
		HiscoreSkill.KRAKEN,
		HiscoreSkill.KREEARRA,
		HiscoreSkill.KRIL_TSUTSAROTH,
		HiscoreSkill.LUNAR_CHESTS,
		HiscoreSkill.MAGGOT_KING,
		HiscoreSkill.MIMIC,
		HiscoreSkill.NEX,
		HiscoreSkill.NIGHTMARE,
		HiscoreSkill.PHOSANIS_NIGHTMARE,
		HiscoreSkill.OBOR,
		HiscoreSkill.PHANTOM_MUSPAH,
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
		HiscoreSkill.THE_HUEYCOATL,
		HiscoreSkill.THE_LEVIATHAN,
		HiscoreSkill.THE_ROYAL_TITANS,
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
		BOSS_WIKI_PAGES.put(HiscoreSkill.MIMIC, "The Mimic");
		BOSS_WIKI_PAGES.put(HiscoreSkill.NIGHTMARE, "The Nightmare");
	}

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
	static final int[] CLUE_TIER_ITEM_IDS = {23182, 2677, 2801, 2722, 12073, 19835};
	static final int THIRD_AGE_ITEM_ID = 10348;
	static final int GILDED_ITEM_ID = 3481;
	static final int THIRD_AGE_RING_ITEM_ID = 23185;
	static final int[] THIRD_AGE_ITEMS = {
		10350, 10348, 10346, 23242, 10352,
		10334, 10330, 10332, 10336,
		10342, 10338, 10340, 10344,
		12426, 12422, 12437, 12424,
		23336, 23339, 23345, 23342,
		20014, 20011
	};
	static final int[] GILDED_ITEMS = {
		3486, 3481, 3483, 3485, 3488,
		20146, 20149, 20152, 20155, 20158, 20161,
		12389, 12391, 23258, 23261, 23264, 23267,
		23276, 23279, 23282
	};

	// Native clog rare categories. Public provider APIs do not provide these.
	static final String RARE_HARD = "hard_rare";
	static final String RARE_ELITE = "elite_rare";
	static final String RARE_MASTER = "master_rare";

	static final int[] HARD_RARE_ITEMS = {
		// 3rd age melee + range + mage + amulet (13)
		10350, 10348, 10346, 23242, 10352,
		10334, 10330, 10332, 10336,
		10342, 10338, 10340, 10344,
		// Gilded melee (11)
		3486, 3481, 3483, 3485, 3488,
		20146, 20149, 20152, 20155, 20158, 20161
	};

	static final int[] ELITE_RARE_ITEMS = {
		// 3rd age melee + range + mage + amulet + weapons + cloak (17)
		10350, 10348, 10346, 23242, 10352,
		10334, 10330, 10332, 10336,
		10342, 10338, 10340, 10344,
		12426, 12422, 12437, 12424,
		// All gilded (20)
		3486, 3481, 3483, 3485, 3488,
		20146, 20149, 20152, 20155, 20158, 20161,
		12389, 12391, 23258, 23261, 23264, 23267,
		23276, 23279, 23282,
		// Lava dragon mask, Ring of nature
		12371, 20005
	};

	static final int[] MASTER_RARE_ITEMS = {
		// All 3rd age (24)
		10350, 10348, 10346, 23242, 10352,
		10334, 10330, 10332, 10336,
		10342, 10338, 10340, 10344,
		12426, 12422, 12437, 12424,
		23336, 23339, 23345, 23342,
		20014, 20011, THIRD_AGE_RING_ITEM_ID,
		// All gilded (20)
		3486, 3481, 3483, 3485, 3488,
		20146, 20149, 20152, 20155, 20158, 20161,
		12389, 12391, 23258, 23261, 23264, 23267,
		23276, 23279, 23282,
		// Bucket helm (g), Ring of coins
		20059, 20017
	};

	// Clue tier -> collection-log category key.
	static final Map<HiscoreSkill, String> CLUE_CATEGORIES = new LinkedHashMap<>();
	static
	{
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_BEGINNER, "beginner_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_EASY, "easy_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_MEDIUM, "medium_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_HARD, "hard_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_ELITE, "elite_treasure_trails");
		CLUE_CATEGORIES.put(HiscoreSkill.CLUE_SCROLL_MASTER, "master_treasure_trails");
	}

	// Clog tier trophy item IDs, bronze through gilded.
	static final int[] CLOG_TIER_ITEM_IDS = {
		30579, 30581, 30583, 30585, 30587, 30589, 30591, 30593, 30595
	};

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

	// Raids: hiscore rows (base + hard mode sum into one KC), clog category,
	// and the megarare drop that headlines the Mega Rares row.
	static final String COX_HISCORE = "Chambers of Xeric";
	static final String COX_HISCORE_HARD = "Chambers of Xeric: Challenge Mode";
	static final String COX_CATEGORY = "chambers_of_xeric";
	static final int TWISTED_BOW_ITEM_ID = 20997;

	static final String TOB_HISCORE = "Theatre of Blood";
	static final String TOB_HISCORE_HARD = "Theatre of Blood: Hard Mode";
	static final String TOB_CATEGORY = "theatre_of_blood";
	static final int SCYTHE_ITEM_ID = 22486;

	static final String TOA_HISCORE = "Tombs of Amascut";
	static final String TOA_HISCORE_HARD = "Tombs of Amascut: Expert Mode";
	static final String TOA_CATEGORY = "tombs_of_amascut";
	static final int SHADOW_ITEM_ID = 27277;

	static final int[] MEGARARE_ITEM_IDS = {TWISTED_BOW_ITEM_ID, SCYTHE_ITEM_ID, SHADOW_ITEM_ID};

	// Hover names for the fixed pvm-summary sprites. Static because the pvm
	// summary renders these rows with or without a synced clog result; display
	// names, kept short enough to sit on the narrow summary title line.
	static final String[] MEGARARE_ITEM_NAMES = {
		"Twisted Bow", "Scythe of Vitur", "Shadow",
	};
	static final String[] SUPERIOR_ITEM_NAMES = {"Imbued heart", "Eternal gem"};

	// Clog-summary trophy items: shown in the Special section only when
	// obtained. An unobtained special never renders; the section is a trophy
	// shelf, not a checklist.
	static final int STALE_BAGUETTE_ITEM_ID = 20590;
	static final int HELMET_OF_THE_MOON_ITEM_ID = 30111;
	static final int[] SPECIAL_ITEM_IDS = {STALE_BAGUETTE_ITEM_ID, HELMET_OF_THE_MOON_ITEM_ID};

	// Prestige capes shown beside the player summary account line.
	static final int MAX_CAPE_ITEM_ID = 13280;
	static final int INFERNAL_CAPE_ITEM_ID = 21295;
	static final int INFERNAL_MAX_CAPE_ITEM_ID = 21284;
}
