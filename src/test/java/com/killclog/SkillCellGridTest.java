package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.JLabel;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
		boolean foundIcon = false;
		for (JLabel label : cells.labels().values())
		{
			assertEquals(" ", label.getToolTipText());
			if (label.getIcon() != null)
			{
				foundIcon = true;
				assertEquals(SkillCellGrid.SKILL_ICON_SIZE,
					label.getIcon().getIconWidth());
				assertEquals(SkillCellGrid.SKILL_ICON_SIZE,
					label.getIcon().getIconHeight());
			}
		}
		assertTrue(foundIcon);
	}

	@Test
	public void clearRetainsBlankCellsForDormantLayouts()
	{
		SkillCellGrid cells = grid();
		cells.render(hiscores(Map.of(Skill.ATTACK, 99)), null, false);
		cells.clear();

		assertEquals(24, cells.grid().getComponentCount());
		for (JLabel label : cells.labels().values())
		{
			assertEquals(ClogHelper.pad("--"), label.getText());
			assertEquals(ColorScheme.LIGHT_GRAY_COLOR, label.getForeground());
			assertEquals(JLabel.LEADING, label.getHorizontalAlignment());
		}
	}

	@Test
	public void dormantCellsRetainTheCatalogForFullTooltipPreviews()
	{
		SkillCellGrid cells = grid();
		ClogResult catalog = new ClogResult("Catalog", Collections.emptyMap(),
			Collections.singletonMap("barbarian_assault", java.util.List.of(1, 2)),
			Map.of(1, "Fighter torso", 2, "Granite body"), null, null);

		cells.clear(catalog);

		SkillTooltip attack = (SkillTooltip) cells.labels().get(Skill.ATTACK).createToolTip();
		assertEquals(" (--/2)", attack.getTitleSuffix());
		assertEquals(1, attack.sections().size());
		SkillClogSection section = attack.sections().get(0);
		assertEquals("--/2", SkillClogSectionRenderer.progressText(
			section.primary(), section.itemIds().size()));
		assertTrue(section.primary().obtainedIds().isEmpty());
	}

	@Test
	public void soloCellsUseSkillColorsAndNativeTooltips()
	{
		SkillCellGrid cells = grid();
		HiscoreResult result = hiscores(Map.of(
			Skill.ATTACK, 49,
			Skill.HITPOINTS, 50,
			Skill.MINING, 93,
			Skill.DEFENCE, 99));
		ClogResult clog = emptyClog();
		cells.render(result, null, false, clog, null, clog);

		assertEquals(config().skillLevelColor(),
			cells.labels().get(Skill.ATTACK).getForeground());
		assertEquals(config().skillLevelColor(),
			cells.labels().get(Skill.HITPOINTS).getForeground());
		assertEquals(config().skillLevelColor(),
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
	public void unsyncedSoloCellsKeepNormalWhiteNumbers()
	{
		SkillCellGrid cells = grid();
		HiscoreResult result = hiscores(Map.of(
			Skill.ATTACK, 50,
			Skill.DEFENCE, 99));
		cells.render(result, null, false, null, null, emptyClog());

		assertEquals(Cells.KC_COLOR,
			cells.labels().get(Skill.ATTACK).getForeground());
		assertEquals(Cells.KC_COLOR,
			cells.labels().get(Skill.DEFENCE).getForeground());
	}

	@Test
	public void clogProgressionColorsUseTheCombinedSkillTitleTotal()
	{
		KillClogConfig progressionConfig = new KillClogConfig()
		{
			@Override
			public SkillColorMode skillColorMode()
			{
				return SkillColorMode.CLOG_PROGRESSION;
			}
		};
		SkillCellGrid cells = grid(progressionConfig);
		java.util.List<Integer> allItems = java.util.List.of(101, 102, 103, 104);
		java.util.List<ClogResult.ClogItem> obtained = java.util.List.of(
			new ClogResult.ClogItem(101, 1, null),
			new ClogResult.ClogItem(102, 1, null),
			new ClogResult.ClogItem(103, 1, null));
		ClogResult clog = new ClogResult("Tester",
			Collections.singletonMap("barbarian_assault", obtained),
			Collections.singletonMap("barbarian_assault", allItems),
			Collections.emptyMap(), null, null);

		cells.render(hiscores(Map.of(Skill.ATTACK, 99)), null, false,
			clog, null, clog);

		assertEquals(progressionConfig.missing1Color(),
			cells.labels().get(Skill.ATTACK).getForeground());
		SkillTooltip tooltip = (SkillTooltip) cells.labels().get(Skill.ATTACK).createToolTip();
		assertEquals(" (3/4)", tooltip.getTitleSuffix());
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

		SideBySideTooltip tooltip = (SideBySideTooltip) attack.createToolTip();
		SkillTooltip blueSide = (SkillTooltip) tooltip.sides()[0];
		SkillTooltip redSide = (SkillTooltip) tooltip.sides()[1];
		assertEquals("99", blueSide.stats().levelText());
		assertEquals("98", redSide.stats().levelText());
		assertEquals("Maxed", blueSide.stats().xpToLevelText());
		assertEquals(format(Experience.getXpForLevel(99)
			- Experience.getXpForLevel(98) - 10L), redSide.stats().xpToLevelText());
	}

	@Test
	public void runecraftTooltipOwnsTheRiftsClosedReadout()
	{
		SkillCellGrid cells = grid();
		HiscoreResult blue = hiscores(Map.of(Skill.RUNECRAFT, 93),
			Collections.singletonMap(PanelData.RIFTS_CLOSED_ACTIVITY, 34));
		HiscoreResult red = hiscores(Map.of(Skill.RUNECRAFT, 91),
			Collections.singletonMap(PanelData.RIFTS_CLOSED_ACTIVITY, 1_234));
		String petName = "Rift guardian";
		String gotrName = "Abyssal needle";
		ClogResult clog = new ClogResult("Tester", Collections.emptyMap(),
			Collections.singletonMap(PanelData.GOTR_CATEGORY, Collections.singletonList(1)),
			Map.of(1, gotrName, 20665, petName), null, null);

		cells.render(blue, null, false, clog, null, clog);
		SkillTooltip runecraft = (SkillTooltip) cells.labels().get(Skill.RUNECRAFT).createToolTip();
		SkillTooltip herblore = (SkillTooltip) cells.labels().get(Skill.HERBLORE).createToolTip();
		assertTrue(runecraft.showsRiftsClosed());
		assertEquals("34", runecraft.riftsClosedText());
		assertEquals(" (0/2)", runecraft.getTitleSuffix());
		assertFalse(herblore.showsRiftsClosed());
		SkillTooltip withoutRifts = new SkillTooltip();
		withoutRifts.setData(Skill.RUNECRAFT, blue, false, runecraft.sections(), null);
		paint(withoutRifts);
		paint(runecraft);
		assertEquals(hoverItemY(withoutRifts, petName), hoverItemY(runecraft, petName));
		assertTrue(hoverItemY(runecraft, gotrName) > hoverItemY(withoutRifts, gotrName));

		cells.render(blue, red, false, clog, clog, clog);
		SideBySideTooltip comparison = (SideBySideTooltip) cells.labels()
			.get(Skill.RUNECRAFT).createToolTip();
		SkillTooltip blueSide = (SkillTooltip) comparison.sides()[0];
		SkillTooltip redSide = (SkillTooltip) comparison.sides()[1];
		assertTrue(blueSide.showsRiftsClosed());
		assertEquals("34", blueSide.riftsClosedText());
		assertEquals("1,234", redSide.riftsClosedText());
	}

	@Test
	public void slayerCellAddsTheReusableClogSectionOnlyToSlayer()
	{
		SkillCellGrid cells = grid();
		HiscoreResult result = hiscores(Map.of(Skill.SLAYER, 99));
		String longItemName = "Abyssal orphaned helmet of excessive naming";
		ArrayList<Integer> slayerItems = new ArrayList<>();
		for (int itemId = 1; itemId <= 30; itemId++)
		{
			slayerItems.add(itemId);
		}
		ClogResult clog = new ClogResult("Tester",
			Collections.singletonMap(PanelData.SLAYER_CATEGORY,
				Collections.singletonList(new ClogResult.ClogItem(2, 4, null))),
			Collections.singletonMap(PanelData.SLAYER_CATEGORY,
				slayerItems),
			Collections.singletonMap(2, longItemName), null, null);
		cells.render(result, null, false, clog, null, clog);

		SkillTooltip slayer = (SkillTooltip) cells.labels().get(Skill.SLAYER).createToolTip();
		SkillTooltip attack = (SkillTooltip) cells.labels().get(Skill.ATTACK).createToolTip();

		assertEquals(1, slayer.sections().size());
		assertEquals(30, slayer.sections().get(0).itemIds().size());
		assertFalse(slayer.sections().get(0).hasHeading());
		assertEquals("Slayer", slayer.getTitle());
		assertEquals(" (1/30)", slayer.getTitleSuffix());
		assertEquals(TitleTooltip.CLOG_YELLOW, slayer.getTitleSuffixColor());
		assertEquals(attack.getHeaderHeight() + TitleTooltip.LINE_HEIGHT,
			slayer.getHeaderHeight());
		assertFalse(attack.sections().iterator().hasNext());
		assertTrue(slayer.getPreferredSize().height > attack.getPreferredSize().height);

		paint(slayer);
		assertTrue(hoverItem(slayer, longItemName));
		assertEquals(longItemName, slayer.getHeaderHoverLineText());
		assertEquals("x4", slayer.getHeaderHoverLineRightText());
	}

	@Test
	public void sparseSkillClogsKeepDuplicateCountsOffTheHoverLine()
	{
		SkillCellGrid cells = grid();
		HiscoreResult result = hiscores(Map.of(Skill.ATTACK, 99));
		String itemName = "Fighter torso";
		ClogResult clog = new ClogResult("Tester",
			Collections.singletonMap("barbarian_assault",
				Collections.singletonList(new ClogResult.ClogItem(2, 4, null))),
			Collections.singletonMap("barbarian_assault",
				Collections.singletonList(2)),
			Collections.singletonMap(2, itemName), null, null);
		cells.render(result, null, false, clog, null, clog);

		SkillTooltip attack = (SkillTooltip) cells.labels().get(Skill.ATTACK).createToolTip();
		paint(attack);
		assertTrue(hoverItem(attack, itemName));
		assertEquals(itemName, attack.getHeaderHoverLineText());
		assertNull(attack.getHeaderHoverLineRightText());
	}

	@Test
	public void everySectionStartsAtTheLeftGridEdge()
	{
		SkillCellGrid cells = grid();
		String petName = "Rift guardian";
		String pickpocketName = "Enhanced crystal teleport seed";
		Map<Integer, String> names = new HashMap<>();
		names.put(20665, petName);
		names.put(23959, pickpocketName);
		ClogResult clog = new ClogResult("Tester", Collections.emptyMap(),
			Collections.singletonMap(PanelData.GOTR_CATEGORY,
				Collections.singletonList(1)), names, null, null);
		cells.render(hiscores(Collections.emptyMap()), null, false, clog, null, clog);

		SkillTooltip runecraft = (SkillTooltip) cells.labels().get(Skill.RUNECRAFT).createToolTip();
		paint(runecraft);
		assertEquals(TitleTooltip.getInset(), hoverItemX(runecraft, petName));
		SkillTooltip thieving = (SkillTooltip) cells.labels().get(Skill.THIEVING).createToolTip();
		paint(thieving);
		assertEquals(TitleTooltip.getInset(), hoverItemX(thieving, pickpocketName));

		SkillTooltip left = new SkillTooltip();
		left.setData(Skill.RUNECRAFT, null, false,
			SkillClogSection.forSkill(Skill.RUNECRAFT, clog, clog, clog), null);
		SkillTooltip right = new SkillTooltip();
		right.setData(Skill.RUNECRAFT, null, false,
			SkillClogSection.forSkill(Skill.RUNECRAFT, clog, clog, clog), null);
		SideBySideTooltip comparison = new SideBySideTooltip("Blue", left, "Red", right);
		comparison.setSize(comparison.getPreferredSize());
		comparison.doLayout();
		// Each side is the ordinary solo tooltip, so the grid keeps its inset
		// alignment inside its own card.
		assertEquals(NativeTooltip.getInset(), left.getX());
		paint(left);
		assertEquals(TitleTooltip.getInset(), hoverItemX(left, petName));
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
		return grid(config());
	}

	private static SkillCellGrid grid(KillClogConfig config)
	{
		return new SkillCellGrid(new SkillIconManager(),
			new TooltipController(config), config, null, () -> "Blue", () -> "Red");
	}

	private static void paint(TitleTooltip tooltip)
	{
		Dimension size = tooltip.getPreferredSize();
		tooltip.setSize(size);
		BufferedImage image = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		tooltip.paint(graphics);
		graphics.dispose();
	}

	private static boolean hoverItem(TitleTooltip tooltip, String itemName)
	{
		return hoverItemX(tooltip, itemName) >= 0;
	}

	private static int hoverItemX(TitleTooltip tooltip, String itemName)
	{
		for (int y = 0; y < tooltip.getHeight(); y++)
		{
			for (int x = 0; x < tooltip.getWidth(); x++)
			{
				MouseEvent event = new MouseEvent(tooltip, MouseEvent.MOUSE_MOVED,
					System.currentTimeMillis(), 0, x, y, 0, false);
				for (MouseMotionListener listener : tooltip.getMouseMotionListeners())
				{
					listener.mouseMoved(event);
				}
				if (itemName.equals(tooltip.getHeaderHoverLineText()))
				{
					return x;
				}
			}
		}
		return -1;
	}

	private static int hoverItemY(TitleTooltip tooltip, String itemName)
	{
		for (int y = 0; y < tooltip.getHeight(); y++)
		{
			for (int x = 0; x < tooltip.getWidth(); x++)
			{
				MouseEvent event = new MouseEvent(tooltip, MouseEvent.MOUSE_MOVED,
					System.currentTimeMillis(), 0, x, y, 0, false);
				for (MouseMotionListener listener : tooltip.getMouseMotionListeners())
				{
					listener.mouseMoved(event);
				}
				if (itemName.equals(tooltip.getHeaderHoverLineText()))
				{
					return y;
				}
			}
		}
		return -1;
	}

	private static HiscoreResult hiscores(Map<Skill, Integer> sampleLevels)
	{
		return hiscores(sampleLevels, Collections.emptyMap());
	}

	private static HiscoreResult hiscores(Map<Skill, Integer> sampleLevels,
		Map<String, Integer> activityScores)
	{
		Map<String, Integer> levels = new HashMap<>();
		Map<String, Integer> ranks = new HashMap<>();
		Map<String, Long> xps = new HashMap<>();
		for (Skill skill : SkillGridOrder.skills())
		{
			int level = sampleLevels.getOrDefault(skill, 1);
			String key = skill.getName().toLowerCase(Locale.ROOT);
			levels.put(key, level);
			ranks.put(key, 2_000 + level);
			xps.put(key, Experience.getXpForLevel(level) + 10L);
		}
		return new HiscoreResult(AccountType.REGULAR, HiscoreTable.STANDARD,
			Collections.emptyMap(), Collections.emptyMap(), activityScores,
			Collections.emptyMap(), levels, ranks, xps, 1_200, 24_000_000L, 85, 10_000);
	}

	private static KillClogConfig config()
	{
		return new KillClogConfig()
		{
			@Override
			public Color skillLevelColor()
			{
				return new Color(255, 87, 0);
			}

			@Override
			public Color completedClogColor()
			{
				return new Color(78, 240, 21);
			}
		};
	}

	private static ClogResult emptyClog()
	{
		return new ClogResult("Tester", Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), null, null);
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
