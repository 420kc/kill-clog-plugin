package com.killclog;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LiveClogSyncTest
{
	private static final int FANG = 33634;
	private static final int CLUE_ITEM = 23285;
	private static final int PET = 30000;
	private static final int DEVIL_ITEM = 30001;
	private static final int UNPAGED = 30002;
	private static final int GAUNTLET_ITEM = 30003;
	private static final int PAGE_ITEM = 30004;
	private static final int TWO_PAGES = 30005;
	private static final int OTHER_ITEM = 30006;

	@Test
	public void theGameCountersWaitForASettledWorld()
	{
		Client client = mock(Client.class);
		Player local = mock(Player.class);
		when(local.getName()).thenReturn("Tester");
		when(client.getLocalPlayer()).thenReturn(local);
		// Right after a hop the counters may still be the last world's.
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(900);
		when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX)).thenReturn(1600);
		ClogIndex index = mock(ClogIndex.class);
		when(index.ensureParsed(any(), any())).thenReturn(true);
		when(index.itemIdsForName(any())).thenReturn(List.of(20997));
		LocalClogCache cache = mock(LocalClogCache.class);
		when(cache.serving("Tester")).thenReturn(true);
		when(cache.hasDataFor("Tester")).thenReturn(true);
		LiveClogSync sync = new LiveClogSync();

		sync.handleUnlock("Twisted bow", 12, 1600, client, null, index, cache, mock(KillClogChatNotifier.class), name ->
		{
		}, false);
		verify(cache).updateTotalsUpward("Tester", 12, 1600);
		sync.handleUnlock("Twisted bow", 12, 1600, client, null, index, cache, mock(KillClogChatNotifier.class), name ->
		{
		}, true);
		verify(cache).updateTotalsUpward("Tester", 900, 1600);
	}

	// Dylan 11/14: "Your Maggot King kill count is: 603." at 8:56:57, the Elder venator fang's "New item added" at
	// 8:57:00, past the old five ticks, so the fang was saved with no kill count and !kc had nothing to say.
	@Test
	public void aBossDropSecondsAfterItsKillCarriesTheKill()
	{
		LocalClogCache cache = unlockAfter("Maggot King", 6, FANG);
		verify(cache).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(603), eq("Maggot King"));
	}

	// Maggot King's loot comes when its corpse is searched, as long after the kill as the player likes (up to 259 s in
	// his logs): an item from a boss's own page that the game credited to nobody takes the boss's latest kill, no clock.
	@Test
	public void aDropFromItsOwnPageTakesTheBossesLatestKillHoweverLongAfter()
	{
		verify(unlockAfter("Maggot King", 50_000, FANG)).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(603),
			eq("Maggot King"));
	}

	// Dylan 11/15: the game credits each loot to the NPC that gave it as you receive it (11/14's fang: credited to Maggot
	// King the second it unlocked); the item takes that NPC's latest kill, however long the loot waited.
	@Test
	public void lootTheGameCreditsTakesItsNpcsLatestKillHoweverLongItWaited()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Maggot King", 603), 1000);
		sync.rememberKill(new ClogUnlockParser.KillContext("Zulrah", 50), 1100);
		sync.credit("Maggot King", List.of("Elder venator fang"), 60_000);
		// An item no page names yet, so only the credit can give it Maggot King's kill, not Zulrah's after it.
		verify(unlock(sync, clientAt(60_000), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(603),
			eq("Maggot King"));
	}

	// A plain smoke devil drops the Occult necklace too, and it sits on Thermy's page: credited to the smoke devil, which
	// has no kill count, it takes none, Thermy's kill a moment before notwithstanding.
	@Test
	public void lootCreditedToAPlainMonsterTakesNoKillThoughItsPageIsABosses()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Thermonuclear smoke devil", 603), 1000);
		sync.credit("Smoke devil", List.of("Elder venator fang"), 1100);
		verify(unlock(sync, clientAt(1110), DEVIL_ITEM)).mergeObtainedItem(eq("Tester"), eq(DEVIL_ITEM), any(), any(),
			eq(0), isNull());
	}

	// Loot credited to an NPC named apart from its kill message that pb-names.tsv doesn't name lands with that kill.
	@Test
	public void lootCreditedWithAKillNamedApartTakesIt()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Grotesque Guardians", 603), 1000);
		sync.credit("Dawn", List.of("Elder venator fang"), 1002);
		verify(unlock(sync, clientAt(9000), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(603),
			eq("Grotesque Guardians"));
	}

	// 11/15 review: pb-names.tsv names the NPC an encounter's loot is credited to, so its kill comes however long the
	// loot waits: Dusk for the Grotesque Guardians (his loot tracker), the Royal Titan whose body the player loots.
	@Test
	public void anEncountersNpcNamedApartTakesItsKillHoweverLongTheLootWaits()
	{
		String[][] encounters = {
			{"Dusk", "Grotesque Guardians"}, {"Branda the Fire Queen", "Royal Titans"}, {"Eldric the Ice King", "The Royal Titans"}};
		for (String[] encounter : encounters)
		{
			LiveClogSync sync = new LiveClogSync();
			sync.rememberKill(new ClogUnlockParser.KillContext(encounter[1], 40), 1000);
			sync.rememberKill(new ClogUnlockParser.KillContext("Zulrah", 50), 1100);
			sync.credit(encounter[0], List.of("Elder venator fang"), 1300);
			verify(unlock(sync, clientAt(1300), UNPAGED), org.mockito.Mockito.description(encounter[0])).mergeObtainedItem(
				eq("Tester"), eq(UNPAGED), any(), any(), eq(40), eq(encounter[1]));
		}
	}

	// 11/15 review: the game credits an awakened boss's loot to its plain NPC (his Vardorvis and The Whisperer loot under
	// "Vardorvis (awakened)" kills) and a League's Echo boss's to "<boss> (Echo)" (his Dusk (Echo)): either is the boss's
	// latest kill, whichever variant it was, never an older plain kill.
	@Test
	public void anAwakenedOrEchoKillIsItsBossesLatest()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Vardorvis", 300), 1000);
		sync.rememberKill(new ClogUnlockParser.KillContext("Vardorvis (awakened)", 7), 5000);
		sync.credit("Vardorvis", List.of("Elder venator fang"), 5100);
		verify(unlock(sync, clientAt(5100), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(7),
			eq("Vardorvis (awakened)"));
		sync.rememberKill(new ClogUnlockParser.KillContext("Grotesque Guardians (Echo)", 4), 6000);
		sync.credit("Dusk (Echo)", List.of("Elder venator fang"), 6100);
		verify(unlock(sync, clientAt(6100), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(4),
			eq("Grotesque Guardians (Echo)"));
	}

	// 11/15 review: a skill's or a minigame's page counts laps and challenges, never what a shop's reward was bought at:
	// TzHaar's page holds TzHaar-Ket-Rak's challenges in whole words, Colossal Wyrm Agility its courses' laps. The same
	// count on a boss's page is its content.
	@Test
	public void onlyABossOrRaidPageTakesItsContentsLatestCount()
	{
		String[][] pages = {
			{"TzHaar", "TzHaar-Ket-Rak's First Challenge"}, {"Colossal Wyrm Agility", "Colossal Wyrm Agility Course (Basic)"}};
		for (String[] page : pages)
		{
			LiveClogSync sync = new LiveClogSync();
			sync.rememberKill(new ClogUnlockParser.KillContext(page[1], 120), 1000);
			verify(unlock(sync, clientAt(2000), OTHER_ITEM, page[0]), org.mockito.Mockito.description(page[0]))
				.mergeObtainedItem(eq("Tester"), eq(OTHER_ITEM), any(), any(), eq(0), isNull());
			verify(unlock(sync, clientAt(2000), PAGE_ITEM, page[0]), org.mockito.Mockito.description(page[0] + " as a boss's"))
				.mergeObtainedItem(eq("Tester"), eq(PAGE_ITEM), any(), any(), eq(120), eq(page[1]));
		}
	}

	// A page pb-names.tsv renames is its kill's: The Inferno's is TzKal-Zuk's, Fortis Colosseum's Sol Heredit's.
	@Test
	public void aPageRenamedInPbNamesTakesItsKill()
	{
		String[][] pages = {{"The Inferno", "TzKal-Zuk"}, {"Fortis Colosseum", "Sol Heredit"}};
		for (String[] page : pages)
		{
			LiveClogSync sync = new LiveClogSync();
			sync.rememberKill(new ClogUnlockParser.KillContext(page[1], 3), 1000);
			verify(unlock(sync, clientAt(3000), PAGE_ITEM, page[0]), org.mockito.Mockito.description(page[0]))
				.mergeObtainedItem(eq("Tester"), eq(PAGE_ITEM), any(), any(), eq(3), eq(page[1]));
		}
	}

	// A page's name and its kill message's can differ by a leading "the": The Gauntlet's page, "Your Gauntlet completion
	// count".
	@Test
	public void aPageAndItsKillMatchWithOrWithoutTheir()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Gauntlet", 12), 1000);
		verify(unlock(sync, clientAt(5000), GAUNTLET_ITEM)).mergeObtainedItem(eq("Tester"), eq(GAUNTLET_ITEM), any(),
			any(), eq(12), eq("Gauntlet"));
	}

	// 11/15 review: a raid's hard mode, the Corrupted Gauntlet and Phosani's Nightmare count under their own names on the
	// page of their plain mode; an uncredited reward takes the latest kill of the page's content, never an older mode's.
	@Test
	public void anUncreditedRewardTakesTheLatestKillOfItsPagesContentAnyMode()
	{
		String[][] modes = {
			{"Tombs of Amascut", "Tombs of Amascut: Expert Mode", "Tombs of Amascut"},
			{"Chambers of Xeric", "Chambers of Xeric Challenge Mode", "Chambers of Xeric"},
			{"Gauntlet", "Corrupted Gauntlet", "The Gauntlet"},
			{"Nightmare", "Phosani's Nightmare", "The Nightmare"},
			{"Artio", "Callisto", "Callisto and Artio"}};
		for (String[] mode : modes)
		{
			for (int after : new int[]{2, 60})
			{
				LiveClogSync sync = new LiveClogSync();
				sync.rememberKill(new ClogUnlockParser.KillContext(mode[0], 12), 1000);
				sync.rememberKill(new ClogUnlockParser.KillContext(mode[1], 87), 2000);
				verify(unlock(sync, clientAt(2000 + after), PAGE_ITEM, mode[2]), org.mockito.Mockito.description(
					mode[1] + ", " + after + " ticks")).mergeObtainedItem(eq("Tester"), eq(PAGE_ITEM), any(), any(), eq(87),
					eq(mode[1]));
			}
		}
	}

	// A page's name inside another word is no match: "Nex" is not in "Annex".
	@Test
	public void aPageMatchesItsContentInWholeWordsOnly()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Annex", 5), 1000);
		verify(unlock(sync, clientAt(5000), PAGE_ITEM, "Nex")).mergeObtainedItem(eq("Tester"), eq(PAGE_ITEM), any(), any(),
			eq(0), isNull());
	}

	// Of an item's pages, the one with the latest kill gives it.
	@Test
	public void anItemOnTwoPagesTakesTheLaterKill()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Maggot King", 603), 2000);
		sync.rememberKill(new ClogUnlockParser.KillContext("Thermonuclear smoke devil", 250), 1000);
		verify(unlock(sync, clientAt(9000), TWO_PAGES)).mergeObtainedItem(eq("Tester"), eq(TWO_PAGES), any(), any(), eq(603),
			eq("Maggot King"));
	}

	// A credit is the one loot's: used by its unlock, it never answers another unlock of the name.
	@Test
	public void aCreditIsUsedOnce()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Maggot King", 603), 1000);
		sync.credit("Maggot King", List.of("Elder venator fang"), 1000);
		verify(unlock(sync, clientAt(1000), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(603),
			eq("Maggot King"));
		verify(unlock(sync, clientAt(50_000), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(0),
			isNull());
	}

	// An NPC and its kill message can differ by a leading "the" (The Nightmare's loot, "Your Nightmare kill count").
	@Test
	public void aCreditMatchesItsKillWithOrWithoutThe()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Nightmare", 41), 1000);
		sync.credit("The Nightmare", List.of("Elder venator fang"), 9000);
		verify(unlock(sync, clientAt(9000), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(41),
			eq("Nightmare"));
	}

	// A credit's own NPC's kill wins over another kill it landed beside.
	@Test
	public void aCreditTakesItsOwnNpcsKillOverTheOneItLandedWith()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Maggot King", 603), 1000);
		sync.rememberKill(new ClogUnlockParser.KillContext("Zulrah", 50), 1003);
		sync.credit("Maggot King", List.of("Elder venator fang"), 1004);
		verify(unlock(sync, clientAt(1004), UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(), eq(603),
			eq("Maggot King"));
	}

	// An unlock credited to nobody and off any boss's page with a kill (a casket after a kill) takes only the count
	// message it arrived with: within five ticks, as a chest's reward does; past them, bare.
	@Test
	public void anItemFromAnotherPageTakesOnlyTheKillItArrivedWith()
	{
		verify(unlockAfter("Maggot King", 6, CLUE_ITEM)).mergeObtainedItem(eq("Tester"), eq(CLUE_ITEM), any(), any(),
			eq(0), isNull());
		verify(unlockAfter("Maggot King", 5, CLUE_ITEM)).mergeObtainedItem(eq("Tester"), eq(CLUE_ITEM), any(), any(),
			eq(603), eq("Maggot King"));
	}

	// The game's message and its page can differ in case: "Thermonuclear smoke devil" against its page's name.
	@Test
	public void theBossAndItsPageMatchWhateverTheirCase()
	{
		verify(unlockAfter("Thermonuclear smoke devil", 20, DEVIL_ITEM)).mergeObtainedItem(eq("Tester"), eq(DEVIL_ITEM),
			any(), any(), eq(603), eq("Thermonuclear smoke devil"));
	}

	// A pet sits on All Pets as well as its boss's page: one of its pages being the boss's is enough.
	@Test
	public void anItemOnSeveralPagesTakesTheKillWhenOneIsTheBossPage()
	{
		verify(unlockAfter("Maggot King", 20, PET)).mergeObtainedItem(eq("Tester"), eq(PET), any(), any(), eq(603),
			eq("Maggot King"));
	}

	// Unchanged by the longer window: a tick count behind the kill's is inside the five ticks, as it always was.
	@Test
	public void aTickCountBehindTheKillsStillTakesIt()
	{
		verify(unlockAfter("Maggot King", -3, CLUE_ITEM)).mergeObtainedItem(eq("Tester"), eq(CLUE_ITEM), any(), any(),
			eq(603), eq("Maggot King"));
	}

	// An item the index knows no page for (a page list not read yet) stays bare past five ticks, without failing.
	@Test
	public void anItemWithNoPagesKnownStaysBare()
	{
		verify(unlockAfter("Maggot King", 20, UNPAGED)).mergeObtainedItem(eq("Tester"), eq(UNPAGED), any(), any(),
			eq(0), isNull());
	}

	@Test
	public void anUnlockWithNoKillBeforeItHasNone()
	{
		LiveClogSync sync = new LiveClogSync();
		LocalClogCache cache = unlock(sync, mock(Client.class), FANG);
		verify(cache).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(0), isNull());
	}

	// The plugin forgets its kills and credits at the login screen and on shutdown (KillClogPlugin), so another
	// account's unlock never takes them.
	@Test
	public void aResetForgetsKillsAndCredits()
	{
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext("Maggot King", 603), 1000);
		sync.credit("Maggot King", List.of("Elder venator fang"), 1000);
		sync.reset();
		verify(unlock(sync, clientAt(1001), FANG)).mergeObtainedItem(eq("Tester"), eq(FANG), any(), any(), eq(0), isNull());
	}

	private static Client clientAt(int tick)
	{
		Client client = mock(Client.class);
		when(client.getTickCount()).thenReturn(tick);
		return client;
	}

	/** Kill 603 of `boss`, then the item's unlock `ticks` later. */
	private static LocalClogCache unlockAfter(String boss, int ticks, int itemId)
	{
		Client client = mock(Client.class);
		LiveClogSync sync = new LiveClogSync();
		sync.rememberKill(new ClogUnlockParser.KillContext(boss, 603), 1000);
		when(client.getTickCount()).thenReturn(1000 + ticks);
		return unlock(sync, client, itemId);
	}

	private static LocalClogCache unlock(LiveClogSync sync, Client client, int itemId)
	{
		return unlock(sync, client, itemId, "Probe page");
	}

	/** The unlock of `itemId`, PAGE_ITEM sitting on one page named `pageName`. */
	private static LocalClogCache unlock(LiveClogSync sync, Client client, int itemId, String pageName)
	{
		Player local = mock(Player.class);
		when(local.getName()).thenReturn("Tester");
		when(client.getLocalPlayer()).thenReturn(local);
		ClogIndex index = mock(ClogIndex.class);
		when(index.ensureParsed(any(), any())).thenReturn(true);
		when(index.itemIdsForName(any())).thenReturn(List.of(itemId));
		when(index.categoryKeysForItem(FANG)).thenReturn(List.of("maggot_king"));
		when(index.categoryKeysForItem(CLUE_ITEM)).thenReturn(List.of("beginner_treasure_trails"));
		when(index.categoryKeysForItem(PET)).thenReturn(List.of("all_pets", "maggot_king"));
		when(index.categoryKeysForItem(DEVIL_ITEM)).thenReturn(List.of("thermonuclear_smoke_devil"));
		when(index.categoryKeysForItem(UNPAGED)).thenReturn(null);
		when(index.categoryKeysForItem(GAUNTLET_ITEM)).thenReturn(List.of("the_gauntlet"));
		when(index.pageName("the_gauntlet")).thenReturn("The Gauntlet");
		when(index.categoryKeysForItem(PAGE_ITEM)).thenReturn(List.of("probe_page"));
		when(index.pageName("probe_page")).thenReturn(pageName);
		when(index.categoryKeysForItem(TWO_PAGES)).thenReturn(List.of("thermonuclear_smoke_devil", "maggot_king"));
		when(index.categoryKeysForItem(OTHER_ITEM)).thenReturn(List.of("other_page"));
		when(index.pageName("other_page")).thenReturn(pageName);
		when(index.tabPages()).thenReturn(java.util.Map.of(
			"Bosses", List.of("maggot_king", "thermonuclear_smoke_devil", "the_gauntlet", "probe_page"),
			"Clues", List.of("beginner_treasure_trails"), "Other", List.of("all_pets", "other_page")));
		when(index.pageName("maggot_king")).thenReturn("Maggot King");
		when(index.pageName("all_pets")).thenReturn("All Pets");
		when(index.pageName("beginner_treasure_trails")).thenReturn("Beginner Treasure Trails");
		when(index.pageName("thermonuclear_smoke_devil")).thenReturn("Thermonuclear Smoke Devil");
		LocalClogCache cache = mock(LocalClogCache.class);
		when(cache.serving("Tester")).thenReturn(true);
		when(cache.hasDataFor("Tester")).thenReturn(true);
		when(cache.mergeObtainedItem(anyString(), anyInt(), any(), any(), anyInt(), any())).thenReturn(true);
		sync.handleUnlock("Elder venator fang", 1232, 1734, client, null, index, cache, mock(KillClogChatNotifier.class),
			name ->
			{
			}, true);
		return cache;
	}
}
