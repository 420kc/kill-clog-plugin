package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.Test;

/** The frame's mirrored corners and edges come out pixel for pixel as the hand-written flip drew them. */
public class CardSpritesFlipTest
{
	@Test
	public void runeLitesFlipMatchesTheOldOneBothWays()
	{
		Random random = new Random(42);
		// Lopsided and partly transparent, so any wrong axis or lost alpha shows.
		BufferedImage source = new BufferedImage(7, 5, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 5; y++)
		{
			for (int x = 0; x < 7; x++)
			{
				source.setRGB(x, y, random.nextInt());
			}
		}
		for (boolean vertical : new boolean[]{true, false})
		{
			BufferedImage expected = oldFlip(source, vertical);
			BufferedImage actual = CardSprites.flip(source, vertical);
			for (int y = 0; y < 5; y++)
			{
				for (int x = 0; x < 7; x++)
				{
					assertEquals(vertical + " " + x + "," + y, expected.getRGB(x, y), actual.getRGB(x, y));
				}
			}
		}
		assertNull(CardSprites.flip(null, true));
	}

	/** The plugin's flip before it used RuneLite's ImageUtil.flipImage. */
	private static BufferedImage oldFlip(BufferedImage src, boolean vertical)
	{
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = flipped.createGraphics();
		g.drawImage(src, 0, 0, w, h, vertical ? 0 : w, vertical ? h : 0, vertical ? w : 0, vertical ? 0 : h, null);
		g.dispose();
		return flipped;
	}
}
