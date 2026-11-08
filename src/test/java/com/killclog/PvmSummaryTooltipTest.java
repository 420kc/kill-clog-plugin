package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
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
		assertArrayEquals(new String[]{"Twisted Bow", "Scythe of Vitur", "Tumeken's Shadow", "Obligator"},
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
	public void raidTilesMoveDownWithTheTierRowAndStillAnswer()
	{
		Map<CombatAchievementTier, Integer> totals = new java.util.EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			totals.put(tier, tier.totalTasks());
		}
		Map<CombatAchievementTier, Integer> easyDone = new java.util.EnumMap<>(CombatAchievementTier.class);
		easyDone.put(CombatAchievementTier.EASY, CombatAchievementTier.EASY.totalTasks());
		PvmSummaryTooltip plain = fill(pvmCard(null));
		PvmSummaryTooltip tiered = fill(pvmCard(CombatAchievementResult.of(easyDone, totals)));

		Point plainTop = firstRaid(painted(plain));
		Point tieredTop = firstRaid(painted(tiered));
		// The tier row's room moves the raid tiles down, and their presses move with them.
		assertEquals(plainTop.y + 4 + 22 + 6, tieredTop.y);
		assertEquals(-1, TestViews.tileAt(tiered, "raidRows", tieredTop.x + 10, tieredTop.y - 1));
		assertEquals(0, TestViews.tileAt(tiered, "raidRows", tieredTop.x + 10, tieredTop.y + 10));
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
	public void raidTilesAnswerOnlyWhereTheyWerePainted()
	{
		PvmSummaryTooltip tip = card();
		// Before the first paint there is no raid section to answer.
		assertEquals(-1, TestViews.tileAt(tip, "raidRows", 20, 200));

		List<Point> raids = TestViews.tiles(render(painted(tip)));
		// CoX, ToB and ToA, three across; The Fractured Archive's waits for its hiscore row, which this lookup hasn't.
		assertEquals(3, raids.size());
		for (int row = 0; row < 3; row++)
		{
			Point tile = raids.get(row);
			assertEquals(row, TestViews.tileAt(tip, "raidRows", tile.x, tile.y));
			assertEquals(row, TestViews.tileAt(tip, "raidRows", tile.x + GameTile.WIDTH - 1, tile.y + GameTile.HEIGHT - 1));
		}
		assertEquals(-1, TestViews.tileAt(tip, "raidRows", raids.get(0).x + 10, raids.get(0).y - 1));
		assertEquals(-1, TestViews.tileAt(tip, "raidRows", raids.get(0).x + GameTile.WIDTH + 1, raids.get(0).y + 10));
	}

	@Test
	public void aHoveredRaidTileLightensItselfAndNothingElse()
	{
		PvmSummaryTooltip tip = painted(card());
		BufferedImage resting = render(tip);
		Point tob = TestViews.tiles(resting).get(1);
		move(tip, tob.x + 10, tob.y + 10);
		assertEquals(1, TestViews.hoveredRow(tip, "raidRows"));
		BufferedImage hovered = render(tip);
		int changed = 0;
		int bandTop = Integer.MAX_VALUE;
		int bandBottom = -1;
		for (int y = 0; y < resting.getHeight(); y++)
		{
			for (int x = 0; x < resting.getWidth(); x++)
			{
				if (resting.getRGB(x, y) != hovered.getRGB(x, y))
				{
					if (x >= tob.x && x < tob.x + GameTile.WIDTH && y >= tob.y && y < tob.y + GameTile.HEIGHT)
					{
						changed++;
					}
					else
					{
						bandTop = Math.min(bandTop, y);
						bandBottom = Math.max(bandBottom, y);
					}
				}
			}
		}
		assertTrue(changed > 0);
		// Outside ToB's tile only the hover band changes: one line of text, above the cards' rows.
		assertTrue("the band names the Scythe", bandBottom >= 0);
		assertTrue("only one line outside the tile changes", bandBottom - bandTop < NativeTooltip.LINE_HEIGHT + 4);
		assertTrue("the band sits above the tiles", bandBottom < tob.y);
		move(tip, 5, 2);
		assertEquals(-1, TestViews.hoveredRow(tip, "raidRows"));
	}

	@Test
	public void aRaidTileNamesItsMegarareInTheHoverBandGreenWhenHeld()
	{
		// As the megarare row did before the tiles: the band names the raid's megarare, green held and red not.
		PvmSummaryTooltip tip = card();
		tip.setMegarares(1, 0, 0, 0, null);
		List<Point> raids = TestViews.tiles(render(painted(tip)));
		move(tip, raids.get(0).x + 10, raids.get(0).y + 10);
		assertEquals("Twisted Bow", tip.getHeaderHoverLineText());
		assertEquals(new Color(0, 255, 0), tip.getHeaderHoverLineColor());
		move(tip, raids.get(1).x + 10, raids.get(1).y + 10);
		assertEquals("Scythe of Vitur", tip.getHeaderHoverLineText());
		assertEquals(new Color(255, 0, 0), tip.getHeaderHoverLineColor());
		// Off the tiles, the band is empty again.
		move(tip, 5, 2);
		assertNull(tip.getHeaderHoverLineText());
	}

	@Test
	public void pressingARaidTileOpensThatRaidAndNothingElseDoes()
	{
		PvmSummaryTooltip tip = card();
		List<HiscoreSkill> opened = new ArrayList<>();
		tip.setOnOpenRaid((press, raid) -> opened.add(raid));

		// Before the first paint there is no raid section to press.
		press(tip, 20, 200, MouseEvent.BUTTON1);
		assertTrue(opened.isEmpty());

		List<Point> raids = TestViews.tiles(render(painted(tip)));
		for (Point tile : raids)
		{
			// The press that opens a raid is spent on it.
			assertTrue(press(tip, tile.x + 10, tile.y + 10, MouseEvent.BUTTON1).isConsumed());
		}
		assertEquals(List.of(HiscoreSkill.CHAMBERS_OF_XERIC, HiscoreSkill.THEATRE_OF_BLOOD,
			HiscoreSkill.TOMBS_OF_AMASCUT), opened);

		// The rows above, the gap between tiles and a right click are not links, and pass through.
		Point cox = raids.get(0);
		assertFalse(press(tip, cox.x + 10, cox.y - 40, MouseEvent.BUTTON1).isConsumed());
		assertFalse(press(tip, cox.x + GameTile.WIDTH + 1, cox.y + 10, MouseEvent.BUTTON1).isConsumed());
		assertFalse(press(tip, cox.x + 10, cox.y + 10, MouseEvent.BUTTON3).isConsumed());
		assertEquals(3, opened.size());
	}

	@Test
	public void theFracturedArchiveTileWaitsForItsHiscoreRow()
	{
		// Before Jagex adds the raid's row the card has three raid tiles; from the lookup that carries it, a fourth,
		// and four lay out two across. Pressing it opens the raid once the client knows it.
		assertEquals(3, TestViews.tiles(render(painted(card()))).size());

		Map<String, Integer> kills = new HashMap<>();
		kills.put(HiscoreSkill.CHAMBERS_OF_XERIC.getName(), 120);
		kills.put(PanelData.TFA_HISCORE, -1);
		PvmSummaryTooltip out = new PvmSummaryTooltip();
		out.setData(110.5, 2_500, 40, PanelData.bossCount(), "Vorkath", 900);
		out.setRaids(TestViews.hiscore(AccountType.REGULAR, kills, Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1), null);
		List<HiscoreSkill> opened = new ArrayList<>();
		out.setOnOpenRaid((press, raid) -> opened.add(raid));
		List<Point> raids = TestViews.tiles(render(painted(out)), GameTile.PAGE_WIDTH, GameTile.HEIGHT);
		assertEquals(4, raids.size());
		assertEquals(raids.get(0).x, raids.get(2).x);
		// Its popup comes with the launch-day update's boss tile: before the panel lists the raid, even a client whose
		// RuneLite knows it opens nothing, rather than a popup with no data asking a synced player to set up.
		Point tfa = raids.get(3);
		assertEquals(PanelData.tfaListed(), press(out, tfa.x + 10, tfa.y + 10, MouseEvent.BUTTON1).isConsumed());
		assertTrue("this release's panel lists the raid", PanelData.tfaListed());
		// The rule itself, for a client whose RuneLite does know the raid: a boss the list carries opens, one it
		// doesn't (as the raid until the launch-day update) opens nothing, whatever RuneLite knows.
		assertEquals(HiscoreSkill.ZULRAH, PanelData.listed(HiscoreSkill.ZULRAH));
		assertNull(PanelData.listed(HiscoreSkill.OVERALL));
		assertNull(PanelData.listed(null));
	}
	@Test
	public void hoverRepaintsOnlyWhenTheRaidTileChanges()
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
		List<Point> raids = TestViews.tiles(render(painted(tip)));
		Point cox = raids.get(0);
		// A change of tile repaints (the tile, and the band naming its megarare); a move that changes nothing doesn't.
		repaints[0] = 0;
		move(tip, cox.x + 5, cox.y + 2);
		int onCox = repaints[0];
		assertTrue(onCox > 0);
		move(tip, cox.x + 9, cox.y + 4);
		assertEquals(onCox, repaints[0]);
		move(tip, raids.get(1).x + 5, raids.get(1).y + 2);
		int onTob = repaints[0];
		assertTrue(onTob > onCox);
		move(tip, 5, 2);
		int off = repaints[0];
		assertTrue(off > onTob);
		move(tip, 5, 4);
		assertEquals(off, repaints[0]);
	}

	@Test
	public void theHoveredRaidTileIsClearedOnExit()
	{
		PvmSummaryTooltip tip = painted(card());
		Point cox = firstRaid(tip);
		move(tip, cox.x + 5, cox.y + 2);
		assertEquals(0, TestViews.hoveredRow(tip, "raidRows"));
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseExited(event(tip, MouseEvent.MOUSE_EXITED, cox.x + 5, cox.y + 2, MouseEvent.NOBUTTON));
		}
		assertEquals(-1, TestViews.hoveredRow(tip, "raidRows"));
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
		tip.setRaids(TestViews.hiscore(AccountType.REGULAR, kills, Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1), null);
		// Raid tiles answer once something can open them.
		tip.setOnOpenRaid((press, raid) ->
		{
		});
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
	/** The first raid tile's top-left corner. */
	private static Point firstRaid(PvmSummaryTooltip tip)
	{
		return TestViews.tiles(render(tip)).get(0);
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
		return TestViews.hiscore(AccountType.REGULAR,
			Collections.emptyMap(), Collections.emptyMap(), scores, Collections.emptyMap(),
			Collections.emptyMap(), 0, 0, 0, -1);
	}
}
