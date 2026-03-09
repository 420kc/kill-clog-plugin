package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

class ClogButtonOverlay extends Overlay implements MouseListener
{
	private static final int CLOG_INTERFACE = KillClogPlugin.CLOG_INTERFACE;
	private static final int ICON_SIZE = 16;
	private static final Color GREEN = new Color(76, 175, 110);
	private static final long GREEN_DURATION_MS = 2000;

	private final Client client;
	private final KillClogPlugin plugin;
	private final KillClogConfig config;

	private BufferedImage icon;
	private BufferedImage greenIcon;
	private Rectangle buttonBounds;
	private long greenUntil;

	@Inject
	ClogButtonOverlay(Client client, KillClogPlugin plugin, KillClogConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		drawAfterInterface(CLOG_INTERFACE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Widget container = client.getWidget(CLOG_INTERFACE, 0);
		if (container == null || container.isHidden())
		{
			buttonBounds = null;
			return null;
		}

		if (icon == null)
		{
			icon = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
			icon = ImageUtil.resizeImage(icon, ICON_SIZE, ICON_SIZE);

			greenIcon = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = greenIcon.createGraphics();
			g2.drawImage(icon, 0, 0, null);
			g2.setComposite(AlphaComposite.SrcAtop);
			g2.setColor(GREEN);
			g2.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
			g2.dispose();
		}

		Widget header = client.getWidget(CLOG_INTERFACE, 1);
		int x, y;
		if (header != null && !header.isHidden())
		{
			x = header.getCanvasLocation().getX() + header.getWidth() - 128;
			y = header.getCanvasLocation().getY() + 5;
		}
		else
		{
			buttonBounds = null;
			return null;
		}

		buttonBounds = new Rectangle(x, y, ICON_SIZE, ICON_SIZE);

		boolean hover = buttonBounds.contains(
			client.getMouseCanvasPosition().getX(),
			client.getMouseCanvasPosition().getY());

		boolean green = System.currentTimeMillis() < greenUntil;

		if (green)
		{
			g.drawImage(greenIcon, x, y, null);
		}
		else if (hover)
		{
			g.drawImage(icon, x, y, null);
		}
		else
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
			g.drawImage(icon, x, y, null);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
		}

		return null;
	}

	void flashGreen()
	{
		greenUntil = System.currentTimeMillis() + GREEN_DURATION_MS;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (buttonBounds != null && buttonBounds.contains(e.getPoint()))
		{
			plugin.onSyncClicked();
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		return e;
	}
}
