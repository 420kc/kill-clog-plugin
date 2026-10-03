package com.killclog;

import java.awt.image.BufferedImage;
import java.util.Collections;
import javax.swing.ImageIcon;
import net.runelite.client.util.ImageUtil;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AccountDisplayTest
{
	@Test
	public void aFrozenBoardWearsTheModeTheAccountLeft()
	{
		AccountDisplay iron = AccountDisplay.of(AccountType.IRONMAN, HiscoreTable.STANDARD);
		assertEquals("Dead Hardcore", iron.frozenOn(RankLeaderboard.HARDCORE).label());

		AccountDisplay main = AccountDisplay.of(AccountType.REGULAR, HiscoreTable.STANDARD);
		AccountDisplay pure = AccountDisplay.of(AccountType.REGULAR, HiscoreTable.ONE_DEFENCE);
		for (RankLeaderboard board : new RankLeaderboard[]{
			RankLeaderboard.IRONMAN, RankLeaderboard.HARDCORE, RankLeaderboard.ULTIMATE})
		{
			assertEquals("De-ironed", main.frozenOn(board).label());
			// The mode left outranks a pure's or skiller's own table.
			assertEquals("De-ironed", pure.frozenOn(board).label());
		}
	}

	@Test
	public void onlyARecordedFrozenRowWearsTheModeLeft()
	{
		HiscoreResult today = row(AccountType.IRONMAN, 1_500, 20_000_000L, true);
		HiscoreResult atTheDeath = row(AccountType.HARDCORE_IRONMAN, 1_200, 9_000_000L, null);
		AccountDisplay iron = AccountDisplay.of(AccountType.IRONMAN, HiscoreTable.STANDARD);
		assertEquals("Dead Hardcore", iron.shownOn(today.withRow(atTheDeath), RankLeaderboard.HARDCORE).label());

		// Loading, unanswered, or not on the board: the placeholder row trails too, but records nothing.
		HiscoreResult nothing = new HiscoreResult(null, null, null, null, null, null, 0, 0, 0, -1);
		assertSame(iron, iron.shownOn(today.withRow(nothing), RankLeaderboard.HARDCORE));
		// The account's own row, or a row level with it, isn't frozen.
		assertSame(iron, iron.shownOn(today, RankLeaderboard.HARDCORE));
		assertSame(iron, iron.shownOn(today.withRow(today), RankLeaderboard.HARDCORE));
		assertSame(iron, iron.shownOn(null, RankLeaderboard.HARDCORE));
	}

	@Test
	public void aFormerModeNeedsTheLookupsOwnIronmanAnswerToAgree()
	{
		AccountDisplay iron = AccountDisplay.of(AccountType.IRONMAN, HiscoreTable.STANDARD);
		AccountDisplay main = AccountDisplay.of(AccountType.REGULAR, HiscoreTable.STANDARD);
		HiscoreResult hardcoreRow = row(AccountType.HARDCORE_IRONMAN, 1_200, 9_000_000L, null);

		// Ironman now: a dead Hardcore, never a de-ironed account.
		HiscoreResult ironNow = row(AccountType.IRONMAN, 1_500, 20_000_000L, true).withRow(hardcoreRow);
		assertEquals("Dead Hardcore", iron.shownOn(ironNow, RankLeaderboard.HARDCORE).label());
		assertSame(main, main.shownOn(ironNow, RankLeaderboard.HARDCORE));
		// An Ultimate who stepped down is an Ironman now, and keeps the Ironman helm.
		assertSame(iron, iron.shownOn(ironNow, RankLeaderboard.ULTIMATE));
		// Ironman row trailing: de-ironed, never a dead Hardcore.
		HiscoreResult leftIron = row(AccountType.REGULAR, 1_500, 20_000_000L, false).withRow(hardcoreRow);
		assertEquals("De-ironed", main.shownOn(leftIron, RankLeaderboard.HARDCORE).label());
		assertSame(iron, iron.shownOn(leftIron, RankLeaderboard.HARDCORE));
		// A lookup whose regular or Ironman board didn't answer proves neither.
		HiscoreResult unproven = row(AccountType.REGULAR, 1_500, 20_000_000L, null).withRow(hardcoreRow);
		assertSame(iron, iron.shownOn(unproven, RankLeaderboard.HARDCORE));
		assertSame(main, main.shownOn(unproven, RankLeaderboard.HARDCORE));
		// The answer travels with the lookup into its projections.
		assertEquals(Boolean.TRUE, row(AccountType.IRONMAN, 1, 1L, true).withRanks(null).getIronmanNow());
	}

	@Test
	public void everyOtherFrozenBoardKeepsTheAccountsOwnBadge()
	{
		for (AccountType type : AccountType.values())
		{
			AccountDisplay display = AccountDisplay.of(type, HiscoreTable.STANDARD);
			assertSame(display, display.frozenOn(null));
			for (RankLeaderboard board : RankLeaderboard.values())
			{
				boolean deadHardcore = type == AccountType.IRONMAN && board == RankLeaderboard.HARDCORE;
				boolean deIroned = type == AccountType.REGULAR && (board == RankLeaderboard.IRONMAN
					|| board == RankLeaderboard.HARDCORE || board == RankLeaderboard.ULTIMATE);
				if (!deadHardcore && !deIroned)
				{
					// An Ultimate who stepped down to Ironman is one of these.
					assertSame(type + " on " + board, display, display.frozenOn(board));
				}
			}
		}
		assertNull(AccountDisplay.of(AccountType.IRONMAN, HiscoreTable.STANDARD).former());
	}

	@Test
	public void aFormerBadgeIsTheHelmWithItsMarkDrawnOver()
	{
		AccountDisplay[] shown = {
			AccountDisplay.of(AccountType.IRONMAN, HiscoreTable.STANDARD).frozenOn(RankLeaderboard.HARDCORE),
			AccountDisplay.of(AccountType.REGULAR, HiscoreTable.STANDARD).frozenOn(RankLeaderboard.IRONMAN)};
		// The red Hardcore helm under the skull; the grey Ironman helm under the circle and slash.
		AccountType[] helms = {AccountType.HARDCORE_IRONMAN, AccountType.IRONMAN};
		String[] marks = {"dead-hardcore-overlay.png", "de-ironed-overlay.png"};
		for (int i = 0; i < shown.length; i++)
		{
			AccountDisplay display = shown[i];
			AccountDisplay.Former former = display.former();
			BufferedImage helm = AccountBadgeResolver.badge(AccountDisplay.of(helms[i], HiscoreTable.STANDARD));
			BufferedImage mark = ImageUtil.loadImageResource(AccountBadgeResolver.class, marks[i]);
			BufferedImage badge = AccountBadgeResolver.badge(display);
			assertNotNull(badge);
			assertEquals(helm.getWidth(), badge.getWidth());
			assertEquals(helm.getHeight(), badge.getHeight());
			int marked = 0;
			for (int y = 0; y < helm.getHeight(); y++)
			{
				for (int x = 0; x < helm.getWidth(); x++)
				{
					int alpha = mark.getRGB(x, y) >>> 24;
					if (alpha == 255)
					{
						assertEquals(former + " at " + x + "," + y, mark.getRGB(x, y), badge.getRGB(x, y));
						marked++;
					}
					else if (alpha == 0)
					{
						assertEquals(former + " at " + x + "," + y, helm.getRGB(x, y), badge.getRGB(x, y));
					}
				}
			}
			assertTrue(marked > 0);
			// The name row draws it at the same 15px as every other account badge.
			ImageIcon icon = AccountBadgeResolver.labelIcon(display);
			assertEquals(15, icon.getIconHeight());
		}
	}

	private static HiscoreResult row(AccountType type, int totalLevel, long totalXp, Boolean ironmanNow)
	{
		HiscoreResult row = new HiscoreResult(type, Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), totalLevel, totalXp, 100, 1);
		row.setIronmanNow(ironmanNow);
		return row;
	}
}
