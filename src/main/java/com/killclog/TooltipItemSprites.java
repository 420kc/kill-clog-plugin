package com.killclog;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

final class TooltipItemSprites
{
	private static final int DEFAULT_SPRITE_SIZE = 32;

	private final BufferedImage[] sprites;
	private final String[] itemNames;

	private TooltipItemSprites(int count)
	{
		sprites = new BufferedImage[count];
		itemNames = new String[count];
	}

	static TooltipItemSprites load(List<Integer> itemIds, Map<Integer, String> itemNames,
		ItemManager itemManager, int spriteSize, IntUnaryOperator countForItem, JComponent repaintTarget)
	{
		TooltipItemSprites loaded = new TooltipItemSprites(itemIds.size());
		for (int i = 0; i < itemIds.size(); i++)
		{
			int itemId = itemIds.get(i);
			loaded.itemNames[i] = TooltipItemLink.itemName(itemNames, itemId);
			BufferedImage img = itemManager.getImage(itemId, Math.max(1, countForItem.applyAsInt(itemId)), false);
			final int idx = i;
			if (img instanceof AsyncBufferedImage)
			{
				((AsyncBufferedImage) img).onLoaded(() ->
					SwingUtilities.invokeLater(() ->
					{
						loaded.sprites[idx] = resizeSprite(img, spriteSize);
						repaintTarget.repaint();
					}));
			}
			loaded.sprites[i] = resizeSprite(img, spriteSize);
		}
		return loaded;
	}

	BufferedImage spriteAt(int index)
	{
		if (index < 0 || index >= sprites.length)
		{
			return null;
		}
		return sprites[index];
	}

	String nameAt(int index)
	{
		if (index < 0 || index >= itemNames.length)
		{
			return null;
		}
		return itemNames[index];
	}

	private static BufferedImage resizeSprite(BufferedImage img, int spriteSize)
	{
		if (img == null || spriteSize >= DEFAULT_SPRITE_SIZE)
		{
			return img;
		}
		return ImageUtil.resizeImage(
			ImageUtil.resizeCanvas(img, DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE),
			spriteSize, spriteSize);
	}
}
