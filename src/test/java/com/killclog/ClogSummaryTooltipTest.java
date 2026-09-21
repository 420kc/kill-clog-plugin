package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClogSummaryTooltipTest
{
	private static final int ICON = 13;
	private static final int ICON_STEP = 16;

	@Test
	public void theTierLadderReadsAReachedTierGreenAndTheNextOneRed()
	{
		// 1,150 of 1,700: rune is reached, dragon is not.
		ClogSummaryTooltip tip = painted(card(1150, 1700));
		Dimension idle = tip.getPreferredSize();
		int y = ladderY(tip);

		move(tip, ladderX(tip, 6) + 6, y);
		assertEquals("Rune: 1,100-1,199", tip.getHeaderHoverLineText());
		assertEquals(new Color(0, 255, 0), tip.getHeaderHoverLineColor());

		move(tip, ladderX(tip, 7) + 6, y);
		assertEquals("Dragon: 1,200-1,524 (50 more)", tip.getHeaderHoverLineText());
		assertEquals(new Color(255, 0, 0), tip.getHeaderHoverLineColor());

		move(tip, ladderX(tip, 8) + 6, y);
		assertEquals("Gilded: 1,525+ (375 more)", tip.getHeaderHoverLineText());

		// The readout row is always reserved, so hovering never resizes the card.
		assertEquals(idle, tip.getPreferredSize());
		move(tip, 1, 1);
		assertNull(tip.getHeaderHoverLineText());
	}

	@Test
	public void theCurrentTierSitsInTheHeaderCornerClearOfTheTitle() throws ReflectiveOperationException
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		tip.setRank(4321);
		Dimension bare = tip.getPreferredSize();
		BufferedImage sprite = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sprite.createGraphics();
		g.setColor(Color.MAGENTA);
		g.fillRect(0, 0, 32, 32);
		g.dispose();
		Field field = ClogSummaryTooltip.class.getDeclaredField("tierSprite");
		field.setAccessible(true);
		((BufferedImage[]) field.get(tip))[0] = sprite;

		// A decoration, not a row: the card is the same size with it.
		assertEquals(bare, tip.getPreferredSize());
		tip.setSize(bare);
		BufferedImage image = new BufferedImage(bare.width, bare.height, BufferedImage.TYPE_INT_ARGB);
		g = image.createGraphics();
		tip.paint(g);
		g.dispose();

		int inset = NativeTooltip.getInset();
		int left = bare.width;
		int bottom = 0;
		for (int y = 0; y < bare.height; y++)
		{
			for (int x = 0; x < bare.width; x++)
			{
				if (image.getRGB(x, y) == Color.MAGENTA.getRGB())
				{
					left = Math.min(left, x);
					bottom = Math.max(bottom, y);
				}
			}
		}
		assertEquals(bare.width - inset - 32, left);
		// Inside the header, above its rule, and right of the widest header text.
		assertTrue(bottom < inset + tip.getHeaderHeight());
		int titleWidth = tip.getFontMetrics(tip.getTitleFont()).stringWidth("Clog Summary");
		assertTrue(inset + titleWidth < left);
	}

	@Test
	public void everyTabAddsARowAndACardWithoutTabsHasNone()
	{
		ClogSummaryTooltip bare = card(1150, 1700);
		ClogSummaryTooltip withTabs = card(1150, 1700);
		Map<String, int[]> tabs = new LinkedHashMap<>();
		tabs.put("Bosses", new int[]{290, 381});
		tabs.put("Raids", new int[]{60, 79});
		withTabs.setTabs(tabs);
		int twoTabs = withTabs.getPreferredSize().height;
		assertTrue(twoTabs > bare.getPreferredSize().height);

		tabs.put("Clues", new int[]{0, 470});
		int perTab = withTabs.getPreferredSize().height - twoTabs;
		assertTrue(perTab > NativeTooltip.LINE_HEIGHT);
		tabs.put("Minigames", new int[]{120, 120});
		assertEquals(twoTabs + 2 * perTab, withTabs.getPreferredSize().height);
		painted(withTabs);
	}

	@Test
	public void theLastUpdateSitsInTheFooterWithTheSources()
	{
		ClogSummaryTooltip tip = card(1150, 1700);
		tip.setClogSources(true, false, false);
		int sourcesOnly = tip.getPreferredSize().height;
		tip.setSyncData("2 hours ago", false);
		// One more line under the same rule, not a second section.
		assertEquals(sourcesOnly + NativeTooltip.LINE_HEIGHT, tip.getPreferredSize().height);
		painted(tip);
	}

	@Test
	public void aCardWithNoTotalsPaintsNoLadder()
	{
		ClogSummaryTooltip tip = new ClogSummaryTooltip();
		tip.setTitle("Clog Summary");
		painted(tip);
		move(tip, tip.getWidth() / 2, tip.getHeight() - 4);
		assertNull(tip.getHeaderHoverLineText());
	}

	private static ClogSummaryTooltip card(int obtained, int total)
	{
		ClogSummaryTooltip tip = new ClogSummaryTooltip();
		tip.setTierData(obtained, total, null, null);
		return tip;
	}

	private static ClogSummaryTooltip painted(ClogSummaryTooltip tip)
	{
		Dimension size = tip.getPreferredSize();
		tip.setSize(size);
		Graphics2D graphics = new BufferedImage(
			size.width, size.height, BufferedImage.TYPE_INT_ARGB).createGraphics();
		tip.paint(graphics);
		graphics.dispose();
		return tip;
	}

	/** The ladder is the last thing on a card with no tabs, items or sources. */
	private static int ladderY(ClogSummaryTooltip tip)
	{
		return tip.getHeight() - NativeTooltip.getInset() - ICON / 2
			- TitleTooltip.hoverRowHeight(tip.getFontMetrics(FontManager.getRunescapeSmallFont()));
	}

	private static int ladderX(ClogSummaryTooltip tip, int tier)
	{
		int width = ClogHelper.CLOG_TIERS.length * ICON_STEP - (ICON_STEP - ICON);
		return (tip.getWidth() - width) / 2 + tier * ICON_STEP;
	}

	private static void move(ClogSummaryTooltip tip, int x, int y)
	{
		for (MouseMotionListener listener : tip.getMouseMotionListeners())
		{
			listener.mouseMoved(new MouseEvent(tip, MouseEvent.MOUSE_MOVED,
				System.currentTimeMillis(), 0, x, y, 0, false, MouseEvent.NOBUTTON));
		}
	}
}
