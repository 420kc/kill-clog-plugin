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
	private BufferedImage card;
	private String rsn;
	private int maxWidth;
	private int maxHeight;
	private JLabel image;
	private JLabel status;
	private JLabel syncLink;

	void show(Component owner, BufferedImage card, String rsn,
		Runnable syncAction, boolean published)
	{
		close();
		ownerWindow = SwingUtilities.getWindowAncestor(owner);
		Dimension limit = ownerWindow != null
			? ownerWindow.getSize() : Toolkit.getDefaultToolkit().getScreenSize();
		this.card = card;
		this.rsn = rsn;
		maxWidth = Math.max(320, limit.width - 64);
		maxHeight = Math.max(192, limit.height - 112);
		root = build(scaled(card, maxWidth, maxHeight), syncAction, published);
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
		card = null;
		rsn = null;
		image = null;
		status = null;
		syncLink = null;
	}

	void replaceCard(BufferedImage replacement)
	{
		card = replacement;
		if (image != null)
		{
			image.setIcon(new ImageIcon(scaled(replacement, maxWidth, maxHeight)));
		}
	}

	void showSyncStatus(String text, boolean ok)
	{
		if (status == null)
		{
			return;
		}
		if (ok && "synced!".equals(text))
		{
			status.setText("synced to killclog.com");
			status.setForeground(TitleTooltip.CLOG_GREEN);
			if (syncLink != null)
			{
				syncLink.setText("update killclog.com");
			}
		}
		else if ("syncing...".equals(text) || "retrying...".equals(text))
		{
			status.setText("syncing...".equals(text) ? "updating..." : text);
			status.setForeground(NativeTooltip.OSRS_ORANGE);
		}
		else if ("sync failed".equals(text))
		{
			status.setText(text);
			status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else if ("sync not published".equals(text))
		{
			status.setText(text);
			status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else if (text == null || text.isBlank())
		{
			status.setText(" ");
		}
	}

	void showAppearanceStatus(String text, boolean ok)
	{
		if (status == null)
		{
			return;
		}
		status.setText(text == null || text.isBlank() ? " " : text);
		status.setForeground(ok ? TitleTooltip.CLOG_GREEN : ColorScheme.LIGHT_GRAY_COLOR);
	}

	private JPanel build(BufferedImage preview, Runnable syncAction, boolean published)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(12, 12, 10, 12));

		image = new JLabel(new ImageIcon(preview));
		image.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add(image);
		panel.add(Box.createRigidArea(new Dimension(0, 7)));

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
		controls.setOpaque(false);
		controls.setAlignmentX(Component.CENTER_ALIGNMENT);
		status = new JLabel(" ");
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		controls.add(status);
		controls.add(Box.createHorizontalGlue());

		controls.add(link("copy", () ->
		{
			boolean copied = ProfileCardShare.copyToClipboard(card);
			status.setText(copied ? "copied to clipboard" : "clipboard unavailable");
			status.setForeground(copied
				? TitleTooltip.CLOG_GREEN : ColorScheme.LIGHT_GRAY_COLOR);
		}));
		controls.add(Box.createRigidArea(new Dimension(14, 0)));

		File[] saved = new File[1];
		JLabel[] saveLink = new JLabel[1];
		saveLink[0] = link("save", () ->
		{
			if (saved[0] == null)
			{
				saved[0] = ProfileCardShare.saveToDisk(card, rsn);
				status.setText(saved[0] != null ? "saved to screenshots" : "save failed");
				status.setForeground(saved[0] != null
					? TitleTooltip.CLOG_GREEN : ColorScheme.LIGHT_GRAY_COLOR);
				if (saved[0] != null)
				{
					saveLink[0].setText("show in folder");
				}
			}
			else
			{
				openFolder(saved[0]);
			}
		});
		controls.add(saveLink[0]);
		controls.add(Box.createRigidArea(new Dimension(14, 0)));

		if (syncAction != null)
		{
			syncLink = link(published ? "update killclog.com" : "publish to killclog.com", () ->
			{
				status.setText("updating...");
				status.setForeground(NativeTooltip.OSRS_ORANGE);
				syncAction.run();
			});
			syncLink.setToolTipText("Publish profile and current character");
			controls.add(syncLink);
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
