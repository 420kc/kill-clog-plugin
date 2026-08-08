package com.killclog;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileCardLocalCaptureTest
{
	@Test
	public void readsAchievementProgressFromAccountSummaryText()
	{
		assertArrayEquals(new int[]{422, 492}, ProfileCardLocalCapture.summaryProgress(
			Arrays.asList("<col=ff981f>Achievements<br>Completed:</col>",
				"<col=00ff00>422/492</col>"), "achievements completed"));
	}

	@Test
	public void readsAchievementProgressSplitAcrossSummaryWidgets()
	{
		assertArrayEquals(new int[]{422, 492}, ProfileCardLocalCapture.summaryProgress(
			Arrays.asList("Achievements", "Completed:", "422", "/492"),
			"achievements completed"));
	}

	@Test
	public void ignoresCombatAchievementsBeforeNativeAchievementProgress()
	{
		assertArrayEquals(new int[]{422, 492}, ProfileCardLocalCapture.summaryProgress(
			Arrays.asList("Combat Achievements", "336/646", "Achievements", "Completed:",
				"422/492"), "achievements completed"));
	}

	@Test
	public void doesNotBorrowCombatAchievementsAfterMissingNativeProgress()
	{
		assertArrayEquals(new int[]{-1, -1}, ProfileCardLocalCapture.summaryProgress(
			Arrays.asList("Achievements", "Completed:", "Combat Achievements", "336/646"),
			"achievements completed"));
	}

	@Test
	public void validatesProgressBeforeCachingOrPersisting()
	{
		assertTrue(ProfileCardLocalCapture.validProgress(422, 492));
		assertTrue(ProfileCardLocalCapture.validProgress(0, 492));
		assertFalse(ProfileCardLocalCapture.validProgress(-1, 492));
		assertFalse(ProfileCardLocalCapture.validProgress(0, 0));
		assertFalse(ProfileCardLocalCapture.validProgress(493, 492));
	}

	@Test
	public void leavesUnavailableAchievementProgressUnknown()
	{
		assertArrayEquals(new int[]{-1, -1}, ProfileCardLocalCapture.summaryProgress(
			Arrays.asList("Combat Level", "126"), "achievements completed"));
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
