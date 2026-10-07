package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;
import static org.junit.Assert.*;

public class PvmSummaryTooltipTest
{
	@Test
	public void megaRareHoverNamesUseFullDisplayNames()
	{
		assertArrayEquals(new String[]{"Twisted Bow", "Scythe of Vitur", "Tumeken's Shadow"},
			PanelData.MEGARARE_ITEM_NAMES);
	}

	@Test
	public void testSlayerXpAlwaysReadsFullAmount()
	{
		// Solo PvM summary: exact digits at every magnitude, never rounded.
		// Max slayer xp is 200,000,000 and the tooltip has the width for it.
		assertEquals("350,000", PvmSummaryTooltip.slayerXpText(350_000L));
		assertEquals("9,741,203", PvmSummaryTooltip.slayerXpText(9_741_203L));
		assertEquals("14,200,000", PvmSummaryTooltip.slayerXpText(14_200_000L));
		assertEquals("200,000,000", PvmSummaryTooltip.slayerXpText(200_000_000L));
		assertEquals("--", PvmSummaryTooltip.slayerXpText(0L));
		assertEquals("--", PvmSummaryTooltip.slayerXpText(-1L));
	}

	@Test
	public void theTierRowWaitsForATierThenTakesTheCombatRowsRoom()
	{
		Map<CombatAchievementTier, Integer> totals = new java.util.EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			totals.put(tier, tier.totalTasks());
		}
		Map<CombatAchievementTier, Integer> easyDone = new java.util.EnumMap<>(CombatAchievementTier.class);
		easyDone.put(CombatAchievementTier.EASY, CombatAchievementTier.EASY.totalTasks());

		// The PvM rows stand above the band, so the room shows in the card's height.
		int noData = pvmCard(null).getPreferredSize().height;
		int noTier = pvmCard(CombatAchievementResult.of(Collections.emptyMap(), totals)).getPreferredSize().height;
		int easy = pvmCard(CombatAchievementResult.of(easyDone, totals)).getPreferredSize().height;

		assertEquals(noData, noTier);
		// 4 px above, the 22 px row, 6 px below: the combat level row's room.
		assertEquals(4 + 22 + 6, easy - noData);
	}

	@Test
	public void raidRowsStillAnswerClicksUnderTheTierRow() throws ReflectiveOperationException
	{
		Map<CombatAchievementTier, Integer> totals = new java.util.EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			totals.put(tier, tier.totalTasks());
		}
		Map<CombatAchievementTier, Integer> easyDone = new java.util.EnumMap<>(CombatAchievementTier.class);
		easyDone.put(CombatAchievementTier.EASY, CombatAchievementTier.EASY.totalTasks());
		PvmSummaryTooltip plain = pvmCard(null);
		PvmSummaryTooltip tiered = pvmCard(CombatAchievementResult.of(easyDone, totals));

		int plainTop = paintedRaidTop(plain);
		int tieredTop = paintedRaidTop(tiered);
		// The tier row's room moves the raid rows down, and their clicks move with them.
		assertEquals(plainTop + 4 + 22 + 6, tieredTop);
		assertEquals(-1, tiered.raidAt(tieredTop - 1));
		assertEquals(0, tiered.raidAt(tieredTop));
		assertEquals(2, tiered.raidAt(tieredTop + 2 * 14 + 13));
		assertEquals(-1, tiered.raidAt(tieredTop + 3 * 14));
	}

	private static int paintedRaidTop(PvmSummaryTooltip tip) throws ReflectiveOperationException
	{
		Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		tip.paint(g);
		g.dispose();
		// The first raid row's top, read through the card's own hit test.
		for (int y = 0; y < size.height; y++)
		{
			if (tip.raidAt(y) == 0)
			{
				return y;
			}
		}
		return -1;
	}

	private static PvmSummaryTooltip pvmCard(CombatAchievementResult ca)
	{
		PvmSummaryTooltip tip = new PvmSummaryTooltip();
		tip.setData(126, 12345, 20, 60, "Zulrah", 5000);
		if (ca != null)
		{
			tip.setCombatAchievements(ca, null);
		}
		return tip;
	}

	@Test
	public void combatLevelReadsLikeVanillasExactCombatLevel()
	{
		assertEquals("83.925", PvmSummaryTooltip.combatText(83.925));
		assertEquals("126.1", PvmSummaryTooltip.combatText(126.1));
		assertEquals("3", PvmSummaryTooltip.combatText(3.0));
		assertEquals("104.475", PvmSummaryTooltip.combatText(104.47500000000001));
		assertEquals("--", PvmSummaryTooltip.combatText(-1));
		assertEquals("--", PvmSummaryTooltip.combatText(0));
	}

	@Test
	public void solHereditReplacesKcWithExactPositiveGlory()
	{
		assertTrue(ColosseumGlory.replacesKc("Sol Heredit"));
		assertFalse(ColosseumGlory.replacesKc("Fortis Colosseum"));
		assertEquals(33_333, ColosseumGlory.score(hiscoreWithGlory(33_333)));
		assertEquals("33,333", ColosseumGlory.format(33_333));
	}

	@Test
	public void absentGloryFollowsTheShibuiOmissionRule()
	{
		assertFalse(ColosseumGlory.isVisible(-1));
		assertFalse(ColosseumGlory.isVisible(0));
		assertTrue(ColosseumGlory.isVisible(1));
		assertEquals("--", ColosseumGlory.format(0));
		assertEquals(-1, ColosseumGlory.score(null));
	}

	@Test
	public void absentGloryFallsBackToKnownSolKc()
	{
		assertTrue(ColosseumGlory.hasHeaderScore(0, 17));
		assertEquals("KC: ", ColosseumGlory.headerLabel(0));
		assertEquals("17", ColosseumGlory.headerValue(0, 17));
		assertEquals(ColosseumGlory.LABEL, ColosseumGlory.headerLabel(33_333));
		assertEquals("33,333", ColosseumGlory.headerValue(33_333, 17));
	}

	@Test
	public void raidRowsAreHitTestedOnlyWhereTheyWerePainted()
	{
		PvmSummaryTooltip tip = card();
		// Before the first paint there is no raid section to answer.
		assertEquals(-1, tip.raidAt(200));

		painted(tip);
		assertEquals(-1, tip.raidAt(0));
		int top = firstRaidY(tip);
		assertEquals(-1, tip.raidAt(top - 1));
		for (int row = 0; row < 3; row++)
		{
			assertEquals(row, tip.raidAt(top + row * NativeTooltip.LINE_HEIGHT));
			assertEquals(row, tip.raidAt(top + row * NativeTooltip.LINE_HEIGHT + NativeTooltip.LINE_HEIGHT - 1));
		}
		// The mega rare sprites below the rows are not part of them.
		assertEquals(-1, tip.raidAt(top + 3 * NativeTooltip.LINE_HEIGHT));
	}

	@Test
	public void aHoveredRaidTurnsItsOwnLabelWhiteAndNothingElse()
	{
		PvmSummaryTooltip tip = painted(card());
		BufferedImage resting = render(tip);
		int top = firstRaidY(tip);
		int orange = NativeTooltip.OSRS_ORANGE.getRGB();
		int white = Color.WHITE.getRGB();
		for (int row = 0; row < 3; row++)
		{
			move(tip, 5, top + row * NativeTooltip.LINE_HEIGHT + 2);
			assertEquals(row, tip.hoveredRaid());
			BufferedImage hovered = render(tip);
			int changed = 0;
			for (int y = 0; y < resting.getHeight(); y++)
			{
				for (int x = 0; x < resting.getWidth(); x++)
				{
					if (resting.getRGB(x, y) != hovered.getRGB(x, y))
					{
						// Only this row's label moves, and only from orange to white.
						assertEquals(row, tip.raidAt(y));
						assertEquals(orange, resting.getRGB(x, y));
						assertEquals(white, hovered.getRGB(x, y));
						changed++;
					}
				}
			}
			assertTrue(changed > 0);
		}

		move(tip, 5, 2);
		assertEquals(-1, tip.hoveredRaid());
		BufferedImage after = render(tip);
		for (int y = 0; y < resting.getHeight(); y++)
		{
			for (int x = 0; x < resting.getWidth(); x++)
			{
				assertEquals(resting.getRGB(x, y), after.getRGB(x, y));
			}
		}
	}

	@Test
	public void pressingARaidRowOpensThatRaidAndNothingElseDoes()
	{
		PvmSummaryTooltip tip = card();
		List<HiscoreSkill> opened = new ArrayList<>();
		tip.setOnOpenRaid((press, raid) -> opened.add(raid));

		// Before the first paint there is no raid section to press.
		press(tip, 5, 200, MouseEvent.BUTTON1);
		assertTrue(opened.isEmpty());

		painted(tip);
		int top = firstRaidY(tip);
		for (int row = 0; row < 3; row++)
		{
			// The press that opens a raid is spent on it.
			assertTrue(press(tip, 5, top + row * NativeTooltip.LINE_HEIGHT + 3, MouseEvent.BUTTON1).isConsumed());
		}
		assertEquals(List.of(HiscoreSkill.CHAMBERS_OF_XERIC, HiscoreSkill.THEATRE_OF_BLOOD,
			HiscoreSkill.TOMBS_OF_AMASCUT), opened);

		// The Slayer rows above, the sprites below and a right click are not links, and pass through.
		assertFalse(press(tip, 5, top - 40, MouseEvent.BUTTON1).isConsumed());
		assertFalse(press(tip, 5, top + 3 * NativeTooltip.LINE_HEIGHT + 3, MouseEvent.BUTTON1).isConsumed());
		assertFalse(press(tip, 5, top + 3, MouseEvent.BUTTON3).isConsumed());
		assertEquals(3, opened.size());
	}

	@Test
	public void hoverRepaintsOnlyWhenTheRaidRowChanges()
	{
		int[] repaints = {0};
		PvmSummaryTooltip tip = new PvmSummaryTooltip()
		{
			@Override
			public void repaint(long tm, int x, int y, int width, int height)
			{
				repaints[0]++;
			}
		};
		fill(tip);
		painted(tip);
		int top = firstRaidY(tip);
		repaints[0] = 0;
		move(tip, 5, top + 2);
		assertEquals(1, repaints[0]);
		move(tip, 9, top + 4);
		assertEquals(1, repaints[0]);
		move(tip, 5, top + NativeTooltip.LINE_HEIGHT + 2);
		assertEquals(2, repaints[0]);
		move(tip, 5, 2);
		assertEquals(3, repaints[0]);
		move(tip, 5, 4);
		assertEquals(3, repaints[0]);
	}

	@Test
	public void theHoveredRaidRowIsClearedOnExit()
	{
		PvmSummaryTooltip tip = painted(card());
		int top = firstRaidY(tip);
		move(tip, 5, top + 2);
		assertEquals(0, tip.hoveredRaid());
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseExited(event(tip, MouseEvent.MOUSE_EXITED, 5, top + 2, MouseEvent.NOBUTTON));
		}
		assertEquals(-1, tip.hoveredRaid());
	}

	private static PvmSummaryTooltip card()
	{
		return fill(new PvmSummaryTooltip());
	}

	private static PvmSummaryTooltip fill(PvmSummaryTooltip tip)
	{
		tip.setData(110.5, 2_500, 40, PanelData.bossCount(), "Vorkath", 900);
		Map<String, Integer> kills = new HashMap<>();
		kills.put(HiscoreSkill.CHAMBERS_OF_XERIC.getName(), 120);
		kills.put(HiscoreSkill.THEATRE_OF_BLOOD.getName(), 80);
		kills.put(HiscoreSkill.TOMBS_OF_AMASCUT.getName(), 300);
		tip.setRaids(new HiscoreResult(AccountType.REGULAR, kills, Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1), null);
		return tip;
	}

	private static PvmSummaryTooltip painted(PvmSummaryTooltip tip)
	{
		tip.setSize(tip.getPreferredSize());
		render(tip);
		return tip;
	}

	private static BufferedImage render(PvmSummaryTooltip tip)
	{
		Dimension size = tip.getSize();
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		tip.paint(graphics);
		graphics.dispose();
		return image;
	}

	/** The first y that answers as raid row 0, found the way a pointer would. */
	private static int firstRaidY(PvmSummaryTooltip tip)
	{
		for (int y = 0; y < tip.getHeight(); y++)
		{
			if (tip.raidAt(y) == 0)
			{
				return y;
			}
		}
		throw new AssertionError("no raid section painted");
	}

	/** One press through every listener, the way Swing delivers it; returns it to read consumption. */
	private static MouseEvent press(PvmSummaryTooltip tip, int x, int y, int button)
	{
		MouseEvent press = event(tip, MouseEvent.MOUSE_PRESSED, x, y, button);
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mousePressed(press);
		}
		return press;
	}

	private static void move(PvmSummaryTooltip tip, int x, int y)
	{
		for (MouseMotionListener listener : tip.getMouseMotionListeners())
		{
			listener.mouseMoved(event(tip, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON));
		}
	}

	private static MouseEvent event(PvmSummaryTooltip tip, int id, int x, int y, int button)
	{
		return new MouseEvent(tip, id, System.currentTimeMillis(), 0, x, y, 1, false, button);
	}

	private static HiscoreResult hiscoreWithGlory(int glory)
	{
		Map<String, Integer> scores = new HashMap<>();
		scores.put(ColosseumGlory.ACTIVITY_NAME, glory);
		return new HiscoreResult(AccountType.REGULAR,
			Collections.emptyMap(), Collections.emptyMap(), scores, Collections.emptyMap(),
			Collections.emptyMap(), 0, 0, 0, -1);
	}

}
