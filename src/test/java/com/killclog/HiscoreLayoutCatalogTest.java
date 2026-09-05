package com.killclog;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

/**
 * Pins the bundled hiscore CSV layout to the exact arrays the plugin shipped
 * with in 2.3.0, when they lived in HiscoreService itself. Order is part of
 * the contract: rows are positional against the Jagex feed.
 *
 * These goldens are drift alarms, not invariants: a deliberate catalog edit
 * updates the golden alongside it in the same commit.
 */
public class HiscoreLayoutCatalogTest
{
	@Test
	public void skillsMatchGoldenLayout()
	{
		assertArrayEquals(new String[]{
			"attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic",
			"cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting",
			"smithing", "mining", "herblore", "agility", "thieving", "slayer", "farming",
			"runecraft", "hunter", "construction", "sailing"
		}, HiscoreService.skillNames());
	}

	@Test
	public void activitiesMatchGoldenLayout()
	{
		assertArrayEquals(new String[]{
			"Grid Points", "League Points", "Deadman Points", "Bounty Hunter - Hunter",
			"Bounty Hunter - Rogue", "Bounty Hunter (Legacy) - Hunter",
			"Bounty Hunter (Legacy) - Rogue", "Clue Scrolls (all)", "Clue Scrolls (beginner)",
			"Clue Scrolls (easy)", "Clue Scrolls (medium)", "Clue Scrolls (hard)",
			"Clue Scrolls (elite)", "Clue Scrolls (master)", "LMS - Rank", "PvP Arena - Rank",
			"Soul Wars Zeal", "Rifts closed", "Colosseum Glory", "Collections Logged"
		}, HiscoreService.activityNames());
	}

	@Test
	public void bossesMatchGoldenLayout()
	{
		assertArrayEquals(new String[]{
			"Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Artio",
			"Barrows Chests", "Brutus", "Bryophyta", "Callisto", "Cal'varion", "Cerberus",
			"Chambers of Xeric", "Chambers of Xeric: Challenge Mode", "Chaos Elemental",
			"Chaos Fanatic", "Commander Zilyana", "Corporeal Beast", "Crazy Archaeologist",
			"Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme", "Deranged Archaeologist",
			"Doom of Mokhaiotl", "Duke Sucellus", "General Graardor", "Giant Mole",
			"Grotesque Guardians", "Hespori", "Kalphite Queen", "King Black Dragon", "Kraken",
			"Kree'Arra", "K'ril Tsutsaroth", "Lunar Chests", "Mad Angel", "Maggot King",
			"Mimic", "Nex", "Nightmare", "Phosani's Nightmare", "Obor", "Phantom Muspah",
			"Sarachnis", "Scorpia", "Scurrius", "Shellbane Gryphon", "Skotizo", "Sol Heredit",
			"Spindel", "Tempoross", "The Gauntlet", "The Corrupted Gauntlet", "The Hueycoatl",
			"The Leviathan", "The Royal Titans", "The Whisperer", "Theatre of Blood",
			"Theatre of Blood: Hard Mode", "Thermonuclear Smoke Devil", "Tombs of Amascut",
			"Tombs of Amascut: Expert Mode", "TzKal-Zuk", "TzTok-Jad", "Vardorvis",
			"Venenatis", "Vet'ion", "Vorkath", "Wintertodt", "Yama", "Zalcano", "Zulrah"
		}, HiscoreService.bossNames());
	}
}
