package com.killclog;

import com.google.gson.Gson;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KillclogSyncGateTest
{
	/** A push as the gate sees it: a click or not, and its game; two waiting go as one the coordinator's way. */
	private static final class Ask
	{
		final boolean manual;
		final String mode;

		Ask(boolean manual, String mode)
		{
			this.manual = manual;
			this.mode = mode;
		}

		static Ask merge(Ask waiting, Ask later)
		{
			Ask lead = later.manual || !waiting.manual ? later : waiting;
			return new Ask(waiting.manual || later.manual, lead.mode);
		}
	}

	private static KillclogSyncGate<Ask> newGate()
	{
		return new KillclogSyncGate<>(ask -> ask.manual, Ask::merge);
	}

	@Test
	public void manualClickBehindAutomaticSyncKeepsManualFeedback()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int automatic = gate.beginAttempt(new Ask(false, null));
		assertEquals(-1, gate.beginAttempt(new Ask(true, null)));
		assertEquals(-1, gate.beginAttempt(new Ask(false, null)));
		gate.complete(automatic);
		assertTrue(gate.consumeQueued().manual);
		assertNull(gate.consumeQueued());
	}

	@Test
	public void backgroundQueueDoesNotBecomeManual()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int manual = gate.beginAttempt(new Ask(true, null));
		assertEquals(-1, gate.beginAttempt(new Ask(false, null)));
		gate.complete(manual);
		assertFalse(gate.consumeQueued().manual);
	}

	@Test
	public void cancellationForgetsManualFeedbackIntent()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int old = gate.beginAttempt(new Ask(false, null));
		gate.beginAttempt(new Ask(true, null));
		gate.cancel();
		gate.beginAttempt(new Ask(false, null));
		gate.complete(old);
		assertFalse(gate.consumeQueued().manual);
	}

	@Test
	public void singleAttemptCompletesCurrent()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.beginAttempt(new Ask(false, null));
		assertTrue(gen >= 0);
		assertTrue(gate.complete(gen));
	}

	@Test
	public void cancelledGenerationCannotGatherALaterAccount()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.beginAttempt(new Ask(false, null));
		assertTrue(gate.isCurrent(gen));
		gate.cancel();
		assertFalse("a queued client callback must stop before gathering identity",
			gate.isCurrent(gen));
	}

	@Test
	public void optOutDuringBlockedPreflightPreventsFinalEnqueue() throws Exception
	{
		KillclogSyncGate<Ask> gate = newGate();
		LocalClogCache cache = new LocalClogCache(
			new Gson(), new NoopScheduledExecutorService());
		int generation = gate.beginAttempt(new Ask(false, null));
		Session epoch = Session.none(0);
		CountDownLatch preflightBlocked = new CountDownLatch(1);
		CountDownLatch releasePreflight = new CountDownLatch(1);
		AtomicBoolean httpEnqueued = new AtomicBoolean();
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			Future<String> result = worker.submit(() ->
			{
				preflightBlocked.countDown();
				releasePreflight.await(1, TimeUnit.SECONDS);
				return cache.commitIfSessionCurrent(epoch,
					() -> gate.commitIfCurrent(generation, () ->
					{
						httpEnqueued.set(true);
						return "http-enqueued";
					}));
			});
			assertTrue(preflightBlocked.await(1, TimeUnit.SECONDS));

			gate.cancel(); // user opts out while rename arbitration is blocked
			releasePreflight.countDown();

			assertNull("revocation must win before the HTTP supplier runs",
				result.get(1, TimeUnit.SECONDS));
			assertFalse(httpEnqueued.get());
		}
		finally
		{
			releasePreflight.countDown();
			worker.shutdownNow();
		}
	}

	@Test
	public void occupiedSlotQueuesTheIntentExactlyOnce()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.beginAttempt(new Ask(false, null));
		assertEquals(-1, gate.beginAttempt(new Ask(false, null)));
		assertEquals(-1, gate.beginAttempt(new Ask(false, null)));
		gate.complete(gen);
		assertTrue(TestViews.consumeQueued(gate));
		assertFalse(TestViews.consumeQueued(gate));
	}

	@Test
	public void optOutOptInDuringLiveRequestStillDeliversThePush()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int oldGen = gate.beginAttempt(new Ask(false, null));

		// Opt-out mid-flight, then opt-in: the immediate push finds the slot
		// occupied and queues.
		gate.cancel();
		assertEquals(-1, gate.beginAttempt(new Ask(false, null)));

		// The old request completes: stale (no chat), but the queued intent
		// survives so the current-era push launches next.
		assertFalse(gate.complete(oldGen));
		assertTrue(TestViews.consumeQueued(gate));
		int newGen = gate.beginAttempt(new Ask(false, null));
		assertTrue(newGen >= 0);
		assertTrue(gate.complete(newGen));
	}

	@Test
	public void cancelForgetsQueuedIntent()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.beginAttempt(new Ask(false, null));
		assertEquals(-1, gate.beginAttempt(new Ask(false, null)));
		gate.cancel();
		gate.complete(gen);
		assertFalse("opt-out must forget the queued push", TestViews.consumeQueued(gate));
	}

	@Test
	public void abortReleasesTheSlot()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int first = gate.beginAttempt(new Ask(false, null));
		assertTrue(first >= 0);
		gate.abortAttempt();
		assertTrue(gate.beginAttempt(new Ask(false, null)) >= 0);
	}

	@Test
	public void staleGenerationCompletesSilentButFreesTheSlot()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.beginAttempt(new Ask(false, null));
		gate.cancel();
		assertFalse(gate.complete(gen));
		assertTrue("slot must free after a stale completion", gate.beginAttempt(new Ask(false, null)) >= 0);
	}

	@Test
	public void aQueuedClickKeepsItsGameOverLaterAutomaticPushes()
	{
		KillclogSyncGate<Ask> gate = newGate();
		gate.beginAttempt(new Ask(false, "main"));
		gate.beginAttempt(new Ask(true, "demonic-pacts"));
		gate.beginAttempt(new Ask(false, "main"));
		Ask intent = gate.consumeQueued();
		assertTrue(intent.manual);
		assertEquals("demonic-pacts", intent.mode);
		gate.beginAttempt(new Ask(false, "main"));
		gate.beginAttempt(new Ask(true, "demonic-pacts"));
		assertEquals("the latest click wins", "main", pairAfterClick(gate));
	}

	private static String pairAfterClick(KillclogSyncGate<Ask> gate)
	{
		gate.beginAttempt(new Ask(true, "main"));
		return gate.consumeQueued().mode;
	}

	@Test
	public void automaticSyncOffStopsOnlyAnAutomaticPushNotYetSent()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int automatic = gate.beginAttempt(new Ask(false, null));
		gate.cancelAutomatic();
		assertNull(gate.commitIfCurrent(automatic, () -> "sent"));
		gate.complete(automatic);

		int click = gate.beginAttempt(new Ask(true, null));
		gate.cancelAutomatic();
		assertEquals("sent", gate.commitIfCurrent(click, () -> "sent"));

		// Behind that click, a queued automatic push is forgotten and a queued click is kept.
		assertEquals(-1, gate.beginAttempt(new Ask(false, null)));
		gate.cancelAutomatic();
		assertFalse(TestViews.consumeQueued(gate));
		assertEquals(-1, gate.beginAttempt(new Ask(true, null)));
		gate.cancelAutomatic();
		assertTrue(TestViews.consumeQueued(gate));
	}
}
