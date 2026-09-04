package com.killclog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;

/** Shared 3x8 in-game skill-tab order for every skill grid surface. */
final class SkillGridOrder
{
	static final int ROWS = 8;
	static final int COLUMNS = 3;

	private static final Skill[] ORDER = {
		Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
		Skill.DEFENCE, Skill.AGILITY, Skill.SMITHING,
		Skill.STRENGTH, Skill.HERBLORE, Skill.FISHING,
		Skill.RANGED, Skill.THIEVING, Skill.COOKING,
		Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
		Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
		Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
		Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
	};

	private static final List<Skill> SKILLS =
		Collections.unmodifiableList(Arrays.asList(ORDER));

	private SkillGridOrder()
	{
	}

	static Skill at(int row, int column)
	{
		return ORDER[row * COLUMNS + column];
	}

	static List<Skill> skills()
	{
		return SKILLS;
	}
}
