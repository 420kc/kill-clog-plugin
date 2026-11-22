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
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class PvmSummaryTooltipTest
{
	@Test
	public void theRaidsTableHoldsEveryRaid()
	{
		// raids.tsv, golden: the tiles in order, their megarares by full name, the hard modes and their short names.
		List<String> rows = new ArrayList<>();
		for (Raid raid : PanelData.RAIDS)
		{
			rows.add(raid.getTile() + " " + raid.getSkill() + " " + raid.getHard() + " " + raid.getHardLabel() + " "
				+ raid.isWaits() + " " + raid.getPage() + " " + raid.getMegarare() + " " + raid.getMegarareName());
			// The hiscore row a tile reads its kills from is RuneLite's own name for the raid.
			assertEquals(raid.getSkill().getName(), raid.getRow());
		}
		assertEquals(java.util.Arrays.asList(
			"CoX CHAMBERS_OF_XERIC CHAMBERS_OF_XERIC_CHALLENGE_MODE CM false chambers_of_xeric 20997 Twisted Bow",
			"ToB THEATRE_OF_BLOOD THEATRE_OF_BLOOD_HARD_MODE HM false theatre_of_blood 22486 Scythe of Vitur",
			"ToA TOMBS_OF_AMASCUT TOMBS_OF_AMASCUT_EXPERT Expert false tombs_of_amascut 27277 Tumeken's Shadow",
			"TFA THE_FRACTURED_ARCHIVE null - true the_fractured_archive 34717 Obligator"), rows);
		assertArrayEquals(new int[]{20997, 22486, 27277, 34717}, PanelData.MEGARARE_ITEM_IDS);
		// A hard mode's cell opens its raid's card; anything else opens its own.
		assertSame(HiscoreSkill.TOMBS_OF_AMASCUT, PanelData.card(HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT));
		assertSame(HiscoreSkill.ZULRAH, PanelData.card(HiscoreSkill.ZULRAH));
	}

	@Test
	public void beforeAnyLookupTheBossesTileReadsAGreyDashNotARedNone()
	{
		// At rest the tile reads the raids' resting "--/72" in grey; a looked-up player with no boss kills reads a real
		// "Killed: 0/72", in white like Most Killed's kills.
		PvmSummaryTooltip rest = new PvmSummaryTooltip();
		rest.setData(0, -1, -1, 72, null, 0);
		PvmSummaryTooltip none = new PvmSummaryTooltip();
		none.setData(126.1, 0, 0, 72, null, 0);
		assertTrue(bossesCount(rest, TitleTooltip.MUTED_GRAY) > 0);
		assertEquals(0, bossesCount(none, TitleTooltip.MUTED_GRAY));
		assertTrue(bossesCount(none, NativeTooltip.valueColor) > 0);
	}

	/** How many pixels of a colour the Bosses tile's count line holds, the tile alone in the PvM row. */
	private static int bossesCount(PvmSummaryTooltip tip, java.awt.Color color)
	{
		BufferedImage image = render(painted(tip));
		Point bosses = TestViews.tiles(image).get(0);
		int count = 0;
		for (int y = bosses.y + 48; y < bosses.y + 66; y++)
		{
			for (int x = bosses.x; x < bosses.x + GameTile.WIDTH; x++)
			{
				count += image.getRGB(x, y) == color.getRGB() ? 1 : 0;
			}
		}
		return count;
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

	// Dylan 11/17: a tile opens what it names, and the Combat Achievements tile opened nothing. The tier and its points read
	// as PvM lines over the skill cards' bar, and the tier's reward stands under the title at full size, with the gear.
	@Test
	public void combatAchievementsReadAsLinesOverABarAndTheTiersRewardJoinsTheGear()
	{
		CombatAchievementResult easy = easy();
		// Easy's points, toward Medium's threshold: every Easy and Medium task's points.
		assertEquals(CombatAchievementTier.EASY.totalTasks(), easy.getTotalPoints());
		assertEquals(CombatAchievementTier.EASY.totalTasks() + 2 * CombatAchievementTier.MEDIUM.totalTasks(),
			easy.nextTierPoints());
		// Every task done: the bar's end is the points held, so it fills.
		CombatAchievementResult grandmaster = CombatAchievementResult.of(caTotals(), caTotals());
		assertEquals(grandmaster.getTotalPoints(), grandmaster.nextTierPoints());

		// Most Killed and Bosses, two across and wider, with a tier or without: no tile stands for the tier.
		BufferedImage none = render(painted(fill(pvmCard(null))));
		BufferedImage tiered = render(painted(fill(pvmCard(easy))));
		for (BufferedImage card : new BufferedImage[]{none, tiered})
		{
			assertTrue(rowHas(card, TestViews.tiles(card).get(0).y, GameTile.PAGE_WIDTH, 2));
		}
		// A Grandmaster's bar is green and spans the track, the card less its scroll rail; Easy's is the game's yellow,
		// filled to its share of the way to Medium.
		BufferedImage gm = render(painted(fill(pvmCard(grandmaster))));
		int track = gm.getWidth() - CardBody.RAIL_GAP - CardBody.RAIL_WIDTH - 2 * NativeTooltip.getInset();
		int[] full = TestViews.longestRun(gm, TitleTooltip.CLOG_GREEN);
		int[] partial = TestViews.longestRun(tiered, TitleTooltip.CLOG_YELLOW);
		assertEquals(NativeTooltip.getInset(), full[0]);
		assertEquals(track, full[2]);
		assertEquals(CardBody.BAR_HEIGHT, full[3]);
		assertEquals(NativeTooltip.getInset(), partial[0]);
		assertEquals(track * (double) easy.getTotalPoints() / easy.nextTierPoints(), partial[2], 1);
		assertEquals(CardBody.BAR_HEIGHT, partial[3]);
		assertEquals("no Combat Achievements, no bar", 0, TestViews.longestRun(none, TitleTooltip.CLOG_YELLOW)[2]);

		// The reward under the title, named for its tier while hovered.
		PvmSummaryTooltip tip = painted(fill(pvmCard(easy)));
		Point reward = find(render(tip), 0xffff00ff);
		assertNotNull(reward);
		assertTrue("above the tiles", reward.y + 32 <= TestViews.tiles(render(tip)).get(0).y);
		move(tip, reward.x + 16, reward.y + 16);
		assertEquals("Easy Combat Achievements", tip.getHeaderHoverLineText());
		assertNull("no tier, no reward", find(none, 0xffff00ff));
	}

	@Test
	public void aComparisonPairKeepsTheGearRowAndCombatAchievementsLinesOnBothSides()
	{
		// One side with a tier's reward and its Combat Achievements, the other with neither: once lined up, each keeps
		// both, so the pair's tiles sit level.
		PvmSummaryTooltip blue = fill(pvmCard(easy()));
		PvmSummaryTooltip red = fill(pvmCard(null));
		assertNotEquals(firstTileY(blue), firstTileY(red));
		PvmSummaryTooltip.lineUp(blue, red);
		assertEquals(blue.getPreferredSize().height, red.getPreferredSize().height);
		assertEquals(firstTileY(blue), firstTileY(red));
		// A card without Combat Achievements shows no lines for them, and a pair of two such cards gains no row.
		int bare = firstTileY(card());
		PvmSummaryTooltip plain = fill(pvmCard(null));
		assertEquals(bare, firstTileY(plain));
		PvmSummaryTooltip.lineUp(plain, fill(pvmCard(null)));
		assertEquals(bare, firstTileY(plain));
		assertEquals(card().getPreferredSize().height, plain.getPreferredSize().height);
	}

	/**
	 * No task done reads as unknown, not None: RuneProfile sends every tier at 0 for an account it holds none for. Below
	 * Easy with tasks done, the lines show, the bar part filled toward Easy.
	 */
	@Test
	public void noTaskDoneShowsNoCombatAchievementsLinesWhileBelowEasyWithSomeDoes()
	{
		Map<CombatAchievementTier, Integer> zero = new java.util.EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			zero.put(tier, 0);
		}
		int bare = firstTileY(fill(pvmCard(null)));
		PvmSummaryTooltip unknown = fill(pvmCard(CombatAchievementResult.of(zero, caTotals())));
		assertEquals("no lines", bare, firstTileY(unknown));
		assertEquals("no bar", 0, TestViews.longestRun(render(painted(unknown)), TitleTooltip.CLOG_YELLOW)[2]);
		// Beside a side with a reading, it keeps "--" lines, as a side with none does, so the pair still lines up.
		PvmSummaryTooltip blue = fill(pvmCard(easy()));
		PvmSummaryTooltip red = fill(pvmCard(CombatAchievementResult.of(zero, caTotals())));
		PvmSummaryTooltip.lineUp(blue, red);
		assertEquals(firstTileY(blue), firstTileY(red));

		Map<CombatAchievementTier, Integer> some = new java.util.EnumMap<>(zero);
		some.put(CombatAchievementTier.EASY, 20);
		PvmSummaryTooltip below = fill(pvmCard(CombatAchievementResult.of(some, caTotals())));
		assertTrue("the lines show", firstTileY(below) > bare);
		assertTrue("its bar toward Easy", TestViews.longestRun(render(painted(below)), TitleTooltip.CLOG_YELLOW)[2] > 0);
	}

	private static int firstTileY(PvmSummaryTooltip tip)
	{
		return TestViews.tiles(render(painted(tip))).get(0).y;
	}

	private static Map<CombatAchievementTier, Integer> caTotals()
	{
		Map<CombatAchievementTier, Integer> totals = new java.util.EnumMap<>(CombatAchievementTier.class);
		for (CombatAchievementTier tier : CombatAchievementTier.values())
		{
			totals.put(tier, tier.totalTasks());
		}
		return totals;
	}

	/** Every Easy task done, and nothing else. */
	private static CombatAchievementResult easy()
	{
		Map<CombatAchievementTier, Integer> easyDone = new java.util.EnumMap<>(CombatAchievementTier.class);
		easyDone.put(CombatAchievementTier.EASY, CombatAchievementTier.EASY.totalTasks());
		return CombatAchievementResult.of(easyDone, caTotals());
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

	// Dylan 11/16: a press on 2.8.2's Soul Wars tile closed the Combat Summary instead of opening its log page. LMS and
	// Soul Wars open their pages once something can open them, as a raid's tile opens its popup; PvP Arena and Bounty
	// Hunter have no log page, so a press there is the card's own.
	@Test
	public void theLmsAndSoulWarsTilesOpenTheirLogPagesAndTheOtherPvpTilesOpenNothing()
	{
		PvmSummaryTooltip bare = painted(card());
		List<Point> unwired = pvpTiles(bare);
		for (Point tile : unwired)
		{
			assertEquals("nothing can open a page yet", -1, TestViews.tileAt(bare, "tiles", tile.x + 10, tile.y + 10));
		}

		PvmSummaryTooltip tip = card();
		List<String> opened = new ArrayList<>();
		tip.setOnOpenPage((press, page) -> opened.add(page));
		painted(tip);
		List<Point> pvp = pvpTiles(tip);
		assertEquals(5, pvp.size());
		int[] keys = pvp.stream().mapToInt(tile -> TestViews.tileAt(tip, "tiles", tile.x + 10, tile.y + 10)).toArray();
		// The first two answer, each its own tile; the rest have nothing under the pointer.
		assertTrue(keys[0] >= 0 && keys[1] > keys[0]);
		assertArrayEquals(new int[]{-1, -1, -1}, java.util.Arrays.copyOfRange(keys, 2, keys.length));
		assertTrue(press(tip, pvp.get(0).x + 10, pvp.get(0).y + 10, MouseEvent.BUTTON1).isConsumed());
		assertTrue(press(tip, pvp.get(1).x + 10, pvp.get(1).y + 10, MouseEvent.BUTTON1).isConsumed());
		assertFalse(press(tip, pvp.get(2).x + 10, pvp.get(2).y + 10, MouseEvent.BUTTON1).isConsumed());
		assertFalse(press(tip, pvp.get(1).x + 10, pvp.get(1).y + 10, MouseEvent.BUTTON3).isConsumed());
		assertEquals(List.of("last_man_standing", "soul_wars"), opened);
	}

	/** The PvP tiles, the card's last five: wheeled to the scroll's end as a player does, then painted where they show. */
	private static List<Point> pvpTiles(PvmSummaryTooltip tip)
	{
		Point raid = firstRaid(tip);
		for (java.awt.event.MouseWheelListener listener : tip.getMouseWheelListeners())
		{
			listener.mouseWheelMoved(new java.awt.event.MouseWheelEvent(tip, MouseEvent.MOUSE_WHEEL, 0, 0,
				raid.x + 10, raid.y + 10, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 50));
		}
		List<Point> tiles = TestViews.tiles(render(tip));
		return tiles.subList(Math.max(0, tiles.size() - 5), tiles.size());
	}

	// Dylan 11/14: the combat level moves to the title's right, and the prestige cape and Dizana's quiver stand under the
	// title as items, each named in the band while hovered.
	@Test
	public void theCapeAndQuiverStandUnderTheTitleAsItemsNamedWhileHovered()
	{
		net.runelite.client.game.ItemManager items = gearItems();
		PvmSummaryTooltip tip = card();
		tip.addGear(PanelData.INFERNAL_MAX_CAPE_ITEM_ID, "Maxed Infernal", items);
		tip.addGear(PanelData.DIZANAS_QUIVER_ITEM_ID, "Dizana's quiver", items);
		BufferedImage image = render(painted(tip));
		Point cape = find(image, 0xffff00ff);
		Point quiver = find(image, 0xff00ffff);
		assertNotNull(cape);
		assertNotNull(quiver);
		assertTrue("the cape, then the quiver", cape.x < quiver.x);
		assertTrue("from the card's left", cape.x < image.getWidth() / 2);
		assertEquals("one row", cape.y, quiver.y);
		assertTrue("above the tiles", quiver.y + 32 <= TestViews.tiles(image).get(0).y);
		move(tip, cape.x + 16, cape.y + 16);
		assertEquals("Maxed Infernal", tip.getHeaderHoverLineText());
		move(tip, quiver.x + 16, quiver.y + 16);
		assertEquals("Dizana's quiver", tip.getHeaderHoverLineText());
	}

	@Test
	public void theGearRowShowsOnlyWithGear()
	{
		PvmSummaryTooltip plain = card();
		PvmSummaryTooltip geared = card();
		geared.addGear(PanelData.MAX_CAPE_ITEM_ID, "Maxed", gearItems());
		// A gap and the item sprites' row, then 11 px more: the band's own top divider and the room above it, which a card
		// with no gear row leaves out, sharing the title's divider.
		assertEquals(4 + CardBody.GRID_SPRITE + 11, geared.getPreferredSize().height - plain.getPreferredSize().height);
	}

	@Test
	public void withNoGearRowTheBandTakesTheTitlesDividerForItsTop()
	{
		// Before any lookup, or for a player with no cape, quiver or tier reward, nothing stands between the title and
		// the band: the title's line, the band's name line, then the band's own line, as the Collection Log card draws it
		// (Dylan, 2026-11-18: 2.8.2 drew a second line 11 px under the title's).
		PvmSummaryTooltip tip = painted(card());
		List<Integer> rules = rules(render(tip));
		int band = tip.getFontMetrics(FontManager.getRunescapeSmallFont()).getHeight() + 4;
		assertTrue("the title's line, then the band's: " + rules, rules.size() >= 2);
		assertEquals("one name line between them: " + rules, band + 1, rules.get(1) - rules.get(0));
	}

	// Where the band writes its hovered name was pinned by nothing: 7 px lower, across the band's bottom line, every
	// test passed.
	@Test
	public void theBandsHoveredNameSitsBetweenItsTwoLines()
	{
		PvmSummaryTooltip geared = card();
		geared.addGear(PanelData.MAX_CAPE_ITEM_ID, "Maxed", gearItems());
		for (PvmSummaryTooltip tip : new PvmSummaryTooltip[]{card(), geared})
		{
			painted(tip);
			Point cox = firstRaid(tip);
			move(tip, cox.x + 10, cox.y + 10);
			assertEquals("Twisted Bow", tip.getHeaderHoverLineText());
			BufferedImage image = render(tip);
			List<Integer> rules = rules(image);
			// With gear the band has its own top line under the title's; without, the title's line is its top.
			int top = rules.get(tip == geared ? 1 : 0);
			int bottom = rules.get(tip == geared ? 2 : 1);
			int name = tip.getHeaderHoverLineColor().getRGB();
			List<Integer> rows = new ArrayList<>();
			for (int y = 0; y < bottom + NativeTooltip.LINE_HEIGHT; y++)
			{
				for (int x = 0; x < image.getWidth() && !rows.contains(y); x++)
				{
					if (image.getRGB(x, y) == name)
					{
						rows.add(y);
					}
				}
			}
			assertFalse("the name is drawn", rows.isEmpty());
			assertTrue("between the band's lines at " + top + " and " + bottom + ": " + rows,
				rows.get(0) > top && rows.get(rows.size() - 1) < bottom);
		}
	}

	@Test
	public void theBandsNameStartsAtTheCardsEdgeAndIsCutToItsWidth()
	{
		PvmSummaryTooltip tip = painted(card());
		Point cox = firstRaid(tip);
		move(tip, cox.x + 10, cox.y + 10);
		BufferedImage image = render(tip);
		Point name = TestViews.textAt(image, "Twisted Bow", FontManager.getRunescapeSmallFont(), tip.getHeaderHoverLineColor());
		assertNotNull("the band names the hovered megarare", name);
		assertEquals("from the card's own edge", NativeTooltip.getInset(), name.x);

		// A name longer than the card is cut to fit between its edges, never run past them.
		PvmSummaryTooltip longNamed = new PvmSummaryTooltip();
		fill(longNamed);
		longNamed.setData(110.5, 2_500, 40, PanelData.bossCount(), "Vorkath the long named dragon of Ungael who goes on and on", 900);
		painted(longNamed);
		Point mostKilled = TestViews.tiles(render(longNamed)).get(0);
		move(longNamed, mostKilled.x + 10, mostKilled.y + 10);
		assertTrue(longNamed.getHeaderHoverLineText().startsWith("Vorkath the long"));
		BufferedImage wide = render(longNamed);
		List<Integer> rules = rules(wide);
		int ink = longNamed.getHeaderHoverLineColor().getRGB();
		int inked = 0;
		for (int y = rules.get(0) + 1; y < rules.get(1); y++)
		{
			for (int x = 0; x < wide.getWidth(); x++)
			{
				if (wide.getRGB(x, y) == ink)
				{
					inked++;
					assertTrue("inside the card's edges, at " + x, x >= NativeTooltip.getInset()
						&& x < wide.getWidth() - NativeTooltip.getInset());
				}
			}
		}
		assertTrue("and still drawn", inked > 0);
	}

	// The card read a log's counts in three places no test drew: a raid's tile, the Slayer section's Obtained line and
	// a PvP tile with a log page.
	@Test
	public void aRaidsTileAndTheSlayerLineReadTheirOwnLogs()
	{
		Map<String, Integer> pages = new java.util.LinkedHashMap<>();
		Map<String, Integer> held = new HashMap<>();
		for (int i = 0; i < 3; i++)
		{
			pages.put(PanelData.RAIDS.get(i).getPage(), 4);
		}
		// Chambers complete, Theatre untouched, Tombs part way; one of Slayer's five.
		held.put(PanelData.RAIDS.get(0).getPage(), 4);
		held.put(PanelData.RAIDS.get(2).getPage(), 2);
		pages.put(PanelData.SLAYER_CATEGORY, 5);
		held.put(PanelData.SLAYER_CATEGORY, 1);
		ClogResult log = TestViews.log("Probe", pages, held);
		Map<String, Integer> kills = new HashMap<>();
		kills.put(HiscoreSkill.CHAMBERS_OF_XERIC.getName(), 120);
		HiscoreResult hiscore = TestViews.hiscore(AccountType.REGULAR, kills, Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1);
		PvmSummaryTooltip tip = new PvmSummaryTooltip();
		tip.setData(110.5, 2_500, 40, PanelData.bossCount(), "Vorkath", 900);
		tip.setRaids(hiscore, log);
		tip.setSlayer(hiscore, log);
		BufferedImage image = render(painted(tip));
		List<Point> raids = raidTiles(image);
		java.awt.Font count = FontManager.getRunescapeFont();
		assertNotNull("Chambers reads 4/4 in green", TestViews.textAt(image, "4/4", count, new Color(0, 255, 0)));
		assertTrue(TestViews.tileHas(image, raids.get(0), new Color(0, 255, 0)));
		assertNotNull("Theatre reads 0/4 in red", TestViews.textAt(image, "0/4", count, new Color(255, 0, 0)));
		assertTrue(TestViews.tileHas(image, raids.get(1), new Color(255, 0, 0)));
		assertNotNull("Tombs reads 2/4 in yellow", TestViews.textAt(image, "2/4", count, new Color(255, 255, 0)));
		assertTrue(TestViews.tileHas(image, raids.get(2), new Color(255, 255, 0)));

		// The Slayer section's own line, in the card's small font under the section's bold title, wherever it scrolls to.
		Point obtained = TestViews.textAt(image, "1/5", FontManager.getRunescapeSmallFont(), new Color(255, 255, 0));
		if (obtained == null)
		{
			pvpTiles(tip);
			obtained = TestViews.textAt(render(tip), "1/5", FontManager.getRunescapeSmallFont(), new Color(255, 255, 0));
		}
		assertNotNull("Slayer's Obtained line reads 1/5 in yellow", obtained);

		int withLog = TooltipController.cardImage(tip).getHeight();
		// With no log there is no such line, and the card is a line shorter for it.
		PvmSummaryTooltip bare = new PvmSummaryTooltip();
		bare.setData(110.5, 2_500, 40, PanelData.bossCount(), "Vorkath", 900);
		bare.setRaids(hiscore, null);
		bare.setSlayer(hiscore, null);
		assertNull(TestViews.textAt(render(painted(bare)), "1/5", FontManager.getRunescapeSmallFont(), new Color(255, 255, 0)));
		assertEquals(NativeTooltip.LINE_HEIGHT, withLog - TooltipController.cardImage(bare).getHeight());
	}

	@Test
	public void aComparisonsEmptyCombatAchievementLinesAreMutedToo()
	{
		// The side with no Combat Achievements keeps the lines so the pair lines up, each a muted "--".
		PvmSummaryTooltip with = pvmCard(easy());
		PvmSummaryTooltip without = pvmCard(null);
		PvmSummaryTooltip.lineUp(with, without);
		BufferedImage image = TooltipController.cardImage(without);
		java.awt.Font small = FontManager.getRunescapeSmallFont();
		assertNotNull(TestViews.textAt(image, "--", small, TitleTooltip.MUTED_GRAY));
		assertNull(TestViews.textAt(image, "--", small, NativeTooltip.valueColor));
	}

	@Test
	public void aPvpTileWithALogPageWearsThatLogsBar()
	{
		Map<String, Integer> pages = new java.util.LinkedHashMap<>();
		pages.put("last_man_standing", 4);
		pages.put("soul_wars", 3);
		Map<String, Integer> held = new HashMap<>();
		held.put("last_man_standing", 2);
		Map<String, Integer> scores = new HashMap<>();
		for (HiscoreSkill activity : PanelData.PVP_ACTIVITIES)
		{
			scores.put(activity.getName(), 500);
		}
		PvmSummaryTooltip tip = card();
		tip.setPvp(TestViews.hiscore(AccountType.REGULAR, Collections.emptyMap(), Collections.emptyMap(), scores,
			Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0, -1), TestViews.log("Probe", pages, held), null);
		painted(tip);
		List<Point> pvp = pvpTiles(tip);
		BufferedImage image = render(tip);
		Color track = NativeTooltip.railTrack(GameTile.BAR_TRACK);
		assertTrue("LMS wears its log's bar", TestViews.tileHas(image, pvp.get(0), track));
		assertTrue("half full, in yellow", TestViews.tileHas(image, pvp.get(0), new Color(255, 255, 0)));
		assertTrue("Soul Wars wears its own, empty", TestViews.tileHas(image, pvp.get(1), track));
		assertFalse(TestViews.tileHas(image, pvp.get(1), new Color(255, 255, 0)));
		assertFalse("PvP Arena has no log page, so no bar", TestViews.tileHas(image, pvp.get(2), track));
	}

	/** The rows a divider runs across the card in, top down. */
	private static List<Integer> rules(BufferedImage image)
	{
		return TestViews.rules(image);
	}

	/** Items as the client draws them, each its own colour: Dizana's quiver cyan, any cape magenta. */
	private static net.runelite.client.game.ItemManager gearItems()
	{
		net.runelite.client.game.ItemManager items = org.mockito.Mockito.mock(net.runelite.client.game.ItemManager.class);
		org.mockito.Mockito.when(items.getImage(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.anyBoolean())).thenAnswer(invocation ->
		{
			net.runelite.client.util.AsyncBufferedImage image = new net.runelite.client.util.AsyncBufferedImage(
				org.mockito.Mockito.mock(net.runelite.client.callback.ClientThread.class), 32, 32, BufferedImage.TYPE_INT_ARGB);
			int argb = (int) invocation.getArgument(0) == PanelData.DIZANAS_QUIVER_ITEM_ID ? 0xff00ffff : 0xffff00ff;
			for (int y = 0; y < 32; y++)
			{
				for (int x = 0; x < 32; x++)
				{
					image.setRGB(x, y, argb);
				}
			}
			return image;
		});
		return items;
	}

	private static BufferedImage solid(int width, int height, int argb)
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				image.setRGB(x, y, argb);
			}
		}
		return image;
	}

	/** The top-left of the first pixel of this colour, or null. */
	private static Point find(BufferedImage image, int argb)
	{
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (image.getRGB(x, y) == argb)
				{
					return new Point(x, y);
				}
			}
		}
		return null;
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

	private static PvmSummaryTooltip pvmCard(CombatAchievementResult ca)
	{
		PvmSummaryTooltip tip = new PvmSummaryTooltip();
		tip.setData(126, 12345, 20, 60, "Zulrah", 5000);
		tip.setCombatAchievements(ca, gearItems());
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
	public void raidTilesAnswerOnlyWhereTheyWerePainted()
	{
		PvmSummaryTooltip tip = card();
		// Before the first paint there is no raid section to answer.
		assertEquals(-1, TestViews.tileAt(tip, "tiles", 20, 200));

		List<Point> raids = raidTiles(render(painted(tip)));
		// CoX, ToB and ToA, three across; The Fractured Archive's waits for its hiscore row, which this lookup hasn't.
		assertEquals(3, raids.size());
		for (int row = 0; row < 3; row++)
		{
			Point tile = raids.get(row);
			assertEquals(row, TestViews.tileAt(tip, "tiles", tile.x, tile.y));
			assertEquals(row, TestViews.tileAt(tip, "tiles", tile.x + GameTile.WIDTH - 1, tile.y + GameTile.HEIGHT - 1));
		}
		assertEquals(-1, TestViews.tileAt(tip, "tiles", raids.get(0).x + 10, raids.get(0).y - 1));
		assertEquals(-1, TestViews.tileAt(tip, "tiles", raids.get(0).x + GameTile.WIDTH + 1, raids.get(0).y + 10));
	}

	@Test
	public void aHoveredRaidTileLightensItselfAndNothingElse()
	{
		PvmSummaryTooltip tip = painted(card());
		BufferedImage resting = render(tip);
		Point tob = raidTiles(resting).get(1);
		move(tip, tob.x + 10, tob.y + 10);
		assertEquals(1, TestViews.hoveredRow(tip, "tiles"));
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
		assertEquals(-1, TestViews.hoveredRow(tip, "tiles"));
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
		tip.setMegarares(new int[]{1, 0, 0, 0}, null);
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
	public void aRaidTileBeforeAnyLookupSpacesItsNameAndSpriteEvenly()
	{
		// Nothing under its sprite: the name's ink starts 12 px down where a looked-up tile's starts at 6, and the
		// sprite's middle sits at 50, as killclog.com draws its dormant raid tiles (Dylan, 2026-11-17).
		PvmSummaryTooltip dormant = new PvmSummaryTooltip();
		dormant.setData(0, 0, 0, PanelData.bossCount(), null, 0);
		dormant.setMegarares(new int[4], squareSprites());
		BufferedImage image = render(painted(dormant));
		Point cox = raidTiles(image).get(0);
		assertEquals(12, firstRow(image, cox, rgb -> rgb == NativeTooltip.labelColor.getRGB()) - cox.y);
		int top = firstRow(image, cox, PvmSummaryTooltipTest::magenta);
		int bottom = top;
		while (rowHas(image, cox, bottom + 1, PvmSummaryTooltipTest::magenta))
		{
			bottom++;
		}
		assertEquals("the sprite's middle", 50, (top + bottom + 1) / 2 - cox.y);

		BufferedImage looked = render(painted(card()));
		Point scored = raidTiles(looked).get(0);
		assertEquals("a looked-up tile's name keeps its place", 6,
			firstRow(looked, scored, rgb -> rgb == NativeTooltip.labelColor.getRGB()) - scored.y);
	}

	/** Every item a square of magenta, 32 px a side, as the client hands item sprites over. */
	private static net.runelite.client.game.ItemManager squareSprites()
	{
		net.runelite.client.game.ItemManager items = org.mockito.Mockito.mock(net.runelite.client.game.ItemManager.class);
		org.mockito.Mockito.when(items.getImage(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation ->
		{
			net.runelite.client.util.AsyncBufferedImage image = new net.runelite.client.util.AsyncBufferedImage(
				org.mockito.Mockito.mock(net.runelite.client.callback.ClientThread.class), 32, 32, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 32; y++)
			{
				for (int x = 0; x < 32; x++)
				{
					image.setRGB(x, y, 0xffff00ff);
				}
			}
			return image;
		});
		return items;
	}

	/** Magenta, faint or not: red and blue well over green, unlike the fill, the frame and the orange names. */
	private static boolean magenta(int rgb)
	{
		Color c = new Color(rgb);
		return c.getRed() > c.getGreen() + 30 && c.getBlue() > c.getGreen() + 30;
	}

	/** The first row of a tile, from its top, holding a pixel that matches; -1 for none. */
	private static int firstRow(BufferedImage image, Point tile, java.util.function.IntPredicate match)
	{
		for (int y = tile.y; y < tile.y + GameTile.HEIGHT; y++)
		{
			if (rowHas(image, tile, y, match))
			{
				return y;
			}
		}
		return -1;
	}

	private static boolean rowHas(BufferedImage image, Point tile, int y, java.util.function.IntPredicate match)
	{
		for (int x = tile.x; x < tile.x + GameTile.WIDTH && y < image.getHeight(); x++)
		{
			if (match.test(image.getRGB(x, y)))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void theDormantCardShowsAllFourRaids()
	{
		// No lookup yet: the panel lists The Fractured Archive, so its tile stands with the other three.
		PvmSummaryTooltip dormant = new PvmSummaryTooltip();
		dormant.setData(0, 0, 0, PanelData.bossCount(), null, 0);
		assertTrue(PanelData.tfaListed());
		assertEquals(4, raidTiles(render(painted(dormant))).size());
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
		assertEquals(0, TestViews.hoveredRow(tip, "tiles"));
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseExited(event(tip, MouseEvent.MOUSE_EXITED, cox.x + 5, cox.y + 2, MouseEvent.NOBUTTON));
		}
		assertEquals(-1, TestViews.hoveredRow(tip, "tiles"));
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
		// The logs' bar is drawn where a tile's bar sits: with a log it adds pixels there, without one it doesn't.
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
		assertTrue("the logs' bar draws", changed > 0);

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
