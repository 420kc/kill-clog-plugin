package com.killclog;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Kill Clog syncers' characters for the Player Summary. When a player publishes their character,
 * killclog.com draws it in the game's own style, with their follower beside them, and serves it at
 * {@code /api/player/{rsn}/portrait}. Only lookups that came back from the player's own Kill Clog
 * sync (or the local player's own log) ask for one, so nobody else costs a request.
 *
 * <p>Every lookup re-checks the portrait (an unchanged one answers 304 with no body), and a portrait
 * shows only once the server has confirmed it for that lookup: a player who withdraws or deletes
 * their character is gone from their next lookup on. Each lookup is a new generation for its player,
 * and only an answer to the latest generation confirms; one request per player is out at a time, and
 * an answer to an older one sends a fresh check for the lookup that landed meanwhile.
 */
@Slf4j
@Singleton
class PlayerPortraits
{
	static final int MAX_BYTES = 64 * 1024;
	static final int MAX_SIDE = 256;
	// A miss is often a character still being drawn, or one about to be published, so it is asked
	// about again sooner than a provider miss.
	static final long NOT_FOUND_TTL_MS = 10 * 60 * 1000;
	// Lookup counts kept past this are pruned to names with a portrait held or a check out.
	static final int MAX_GENERATIONS = 2 * HttpUtil.CACHE_CAP;
	private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

	private final OkHttpClient httpClient;
	final HttpUtil.Lane<Portrait> lane = new HttpUtil.Lane<>(new CircuitBreaker("killclog.com portraits"),
		HttpUtil.Lane.RESULT_TTL_MS, NOT_FOUND_TTL_MS);
	// Names whose held portrait the server confirmed since their latest lookup.
	private final Set<String> confirmed = ConcurrentHashMap.newKeySet();
	// Each player's latest lookup, and the lookup the request now out for them was sent for.
	private final Map<String, Integer> generations = new ConcurrentHashMap<>();
	private final Map<String, Integer> asked = new ConcurrentHashMap<>();

	/** A portrait and the ETag it was served with, for the next lookup's re-check. */
	static final class Portrait
	{
		final BufferedImage image;
		@Nullable
		final String etag;

		Portrait(BufferedImage image, @Nullable String etag)
		{
			this.image = image;
			this.etag = etag;
		}
	}

	@Inject
	PlayerPortraits(OkHttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	/** A lookup landed: withhold this player's portrait until the server confirms it again. */
	synchronized void lookedUp(@Nullable ClogResult clog)
	{
		String name = syncedName(clog);
		if (name != null)
		{
			String key = key(name);
			generations.merge(key, 1, Integer::sum);
			if (generations.size() > MAX_GENERATIONS)
			{
				// A name with nothing held and nothing asked needs no count: its next lookup starts over.
				generations.keySet().removeIf(other -> !other.equals(key) && !asked.containsKey(other)
					&& !lane.values.containsKey(other));
			}
			confirmed.remove(key);
			ask(name);
		}
	}

	/** The portrait to draw for this lookup's player: one the server confirmed since the lookup, or null. */
	@Nullable
	BufferedImage forSummary(@Nullable ClogResult clog)
	{
		String name = syncedName(clog);
		if (name == null)
		{
			return null;
		}
		String key = key(name);
		Portrait held = lane.values.get(key);
		if (held != null && confirmed.contains(key))
		{
			return held.image;
		}
		ask(name);
		return null;
	}

	/** The player's name when their log came from their own Kill Clog sync or the local player's own log. */
	@Nullable
	private static String syncedName(@Nullable ClogResult clog)
	{
		if (clog == null || !(clog.isFromKillclog() || clog.isFromLocal()))
		{
			return null;
		}
		String name = clog.getPlayerName();
		return name == null || name.trim().isEmpty() ? null : name.trim();
	}

	/** Sends a check for the player's latest lookup, unless one is already out or the name is resting. */
	private synchronized void ask(String name)
	{
		String key = key(name);
		if (asked.containsKey(key) || lane.resting(key))
		{
			return;
		}
		int generation = generations.getOrDefault(key, 0);
		asked.put(key, generation);
		Portrait held = lane.values.get(key);
		String url = KillClogEndpoint.apiBaseUrl() + "/player/" + HttpUtil.pathSegment(name) + "/portrait";
		CompletableFuture<HttpUtil.BytesResult> request;
		try
		{
			request = HttpUtil.httpGetBytes(httpClient, url, MAX_BYTES, held != null ? held.etag : null);
		}
		catch (RuntimeException e)
		{
			asked.remove(key);
			throw e;
		}
		request.thenAccept(response -> onResponse(name, generation, response.code, response.bytes, response.etag));
	}

	/** How many players' lookups are counted; bounded by {@link #MAX_GENERATIONS} plus those still in use. */
	int countedLookups()
	{
		return generations.size();
	}

	/** The lookup generation the request now out for this player was sent for, or null. */
	@Nullable
	Integer askedFor(String name)
	{
		return asked.get(key(name));
	}

	/**
	 * The answer to a check sent for lookup {@code generation}. 404 (no published character, or one
	 * still being drawn) and 451 (opted out) drop the portrait whatever lookup asked; only an answer for
	 * the latest lookup confirms one; a failure keeps it held but unconfirmed. An answer for an older
	 * lookup sends a fresh check for the latest.
	 */
	synchronized void onResponse(String name, int generation, int code, @Nullable byte[] bytes, @Nullable String etag)
	{
		String key = key(name);
		asked.remove(key);
		boolean latest = generation == generations.getOrDefault(key, 0);
		if (code == 404 || code == 451)
		{
			confirmed.remove(key);
			lane.values.remove(key);
			lane.fetched.remove(key);
			lane.missing(key);
			return;
		}
		Portrait held = lane.values.get(key);
		if (code == 304 && held != null)
		{
			lane.breaker.success();
			lane.ok(key, held);
		}
		else
		{
			BufferedImage image = code == 200 ? decode(bytes) : null;
			if (image == null)
			{
				lane.fail(key);
				return;
			}
			lane.breaker.success();
			lane.ok(key, new Portrait(image, etag));
		}
		if (latest)
		{
			confirmed.add(key);
			confirmed.retainAll(lane.values.keySet());
		}
		else
		{
			ask(name);
		}
	}

	/**
	 * A PNG of at most {@link #MAX_SIDE} pixels a side, or null. The size is read from the header
	 * before any pixels are, so a hostile file can't make the decoder allocate a huge image.
	 */
	@Nullable
	static BufferedImage decode(@Nullable byte[] bytes)
	{
		if (bytes == null || bytes.length < PNG_SIGNATURE.length || bytes.length > MAX_BYTES)
		{
			return null;
		}
		for (int i = 0; i < PNG_SIGNATURE.length; i++)
		{
			if (bytes[i] != PNG_SIGNATURE[i])
			{
				return null;
			}
		}
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
		if (!readers.hasNext())
		{
			return null;
		}
		ImageReader reader = readers.next();
		// Read in memory: ImageIO's default stream can spill to a temporary file on disk.
		try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes)))
		{
			reader.setInput(input, true, true);
			int width = reader.getWidth(0);
			int height = reader.getHeight(0);
			if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE)
			{
				return null;
			}
			return reader.read(0);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Unreadable portrait: {}", e.getMessage());
			return null;
		}
		finally
		{
			reader.dispose();
		}
	}

	/** Space, hyphen and underscore are one separator to Jagex, so every spelling is one player. */
	static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", " ");
	}
}
