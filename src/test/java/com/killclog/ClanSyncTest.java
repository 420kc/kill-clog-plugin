package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.runelite.api.clan.ClanTitle;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Your clan rides your sync (2.6.3): out in the body once the game has said, back to lookups from the proof view. */
public class ClanSyncTest
{
	@Test
	public void theSyncSaysYourClanNoneOrNothingYet() throws Exception
	{
		PlayerClans clans = mock(PlayerClans.class);
		SyncService service = new SyncService(null, new Gson());
		service.setClans(clans);
		when(clans.forSync("420 kc")).thenReturn(new PlayerClans.Yours("420 kc", "Clogged", new ClanTitle(100, "Administrator"), true));
		JsonObject clan = body(service).getAsJsonObject("clan");
		assertEquals("Clogged", clan.get("name").getAsString());
		assertEquals(100, clan.get("title_id").getAsInt());
		assertEquals("Administrator", clan.get("title").getAsString());

		when(clans.forSync("420 kc")).thenReturn(new PlayerClans.Yours("420 kc", null, null, true));
		assertTrue("in no clan: an explicit none", body(service).get("clan").isJsonNull());

		when(clans.forSync("420 kc")).thenReturn(null);
		assertFalse("before the game has said: left out, so killclog.com keeps it", body(service).has("clan"));
	}

	@Test
	public void theSnapshotSpeaksOnlyForItsOwnAccountAndOnlyUntilTheWorldUnsettles() throws Exception
	{
		PlayerClans clans = new PlayerClans(null, null, null, () -> true, () -> true);
		Field yours = PlayerClans.class.getDeclaredField("yours");
		yours.setAccessible(true);
		yours.set(clans, new PlayerClans.Yours("420 kc", "Clogged", null, true));
		assertEquals("Clogged", clans.forSync("420 kc").clan);
		assertNull("another account's sync never carries it", clans.forSync("based batt"));
		clans.clearClientSnapshot();
		assertNull("an unsettled world clears it until the game says again", clans.forSync("420 kc"));
	}

	@Test
	public void shareClanOffSaysNoneAtOnceSoNoKillClogSurfaceNamesIt() throws Exception
	{
		boolean[] shared = {false};
		PlayerClans clans = new PlayerClans(null, null, null, () -> true, () -> shared[0]);
		Field yours = PlayerClans.class.getDeclaredField("yours");
		yours.setAccessible(true);
		yours.set(clans, new PlayerClans.Yours("420 kc", "Clogged", null, true));
		PlayerClans.Yours sent = clans.forSync("420 kc");
		assertNull("off: the sync says none, an explicit answer, so RuneProfile never stands in", sent.clan);
		assertNull(sent.title);
		assertTrue(sent.known);
		// Even before the game has said, so turning it off never leaves the last clan standing.
		yours.set(clans, new PlayerClans.Yours("420 kc", null, null, false));
		assertNull(clans.forSync("420 kc").clan);
		assertNull("another account's sync still carries nothing", clans.forSync("based batt"));
		shared[0] = true;
		yours.set(clans, new PlayerClans.Yours("420 kc", "Clogged", null, true));
		assertEquals("on again: the clan the game shows", "Clogged", clans.forSync("420 kc").clan);
	}

	@Test
	public void aLookupReadsTheClanTheirSyncNamed()
	{
		String json = "{\"rsn\":\"A q p Weed\",\"clog\":{\"items_by_category\":{}},"
			+ "\"clan\":{\"name\":\"Iron Clan\",\"title_id\":24,\"title\":\"Recruit\"}}";
		ClogResult clog = new KillclogService(null, new Gson(), null).parseProofView("A q p Weed", json, "a q p weed");
		assertEquals("Iron Clan", clog.getSyncedClan());
		assertEquals(24, clog.getSyncedClanTitle().getId());

		ClogResult none = new KillclogService(null, new Gson(), null)
			.parseProofView("x", "{\"clog\":{\"items_by_category\":{}},\"clan\":null}", "x");
		assertNull(none.getSyncedClan());
		assertTrue("in no clan is an answer: RuneProfile's never stands in for it", none.isSyncedClanKnown());

		ClogResult unsaid = new KillclogService(null, new Gson(), null)
			.parseProofView("x", "{\"clog\":{\"items_by_category\":{}}}", "x");
		assertFalse("a sync that never said leaves RuneProfile to stand in", unsaid.isSyncedClanKnown());
	}

	@Test
	public void theSyncedClanSurvivesACombineWhicheverLogWins()
	{
		ClogResult synced = new ClogResult("A q p Weed", Map.of(), Map.of(), Map.of(), null, AccountType.IRONMAN);
		synced.setSyncedClan("Iron Clan");
		synced.setSyncedClanKnown(true);
		ClogResult provider = new ClogResult("A q p Weed", Map.of("hats", java.util.List.of(new ClogResult.ClogItem(2991, 1, null))),
			Map.of(), Map.of(), null, AccountType.IRONMAN);
		assertEquals("Iron Clan", ClogResult.pickFullest(provider, synced).getSyncedClan());
	}

	@Test
	public void shareClanOffSendsAnExplicitNoneThroughTheRealBody() throws Exception
	{
		PlayerClans clans = new PlayerClans(null, null, null, () -> true, () -> false);
		Field yours = PlayerClans.class.getDeclaredField("yours");
		yours.setAccessible(true);
		yours.set(clans, new PlayerClans.Yours("420 kc", "Clannabis", new ClanTitle(-3, "Deputy Owner"), true));
		SyncService service = new SyncService(null, new Gson());
		service.setClans(clans);
		JsonObject sent = body(service);
		assertTrue("the field is there, so killclog.com replaces what it holds", sent.has("clan"));
		assertTrue("and it says none", sent.get("clan").isJsonNull());
		// As a push sends it, too: Gson's default leaves a null field out, so the server never heard "none".
		String text = service.requestBody(1L, null, new ClogResult("420 kc", Map.of(), Map.of(), Map.of(), null, null),
			Map.of(), Map.of(), GameMode.MAIN);
		assertTrue(text.contains("\"clan\":null"));
	}

	private static JsonObject body(SyncService service) throws Exception
	{
		Method build = SyncService.class.getDeclaredMethod("buildBody", long.class, AccountType.class,
			ClogResult.class, Map.class, Map.class);
		build.setAccessible(true);
		return (JsonObject) build.invoke(service, 1L, null,
			new ClogResult("420 kc", Map.of(), Map.of(), Map.of(), null, null), Map.of(), Map.of());
	}
}
