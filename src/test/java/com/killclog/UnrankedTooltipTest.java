package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnrankedTooltipTest
{
	private static HiscoreResult result(int rank)
	{
		return RankSelectorTest.result(AccountType.IRONMAN, HiscoreTable.STANDARD, rank, 10000);
	}

	private static String rankText(TitleTooltip tip) throws Exception
	{
		return TestViews.headerLine(tip, "rank", "value");
	}

	private static TitleTooltip tooltip(TooltipData data, HiscoreResult result, boolean enabled)
	{
		TitleTooltip tip = new ImgTooltip(5);
		tip.setTitle("Alchemical Hydra");
		Cells.configureRank(tip, data, result, enabled);
		return tip;
	}

	@Test
	public void knownMissingRankIsUnrankedButAbsentLeaderboardIsNot() throws Exception
	{
		HiscoreResult nativeResult = result(10);
		HiscoreResult unranked = nativeResult.withRanks(result(-1));
		TooltipData synced = TooltipData.builder().name("Alchemical Hydra")
			.obtainedCount(3).totalItems(11).rank(-1).kc(1420).build();
		assertTrue(unranked.isRankDataAvailable());
		assertEquals(123, unranked.getKc("Zulrah"));
		assertEquals("Unranked", rankText(tooltip(synced, unranked, true)));
		assertNull(rankText(tooltip(synced, nativeResult.withRanks(null), true)));
		assertFalse(nativeResult.withRanks(null).isRankDataAvailable());
		assertNull(rankText(tooltip(synced, null, true)));
		assertNull(rankText(tooltip(synced, unranked, false)));
		assertNull(rankText(tooltip(synced.toBuilder().rankTracked(false).build(), unranked, true)));
		assertNull(rankText(tooltip(null, unranked, true)));
		assertEquals("12,345", rankText(tooltip(synced.toBuilder().rank(12345).build(), result(12345), true)));
	}

	@Test
	public void unsyncedKeepsItsPlayerSignalAndCatalogOnlyRules() throws Exception
	{
		TooltipData knownKills = TooltipData.builder().rank(-1).kc(42).build();
		assertEquals("Unranked", rankText(tooltip(knownKills, result(-1), true)));
		assertNull(rankText(tooltip(knownKills, result(1).withRanks(null), true)));
		assertNull(rankText(tooltip(knownKills.toBuilder().kc(-1).build(), result(-1), true)));
	}

	// One look (Dylan, 2026-11-21): where a card has no value it writes "--", and for a player the hiscores answered
	// without a rank it writes "Unranked", both in the cards' one muted grey, never in a number's white.
	@Test
	public void aMissingValueAndAnUnrankedPlayerReadOneWayOnEveryCard()
	{
		assertEquals(TitleTooltip.MUTED_GRAY, TitleTooltip.ink("--"));
		assertEquals(TitleTooltip.MUTED_GRAY, TitleTooltip.ink("Unranked"));
		assertEquals(NativeTooltip.valueColor, TitleTooltip.ink("0"));
		assertEquals("Unranked", TitleTooltip.rankText(-1, true));
		assertEquals("--", TitleTooltip.rankText(-1, false));
		assertEquals("12,345", TitleTooltip.rankText(12345, true));

		java.awt.Font small = net.runelite.client.ui.FontManager.getRunescapeSmallFont();
		java.awt.Color muted = TitleTooltip.MUTED_GRAY;
		// A player the hiscores do not rank: Experience & Efficiency, the Combat Summary's Slayer and a card's header.
		SkillsTooltip totals = new SkillsTooltip();
		totals.setData(result(-1), null, false);
		assertNotNull(TestViews.textAt(TestViews.paint(totals), "Unranked", small, muted));
		PvmSummaryTooltip combat = new PvmSummaryTooltip();
		combat.setData(110.5, 2_500, 40, PanelData.bossCount(), "Vorkath", 900);
		combat.setRaids(result(-1), null);
		combat.setSlayer(result(-1), null);
		assertNotNull(TestViews.textAt(TestViews.paint(combat), "Unranked", small, muted));
		ImgTooltip boss = new ImgTooltip(5);
		boss.setTitle("Zulrah");
		boss.setRank(0);
		// A raid's hard mode beside it, unranked as well.
		boss.setRankPair("CM: ", 0);
		BufferedImage header = TestViews.paint(boss);
		assertNotNull(TestViews.textAt(header, "Unranked", small, muted));
		assertNull("not in the label's orange", TestViews.textAt(header, "Unranked", small, NativeTooltip.labelColor));
		assertNull("nor a number's white", TestViews.textAt(header, "Unranked", small, NativeTooltip.valueColor));

		// Until the hiscores answer, the same lines read "--": a rank nobody has told us is not Unranked.
		HiscoreResult unanswered = result(10).withRanks(null);
		SkillsTooltip waiting = new SkillsTooltip();
		waiting.setData(unanswered, null, false);
		assertNull(TestViews.textAt(TestViews.paint(waiting), "Unranked", small, muted));
		PvmSummaryTooltip waitingCombat = new PvmSummaryTooltip();
		waitingCombat.setData(110.5, 2_500, 40, PanelData.bossCount(), "Vorkath", 900);
		waitingCombat.setRaids(unanswered, null);
		waitingCombat.setSlayer(unanswered, null);
		assertNull(TestViews.textAt(TooltipController.cardImage(waitingCombat), "Unranked", small, muted));

		// Before any lookup every card's "--" is muted, and none is a number's white.
		PvmSummaryTooltip restingCombat = new PvmSummaryTooltip();
		restingCombat.setData(-1, -1, -1, PanelData.bossCount(), null, 0);
		restingCombat.setSlayer(null, null);
		SkillsTooltip restingTotals = new SkillsTooltip();
		restingTotals.setData(null, null, false);
		SkillTooltip restingSkill = new SkillTooltip();
		restingSkill.setData(Skill.ATTACK, null, false, null, null);
		for (javax.swing.JToolTip card : new javax.swing.JToolTip[]{restingCombat, restingTotals, restingSkill})
		{
			// The whole card, as Copy holds it: the Combat Summary's PvP tiles sit under its scroll.
			BufferedImage image = TooltipController.cardImage(card);
			assertNull("a tile's count too", TestViews.textAt(image, "--", net.runelite.client.ui.FontManager.getRunescapeFont(),
				NativeTooltip.valueColor));
			assertNotNull(card.getClass().getSimpleName(), TestViews.textAt(image, "--", small, muted));
			assertNull(card.getClass().getSimpleName(), TestViews.textAt(image, "--", small, NativeTooltip.valueColor));
		}
		// The Combat Summary's level beside its title too: grey there, with nothing near a number's white (the title's
		// larger font is smoothed, so this reads shades where the lines above read exact pixels).
		BufferedImage top = TestViews.paint(restingCombat);
		int grey = 0;
		for (int y = 0; y < TestViews.rules(top).get(0); y++)
		{
			for (int x = top.getWidth() / 2; x < top.getWidth(); x++)
			{
				java.awt.Color c = new java.awt.Color(top.getRGB(x, y));
				assertFalse("no white at " + x + "," + y, Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue())) > 200);
				grey += c.getRed() >= 110 && c.getRed() - c.getBlue() < 25 ? 1 : 0;
			}
		}
		assertTrue("the level's \"--\" is drawn", grey > 0);
	}

	@Test
	public void skillsDistinguishUnrankedFromLoadingAndDormant()
	{
		assertEquals("Unranked", SkillTooltip.Stats.from(Skill.ATTACK, result(-1), false).rankText());
		assertEquals("--", SkillTooltip.Stats.from(Skill.ATTACK, result(10).withRanks(null), false).rankText());
		assertEquals("--", SkillTooltip.Stats.from(Skill.ATTACK, null, false).rankText());
		assertEquals("12,345", SkillTooltip.Stats.from(Skill.ATTACK, result(12345), false).rankText());
	}

	@Test
	public void clueSummaryDoesNotCallPendingRanksUnranked() throws Exception
	{
		ClueSummaryTooltip tip = new ClueSummaryTooltip();
		tip.setData(result(10).withRanks(null), true);
		assertNull(rankText(tip));
		tip = new ClueSummaryTooltip();
		tip.setData(result(-1), true);
		assertEquals("Unranked", rankText(tip));
		tip = new ClueSummaryTooltip();
		tip.setData(result(-1), false);
		assertNull(rankText(tip));
	}

	@Test
	public void comparisonRendersTheSameUnrankedLineAsSolo() throws Exception
	{
		TooltipData data = TooltipData.builder().obtainedCount(3).totalItems(11).build();
		TitleTooltip blue = tooltip(data, result(-1), true);
		TitleTooltip red = tooltip(data.toBuilder().rank(12345).build(), result(12345), true);
		blue.addInfoLine("KC: ", "1420", java.awt.Color.WHITE);
		red.addInfoLine("KC: ", "1420", java.awt.Color.WHITE);
		SideBySideTooltip pair = new SideBySideTooltip("Unranked", blue, "Ranked", red);
		assertEquals("Unranked", rankText(blue));
		assertEquals("12,345", rankText(red));
		Dimension size = pair.getPreferredSize();
		assertTrue(size.width <= 765);
		pair.setSize(size);
		BufferedImage preview = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = preview.createGraphics();
		pair.paint(graphics);
		graphics.dispose();
		File output = new File("build/reports/unranked-tooltip-preview.png");
		assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
		assertTrue(ImageIO.write(preview, "png", output));
	}
}
