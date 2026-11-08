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
		assertEquals("350,000", TitleTooltip.scoreText(350_000L));
		assertEquals("9,741,203", TitleTooltip.scoreText(9_741_203L));
		assertEquals("14,200,000", TitleTooltip.scoreText(14_200_000L));
		assertEquals("200,000,000", TitleTooltip.scoreText(200_000_000L));
		assertEquals("--", TitleTooltip.scoreText(0L));
		assertEquals("--", TitleTooltip.scoreText(-1L));
	}

	@Test
	public void theCaTileWaitsForATierAndFillsTowardTheNextOne()
	{
		Map<CombatAchievementTier, Integer> totals = new java.util.EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			totals.put(tier, tier.totalTasks());
		}
		Map<CombatAchievementTier, Integer> easyDone = new java.util.EnumMap<>(CombatAchievementTier.class);
		easyDone.put(CombatAchievementTier.EASY, CombatAchievementTier.EASY.totalTasks());
		CombatAchievementResult easy = CombatAchievementResult.of(easyDone, totals);
		// Easy's points, toward Medium's threshold: every Easy and Medium task's points.
		assertEquals(CombatAchievementTier.EASY.totalTasks(), easy.getTotalPoints());
		assertEquals(CombatAchievementTier.EASY.totalTasks() + 2 * CombatAchievementTier.MEDIUM.totalTasks(),
			easy.nextTierPoints());
		// Every task done: the bar's end is the points held, so it fills.
		CombatAchievementResult grandmaster = CombatAchievementResult.of(totals, totals);
		assertEquals(grandmaster.getTotalPoints(), grandmaster.nextTierPoints());

		// No tier: Most Killed and Bosses, two across and wider; with a tier, three across.
		BufferedImage noTier = render(painted(fill(pvmCard(CombatAchievementResult.of(Collections.emptyMap(), totals)))));
		BufferedImage tiered = render(painted(fill(pvmCard(easy))));
		int row = TestViews.tiles(noTier).get(0).y;
		assertTrue(rowHas(noTier, row, GameTile.PAGE_WIDTH, 2));
		assertTrue(rowHas(tiered, row, GameTile.WIDTH, 3));
		// The PvM row is a tile tall either way, so the raids stay put.
		assertEquals(raidTiles(noTier), raidTiles(tiered));
	}

	/** Whether a row holds n tiles of this width side by side, a gap apart. */
	private static boolean rowHas(BufferedImage image, int row, int width, int n)
	{
		List<Point> tiles = TestViews.tiles(image, width, GameTile.HEIGHT);
		Point first = tiles.stream().filter(tile -> tile.y == row).findFirst().orElse(null);
		for (int i = 0; first != null && i < n; i++)
		{
			Point want = new Point(first.x + i * (width + GameTile.GAP), row);
			if (!tiles.contains(want))
			{
				return false;
			}
		}
		return first != null;
	}

	@Test
	public void theMostKilledAndBossesTilesOpenTheirCardsAndNameOnlyInTheBand()
	{
		assertTrue("tests run headless, so no link can reach a browser", java.awt.GraphicsEnvironment.isHeadless());
		PvmSummaryTooltip tip = card();
		tip.setMostKilled(HiscoreSkill.VORKATH, null, 12, 12);
		List<String> opened = new ArrayList<>();
		tip.setOnOpenRaid((press, boss) -> opened.add(boss.getName()));
		tip.setOnOpenBosses(press -> opened.add("Bosses tab"));
		BufferedImage image = render(painted(tip));
		int row = TestViews.tiles(image).get(0).y;
		List<Point> wide = TestViews.tiles(image, GameTile.PAGE_WIDTH, GameTile.HEIGHT);
		Point most = wide.stream().filter(tile -> tile.y == row).findFirst().get();
		Point bosses = new Point(most.x + GameTile.PAGE_WIDTH + GameTile.GAP, row);
		move(tip, most.x + 10, most.y + 10);
		assertEquals("Vorkath", tip.getHeaderHoverLineText());
		assertTrue(press(tip, most.x + 10, most.y + 10, MouseEvent.BUTTON1).isConsumed());
		assertTrue(press(tip, bosses.x + 10, bosses.y + 10, MouseEvent.BUTTON1).isConsumed());
		assertEquals(List.of("Vorkath", "Bosses tab"), opened);
	}

	@Test
	public void aTilesHoverNameIsNeverAWikiLink()
	{
		// A press on a tile opens its card; the megarare it names must not open its wiki page as well (11/7).
		PvmSummaryTooltip tip = painted(card());
		Point cox = firstRaid(tip);
		move(tip, cox.x + 10, cox.y + 10);
		assertEquals("Twisted Bow", tip.getHeaderHoverLineText());
		assertEquals(-1, (int) TestViews.field(tip.itemHover, "hoveredItemId"));
	}

	/** How many tiles the PvM row holds: those level with the card's first tile. */
	private static int pvmRow(PvmSummaryTooltip tip)
	{
		List<Point> tiles = TestViews.tiles(render(tip));
		int row = tiles.get(0).y;
		return (int) tiles.stream().filter(tile -> tile.y == row).count();
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

		List<Point> raids = raidTiles(render(painted(tip)));
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
		Point tob = raidTiles(resting).get(1);
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
	public void withNoLogARaidTileShowsItsKillsInWhite()
	{
		// A lookup with no synced log (Dylan, 2026-11-07): each raid tile reads its kills, both modes together.
		PvmSummaryTooltip scored = painted(card());
		List<Point> raids = raidTiles(render(scored));
		assertTrue(hasWhite(render(scored), raids.get(0), GameTile.WIDTH, GameTile.HEIGHT));
		PvmSummaryTooltip bare = new PvmSummaryTooltip();
		bare.setData(110.5, 2_500, 40, PanelData.bossCount(), "Vorkath", 900);
		bare.setRaids(TestViews.hiscore(AccountType.REGULAR, Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1), null);
		bare.setOnOpenRaid((press, raid) ->
		{
		});
		painted(bare);
		assertFalse("no kills, no number", hasWhite(render(bare), raidTiles(render(bare)).get(0), GameTile.WIDTH,
			GameTile.HEIGHT));
	}

	@Test
	public void aRaidTileNamesItsMegarareInTheHoverBandGreenWhenHeld()
	{
		// As the megarare row did before the tiles: the band names the raid's megarare, green held and red not.
		PvmSummaryTooltip tip = card();
		tip.setMegarares(1, 0, 0, 0, null);
		List<Point> raids = raidTiles(render(painted(tip)));
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

		List<Point> raids = raidTiles(render(painted(tip)));
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
	public void theDormantCardShowsAllFourRaids()
	{
		// No lookup yet: the panel lists The Fractured Archive, so its tile stands with the other three.
		PvmSummaryTooltip dormant = new PvmSummaryTooltip();
		dormant.setData(0, 0, 0, PanelData.bossCount(), null, 0);
		assertTrue(PanelData.tfaListed());
		painted(dormant);
		assertEquals(4, (int) TestViews.call(dormant, "raidRowCount"));
	}

	@Test
	public void theFracturedArchiveTileWaitsForItsHiscoreRow()
	{
		// Before Jagex adds the raid's row the card has three raid tiles; from the lookup that carries it, a fourth,
		// and four lay out two across. Pressing it opens the raid once the client knows it.
		assertEquals(3, raidTiles(render(painted(card()))).size());

		Map<String, Integer> kills = new HashMap<>();
		kills.put(HiscoreSkill.CHAMBERS_OF_XERIC.getName(), 120);
		kills.put(PanelData.TFA_HISCORE, -1);
		PvmSummaryTooltip out = new PvmSummaryTooltip();
		out.setData(110.5, 2_500, 40, PanelData.bossCount(), null, 0);
		out.setRaids(TestViews.hiscore(AccountType.REGULAR, kills, Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1), null);
		List<HiscoreSkill> opened = new ArrayList<>();
		out.setOnOpenRaid((press, raid) -> opened.add(raid));
		// No most killed boss and no CA tier: no PvM tiles, so every wide tile is a raid's.
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
		List<Point> raids = raidTiles(render(painted(tip)));
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

	/** Whether any pixel of a tile at this top-left is the cards' white, the color a hiscores count is written in. */
	private static boolean hasWhite(BufferedImage image, Point tile, int width, int height)
	{
		int white = NativeTooltip.valueColor.getRGB();
		for (int y = tile.y; y < tile.y + height; y++)
		{
			for (int x = tile.x; x < tile.x + width; x++)
			{
				if (image.getRGB(x, y) == white)
				{
					return true;
				}
			}
		}
		return false;
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
	/** The raid tiles: every tile below the PvM tiles' row, which leads the card's tiles. */
	private static List<Point> raidTiles(BufferedImage image)
	{
		List<Point> tiles = TestViews.tiles(image);
		int pvmRow = tiles.get(0).y;
		List<Point> raids = new ArrayList<>();
		for (Point tile : tiles)
		{
			if (tile.y > pvmRow)
			{
				raids.add(tile);
			}
		}
		return raids;
	}

	private static Point firstRaid(PvmSummaryTooltip tip)
	{
		return raidTiles(render(tip)).get(0);
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

	@Test
	public void aTilesFillIsTheStoneOrThePacksTintFortyAtRestSeventyFiveUnderThePointer()
	{
		java.awt.Color stone = NativeTooltip.tileFill(false);
		assertEquals(new java.awt.Color(73, 63, 52), stone);
		assertEquals(new java.awt.Color(86, 75, 61), NativeTooltip.tileFill(true));
		java.awt.Color ground = new java.awt.Color(10, 20, 30);
		java.awt.Color rim = new java.awt.Color(210, 120, 30);
		try
		{
			NativeTooltip.cardColors(null, null, null, ground, rim, null);
			assertEquals(net.runelite.client.util.ColorUtil.colorLerp(ground, rim, 0.4), NativeTooltip.tileFill(false));
			assertEquals(net.runelite.client.util.ColorUtil.colorLerp(ground, rim, 0.75), NativeTooltip.tileFill(true));
		}
		finally
		{
			NativeTooltip.cardColors(null, null, null, null, null, null);
		}
	}

	@Test
	public void aRaidsKillsCountItsHardModeToo()
	{
		Map<String, Integer> kills = new HashMap<>();
		kills.put(HiscoreSkill.CHAMBERS_OF_XERIC.getName(), 183);
		kills.put(HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE.getName(), 6);
		kills.put(HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE.getName(), 2);
		PvmSummaryTooltip tip = new PvmSummaryTooltip();
		tip.setRaids(TestViews.hiscore(AccountType.REGULAR, kills, Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1), null);
		int[] raidKills = TestViews.field(tip, "raidKills");
		assertEquals(189, raidKills[0]);
		assertEquals(2, raidKills[1]);
	}

	@Test
	public void theBossesTileDrawsItsLogsLineAndASmallIconSitsInTheMiddleOfItsSlot()
	{
		// The Logs line is drawn where a tile's bar sits: with a log it adds pixels there, without one it doesn't.
		PvmSummaryTooltip without = card();
		PvmSummaryTooltip with = card();
		with.setCompletion(16, 72);
		BufferedImage a = render(painted(without));
		BufferedImage b = render(painted(with));
		int row = TestViews.tiles(a).get(0).y;
		List<Point> wide = TestViews.tiles(a, GameTile.PAGE_WIDTH, GameTile.HEIGHT);
		Point bosses = wide.stream().filter(tile -> tile.y == row).reduce((first, second) -> second).get();
		int changed = 0;
		for (int y = bosses.y + GameTile.HEIGHT - 16; y < bosses.y + GameTile.HEIGHT - 3; y++)
		{
			for (int x = bosses.x; x < bosses.x + GameTile.PAGE_WIDTH; x++)
			{
				changed += a.getRGB(x, y) != b.getRGB(x, y) ? 1 : 0;
			}
		}
		assertTrue("the Logs line draws", changed > 0);

		// A 20 px icon's top sits 6 px into the 32 px slot that starts 17 px down the tile.
		PvmSummaryTooltip icon = card();
		BufferedImage red = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = red.createGraphics();
		g.setColor(java.awt.Color.RED);
		g.fillRect(0, 0, 20, 20);
		g.dispose();
		icon.setMostKilled(HiscoreSkill.VORKATH, red, 12, 12);
		BufferedImage c = render(painted(icon));
		Point most = TestViews.tiles(c, GameTile.PAGE_WIDTH, GameTile.HEIGHT).stream().filter(tile -> tile.y == row)
			.findFirst().get();
		int top = -1;
		for (int y = most.y; y < most.y + GameTile.HEIGHT && top < 0; y++)
		{
			if (c.getRGB(most.x + GameTile.PAGE_WIDTH / 2, y) == java.awt.Color.RED.getRGB())
			{
				top = y - most.y;
			}
		}
		assertEquals(17 + 6, top);
	}
}
