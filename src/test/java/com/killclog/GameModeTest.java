package com.killclog;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GameModeTest
{
	@Test
	public void onlyWorldsWithoutASeparateCharacterAreTheMainGame()
	{
		assertEquals("main", GameMode.of(EnumSet.noneOf(WorldType.class), null));
		assertEquals("main", GameMode.of(EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK), "demonic-pacts"));
		assertEquals("main", GameMode.of(EnumSet.of(WorldType.SKILL_TOTAL, WorldType.BOUNTY, WorldType.LAST_MAN_STANDING), null));
		for (WorldType separate : new WorldType[]{WorldType.DEADMAN, WorldType.BETA_WORLD, WorldType.QUEST_SPEEDRUNNING,
			WorldType.FRESH_START_WORLD, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD})
		{
			assertNull(separate.name(), GameMode.of(EnumSet.of(WorldType.MEMBERS, separate), "demonic-pacts"));
		}
		assertNull(GameMode.of(null, "demonic-pacts"));
	}

	@Test
	public void aSeasonalWorldIsTheLeagueTheServerAnnounced()
	{
		assertEquals("demonic-pacts", GameMode.of(EnumSet.of(WorldType.SEASONAL, WorldType.MEMBERS), "demonic-pacts"));
		assertNull("no League announced, no mode", GameMode.of(EnumSet.of(WorldType.SEASONAL), null));
		assertNull("a seasonal Deadman world is not a League", GameMode.of(EnumSet.of(WorldType.SEASONAL, WorldType.DEADMAN), "demonic-pacts"));
	}
}
