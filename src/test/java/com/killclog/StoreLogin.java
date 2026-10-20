package com.killclog;

/** A login as the plugin's tick makes it: asked again each tick until the store has read the account's log fresh. */
final class StoreLogin
{
	private StoreLogin()
	{
	}

	static boolean activate(LocalClogCache store, String name, long accountHash)
	{
		long until = System.currentTimeMillis() + 2000;
		while (!store.activate(name, accountHash))
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
}
