package com.killclog;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Large non-blocking preview centered over the RuneLite window. */
final class ProfileCardPreview
{
	private Popup popup;
	private JPanel root;
	private AWTEventListener dismissListener;
	private Window ownerWindow;
	private WindowAdapter focusListener;

	void show(Component owner, BufferedImage card, ProfileCardShare.Export export)
	{
		close();
		ownerWindow = SwingUtilities.getWindowAncestor(owner);
		Dimension limit = ownerWindow != null
			? ownerWindow.getSize() : Toolkit.getDefaultToolkit().getScreenSize();
		BufferedImage preview = scaled(card,
			Math.max(320, limit.width - 64), Math.max(192, limit.height - 112));
		root = build(preview, export);
		root.setSize(root.getPreferredSize());

		Point anchor = popupPoint(owner, root.getPreferredSize());
		popup = PopupFactory.getSharedInstance().getPopup(
			owner, root, anchor.x, anchor.y);
		popup.show();
		installDismissListeners();
	}

	void close()
	{
		if (popup != null)
		{
			popup.hide();
			popup = null;
		}
		if (dismissListener != null)
		{
			Toolkit.getDefaultToolkit().removeAWTEventListener(dismissListener);
			dismissListener = null;
		}
		if (ownerWindow != null && focusListener != null)
		{
			ownerWindow.removeWindowFocusListener(focusListener);
		}
		ownerWindow = null;
		focusListener = null;
		root = null;
	}

	private JPanel build(BufferedImage card, ProfileCardShare.Export export)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(12, 12, 10, 12));

		JLabel image = new JLabel(new ImageIcon(card));
		image.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add(image);
		panel.add(Box.createRigidArea(new Dimension(0, 7)));

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
		controls.setOpaque(false);
		controls.setAlignmentX(Component.CENTER_ALIGNMENT);
		String confirmation = export.copied
			? "copied to clipboard" : "saved to screenshots";
		JLabel status = new JLabel(confirmation);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(TitleTooltip.CLOG_GREEN);
		controls.add(status);
		controls.add(Box.createHorizontalGlue());
		if (export.saved != null)
		{
			controls.add(link("show in folder", () -> openFolder(export.saved)));
			controls.add(Box.createRigidArea(new Dimension(14, 0)));
		}
		controls.add(link("close", this::close));
		panel.add(controls);
		return panel;
	}

	private static JLabel link(String text, Runnable action)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(NativeTooltip.OSRS_ORANGE);
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent event)
			{
				label.setForeground(java.awt.Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				label.setForeground(NativeTooltip.OSRS_ORANGE);
			}

			@Override
			public void mousePressed(MouseEvent event)
			{
				if (event.getButton() == MouseEvent.BUTTON1)
				{
					action.run();
				}
			}
		});
		return label;
	}

	private void installDismissListeners()
	{
		dismissListener = event ->
		{
			if (event instanceof KeyEvent
				&& event.getID() == KeyEvent.KEY_PRESSED
				&& ((KeyEvent) event).getKeyCode() == KeyEvent.VK_ESCAPE)
			{
				SwingUtilities.invokeLater(this::close);
			}
			else if (event instanceof MouseEvent
				&& event.getID() == MouseEvent.MOUSE_PRESSED
				&& event.getSource() instanceof Component
				&& root != null
				&& !SwingUtilities.isDescendingFrom((Component) event.getSource(), root))
			{
				SwingUtilities.invokeLater(this::close);
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(dismissListener,
			AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);

		if (ownerWindow != null)
		{
			focusListener = new WindowAdapter()
			{
				@Override
				public void windowLostFocus(WindowEvent event)
				{
					SwingUtilities.invokeLater(ProfileCardPreview.this::close);
				}
			};
			ownerWindow.addWindowFocusListener(focusListener);
		}
	}

	private Point popupPoint(Component owner, Dimension popupSize)
	{
		try
		{
			if (ownerWindow != null)
			{
				Point point = ownerWindow.getLocationOnScreen();
				return new Point(
					point.x + (ownerWindow.getWidth() - popupSize.width) / 2,
					point.y + (ownerWindow.getHeight() - popupSize.height) / 2);
			}
			Point point = owner.getLocationOnScreen();
			return new Point(point.x, point.y);
		}
		catch (IllegalComponentStateException ignored)
		{
			return new Point(40, 40);
		}
	}

	private static BufferedImage scaled(BufferedImage card, int maxWidth, int maxHeight)
	{
		double scale = Math.min(1d, Math.min(
			maxWidth / (double) card.getWidth(), maxHeight / (double) card.getHeight()));
		if (scale >= 1d)
		{
			return card;
		}
		int width = Math.max(1, (int) Math.round(card.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(card.getHeight() * scale));
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(card, 0, 0, width, height, null);
		g.dispose();
		return image;
	}

	private static void openFolder(File saved)
	{
		File parent = saved.getParentFile();
		if (parent != null)
		{
			LinkBrowser.open(parent.getAbsolutePath());
		}
	}
}
