package com.killclog;

import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

/**
 * Pins the bundled panel catalog to the exact tables the plugin shipped with
 * in 2.3.0, when they lived in PanelData itself. The golden boss list keeps
 * compile-time checking of every HiscoreSkill reference on the test side.
 *
 * These goldens are drift alarms, not invariants: a deliberate catalog edit
 * updates the golden alongside it in the same commit.
 */
public class PanelCatalogTest
{
	@Test
	public void bossesMatchGoldenDisplayOrder()
	{
		assertArrayEquals(new HiscoreSkill[]{
			HiscoreSkill.ABYSSAL_SIRE, HiscoreSkill.ALCHEMICAL_HYDRA, HiscoreSkill.AMOXLIATL,
			HiscoreSkill.ARAXXOR, HiscoreSkill.ARTIO, HiscoreSkill.BARROWS_CHESTS,
			HiscoreSkill.BRUTUS, HiscoreSkill.BRYOPHYTA, HiscoreSkill.CALLISTO,
			HiscoreSkill.CALVARION, HiscoreSkill.CERBERUS, HiscoreSkill.CHAMBERS_OF_XERIC,
			HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE, HiscoreSkill.CHAOS_ELEMENTAL,
			HiscoreSkill.CHAOS_FANATIC, HiscoreSkill.COMMANDER_ZILYANA,
			HiscoreSkill.CORPOREAL_BEAST, HiscoreSkill.CRAZY_ARCHAEOLOGIST,
			HiscoreSkill.DAGANNOTH_PRIME, HiscoreSkill.DAGANNOTH_REX,
			HiscoreSkill.DAGANNOTH_SUPREME, HiscoreSkill.DERANGED_ARCHAEOLOGIST,
			HiscoreSkill.DOOM_OF_MOKHAIOTL, HiscoreSkill.DUKE_SUCELLUS,
			HiscoreSkill.GENERAL_GRAARDOR, HiscoreSkill.GIANT_MOLE,
			HiscoreSkill.GROTESQUE_GUARDIANS, HiscoreSkill.HESPORI, HiscoreSkill.KALPHITE_QUEEN,
			HiscoreSkill.KING_BLACK_DRAGON, HiscoreSkill.KRAKEN, HiscoreSkill.KREEARRA,
			HiscoreSkill.KRIL_TSUTSAROTH, HiscoreSkill.LUNAR_CHESTS, HiscoreSkill.MAD_ANGEL,
			HiscoreSkill.MAGGOT_KING, HiscoreSkill.MIMIC, HiscoreSkill.NEX, HiscoreSkill.NIGHTMARE,
			HiscoreSkill.PHOSANIS_NIGHTMARE, HiscoreSkill.OBOR, HiscoreSkill.PHANTOM_MUSPAH,
			HiscoreSkill.SARACHNIS, HiscoreSkill.SCORPIA, HiscoreSkill.SCURRIUS,
			HiscoreSkill.SHELLBANE_GRYPHON, HiscoreSkill.SKOTIZO, HiscoreSkill.SOL_HEREDIT,
			HiscoreSkill.SPINDEL, HiscoreSkill.TEMPOROSS, HiscoreSkill.THE_GAUNTLET,
			HiscoreSkill.THE_CORRUPTED_GAUNTLET, HiscoreSkill.THE_HUEYCOATL,
			HiscoreSkill.THE_LEVIATHAN, HiscoreSkill.THE_ROYAL_TITANS, HiscoreSkill.THE_WHISPERER,
			HiscoreSkill.THEATRE_OF_BLOOD, HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE,
			HiscoreSkill.THERMONUCLEAR_SMOKE_DEVIL, HiscoreSkill.TOMBS_OF_AMASCUT,
			HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT, HiscoreSkill.TZKAL_ZUK, HiscoreSkill.TZTOK_JAD,
			HiscoreSkill.VARDORVIS, HiscoreSkill.VENENATIS, HiscoreSkill.VETION,
			HiscoreSkill.VORKATH, HiscoreSkill.WINTERTODT, HiscoreSkill.YAMA, HiscoreSkill.ZALCANO,
			HiscoreSkill.ZULRAH
		}, PanelData.BOSSES);
	}

	@Test
	public void itemTablesMatchGoldenLists()
	{
		assertArrayEquals(new int[]{
			23182, 2677, 2801, 2722, 12073, 19835
		}, PanelData.CLUE_TIER_ITEM_IDS);
		assertArrayEquals(new int[]{
			10350, 10348, 10346, 23242, 10352, 10334, 10330, 10332, 10336, 10342, 10338, 10340,
			10344, 12426, 12422, 12437, 12424, 23336, 23339, 23345, 23342, 20014, 20011
		}, PanelData.THIRD_AGE_ITEMS);
		assertArrayEquals(new int[]{
			3486, 3481, 3483, 3485, 3488, 20146, 20149, 20152, 20155, 20158, 20161, 12389, 12391,
			23258, 23261, 23264, 23267, 23276, 23279, 23282
		}, PanelData.GILDED_ITEMS);
		assertArrayEquals(new int[]{
			10350, 10348, 10346, 23242, 10352, 10334, 10330, 10332, 10336, 10342, 10338, 10340,
			10344, 3486, 3481, 3483, 3485, 3488, 20146, 20149, 20152, 20155, 20158, 20161
		}, PanelData.HARD_RARE_ITEMS);
		assertArrayEquals(new int[]{
			10350, 10348, 10346, 23242, 10352, 10334, 10330, 10332, 10336, 10342, 10338, 10340,
			10344, 12426, 12422, 12437, 12424, 3486, 3481, 3483, 3485, 3488, 20146, 20149, 20152,
			20155, 20158, 20161, 12389, 12391, 23258, 23261, 23264, 23267, 23276, 23279, 23282,
			12371, 20005
		}, PanelData.ELITE_RARE_ITEMS);
		assertArrayEquals(new int[]{
			10350, 10348, 10346, 23242, 10352, 10334, 10330, 10332, 10336, 10342, 10338, 10340,
			10344, 12426, 12422, 12437, 12424, 23336, 23339, 23345, 23342, 20014, 20011,
			PanelData.THIRD_AGE_RING_ITEM_ID, 3486, 3481, 3483, 3485, 3488, 20146, 20149, 20152,
			20155, 20158, 20161, 12389, 12391, 23258, 23261, 23264, 23267, 23276, 23279, 23282,
			20059, 20017
		}, PanelData.MASTER_RARE_ITEMS);
		assertArrayEquals(new int[]{
			30579, 30581, 30583, 30585, 30587, 30589, 30591, 30593, 30595
		}, PanelData.CLOG_TIER_ITEM_IDS);
	}
}
