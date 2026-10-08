package com.killclog;

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class TooltipControllerTest
{
	private TooltipMode mode = TooltipMode.CLICK;
	private final KillClogConfig config = new KillClogConfig()
	{
		// These check the outline; the default is now Tint.
		@Override
		public HoverStyle hoverStyle()
		{
			return HoverStyle.OUTLINE;
		}

		@Override
		public TooltipMode tooltipMode()
		{
			return mode;
		}
	};

	@Test
	public void modeLifecycleNeverChangesGlobalTooltipDelays()
	{
		ToolTipManager manager = ToolTipManager.sharedInstance();
		int originalInitial = manager.getInitialDelay();
		int originalDismiss = manager.getDismissDelay();
		try
		{
			manager.setInitialDelay(321);
			manager.setDismissDelay(4321);
			TooltipController controller = new TooltipController(config);

			controller.activate(new JPanel());
			assertEquals(321, manager.getInitialDelay());
			assertEquals(4321, manager.getDismissDelay());

			mode = TooltipMode.HOVER;
			controller.onTooltipModeChanged();
			mode = TooltipMode.CLICK;
			controller.onTooltipModeChanged();
			controller.deactivate();

			assertEquals(321, manager.getInitialDelay());
			assertEquals(4321, manager.getDismissDelay());
		}
		finally
		{
			manager.setInitialDelay(originalInitial);
			manager.setDismissDelay(originalDismiss);
		}
	}

	@Test
	public void clickModeUnregistersOnlyTrackedComponents() throws Exception
	{
		ToolTipManager manager = ToolTipManager.sharedInstance();
		TooltipController controller = new TooltipController(config);
		JLabel tracked = new JLabel();
		JLabel otherPlugin = new JLabel();
		tracked.setToolTipText("Kill Clog");
		otherPlugin.setToolTipText("Other plugin");

		try
		{
			controller.trackTooltipComponent(tracked);
			flushEdt();
			assertFalse(isRegistered(tracked, manager));
			assertTrue(isRegistered(otherPlugin, manager));
			assertEquals("Kill Clog", tracked.getToolTipText());

			mode = TooltipMode.HOVER;
			controller.onTooltipModeChanged();
			assertTrue(isRegistered(tracked, manager));
			assertTrue(isRegistered(otherPlugin, manager));

			mode = TooltipMode.CLICK;
			controller.onTooltipModeChanged();
			tracked.setToolTipText("Updated Kill Clog");
			flushEdt();
			assertFalse(isRegistered(tracked, manager));
			assertTrue(isRegistered(otherPlugin, manager));
		}
		finally
		{
			tracked.setToolTipText(null);
			otherPlugin.setToolTipText(null);
		}
	}

	@Test
	public void cellPressPinsTooltipInBothModes()
	{
		AtomicInteger pins = new AtomicInteger();
		AtomicInteger dismissedHoverPreviews = new AtomicInteger();
		TooltipController controller = new TooltipController(config)
		{
			@Override
			void dismissHoverPreview(MouseEvent event)
			{
				dismissedHoverPreviews.incrementAndGet();
			}

			@Override
			void showPinnedTooltip(JComponent source, JPanel cell)
			{
				pins.incrementAndGet();
			}
		};
		JPanel cell = new JPanel();
		JLabel label = new JLabel();
		label.setToolTipText("Kill Clog");

		try
		{
			controller.addCellHoverEffect(cell, label);
			pressWithoutToolTipManager(label);
			assertEquals(1, pins.get());
			assertEquals(0, dismissedHoverPreviews.get());

			mode = TooltipMode.HOVER;
			controller.onTooltipModeChanged();
			pressWithoutToolTipManager(label);
			assertEquals(2, pins.get());
			assertEquals(1, dismissedHoverPreviews.get());
		}
		finally
		{
			label.setToolTipText(null);
			controller.deactivate();
		}
	}

	@Test
	public void aModalOpensAnotherOnTheSamePressThatDismissedIt() throws Exception
	{
		List<Component> shown = new ArrayList<>();
		PopupFactory original = PopupFactory.getSharedInstance();
		PopupFactory.setSharedInstance(new PopupFactory()
		{
			@Override
			public Popup getPopup(Component owner, Component contents, int x, int y)
			{
				return new Popup()
				{
					@Override
					public void show()
					{
						shown.add(contents);
					}

					@Override
					public void hide()
					{
						shown.remove(contents);
					}
				};
			}
		});
		GraphicsConfiguration screen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
			.createGraphics().getDeviceConfiguration();
		JPanel cell = new JPanel()
		{
			@Override
			public Point getLocationOnScreen()
			{
				return new Point(10, 10);
			}

			@Override
			public GraphicsConfiguration getGraphicsConfiguration()
			{
				return screen;
			}
		};
		JToolTip summary = new JToolTip();
		JToolTip rare = new JToolTip();
		AtomicBoolean showing = new AtomicBoolean(true);
		JLabel source = new JLabel()
		{
			@Override
			public boolean isShowing()
			{
				return showing.get();
			}

			@Override
			public Point getLocationOnScreen()
			{
				return new Point(10, 10);
			}

			@Override
			public JToolTip createToolTip()
			{
				return summary;
			}
		};
		source.setToolTipText("Clue Summary");
		TooltipController controller = new TooltipController(config);

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				controller.showPinnedTooltip(source, cell);
				assertEquals(Collections.singletonList(summary), shown);

				// A press on a row inside the summary reaches the dismiss listener first.
				try
				{
					Field listener = TooltipController.class.getDeclaredField("pinDismissListener");
					listener.setAccessible(true);
					((AWTEventListener) listener.get(controller)).eventDispatched(new MouseEvent(
						summary, MouseEvent.MOUSE_PRESSED, 0L, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
				}
				catch (ReflectiveOperationException e)
				{
					throw new AssertionError(e);
				}
				assertTrue(shown.isEmpty());

				// The row's own request still opens, where a plain re-press of the cell would toggle off.
				controller.pinTooltip(source, cell, rare);
				assertEquals(Collections.singletonList(rare), shown);

				controller.hidePinnedTooltip();
				assertTrue(shown.isEmpty());

				// Once the panel is off screen there is nowhere to open it.
				showing.set(false);
				controller.pinTooltip(source, cell, rare);
				assertTrue(shown.isEmpty());
			});
		}
		finally
		{
			source.setToolTipText(null);
			controller.deactivate();
			PopupFactory.setSharedInstance(original);
		}
	}

	@Test
	public void aPinnedCardsWholeLifeLeavesNothingBehind() throws Exception
	{
		List<Component> shown = new ArrayList<>();
		PopupFactory original = PopupFactory.getSharedInstance();
		PopupFactory.setSharedInstance(new PopupFactory()
		{
			@Override
			public Popup getPopup(Component owner, Component contents, int x, int y)
			{
				return new Popup()
				{
					@Override
					public void show()
					{
						shown.add(contents);
					}

					@Override
					public void hide()
					{
						shown.remove(contents);
					}
				};
			}
		});
		GraphicsConfiguration screen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
			.createGraphics().getDeviceConfiguration();
		JPanel cell = new JPanel()
		{
			@Override
			public Point getLocationOnScreen()
			{
				return new Point(10, 10);
			}

			@Override
			public GraphicsConfiguration getGraphicsConfiguration()
			{
				return screen;
			}
		};
		JToolTip summary = new JToolTip();
		JLabel source = new JLabel()
		{
			@Override
			public boolean isShowing()
			{
				return true;
			}

			@Override
			public Point getLocationOnScreen()
			{
				return new Point(10, 10);
			}

			@Override
			public JToolTip createToolTip()
			{
				return summary;
			}
		};
		source.setToolTipText("Clog Summary");
		TooltipController controller = new TooltipController(config);
		int listeners = java.awt.Toolkit.getDefaultToolkit().getAWTEventListeners().length;

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				controller.showPinnedTooltip(source, cell);
				assertEquals(Collections.singletonList(summary), shown);
				assertEquals(listeners + 1, java.awt.Toolkit.getDefaultToolkit().getAWTEventListeners().length);
				assertNull("the source's own tooltip waits while its card is pinned", source.getToolTipText());

				// Summary to tab, tab to page, page back to the summary: each press closes one card and opens
				// the next, and only one dismiss listener is ever installed.
				for (JToolTip next : new JToolTip[]{new JToolTip(), new JToolTip(), summary})
				{
					dismissListener(controller).eventDispatched(new MouseEvent(shown.get(0), MouseEvent.MOUSE_PRESSED,
						0L, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
					assertTrue(shown.isEmpty());
					controller.pinTooltip(source, cell, next);
					assertEquals(Collections.singletonList(next), shown);
					assertEquals(listeners + 1, java.awt.Toolkit.getDefaultToolkit().getAWTEventListeners().length);
				}

				// Escape closes the last card and leaves nothing behind.
				dismissListener(controller).eventDispatched(new java.awt.event.KeyEvent(summary,
					java.awt.event.KeyEvent.KEY_PRESSED, 0L, 0, java.awt.event.KeyEvent.VK_ESCAPE,
					java.awt.event.KeyEvent.CHAR_UNDEFINED));
				assertTrue(shown.isEmpty());
				assertEquals(listeners, java.awt.Toolkit.getDefaultToolkit().getAWTEventListeners().length);
				assertEquals("Clog Summary", source.getToolTipText());
			});
		}
		finally
		{
			source.setToolTipText(null);
			controller.deactivate();
			PopupFactory.setSharedInstance(original);
		}
	}

	private static AWTEventListener dismissListener(TooltipController controller)
	{
		try
		{
			Field listener = TooltipController.class.getDeclaredField("pinDismissListener");
			listener.setAccessible(true);
			return (AWTEventListener) listener.get(controller);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	@Test
	public void aCardOpenedInPlaceOutlinesOnlyThePanelsOwnCells() throws Exception
	{
		PopupFactory original = PopupFactory.getSharedInstance();
		PopupFactory.setSharedInstance(new PopupFactory()
		{
			@Override
			public Popup getPopup(Component owner, Component contents, int x, int y)
			{
				return new Popup()
				{
					@Override
					public void show()
					{
					}

					@Override
					public void hide()
					{
					}
				};
			}
		});
		GraphicsConfiguration screen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
			.createGraphics().getDeviceConfiguration();
		java.awt.Color chrome = net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR;
		JPanel infoRow = onScreen(screen);
		infoRow.setBackground(chrome);
		JPanel cell = onScreen(screen);
		JLabel name = showingLabel();
		JLabel boss = showingLabel();
		infoRow.add(name);
		cell.add(boss);
		TooltipController controller = new TooltipController(config);
		controller.addCellHoverEffect(cell, boss);

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				// The Clog Summary opens a tab from the info row: the row keeps its chrome, with no outline.
				controller.pinTooltipFromPress(name, infoRow, press(name), new JToolTip());
				assertNull(infoRow.getBorder());
				controller.hidePinnedTooltip();
				controller.clearHoveredCell();
				assertEquals(chrome, infoRow.getBackground());
				assertNull(infoRow.getBorder());

				// A raid opened from a boss cell's summary still outlines that cell.
				controller.pinTooltipFromPress(boss, cell, press(boss), new JToolTip());
				assertTrue(cell.getBorder() instanceof javax.swing.border.MatteBorder);
				controller.hidePinnedTooltip();
			});
		}
		finally
		{
			controller.deactivate();
			PopupFactory.setSharedInstance(original);
		}
	}

	private static JPanel onScreen(GraphicsConfiguration screen)
	{
		return new JPanel()
		{
			@Override
			public Point getLocationOnScreen()
			{
				return new Point(10, 10);
			}

			@Override
			public GraphicsConfiguration getGraphicsConfiguration()
			{
				return screen;
			}
		};
	}

	private static JLabel showingLabel()
	{
		JLabel label = new JLabel()
		{
			@Override
			public boolean isShowing()
			{
				return true;
			}

			@Override
			public Point getLocationOnScreen()
			{
				return new Point(10, 10);
			}
		};
		label.setToolTipText("card");
		return label;
	}

	private static MouseEvent press(JComponent source)
	{
		return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0L, 0, 5, 5, 1, false, MouseEvent.BUTTON1);
	}

	@Test
	public void pinnedSourceTooltipIsSuppressedAndRestored() throws Exception
	{
		mode = TooltipMode.HOVER;
		ToolTipManager manager = ToolTipManager.sharedInstance();
		TooltipController controller = new TooltipController(config);
		JLabel source = new JLabel();
		source.setToolTipText("Kill Clog");
		controller.trackTooltipComponent(source);

		try
		{
			controller.suppressPinnedSourceTooltip(source, source.getToolTipText());
			flushEdt();
			assertNull(source.getToolTipText());
			assertFalse(isRegistered(source, manager));

			controller.restorePinnedSourceTooltip();
			flushEdt();
			assertEquals("Kill Clog", source.getToolTipText());
			assertTrue(isRegistered(source, manager));
		}
		finally
		{
			source.setToolTipText(null);
			controller.deactivate();
		}
	}

	@Test
	public void tooltipTextChangesAreFencedSynchronouslyWhilePinned() throws Exception
	{
		mode = TooltipMode.HOVER;
		AtomicBoolean pinned = new AtomicBoolean();
		ToolTipManager manager = ToolTipManager.sharedInstance();
		TooltipController controller = new TooltipController(config)
		{
			@Override
			boolean hasPinnedTooltip()
			{
				return pinned.get();
			}
		};
		JLabel summary = new JLabel();
		summary.setToolTipText("Summary");

		try
		{
			controller.trackTooltipComponent(summary);
			assertTrue(isRegistered(summary, manager));
			controller.setTooltipText(summary, null);
			assertFalse(isRegistered(summary, manager));

			pinned.set(true);
			SwingUtilities.invokeAndWait(() ->
			{
				controller.setTooltipText(summary, "Updated summary");
				assertFalse(isRegistered(summary, manager));
			});

			pinned.set(false);
			SwingUtilities.invokeAndWait(() ->
			{
				controller.setTooltipText(summary, "Restored summary");
				assertTrue(isRegistered(summary, manager));
			});
		}
		finally
		{
			summary.setToolTipText(null);
			controller.deactivate();
		}
	}

	@Test
	public void enteringPinnedTooltipClearsResidualHoverPreview()
	{
		AtomicInteger dismissedHoverPreviews = new AtomicInteger();
		TooltipController controller = new TooltipController(config)
		{
			@Override
			void dismissHoverPreview(MouseEvent event)
			{
				dismissedHoverPreviews.incrementAndGet();
			}
		};
		JToolTip tip = new JToolTip();
		controller.guardPinnedTooltip(tip);
		MouseEvent event = new MouseEvent(tip, MouseEvent.MOUSE_ENTERED,
			System.currentTimeMillis(), 0, 1, 1, 0, false);
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseEntered(event);
		}

		assertEquals(1, dismissedHoverPreviews.get());
	}

	@Test
	public void scopedRefreshDismissesOnlyItsOwnPinnedSource() throws Exception
	{
		TooltipController controller = new TooltipController(config);
		JLabel boss = new JLabel();
		JLabel skill = new JLabel();
		Field activeSource = TooltipController.class.getDeclaredField("activePinnedComponent");
		activeSource.setAccessible(true);
		activeSource.set(controller, boss);

		controller.hidePinnedTooltipIfOwnedBy(skill);
		assertSame(boss, activeSource.get(controller));

		controller.hidePinnedTooltipIfOwnedBy(boss);
		assertNull(activeSource.get(controller));
	}

	private static boolean isRegistered(JLabel label, ToolTipManager manager)
	{
		for (MouseListener listener : label.getMouseListeners())
		{
			if (listener == manager)
			{
				return true;
			}
		}
		return false;
	}

	private static void flushEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	private static void pressWithoutToolTipManager(JLabel label)
	{
		ToolTipManager manager = ToolTipManager.sharedInstance();
		MouseEvent event = new MouseEvent(label, MouseEvent.MOUSE_PRESSED,
			System.currentTimeMillis(), 0, 1, 1, 1, false, MouseEvent.BUTTON1);
		for (MouseListener listener : label.getMouseListeners())
		{
			if (listener != manager)
			{
				listener.mousePressed(event);
			}
		}
	}
}
