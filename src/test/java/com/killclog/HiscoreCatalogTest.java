package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the bundled hiscore catalog: the names Kill Clog reads, each listed
 * once. Rows are matched by name, so only membership is checked, never order.
 *
 * These goldens are drift alarms, not invariants: a deliberate catalog edit
 * updates the golden alongside it in the same commit.
 */
public class HiscoreCatalogTest
{
	private static final String[] ACTIVITIES = TestViews.field(HiscoreService.class, "ACTIVITY_NAMES");

	@Test
	public void skillsMatchGoldenCatalog()
	{
		assertCatalog(new String[]{
			"attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic",
			"cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting",
			"smithing", "mining", "herblore", "agility", "thieving", "slayer", "farming",
			"runecraft", "hunter", "construction", "sailing"
		}, HiscoreService.skillNames());
	}

	@Test
	public void activitiesMatchGoldenCatalog()
	{
		assertCatalog(new String[]{
			"Grid Points", "League Points", "Deadman Points", "Bounty Hunter - Hunter",
			"Bounty Hunter - Rogue", "Bounty Hunter (Legacy) - Hunter",
			"Bounty Hunter (Legacy) - Rogue", "Clue Scrolls (all)", "Clue Scrolls (beginner)",
			"Clue Scrolls (easy)", "Clue Scrolls (medium)", "Clue Scrolls (hard)",
			"Clue Scrolls (elite)", "Clue Scrolls (master)", "LMS - Rank", "PvP Arena - Rank",
			"Soul Wars Zeal", "Rifts closed", "Colosseum Glory", "Collections Logged"
		}, ACTIVITIES);
	}

	@Test
	public void bossesMatchGoldenCatalog()
	{
		assertCatalog(new String[]{
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

	@Test
	public void noBossSharesAnActivitysName()
	{
		// An activity row is read as an activity first, so a boss under the same name would never get its KC.
		Set<String> bosses = new HashSet<>(Arrays.asList(HiscoreService.bossNames()));
		assertTrue(Collections.disjoint(bosses, Arrays.asList(ACTIVITIES)));
	}

	private static void assertCatalog(String[] golden, String[] catalog)
	{
		Set<String> names = new HashSet<>(Arrays.asList(catalog));
		assertEquals("a name is listed twice", catalog.length, names.size());
		assertEquals(new HashSet<>(Arrays.asList(golden)), names);
	}
}
