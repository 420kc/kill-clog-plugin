package com.killclog;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpSingleFlightTest
{
	private final Map<String, CompletableFuture<String>> flights = new ConcurrentHashMap<>();

	@Test
	public void timeoutCannotSuppressSourceCompletionOrServiceCleanup() throws Exception
	{
		CompletableFuture<String> transport = new CompletableFuture<>();
		AtomicInteger cached = new AtomicInteger();
		CompletableFuture<String> source = transport.thenApply(value ->
		{
			cached.incrementAndGet();
			return value;
		});
		CompletableFuture<String> first = HttpUtil.singleFlightLookup(flights, "player", () -> source);
		CompletableFuture<String> second = HttpUtil.singleFlightLookup(flights, "player", () -> source);
		assertNull(first.completeOnTimeout(null, 1, TimeUnit.MILLISECONDS).get(1, TimeUnit.SECONDS));
		assertFalse(source.isDone());
		assertFalse(second.isDone());
		transport.complete("late data");
		assertEquals("late data", second.join());
		assertEquals(1, cached.get());
		assertTrue(flights.isEmpty());
		assertEquals("fresh", HttpUtil.singleFlightLookup(flights, "player",
			() -> CompletableFuture.completedFuture("fresh")).join());
	}

	@Test
	public void immediateCompletionRemovesRegisteredFlightWithoutRecursiveUpdate()
	{
		assertEquals("ready", HttpUtil.singleFlightLookup(flights, "player",
			() -> CompletableFuture.completedFuture("ready")).join());
		assertTrue(flights.isEmpty());

		CompletableFuture<String> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IllegalStateException("test failure"));
		assertTrue(HttpUtil.singleFlightLookup(flights, "player", () -> failed)
			.isCompletedExceptionally());
		assertTrue(flights.isEmpty());
	}

	@Test
	public void pendingRequestsShareOneFlightAndReleaseItOnCompletion()
	{
		AtomicInteger starts = new AtomicInteger();
		CompletableFuture<String> source = new CompletableFuture<>();
		java.util.function.Supplier<CompletableFuture<String>> start = () ->
		{
			starts.incrementAndGet();
			return source;
		};
		CompletableFuture<String> first = HttpUtil.singleFlightLookup(flights, "player", start);
		CompletableFuture<String> second = HttpUtil.singleFlightLookup(flights, "player", start);
		assertEquals(1, starts.get());
		assertSame(source, flights.get("player"));
		assertFalse(first.isDone());
		assertFalse(second.isDone());
		source.complete("ready");
		assertEquals("ready", first.join());
		assertEquals("ready", second.join());
		assertTrue(flights.isEmpty());
	}

	@Test
	public void lateExceptionalCompletionCannotRemoveReplacementFlight()
	{
		CompletableFuture<String> source = new CompletableFuture<>();
		CompletableFuture<String> first = HttpUtil.singleFlightLookup(flights, "player", () -> source);
		CompletableFuture<String> replacement = new CompletableFuture<>();
		flights.put("player", replacement);
		source.completeExceptionally(new IllegalStateException("test failure"));
		assertTrue(first.isCompletedExceptionally());
		assertSame(replacement, flights.get("player"));
	}
}
