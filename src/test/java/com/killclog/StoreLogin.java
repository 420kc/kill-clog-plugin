package com.killclog;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logins as the session tracker hands them to a store: one session per login, asked again each tick until the store has
 * read the account's log fresh, and a logout that ends it.
 */
final class StoreLogin
{
	private static final AtomicLong NUMBERS = new AtomicLong(1000);
	private static final Map<Object, Session> LOGINS = Collections.synchronizedMap(new WeakHashMap<>());

	private StoreLogin()
	{
	}

	/** The store's login if it is this name and account, else a new one. */
	static Session session(LocalClogCache store, String name, long accountHash)
	{
		Session login = LOGINS.get(store);
		if (login == null || !login.loggedIn() || login.getAccountHash() != accountHash
			|| !RsnInputPolicy.sameName(login.getName(), name))
		{
			login = new Session(NUMBERS.incrementAndGet(), name, accountHash);
			LOGINS.put(store, login);
		}
		return login;
	}

	/** The plugin's tick: true once the store serves the login. The tracker never makes a login without its account. */
	static boolean activate(LocalClogCache store, String name, long accountHash)
	{
		if (name == null || accountHash == -1)
		{
			return false;
		}
		Session login = session(store, name, accountHash);
		long until = System.currentTimeMillis() + 2000;
		while (!store.serve(login))
		{
			if (System.currentTimeMillis() > until)
			{
				return false;
			}
			try
			{
				Thread.sleep(5);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return true;
	}

	/** The login screen: the store's login ends. */
	static void logout(LocalClogCache store)
	{
		Session none = Session.none(NUMBERS.incrementAndGet());
		LOGINS.put(store, none);
		store.serve(none);
	}
}
