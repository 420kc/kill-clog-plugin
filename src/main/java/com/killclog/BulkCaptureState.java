package com.killclog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BulkCaptureState
{
	private boolean active;
	int startTickCount = -1;
	int finalizeTickCount = -1;
	int clogCount = -1;
	int clogTotal = -1;
	final List<ClogResult.ClogItem> obtained = new ArrayList<>();
	final Set<Integer> obtainedIds = new HashSet<>();

	// Buffered category read captured before bulk has cache data to merge into.
	String bufferedCategoryKey;
	String bufferedCategoryName;
	List<Integer> bufferedCategoryItems;
	List<ClogResult.ClogItem> bufferedCategoryObtained;

	boolean isActive()
	{
		return active;
	}

	boolean readyToFinalize(int tickCount)
	{
		return active && finalizeTickCount > 0 && tickCount >= finalizeTickCount;
	}

	boolean timedOut(int tickCount, int timeoutTicks)
	{
		return active && finalizeTickCount < 0 && startTickCount >= 0
			&& tickCount - startTickCount > timeoutTicks;
	}

	void arm(int tickCount, int clogCount, int clogTotal)
	{
		active = true;
		startTickCount = tickCount;
		finalizeTickCount = -1;
		this.clogCount = clogCount;
		this.clogTotal = clogTotal;
		obtained.clear();
		obtainedIds.clear();
	}

	void captureScriptArguments(Object[] args, int tickCount)
	{
		if (args == null || args.length < 3 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer))
		{
			return;
		}

		int itemId = (int) args[1];
		int count = (int) args[2];
		if (obtainedIds.add(itemId))
		{
			obtained.add(new ClogResult.ClogItem(itemId, count, null));
		}
		finalizeTickCount = tickCount + 3;
	}

	void scheduleEmptySearchFinalization(int tickCount)
	{
		if (active && clogCount == 0 && clogTotal > 0)
		{
			finalizeTickCount = tickCount + 3;
		}
	}

	void reset()
	{
		active = false;
		startTickCount = -1;
		finalizeTickCount = -1;
		clogCount = -1;
		clogTotal = -1;
		obtained.clear();
		obtainedIds.clear();
		bufferedCategoryKey = null;
		bufferedCategoryName = null;
		bufferedCategoryItems = null;
		bufferedCategoryObtained = null;
	}
}
