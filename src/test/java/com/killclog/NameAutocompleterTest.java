package com.killclog;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import javax.swing.JTextField;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameAutocompleterTest
{
	@Test
	public void testSearchHistoryWinsOverClientSnapshot()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.setClientNameSnapshot(Arrays.asList("rng shango", "rng stranger"));
		autocompleter.addToSearchHistory("rng history");

		assertEquals("rng history", autocompleter.findAutofillCandidate("rng"));
	}

	@Test
	public void testClientSnapshotSuppliesMatchWithoutClientThreadRead()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.setClientNameSnapshot(Arrays.asList("cbc", "420 kc"));

		assertEquals("420 kc", autocompleter.findAutofillCandidate("420"));
	}

	@Test
	public void testSeparatorCharactersMatchEachOther()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.setClientNameSnapshot(Arrays.asList("iron" + Character.toString('\u00a0') + "whiff"));

		assertEquals("iron" + Character.toString('\u00a0') + "whiff",
			autocompleter.findAutofillCandidate("iron "));
	}

	@Test
	public void testClearClientSnapshotRemovesClientMatches()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.setClientNameSnapshot(Arrays.asList("cbc"));
		autocompleter.clearClientSnapshot();

		assertNull(autocompleter.findAutofillCandidate("cb"));
	}

	private static boolean press(NameAutocompleter autocompleter, JTextField box, int key)
	{
		KeyEvent event = new KeyEvent(box, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED);
		autocompleter.keyPressed(event);
		return event.isConsumed();
	}

	@Test
	public void rightBringsBackThisSessionsSearchesIntoAnEmptyBox()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		JTextField box = new JTextField();
		assertFalse("nothing searched yet: Right is the box's own", press(autocompleter, box, KeyEvent.VK_RIGHT));
		autocompleter.addToSearchHistory("Zezima");
		autocompleter.addToSearchHistory("Lynx Titan");
		autocompleter.addToSearchHistory("420 kc");

		assertTrue(press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("the newest first", "420 kc", box.getText());
		press(autocompleter, box, KeyEvent.VK_RIGHT);
		assertEquals("Lynx Titan", box.getText());
		press(autocompleter, box, KeyEvent.VK_RIGHT);
		assertEquals("Zezima", box.getText());
		press(autocompleter, box, KeyEvent.VK_RIGHT);
		assertEquals("past the oldest, round to the newest", "420 kc", box.getText());
		assertFalse("Left stays the box's own", press(autocompleter, box, KeyEvent.VK_LEFT));
	}

	@Test
	public void aBoxHoldingAnythingElseKeepsItsRightArrow()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.addToSearchHistory("Zezima");
		JTextField box = new JTextField("Lyn");
		assertFalse(press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("Lyn", box.getText());

		box.setText("");
		press(autocompleter, box, KeyEvent.VK_RIGHT);
		box.setText("Zezima2");
		assertFalse("an edited recall moves the cursor again", press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("Zezima2", box.getText());
	}

	private static NameAutocompleter recalledLynxTitan(JTextField box)
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.addToSearchHistory("Zezima");
		autocompleter.addToSearchHistory("Lynx Titan");
		autocompleter.addToSearchHistory("420 kc");
		press(autocompleter, box, KeyEvent.VK_RIGHT);
		press(autocompleter, box, KeyEvent.VK_RIGHT);
		assertEquals("Lynx Titan", box.getText());
		return autocompleter;
	}

	@Test
	public void aNameTypedOverARecallKeepsRightForTheAutofill() throws Exception
	{
		// After a recall, typing the same name's first letter autofills it, and Right must accept that, not
		// swap in another search because the text matches the one recalled.
		JTextField box = new JTextField();
		NameAutocompleter autocompleter = recalledLynxTitan(box);
		box.setText("");
		// The key, then the field taking its letter, in one turn of the Swing thread as Swing types it: typed from the
		// test's thread, the autofill could run before the letter landed and find nothing to finish (a slow CI runner).
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
			autocompleter.keyTyped(new KeyEvent(box, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'L'));
			box.setText("L");
		});
		javax.swing.SwingUtilities.invokeAndWait(Thread::yield);      // and the autofill lands after it
		assertEquals("the autofill offers the rest", "Lynx Titan", box.getText());
		assertEquals(1, box.getSelectionStart());
		assertFalse(press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("Lynx Titan", box.getText());
	}

	@Test
	public void aSearchedRecallKeepsItsRightArrow()
	{
		// Enter on a recalled name looks it up and the panel writes it back; Right then moves the cursor.
		JTextField box = new JTextField();
		NameAutocompleter autocompleter = recalledLynxTitan(box);
		press(autocompleter, box, KeyEvent.VK_ENTER);
		autocompleter.addToSearchHistory("Lynx Titan");
		box.setText("Lynx Titan");
		assertFalse(press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("Lynx Titan", box.getText());
	}

	@Test
	public void enterOnTheNewestRecallEndsIt()
	{
		// Enter on the newest search, which stays the newest, still hands Right back to the box.
		JTextField box = new JTextField();
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.addToSearchHistory("Zezima");
		autocompleter.addToSearchHistory("420 kc");
		press(autocompleter, box, KeyEvent.VK_RIGHT);
		assertEquals("420 kc", box.getText());
		press(autocompleter, box, KeyEvent.VK_ENTER);
		autocompleter.addToSearchHistory("420 kc");
		box.setText("420 kc");
		assertFalse(press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("420 kc", box.getText());
	}

	@Test
	public void aClickIntoARecallOrALookupWrittenInKeepsRightForTheCursor()
	{
		// No key ends these, so the recall holds only while the box shows just what it put there, cursor at
		// its end, where Right has nothing to move.
		JTextField box = new JTextField();
		NameAutocompleter autocompleter = recalledLynxTitan(box);
		box.setCaretPosition(2);
		assertFalse("a click into the name: Right moves the cursor", press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("Lynx Titan", box.getText());

		// The newest recalled, then a lookup's name written in becomes the newest search in its place.
		box = new JTextField();
		autocompleter = new NameAutocompleter(null);
		autocompleter.addToSearchHistory("Zezima");
		autocompleter.addToSearchHistory("420 kc");
		press(autocompleter, box, KeyEvent.VK_RIGHT);
		assertEquals("420 kc", box.getText());
		autocompleter.addToSearchHistory("Woox");
		box.setText("Woox");
		box.setCaretPosition(4);
		assertFalse("a lookup's name written in is the player's own", press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("Woox", box.getText());
	}

	@Test
	public void aSelectionRunningToTheEndKeepsRightForTheBox()
	{
		// An autofill's tail is selected to the box's end; with no key between, only the selection's start
		// tells it from a recall, and Right accepts it.
		JTextField box = new JTextField();
		NameAutocompleter autocompleter = recalledLynxTitan(box);
		box.select(1, box.getText().length());
		assertFalse(press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("Lynx Titan", box.getText());
	}

	@Test
	public void shiftAloneLeavesTheRecallStanding()
	{
		JTextField box = new JTextField();
		NameAutocompleter autocompleter = recalledLynxTitan(box);
		press(autocompleter, box, KeyEvent.VK_SHIFT);
		assertTrue(press(autocompleter, box, KeyEvent.VK_RIGHT));
		assertEquals("Zezima", box.getText());
		press(autocompleter, box, KeyEvent.VK_LEFT);
		box.setCaretPosition(box.getText().length());
		assertFalse("Left ends it, though no character was typed", press(autocompleter, box, KeyEvent.VK_RIGHT));
	}

	@Test
	public void rightWithAModifierIsTheBoxsOwn()
	{
		JTextField box = new JTextField();
		NameAutocompleter autocompleter = recalledLynxTitan(box);
		press(autocompleter, box, KeyEvent.VK_SHIFT);
		for (int mask : new int[]{InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK})
		{
			KeyEvent event = new KeyEvent(box, KeyEvent.KEY_PRESSED, 0, mask, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED);
			autocompleter.keyPressed(event);
			assertFalse("Shift, Ctrl or Alt with Right selects or moves by word, as the box does", event.isConsumed());
			assertEquals("Lynx Titan", box.getText());
		}
	}
}
