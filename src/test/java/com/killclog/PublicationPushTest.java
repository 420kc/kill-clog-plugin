package com.killclog;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PublicationPushTest
{
	private static final Session LOGIN = new Session(1, "Dylan", 42);
	private static final Session NEXT = new Session(3, "Other", 99);
	private static final int NONE = PublicationCoordinator.NONE;

	private static PublicationCoordinator.Push click(String mode)
	{
		return PublicationCoordinator.Push.ask(LOGIN, mode, true, NONE);
	}

	private static PublicationCoordinator.Push automatic(String mode)
	{
		return PublicationCoordinator.Push.ask(LOGIN, mode, false, NONE);
	}

	private static PublicationCoordinator.Push merge(PublicationCoordinator.Push one, PublicationCoordinator.Push other)
	{
		return PublicationCoordinator.Push.merge(one, other);
	}

	@Test
	public void twoPushesGoAsOneTheSameWayInWhicheverOrderAndGroupingTheyMeet()
	{
		PublicationCoordinator.Push league = click("demonic-pacts");
		PublicationCoordinator.Push main = click("main");
		PublicationCoordinator.Push quiet = automatic("demonic-pacts");
		assertEquals("the later click leads", "main", merge(league, main).mode);
		assertEquals("in either order", "main", merge(main, league).mode);
		// An automatic push merged in between never makes the earlier click look later.
		assertEquals("main", merge(merge(league, quiet), main).mode);
		assertEquals("main", merge(league, merge(quiet, main)).mode);
		assertTrue(merge(quiet, league).manual);
	}

	@Test
	public void theNewerCharacterRequestAndOneRetryAreKept()
	{
		PublicationCoordinator.Push first = PublicationCoordinator.Push.ask(LOGIN, "main", true, 1);
		PublicationCoordinator.Push second = PublicationCoordinator.Push.ask(LOGIN, "main", false, 3);
		assertEquals(3, merge(second, first).forCharacter);
		assertEquals(3, merge(first, second).forCharacter);
		assertTrue(merge(first.retriedIn("main"), second).retry);
	}

	@Test
	public void pushesOfTwoLoginsNeverMix()
	{
		PublicationCoordinator.Push old = PublicationCoordinator.Push.ask(LOGIN, "main", true, NONE);
		PublicationCoordinator.Push next = PublicationCoordinator.Push.ask(NEXT, "main", false, 5);
		assertSame(next, merge(old, next));
		assertSame(next, merge(next, old));
	}
}
