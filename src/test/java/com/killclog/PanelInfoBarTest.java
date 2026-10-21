package com.killclog;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PanelInfoBarTest
{
	@Test
	public void theMenuButtonIsATintOfTheBarItSitsOnTheGreysItAlwaysWasOnRuneLitesGrey() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel row = PanelInfoBar.build(new JLabel(), new JLabel(), new TooltipController(new KillClogConfig()
			{
			}), () -> false, () ->
			{
			});
			JLabel menu = (JLabel) row.getComponent(1);
			assertEquals(new Color(70, 70, 70), bar(menu.getIcon()));
			for (MouseListener listener : menu.getMouseListeners())
			{
				listener.mouseEntered(new MouseEvent(menu, MouseEvent.MOUSE_ENTERED, 0L, 0, 1, 1, 0, false));
			}
			assertEquals(new Color(96, 96, 96), bar(menu.getIcon()));
		});
	}

	private static Color bar(Icon icon)
	{
		BufferedImage image = (BufferedImage) ((ImageIcon) icon).getImage();
		return new Color(image.getRGB(2, 0), true).equals(new Color(0, 0, 0, 0)) ? null : new Color(image.getRGB(2, 0));
	}
}
