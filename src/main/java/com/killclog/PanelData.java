package com.killclog;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Static reference data for the panel grid — boss list, clue tiers, item IDs, category mappings.
 * Pure constants, no state.
 */
final class PanelData
{
	private PanelData()
	{
	}

	static final int MAX_TOTAL_LEVEL = 2376;

	// Boss display order matching vanilla RuneLite hiscores.
	// Must contain the same bosses as BOSS_NAMES in HiscoreService (order differs — this is display order, not CSV order).
	// New boss? Add HiscoreSkill.BOSS_NAME here alphabetically once RuneLite adds the enum.
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

	// Native clog rare categories — hardcoded item IDs (Temple doesn't have these)
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
		// All 3rd age (23)
		10350, 10348, 10346, 23242, 10352,
		10334, 10330, 10332, 10336,
		10342, 10338, 10340, 10344,
		12426, 12422, 12437, 12424,
		23336, 23339, 23345, 23342,
		20014, 20011,
		// All gilded (20)
		3486, 3481, 3483, 3485, 3488,
		20146, 20149, 20152, 20155, 20158, 20161,
		12389, 12391, 23258, 23261, 23264, 23267,
		23276, 23279, 23282,
		// Bucket helm (g), Ring of coins
		20059, 20017
	};

	// Clue tier -> Temple clog category
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

	// Clog tier trophy item IDs — one per tier (bronze through gilded)
	static final int[] CLOG_TIER_ITEM_IDS = {
		30579, 30581, 30583, 30585, 30587, 30589, 30591, 30593, 30595
	};

	static final HiscoreSkill[] PVP_ACTIVITIES = {
		HiscoreSkill.LAST_MAN_STANDING,
		HiscoreSkill.SOUL_WARS_ZEAL,
		HiscoreSkill.PVP_ARENA_RANK,
		HiscoreSkill.BOUNTY_HUNTER_HUNTER,
		HiscoreSkill.BOUNTY_HUNTER_ROGUE,
	};
}
