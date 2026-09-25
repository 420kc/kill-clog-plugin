package com.killclog;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameModeTest
{
	@Test
	public void onlyWorldsWithoutASeparateCharacterAreTheMainGame()
	{
		assertTrue(GameMode.isMain(EnumSet.noneOf(WorldType.class)));
		assertTrue(GameMode.isMain(EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK)));
		assertTrue(GameMode.isMain(EnumSet.of(WorldType.SKILL_TOTAL, WorldType.BOUNTY, WorldType.LAST_MAN_STANDING)));
		for (WorldType separate : new WorldType[]{WorldType.SEASONAL, WorldType.DEADMAN, WorldType.BETA_WORLD,
			WorldType.QUEST_SPEEDRUNNING, WorldType.FRESH_START_WORLD, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD})
		{
			assertFalse(separate.name(), GameMode.isMain(EnumSet.of(WorldType.MEMBERS, separate)));
		}
		assertFalse(GameMode.isMain(null));
	}
}
