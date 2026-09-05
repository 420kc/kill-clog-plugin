package com.killclog;

import lombok.AccessLevel;
import lombok.Setter;

final class ClogSessionState
{
	private boolean pendingAutoLookup;
	private boolean pendingAcctTypeRecheck;
	@Setter(AccessLevel.PACKAGE)
	private boolean pendingCaRead;
	private boolean autoLookedUp;

	void reset()
	{
		pendingAutoLookup = false;
		pendingAcctTypeRecheck = false;
		pendingCaRead = false;
		autoLookedUp = false;
	}

	void resetAutoLookupSession()
	{
		autoLookedUp = false;
	}

	void requestLocalReads()
	{
		pendingAcctTypeRecheck = true;
		pendingCaRead = true;
	}

	void requestCaRead()
	{
		pendingCaRead = true;
	}

	void requestAutoLookup(boolean enabled)
	{
		if (enabled && !autoLookedUp)
		{
			pendingAutoLookup = true;
		}
	}

	boolean pendingAcctTypeRecheck()
	{
		return pendingAcctTypeRecheck;
	}

	void clearAcctTypeRecheck()
	{
		pendingAcctTypeRecheck = false;
	}

	boolean pendingCaRead()
	{
		return pendingCaRead;
	}


	boolean pendingAutoLookup()
	{
		return pendingAutoLookup;
	}

	void markAutoLookupStarted()
	{
		pendingAutoLookup = false;
		autoLookedUp = true;
	}
}
