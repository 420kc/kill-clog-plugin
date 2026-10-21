package com.killclog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.Client;
import net.runelite.api.GameState;

/**
 * The one place the plugin decides who is logged in. The client thread hands it the game's state on each game state
 * change and each tick: a login whose name and account have both arrived becomes a session, a different account or
 * name is a new one, and the login screen ends it. A hop or a dropped connection keeps it, as the game does. Each
 * change goes once to every listener, in the order they were added, so the stores, the panel and the publication
 * flow move together; work that outlives its session asks {@link #isCurrent} before it acts.
 */
final class SessionTracker
{
	/** Told of each change on the client thread, before anything else can see the new session. */
	interface Listener
	{
		void sessionChanged(Session previous, Session now);
	}

	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private volatile Session current = Session.none(0);

	void addListener(Listener listener)
	{
		listeners.add(listener);
	}

	Session current()
	{
		return current;
	}

	/** Whether nothing has changed who is logged in since {@code session} began. */
	boolean isCurrent(Session session)
	{
		return session.getNumber() == current.getNumber();
	}

	/** On the client thread, after each game state change and on every tick. */
	void update(Client client)
	{
		GameState state = client.getGameState();
		Session now = current;
		if (state == GameState.LOGIN_SCREEN)
		{
			if (now.loggedIn())
			{
				change(now, Session.none(now.getNumber() + 1));
			}
			return;
		}
		String name = RsnInputPolicy.localName(client);
		long accountHash = client.getAccountHash();
		if (state != GameState.LOGGED_IN || name == null || accountHash == -1)
		{
			return;
		}
		if (!now.loggedIn() || now.getAccountHash() != accountHash || !RsnInputPolicy.sameName(now.getName(), name))
		{
			change(now, new Session(now.getNumber() + 1, name, accountHash));
		}
	}

	private void change(Session previous, Session next)
	{
		current = next;
		for (Listener listener : listeners)
		{
			listener.sessionChanged(previous, next);
		}
	}
}
