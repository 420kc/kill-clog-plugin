package com.killclog;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuneProfileSingleFlightTest
{
	private final Map<String, CompletableFuture<String>> flights = new ConcurrentHashMap<>();

	@Test
	public void immediateCompletionRemovesRegisteredFlightWithoutRecursiveUpdate()
	{
		assertEquals("ready", RuneProfileService.singleFlightLookup(flights, "player",
			() -> CompletableFuture.completedFuture("ready")).join());
		assertTrue(flights.isEmpty());

		CompletableFuture<String> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IllegalStateException("test failure"));
		assertTrue(RuneProfileService.singleFlightLookup(flights, "player", () -> failed)
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
		CompletableFuture<String> first = RuneProfileService.singleFlightLookup(flights, "player", start);
		CompletableFuture<String> second = RuneProfileService.singleFlightLookup(flights, "player", start);
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
		CompletableFuture<String> first = RuneProfileService.singleFlightLookup(flights, "player", () -> source);
		CompletableFuture<String> replacement = new CompletableFuture<>();
		flights.put("player", replacement);
		source.completeExceptionally(new IllegalStateException("test failure"));
		assertTrue(first.isCompletedExceptionally());
		assertSame(replacement, flights.get("player"));
	}
}
