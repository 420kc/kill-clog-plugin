package com.killclog;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProfileCardPlayerModelTest
{
	@Test
	public void rendersCopiedModelGeometryToTransparentPortrait()
	{
		int bronze = (7 << 10) | (5 << 7) | 58;
		ProfileCardPlayerModel.Snapshot model = new ProfileCardPlayerModel.Snapshot(
			new float[]{-48, 48, 48, -48, 0},
			new float[]{0, 0, 0, 0, -130},
			new float[]{-40, -40, 40, 40, 0},
			new int[]{0, 1, 2, 3, 0, 1},
			new int[]{1, 2, 3, 0, 4, 4},
			new int[]{4, 4, 4, 4, 3, 2},
			new int[]{bronze, bronze, bronze, bronze, bronze, bronze},
			new int[]{bronze, bronze, bronze, bronze, bronze, bronze},
			new int[]{-1, -1, -1, -1, -1, -1},
			null, null, null);

		BufferedImage portrait = ProfileCardPlayerModel.render(model);
		assertNotNull(portrait);
		assertEquals(ProfileCardPlayerModel.WIDTH, portrait.getWidth());
		assertEquals(ProfileCardPlayerModel.HEIGHT, portrait.getHeight());
		assertTrue(hasVisiblePixel(portrait));
	}

	@Test
	public void rendersRealTexturePixelsInsteadOfFallbackFaceColor()
	{
		Map<Integer, ProfileCardPlayerModel.TextureData> textures = new HashMap<>();
		textures.put(4, new ProfileCardPlayerModel.TextureData(
			new int[]{0xFF5B11, 0xD9300A, 0xFF8A19, 0x7A0900}, 2, 0f, 0f));
		ProfileCardPlayerModel.Snapshot model = new ProfileCardPlayerModel.Snapshot(
			new float[]{-50, 50, -50},
			new float[]{0, 0, -100},
			new float[]{0, 0, 0},
			new int[]{0}, new int[]{1}, new int[]{2},
			new int[]{127}, new int[]{127}, new int[]{-1},
			null, new short[]{4}, null,
			new float[]{0f, 1f, 0f}, new float[]{0f, 0f, 1f}, textures);

		BufferedImage portrait = ProfileCardPlayerModel.render(model);

		assertNotNull(portrait);
		assertTrue(hasOrangePixel(portrait));
	}

	private static boolean hasVisiblePixel(BufferedImage image)
	{
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) != 0)
				{
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasOrangePixel(BufferedImage image)
	{
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int color = image.getRGB(x, y);
				int red = color >> 16 & 0xFF;
				int green = color >> 8 & 0xFF;
				int blue = color & 0xFF;
				if ((color >>> 24) > 128 && red > green * 2 && green > blue)
				{
					return true;
				}
			}
		}
		return false;
	}
}
