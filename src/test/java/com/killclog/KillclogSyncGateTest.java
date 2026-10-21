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
		return new KillclogSyncGate<>(Ask::merge);
	}

	@Test
	public void manualClickBehindAutomaticSyncKeepsManualFeedback()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int automatic = gate.begin(new Ask(false, null), gate.era());
		assertEquals(-1, gate.begin(new Ask(true, null), gate.era()));
		assertEquals(-1, gate.begin(new Ask(false, null), gate.era()));
		gate.release(automatic);
		assertTrue(gate.takeQueued().manual);
		assertNull(gate.takeQueued());
	}

	@Test
	public void backgroundQueueDoesNotBecomeManual()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int manual = gate.begin(new Ask(true, null), gate.era());
		assertEquals(-1, gate.begin(new Ask(false, null), gate.era()));
		gate.release(manual);
		assertFalse(gate.takeQueued().manual);
	}

	@Test
	public void cancellationForgetsManualFeedbackIntent()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int old = gate.begin(new Ask(false, null), gate.era());
		gate.begin(new Ask(true, null), gate.era());
		gate.cancel();
		gate.begin(new Ask(false, null), gate.era());
		gate.release(old);
		assertFalse(gate.takeQueued().manual);
	}

	@Test
	public void singleAttemptCompletesCurrent()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.begin(new Ask(false, null), gate.era());
		assertTrue(gen >= 0);
		assertTrue(gate.release(gen));
	}

	@Test
	public void cancelledGenerationCannotGatherALaterAccount()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.begin(new Ask(false, null), gate.era());
		assertTrue(gate.live(gen));
		gate.cancel();
		assertFalse("a queued client callback must stop before gathering identity",
			gate.live(gen));
	}

	@Test
	public void optOutDuringBlockedPreflightPreventsFinalEnqueue() throws Exception
	{
		KillclogSyncGate<Ask> gate = newGate();
		LocalClogCache cache = new LocalClogCache(
			new Gson(), new NoopScheduledExecutorService());
		int generation = gate.begin(new Ask(false, null), gate.era());
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
		int gen = gate.begin(new Ask(false, null), gate.era());
		assertEquals(-1, gate.begin(new Ask(false, null), gate.era()));
		assertEquals(-1, gate.begin(new Ask(false, null), gate.era()));
		gate.release(gen);
		assertTrue(TestViews.consumeQueued(gate));
		assertFalse(TestViews.consumeQueued(gate));
	}

	@Test
	public void optOutOptInDuringLiveRequestStillDeliversThePush()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int oldGen = gate.begin(new Ask(false, null), gate.era());

		// Opt-out mid-flight, then opt-in: the immediate push finds the slot
		// occupied and queues.
		gate.cancel();
		assertEquals(-1, gate.begin(new Ask(false, null), gate.era()));

		// The old request completes: stale (no chat), but the queued intent
		// survives so the current-era push launches next.
		assertFalse(gate.release(oldGen));
		assertTrue(TestViews.consumeQueued(gate));
		int newGen = gate.begin(new Ask(false, null), gate.era());
		assertTrue(newGen >= 0);
		assertTrue(gate.release(newGen));
	}

	@Test
	public void cancelForgetsQueuedIntent()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.begin(new Ask(false, null), gate.era());
		assertEquals(-1, gate.begin(new Ask(false, null), gate.era()));
		gate.cancel();
		gate.release(gen);
		assertFalse("opt-out must forget the queued push", TestViews.consumeQueued(gate));
	}

	@Test
	public void abortReleasesTheSlot()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int first = gate.begin(new Ask(false, null), gate.era());
		assertTrue(first > 0);
		gate.release(first);
		assertTrue(gate.begin(new Ask(false, null), gate.era()) >= 0);
	}

	@Test
	public void staleGenerationCompletesSilentButFreesTheSlot()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int gen = gate.begin(new Ask(false, null), gate.era());
		gate.cancel();
		assertFalse(gate.release(gen));
		assertTrue("slot must free after a stale completion", gate.begin(new Ask(false, null), gate.era()) >= 0);
	}

	@Test
	public void aQueuedClickKeepsItsGameOverLaterAutomaticPushes()
	{
		KillclogSyncGate<Ask> gate = newGate();
		gate.begin(new Ask(false, "main"), gate.era());
		gate.begin(new Ask(true, "demonic-pacts"), gate.era());
		gate.begin(new Ask(false, "main"), gate.era());
		Ask intent = gate.takeQueued();
		assertTrue(intent.manual);
		assertEquals("demonic-pacts", intent.mode);
		gate.begin(new Ask(false, "main"), gate.era());
		gate.begin(new Ask(true, "demonic-pacts"), gate.era());
		assertEquals("the latest click wins", "main", pairAfterClick(gate));
	}

	private static String pairAfterClick(KillclogSyncGate<Ask> gate)
	{
		gate.begin(new Ask(true, "main"), gate.era());
		return gate.takeQueued().mode;
	}

	@Test
	public void automaticSyncOffStopsOnlyAnAutomaticPushNotYetSent()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int automatic = gate.begin(new Ask(false, null), gate.era());
		gate.silence(ask -> !ask.manual);
		assertNull(gate.commitIfCurrent(automatic, () -> "sent"));
		gate.release(automatic);

		int click = gate.begin(new Ask(true, null), gate.era());
		gate.silence(ask -> !ask.manual);
		assertEquals("sent", gate.commitIfCurrent(click, () -> "sent"));

		// Behind that click, a queued automatic push is forgotten and a queued click is kept.
		assertEquals(-1, gate.begin(new Ask(false, null), gate.era()));
		gate.silence(ask -> !ask.manual);
		assertFalse(TestViews.consumeQueued(gate));
		assertEquals(-1, gate.begin(new Ask(true, null), gate.era()));
		gate.silence(ask -> !ask.manual);
		assertTrue(TestViews.consumeQueued(gate));
	}

	@Test
	public void aPushFromAPassedEraNeverClaimsTheSlot()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int asked = gate.era();
		gate.cancel();
		assertEquals(KillclogSyncGate.STALE, gate.begin(new Ask(true, null), asked));
		assertFalse(gate.busy());
		assertNull("nor waits for it", gate.takeQueued());
	}

	@Test
	public void aStaleReleaseNeverFreesANewerAttempt()
	{
		KillclogSyncGate<Ask> gate = newGate();
		int old = gate.begin(new Ask(false, null), gate.era());
		gate.release(old);
		int newer = gate.begin(new Ask(true, null), gate.era());
		assertFalse(gate.release(old));
		assertTrue("the newer attempt still holds the slot", gate.busy());
		assertTrue(gate.live(newer));
	}
}
