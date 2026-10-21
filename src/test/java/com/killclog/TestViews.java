package com.killclog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.Skill;
import net.runelite.client.hiscore.HiscoreSkill;
import javax.swing.ImageIcon;

/**
 * What tests read inside the plugin that the plugin itself never asks for. It lives here so the plugin carries none
 * of it: every line under src/main/java counts toward the Plugin Hub's size limit (Dylan, 2026-10-13).
 */
final class TestViews
{
	private TestViews()
	{
	}

	/** A field's value, on an object or (given a class) a static one, from the class or a superclass. */
	@SuppressWarnings("unchecked")
	static <T> T field(Object owner, String name)
	{
		Class<?> type = owner instanceof Class ? (Class<?>) owner : owner.getClass();
		for (Class<?> c = type; c != null; c = c.getSuperclass())
		{
			try
			{
				Field field = c.getDeclaredField(name);
				field.setAccessible(true);
				return (T) field.get(owner instanceof Class ? null : owner);
			}
			catch (NoSuchFieldException e)
			{
				// look in the superclass
			}
			catch (IllegalAccessException e)
			{
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalArgumentException("no field " + name + " on " + type);
	}

	static void set(Object owner, String name, Object value)
	{
		for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass())
		{
			try
			{
				Field field = c.getDeclaredField(name);
				field.setAccessible(true);
				field.set(owner, value);
				return;
			}
			catch (NoSuchFieldException e)
			{
				// look in the superclass
			}
			catch (IllegalAccessException e)
			{
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalArgumentException("no field " + name + " on " + owner.getClass());
	}

	/** A method's answer, by name and argument count, on an object or (given a class) a static one. */
	@SuppressWarnings("unchecked")
	static <T> T call(Object owner, String name, Object... args)
	{
		Class<?> type = owner instanceof Class ? (Class<?>) owner : owner.getClass();
		for (Class<?> c = type; c != null; c = c.getSuperclass())
		{
			for (Method method : c.getDeclaredMethods())
			{
				if (method.getName().equals(name) && method.getParameterCount() == args.length)
				{
					try
					{
						method.setAccessible(true);
						return (T) method.invoke(owner instanceof Class ? null : owner, args);
					}
					catch (ReflectiveOperationException e)
					{
						throw new IllegalStateException(e);
					}
				}
			}
		}
		throw new IllegalArgumentException("no method " + name + " on " + type);
	}

	/** The hiscores result the plugin builds, with whatever a test leaves out empty (STANDARD table, no ranks or XP). */
	static HiscoreResult hiscore(AccountType accountType, HiscoreTable hiscoreTable, Map<String, Integer> bossKills,
		Map<String, Integer> bossRanks, Map<String, Integer> activityScores, Map<String, Integer> activityRanks,
		Map<String, Integer> skillLevels, Map<String, Integer> skillRanks, Map<String, Long> skillXps, int totalLevel,
		long totalXp, double combatLevel, int overallRank)
	{
		return new HiscoreResult(accountType, hiscoreTable, bossKills, bossRanks, activityScores, activityRanks,
			skillLevels, skillRanks, skillXps, totalLevel, totalXp, combatLevel, overallRank);
	}

	static HiscoreResult hiscore(AccountType accountType, HiscoreTable hiscoreTable, Map<String, Integer> bossKills,
		Map<String, Integer> bossRanks, Map<String, Integer> activityScores, Map<String, Integer> activityRanks,
		Map<String, Integer> skillLevels, int totalLevel, long totalXp, double combatLevel, int overallRank)
	{
		return hiscore(accountType, hiscoreTable, bossKills, bossRanks, activityScores, activityRanks, skillLevels,
			null, null, totalLevel, totalXp, combatLevel, overallRank);
	}

	static HiscoreResult hiscore(AccountType accountType, Map<String, Integer> bossKills, Map<String, Integer> bossRanks,
		Map<String, Integer> activityScores, Map<String, Integer> activityRanks, Map<String, Integer> skillLevels,
		int totalLevel, long totalXp, double combatLevel, int overallRank)
	{
		return hiscore(accountType, null, bossKills, bossRanks, activityScores, activityRanks, skillLevels, null, null,
			totalLevel, totalXp, combatLevel, overallRank);
	}

	/** A card's clickable row under a y coordinate, or -1 ({@code rows}: raidRows, rareRows, pageRows, tabRows). */
	static int rowAt(Object card, String rows, int y)
	{
		return call(field(card, rows), "at", y);
	}

	static int hoveredRow(Object card, String rows)
	{
		return call(field(card, rows), "hovered");
	}

	/** The Skill Clog catalog as kind|heading|category-or-ids, one line per section. */
	static List<String> specSignature(Skill skill)
	{
		Map<Skill, List<Object>> specs = field(SkillClogSection.class, "SPECS");
		List<Object> sections = specs.get(skill);
		if (sections == null)
		{
			return Collections.emptyList();
		}
		List<String> signature = new ArrayList<>(sections.size());
		for (Object spec : sections)
		{
			String heading = field(spec, "heading");
			String category = field(spec, "category");
			List<Integer> itemIds = field(spec, "itemIds");
			StringBuilder line = new StringBuilder(category != null ? "category" : "items");
			line.append('|').append(heading != null ? heading : "").append('|');
			if (category != null)
			{
				line.append(category);
			}
			else
			{
				for (int i = 0; i < itemIds.size(); i++)
				{
					line.append(i > 0 ? "," : "").append(itemIds.get(i));
				}
			}
			signature.add(line.toString());
		}
		return signature;
	}

	static List<String> sourceNames(ClogSummaryTooltip tooltip)
	{
		List<String> names = new ArrayList<>();
		for (Object source : TestViews.<List<?>>field(tooltip, "clogSources"))
		{
			names.add(field(source, "name"));
		}
		return names;
	}

	/** A catalog already published with these totals. */
	static CaCatalog caCatalog(Map<CombatAchievementTier, Integer> totals)
	{
		CaCatalog catalog = new CaCatalog();
		set(catalog, "totals", Collections.unmodifiableMap(new EnumMap<>(totals)));
		return catalog;
	}

	static int completed(CombatAchievementResult result, CombatAchievementTier tier)
	{
		return TestViews.<Map<CombatAchievementTier, Integer>>field(result, "completed").getOrDefault(tier, 0);
	}

	/** The tier's task count: the live catalog's once the result rides one, else what the provider said. */
	static int total(CombatAchievementResult result, CombatAchievementTier tier)
	{
		boolean live = field(result, "liveCatalog");
		return TestViews.<Map<CombatAchievementTier, Integer>>field(result, live ? "currentTotals" : "providerTotal")
			.getOrDefault(tier, 0);
	}

	private static KillClogChatCommand.ClogTarget clueTarget(String query)
	{
		Map<String, KillClogChatCommand.ClogTarget> aliases = field(KillClogChatCommand.class, "CLUE_ALIASES");
		return aliases.get(KillClogChatCommand.normalize(query));
	}

	static String clueCategory(String query)
	{
		KillClogChatCommand.ClogTarget target = clueTarget(query);
		return target != null ? target.categoryKey : null;
	}

	static String clueLabel(String query)
	{
		KillClogChatCommand.ClogTarget target = clueTarget(query);
		return target != null ? target.label : null;
	}

	static int clueAliasCount()
	{
		return TestViews.<Map<?, ?>>field(KillClogChatCommand.class, "CLUE_ALIASES").size();
	}

	static void leagueCacheFactory(KillClogPlugin plugin, Function<String, LocalClogCache> factory)
	{
		set(plugin, "leagueCacheFactory", factory);
	}

	static Map<HiscoreSkill, ImageIcon> originalIcons(Cells cells)
	{
		return Collections.unmodifiableMap(field(cells, "originalIcons"));
	}

	static Map<HiscoreSkill, ImageIcon> dimmedIcons(Cells cells)
	{
		return Collections.unmodifiableMap(field(cells, "dimmedIcons"));
	}

	/** The whole combat level the hiscores' skill levels give. */
	static int combatLevelFromLevels(HiscoreService service, Map<String, Integer> levels)
	{
		return (int) Math.floor(service.calcCmbLvlExact(levels));
	}

	static SkillTooltip.Stats stats(SkillTooltip tip)
	{
		return field(tip, "stats");
	}

	static List<SkillClogSection> sections(SkillTooltip tip)
	{
		return field(tip, "sections");
	}

	static Map<String, String> bossCategoryOverrides()
	{
		return Collections.unmodifiableMap(field(ClogService.class, "BOSS_CATEGORY_OVERRIDES"));
	}

	/** The lookup generation the request now out for this player was sent for, or null. */
	static Integer askedFor(PlayerPortraits portraits, String name)
	{
		return TestViews.<Map<String, Integer>>field(portraits, "asked").get(PlayerPortraits.key(name));
	}

	static int countedLookups(PlayerPortraits portraits)
	{
		return TestViews.<Map<?, ?>>field(portraits, "generations").size();
	}

	/** True exactly once per remembered push intent. */
	static boolean consumeQueued(KillclogSyncGate<?> gate)
	{
		return gate.takeQueued() != null;
	}
}
