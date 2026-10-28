package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.clan.ClanTitle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RuneProfileAccountSummaryParserTest
{
	@Test
	public void theAccountTypeReadsItsKeyThenItsNameThenItsId()
	{
		assertEquals(AccountType.HARDCORE_IRONMAN, type("{accountType: {key: 'HARDCORE_IRONMAN', name: 'Ironman', id: 0}}"));
		assertEquals(AccountType.IRONMAN, type("{accountType: {key: null, name: 'Ironman', id: 0}}"));
		assertEquals(AccountType.fromProviderId(1), type("{accountType: {id: 1}}"));
	}

	@Test
	public void noAccountTypeReadsAsUnknown()
	{
		assertNull(type("{}"));
		assertNull(type("{accountType: null}"));
		assertNull(type("{accountType: 'IRONMAN'}"));
		assertNull(type("{accountType: {}}"));
	}

	@Test
	public void theClanIsItsTrimmedNameAndItsTitleTheIconsId()
	{
		JsonObject summary = json("{clan: {name: ' 420 kc ', icon: 12, title: 'Captain'}}");
		assertEquals("420 kc", RuneProfileAccountSummaryParser.parseClan(summary));
		ClanTitle title = RuneProfileAccountSummaryParser.parseClanTitle(summary);
		assertEquals(12, title.getId());
		assertEquals("Captain", title.getName());
	}

	@Test
	public void noClanOrABlankOneReadsAsNone()
	{
		assertNull(RuneProfileAccountSummaryParser.parseClan(json("{}")));
		assertNull(RuneProfileAccountSummaryParser.parseClan(json("{clan: null}")));
		assertNull(RuneProfileAccountSummaryParser.parseClan(json("{clan: {name: '  '}}")));
		assertNull(RuneProfileAccountSummaryParser.parseClan(json("{clan: {name: {first: 'x'}}}")));
		assertNull(RuneProfileAccountSummaryParser.parseClanTitle(json("{clan: {name: '420 kc'}}")));
	}

	private static AccountType type(String text)
	{
		return RuneProfileAccountSummaryParser.parseAccountType(json(text));
	}

	private static JsonObject json(String text)
	{
		return new Gson().fromJson(text, JsonObject.class);
	}
}
