package com.killclog;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.JLabel;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SkillCellGridTest
{
	@Test
	public void buildsTwentyFourIndependentCellsInGameOrder()
	{
		SkillCellGrid cells = grid();
		assertEquals(24, cells.grid().getComponentCount());
		assertEquals(24, cells.labels().size());
		assertEquals(Skill.ATTACK, new ArrayList<>(cells.labels().keySet()).get(0));
		assertEquals(Skill.HITPOINTS, new ArrayList<>(cells.labels().keySet()).get(1));
		assertEquals(Skill.MINING, new ArrayList<>(cells.labels().keySet()).get(2));
		assertEquals(Skill.SAILING, new ArrayList<>(cells.labels().keySet()).get(23));
		for (JLabel label : cells.labels().values())
		{
			assertEquals(" ", label.getToolTipText());
		}
	}

	@Test
	public void soloCellsUseProgressionColorsAndNativeTooltips()
	{
		SkillCellGrid cells = grid();
		HiscoreResult result = hiscores(Map.of(
			Skill.ATTACK, 49,
			Skill.HITPOINTS, 50,
			Skill.MINING, 93,
			Skill.DEFENCE, 99));
		cells.render(result, null, false);

		assertEquals(config().emptyClogColor(),
			cells.labels().get(Skill.ATTACK).getForeground());
		assertEquals(config().inProgressClogColor(),
			cells.labels().get(Skill.HITPOINTS).getForeground());
		assertEquals(config().missing1Color(),
			cells.labels().get(Skill.MINING).getForeground());
		assertEquals(config().completedClogColor(),
			cells.labels().get(Skill.DEFENCE).getForeground());

		SkillTooltip attack = (SkillTooltip) cells.labels().get(Skill.ATTACK).createToolTip();
		long attackXp = Experience.getXpForLevel(49) + 10L;
		assertEquals("49", attack.stats().levelText());
		assertEquals(format(attackXp), attack.stats().xpText());
		assertEquals("2,049", attack.stats().rankText());
		assertEquals(format(Experience.getXpForLevel(50) - attackXp),
			attack.stats().xpToLevelText());

		SkillTooltip defence = (SkillTooltip) cells.labels().get(Skill.DEFENCE).createToolTip();
		assertEquals("Maxed", defence.stats().xpToLevelText());
	}

	@Test
	public void comparisonCellsKeepBlueAndRedIdentity()
	{
		SkillCellGrid cells = grid();
		cells.render(hiscores(Map.of(Skill.ATTACK, 99)),
			hiscores(Map.of(Skill.ATTACK, 98)), false);

		JLabel attack = cells.labels().get(Skill.ATTACK);
		assertTrue(attack.getText().contains("#5ba4cf"));
		assertTrue(attack.getText().contains("#e05656"));
		assertTrue(attack.getText().contains("99"));
		assertTrue(attack.getText().contains("98"));
		assertEquals(TitleTooltip.COMPARE_BLUE, attack.getForeground());

		CompareSkillTooltip tooltip = (CompareSkillTooltip) attack.createToolTip();
		assertEquals("99", tooltip.blueStats().levelText());
		assertEquals("98", tooltip.redStats().levelText());
		assertEquals("Maxed", tooltip.blueStats().xpToLevelText());
		assertEquals(format(Experience.getXpForLevel(99)
			- Experience.getXpForLevel(98) - 10L), tooltip.redStats().xpToLevelText());
	}

	@Test
	public void xpToLevelFollowsRealAndVirtualLevelCaps()
	{
		SkillTooltip fresh = new SkillTooltip();
		fresh.setData(Skill.HERBLORE, skillHiscores(Skill.HERBLORE, 1, 0, -1), false);
		assertEquals("0", fresh.stats().xpText());
		assertEquals("--", fresh.stats().rankText());
		assertEquals(format(Experience.getXpForLevel(2)), fresh.stats().xpToLevelText());

		long virtualXp = 32_643_866L;
		int virtualLevel = Experience.getLevelForXp((int) virtualXp);
		SkillTooltip virtual = new SkillTooltip();
		virtual.setData(Skill.HERBLORE,
			skillHiscores(Skill.HERBLORE, 99, virtualXp, 1_075), true);
		assertEquals(String.valueOf(virtualLevel), virtual.stats().levelText());
		assertEquals(format(Experience.getXpForLevel(virtualLevel + 1) - virtualXp),
			virtual.stats().xpToLevelText());

		SkillTooltip capped = new SkillTooltip();
		capped.setData(Skill.HERBLORE,
			skillHiscores(Skill.HERBLORE, 99, Experience.MAX_SKILL_XP, 1), true);
		assertEquals(String.valueOf(Experience.MAX_VIRT_LEVEL), capped.stats().levelText());
		assertEquals("Maxed", capped.stats().xpToLevelText());
	}

	private static SkillCellGrid grid()
	{
		KillClogConfig config = config();
		return new SkillCellGrid(new SkillIconManager(),
			new TooltipController(config), config);
	}

	private static HiscoreResult hiscores(Map<Skill, Integer> sampleLevels)
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Integer> ranks = new HashMap<>();
		Map<String, Long> xps = new HashMap<>();
		for (Skill skill : SkillGridOrder.skills())
		{
			int level = sampleLevels.getOrDefault(skill, 1);
			String key = skill.getName().toLowerCase();
			levels.put(key, level);
			ranks.put(key, 2_000 + level);
			xps.put(key, Experience.getXpForLevel(level) + 10L);
		}
		return new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), levels, ranks, xps, 1_200, 24_000_000L, 85, 10_000);
	}

	private static KillClogConfig config()
	{
		return new KillClogConfig()
		{
			@Override
			public boolean completionistHighlighter()
			{
				return true;
			}

			@Override
			public Color completedClogColor()
			{
				return new Color(78, 240, 21);
			}

			@Override
			public Color missing1Color()
			{
				return new Color(202, 255, 0);
			}

			@Override
			public Color inProgressClogColor()
			{
				return new Color(255, 173, 0);
			}

			@Override
			public Color emptyClogColor()
			{
				return new Color(255, 87, 0);
			}
		};
	}

	private static HiscoreResult skillHiscores(Skill skill, int level, long xp, int rank)
	{
		String key = skill.getName().toLowerCase(Locale.ROOT);
		return new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), Map.of(key, level), Map.of(key, rank), Map.of(key, xp),
			level, xp, 3, 100);
	}

	private static String format(long value)
	{
		return String.format(Locale.US, "%,d", value);
	}
}
