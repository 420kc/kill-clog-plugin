package com.killclog;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class KillClogChatEmojiTest
{
	@Test
	public void testRewriteBothEmoji()
	{
		assertEquals("<img=7> <img=8>",
			KillClogChatEmoji.rewriteText(":killclog: :clog:", 7, 8));
	}

	@Test
	public void testRewriteRepeatedEmoji()
	{
		assertEquals("<img=8> <img=8>",
			KillClogChatEmoji.rewriteText(":clog: :clog:", null, 8));
	}

	@Test
	public void testRewriteLeavesUnknownText()
	{
		assertNull(KillClogChatEmoji.rewriteText(":other:", 7, 8));
	}

	@Test
	public void testRewriteTierEmoji()
	{
		Map<String, Integer> icons = new LinkedHashMap<>();
		icons.put(KillClogChatEmoji.RUNE_TRIGGER, 9);
		icons.put(KillClogChatEmoji.DRAGON_TRIGGER, 10);
		icons.put(KillClogChatEmoji.GILDED_TRIGGER, 11);
		icons.put(KillClogChatEmoji.GREEN_TRIGGER, 12);

		assertEquals("<img=9> <img=10> <img=11> <img=12>",
			KillClogChatEmoji.rewriteText(":rune: :dragon: :gilded: :green:", icons));
	}

	@Test
	public void testResizeInlineIconPreservesAspectRatio()
	{
		BufferedImage wide = new BufferedImage(28, 14, BufferedImage.TYPE_INT_ARGB);
		BufferedImage resizedWide = KillClogChatEmoji.resizeInlineIcon(wide);
		assertEquals(28, resizedWide.getWidth());
		assertEquals(14, resizedWide.getHeight());

		BufferedImage tall = new BufferedImage(14, 28, BufferedImage.TYPE_INT_ARGB);
		BufferedImage resizedTall = KillClogChatEmoji.resizeInlineIcon(tall);
		assertEquals(7, resizedTall.getWidth());
		assertEquals(14, resizedTall.getHeight());
	}

	@Test
	public void testGreenIconKeepsAlpha()
	{
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0x80402010);

		BufferedImage green = KillClogChatEmoji.greenIcon(image);

		assertEquals(0x80, (green.getRGB(0, 0) >>> 24) & 0xFF);
		assertTrue(((green.getRGB(0, 0) >>> 8) & 0xFF) > ((green.getRGB(0, 0) >>> 16) & 0xFF));
	}
}
