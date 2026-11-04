package com.killclog;

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
}
