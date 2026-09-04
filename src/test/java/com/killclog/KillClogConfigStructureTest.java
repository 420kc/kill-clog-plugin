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
	public void reorganizationPreservesEveryExistingKeyAndAddsOnlySkillDisplay()
	{
		Set<String> keys = Arrays.stream(KillClogConfig.class.getDeclaredMethods())
			.map(method -> method.getAnnotation(ConfigItem.class))
			.filter(item -> item != null)
			.map(ConfigItem::keyName)
			.collect(Collectors.toSet());

		Set<String> expected = new java.util.HashSet<>(PRE_220_KEYS);
		expected.add("skillDisplay");
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
	public void skillDisplayLabelsNameAllThreePlacements()
	{
		assertEquals("Tooltip only", SkillDisplay.TOOLTIP.toString());
		assertEquals("Fixed above boss grid", SkillDisplay.FIXED.toString());
		assertEquals("Above clues in tray", SkillDisplay.TRAY.toString());
	}
}
