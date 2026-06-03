package com.killclog;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import com.google.common.collect.EvictingQueue;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.FriendContainer;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.Nameable;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;

/**
 * Autocompletes player names in the search bar from search history plus a
 * client-thread snapshot of friends, clan, and nearby players.
 */
@Slf4j
@Singleton
public class NameAutocompleter implements KeyListener
{
	private static final String NBSP = Character.toString('\u00a0');
	private static final Pattern INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9_ -]");
	private static final int MAX_HISTORY = 25;

	private final Client client;
	private final EvictingQueue<String> searchHistory = EvictingQueue.create(MAX_HISTORY);
	private volatile List<String> clientNameSnapshot = Collections.emptyList();
	private String autofill;
	private Pattern autofillPattern;

	@Inject
	NameAutocompleter(Client client)
	{
		this.client = client;
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		JTextComponent input = (JTextComponent) e.getSource();
		String inputText = input.getText();

		if (input.getSelectionEnd() != inputText.length())
		{
			return;
		}

		String charToInsert = Character.toString(e.getKeyChar());
		if (INVALID_CHARS.matcher(charToInsert).find() || INVALID_CHARS.matcher(inputText).find())
		{
			return;
		}

		if (autofill != null && autofillPattern.matcher(inputText).matches())
		{
			if (isExpectedNext(input, charToInsert))
			{
				try
				{
					int insertIndex = input.getSelectionStart();
					int selectionEnd = input.getSelectionEnd();
					Document doc = input.getDocument();
					doc.remove(insertIndex, 1);
					doc.insertString(insertIndex, charToInsert, null);
					input.select(insertIndex + 1, selectionEnd);
				}
				catch (BadLocationException ex)
				{
					log.warn("Could not insert character.", ex);
				}
				e.consume();
			}
			else
			{
				startAutofill(e);
			}
		}
		else
		{
			startAutofill(e);
		}
	}

	private void startAutofill(KeyEvent e)
	{
		JTextComponent input = (JTextComponent) e.getSource();
		String inputText = input.getText();
		String nameStart = inputText.substring(0, input.getSelectionStart()) + e.getKeyChar();

		if (findAutofill(nameStart))
		{
			String name = autofill;
			SwingUtilities.invokeLater(() ->
			{
				try
				{
					input.getDocument().insertString(nameStart.length(), name.substring(nameStart.length()), null);
					input.select(nameStart.length(), name.length());
				}
				catch (BadLocationException ex)
				{
					log.warn("Could not autocomplete name.", ex);
				}
			});
		}
	}

	private boolean findAutofill(String nameStart)
	{
		String match = findAutofillCandidate(nameStart);
		if (match != null)
		{
			autofill = match.replace(NBSP, " ");
			autofillPattern = Pattern.compile("(?i)^" + autofill.replaceAll("[ _-]", "[ _-]") + "$");
		}
		else
		{
			autofill = null;
			autofillPattern = null;
		}

		return match != null;
	}

	String findAutofillCandidate(String nameStart)
	{
		Pattern pattern = Pattern.compile("(?i)^" + nameStart.replaceAll("[ _-]", "[ _" + NBSP + "-]") + ".+?");

		String historyMatch = searchHistory.stream()
			.filter(n -> pattern.matcher(n).matches())
			.findFirst()
			.orElse(null);
		if (historyMatch != null)
		{
			return historyMatch;
		}

		return clientNameSnapshot.stream()
			.filter(n -> pattern.matcher(n).matches())
			.findFirst()
			.orElse(null);
	}

	void addToSearchHistory(@NonNull String name)
	{
		searchHistory.remove(name);
		searchHistory.offer(name);
	}

	void refreshClientSnapshot()
	{
		if (client == null)
		{
			setClientNameSnapshot(Collections.emptyList());
			return;
		}

		List<String> names = new ArrayList<>();
		addFriendNames(names);
		addFriendsChatNames(names);
		addClanNames(names);
		addNearbyPlayerNames(names);
		setClientNameSnapshot(names);
	}

	void clearClientSnapshot()
	{
		setClientNameSnapshot(Collections.emptyList());
	}

	void setClientNameSnapshot(List<String> names)
	{
		List<String> copy = names == null ? Collections.emptyList() : new ArrayList<>(names);
		clientNameSnapshot = Collections.unmodifiableList(copy);
	}

	private void addFriendNames(List<String> names)
	{
		FriendContainer friendContainer = client.getFriendContainer();
		if (friendContainer == null || friendContainer.getMembers() == null)
		{
			return;
		}
		Arrays.stream(friendContainer.getMembers())
			.filter(Objects::nonNull)
			.map(Nameable::getName)
			.forEach(name -> addName(names, name));
	}

	private void addFriendsChatNames(List<String> names)
	{
		FriendsChatManager friendsChatManager = client.getFriendsChatManager();
		if (friendsChatManager == null || friendsChatManager.getMembers() == null)
		{
			return;
		}
		Arrays.stream(friendsChatManager.getMembers())
			.filter(Objects::nonNull)
			.map(Nameable::getName)
			.forEach(name -> addName(names, name));
	}

	private void addClanNames(List<String> names)
	{
		ClanSettings[] clanSettings = new ClanSettings[]{
			client.getClanSettings(0),
			client.getClanSettings(1),
			client.getGuestClanSettings()
		};
		Arrays.stream(clanSettings)
			.filter(Objects::nonNull)
			.map(ClanSettings::getMembers)
			.filter(Objects::nonNull)
			.flatMap(List::stream)
			.filter(Objects::nonNull)
			.map(ClanMember::getName)
			.forEach(name -> addName(names, name));
	}

	private void addNearbyPlayerNames(List<String> names)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}
		worldView.players().stream()
			.filter(Objects::nonNull)
			.map(Actor::getName)
			.forEach(name -> addName(names, name));
	}

	private static void addName(List<String> names, String name)
	{
		if (name != null && !name.trim().isEmpty())
		{
			names.add(name);
		}
	}

	private boolean isExpectedNext(JTextComponent input, String nextChar)
	{
		String expected;
		if (input.getSelectionStart() < input.getSelectionEnd())
		{
			try
			{
				expected = input.getText(input.getSelectionStart(), 1);
			}
			catch (BadLocationException ex)
			{
				log.warn("Could not get first character from input selection.", ex);
				return false;
			}
		}
		else
		{
			expected = "";
		}
		return nextChar.equalsIgnoreCase(expected);
	}
}
