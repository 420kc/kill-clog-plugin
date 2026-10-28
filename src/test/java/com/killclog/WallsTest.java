package com.killclog;

import java.awt.Component;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The walls between the plugin's jobs, without the packages the Hub's token count rules out: only the game readers
 * named here hold or take the game client, and no view (anything Swing draws) does. A reader keeps the client to
 * itself (a private field, never a return value), so another class reaches it only through its own signature: a
 * field, a parameter, or a type that carries it (a Provider or Supplier of it), lambdas included (they compile to
 * methods of their class). Short of disguising it as an Object or reflection, the compiled signatures are the answer.
 * A new reader is added here on purpose; a view that needs the game asks a reader for a finished snapshot instead
 * (the 10/11 clan card read the game from Swing's thread and stopped).
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

	/** A reader that returned its client, or left the field open, would let any class read the game unseen. */
	@Test
	public void noReaderHandsItsClientOut() throws Exception
	{
		Set<String> open = new TreeSet<>();
		for (Class<?> type : pluginClasses())
		{
			for (Field field : type.getDeclaredFields())
			{
				if (mentionsClient(field.getGenericType()) && !Modifier.isPrivate(field.getModifiers()))
				{
					open.add(where(field));
				}
			}
			for (Method method : type.getDeclaredMethods())
			{
				if (mentionsClient(method.getGenericReturnType()))
				{
					open.add(where(method));
				}
			}
		}
		assertEquals("the client leaves its reader here: keep it private and hand out snapshots", Set.of(), open);
	}

	private static boolean touchesClient(Class<?> type)
	{
		for (Field field : type.getDeclaredFields())
		{
			if (mentionsClient(field.getGenericType()))
			{
				return true;
			}
		}
		for (Constructor<?> constructor : type.getDeclaredConstructors())
		{
			if (Arrays.stream(constructor.getGenericParameterTypes()).anyMatch(WallsTest::mentionsClient))
			{
				return true;
			}
		}
		for (Method method : type.getDeclaredMethods())
		{
			if (mentionsClient(method.getGenericReturnType())
				|| Arrays.stream(method.getGenericParameterTypes()).anyMatch(WallsTest::mentionsClient))
			{
				return true;
			}
		}
		return false;
	}

	/** Client itself, an array of it, or a type built on it: Provider&lt;Client&gt;, Supplier&lt;? extends Client&gt;. */
	private static boolean mentionsClient(Type type)
	{
		if (type instanceof Class)
		{
			Class<?> raw = (Class<?>) type;
			return raw.isArray() ? mentionsClient(raw.getComponentType()) : Client.class.isAssignableFrom(raw);
		}
		if (type instanceof ParameterizedType)
		{
			return Arrays.stream(((ParameterizedType) type).getActualTypeArguments()).anyMatch(WallsTest::mentionsClient);
		}
		if (type instanceof WildcardType)
		{
			WildcardType wildcard = (WildcardType) type;
			return Stream.concat(Arrays.stream(wildcard.getUpperBounds()), Arrays.stream(wildcard.getLowerBounds()))
				.anyMatch(WallsTest::mentionsClient);
		}
		return type instanceof GenericArrayType && mentionsClient(((GenericArrayType) type).getGenericComponentType());
	}

	private static String where(Member member)
	{
		return member.getDeclaringClass().getSimpleName() + "." + member.getName();
	}

	/** The top-level class a nested, anonymous or local class belongs to, by its name inside the plugin's package. */
	private static String outer(Class<?> type)
	{
		String name = type.getName().substring(KillClogPlugin.class.getPackageName().length() + 1);
		int nested = name.indexOf('$');
		return nested < 0 ? name : name.substring(0, nested);
	}

	/** Every compiled class of the plugin, sub-packages included, from where KillClogPlugin was loaded. */
	private static List<Class<?>> pluginClasses() throws Exception
	{
		Path root = Path.of(KillClogPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		Path dir = root.resolve(KillClogPlugin.class.getPackageName().replace('.', File.separatorChar));
		List<String> names;
		try (Stream<Path> files = Files.walk(dir))
		{
			names = files.map(file -> root.relativize(file).toString())
				.filter(name -> name.endsWith(".class"))
				.map(name -> name.substring(0, name.length() - ".class".length()).replace(File.separatorChar, '.'))
				.sorted()
				.collect(Collectors.toList());
		}
		List<Class<?>> classes = new ArrayList<>();
		for (String name : names)
		{
			classes.add(Class.forName(name, false, WallsTest.class.getClassLoader()));
		}
		assertTrue("the plugin's classes are found in " + dir, classes.size() > 100);
		return classes;
	}
}
