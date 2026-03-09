package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

@Slf4j
class ClogButtonOverlay extends Overlay implements MouseListener
{
	private static final int CLOG_INTERFACE = 621;
	private static final int ICON_SIZE = 16;

	private final Client client;
	private final KillClogPlugin plugin;
	private final KillClogConfig config;

	private BufferedImage icon;
	private Rectangle buttonBounds;

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

		if (config.clogSource() == ClogSource.TEMPLE)
		{
			buttonBounds = null;
			return null;
		}

		if (icon == null)
		{
			icon = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
			icon = ImageUtil.resizeImage(icon, ICON_SIZE, ICON_SIZE);
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

		if (hover)
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

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (buttonBounds != null && buttonBounds.contains(e.getPoint()))
		{
			log.debug("Sync button clicked at ({}, {})", e.getX(), e.getY());
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
