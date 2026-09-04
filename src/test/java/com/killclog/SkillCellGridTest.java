package com.killclog;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
			assertNotNull(label.getToolTipText());
		}
	}

	@Test
	public void soloCellsUseProgressionColorsAndOwnStatsTooltips()
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
		String attackTooltip = cells.labels().get(Skill.ATTACK).getToolTipText();
		assertTrue(attackTooltip.contains("Attack"));
		assertTrue(attackTooltip.contains("Level: {w}49"));
		assertTrue(attackTooltip.contains("XP: {w}1,000,049"));
		assertTrue(attackTooltip.contains("Rank: {w}#2,049"));
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
		assertTrue(attack.getToolTipText().contains("Blue: {w}99"));
		assertTrue(attack.getToolTipText().contains("Red: {w}98"));
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
			xps.put(key, 1_000_000L + level);
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
}
