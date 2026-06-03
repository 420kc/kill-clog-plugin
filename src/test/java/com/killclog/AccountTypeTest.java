package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccountTypeTest
{
	@Test
	public void testProviderIdsUseProviderOrder()
	{
		assertEquals(AccountType.REGULAR, AccountType.fromProviderId(0));
		assertEquals(AccountType.IRONMAN, AccountType.fromProviderId(1));
		assertEquals(AccountType.HARDCORE_IRONMAN, AccountType.fromProviderId(2));
		assertEquals(AccountType.ULTIMATE_IRONMAN, AccountType.fromProviderId(3));
		assertEquals(AccountType.GROUP_IRONMAN, AccountType.fromProviderId(4));
		assertEquals(AccountType.HARDCORE_GROUP_IRONMAN, AccountType.fromProviderId(5));
		assertEquals(AccountType.UNRANKED_GROUP_IRONMAN, AccountType.fromProviderId(6));
		assertNull(AccountType.fromProviderId(99));
	}

	@Test
	public void testRuneLiteVarbitsUseClientOrder()
	{
		assertEquals(AccountType.REGULAR, AccountType.fromRuneLiteVarbit(0));
		assertEquals(AccountType.IRONMAN, AccountType.fromRuneLiteVarbit(1));
		assertEquals(AccountType.ULTIMATE_IRONMAN, AccountType.fromRuneLiteVarbit(2));
		assertEquals(AccountType.HARDCORE_IRONMAN, AccountType.fromRuneLiteVarbit(3));
		assertEquals(AccountType.GROUP_IRONMAN, AccountType.fromRuneLiteVarbit(4));
		assertEquals(AccountType.HARDCORE_GROUP_IRONMAN, AccountType.fromRuneLiteVarbit(5));
		assertEquals(AccountType.UNRANKED_GROUP_IRONMAN, AccountType.fromRuneLiteVarbit(6));
	}

	@Test
	public void testProviderValueNormalizesNames()
	{
		assertEquals(AccountType.REGULAR, AccountType.fromProviderValue("normal"));
		assertEquals(AccountType.HARDCORE_GROUP_IRONMAN,
			AccountType.fromProviderValue("hardcore group ironman"));
		assertEquals(AccountType.UNRANKED_GROUP_IRONMAN,
			AccountType.fromProviderValue("unranked-group-ironman"));
		assertNull(AccountType.fromProviderValue("league"));
	}

	@Test
	public void testDisplayTypeKeepsJagexBaseTypesLive()
	{
		assertEquals(AccountType.IRONMAN,
			AccountType.displayType(AccountType.IRONMAN, AccountType.REGULAR));
		assertEquals(AccountType.GROUP_IRONMAN,
			AccountType.displayType(AccountType.REGULAR, AccountType.GROUP_IRONMAN));
		assertEquals(AccountType.HARDCORE_GROUP_IRONMAN,
			AccountType.displayType(null, AccountType.HARDCORE_GROUP_IRONMAN));
		assertEquals(AccountType.ULTIMATE_IRONMAN,
			AccountType.displayType(AccountType.ULTIMATE_IRONMAN, null));
	}
}
