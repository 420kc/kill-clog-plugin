package com.killclog;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Where a right-click offers a Kill Clog lookup: a Jagex interface change breaks this silently, in game only. */
public class ClogLookupMenuTest
{
	private static final KillClogConfig ALL_ON = new KillClogConfig()
	{
	};

	@Test
	public void eachListOffersTheLookupBesideItsOwnOption()
	{
		int chat = InterfaceID.CHATBOX << 16 | 5;
		int pm = InterfaceID.PM_CHAT << 16 | 3;
		int friends = InterfaceID.FRIENDS << 16 | 11;
		int ignore = InterfaceID.IGNORE << 16 | 11;
		int channel = InterfaceID.CHATCHANNEL_CURRENT << 16 | 8;
		int gim = InterfaceID.GIM_SIDEPANEL << 16 | 4;
		Object[][] table = {
			{chat, "Add ignore", true}, {chat, "Message", true}, {chat, "Report", false},
			{pm, "Add ignore", true}, {pm, "Message", true},
			{friends, "Delete", true}, {friends, "Message", false},
			{ignore, "Delete", true},
			{channel, "Add ignore", true}, {channel, "Remove friend", true}, {channel, "Kick", false},
			{InterfaceID.ClansSidepanel.PLAYERLIST, "Add ignore", true},
			{InterfaceID.ClansSidepanel.PLAYERLIST, "Remove friend", true},
			{InterfaceID.ClansSidepanel.PLAYERLIST, "Message", false},
			{InterfaceID.ClansGuestSidepanel.PLAYERLIST, "Add ignore", true},
			{gim, "Add friend", true}, {gim, "Remove friend", true}, {gim, "Remove ignore", true},
			{InterfaceID.BANKMAIN << 16 | 12, "Delete", false},
		};
		for (Object[] row : table)
		{
			List<String> looked = new ArrayList<>();
			boolean added = offer(ALL_ON, (int) row[0], (String) row[1], "<col=ffffff>Foo_Bar</col>", looked);
			assertEquals(row[0] + " " + row[1], row[2], added);
			if (added)
			{
				assertEquals("tags and the underscore go: the name as Jagex spells it", List.of("Foo Bar"), looked);
			}
		}
	}

	@Test
	public void eachListsSettingTurnsItsLookupOff()
	{
		KillClogConfig noChat = new KillClogConfig()
		{
			@Override
			public boolean menuOnChat()
			{
				return false;
			}
		};
		KillClogConfig noLookups = new KillClogConfig()
		{
			@Override
			public boolean playerMenuLookup()
			{
				return false;
			}
		};
		int chat = InterfaceID.CHATBOX << 16 | 5;
		assertFalse(offer(noChat, chat, "Message", "Foo", new ArrayList<>()));
		assertTrue(offer(noChat, InterfaceID.PM_CHAT << 16 | 3, "Message", "Foo", new ArrayList<>()));
		assertFalse(offer(noLookups, chat, "Message", "Foo", new ArrayList<>()));
	}

	@Test
	public void aFullPlayerMenuWarnsOnceASession()
	{
		ClogLookupMenu menu = new ClogLookupMenu();
		Client client = mock(Client.class);
		KillClogChatNotifier notifier = mock(KillClogChatNotifier.class);
		when(client.getPlayerOptions()).thenReturn(new String[]{"", "", "", "", "Trade", "Follow", "Report", "Walk"});
		menu.warnIfPlayerMenuSlotUnavailable(client, ALL_ON, notifier);
		menu.warnIfPlayerMenuSlotUnavailable(client, ALL_ON, notifier);
		verify(notifier, times(1)).send(eq(ChatNotice.WARNING), contains("Right-click menu is full"));

		ClogLookupMenu placed = new ClogLookupMenu();
		KillClogChatNotifier quiet = mock(KillClogChatNotifier.class);
		when(client.getPlayerOptions()).thenReturn(new String[]{"", "", "", "", "Trade", "Kill Clog", "Report", "Walk"});
		placed.warnIfPlayerMenuSlotUnavailable(client, ALL_ON, quiet);
		verifyNoInteractions(quiet);
	}

	/** Whether a right-click on this component and option gained a lookup entry; a click on it records the name. */
	private static boolean offer(KillClogConfig config, int componentId, String option, String target, List<String> looked)
	{
		MenuEntry source = mock(MenuEntry.class);
		when(source.getType()).thenReturn(MenuAction.CC_OP);
		when(source.getParam1()).thenReturn(componentId);
		when(source.getOption()).thenReturn(option);
		when(source.getTarget()).thenReturn(target);
		MenuEntry created = mock(MenuEntry.class, RETURNS_SELF);
		Menu gameMenu = mock(Menu.class);
		when(gameMenu.createMenuEntry(-2)).thenReturn(created);
		Client client = mock(Client.class);
		when(client.getMenu()).thenReturn(gameMenu);

		new ClogLookupMenu().addLookupEntry(new MenuEntryAdded(source), client, config, looked::add);
		boolean added = mockingDetails(gameMenu).getInvocations().stream()
			.anyMatch(call -> call.getMethod().getName().equals("createMenuEntry"));
		if (added)
		{
			org.mockito.ArgumentCaptor<java.util.function.Consumer<MenuEntry>> click =
				org.mockito.ArgumentCaptor.forClass(java.util.function.Consumer.class);
			verify(created).onClick(click.capture());
			click.getValue().accept(created);
		}
		return added;
	}
}
