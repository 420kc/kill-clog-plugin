package com.killclog;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayDeque;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.Text;

/**
 * Autocompletes player names in the search bar from friends, clan, and nearby players.
 * Mirrors the native HiscorePanel NameAutocompleter behavior.
 */
@Singleton
public class NameAutocompleter implements KeyListener
{
    private static final int MAX_HISTORY = 25;
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9_ -]");
    private static final String NBSP = "\u00a0";

    @Inject
    private Client client;

    private final ArrayDeque<String> searchHistory = new ArrayDeque<>(MAX_HISTORY);
    private IconTextField searchBar;

    public void setSearchBar(IconTextField searchBar)
    {
        this.searchBar = searchBar;
    }

    public void addToSearchHistory(String name)
    {
        searchHistory.remove(name.toLowerCase());
        searchHistory.addFirst(name.toLowerCase());
        while (searchHistory.size() > MAX_HISTORY)
        {
            searchHistory.removeLast();
        }
    }

    @Override
    public void keyTyped(KeyEvent e)
    {
        if (searchBar == null)
        {
            return;
        }

        char typed = e.getKeyChar();
        if (typed == KeyEvent.CHAR_UNDEFINED || typed == '\b' || typed == '\n' || typed == '\r')
        {
            return;
        }

        // Get the text field component to check selection state
        JTextComponent textComponent = getTextComponent();
        if (textComponent == null)
        {
            return;
        }

        // Build what the text will be after this keystroke
        String current = textComponent.getText();
        int selStart = textComponent.getSelectionStart();
        int selEnd = textComponent.getSelectionEnd();
        String nameStart = (current.substring(0, selStart) + typed + current.substring(selEnd)).trim();

        if (nameStart.isEmpty() || INVALID_CHARS.matcher(nameStart).find())
        {
            return;
        }

        String match = findAutocompleteName(nameStart);
        if (match != null)
        {
            final int caretPos = nameStart.length();
            SwingUtilities.invokeLater(() ->
            {
                textComponent.setText(match);
                textComponent.setSelectionStart(caretPos);
                textComponent.setSelectionEnd(match.length());
            });
            e.consume();
        }
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
    }

    private JTextComponent getTextComponent()
    {
        for (java.awt.Component c : searchBar.getComponents())
        {
            if (c instanceof net.runelite.client.ui.components.FlatTextField)
            {
                return ((net.runelite.client.ui.components.FlatTextField) c).getTextField();
            }
        }
        return null;
    }

    private String findAutocompleteName(String nameStart)
    {
        String lower = nameStart.toLowerCase();

        // 1. Search history
        for (String name : searchHistory)
        {
            if (name.toLowerCase().startsWith(lower))
            {
                return name;
            }
        }

        // 2. Friends list
        FriendContainer friends = client.getFriendContainer();
        if (friends != null)
        {
            for (Friend f : friends.getMembers())
            {
                String name = Text.toJagexName(f.getName());
                if (name.toLowerCase().startsWith(lower))
                {
                    return name;
                }
            }
        }

        // 3. Friends chat
        FriendsChatManager fc = client.getFriendsChatManager();
        if (fc != null)
        {
            for (FriendsChatMember m : fc.getMembers())
            {
                String name = Text.toJagexName(m.getName());
                if (name.toLowerCase().startsWith(lower))
                {
                    return name;
                }
            }
        }

        // 4. Clan channel
        ClanChannel clan = client.getClanChannel();
        if (clan != null)
        {
            for (ClanChannelMember m : clan.getMembers())
            {
                String name = Text.toJagexName(m.getName());
                if (name.toLowerCase().startsWith(lower))
                {
                    return name;
                }
            }
        }

        // 5. Clan settings (offline members)
        ClanSettings clanSettings = client.getClanSettings();
        if (clanSettings != null)
        {
            for (ClanMember m : clanSettings.getMembers())
            {
                String name = Text.toJagexName(m.getName());
                if (name.toLowerCase().startsWith(lower))
                {
                    return name;
                }
            }
        }

        // 6. Guest clan
        ClanChannel guestClan = client.getGuestClanChannel();
        if (guestClan != null)
        {
            for (ClanChannelMember m : guestClan.getMembers())
            {
                String name = Text.toJagexName(m.getName());
                if (name.toLowerCase().startsWith(lower))
                {
                    return name;
                }
            }
        }

        // 7. Nearby players
        WorldView wv = client.getTopLevelWorldView();
        if (wv != null)
        {
            for (Player p : wv.players())
            {
                if (p != null && p.getName() != null)
                {
                    String name = Text.toJagexName(p.getName());
                    if (name.toLowerCase().startsWith(lower))
                    {
                        return name;
                    }
                }
            }
        }

        return null;
    }
}
