package com.killclog;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class SkillClogLayoutTest
{
	@Test
	public void defenceKeepsTwelveThreeSlotRowsAtRegularSizeEvenOnWideCards() throws Exception
	{
		List<SkillClogSection> sections = SkillClogSection.forSkill(Skill.DEFENCE, null, null, null);
		assertEquals(36, SkillClogSection.combinedProgress(sections, false).total());
		assertEquals(Arrays.asList("Inquisitor", "Bandos", "Justiciar", "Oathplate", "Torva"),
			sections.get(0).layout().rowLabels);
		assertEquals(Arrays.asList(11832, 11834, 11836), sections.get(0).itemIds().subList(3, 6));
		assertEquals(Arrays.asList(26376, 26378, 26380), sections.get(0).itemIds().subList(12, 15));
		assertEquals(Integer.valueOf(22477), sections.get(3).itemIds().get(8));
		for (int width : new int[]{100, 320})
		{
			List<TooltipItemHover.HitBox> boxes = paintSections(sections, width);
			assertEquals(36, boxes.size());
			for (int i = 0; i < boxes.size(); i += 3)
			{
				Rectangle first = bounds(boxes.get(i));
				for (int col = 0; col < 3; col++)
				{
					Rectangle slot = bounds(boxes.get(i + col));
					assertEquals(32, slot.width);
					assertEquals(32, slot.height);
					assertEquals(first.y, slot.y);
					assertEquals(first.x + col * 36, slot.x);
				}
			}
			assertEquals(bounds(boxes.get(0)).y + 36, bounds(boxes.get(3)).y);
			assertEquals(bounds(boxes.get(27)).y + 72, bounds(boxes.get(33)).y);
		}
	}

	@Test
	public void slayerPartitionsEverySlotAndKeepsMysticRowsAndSuperiorCounts() throws Exception
	{
		List<SkillClogSection> fixed = SkillClogSection.forSkill(Skill.SLAYER, null, null, null);
		List<Integer> ids = new ArrayList<>();
		for (SkillClogSection section : fixed) ids.addAll(section.itemIds());
		for (int id = 1; id <= 30; id++) ids.add(id);
		ClogResult player = new ClogResult("Blue", Map.of("slayer", Arrays.asList(
			new ClogResult.ClogItem(20724, 3, null), new ClogResult.ClogItem(4109, 1, null),
			new ClogResult.ClogItem(1, 2, null))), Map.of("slayer", ids), Collections.emptyMap(), null, null);
		List<SkillClogSection> sections = SkillClogSection.forSkill(Skill.SLAYER, player, null, player);
		assertEquals(3, sections.size());
		assertEquals(4, sections.get(0).itemIds().size());
		assertEquals(15, sections.get(1).itemIds().size());
		assertEquals(30, sections.get(2).itemIds().size());
		HashSet<Integer> unique = new HashSet<>();
		for (SkillClogSection section : sections)
		{
			for (int id : section.itemIds()) assertTrue("repeated slot " + id, unique.add(id));
			assertEquals(1, section.primary().obtainedCount());
			assertEquals(-1, section.compared().obtainedCount());
		}
		assertEquals(new HashSet<>(ids), unique);
		assertEquals(3, SkillClogSection.combinedProgress(sections, false).obtained());
		assertEquals(Integer.valueOf(3), sections.get(0).primary().obtainedCounts().get(20724));
		assertEquals(49, ids.size()); // The source catalog was not mutated.
		List<TooltipItemHover.HitBox> boxes = paintSections(sections, 100);
		for (int i = 0; i < 19; i++) assertEquals(32, bounds(boxes.get(i)).width);
		for (int i = 19; i < 49; i++) assertEquals(15, bounds(boxes.get(i)).width);
		for (int row = 0; row < 3; row++)
		{
			Rectangle first = bounds(boxes.get(4 + row * 5));
			for (int col = 0; col < 5; col++)
			{
				Rectangle slot = bounds(boxes.get(4 + row * 5 + col));
				assertEquals(first.y, slot.y);
				assertEquals(first.x + col * 36, slot.x);
			}
		}
	}

	@Test
	public void avernicCountsUnderBothSkillsWithoutChangingCanonicalTotals()
	{
		ClogResult player = new ClogResult("Blue", Map.of("theatre_of_blood",
			Arrays.asList(new ClogResult.ClogItem(22477, 2, null))),
			Map.of("theatre_of_blood", Arrays.asList(22477)), Collections.emptyMap(), null, null);
		for (Skill skill : new Skill[]{Skill.ATTACK, Skill.DEFENCE})
		{
			List<SkillClogSection> sections = SkillClogSection.forSkill(skill, player, null, null);
			assertEquals(1, SkillClogSection.combinedProgress(sections, false).obtained());
		}
		assertArrayEquals(new int[]{1, 1}, ClogHelper.sumClogTotals(player));
	}

	private static List<TooltipItemHover.HitBox> paintSections(List<SkillClogSection> sections,
		int availableWidth) throws Exception
	{
		SkillClogSectionRenderer renderer = new SkillClogSectionRenderer(new SkillTooltip());
		renderer.setSections(sections, null);
		Dimension size = renderer.soloSize(availableWidth);
		int width = size.width + TitleTooltip.getInset() * 2;
		BufferedImage image = new BufferedImage(width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		List<TooltipItemHover.HitBox> boxes = new ArrayList<>();
		assertEquals(size.height, renderer.paintSolo(graphics, width, 0, boxes));
		graphics.dispose();
		for (TooltipItemHover.HitBox box : boxes)
		{
			assertTrue(new Rectangle(0, 0, width, size.height).contains(bounds(box)));
		}
		return boxes;
	}

	private static Rectangle bounds(TooltipItemHover.HitBox box) throws Exception
	{
		Field field = TooltipItemHover.HitBox.class.getDeclaredField("bounds");
		field.setAccessible(true);
		return (Rectangle) field.get(box);
	}
}
