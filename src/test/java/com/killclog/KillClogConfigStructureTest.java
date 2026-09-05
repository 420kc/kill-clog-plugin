package com.killclog;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillClogConfigStructureTest
{
	private static final Set<String> PRE_220_KEYS = Set.of(
		"autoLookupOnLogin", "playerMenuLookup", "menuLabel",
		"menuOnPlayers", "menuOnFriendsList", "menuOnIgnoreList",
		"menuOnClanList", "menuOnGuestClanList", "menuOnChatChannels",
		"menuOnChat", "menuOnPrivateMessages", "menuOnGroupIronman",
		"tooltipMode", "hoverStyle", "virtualLevels", "wikiItemLinks",
		"chatNewClogMessages", "showChatEmojis", "showTooltipKc",
		"showTooltipPb", "showTooltipRank", "completionistHighlighter",
		"infoBarColor", "completedClogColor", "missing1Color",
		"inProgressClogColor", "emptyClogColor", "activitiesExpanded",
		"bossListView", "seenSelfGreeting", "killclogSync", "characterModel");

	@Test
	public void reorganizationPreservesEveryExpectedSetting()
	{
		Set<String> keys = Arrays.stream(KillClogConfig.class.getDeclaredMethods())
			.map(method -> method.getAnnotation(ConfigItem.class))
			.filter(item -> item != null)
			.map(ConfigItem::keyName)
			.collect(Collectors.toSet());

		Set<String> expected = new java.util.HashSet<>(PRE_220_KEYS);
		expected.add("enableComparison");
		expected.add("skillDisplay");
		expected.add("skillLevelColor");
		expected.add("skillColorMode");
		assertEquals(expected, keys);
	}

	@Test
	public void comparisonSettingIsDefaultOnUnderLookup() throws Exception
	{
		KillClogConfig defaults = new KillClogConfig()
		{
		};
		assertTrue(defaults.enableComparison());
		ConfigItem item = KillClogConfig.class.getDeclaredMethod("enableComparison")
			.getAnnotation(ConfigItem.class);
		assertEquals(KillClogConfig.lookupSection, item.section());
	}

	@Test
	public void chatEmojiDescriptionListsEverySupportedToken() throws Exception
	{
		ConfigItem item = KillClogConfig.class.getDeclaredMethod("showChatEmojis")
			.getAnnotation(ConfigItem.class);
		assertEquals("Show Kill Clog's custom emojis in chat: :killclog:, :rune:, "
			+ ":dragon:, :gilded:, :clog:, and :green:", item.description());
	}

	@Test
	public void everyVisibleSettingLivesInACollapsibleSection()
	{
		for (Method method : KillClogConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && !item.hidden())
			{
				assertFalse(item.keyName() + " is loose in the settings pane",
					item.section().isEmpty());
			}
		}
	}

	@Test
	public void collapsedSectionsFollowProductOrder()
	{
		List<String> sectionNames = Arrays.stream(KillClogConfig.class.getDeclaredFields())
			.map(field -> field.getAnnotation(ConfigSection.class))
			.filter(section -> section != null)
			.sorted(Comparator.comparingInt(ConfigSection::position))
			.map(ConfigSection::name)
			.collect(Collectors.toList());

		assertEquals(Arrays.asList("killclog.com", "Tooltips", "Lookup",
			"Menu location", "Skills", "Chat", "Progress Highlighter"), sectionNames);
	}

	@Test
	public void skillSettingsShareDedicatedSection() throws Exception
	{
		for (String methodName : Arrays.asList(
			"skillDisplay", "virtualLevels", "skillLevelColor", "skillColorMode"))
		{
			ConfigItem item = KillClogConfig.class.getDeclaredMethod(methodName)
				.getAnnotation(ConfigItem.class);
			assertEquals(KillClogConfig.skillsSection, item.section());
		}
	}

	@Test
	public void skillLocationKeepsEstablishedConfigKeyAndClearLabels() throws Exception
	{
		ConfigItem item = KillClogConfig.class.getDeclaredMethod("skillDisplay")
			.getAnnotation(ConfigItem.class);
		assertEquals("skillDisplay", item.keyName());
		assertEquals("Skill Location", item.name());
		assertEquals("Skill Summary only", SkillDisplay.TOOLTIP.toString());
		assertEquals("Main Grid", SkillDisplay.FIXED.toString());
		assertEquals("Activity Tray", SkillDisplay.TRAY.toString());
		KillClogConfig defaults = new KillClogConfig()
		{
		};
		assertEquals(SkillDisplay.FIXED, defaults.skillDisplay());
		assertEquals(SkillColorMode.LEVEL_COMPLETION, defaults.skillColorMode());
		assertEquals("99+ Completion", SkillColorMode.LEVEL_COMPLETION.toString());
		assertEquals("Clog Progression", SkillColorMode.CLOG_PROGRESSION.toString());
		assertEquals("Skill Color", SkillColorMode.SKILL_COLOR.toString());
	}
}
