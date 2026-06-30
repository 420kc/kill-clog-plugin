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
		ClogHelper.setGimBadges(null, null, null);
	}

	@Test
	public void testProviderAccountMetadataWinsTooltipLabel()
	{
		HiscoreResult regularHiscore = hiscore(AccountType.REGULAR);
		ClogResult providerGimClog = clog(AccountType.GROUP_IRONMAN);

		assertEquals("Group Ironman",
			LookupQueries.getAccountLabel(regularHiscore, providerGimClog));
	}

	@Test
	public void testHiscoreWinsWhenProviderTypeIsNotGroupIronman()
	{
		HiscoreResult ironHiscore = hiscore(AccountType.IRONMAN);
		ClogResult providerRegularClog = clog(AccountType.REGULAR);

		assertEquals("Ironman", LookupQueries.getAccountLabel(ironHiscore, providerRegularClog));
	}

	@Test
	public void testProviderRegularDoesNotAddVisibleLabel()
	{
		ClogResult providerRegularClog = clog(AccountType.REGULAR);

		assertNull(LookupQueries.getAccountLabel(null, providerRegularClog));
	}

	@Test
	public void testHiscoreAccountTypeUsedWhenClogHasNoMetadata()
	{
		HiscoreResult ironHiscore = hiscore(AccountType.IRONMAN);
		ClogResult noTypeClog = clog(null);

		assertEquals("Ironman", LookupQueries.getAccountLabel(ironHiscore, noTypeClog));
	}

	@Test
	public void testSpecialHiscoreTableNamesRegularPure()
	{
		HiscoreResult pureHiscore = hiscore(AccountType.REGULAR, HiscoreTable.ONE_DEFENCE);

		assertEquals("Pure", LookupQueries.getAccountLabel(pureHiscore, null));
	}

	@Test
	public void testSpecialHiscoreTableNamesRegularSkiller()
	{
		HiscoreResult skillerHiscore = hiscore(AccountType.REGULAR, HiscoreTable.SKILLER);

		assertEquals("Skiller", LookupQueries.getAccountLabel(skillerHiscore, null));
	}

	@Test
	public void testSpecialHiscoreTableDoesNotOverrideIronman()
	{
		HiscoreResult ironHiscore = hiscore(AccountType.IRONMAN, HiscoreTable.ONE_DEFENCE);

		assertEquals("Ironman", LookupQueries.getAccountLabel(ironHiscore, null));
	}

	@Test
	public void testSyncLineNullStaysHidden()
	{
		assertNull(LookupQueries.syncLine(null, true));
	}

	@Test
	public void testProviderGroupIronBadgeComesFromModiconCache()
	{
		BufferedImage gim = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		ClogHelper.setGimBadges(gim, null, null);

		assertSame(gim, LookupQueries.getAccountBadge(
			hiscore(AccountType.REGULAR),
			clog(AccountType.GROUP_IRONMAN)));
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
}
