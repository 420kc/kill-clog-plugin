package com.killclog;

import java.awt.Component;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The walls between the plugin's jobs, without the packages the Hub's token count rules out: only the game readers
 * named here hold or take the game client, and no view (anything Swing draws) does. A class reaches the client only
 * through a field, a parameter or a return value, lambdas included (they compile to methods of their class), so the
 * compiled signatures are the whole answer. A new reader is added here on purpose; a view that needs the game asks a
 * reader for a finished snapshot instead (the 10/11 clan card read the game from Swing's thread and stopped).
 */
public class WallsTest
{
	/** The classes that read the game, each on the client thread. */
	private static final Set<String> READERS = new TreeSet<>(Arrays.asList(
		"AdvLogPbs", "CaCatalog", "CardSprites", "ClogIndex", "ClogLookupMenu", "ClogPbs", "GimBadgeLoader",
		"KillClogChatCommand", "KillClogChatEmoji", "KillClogChatNotifier", "KillClogPlugin", "KillLog",
		"LiveClogSync", "LocalCaReader", "ManualClogSync", "NameAutocompleter", "PlayerClans",
		"ProfileAppearanceService", "RsnInputPolicy", "SessionTracker"));

	@Test
	public void onlyTheReadersTouchTheGameClient() throws Exception
	{
		Set<String> touching = new TreeSet<>();
		for (Class<?> type : pluginClasses())
		{
			if (touchesClient(type))
			{
				touching.add(outer(type));
			}
		}
		Set<String> strays = new TreeSet<>(touching);
		strays.removeAll(READERS);
		assertEquals("classes that hold the game client but aren't readers: move the read into a reader", Set.of(),
			strays);
		Set<String> gone = new TreeSet<>(READERS);
		gone.removeAll(touching);
		assertEquals("readers that no longer touch the client: take them off the list", Set.of(), gone);
	}

	@Test
	public void noViewTouchesTheGameClient() throws Exception
	{
		List<String> views = new ArrayList<>();
		for (Class<?> type : pluginClasses())
		{
			if (Component.class.isAssignableFrom(type))
			{
				views.add(type.getName());
				assertTrue(type.getName() + " draws, so it reads the game only through a reader's snapshot",
					!touchesClient(type) && !READERS.contains(outer(type)));
			}
		}
		assertTrue("the views are found: " + views, views.contains(KillClogPanel.class.getName()));
	}

	private static boolean touchesClient(Class<?> type)
	{
		for (Field field : type.getDeclaredFields())
		{
			if (field.getType() == Client.class)
			{
				return true;
			}
		}
		for (Constructor<?> constructor : type.getDeclaredConstructors())
		{
			if (Arrays.asList(constructor.getParameterTypes()).contains(Client.class))
			{
				return true;
			}
		}
		for (Method method : type.getDeclaredMethods())
		{
			if (method.getReturnType() == Client.class || Arrays.asList(method.getParameterTypes()).contains(Client.class))
			{
				return true;
			}
		}
		return false;
	}

	/** The top-level class a nested, anonymous or local class belongs to, by its simple name. */
	private static String outer(Class<?> type)
	{
		String name = type.getName().substring(type.getPackageName().length() + 1);
		int nested = name.indexOf('$');
		return nested < 0 ? name : name.substring(0, nested);
	}

	/** Every compiled class of the plugin, from the directory KillClogPlugin was loaded from. */
	private static List<Class<?>> pluginClasses() throws Exception
	{
		File root = new File(KillClogPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		File dir = new File(root, KillClogPlugin.class.getPackageName().replace('.', File.separatorChar));
		List<Class<?>> classes = new ArrayList<>();
		try (Stream<String> names = Stream.of(dir.list()))
		{
			for (String name : (Iterable<String>) names.filter(n -> n.endsWith(".class")).sorted()::iterator)
			{
				classes.add(Class.forName(KillClogPlugin.class.getPackageName() + "." + name.replace(".class", ""),
					false, WallsTest.class.getClassLoader()));
			}
		}
		assertTrue("the plugin's classes are found in " + dir, classes.size() > 100);
		return classes;
	}
}
