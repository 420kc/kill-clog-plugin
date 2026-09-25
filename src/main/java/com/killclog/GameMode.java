package com.killclog;

import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.WorldType;

/** Which game a world belongs to. Anything not known to be the main game or the active League has no mode. */
final class GameMode
{
	static final String MAIN = "main";
	// World flags that do not create a separate character.
	private static final Set<WorldType> MAIN_FLAGS = EnumSet.of(WorldType.MEMBERS, WorldType.PVP,
		WorldType.BOUNTY, WorldType.SKILL_TOTAL, WorldType.HIGH_RISK, WorldType.LAST_MAN_STANDING);

	private GameMode()
	{
	}

	/** Main, the League the server announced for seasonal worlds, or null. */
	@Nullable
	static String of(@Nullable Set<WorldType> flags, @Nullable String activeLeague)
	{
		if (flags == null)
		{
			return null;
		}
		for (WorldType flag : flags)
		{
			if (flag != WorldType.SEASONAL && !MAIN_FLAGS.contains(flag))
			{
				return null;
			}
		}
		return flags.contains(WorldType.SEASONAL) ? activeLeague : MAIN;
	}
}
