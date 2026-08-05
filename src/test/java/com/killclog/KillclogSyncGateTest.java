package com.killclog;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillclogSyncGateTest
{
	@Test
	public void singleAttemptCompletesCurrent()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		int gen = gate.beginAttempt();
		assertTrue(gen >= 0);
		assertTrue(gate.complete(gen));
	}

	@Test
	public void occupiedSlotQueuesTheIntentExactlyOnce()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		int gen = gate.beginAttempt();
		assertEquals(-1, gate.beginAttempt());
		assertEquals(-1, gate.beginAttempt());
		gate.complete(gen);
		assertTrue(gate.consumeQueued());
		assertFalse(gate.consumeQueued());
	}

	@Test
	public void optOutOptInDuringLiveRequestStillDeliversThePush()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		int oldGen = gate.beginAttempt();

		// Opt-out mid-flight, then opt-in: the immediate push finds the slot
		// occupied and queues.
		gate.cancel();
		assertEquals(-1, gate.beginAttempt());

		// The old request completes: stale (no chat), but the queued intent
		// survives so the current-era push launches next.
		assertFalse(gate.complete(oldGen));
		assertTrue(gate.consumeQueued());
		int newGen = gate.beginAttempt();
		assertTrue(newGen >= 0);
		assertTrue(gate.complete(newGen));
	}

	@Test
	public void cancelForgetsQueuedIntent()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		int gen = gate.beginAttempt();
		assertEquals(-1, gate.beginAttempt());
		gate.cancel();
		gate.complete(gen);
		assertFalse("opt-out must forget the queued push", gate.consumeQueued());
	}

	@Test
	public void abortReleasesTheSlot()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		int first = gate.beginAttempt();
		assertTrue(first >= 0);
		gate.abortAttempt();
		assertTrue(gate.beginAttempt() >= 0);
	}

	@Test
	public void retryCreditIsOnePerEpisodeAndNewEpisodesStartFresh()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		// First 409 takes the credit; the second finds it consumed.
		assertTrue(gate.consumeRetryCredit());
		assertFalse("second 409 in the episode must not retry", gate.consumeRetryCredit());
		// The terminal failure restores the credit; a later independent
		// episode starts with its retry available again.
		gate.restoreRetryCredit();
		assertTrue("a new episode must start with credit", gate.consumeRetryCredit());
	}

	@Test
	public void cancelRestoresRetryCredit()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		assertTrue(gate.consumeRetryCredit());
		gate.cancel();
		assertTrue("opt-out/shutdown must not starve the next episode", gate.consumeRetryCredit());
	}

	@Test
	public void abortRestoresRetryCredit()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		assertTrue(gate.consumeRetryCredit());
		gate.abortAttempt();
		assertTrue("a pre-dispatch abort ends the episode", gate.consumeRetryCredit());
	}

	@Test
	public void staleGenerationCompletesSilentButFreesTheSlot()
	{
		KillclogSyncGate gate = new KillclogSyncGate();
		int gen = gate.beginAttempt();
		gate.cancel();
		assertFalse(gate.complete(gen));
		assertTrue("slot must free after a stale completion", gate.beginAttempt() >= 0);
	}
}
