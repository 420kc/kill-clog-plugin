package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

/**
 * What TempleOSRS's player stats answer becomes: the canonical name, and the account type the collection log
 * endpoint can't tell (a GIM). Anything missing, empty or malformed reads as nothing, never as an error. No network.
 */
public class TempleStatsReadTest
{
	private String reply;

	@Test
	public void theCanonicalNameComesFromTheInfoBlock() throws Exception
	{
		assertEquals("Lynx Titan", name("{\"data\":{\"info\":{\"player_name_with_capitalization\":\"Lynx Titan\"}}}"));
		assertNull(name("{\"data\":{\"info\":{\"Username\":\"lynx titan\"}}}"));
	}

	@Test
	public void anEmptyOrMalformedAnswerReadsAsNothing() throws Exception
	{
		for (String body : new String[]{"{}", "{\"data\":{}}", "{\"data\":null}", "{\"data\":{\"info\":null}}",
			"{\"data\":[]}", "not json", "null"})
		{
			assertNull(body, name(body));
			assertNull(body, accountType(body));
		}
	}

	@Test
	public void aGameModeOrAGroupNamesTheAccountType() throws Exception
	{
		assertEquals(AccountType.IRONMAN, accountType("{\"data\":{\"info\":{\"Game mode\":\"1\",\"GIM\":0}}}"));
		assertEquals(AccountType.GROUP_IRONMAN, accountType("{\"data\":{\"info\":{\"Game mode\":\"0\",\"GIM\":4521}}}"));
		// A plain account says nothing the clog endpoint didn't, and a broken GIM field is no answer.
		assertNull(accountType("{\"data\":{\"info\":{\"Game mode\":\"0\",\"GIM\":0}}}"));
		assertNull(accountType("{\"data\":{\"info\":{\"Game mode\":null,\"GIM\":\"x\"}}}"));
		// A ranked game mode wins over a group id.
		assertEquals(AccountType.HARDCORE_IRONMAN,
			accountType("{\"data\":{\"info\":{\"Game mode\":\"2\",\"GIM\":4521}}}"));
	}

	private String name(String body) throws Exception
	{
		reply = body;
		return service().lookupRsn("lynx titan").get();
	}

	@SuppressWarnings("unchecked")
	private AccountType accountType(String body) throws Exception
	{
		reply = body;
		Method fetch = ClogService.class.getDeclaredMethod("fetchStatsAccountType", String.class);
		fetch.setAccessible(true);
		return ((CompletableFuture<AccountType>) fetch.invoke(service(), "lynx+titan")).get();
	}

	private ClogService service()
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
			new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
				.body(ResponseBody.create(MediaType.get("application/json"), reply)).build()).build();
		return new ClogService(http, new Gson(), null);
	}
}
