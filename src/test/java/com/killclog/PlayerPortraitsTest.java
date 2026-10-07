package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PlayerPortraitsTest
{
	private static ClogResult clog(String name, boolean killclog)
	{
		return new ClogResult(name, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			null, null).withSources(!killclog, false, killclog);
	}

	private static OkHttpClient client()
	{
		OkHttpClient client = mock(OkHttpClient.class);
		when(client.newCall(any(Request.class))).thenReturn(mock(Call.class));
		return client;
	}

	private static List<Request> requests(OkHttpClient client, int count)
	{
		ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
		verify(client, times(count)).newCall(request.capture());
		return request.getAllValues();
	}

	private static byte[] png(int width, int height) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
		return out.toByteArray();
	}

	/** Answer the request out for a name, for the lookup it was sent for, as its completed call would. */
	private static void answer(PlayerPortraits portraits, String name, int code, byte[] bytes, String etag)
	{
		Integer generation = portraits.askedFor(name);
		portraits.onResponse(name, generation != null ? generation : 0, code, bytes, etag);
	}

	/** A PNG signature and header claiming {@code side} x {@code side}, with no image data behind it. */
	private static byte[] headerOnlyPng(int side)
	{
		ByteBuffer ihdr = ByteBuffer.allocate(17).put("IHDR".getBytes()).putInt(side).putInt(side)
			.put(new byte[] {8, 6, 0, 0, 0});
		CRC32 crc = new CRC32();
		crc.update(ihdr.array());
		return ByteBuffer.allocate(8 + 4 + 17 + 4)
			.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})
			.putInt(13).put(ihdr.array()).putInt((int) crc.getValue()).array();
	}

	@Test
	public void onlyAKillClogSyncerIsAskedAbout()
	{
		OkHttpClient client = client();
		PlayerPortraits portraits = new PlayerPortraits(client);
		portraits.lookedUp(clog("Lynx Titan", false));
		portraits.lookedUp(null);
		assertNull(portraits.forSummary(clog("Lynx Titan", false)));
		verify(client, never()).newCall(any(Request.class));

		portraits.lookedUp(clog("420 kc", true));
		portraits.lookedUp(clog("420_KC", true));
		Request request = requests(client, 1).get(0);
		assertEquals("https://killclog.com/api/player/420%20kc/portrait/large", request.url().toString());
		assertEquals(HttpUtil.USER_AGENT, request.header("User-Agent"));
		assertNull("nothing held yet, so nothing to re-check", request.header("If-None-Match"));
	}

	@Test
	public void theLocalPlayersOwnLogAsksToo()
	{
		OkHttpClient client = client();
		new PlayerPortraits(client).lookedUp(clog("420 kc", false).withLocalSource(true));
		requests(client, 1);
	}

	@Test
	public void aPortraitShowsOnlyOnceTheServerConfirmsItForTheLookup() throws IOException
	{
		OkHttpClient client = client();
		PlayerPortraits portraits = new PlayerPortraits(client);
		String key = PlayerPortraits.key("420 kc");
		portraits.lookedUp(clog("420 kc", true));
		assertNull("not before the answer", portraits.forSummary(clog("420 kc", true)));
		answer(portraits, key, 200, png(56, 80), "\"v1\"");
		BufferedImage shown = portraits.forSummary(clog("420 kc", true));
		assertNotNull(shown);
		assertEquals(56, shown.getWidth());

		// The next lookup withholds it until the server answers again; unchanged is a bodiless 304.
		portraits.lookedUp(clog("420 kc", true));
		assertNull(portraits.forSummary(clog("420 kc", true)));
		assertEquals("\"v1\"", requests(client, 2).get(1).header("If-None-Match"));
		answer(portraits, key, 304, null, "\"v1\"");
		assertSame(shown, portraits.forSummary(clog("420 kc", true)));
	}

	@Test
	public void aWithdrawalIsGoneFromTheNextLookup() throws IOException
	{
		for (int code : new int[] {404, 451})
		{
			PlayerPortraits portraits = new PlayerPortraits(client());
			String key = PlayerPortraits.key("420 kc");
			portraits.lookedUp(clog("420 kc", true));
			answer(portraits, key, 200, png(56, 80), "\"v1\"");
			assertNotNull(portraits.forSummary(clog("420 kc", true)));
			portraits.lookedUp(clog("420 kc", true));
			answer(portraits, key, code, null, null);
			assertNull(String.valueOf(code), portraits.forSummary(clog("420 kc", true)));
		}
	}

	@Test
	public void aFailedReCheckWithholdsTheHeldPortrait() throws IOException
	{
		PlayerPortraits portraits = new PlayerPortraits(client());
		String key = PlayerPortraits.key("420 kc");
		portraits.lookedUp(clog("420 kc", true));
		answer(portraits, key, 200, png(56, 80), "\"v1\"");
		portraits.lookedUp(clog("420 kc", true));
		answer(portraits, key, 503, null, null);
		assertNull("an old answer doesn't vouch for it", portraits.forSummary(clog("420 kc", true)));
		answer(portraits, key, -1, null, null);
		assertNull(portraits.forSummary(clog("420 kc", true)));
	}

	@Test
	public void aMissIsNotAskedAgainStraightAwayButSoonerThanAProviderMiss()
	{
		OkHttpClient client = client();
		PlayerPortraits portraits = new PlayerPortraits(client);
		String key = PlayerPortraits.key("CBC");
		answer(portraits, key, 404, null, null);
		portraits.lookedUp(clog("CBC", true));
		assertNull(portraits.forSummary(clog("CBC", true)));
		verify(client, never()).newCall(any(Request.class));
		// Past the portrait's ten minutes, though well inside a provider miss's hour.
		portraits.lane.notFound.put(key, System.currentTimeMillis() - PlayerPortraits.NOT_FOUND_TTL_MS - 1);
		portraits.lookedUp(clog("CBC", true));
		requests(client, 1);
	}

	@Test
	public void overlappingLookupsTrustOnlyTheLatestAnswer() throws IOException
	{
		OkHttpClient client = client();
		PlayerPortraits portraits = new PlayerPortraits(client);
		portraits.lookedUp(clog("420 kc", true));
		answer(portraits, "420 kc", 200, png(56, 80), "\"v1\"");
		assertNotNull(portraits.forSummary(clog("420 kc", true)));

		// A second lookup's check is out when a third lands: one request at a time, nothing new yet.
		portraits.lookedUp(clog("420 kc", true));
		portraits.lookedUp(clog("420 kc", true));
		requests(client, 2);
		// The second lookup's "unchanged" doesn't vouch for the third; a fresh check goes out for it.
		answer(portraits, "420 kc", 304, null, "\"v1\"");
		assertNull(portraits.forSummary(clog("420 kc", true)));
		requests(client, 3);
		// The player withdrew meanwhile: the latest answer is the one that counts.
		answer(portraits, "420 kc", 404, null, null);
		assertNull(portraits.forSummary(clog("420 kc", true)));
	}

	@Test
	public void aNewPortraitFromAnOlderLookupWaitsForTheLatestCheck() throws IOException
	{
		OkHttpClient client = client();
		PlayerPortraits portraits = new PlayerPortraits(client);
		portraits.lookedUp(clog("CBC", true));
		portraits.lookedUp(clog("CBC", true));
		answer(portraits, "CBC", 200, png(62, 80), "\"v2\"");
		assertNull("held, but not confirmed for the latest lookup", portraits.forSummary(clog("CBC", true)));
		assertEquals("\"v2\"", requests(client, 2).get(1).header("If-None-Match"));
		answer(portraits, "CBC", 304, null, "\"v2\"");
		assertNotNull(portraits.forSummary(clog("CBC", true)));
	}

	@Test
	public void lookupCountsStayBounded()
	{
		PlayerPortraits portraits = new PlayerPortraits(client());
		for (int i = 0; i < PlayerPortraits.MAX_GENERATIONS + 50; i++)
		{
			portraits.lookedUp(clog("player " + i, true));
			answer(portraits, "player " + i, 404, null, null);
		}
		assertTrue(portraits.countedLookups() <= PlayerPortraits.MAX_GENERATIONS + 1);
	}

	@Test
	public void onlyASmallPngIsDecoded() throws IOException
	{
		assertNotNull(PlayerPortraits.decode(png(96, 80)));
		assertNotNull(PlayerPortraits.decode(png(256, 256)));
		assertNull(PlayerPortraits.decode(png(257, 80)));
		// A header claiming a huge image is refused before any pixels are allocated.
		assertNull(PlayerPortraits.decode(headerOnlyPng(16384)));
		// Other formats are refused outright, whatever the decoder could make of them.
		ByteArrayOutputStream gif = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "gif", gif);
		assertNull(PlayerPortraits.decode(gif.toByteArray()));
		assertNull(PlayerPortraits.decode("not a png".getBytes()));
		assertNull(PlayerPortraits.decode(Arrays.copyOf(png(56, 80), 40)));
		assertNull(PlayerPortraits.decode(new byte[0]));
		assertNull(PlayerPortraits.decode(null));
		assertNull(PlayerPortraits.decode(new byte[PlayerPortraits.MAX_BYTES + 1]));
	}

	@Test
	public void everySpellingOfANameIsOnePlayer()
	{
		assertEquals("ye ol buck", PlayerPortraits.key(" Ye_Ol-Buck "));
		assertEquals(PlayerPortraits.key("420 kc"), PlayerPortraits.key("420_KC"));
	}
}
