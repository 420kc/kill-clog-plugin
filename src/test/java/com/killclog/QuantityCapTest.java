package com.killclog;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The game stops counting most log items at 250 and a few stackables at 65,535; a count at its cap reads green. */
public class QuantityCapTest
{
	private static final int ZULRAHS_SCALES = 12934;
	private static final int BRACELET_OF_ETHEREUM = 21817;

	@Test
	public void eachItemReadsGreenAtItsOwnCap()
	{
		assertTrue(TooltipItemSprites.atCap(BRACELET_OF_ETHEREUM, 250));
		assertFalse(TooltipItemSprites.atCap(BRACELET_OF_ETHEREUM, 249));
		// Scales count on past 250, so 250 of them is just a stack.
		assertFalse(TooltipItemSprites.atCap(ZULRAHS_SCALES, 250));
		assertTrue(TooltipItemSprites.atCap(ZULRAHS_SCALES, 65535));
		assertEquals(53, PanelData.COUNTS_PAST_250_ITEMS.length);
	}

	@Test
	public void theGridPaintsACappedCountGreenAndAnyOtherYellow()
	{
		assertEquals(TitleTooltip.CLOG_GREEN.getRGB(), quantityColor(BRACELET_OF_ETHEREUM, 250));
		assertEquals(TitleTooltip.CLOG_YELLOW.getRGB(), quantityColor(BRACELET_OF_ETHEREUM, 249));
		assertEquals(TitleTooltip.CLOG_YELLOW.getRGB(), quantityColor(ZULRAHS_SCALES, 250));
	}

	/** The brightest pixel the count paints in its corner: its digits in the count's color over their black shadow. */
	private static int quantityColor(int itemId, int count)
	{
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		TooltipItemSprites.paintGrid(g, null, null, 0, List.of(itemId), Set.of(itemId), Map.of(itemId, count),
			0, 0, 1, 32, 36);
		g.dispose();
		for (int y = 0; y < 40; y++)
		{
			for (int x = 0; x < 40; x++)
			{
				int rgb = image.getRGB(x, y);
				if (rgb == TitleTooltip.CLOG_GREEN.getRGB() || rgb == TitleTooltip.CLOG_YELLOW.getRGB())
				{
					return rgb;
				}
			}
		}
		return 0;
	}
}
