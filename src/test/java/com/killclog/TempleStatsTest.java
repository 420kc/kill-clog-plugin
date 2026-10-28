package com.killclog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TempleStatsTest
{
	private static JsonObject json(String text)
	{
		return new JsonParser().parse(text).getAsJsonObject();
	}

	@Test
	public void statsGiveTheModeTheNameAndEhp()
	{
		TempleStats iron = new TempleStats(json("{\"info\":{\"Game mode\":1,\"GIM\":0,"
			+ "\"player_name_with_capitalization\":\"420 kc\"},\"Overall_ehp\":3043.0743}"));
		assertEquals(AccountType.IRONMAN, iron.accountType);
		assertEquals("420 kc", iron.canonicalName);
		assertEquals(3043.0743, iron.ehp, 1e-9);

		TempleStats group = new TempleStats(json("{\"info\":{\"Game mode\":0,\"GIM\":\"5521\"}}"));
		assertEquals("a regular mode with a group is a group ironman", AccountType.GROUP_IRONMAN, group.accountType);
		assertEquals(-1, group.ehp, 0);

		TempleStats regular = new TempleStats(json("{\"info\":{\"Game mode\":0},\"Overall_ehp\":\"n/a\"}"));
		assertNull(regular.accountType);
		assertEquals(-1, regular.ehp, 0);
	}

	@Test
	public void ehcFollowsTheAccountsFlavour()
	{
		JsonObject clog = json("{\"ehc\":3377.93,\"ehc_gilded\":2449.16,\"ehc_im\":3417.36,\"ehc_gilded_im\":2612.03}");
		assertEquals(3417.36, TempleStats.ehc(clog, AccountType.IRONMAN), 1e-9);
		assertEquals(3417.36, TempleStats.ehc(clog, AccountType.GROUP_IRONMAN), 1e-9);
		assertEquals(3377.93, TempleStats.ehc(clog, AccountType.REGULAR), 1e-9);
		assertEquals(3377.93, TempleStats.ehc(clog, null), 1e-9);
		assertEquals(-1, TempleStats.ehc(json("{}"), null), 0);
	}

	@Test
	public void templesFiguresSurviveACombineWhicheverLogWins()
	{
		ClogResult temple = log("Temple");
		temple.setTempleEhp(3043.07);
		temple.setTempleEhc(3417.36);
		ClogResult killclog = log("Kill Clog");

		ClogResult combined = ClogResult.pickFullest(temple, killclog);
		assertEquals(3043.07, combined.getTempleEhp(), 1e-9);
		assertEquals(3417.36, combined.getTempleEhc(), 1e-9);
		ClogResult freshest = ClogResult.pickFullerProvider(temple, null);
		assertEquals(3043.07, freshest.getTempleEhp(), 1e-9);
	}

	private static ClogResult log(String name)
	{
		return new ClogResult(name, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), null,
			AccountType.IRONMAN);
	}
}
