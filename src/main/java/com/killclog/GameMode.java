package com.killclog;

import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.WorldType;

/** Which game a world belongs to. Anything not known to be the main game is never treated as it. */
final class GameMode
{
	// World flags that do not create a separate character.
	private static final Set<WorldType> MAIN_FLAGS = EnumSet.of(WorldType.MEMBERS, WorldType.PVP,
		WorldType.BOUNTY, WorldType.SKILL_TOTAL, WorldType.HIGH_RISK, WorldType.LAST_MAN_STANDING);

	private GameMode()
	{
	}

	static boolean isMain(@Nullable Set<WorldType> flags)
	{
		return flags != null && MAIN_FLAGS.containsAll(flags);
	}
}
