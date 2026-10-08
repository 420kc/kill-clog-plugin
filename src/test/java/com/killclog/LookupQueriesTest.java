package com.killclog;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class LookupQueriesTest
{
	@After
	public void tearDown()
	{
		GimBadgeLoader.setGimBadges(null, null, null);
	}

	@Test
	public void testProviderAccountMetadataWinsTooltipLabel()
	{
		HiscoreResult regularHiscore = hiscore(AccountType.REGULAR);
		ClogResult providerGimClog = clog(AccountType.GROUP_IRONMAN);

		assertEquals("Group Ironman",
			AccountBadgeResolver.label(LookupQueries.accountDisplay(regularHiscore, providerGimClog)));
	}

	@Test
	public void testHiscoreWinsWhenProviderTypeIsNotGroupIronman()
	{
		HiscoreResult ironHiscore = hiscore(AccountType.IRONMAN);
		ClogResult providerRegularClog = clog(AccountType.REGULAR);

		assertEquals("Ironman", AccountBadgeResolver.label(LookupQueries.accountDisplay(ironHiscore, providerRegularClog)));
	}

	@Test
	public void testProviderRegularDoesNotAddVisibleLabel()
	{
		ClogResult providerRegularClog = clog(AccountType.REGULAR);

		assertNull(AccountBadgeResolver.label(LookupQueries.accountDisplay(null, providerRegularClog)));
	}

	@Test
	public void testHiscoreAccountTypeUsedWhenClogHasNoMetadata()
	{
		HiscoreResult ironHiscore = hiscore(AccountType.IRONMAN);
		ClogResult noTypeClog = clog(null);

		assertEquals("Ironman", AccountBadgeResolver.label(LookupQueries.accountDisplay(ironHiscore, noTypeClog)));
	}

	@Test
	public void testSpecialHiscoreTableNamesRegularPure()
	{
		HiscoreResult pureHiscore = hiscore(AccountType.REGULAR, HiscoreTable.ONE_DEFENCE);

		assertEquals("Pure", AccountBadgeResolver.label(LookupQueries.accountDisplay(pureHiscore, null)));
	}

	@Test
	public void testSpecialHiscoreTableNamesRegularSkiller()
	{
		HiscoreResult skillerHiscore = hiscore(AccountType.REGULAR, HiscoreTable.SKILLER);

		assertEquals("Skiller", AccountBadgeResolver.label(LookupQueries.accountDisplay(skillerHiscore, null)));
	}

	@Test
	public void testSpecialHiscoreTableDoesNotOverrideIronman()
	{
		HiscoreResult ironHiscore = hiscore(AccountType.IRONMAN, HiscoreTable.ONE_DEFENCE);

		assertEquals("Ironman", AccountBadgeResolver.label(LookupQueries.accountDisplay(ironHiscore, null)));
	}

	@Test
	public void testSyncLineNullStaysHidden()
	{
		assertNull(LookupQueries.syncLine(null, true));
	}

	@Test
	public void theLastUpdateReadsOnThePlayersOwnClock()
	{
		java.util.TimeZone before = java.util.TimeZone.getDefault();
		java.util.Locale locale = java.util.Locale.getDefault();
		try
		{
			java.util.Locale.setDefault(java.util.Locale.ENGLISH);
			// 01:20 UTC on Oct 6 is 8:20 PM on Oct 5 in Chicago.
			java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Chicago"));
			assertEquals("Oct 5 '25", LookupQueries.syncLine("2025-10-06 01:20:41", true).substring(0, 9));
			assertEquals("Oct 5", ClogSummaryTooltip.shortDate("2025-10-06 01:20:41"));
			java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
			assertEquals("Oct 6", ClogSummaryTooltip.shortDate("2025-10-06 01:20:41"));
			assertNull(ClogSummaryTooltip.shortDate("not a date"));
			assertTrue(LookupQueries.isSyncStale("2025-10-06 01:20:41", 90));
			assertTrue(LookupQueries.isSyncStale(null, 90));
		}
		finally
		{
			java.util.TimeZone.setDefault(before);
			java.util.Locale.setDefault(locale);
		}
	}

	@Test
	public void testProviderGroupIronBadgeComesFromModiconCache()
	{
		BufferedImage gim = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		GimBadgeLoader.setGimBadges(gim, null, null);

		assertSame(gim, AccountBadgeResolver.badge(LookupQueries.accountDisplay(
			hiscore(AccountType.REGULAR),
			clog(AccountType.GROUP_IRONMAN))));
	}

	private static HiscoreResult hiscore(AccountType accountType)
	{
		return hiscore(accountType, HiscoreTable.STANDARD);
	}

	private static HiscoreResult hiscore(AccountType accountType, HiscoreTable table)
	{
		return new HiscoreResult(
			accountType,
			table,
			Collections.emptyMap(),
			Collections.emptyMap(),
			Collections.emptyMap(),
			Collections.emptyMap(),
			Collections.emptyMap(),
			0,
			0,
			0,
			0);
	}

	private static ClogResult clog(AccountType accountType)
	{
		return new ClogResult(
			"test",
			Collections.emptyMap(),
			Collections.emptyMap(),
			new HashMap<>(),
			null,
			accountType);
	}

	@Test
	public void daysAgoCountsRealHours()
	{
		java.util.TimeZone before = java.util.TimeZone.getDefault();
		try
		{
			java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Chicago"));
			java.time.Instant now = java.time.Instant.now();
			String almostADay = now.minus(java.time.Duration.ofMinutes(23 * 60 + 30)).toString();
			String overADay = now.minus(java.time.Duration.ofMinutes(24 * 60 + 30)).toString();
			assertFalse(LookupQueries.syncLine(almostADay, false).contains("ago"));
			assertTrue(LookupQueries.syncLine(overADay, false).endsWith("(1d ago)"));
			assertFalse(LookupQueries.isSyncStale(almostADay, 1));
			assertTrue(LookupQueries.isSyncStale(overADay, 1));
		}
		finally
		{
			java.util.TimeZone.setDefault(before);
		}
	}
}
