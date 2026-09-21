package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClueSummaryTooltipTest
{
	@Test
	public void rareRowsAreHitTestedOnlyWhereTheyWerePainted()
	{
		ClueSummaryTooltip tip = painted(card());
		// Nothing above the rare section answers, and nothing below its five rows.
		assertEquals(-1, tip.rareAt(0));
		int top = firstRareY(tip);
		assertEquals(-1, tip.rareAt(top - 1));
		for (int row = 0; row < ClueSummaryTooltip.RARE_LABELS.length; row++)
		{
			assertEquals(row, tip.rareAt(top + row * NativeTooltip.LINE_HEIGHT));
			assertEquals(row, tip.rareAt(top + row * NativeTooltip.LINE_HEIGHT + NativeTooltip.LINE_HEIGHT - 1));
		}
		assertEquals(-1, tip.rareAt(top + ClueSummaryTooltip.RARE_LABELS.length * NativeTooltip.LINE_HEIGHT));
	}

	@Test
	public void aRareRowAnswersExactlyWhereItsIconWasPainted()
	{
		ClueSummaryTooltip tip = card();
		BufferedImage[] icons = new BufferedImage[ClueSummaryTooltip.RARE_LABELS.length];
		for (int row = 0; row < icons.length; row++)
		{
			icons[row] = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = icons[row].createGraphics();
			g.setColor(new Color(255, 0, 255 - row));
			g.fillRect(0, 0, 13, 13);
			g.dispose();
		}
		tip.setRareIcons(icons);
		Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		tip.paint(graphics);
		graphics.dispose();

		int x = NativeTooltip.getInset() + 6;
		int found = 0;
		for (int y = 0; y < size.height; y++)
		{
			int rgb = image.getRGB(x, y);
			for (int row = 0; row < icons.length; row++)
			{
				if (rgb == new Color(255, 0, 255 - row).getRGB())
				{
					// Every painted pixel of a row's icon belongs to that row and no other.
					assertEquals(row, tip.rareAt(y));
					found++;
				}
			}
		}
		assertEquals(13 * icons.length, found);
		// The last row ends inside the card.
		assertTrue(firstRareY(tip) + icons.length * NativeTooltip.LINE_HEIGHT
			<= size.height - NativeTooltip.getInset());
	}

	@Test
	public void pressingARareRowOpensThatCollectionAndNothingElseDoes()
	{
		ClueSummaryTooltip tip = card();
		List<Integer> opened = new ArrayList<>();
		tip.setOnOpenRare((press, row) -> opened.add(row));

		// Before the first paint there is no rare section to press.
		press(tip, 5, 200, MouseEvent.BUTTON1);
		assertTrue(opened.isEmpty());

		painted(tip);
		int top = firstRareY(tip);
		press(tip, 5, top + 2 * NativeTooltip.LINE_HEIGHT + 3, MouseEvent.BUTTON1);
		assertEquals(List.of(2), opened);

		// A tier line is not a link, and neither is a right click on a rare row.
		press(tip, 5, top - 40, MouseEvent.BUTTON1);
		press(tip, 5, top + 3, MouseEvent.BUTTON3);
		assertEquals(List.of(2), opened);
	}

	@Test
	public void theHoveredRareRowIsTrackedAndClearedOnExit()
	{
		ClueSummaryTooltip tip = painted(card());
		int top = firstRareY(tip);
		move(tip, 5, top + NativeTooltip.LINE_HEIGHT + 2);
		assertEquals(1, tip.hoveredRare());
		move(tip, 5, 2);
		assertEquals(-1, tip.hoveredRare());
		move(tip, 5, top + 2);
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mouseExited(event(tip, MouseEvent.MOUSE_EXITED, 5, top + 2, MouseEvent.NOBUTTON));
		}
		assertEquals(-1, tip.hoveredRare());
	}

	@Test
	public void progressWidensTheCardAndItsAbsenceLeavesItBare()
	{
		ClueSummaryTooltip bare = new ClueSummaryTooltip();
		bare.setData(null, false);
		ClueSummaryTooltip withProgress = card();
		// Same rows either way, so a comparison pair never staggers.
		assertEquals(bare.getPreferredSize().height, withProgress.getPreferredSize().height);
		assertTrue(bare.getPreferredSize().width < withProgress.getPreferredSize().width);

		// Counts cleared again (an unsynced player): back to the bare width.
		for (int line = 1; line <= 7; line++)
		{
			withProgress.setProgress(line, null);
		}
		for (int row = 0; row < ClueSummaryTooltip.RARE_LABELS.length; row++)
		{
			withProgress.setRare(row, null);
		}
		assertEquals(bare.getPreferredSize().width, withProgress.getPreferredSize().width);
	}

	private static ClueSummaryTooltip card()
	{
		ClueSummaryTooltip tip = new ClueSummaryTooltip();
		tip.setData(null, false);
		for (int line = 1; line <= 6; line++)
		{
			tip.setProgress(line, new int[]{line * 10, 140});
		}
		tip.setProgress(7, new int[]{1, 1});
		for (int row = 0; row < ClueSummaryTooltip.RARE_LABELS.length; row++)
		{
			tip.setRare(row, new int[]{row, 38});
		}
		return tip;
	}

	private static ClueSummaryTooltip painted(ClueSummaryTooltip tip)
	{
		Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		Graphics2D graphics = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		tip.paint(graphics);
		graphics.dispose();
		return tip;
	}

	/** The first y that answers as rare row 0, found the way a pointer would. */
	private static int firstRareY(ClueSummaryTooltip tip)
	{
		for (int y = 0; y < tip.getHeight(); y++)
		{
			if (tip.rareAt(y) == 0)
			{
				return y;
			}
		}
		throw new AssertionError("no rare section painted");
	}

	private static void press(ClueSummaryTooltip tip, int x, int y, int button)
	{
		for (MouseListener listener : tip.getMouseListeners())
		{
			listener.mousePressed(event(tip, MouseEvent.MOUSE_PRESSED, x, y, button));
		}
	}

	private static void move(ClueSummaryTooltip tip, int x, int y)
	{
		for (MouseMotionListener listener : tip.getMouseMotionListeners())
		{
			listener.mouseMoved(event(tip, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON));
		}
	}

	private static MouseEvent event(ClueSummaryTooltip tip, int id, int x, int y, int button)
	{
		return new MouseEvent(tip, id, System.currentTimeMillis(), 0, x, y, 1, false, button);
	}
}
