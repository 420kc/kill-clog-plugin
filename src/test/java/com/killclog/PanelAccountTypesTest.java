package com.killclog;

import com.google.gson.Gson;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelAccountTypesTest
{
	private static final String PLAYER = "RNG Shango";
	private static final AccountType[] GROUP_TYPES = {
		AccountType.GROUP_IRONMAN,
		AccountType.HARDCORE_GROUP_IRONMAN,
		AccountType.UNRANKED_GROUP_IRONMAN
	};

	@Test
	public void testClogFirstGimSurvivesHiscoreAndRepeatedRefresh()
	{
		PanelAccountTypes resolver = new PanelAccountTypes(new CachedRuneProfile(null));
		HiscoreResult regular = hiscore(AccountType.REGULAR, HiscoreTable.STANDARD);
		for (AccountType type : GROUP_TYPES)
		{
			ClogResult clog = clog(type);
			assertAllDisplays(resolver, null, clog, type, HiscoreTable.STANDARD, type.displayName());
			assertAllDisplays(resolver, regular, clog, type, HiscoreTable.STANDARD, type.displayName());
			// The null-CA callback and later cached reveal must resolve the same identity.
			assertAllDisplays(resolver, regular, clog, type, HiscoreTable.STANDARD, type.displayName());
		}
	}

	@Test
	public void testHiscoreFirstGimSurvivesRepeatedRefresh()
	{
		PanelAccountTypes resolver = new PanelAccountTypes(new CachedRuneProfile(null));
		HiscoreResult regular = hiscore(AccountType.REGULAR, HiscoreTable.STANDARD);
		for (AccountType type : GROUP_TYPES)
		{
			assertAllDisplays(resolver, regular, null, AccountType.REGULAR, HiscoreTable.STANDARD, null);
			assertAllDisplays(resolver, regular, clog(type), type, HiscoreTable.STANDARD, type.displayName());
			assertAllDisplays(resolver, regular, clog(type), type, HiscoreTable.STANDARD, type.displayName());
		}
	}

	@Test
	public void testKnownRuntimeGimSurvivesConflictingProviderTypes()
	{
		for (AccountType known : GROUP_TYPES)
		{
			for (AccountType provider : GROUP_TYPES)
			{
				PanelAccountTypes resolver = new PanelAccountTypes(new CachedRuneProfile(provider));
				ClogResult clog = clog(provider);
				assertAllDisplays(resolver, hiscore(known, HiscoreTable.STANDARD), clog,
					known, HiscoreTable.STANDARD, known.displayName());
				assertDisplay(resolver.currentDisplay(known,
					hiscore(AccountType.REGULAR, HiscoreTable.STANDARD), clog, PLAYER),
					known, HiscoreTable.STANDARD, known.displayName());
			}
		}
	}

	@Test
	public void testFirstPartyGimSurvivesConflictingRuneProfileCache()
	{
		HiscoreResult regular = hiscore(AccountType.REGULAR, HiscoreTable.STANDARD);
		for (AccountType known : GROUP_TYPES)
		{
			ClogResult firstParty = clog(known).withSources(false, false, true);
			CachedRuneProfile runeProfile = new CachedRuneProfile(null);
			PanelAccountTypes resolver = new PanelAccountTypes(runeProfile);
			assertAllDisplays(resolver, regular, firstParty, known, HiscoreTable.STANDARD, known.displayName());
			for (AccountType provider : GROUP_TYPES)
			{
				runeProfile.accountType = provider;
				assertAllDisplays(resolver, regular, firstParty, known, HiscoreTable.STANDARD, known.displayName());
			}
		}
	}

	@Test
	public void testRuneProfileOnlyGimStillEnrichesRegularHiscores()
	{
		HiscoreResult regular = hiscore(AccountType.REGULAR, HiscoreTable.STANDARD);
		CachedRuneProfile runeProfile = new CachedRuneProfile(null);
		PanelAccountTypes resolver = new PanelAccountTypes(runeProfile);
		assertAllDisplays(resolver, regular, null, AccountType.REGULAR, HiscoreTable.STANDARD, null);
		for (AccountType type : GROUP_TYPES)
		{
			runeProfile.accountType = type;
			assertAllDisplays(resolver, regular, null, type, HiscoreTable.STANDARD, type.displayName());
			assertAllDisplays(resolver, regular, clog(AccountType.REGULAR),
				type, HiscoreTable.STANDARD, type.displayName());
		}
	}

	@Test
	public void testOrdinaryIronmanIdentityAndLabelsStayUnchanged()
	{
		PanelAccountTypes resolver = new PanelAccountTypes(new CachedRuneProfile(AccountType.REGULAR));
		AccountType[] types = {AccountType.IRONMAN, AccountType.HARDCORE_IRONMAN, AccountType.ULTIMATE_IRONMAN};
		String[] labels = {"Ironman", "Hardcore Ironman", "Ultimate Ironman"};
		for (int i = 0; i < types.length; i++)
		{
			assertAllDisplays(resolver, hiscore(types[i], HiscoreTable.STANDARD), clog(AccountType.REGULAR),
				types[i], HiscoreTable.STANDARD, labels[i]);
			assertAllDisplays(resolver, hiscore(types[i], HiscoreTable.ONE_DEFENCE), null,
				types[i], HiscoreTable.STANDARD, labels[i]);
		}
	}

	@Test
	public void testRegularSpecialHiscoreLabelsStayUnchanged()
	{
		PanelAccountTypes resolver = new PanelAccountTypes(new CachedRuneProfile(null));
		assertAllDisplays(resolver, hiscore(AccountType.REGULAR, HiscoreTable.STANDARD), clog(AccountType.REGULAR),
			AccountType.REGULAR, HiscoreTable.STANDARD, null);
		assertAllDisplays(resolver, hiscore(AccountType.REGULAR, HiscoreTable.ONE_DEFENCE), clog(AccountType.REGULAR),
			AccountType.REGULAR, HiscoreTable.ONE_DEFENCE, "Pure");
		assertAllDisplays(resolver, hiscore(AccountType.REGULAR, HiscoreTable.SKILLER), clog(AccountType.REGULAR),
			AccountType.REGULAR, HiscoreTable.SKILLER, "Skiller");
	}

	private static void assertAllDisplays(PanelAccountTypes resolver, HiscoreResult hiscore, ClogResult clog,
		AccountType expectedType, HiscoreTable expectedTable, String expectedLabel)
	{
		assertDisplay(resolver.currentDisplay(hiscore, clog, PLAYER), expectedType, expectedTable, expectedLabel);
		assertDisplay(resolver.currentDisplay(hiscore != null ? hiscore.getAccountType() : null, hiscore, clog, PLAYER),
			expectedType, expectedTable, expectedLabel);
		assertDisplay(resolver.displayIdentity(hiscore, clog, PLAYER), expectedType, expectedTable, expectedLabel);
	}

	private static void assertDisplay(AccountDisplay display, AccountType type, HiscoreTable table, String label)
	{
		assertEquals(type, display.accountType());
		assertEquals(table, display.hiscoreTable());
		assertEquals(label, display.label());
	}

	private static HiscoreResult hiscore(AccountType type, HiscoreTable table)
	{
		return new HiscoreResult(type, table, Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 100, 0, 10, 1);
	}

	private static ClogResult clog(AccountType type)
	{
		return new ClogResult(PLAYER, Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), null, type);
	}

	private static final class CachedRuneProfile extends RuneProfileService
	{
		private AccountType accountType;

		private CachedRuneProfile(AccountType accountType)
		{
			super(null, new Gson(), null);
			this.accountType = accountType;
		}

		@Override
		public AccountType getCachedAccountType(String playerName)
		{
			return accountType;
		}
	}
}
