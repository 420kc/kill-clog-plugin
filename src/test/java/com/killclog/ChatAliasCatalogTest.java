package com.killclog;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Pins the chat alias catalogs to the exact tables the plugin shipped through
 * 2.3.0, when they lived in KillClogChatCommand itself. The golden copies below
 * are verbatim; any drift in the bundled TSVs or their loaders fails here.
 */
public class ChatAliasCatalogTest
{
	@Test
	public void bossAliasesMatchGoldenTable()
	{
		Map<String, String> golden = new HashMap<>();
		String[] canon = {
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
			"Venenatis", "Vet'ion", "Vorkath", "Wintertodt", "Yama", "Zalcano", "Zulrah",
		};
		for (String c : canon)
		{
			golden.put(KillClogChatCommand.normalize(c), c);
		}
		golden.put("vork", "Vorkath");
		golden.put("bandos", "General Graardor");
		golden.put("graardor", "General Graardor");
		golden.put("sara", "Commander Zilyana");
		golden.put("zilyana", "Commander Zilyana");
		golden.put("zammy", "K'ril Tsutsaroth");
		golden.put("kril", "K'ril Tsutsaroth");
		golden.put("arma", "Kree'Arra");
		golden.put("kree", "Kree'Arra");
		golden.put("kreearra", "Kree'Arra");
		golden.put("corp", "Corporeal Beast");
		golden.put("kbd", "King Black Dragon");
		golden.put("kq", "Kalphite Queen");
		golden.put("mole", "Giant Mole");
		golden.put("thermy", "Thermonuclear Smoke Devil");
		golden.put("smoke devil", "Thermonuclear Smoke Devil");
		golden.put("cerb", "Cerberus");
		golden.put("vard", "Vardorvis");
		golden.put("duke", "Duke Sucellus");
		golden.put("whisp", "The Whisperer");
		golden.put("whisperer", "The Whisperer");
		golden.put("levi", "The Leviathan");
		golden.put("leviathan", "The Leviathan");
		golden.put("muspah", "Phantom Muspah");
		golden.put("phosani", "Phosani's Nightmare");
		golden.put("doom", "Doom of Mokhaiotl");
		golden.put("mokhaiotl", "Doom of Mokhaiotl");
		golden.put("amox", "Amoxliatl");
		golden.put("arax", "Araxxor");
		golden.put("huey", "The Hueycoatl");
		golden.put("hueycoatl", "The Hueycoatl");
		golden.put("titans", "The Royal Titans");
		golden.put("royal titans", "The Royal Titans");
		golden.put("gauntlet", "The Gauntlet");
		golden.put("cg", "The Corrupted Gauntlet");
		golden.put("corrupted gauntlet", "The Corrupted Gauntlet");
		golden.put("jad", "TzTok-Jad");
		golden.put("zuk", "TzKal-Zuk");
		golden.put("inferno", "TzKal-Zuk");
		golden.put("sire", "Abyssal Sire");
		golden.put("hydra", "Alchemical Hydra");
		golden.put("scur", "Scurrius");
		golden.put("scurrius", "Scurrius");
		golden.put("sara mage", "Sarachnis");
		golden.put("sarachnis", "Sarachnis");
		golden.put("cox", "Chambers of Xeric");
		golden.put("raids", "Chambers of Xeric");
		golden.put("cm", "Chambers of Xeric: Challenge Mode");
		golden.put("toa", "Tombs of Amascut");
		golden.put("tombs", "Tombs of Amascut");
		golden.put("expert", "Tombs of Amascut: Expert Mode");
		golden.put("toa expert", "Tombs of Amascut: Expert Mode");
		golden.put("tob", "Theatre of Blood");
		golden.put("hmt", "Theatre of Blood: Hard Mode");
		golden.put("prime", "Dagannoth Prime");
		golden.put("rex", "Dagannoth Rex");
		golden.put("supreme", "Dagannoth Supreme");
		golden.put("vetion", "Vet'ion");
		golden.put("calvarion", "Cal'varion");
		golden.put("chaos ele", "Chaos Elemental");
		golden.put("chaos fan", "Chaos Fanatic");
		golden.put("crazy arch", "Crazy Archaeologist");
		golden.put("deranged arch", "Deranged Archaeologist");
		golden.put("grotesque", "Grotesque Guardians");
		golden.put("gg", "Grotesque Guardians");
		golden.put("shellbane", "Shellbane Gryphon");
		golden.put("sol", "Sol Heredit");
		golden.put("colosseum", "Sol Heredit");
		golden.put("barrows", "Barrows Chests");
		golden.put("lunar", "Lunar Chests");
		golden.put("moons", "Lunar Chests");
		golden.put("mad angel", "Mad Angel");
		golden.put("angel", "Mad Angel");
		golden.put("maggot", "Maggot King");
		golden.put("mk", "Maggot King");
		golden.put("perilous", "Lunar Chests");
		golden.put("perilous moons", "Lunar Chests");
		golden.put("nightmare", "Nightmare");
		golden.put("nm", "Nightmare");
		golden.put("pnm", "Phosani's Nightmare");
		golden.put("vorky", "Vorkath");
		golden.put("bryo", "Bryophyta");
		golden.put("hesp", "Hespori");
		golden.put("wt", "Wintertodt");
		golden.put("winter", "Wintertodt");
		golden.put("zal", "Zalcano");
		golden.put("tempo", "Tempoross");
		golden.put("krak", "Kraken");
		golden.put("abby", "Abyssal Sire");
		golden.put("ele", "Chaos Elemental");
		golden.put("fan", "Chaos Fanatic");
		golden.put("ven", "Venenatis");
		golden.put("venom", "Venenatis");
		golden.put("vet", "Vet'ion");
		golden.put("calv", "Cal'varion");
		golden.put("calli", "Callisto");
		golden.put("art", "Artio");
		golden.put("spin", "Spindel");

		assertEquals(golden, KillClogChatCommand.aliases());
	}

	@Test
	public void clueAliasesMatchGoldenTable()
	{
		int golden = 0;
		golden += assertClueTier("Beginner Treasure Trails", HiscoreSkill.CLUE_SCROLL_BEGINNER,
			"beginner treasure trails", "begs", "beg clues", "beginners", "beginner clues",
			"beginner clue", "clues beg", "clues beginner", "clue beg", "clue beginner");
		golden += assertClueTier("Easy Treasure Trails", HiscoreSkill.CLUE_SCROLL_EASY,
			"easy treasure trails", "easy clues", "easy clue", "easies", "clues easy",
			"clue easy");
		golden += assertClueTier("Medium Treasure Trails", HiscoreSkill.CLUE_SCROLL_MEDIUM,
			"medium treasure trails", "meds", "med", "mediums", "medium clues", "medium clue",
			"clues med", "clues medium", "clue med", "clue medium");
		golden += assertClueTier("Hard Treasure Trails", HiscoreSkill.CLUE_SCROLL_HARD,
			"hard treasure trails", "hards", "hard clues", "hard clue", "clue hard",
			"clues hard");
		golden += assertClueTier("Elite Treasure Trails", HiscoreSkill.CLUE_SCROLL_ELITE,
			"elite treasure trails", "elites", "elite clues", "elite clue", "clue elite",
			"clues elite");
		golden += assertClueTier("Master Treasure Trails", HiscoreSkill.CLUE_SCROLL_MASTER,
			"master treasure trails", "masters", "master clues", "master clue", "clue master",
			"clues master");

		assertEquals(golden, KillClogChatCommand.clueAliasCount());
	}

	private static int assertClueTier(String label, HiscoreSkill tier, String... aliases)
	{
		String categoryKey = PanelData.CLUE_CATEGORIES.get(tier);
		for (String alias : aliases)
		{
			assertEquals(alias, categoryKey, KillClogChatCommand.resolveClueCategory(alias));
			assertEquals(alias, label, KillClogChatCommand.clueLabel(alias));
		}
		return aliases.length;
	}
}
