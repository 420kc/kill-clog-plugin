package com.killclog;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankSelectorTest
{
	static HiscoreResult result(AccountType type, HiscoreTable table, int rank, long xp)
	{
		return new HiscoreResult(type, table, Map.of("Zulrah", 123), Map.of("Zulrah", rank),
			Map.of("Clue Scrolls (all)", 42), Map.of("Clue Scrolls (all)", rank),
			Map.of("attack", 99), Map.of("attack", rank), Map.of("attack", xp), 2277, xp, 126, rank);
	}

	@Test
	public void rankProjectionChangesEveryRankAndNothingElse()
	{
		HiscoreResult base = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		base.setBossSectionShifted(true);
		HiscoreResult other = result(AccountType.REGULAR, HiscoreTable.STANDARD, 100, 5000);
		HiscoreResult view = base.withRanks(other);
		assertEquals(100, view.getRank("Zulrah"));
		assertEquals(100, view.getSkillRank("attack"));
		assertEquals(100, view.getActivityRank("Clue Scrolls (all)"));
		assertEquals(100, view.getOverallRank());
		assertEquals(AccountType.IRONMAN, view.getAccountType());
		assertEquals(10000, view.getTotalXp());
		assertEquals(10000, view.getSkillXp("attack"));
		assertEquals(2277, view.getTotalLevel());
		assertEquals(126, view.getCombatLevel());
		assertEquals(99, view.getSkillLevel("attack"));
		assertEquals(123, view.getKc("Zulrah"));
		assertEquals(42, view.getActivityScore("Clue Scrolls (all)"));
		assertTrue(view.isBossSectionShifted());
		assertEquals(10, base.getOverallRank());
		HiscoreResult missing = base.withRanks(null);
		assertEquals(-1, missing.getOverallRank());
		assertEquals(-1, missing.getRank("Zulrah"));
		assertEquals(-1, missing.getSkillRank("attack"));
		assertEquals(-1, missing.getActivityRank("Clue Scrolls (all)"));
		assertEquals(123, missing.getKc("Zulrah"));
	}

	@Test
	public void rowViewTakesTheWholeRowButKeepsThePlayersOwnTypeAndTable()
	{
		HiscoreResult base = result(AccountType.IRONMAN, HiscoreTable.ONE_DEFENCE, 10, 10000);
		HiscoreResult died = result(AccountType.REGULAR, HiscoreTable.STANDARD, 50, 5000);
		died.setBossSectionShifted(true);
		HiscoreResult view = base.withRow(died);
		assertEquals(5000, view.getTotalXp());
		assertEquals(5000, view.getSkillXp("attack"));
		assertEquals(50, view.getOverallRank());
		assertEquals(50, view.getRank("Zulrah"));
		assertEquals(AccountType.IRONMAN, view.getAccountType());
		assertEquals(HiscoreTable.ONE_DEFENCE, view.getHiscoreTable());
		assertTrue(view.isBossSectionShifted());
		assertEquals(10000, base.getTotalXp());
		assertTrue(view.isFrozen());
		assertFalse(base.withRow(base).isFrozen());
		assertFalse(base.withRanks(died).isFrozen());
	}

	@Test
	public void aFrozenRowHidesTodaysLogBesideItButNeverTheRivalsOwn() throws Exception
	{
		LookupTestFixture fixture = new LookupTestFixture();
		HiscoreResult base = fixture.primary.getNativeHiscoreResult();
		ClogResult log = fixture.primary.getNativeClogResult();
		HiscoreResult frozen = base.withRow(result(AccountType.REGULAR, HiscoreTable.STANDARD, 5, 0));
		LookupTestFixture.edt(() -> fixture.primary.setRankView(row -> frozen));
		// A mirror compare started while frozen takes the real log, and reads it as the rival's own:
		// the board view belongs to the looked-up player alone.
		LookupTestFixture.edt(() -> fixture.comparison.doCompareLookup("Blue", "Blue", null));
		assertNull(fixture.primary.getClogResult());
		assertSame(log, fixture.comparison.getCompareClogResult());
		assertSame(log, fixture.primary.getNativeClogResult());
		assertFalse("no setup prompt beside a frozen row", fixture.primary.readsOwnLog());

		LookupTestFixture.edt(() -> fixture.primary.setRankView(row -> row));
		assertSame(log, fixture.primary.getClogResult());
		assertSame(log, fixture.comparison.getCompareClogResult());
		assertTrue(fixture.primary.readsOwnLog());
	}

	@Test
	public void rankChoiceCannotChangeSpecialtyIdentity()
	{
		HiscoreResult base = result(AccountType.REGULAR, HiscoreTable.ONE_DEFENCE, 10, 10000);
		assertEquals(HiscoreTable.ONE_DEFENCE, base.withRanks(null).getHiscoreTable());
		assertEquals(RankLeaderboard.PURE, RankLeaderboard.nativeOf(base));
		assertEquals(RankLeaderboard.NORMAL,
			RankLeaderboard.nativeOf(result(AccountType.GROUP_IRONMAN, HiscoreTable.STANDARD, 10, 10000)));
	}

	@Test
	public void disabledDefaultDoesNotFetchOrChangeResults() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RankSelector selector = new RankSelector((name, table) ->
			{
				throw new AssertionError();
			}, (name, table) -> false, RankSelectorTest::noop);
			HiscoreResult base = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
			selector.update(false, "Blue", base);
			assertFalse(selector.isVisible());
			assertSame(base, selector.view(base));
			selector.select(RankLeaderboard.NORMAL);
			assertSame(base, selector.view(base));
		});
	}

	@Test
	public void aBoardStillLoadingShowsNothingAndASupersededAnswerIsIgnored() throws Exception
	{
		Map<String, CompletableFuture<HiscoreResult>> requests = new HashMap<>();
		RankSelector[] holder = new RankSelector[1];
		HiscoreResult blue = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		SwingUtilities.invokeAndWait(() ->
		{
			RankSelector selector = new RankSelector((name, table) ->
				requests.computeIfAbsent(name + table, key -> new CompletableFuture<>()), (name, table) -> false,
				RankSelectorTest::noop);
			holder[0] = selector;
			selector.update(true, "Blue", blue);
			assertSame(blue, selector.view(blue));
			selector.select(RankLeaderboard.NORMAL);
			assertEquals("a board still loading shows nothing", -1, selector.view(blue).getKc("Zulrah"));
			assertEquals("Loading...", selector.blankNotice(selector.view(blue)));
			selector.select(RankLeaderboard.HARDCORE);
			requests.get("Blue" + RankLeaderboard.NORMAL).complete(result(AccountType.REGULAR, HiscoreTable.STANDARD, 100, 10000));
			requests.get("Blue" + RankLeaderboard.HARDCORE).complete(result(AccountType.REGULAR, HiscoreTable.STANDARD, 7, 10000));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals("the board picked last wins", 7, holder[0].view(blue).getOverallRank());
			assertNull(holder[0].blankNotice(holder[0].view(blue)));
			assertEquals(10, blue.getOverallRank());
		});
	}

	@Test
	public void onlyBoardsWithARowGetATab() throws Exception
	{
		HiscoreResult blue = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		Set<RankLeaderboard> blueRows = EnumSet.of(RankLeaderboard.NORMAL, RankLeaderboard.IRONMAN, RankLeaderboard.HARDCORE);
		RankSelector[] holder = new RankSelector[1];
		SwingUtilities.invokeAndWait(() ->
		{
			// Skiller never answers, so its tab stays for a retry; every other board said "not found".
			holder[0] = new RankSelector((name, table) -> CompletableFuture.completedFuture(
				name.equals("Blue") && blueRows.contains(table) ? blue : null),
				(name, table) -> table != RankLeaderboard.SKILLER, RankSelectorTest::noop);
			holder[0].update(true, "Blue", blue);
		});
		SwingUtilities.invokeAndWait(() -> assertEquals(EnumSet.of(RankLeaderboard.NORMAL, RankLeaderboard.IRONMAN,
			RankLeaderboard.HARDCORE, RankLeaderboard.SKILLER), shown(holder[0])));
	}

	private static Set<RankLeaderboard> shown(RankSelector selector)
	{
		Set<RankLeaderboard> shown = EnumSet.noneOf(RankLeaderboard.class);
		for (RankLeaderboard table : RankLeaderboard.values())
		{
			if (selector.getComponent(table.ordinal()).isVisible()) shown.add(table);
		}
		return shown;
	}

	@Test
	public void resetAndDisableInvalidatePendingRankChanges() throws Exception
	{
		CompletableFuture<HiscoreResult> pending = new CompletableFuture<>();
		RankSelector[] holder = new RankSelector[1];
		HiscoreResult base = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		SwingUtilities.invokeAndWait(() ->
		{
			RankSelector selector = new RankSelector((name, table) -> pending, (name, table) -> false, RankSelectorTest::noop);
			holder[0] = selector;
			selector.update(true, "Blue", base);
			selector.select(RankLeaderboard.NORMAL);
			selector.reset();
			selector.update(true, "Next", base);
			assertEquals(RankLeaderboard.IRONMAN, selector.active());
			selector.update(false, "Next", base);
			pending.complete(result(AccountType.REGULAR, HiscoreTable.STANDARD, 999, 10000));
		});
		SwingUtilities.invokeAndWait(() -> assertSame(base, holder[0].view(base)));
	}

	@Test
	public void aDeadHardcoreReadsTheFrozenRowAndABoardWithoutARowShowsNothing() throws Exception
	{
		HiscoreResult base = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		SwingUtilities.invokeAndWait(() ->
		{
			RankSelector selector = new RankSelector((name, table) -> CompletableFuture.completedFuture(
				table == RankLeaderboard.HARDCORE ? result(AccountType.HARDCORE_IRONMAN, HiscoreTable.STANDARD, 50, 5000) : null),
				(name, table) -> table == RankLeaderboard.ULTIMATE, RankSelectorTest::noop);
			selector.update(true, "Blue", base);
			selector.select(RankLeaderboard.HARDCORE);
			HiscoreResult hardcore = selector.view(base);
			assertEquals(50, hardcore.getOverallRank());
			assertEquals(5000, hardcore.getTotalXp());
			assertEquals(AccountType.IRONMAN, hardcore.getAccountType());
			assertNull(selector.blankNotice(hardcore));
			assertTrue(((JButton) selector.getComponent(2)).getToolTipText().contains("Stats frozen"));

			selector.select(RankLeaderboard.ULTIMATE);
			HiscoreResult ultimate = selector.view(base);
			assertEquals("never another board's stats", -1, ultimate.getKc("Zulrah"));
			assertEquals(-1, ultimate.getSkillLevel("attack"));
			assertEquals(-1, ultimate.getActivityScore("Clue Scrolls (all)"));
			assertEquals(0, ultimate.getTotalLevel());
			assertTrue("today's log hides like a frozen row's", ultimate.isFrozen());
			assertEquals("Not on this leaderboard", selector.blankNotice(ultimate));

			selector.select(RankLeaderboard.PURE);
			assertEquals(-1, selector.view(base).getKc("Zulrah"));
			assertEquals("Unavailable; click to retry", selector.blankNotice(selector.view(base)));
			assertTrue(((JButton) selector.getComponent(5)).getToolTipText().contains("Unavailable"));
		});
	}

	private static void noop()
	{
	}
}
