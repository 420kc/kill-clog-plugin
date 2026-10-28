package com.killclog;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HoverLabelTest
{
	private static final Color K4 = new Color(0xFF5700);
	private static final Color K2 = new Color(0xCAFF00);

	@Test
	public void aLabelThatAnswersLightsK2OnHoverAndKeepsItsOwnColor()
	{
		HoverLabel label = new HoverLabel();
		label.setForeground(K4);
		HoverLabel.installHover(label, () -> true);
		hover(label, MouseEvent.MOUSE_ENTERED);
		assertEquals(K2, label.getForeground());
		hover(label, MouseEvent.MOUSE_EXITED);
		assertEquals("nothing can leave it lit", K4, label.getForeground());
		label.setForeground(Color.WHITE);
		assertEquals(Color.WHITE, label.getForeground());
	}

	@Test
	public void aLabelWithNothingToOpenNeverLights()
	{
		HoverLabel label = new HoverLabel();
		label.setForeground(K4);
		HoverLabel.installHover(label, () -> false);
		hover(label, MouseEvent.MOUSE_ENTERED);
		assertEquals(K4, label.getForeground());
	}

	private static void hover(HoverLabel label, int id)
	{
		MouseEvent event = new MouseEvent(label, id, 0, 0, 1, 1, 0, false);
		for (MouseListener listener : label.getMouseListeners())
		{
			if (id == MouseEvent.MOUSE_ENTERED)
			{
				listener.mouseEntered(event);
			}
			else
			{
				listener.mouseExited(event);
			}
		}
	}
}
