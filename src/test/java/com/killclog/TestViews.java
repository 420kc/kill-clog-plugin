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
	/** A catalog handed to the class that reads it, as the plugin's injector hands it over. */
	static void inject(Object target, Object catalog)
	{
		try
		{
			for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
			{
				for (java.lang.reflect.Field field : type.getDeclaredFields())
				{
					if (field.getType() == ClogIndex.class && (catalog == null || catalog instanceof ClogIndex)
						|| field.getType() == CaCatalog.class && (catalog == null || catalog instanceof CaCatalog))
					{
						field.setAccessible(true);
						field.set(target, catalog);
						return;
					}
				}
			}
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
		throw new AssertionError("no catalog field in " + target.getClass().getSimpleName());
	}

	/** ClogIndex's test seam in its short forms: a catalog with its pages and names, with its tabs and remaps, and all. */
	static void publish(ClogIndex index, Map<String, List<Integer>> pages, Map<Integer, String> names)
	{
		publish(index, pages, names, Collections.emptyMap(), Collections.emptyMap());
	}

	static void publish(ClogIndex index, Map<String, List<Integer>> pages, Map<Integer, String> names,
		Map<String, List<String>> tabs, Map<Integer, Integer> remaps)
	{
		index.publishForTest(pages, names, tabs, remaps, Collections.emptyMap());
	}

	static void publish(ClogIndex index, Map<String, List<Integer>> pages, Map<Integer, String> names,
		Map<String, List<String>> tabs, Map<Integer, Integer> remaps, Map<String, String> pageNames)
	{
		index.publishForTest(pages, names, tabs, remaps, pageNames);
	}

	/** The status line's text. */
	static String statusText(PanelStatusRow row)
	{
		return ((javax.swing.JLabel) field(row, "searchStatus")).getText();
	}

	/** Whether a session's lookup is still out. */
	static boolean inFlight(LookupSession session)
	{
		return ((LookupFanout) field(session, "fanout")).isInFlight();
	}

	/** Where a scrolled card's window sits. */
	static int offset(CardBody.Scroll scroll)
	{
		return field(scroll, "offset");
	}

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

	/** A card's line under its title: the Obtained count's value (null with no line), or its colour. */
	static String subtitle(TitleTooltip tip)
	{
		Object line = field(tip, "subtitle");
		return line != null ? field(line, "value") : null;
	}

	static java.awt.Color subtitleColor(TitleTooltip tip)
	{
		return field(field(tip, "subtitle"), "color");
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

	/** A card's clickable row under a y coordinate, or -1 ({@code rows}: tiles, hourRows). */
	static int rowAt(Object card, String rows, int y)
	{
		return tileAt(card, rows, ((java.awt.Component) card).getWidth() / 2, y);
	}

	/** Each game tile's top-left corner in reading order: where its black frame starts a box the tile's size. */
	static java.util.List<java.awt.Point> tiles(java.awt.image.BufferedImage image)
	{
		return tiles(image, GameTile.WIDTH, GameTile.HEIGHT);
	}

	/** The same for tiles of another size, as a tab's pages are. */
	static java.util.List<java.awt.Point> tiles(java.awt.image.BufferedImage image, int width, int height)
	{
		int edge = new java.awt.Color(14, 12, 10).getRGB();
		java.util.List<java.awt.Point> found = new java.util.ArrayList<>();
		for (int y = 1; y + height <= image.getHeight(); y++)
		{
			for (int x = 1; x + width <= image.getWidth(); x++)
			{
				if (image.getRGB(x, y) == edge && image.getRGB(x - 1, y) != edge && image.getRGB(x, y - 1) != edge
					&& image.getRGB(x + width - 1, y) == edge && image.getRGB(x, y + height - 1) == edge)
				{
					found.add(new java.awt.Point(x, y));
				}
			}
		}
		return found;
	}

	static int tileAt(Object card, String rows, int x, int y)
	{
		return call(field(card, rows), "at", new java.awt.Point(x, y));
	}

	static int hoveredRow(Object card, String rows)
	{
		return field(field(card, rows), "hovered");
	}

	/** A Skill Clog section of fixed items, built as the catalog builds one from a row whose ';' starts each set's row. */
	static SkillClogSection skillSection(String heading, String value)
	{
		List<Integer> ids = new ArrayList<>();
		java.util.Set<Integer> rowStarts = new java.util.HashSet<>();
		for (String set : value.split(";"))
		{
			String[] parts = set.split(",");
			if (!ids.isEmpty())
			{
				rowStarts.add(Integer.parseInt(parts[0]));
			}
			for (String id : parts)
			{
				ids.add(Integer.parseInt(id));
			}
		}
		try
		{
			Class<?> specType = Class.forName("com.killclog.SkillClogSection$Spec");
			java.lang.reflect.Constructor<?> make = specType.getDeclaredConstructor(String.class, String.class, List.class,
				java.util.Set.class, boolean.class);
			make.setAccessible(true);
			Method from = SkillClogSection.class.getDeclaredMethod("fromSpec", specType, ClogResult.class, ClogResult.class,
				ClogResult.class, ClogIndex.class, java.util.Set.class);
			from.setAccessible(true);
			return (SkillClogSection) from.invoke(null, make.newInstance(heading, null, ids, rowStarts, false),
				null, null, null, null, new java.util.HashSet<Integer>());
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	/** The Skill Clog catalog as kind|heading|category-or-ids, one line per section, a ';' before each set's own row. */
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
			java.util.Set<Integer> rowStarts = field(spec, "rowStarts");
			boolean rest = field(spec, "rest");
			StringBuilder line = new StringBuilder(category == null ? "items" : rest ? "rest" : "category");
			line.append('|').append(heading != null ? heading : "").append('|');
			if (category != null)
			{
				line.append(category);
			}
			else
			{
				for (int i = 0; i < itemIds.size(); i++)
				{
					line.append(i == 0 ? "" : rowStarts.contains(itemIds.get(i)) ? ";" : ",").append(itemIds.get(i));
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

	/** The XP a skill card holds as left to its next level, or -1 once it is maxed. */
	static long xpToLevel(SkillTooltip tip)
	{
		SkillTooltip.Stats stats = stats(tip);
		return TestViews.<Boolean>field(stats, "maxed") ? -1 : TestViews.<Long>field(stats, "xpToLevel");
	}

	/** A card painted at its preferred size, as the client shows it. */
	static java.awt.image.BufferedImage paint(javax.swing.JComponent card)
	{
		java.awt.Dimension size = card.getPreferredSize();
		card.setSize(size);
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size.width, size.height,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = image.createGraphics();
		card.paint(graphics);
		graphics.dispose();
		return image;
	}

	/**
	 * The longest straight run of one colour in a painted card, a bar's fill: {x, y, length, rows}, rows being how many
	 * rows from y the whole run spans. All zero when the colour never runs.
	 */
	static int[] longestRun(java.awt.image.BufferedImage image, java.awt.Color color)
	{
		int argb = color.getRGB();
		int[] best = {0, 0, 0, 0};
		for (int y = 0; y < image.getHeight(); y++)
		{
			int start = -1;
			for (int x = 0; x <= image.getWidth(); x++)
			{
				boolean on = x < image.getWidth() && image.getRGB(x, y) == argb;
				if (on && start < 0)
				{
					start = x;
				}
				else if (!on && start >= 0)
				{
					if (x - start > best[2])
					{
						best = new int[]{start, y, x - start, 0};
					}
					start = -1;
				}
			}
		}
		for (int y = best[1]; best[2] > 0 && y < image.getHeight(); y++)
		{
			for (int x = best[0]; x < best[0] + best[2]; x++)
			{
				if (image.getRGB(x, y) != argb)
				{
					return best;
				}
			}
			best[3]++;
		}
		return best;
	}

	/** The lowest row above {@code below} holding any pixel of this colour, or -1. */
	static int lowestRowAbove(java.awt.image.BufferedImage image, java.awt.Color color, int below)
	{
		for (int y = below - 1; y >= 0; y--)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (image.getRGB(x, y) == color.getRGB())
				{
					return y;
				}
			}
		}
		return -1;
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

	/**
	 * Where a picture holds this text in this font and color: the point it was drawn from, its left edge and its
	 * baseline, or null when it isn't there. It reads the pixels of exactly that color, and wants the text's own shape
	 * with nothing else of the color inside its box, so "1/5" is never found in "5/1" or in a bar of the same color.
	 */
	static java.awt.Point textAt(java.awt.image.BufferedImage image, String text, java.awt.Font font, java.awt.Color color)
	{
		java.awt.FontMetrics fm = new java.awt.Canvas().getFontMetrics(font);
		int pad = 2;
		java.awt.image.BufferedImage ink = new java.awt.image.BufferedImage(fm.stringWidth(text) + 2 * pad,
			fm.getHeight() + 2 * pad, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = ink.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(font);
		g.setColor(color);
		g.drawString(text, pad, pad + fm.getAscent());
		g.dispose();
		int rgb = color.getRGB();
		int left = ink.getWidth();
		int top = ink.getHeight();
		int right = -1;
		int bottom = -1;
		for (int y = 0; y < ink.getHeight(); y++)
		{
			for (int x = 0; x < ink.getWidth(); x++)
			{
				if (ink.getRGB(x, y) == rgb)
				{
					left = Math.min(left, x);
					right = Math.max(right, x);
					top = Math.min(top, y);
					bottom = Math.max(bottom, y);
				}
			}
		}
		if (right < 0)
		{
			throw new IllegalArgumentException("the font draws nothing solid for: " + text);
		}
		for (int y = 0; y + bottom - top < image.getHeight(); y++)
		{
			for (int x = 0; x + right - left < image.getWidth(); x++)
			{
				boolean same = true;
				for (int dy = 0; same && dy <= bottom - top; dy++)
				{
					for (int dx = 0; same && dx <= right - left; dx++)
					{
						same = (image.getRGB(x + dx, y + dy) == rgb) == (ink.getRGB(left + dx, top + dy) == rgb);
					}
				}
				if (same)
				{
					return new java.awt.Point(x - left + pad, y - top + pad + fm.getAscent());
				}
			}
		}
		return null;
	}

	/** Whether a tile at this top-left holds a pixel of this color. */
	static boolean tileHas(java.awt.image.BufferedImage image, java.awt.Point tile, java.awt.Color color)
	{
		for (int y = tile.y; y < tile.y + GameTile.HEIGHT; y++)
		{
			for (int x = tile.x; x < tile.x + GameTile.WIDTH; x++)
			{
				if (image.getRGB(x, y) == color.getRGB())
				{
					return true;
				}
			}
		}
		return false;
	}

	/** The rows a divider runs across a card in, top down. */
	static java.util.List<Integer> rules(java.awt.image.BufferedImage image)
	{
		int divider = NativeTooltip.dividerColor.getRGB();
		int inset = NativeTooltip.getInset();
		java.util.List<Integer> rows = new java.util.ArrayList<>();
		for (int y = 0; y < image.getHeight(); y++)
		{
			int run = 0;
			for (int x = inset; x < image.getWidth() - inset; x++)
			{
				run += image.getRGB(x, y) == divider ? 1 : 0;
			}
			if (run >= (image.getWidth() - 2 * inset) * 9 / 10)
			{
				rows.add(y);
			}
		}
		return rows;
	}

	/** A log of pages, each its item ids, the player holding the first {@code held} of each. */
	static ClogResult log(String name, Map<String, Integer> pages, Map<String, Integer> held)
	{
		Map<String, List<Integer>> items = new java.util.LinkedHashMap<>();
		Map<String, List<ClogResult.ClogItem>> obtained = new java.util.LinkedHashMap<>();
		int next = 40000;
		for (Map.Entry<String, Integer> page : pages.entrySet())
		{
			List<Integer> ids = new ArrayList<>();
			List<ClogResult.ClogItem> owned = new ArrayList<>();
			for (int i = 0; i < page.getValue(); i++)
			{
				ids.add(next);
				if (i < held.getOrDefault(page.getKey(), 0))
				{
					owned.add(new ClogResult.ClogItem(next, 1, null));
				}
				next++;
			}
			items.put(page.getKey(), ids);
			obtained.put(page.getKey(), owned);
		}
		return new ClogResult(name, obtained, items, Collections.emptyMap(), null, null);
	}

	/** A card header's line ("subtitle" or "rank"), its part ("label" or "value"), or null without the line. */
	static String headerLine(TitleTooltip tip, String line, String part) throws ReflectiveOperationException
	{
		java.lang.reflect.Field lineField = TitleTooltip.class.getDeclaredField(line);
		lineField.setAccessible(true);
		Object value = lineField.get(tip);
		if (value == null)
		{
			return null;
		}
		java.lang.reflect.Field partField = value.getClass().getDeclaredField(part);
		partField.setAccessible(true);
		return (String) partField.get(value);
	}
}
