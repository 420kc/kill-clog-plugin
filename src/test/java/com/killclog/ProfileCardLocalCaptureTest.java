package com.killclog;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class ProfileCardLocalCaptureTest
{
	@Test
	public void readsAchievementProgressFromAccountSummaryText()
	{
		assertArrayEquals(new int[]{422, 492}, ProfileCardLocalCapture.summaryProgress(
			Arrays.asList("<col=ff981f>Achievements<br>Completed:</col>",
				"<col=00ff00>422/492</col>"), "achievements"));
	}

	@Test
	public void leavesUnavailableAchievementProgressUnknown()
	{
		assertArrayEquals(new int[]{-1, -1}, ProfileCardLocalCapture.summaryProgress(
			Arrays.asList("Combat Level", "126"), "achievements"));
	}

	@Test
	public void neverReusesAchievementProgressAcrossAccounts()
	{
		assertArrayEquals(new int[]{422, 492}, ProfileCardLocalCapture.ownedSummaryProgress(
			"420 kc", 422, 492, "420 KC"));
		assertArrayEquals(new int[]{-1, -1}, ProfileCardLocalCapture.ownedSummaryProgress(
			"Player A", 422, 492, "Player B"));
	}
}
