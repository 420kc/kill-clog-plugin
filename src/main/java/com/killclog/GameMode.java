package com.killclog;

import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.WorldType;

/** Which game a world belongs to. Anything not known to be the main game or the active League has no mode. */
final class GameMode
{
	static final String MAIN = "main";
	// World flags that do not create a separate character. A Hardcore Worlds character is a main account on the main
	// game's save, its own name from Tutorial Island, and a transfer takes it to the main game as it is (Jagex 10/22).
	private static final Set<WorldType> MAIN_FLAGS = EnumSet.of(WorldType.MEMBERS, WorldType.PVP,
		WorldType.BOUNTY, WorldType.SKILL_TOTAL, WorldType.HIGH_RISK, WorldType.LAST_MAN_STANDING,
		WorldType.HARDCORE_MAIN);

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

	/** A Hardcore Worlds world: its syncs say so, and a reset starts the character's log over. */
	static boolean hardcore(@Nullable Set<WorldType> flags)
	{
		return flags != null && flags.contains(WorldType.HARDCORE_MAIN) && MAIN.equals(of(flags, null));
	}
}
