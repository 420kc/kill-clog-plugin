package com.killclog;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Which presses open a card's Open / Copy menu, shaped as Java delivers them on Windows and on a Mac. */
public class CardMenuPressTest
{
	private static final JLabel CELL = new JLabel();

	private static MouseEvent press(int modifiers, boolean popupTrigger, int button)
	{
		return new MouseEvent(CELL, MouseEvent.MOUSE_PRESSED, 0L, modifiers, 5, 5, 1, popupTrigger, button);
	}

	@Test
	public void theRightButtonOpensTheMenuWhereverItsTriggerFalls()
	{
		// Windows marks the menu's trigger on the release, so its press is the right button alone.
		assertTrue(TooltipController.opensMenu(press(InputEvent.BUTTON3_DOWN_MASK, false, MouseEvent.BUTTON3)));
		// A Mac or Linux marks it on the press.
		assertTrue(TooltipController.opensMenu(press(InputEvent.BUTTON3_DOWN_MASK, true, MouseEvent.BUTTON3)));
	}

	@Test
	public void aMacsCtrlClickOpensTheMenu()
	{
		// OpenJDK's macOS NSEvent.isPopupTrigger: the left button held with Ctrl, the left button's number.
		assertTrue(TooltipController.opensMenu(
			press(InputEvent.BUTTON1_DOWN_MASK | InputEvent.CTRL_DOWN_MASK, true, MouseEvent.BUTTON1)));
	}

	@Test
	public void theLeftButtonOpensTheCardItself()
	{
		assertFalse(TooltipController.opensMenu(press(InputEvent.BUTTON1_DOWN_MASK, false, MouseEvent.BUTTON1)));
		assertFalse(TooltipController.opensMenu(
			press(InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, false, MouseEvent.BUTTON1)));
		// On Windows Ctrl+click is no menu trigger: the card opens, as it always has.
		assertFalse(TooltipController.opensMenu(
			press(InputEvent.BUTTON1_DOWN_MASK | InputEvent.CTRL_DOWN_MASK, false, MouseEvent.BUTTON1)));
	}
}
