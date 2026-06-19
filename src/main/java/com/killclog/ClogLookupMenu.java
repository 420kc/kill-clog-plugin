package com.killclog;

import java.util.function.Consumer;
import javax.inject.Provider;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.util.Text;

final class ClogLookupMenu
{
	// OSRS player right-click menu reserves indexes 4-7 for plugins (4 slots total).
	private static final int FIRST_PLUGIN_PLAYER_SLOT = 4;
	private static final int LAST_PLUGIN_PLAYER_SLOT_EXCLUSIVE = 8;

	// Right-click menu option strings used to filter Kill Clog lookup entries.
	private static final String OPT_ADD_FRIEND = "Add friend";
	private static final String OPT_ADD_IGNORE = "Add ignore";
	private static final String OPT_REMOVE_FRIEND = "Remove friend";
	private static final String OPT_REMOVE_IGNORE = "Remove ignore";
	private static final String OPT_DELETE = "Delete";
	private static final String OPT_MESSAGE = "Message";

	private String menuOption = "Kill Clog";
	private boolean playerMenuSlotWarned;

	void start(KillClogConfig config, Provider<MenuManager> menuManager)
	{
		menuOption = config.menuLabel().getLabel();
		setPlayerMenuItemEnabled(config, menuManager, config.playerMenuLookup());
	}

	void stop(KillClogConfig config, Provider<MenuManager> menuManager)
	{
		setPlayerMenuItemEnabled(config, menuManager, false);
		playerMenuSlotWarned = false;
	}

	void refresh(KillClogConfig config, Provider<MenuManager> menuManager)
	{
		menuManager.get().removePlayerMenuItem(menuOption);
		menuOption = config.menuLabel().getLabel();
		setPlayerMenuItemEnabled(config, menuManager, config.playerMenuLookup());
	}

	void warnIfPlayerMenuSlotUnavailable(Client client, KillClogConfig config,
		KillClogChatNotifier chatNotifier)
	{
		if (playerMenuSlotWarned || !config.playerMenuLookup() || !config.menuOnPlayers())
		{
			return;
		}

		String[] options = client.getPlayerOptions();
		if (options == null || options.length < LAST_PLUGIN_PLAYER_SLOT_EXCLUSIVE)
		{
			return;
		}
		for (int i = FIRST_PLUGIN_PLAYER_SLOT; i < LAST_PLUGIN_PLAYER_SLOT_EXCLUSIVE; i++)
		{
			if (menuOption.equals(options[i]))
			{
				return;
			}
		}

		playerMenuSlotWarned = true;
		chatNotifier.send(ChatNotice.WARNING,
			"Right-click menu is full. Use the side panel or right-click names in chat instead.");
	}

	void addLookupEntry(MenuEntryAdded event, Client client, KillClogConfig config,
		Consumer<String> lookup)
	{
		if (!config.playerMenuLookup())
		{
			return;
		}

		if (event.getType() != MenuAction.CC_OP.getId()
			&& event.getType() != MenuAction.CC_OP_LOW_PRIORITY.getId())
		{
			return;
		}

		int componentId = event.getActionParam1();
		int groupId = WidgetUtil.componentToInterface(componentId);

		if (!isLookupEligible(config, groupId, componentId, event.getOption()))
		{
			return;
		}

		String target = event.getTarget();
		if (target == null)
		{
			return;
		}
		String name = Text.toJagexName(Text.removeTags(target).trim());
		client.getMenu().createMenuEntry(-2)
			.setOption(menuOption)
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.setIdentifier(event.getIdentifier())
			.onClick(e -> lookup.accept(name));
	}

	void handlePlayerLookup(MenuOptionClicked event, KillClogConfig config,
		Consumer<String> lookup)
	{
		if (event.getMenuAction() != MenuAction.RUNELITE_PLAYER
			|| !event.getMenuOption().equals(menuOption)
			|| !config.menuOnPlayers())
		{
			return;
		}

		Player player = event.getMenuEntry().getPlayer();
		if (player == null || player.getName() == null)
		{
			return;
		}

		lookup.accept(Text.toJagexName(player.getName()));
	}

	private void setPlayerMenuItemEnabled(KillClogConfig config,
		Provider<MenuManager> menuManager, boolean enabled)
	{
		if (enabled && config.menuOnPlayers())
		{
			menuManager.get().addPlayerMenuItem(menuOption);
		}
		else
		{
			menuManager.get().removePlayerMenuItem(menuOption);
		}
	}

	private boolean isLookupEligible(KillClogConfig config, int groupId, int componentId, String option)
	{
		// Clan player lists live in two different interfaces; match by componentId, not groupId.
		if (componentId == InterfaceID.ClansSidepanel.PLAYERLIST)
		{
			return config.menuOnClanList()
				&& (OPT_ADD_IGNORE.equals(option) || OPT_REMOVE_FRIEND.equals(option));
		}
		if (componentId == InterfaceID.ClansGuestSidepanel.PLAYERLIST)
		{
			return config.menuOnGuestClanList()
				&& (OPT_ADD_IGNORE.equals(option) || OPT_REMOVE_FRIEND.equals(option));
		}
		switch (groupId)
		{
			case InterfaceID.FRIENDS:
				return config.menuOnFriendsList() && OPT_DELETE.equals(option);
			case InterfaceID.IGNORE:
				return config.menuOnIgnoreList() && OPT_DELETE.equals(option);
			case InterfaceID.CHATCHANNEL_CURRENT:
				return config.menuOnChatChannels()
					&& (OPT_ADD_IGNORE.equals(option) || OPT_REMOVE_FRIEND.equals(option));
			case InterfaceID.CHATBOX:
				return config.menuOnChat()
					&& (OPT_ADD_IGNORE.equals(option) || OPT_MESSAGE.equals(option));
			case InterfaceID.PM_CHAT:
				return config.menuOnPrivateMessages()
					&& (OPT_ADD_IGNORE.equals(option) || OPT_MESSAGE.equals(option));
			case InterfaceID.GIM_SIDEPANEL:
				return config.menuOnGroupIronman()
					&& (OPT_ADD_FRIEND.equals(option)
						|| OPT_REMOVE_FRIEND.equals(option)
						|| OPT_REMOVE_IGNORE.equals(option));
			default:
				return false;
		}
	}
}
