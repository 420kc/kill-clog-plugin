package com.killclog;

import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/** The boss list's search row behaves like the panel's search bar (2.6.3): a text cursor, and it lightens on hover. */
public class BossListSearchTest
{
	@Test
	public void theSearchRowShowsTheTextCursorAndLightensUnderTheMouse()
	{
		BossListView list = new BossListView(mock(TooltipController.class), mock(Cells.class), () -> false);
		JTextField field = TestViews.field(list, "search");
		JPanel row = (JPanel) field.getParent();
		assertEquals(Cursor.TEXT_CURSOR, field.getCursor().getType());
		// Selected text reads as it does in the panel's search bar: white on the brand orange.
		assertEquals(java.awt.Color.WHITE, field.getSelectedTextColor());
		assertEquals(ColorScheme.BRAND_ORANGE_TRANSPARENT, field.getSelectionColor());
		assertEquals(ColorScheme.DARKER_GRAY_COLOR, row.getBackground());

		for (MouseListener listener : field.getMouseListeners())
		{
			listener.mouseEntered(new MouseEvent(field, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
		}
		assertEquals(ColorScheme.DARK_GRAY_HOVER_COLOR, row.getBackground());
		for (MouseListener listener : field.getMouseListeners())
		{
			listener.mouseExited(new MouseEvent(field, MouseEvent.MOUSE_EXITED, 0, 0, 1, 1, 0, false));
		}
		assertEquals(ColorScheme.DARKER_GRAY_COLOR, row.getBackground());
	}

	@Test
	public void theSearchRowAndItsDividerMatchThePanelsSearchBarAndSeparator()
	{
		BossListView list = new BossListView(mock(TooltipController.class), mock(Cells.class), () -> false);
		JTextField field = TestViews.field(list, "search");
		JPanel row = (JPanel) field.getParent();
		assertEquals(30, row.getPreferredSize().height);
		assertEquals(30, row.getMaximumSize().height);
		java.awt.Container root = row.getParent();
		java.awt.Component divider = root.getComponent(1);
		assertEquals(7, divider.getPreferredSize().height);
		assertEquals(7, divider.getMaximumSize().height);
	}
}
