package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
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
		Field field = TitleTooltip.class.getDeclaredField("rankText");
		field.setAccessible(true);
		return (String) field.get(tip);
	}

	private static TitleTooltip tooltip(TooltipData data, HiscoreResult result, boolean enabled)
	{
		TitleTooltip tip = new ImgTooltip(5);
		tip.setTitle("Alchemical Hydra");
		ClogHelper.configureRank(tip, data, result, enabled);
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
		TooltipData knownKills = TooltipData.builder().rank(-1).statLabel("Kills: ").statValue(42).build();
		assertEquals("Unranked", rankText(tooltip(knownKills, result(-1), true)));
		assertNull(rankText(tooltip(knownKills, result(1).withRanks(null), true)));
		assertNull(rankText(tooltip(knownKills.toBuilder().statValue(-1).build(), result(-1), true)));
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
		blue.setInfoLine("KC: ", "1420", java.awt.Color.WHITE);
		red.setInfoLine("KC: ", "1420", java.awt.Color.WHITE);
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
