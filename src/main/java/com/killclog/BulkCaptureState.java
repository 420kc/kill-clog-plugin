package com.killclog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;

final class BulkCaptureState
{
	@Getter(AccessLevel.PACKAGE)
	private boolean active;
	int startTickCount = -1;
	int finalizeTickCount = -1;
	int clogCount = -1;
	int clogTotal = -1;
	final List<ClogResult.ClogItem> obtained = new ArrayList<>();
	final Set<Integer> obtainedIds = new HashSet<>();

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
		if (itemId <= 0 || count <= 0)
		{
			return;
		}
		if (obtainedIds.add(itemId))
		{
			obtained.add(new ClogResult.ClogItem(itemId, count, null));
		}
		finalizeTickCount = tickCount + 3;
	}

	void scheduleEmptySearchFinalization(int tickCount)
	{
		if (active && clogCount == 0)
		{
			// A known catalog total is already settled. Both counters at zero can
			// also mean a brand-new account, so leave room for delayed scripts.
			finalizeTickCount = tickCount + (clogTotal > 0 ? 3 : 10);
		}
	}

	void deferEmptyFinalizationIfItemsReported(int reportedCount)
	{
		if (active && reportedCount > 0 && obtained.isEmpty())
		{
			finalizeTickCount = -1;
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
	}
}
