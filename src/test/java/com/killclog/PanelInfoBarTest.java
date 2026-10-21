package com.killclog;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.ColorUtil;
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
			assertEquals(new Color(70, 70, 70), bar(menu));
			for (MouseListener listener : menu.getMouseListeners())
			{
				listener.mouseEntered(new MouseEvent(menu, MouseEvent.MOUSE_ENTERED, 0L, 0, 1, 1, 0, false));
			}
			assertEquals(new Color(96, 96, 96), bar(menu));
		});
	}

	@Test
	public void theMenuButtonFollowsItsBarWhateverColourTheBarIsNow() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel row = PanelInfoBar.build(new JLabel(), new JLabel(), new TooltipController(new KillClogConfig()
			{
			}), () -> false, () ->
			{
			});
			JLabel menu = (JLabel) row.getComponent(1);
			row.setBackground(new Color(62, 19, 19));
			assertEquals("the same tint of a maroon bar", ColorUtil.colorLerp(new Color(62, 19, 19), Color.WHITE, 30 / 215.0),
				bar(menu));
		});
	}

	/** The colour of the menu button's top bar, as it paints now. */
	private static Color bar(JLabel menu)
	{
		Icon icon = menu.getIcon();
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		icon.paintIcon(menu, g, 0, 0);
		g.dispose();
		return new Color(image.getRGB(2, 0), true).equals(new Color(0, 0, 0, 0)) ? null : new Color(image.getRGB(2, 0));
	}
}
