package com.killclog;

import java.util.EnumSet;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SkillGridOrderTest
{
	@Test
	public void columnsReadDownLikeTheInGameSkillTab()
	{
		assertArrayEquals(new Skill[]{
			Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED,
			Skill.PRAYER, Skill.MAGIC, Skill.RUNECRAFT, Skill.CONSTRUCTION,
		}, column(0));
		assertArrayEquals(new Skill[]{
			Skill.HITPOINTS, Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING,
			Skill.CRAFTING, Skill.FLETCHING, Skill.SLAYER, Skill.HUNTER,
		}, column(1));
		assertArrayEquals(new Skill[]{
			Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING,
			Skill.FIREMAKING, Skill.WOODCUTTING, Skill.FARMING, Skill.SAILING,
		}, column(2));
	}

	@Test
	public void everySkillAppearsExactlyOnce()
	{
		assertEquals(SkillGridOrder.ROWS * SkillGridOrder.COLUMNS, SkillGridOrder.skills().size());
		assertEquals(EnumSet.copyOf(SkillGridOrder.skills()).size(), SkillGridOrder.skills().size());
	}

	private static Skill[] column(int column)
	{
		Skill[] skills = new Skill[SkillGridOrder.ROWS];
		for (int row = 0; row < SkillGridOrder.ROWS; row++)
		{
			skills[row] = SkillGridOrder.at(row, column);
		}
		return skills;
	}
}
