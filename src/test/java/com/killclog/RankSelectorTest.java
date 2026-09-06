package com.killclog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
			}, RankSelectorTest::noop);
			HiscoreResult base = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
			selector.update(false, "Blue", base, null, null);
			assertFalse(selector.isVisible());
			assertSame(base, selector.view(base));
			selector.select(RankLeaderboard.NORMAL);
			assertSame(base, selector.view(base));
		});
	}

	@Test
	public void comparisonUsesOneBoardAndIgnoresSupersededResponses() throws Exception
	{
		List<CompletableFuture<HiscoreResult>> requests = new ArrayList<>();
		List<RankLeaderboard> tables = new ArrayList<>();
		RankSelector[] holder = new RankSelector[1];
		HiscoreResult blue = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		HiscoreResult red = result(AccountType.REGULAR, HiscoreTable.STANDARD, 20, 10000);
		SwingUtilities.invokeAndWait(() ->
		{
			RankSelector selector = new RankSelector((name, table) ->
			{
				tables.add(table);
				CompletableFuture<HiscoreResult> request = new CompletableFuture<>();
				requests.add(request);
				return request;
			}, RankSelectorTest::noop);
			holder[0] = selector;
			selector.update(true, "Blue", blue, "Red", red);
			assertEquals(List.of(RankLeaderboard.IRONMAN), tables);
			assertSame(blue, selector.view(blue));
			assertEquals(-1, selector.view(red).getOverallRank());
			selector.select(RankLeaderboard.NORMAL);
			assertEquals(List.of(RankLeaderboard.IRONMAN, RankLeaderboard.NORMAL), tables);
			assertSame(red, selector.view(red));
			assertEquals(-1, selector.view(blue).getOverallRank());
			requests.get(0).complete(result(AccountType.IRONMAN, HiscoreTable.STANDARD, 1, 10000));
			requests.get(1).complete(result(AccountType.REGULAR, HiscoreTable.STANDARD, 100, 10000));
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(100, holder[0].view(blue).getOverallRank());
			assertEquals(20, holder[0].view(red).getOverallRank());
			assertEquals(10, blue.getOverallRank());
		});
	}

	@Test
	public void resetAndDisableInvalidatePendingRankChanges() throws Exception
	{
		CompletableFuture<HiscoreResult> pending = new CompletableFuture<>();
		RankSelector[] holder = new RankSelector[1];
		HiscoreResult base = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		SwingUtilities.invokeAndWait(() ->
		{
			RankSelector selector = new RankSelector((name, table) -> pending, RankSelectorTest::noop);
			holder[0] = selector;
			selector.update(true, "Blue", base, null, null);
			selector.select(RankLeaderboard.NORMAL);
			selector.reset();
			selector.update(true, "Next", base, null, null);
			assertEquals(RankLeaderboard.IRONMAN, selector.active());
			selector.update(false, "Next", base, null, null);
			pending.complete(result(AccountType.REGULAR, HiscoreTable.STANDARD, 999, 10000));
		});
		SwingUtilities.invokeAndWait(() -> assertSame(base, holder[0].view(base)));
	}

	@Test
	public void historicalAndMissingRanksAreLabelledWithoutChangingStats() throws Exception
	{
		RankSelector[] holder = new RankSelector[1];
		HiscoreResult base = result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		SwingUtilities.invokeAndWait(() ->
		{
			holder[0] = new RankSelector((name, table) -> CompletableFuture.completedFuture(
				table == RankLeaderboard.HARDCORE ? result(AccountType.HARDCORE_IRONMAN, HiscoreTable.STANDARD, 50, 5000) : null), RankSelectorTest::noop);
			holder[0].update(true, "Blue", base, null, null);
			holder[0].select(RankLeaderboard.HARDCORE);
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(50, holder[0].view(base).getOverallRank());
			assertEquals(10000, holder[0].view(base).getTotalXp());
			assertTrue(((JButton) holder[0].getComponent(2)).getToolTipText().contains("Historical ranks"));
			holder[0].select(RankLeaderboard.ULTIMATE);
		});
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(-1, holder[0].view(base).getOverallRank());
			assertTrue(((JButton) holder[0].getComponent(3)).getToolTipText().contains("Ranks unavailable"));
		});
	}

	private static void noop()
	{
	}
}
