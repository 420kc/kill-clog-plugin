package com.killclog;

import java.awt.event.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.*;
import javax.swing.SwingUtilities;
import javax.swing.text.*;
import com.google.common.collect.EvictingQueue;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.*;

/**
 * Autocompletes player names in the search bar from search history plus a
 * client-thread snapshot of friends, clan, and nearby players, and brings back
 * this session's searches with the right arrow.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Inject)
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
	// A modifier pressed alone, which leaves a recall standing.
	private static final Set<Integer> MODIFIER_KEYS = new HashSet<>(Arrays.asList(KeyEvent.VK_SHIFT,
		KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META, KeyEvent.VK_ALT_GRAPH));
	// Which of this session's searches Right last put in the box, newest at 0, and its text; -1 once a key ended it.
	private int recalled = -1;
	private String recalledText;

	/**
	 * With the box empty, or still showing just what Right last brought back with the cursor at its end, Right steps
	 * back through this session's searches, from the newest round to it again (Dylan 11/4). At the end of the box Right
	 * has nothing to move, so it never takes a cursor move; a click elsewhere in the name, a selection (an autofill's),
	 * a name written in by a lookup, or any other key, typed or pressed, leaves Right as the box's own. With Shift, Ctrl
	 * or Alt held it is always the box's own.
	 */
	@Override
	public void keyPressed(KeyEvent e)
	{
		if (e.getKeyCode() != KeyEvent.VK_RIGHT || e.getModifiersEx() != 0)
		{
			if (!MODIFIER_KEYS.contains(e.getKeyCode()))
			{
				recalled = -1;
			}
			return;
		}
		JTextComponent input = (JTextComponent) e.getSource();
		String text = input.getText();
		List<String> newestFirst = new ArrayList<>(searchHistory);
		Collections.reverse(newestFirst);
		boolean atEnd = input.getSelectionStart() == text.length() && input.getSelectionEnd() == text.length();
		boolean showing = recalled >= 0 && text.equals(recalledText) && atEnd;
		if (newestFirst.isEmpty() || !(text.isEmpty() || showing))
		{
			return;
		}
		recalled = showing ? (recalled + 1) % newestFirst.size() : 0;
		recalledText = newestFirst.get(recalled);
		input.setText(recalledText);
		input.setCaretPosition(recalledText.length());
		autofill = null;
		e.consume();
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		recalled = -1;
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
		addMembers(names, client.getFriendContainer());
		addMembers(names, client.getFriendsChatManager());
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
		// A friend in your clan and in view is one name to offer.
		List<String> copy = names == null ? Collections.emptyList() : new ArrayList<>(new LinkedHashSet<>(names));
		clientNameSnapshot = Collections.unmodifiableList(copy);
	}

	/** The names in a friends list or a friends chat, either of which may not be loaded yet. */
	private void addMembers(List<String> names, @Nullable NameableContainer<?> members)
	{
		if (members == null || members.getMembers() == null)
		{
			return;
		}
		Arrays.stream(members.getMembers())
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
