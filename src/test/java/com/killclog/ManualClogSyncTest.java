package com.killclog;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ManualClogSyncTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();
	private final KillClogPlugin plugin = new KillClogPlugin();
	private ManualClogSync sync;
	private final List<String> notices = new ArrayList<>();
	private int tick = 100;
	private int obtained = 1;
	private int total = 3;
	private int completions;
	private int refreshes;
	private Client client;
	private KillClogChatNotifier notifier;
	private LocalClogCache cache;
	private File directory;
	private ClogIndex index;
	private Widget searchResults = widget(KillClogPlugin.CLOG_INTERFACE, 71, true);
	private int automaticSearches;
	private int viewRestores;
	private boolean failAutomaticSearch;
	private final List<Integer> automaticItems = new ArrayList<>();
	private Widget clogRoot = widget(KillClogPlugin.CLOG_INTERFACE, 0, false);
	private String playerName = "Tester";
	private boolean hostLog;

	@Before
	public void setUp() throws Exception
	{
		sync = (ManualClogSync) pluginField("manualClogSync").get(plugin);
		Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
			new Class<?>[]{Player.class}, (proxy, method, args) ->
			{
				if (method.getName().equals("getName")) return playerName;
				throw new AssertionError(method.getName());
			});
		client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
			new Class<?>[]{Client.class}, (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getLocalPlayer": return player;
					case "getTickCount": return tick;
					case "getVarpValue": return (int) args[0] == ClogVarps.OBTAINED ? obtained : total;
					case "getVarbitValue": return hostLog ? 1 : 0;
					case "getWidget":
						if (args.length != 2 || (int) args[0] != KillClogPlugin.CLOG_INTERFACE) return null;
						return (int) args[1] == 0 ? clogRoot : (int) args[1] == 71 ? searchResults : null;
					case "menuAction":
						assertArrayEquals(new Object[]{-1, KillClogPlugin.CLOG_INTERFACE << 16 | 76,
							MenuAction.CC_OP, 1, -1, "Search", null}, args);
						automaticSearches++;
						if (failAutomaticSearch) throw new IllegalStateException("test request failure");
						for (int id : automaticItems) itemScript(id, 1, tick);
						return null;
					case "runScript":
						assertEquals(2240, ((Object[]) args[0])[0]);
						viewRestores++;
						openCollectionLog(); // Reinitialization must not request Search again.
						return null;
					case "addChatMessage": notices.add((String) args[2]); return null;
					default: throw new AssertionError(method.getName());
				}
			});
		notifier = new KillClogChatNotifier(client, new KillClogConfig()
		{
		});
		directory = temporaryFolder.newFolder();
		cache = new LocalClogCache(new Gson(), new InlineScheduledExecutorService(), directory);
		cache.seedIdentityForTest(new HashMap<>());
		cache.followNameChange("Tester", 42L);
		index = (ClogIndex) pluginField("clogIndex").get(plugin);
		Class<?> snapshotType = Class.forName("com.killclog.ClogIndex$Snapshot");
		Constructor<?> constructor = snapshotType.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		Object snapshot = constructor.newInstance(Map.of("zulrah", List.of(1, 2, 3)),
			Map.of(1, List.of("zulrah"), 2, List.of("zulrah"), 3, List.of("zulrah")),
			Map.of(), Map.of());
		Field snapshotField = ClogIndex.class.getDeclaredField("snapshot");
		snapshotField.setAccessible(true);
		snapshotField.set(index, snapshot);
		pluginField("client").set(plugin, client);
		pluginField("localClogCache").set(plugin, cache);
		pluginField("chatNotifier").set(plugin, notifier);
	}

	@Test
	public void providerPreseedDoesNotBlockSearchAndCompletionSurvivesReload() throws Exception
	{
		cache.cacheResult(new ClogResult("Tester", Map.of("zulrah", List.of(
			new ClogResult.ClogItem(3, 5, null))), Map.of("zulrah", List.of(1, 2, 3)),
			Map.of(), null, null));
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
		assertFalse(sync.onSyncClicked(client, index, null, cache, notifier, name -> refreshes++));
		assertTrue(notices.get(0).contains("choose Search"));
		openCollectionLog();
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		itemScript(1, 2, 101);
		tick(103);
		assertEquals(0, completions);
		tick(104);
		assertEquals(1, completions);
		assertEquals(1, refreshes);
		assertTrue(cache.hasCompletedFirstPartySetupFor("Tester"));
		LocalClogCache reloaded = new LocalClogCache(new Gson(),
			new InlineScheduledExecutorService(), directory);
		assertTrue(reloaded.hasCompletedFirstPartySetupFor("Tester"));
		ClogResult payload = reloaded.toFirstPartySyncResult("Tester");
		assertEquals(List.of(1), payload.getObtainedItems().get("zulrah").stream()
			.map(ClogResult.ClogItem::getId).collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void emptySearchCompletesWithoutCreatingUploadableItems() throws Exception
	{
		obtained = 0;
		total = 0;
		tick(110);
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
		// Retain the menu route for clients that emit this event for Search.
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		assertTrue(notices.stream().anyMatch(text -> text.contains("is reading")));
		tick(119);
		assertEquals(0, completions);
		tick(120);
		assertEquals(1, completions);
		assertTrue(cache.hasCompletedFirstPartySetupFor("Tester"));
		assertFalse(cache.hasFirstPartyDataFor("Tester"));
		LocalClogCache reloaded = new LocalClogCache(new Gson(),
			new InlineScheduledExecutorService(), directory);
		assertTrue(reloaded.hasCompletedFirstPartySetupFor("Tester"));
		assertFalse(reloaded.hasFirstPartyDataFor("Tester"));
		assertTrue(notices.stream().anyMatch(text -> text.contains("Setup complete")));
	}

	@Test
	public void nativeSearchCompletesFreshAccountWithoutAMenuEvent() throws Exception
	{
		// Live trace: root script 4084, source 621:76, Search hidden, 0/1717.
		// The toggle's false argument comes from the matching cached script.
		obtained = 0;
		total = 1717;
		searchResults = widget(KillClogPlugin.CLOG_INTERFACE, 71, true);
		script(4084, widget(KillClogPlugin.CLOG_INTERFACE, 76, false), 4084, 0);
		tick(102);
		assertEquals(0, completions);
		tick(103);
		assertEquals(1, completions);
		assertTrue(notices.stream().anyMatch(text -> text.contains("is reading")));
		LocalClogCache reloaded = new LocalClogCache(new Gson(),
			new InlineScheduledExecutorService(), directory);
		assertTrue(reloaded.hasCompletedFirstPartySetupFor("Tester"));
		assertFalse(reloaded.hasFirstPartyDataFor("Tester"));
	}

	@Test
	public void automaticEmptySearchCompletesOnceAndSurvivesReload() throws Exception
	{
		obtained = 0;
		openCollectionLog();
		openCollectionLog();
		tick(101);
		assertEquals(1, automaticSearches);
		assertEquals(1, viewRestores);
		assertEquals(0, completions);
		tick(104);
		assertEquals(1, completions);
		assertEquals(1, refreshes);
		assertEquals(2, notices.size());
		assertTrue(notices.get(1).contains("Setup complete - 0 items"));
		LocalClogCache reloaded = new LocalClogCache(new Gson(),
			new InlineScheduledExecutorService(), directory);
		assertTrue(reloaded.hasCompletedFirstPartySetupFor("Tester"));
		assertFalse(reloaded.hasFirstPartyDataFor("Tester"));
		reloaded.cacheResult(new ClogResult("Tester", Map.of("zulrah", List.of(
			new ClogResult.ClogItem(3, 5, null))), Map.of("zulrah", List.of(1, 2, 3)),
			Map.of(), null, null));
		assertFalse(reloaded.hasFirstPartyDataFor("Tester"));
		openCollectionLog();
		tick(110);
		assertEquals(1, automaticSearches);
		assertEquals(1, completions);
	}

	@Test
	public void automaticSearchArmsBeforeItemsArriveAndReplacesProviderPreseed() throws Exception
	{
		cache.cacheResult(new ClogResult("Tester", Map.of("zulrah", List.of(
			new ClogResult.ClogItem(3, 5, null))), Map.of("zulrah", List.of(1, 2, 3)),
			Map.of(), null, null));
		automaticItems.add(1);
		openCollectionLog();
		itemScript(2, 1, 100); // Ordinary visible-page data is not the full walk.
		tick(101);
		tick(104);
		assertEquals(1, automaticSearches);
		assertEquals(1, viewRestores);
		assertEquals(1, completions);
		LocalClogCache reloaded = new LocalClogCache(new Gson(),
			new InlineScheduledExecutorService(), directory);
		assertTrue(reloaded.hasCompletedFirstPartySetupFor("Tester"));
		assertEquals(List.of(1), reloaded.toFirstPartySyncResult("Tester")
			.getObtainedItems().get("zulrah").stream().map(ClogResult.ClogItem::getId)
			.collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void anotherPluginsSearchAvoidsADuplicateAutomaticRequest() throws Exception
	{
		obtained = 0;
		openCollectionLog();
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		script(4084, widget(KillClogPlugin.CLOG_INTERFACE, 76, false), 4084, 0);
		tick(103);
		assertEquals(1, completions);
		assertEquals(0, automaticSearches);
		assertEquals(1, notices.stream().filter(text -> text.contains("is reading")).count());
	}

	@Test
	public void failedAutomaticRequestCannotCompleteAndManualSearchCanRetry() throws Exception
	{
		obtained = 0;
		failAutomaticSearch = true;
		openCollectionLog();
		tick(101);
		tick(110);
		assertEquals(1, automaticSearches);
		assertEquals(0, viewRestores);
		assertEquals(0, completions);
		assertTrue(notices.stream().anyMatch(text -> text.contains("choose Search")));
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		tick(113);
		assertEquals(1, completions);
	}

	@Test
	public void automaticPartialStreamCannotCompleteSetup() throws Exception
	{
		obtained = 3;
		automaticItems.addAll(List.of(1, 2));
		openCollectionLog();
		tick(101);
		tick(104);
		assertEquals(1, automaticSearches);
		assertEquals(0, completions);
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
		assertTrue(notices.stream().anyMatch(text -> text.contains("interrupted")));
	}

	@Test
	public void closedLogLogoutAndAccountChangesDiscardTheOpeningRequest() throws Exception
	{
		openCollectionLog();
		clogRoot = null;
		tick(101);
		clogRoot = widget(KillClogPlugin.CLOG_INTERFACE, 0, true);
		openCollectionLog();
		tick(102);
		clogRoot = widget(KillClogPlugin.CLOG_INTERFACE, 0, false);
		openCollectionLog();
		sync.reset();
		tick(103);
		openCollectionLog();
		playerName = "Someone else";
		tick(104);
		assertEquals(0, automaticSearches);
		assertEquals(0, completions);
		assertTrue(notices.isEmpty());
	}

	@Test
	public void missingCatalogOrSearchContainerAndOpenSearchDoNotSendAMenuAction() throws Exception
	{
		searchResults = null;
		openCollectionLog();
		tick(101);
		searchResults = widget(KillClogPlugin.CLOG_INTERFACE, 71, false);
		openCollectionLog();
		tick(102);
		searchResults = widget(KillClogPlugin.CLOG_INTERFACE, 71, true);
		index.clear();
		openCollectionLog();
		tick(103);
		assertEquals(0, automaticSearches);
		assertEquals(0, completions);
		assertTrue(notices.stream().anyMatch(text -> text.contains("choose Search")));
	}

	@Test
	public void hostAdventureLogCannotInitializeOrCompleteLocalSetup() throws Exception
	{
		obtained = 0;
		hostLog = true;
		openCollectionLog();
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		tick(110);
		assertEquals(0, completions);
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
		assertTrue(notices.isEmpty());
		hostLog = false;
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		hostLog = true;
		itemScript(1, 1, 111);
		hostLog = false;
		obtained = 1;
		tick(114);
		assertEquals(0, completions);
	}

	@Test
	public void nativeSearchCapturesPopulatedLogOverProviderPreseed() throws Exception
	{
		cache.cacheResult(new ClogResult("Tester", Map.of("zulrah", List.of(
			new ClogResult.ClogItem(3, 5, null))), Map.of("zulrah", List.of(1, 2, 3)),
			Map.of(), null, null));
		searchResults = widget(KillClogPlugin.CLOG_INTERFACE, 71, true);
		script(4084, widget(KillClogPlugin.CLOG_INTERFACE, 74, false), 4084, 0);
		itemScript(1, 2, 101);
		tick(104);
		assertEquals(1, completions);
		LocalClogCache reloaded = new LocalClogCache(new Gson(),
			new InlineScheduledExecutorService(), directory);
		assertTrue(reloaded.hasCompletedFirstPartySetupFor("Tester"));
		assertEquals(List.of(1), reloaded.toFirstPartySyncResult("Tester")
			.getObtainedItems().get("zulrah").stream().map(ClogResult.ClogItem::getId)
			.collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void nativeCallbackAfterMenuDoesNotRestartCapture() throws Exception
	{
		obtained = 0;
		total = 0;
		searchResults = widget(KillClogPlugin.CLOG_INTERFACE, 71, true);
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		tick(105);
		script(4084, widget(KillClogPlugin.CLOG_INTERFACE, 70, false), 4084, 0);
		tick(110);
		assertEquals(1, completions);
		assertEquals(1, notices.stream().filter(text -> text.contains("is reading")).count());
	}

	@Test
	public void nativeBackInitializationAndUnrelatedScriptsCannotArmSetup() throws Exception
	{
		obtained = 0;
		Widget source = widget(KillClogPlugin.CLOG_INTERFACE, 76, false);
		searchResults = widget(KillClogPlugin.CLOG_INTERFACE, 71, false);
		script(4084, source, 4084, 0); // Search already open: this is Back.
		searchResults = widget(KillClogPlugin.CLOG_INTERFACE, 71, true);
		script(4084, source, 4084, 1); // Forced close during initialization.
		script(4082, source, 4082, 0);
		script(4085, source, 4085, 0);
		plugin.onScriptPreFired(new ScriptPreFired(4084));
		script(4084, null, 4084, 0);
		script(4084, widget(320, 76, false), 4084, 0);
		script(4084, widget(KillClogPlugin.CLOG_INTERFACE, 76, true), 4084, 0);
		script(4084, source, (Object[]) null);
		script(4084, source, 4084);
		script(4084, source, 4084, "0");
		searchResults = null;
		script(4084, source, 4084, 0);
		tick(115);
		assertEquals(0, completions);
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
		assertFalse(notices.stream().anyMatch(text -> text.contains("is reading")));
	}

	@Test
	public void otherSearchBackAndPageScriptsCannotCompleteSetup() throws Exception
	{
		menu("Search", 320 << 16 | 76);
		menu("Back", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		itemScript(1, 1, 101);
		tick(115);
		assertEquals(0, completions);
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
		assertFalse(notices.stream().anyMatch(text -> text.contains("is reading")));
	}

	@Test
	public void repeatedSearchDoesNotRestartCaptureOrCompleteTwice() throws Exception
	{
		obtained = 0;
		total = 0;
		openCollectionLog();
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		tick(105);
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		tick(110);
		assertEquals(1, completions);
		menu("Search", KillClogPlugin.CLOG_INTERFACE << 16 | 76);
		tick(120);
		assertEquals(1, completions);
		assertEquals(1, notices.stream().filter(text -> text.contains("is reading")).count());
	}

	@Test
	public void partialStreamCannotCompleteSetupEvenWhenMoreThanHalfArrives() throws Exception
	{
		obtained = 3;
		sync.onCollectionLogSearch(client, index, cache, notifier);
		sync.captureScriptArguments(client, 4100, new Object[]{4100, 1, 1}, 101);
		sync.captureScriptArguments(client, 4100, new Object[]{4100, 2, 1}, 101);
		tick(104);
		assertEquals(0, completions);
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
		assertTrue(notices.stream().anyMatch(text -> text.contains("interrupted")));
	}

	@Test
	public void delayedCounterAndInvalidScriptsCannotBecomeAnEmptyCompletion() throws Exception
	{
		obtained = 0;
		sync.onCollectionLogSearch(client, index, cache, notifier);
		obtained = 2;
		sync.captureScriptArguments(client, 4100, new Object[]{4100, -1, 1}, 101);
		sync.captureScriptArguments(client, 4100, new Object[]{4100, 1, 0}, 101);
		tick(110);
		assertEquals(0, completions);
		tick(201);
		assertTrue(notices.stream().anyMatch(text -> text.contains("timed out")));
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
	}

	@Test
	public void nonemptyStreamWithUnsettledZeroCounterCannotCompleteSetup() throws Exception
	{
		obtained = 0;
		sync.onCollectionLogSearch(client, index, cache, notifier);
		sync.captureScriptArguments(client, 4100, new Object[]{4100, 1, 1}, 101);
		tick(104);
		assertEquals(0, completions);
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
	}

	@Test
	public void unmappedItemsCannotFillTheCompletenessThreshold() throws Exception
	{
		obtained = 3;
		sync.onCollectionLogSearch(client, index, cache, notifier);
		for (int id : new int[]{1, 2, 999})
		{
			sync.captureScriptArguments(client, 4100, new Object[]{4100, id, 1}, 101);
		}
		tick(104);
		assertEquals(0, completions);
		assertFalse(cache.hasCompletedFirstPartySetupFor("Tester"));
	}

	@Test
	public void logoutResetDiscardsThePendingStreamAndAllowsFreshSearch() throws Exception
	{
		sync.onCollectionLogSearch(client, index, cache, notifier);
		sync.captureScriptArguments(client, 4100, new Object[]{4100, 1, 1}, 101);
		sync.reset();
		sync.captureScriptArguments(client, 4100, new Object[]{4100, 2, 1}, 102);
		tick(105);
		assertEquals(0, completions);
		sync.onCollectionLogSearch(client, index, cache, notifier);
		sync.captureScriptArguments(client, 4100, new Object[]{4100, 2, 1}, 106);
		tick(109);
		assertEquals(1, completions);
		assertEquals(2, cache.toFirstPartySyncResult("Tester")
			.getObtainedItems().get("zulrah").get(0).getId());
	}

	private void tick(int count) throws Exception
	{
		tick = count;
		sync.onGameTick(client, index, cache, notifier,
			new ClogButtonOverlay(client, null, null), () -> completions++, name -> refreshes++);
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	private static Field pluginField(String name) throws Exception
	{
		Field field = KillClogPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private void openCollectionLog()
	{
		WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(KillClogPlugin.CLOG_INTERFACE);
		plugin.onWidgetLoaded(event);
	}

	private void menu(String option, int widgetId)
	{
		MenuEntry entry = (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(),
			new Class<?>[]{MenuEntry.class}, (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getOption": return option;
					case "getParam1": return widgetId;
					case "getParam0": return -1;
					case "getType": return MenuAction.CC_OP;
					default: throw new AssertionError(method.getName());
				}
			});
		plugin.onMenuOptionClicked(new MenuOptionClicked(entry));
	}

	private void itemScript(int itemId, int quantity, int count)
	{
		tick = count;
		script(4100, null, 4100, itemId, quantity);
	}

	private static Widget widget(int group, int child, boolean hidden)
	{
		return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
			new Class<?>[]{Widget.class}, (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getId": return group << 16 | child;
					case "isHidden":
					case "isSelfHidden": return hidden;
					default: throw new AssertionError(method.getName());
				}
			});
	}

	private void script(int scriptId, Widget source, Object... arguments)
	{
		ScriptEvent script = (ScriptEvent) Proxy.newProxyInstance(ScriptEvent.class.getClassLoader(),
			new Class<?>[]{ScriptEvent.class}, (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getArguments": return arguments;
					case "getSource": return source;
					default: throw new AssertionError(method.getName());
				}
			});
		ScriptPreFired event = new ScriptPreFired(scriptId);
		event.setScriptEvent(script);
		plugin.onScriptPreFired(event);
	}
}
