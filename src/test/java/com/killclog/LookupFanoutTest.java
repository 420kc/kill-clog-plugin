package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class LookupFanoutTest
{
	@Test
	public void testGenerationStateMachine()
	{
		LookupFanout fanout = new LookupFanout(null, null, null, null);
		assertFalse(fanout.isInFlight());

		int first = fanout.begin();
		assertTrue(fanout.isInFlight());
		assertTrue(fanout.current(first));

		fanout.settle();
		assertFalse(fanout.isInFlight());
		// Settling resolves the generation without staling its callbacks:
		// clog and CA lanes may still deliver after the hiscore resolves.
		assertTrue(fanout.current(first));

		int second = fanout.begin();
		assertFalse(fanout.current(first));
		assertTrue(fanout.current(second));

		// Cancel abandons an in-flight generation and stales its callbacks.
		fanout.invalidate();
		assertFalse(fanout.isInFlight());
		assertFalse(fanout.current(second));

		// Optional legs can outlive settlement and must also be invalidated.
		int third = fanout.begin();
		fanout.settle();
		fanout.invalidate();
		assertFalse(fanout.current(third));
	}

	@Test
	public void testTransportPolicyPins()
	{
		// The compare side once lost these by re-implementing transport;
		// timeout policy now has exactly one home.
		assertEquals(10, LookupFanout.CA_TIMEOUT_SECONDS);
		assertEquals(15, LookupFanout.HISCORE_TIMEOUT_SECONDS);
	}
}
