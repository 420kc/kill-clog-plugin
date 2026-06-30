package com.killclog;

import java.awt.image.BufferedImage;
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
}
