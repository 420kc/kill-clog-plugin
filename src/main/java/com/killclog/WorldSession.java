package com.killclog;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Which game the logged-in world plays (the main game, the announced League, or none known), the store
 * its captures belong in, and whether the world has settled: counters, CA tiers and the account type
 * sent to the server wait for it, so a hop or login never carries another game's values into this one.
 * When the world's game or the running League changes, the panel and chat are told which to read.
 */
final class WorldSession
{
	static final int SETTLED_TICKS = 10;

	/** Hands the panel and chat the game to read: a League and its store, or null for the main game. */
	interface Shown
	{
		void show(@Nullable String league, @Nullable LocalClogCache leagueLog, @Nullable String activeLeague);
	}

	private final Supplier<String> mode;
	private final Supplier<String> activeLeague;
	private final Supplier<LocalClogCache> main;
	private final Function<String, LocalClogCache> leagueStores;
	private final Consumer<LocalClogCache> opened;
	// Ticks on this world with an unchanged account type.
	private int settledTicks;
	// The active League's own store, opened on first use; one League at a time.
	private LocalClogCache leagueCache;
	private String leagueCacheId;
	// The world's game and running League last handed over.
	private String shown = "";

	/**
	 * @param mode         main, the announced League, or null: logged in on a world whose game is known
	 * @param leagueStores opens a League's own store; {@code opened} wires each one as it opens
	 */
	WorldSession(Supplier<String> mode, Supplier<String> activeLeague, Supplier<LocalClogCache> main,
		Function<String, LocalClogCache> leagueStores, Consumer<LocalClogCache> opened)
	{
		this.mode = mode;
		this.activeLeague = activeLeague;
		this.main = main;
		this.leagueStores = leagueStores;
		this.opened = opened;
	}

	@Nullable
	String mode()
	{
		return mode.get();
	}

	boolean settled()
	{
		return settledTicks >= SETTLED_TICKS;
	}

	boolean mainSettled()
	{
		return settled() && GameMode.MAIN.equals(mode());
	}

	void unsettle()
	{
		settledTicks = 0;
	}

	/** Counts this world's settled ticks; true on the tick a main-game world settles. */
	boolean tick()
	{
		if (mode() == null)
		{
			settledTicks = 0;
			return false;
		}
		return ++settledTicks == SETTLED_TICKS && GameMode.MAIN.equals(mode());
	}

	/** The store this world's captures belong in, or null when the world's game is unknown. */
	@Nullable
	LocalClogCache captureCache()
	{
		String current = mode();
		return current == null ? null : cacheFor(current);
	}

	LocalClogCache cacheFor(String game)
	{
		if (GameMode.MAIN.equals(game))
		{
			return main.get();
		}
		if (!game.equals(leagueCacheId))
		{
			if (leagueCache != null)
			{
				leagueCache.close();
			}
			LocalClogCache created = leagueStores.apply(game);
			opened.accept(created);
			leagueCache = created;
			leagueCacheId = game;
		}
		return leagueCache;
	}

	/** A League world reads its League in the panel and chat; every other world, or none, the main game. */
	void follow(Shown shown)
	{
		String current = mode();
		String league = current == null || GameMode.MAIN.equals(current) ? null : current;
		String active = activeLeague.get();
		// Keyed on the mode itself, so a logout (no mode) ends a flip made on a main world.
		if (!(current + "/" + active).equals(this.shown))
		{
			this.shown = current + "/" + active;
			shown.show(league, league == null ? null : captureCache(), active);
		}
	}

	/** Nothing matches it, so the next follow hands the world's game over again. */
	void showAgain()
	{
		shown = "";
	}

	void sessionEnded()
	{
		if (leagueCache != null)
		{
			leagueCache.onSessionEnded();
		}
	}

	void close()
	{
		if (leagueCache != null)
		{
			leagueCache.close();
			leagueCache = null;
			leagueCacheId = null;
		}
	}
}
