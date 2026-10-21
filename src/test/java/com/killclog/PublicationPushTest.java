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
		return PublicationCoordinator.Push.ask(LOGIN, 0, mode, true, NONE);
	}

	private static PublicationCoordinator.Push automatic(String mode)
	{
		return PublicationCoordinator.Push.ask(LOGIN, 0, mode, false, NONE);
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
		PublicationCoordinator.Push first = PublicationCoordinator.Push.ask(LOGIN, 0, "main", true, 1);
		PublicationCoordinator.Push second = PublicationCoordinator.Push.ask(LOGIN, 0, "main", false, 3);
		assertEquals(3, merge(second, first).forCharacter);
		assertEquals(3, merge(first, second).forCharacter);
		assertTrue(merge(first.retriedIn("main", 1), second).retry());
	}

	@Test
	public void pushesOfTwoLoginsNeverMix()
	{
		PublicationCoordinator.Push old = PublicationCoordinator.Push.ask(LOGIN, 0, "main", true, NONE);
		PublicationCoordinator.Push next = PublicationCoordinator.Push.ask(NEXT, 0, "main", false, 5);
		assertSame(next, merge(old, next));
		assertSame(next, merge(next, old));
	}

	@Test
	public void aPushAndItsOwnRetryGoAsOneTheSameEitherWay()
	{
		PublicationCoordinator.Push asked = click("main");
		PublicationCoordinator.Push retry = asked.retriedIn("demonic-pacts", 1);
		assertEquals("demonic-pacts", merge(asked, retry).mode);
		assertEquals("demonic-pacts", merge(retry, asked).mode);
	}

	@Test
	public void aRetrySpendsItsOneRetryForAPushOnlyOfItsOwnGame()
	{
		PublicationCoordinator.Push leagueRetry = click("demonic-pacts").retriedIn("demonic-pacts", 1);
		PublicationCoordinator.Push mainClick = click("main");
		PublicationCoordinator.Push merged = merge(leagueRetry, mainClick);
		assertEquals("the later click leads", "main", merged.mode);
		assertEquals("and goes as itself, with its own retry", false, merged.retry());
		assertTrue(merge(click("main").retriedIn("main", 1), click("main")).retry());
	}

	@Test
	public void theRetrySpentIsTheSameInAnyGroupingAcrossGames()
	{
		// Asked oldest first: a main retry, a League click, a main click.
		PublicationCoordinator.Push mainRetry = click("main").retriedIn("main", 1);
		PublicationCoordinator.Push leagueClick = click("demonic-pacts");
		PublicationCoordinator.Push mainClick = click("main");
		PublicationCoordinator.Push left = merge(merge(mainRetry, leagueClick), mainClick);
		PublicationCoordinator.Push right = merge(mainRetry, merge(leagueClick, mainClick));
		assertEquals("main", left.mode);
		assertEquals(left.mode, right.mode);
		assertEquals(left.retry(), right.retry());
	}
}
