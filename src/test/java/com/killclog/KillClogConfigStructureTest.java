package com.killclog;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
	public void reorganizationPreservesEveryExistingKeyAndAddsOnlySkillSettings()
	{
		Set<String> keys = Arrays.stream(KillClogConfig.class.getDeclaredMethods())
			.map(method -> method.getAnnotation(ConfigItem.class))
			.filter(item -> item != null)
			.map(ConfigItem::keyName)
			.collect(Collectors.toSet());

		Set<String> expected = new java.util.HashSet<>(PRE_220_KEYS);
		expected.add("skillDisplay");
		expected.add("skillLevelColor");
		expected.add("skillCompletionColor");
		assertEquals(expected, keys);
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
	public void skillSettingsShareDedicatedSection() throws Exception
	{
		for (String methodName : Arrays.asList(
			"skillDisplay", "virtualLevels", "skillLevelColor", "useSkillCompletionColor"))
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
	}
}
