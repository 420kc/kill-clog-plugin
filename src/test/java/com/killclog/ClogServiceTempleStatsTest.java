package com.killclog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClogServiceTempleStatsTest
{
	@Test
	public void readsProviderSelectedPrimaryEhp()
	{
		JsonObject data = new JsonParser().parse("{\"Overall_ehp\":1803.6,\"Im_ehp\":3003.8553}")
			.getAsJsonObject();
		JsonObject info = new JsonParser().parse("{\"Primary_ehp\":\"Im_ehp\"}")
			.getAsJsonObject();

		assertEquals(3003.8553, ClogService.parsePrimaryEhp(data, info), 0.0001);
	}

	@Test
	public void fallsBackToOverallEhpWhenPrimaryIsUnavailable()
	{
		JsonObject data = new JsonParser().parse("{\"Overall_ehp\":867.25}").getAsJsonObject();
		JsonObject info = new JsonObject();

		assertEquals(867.25, ClogService.parsePrimaryEhp(data, info), 0.0001);
	}
}
