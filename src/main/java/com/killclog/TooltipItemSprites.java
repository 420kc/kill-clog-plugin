package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

final class TooltipItemSprites
{
	private final BufferedImage[] sprites;
	private final String[] itemNames;

	private TooltipItemSprites(int count)
	{
		sprites = new BufferedImage[count];
		itemNames = new String[count];
	}

	static TooltipItemSprites load(List<Integer> itemIds, Map<Integer, String> itemNames,
		ItemManager itemManager, IntUnaryOperator countForItem, JComponent repaintTarget)
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
						loaded.sprites[idx] = img;
						repaintTarget.repaint();
					}));
			}
			loaded.sprites[i] = img;
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

	/**
	 * One grid of item sprites in the current font: unobtained ones dimmed,
	 * each centered in its cell, the quantity in the corner when wanted.
	 * Returns a hit box per item, named from {@code names} when given, else as the sprites were loaded.
	 * Without sprites the cells still hover.
	 */
	static List<TooltipItemHover.HitBox> paintGrid(Graphics2D g2, TooltipItemSprites sprites,
		Map<Integer, String> names, int section, List<Integer> ids, Set<Integer> obtainedIds,
		Map<Integer, Integer> counts, int startX, int startY, int cols, int size, int cellSize)
	{
		List<TooltipItemHover.HitBox> hitBoxes = new ArrayList<>(ids.size());
		int ascent = g2.getFontMetrics().getAscent();
		for (int i = 0; i < ids.size(); i++)
		{
			int x = startX + (i % cols) * cellSize;
			int y = startY + (i / cols) * cellSize;
			int itemId = ids.get(i);
			boolean obtained = obtainedIds.contains(itemId);
			int count = obtained ? counts.getOrDefault(itemId, 1) : 1;
			hitBoxes.add(new TooltipItemHover.HitBox(section, itemId,
				names != null || sprites == null ? TooltipItemLink.itemName(names, itemId) : sprites.nameAt(i),
				new Rectangle(x, y, size, size), obtained));
			BufferedImage sprite = sprites != null ? sprites.spriteAt(i) : null;
			if (sprite != null)
			{
				g2.setComposite(obtained
					? AlphaComposite.SrcOver
					: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				g2.drawImage(sprite, x + (size - sprite.getWidth()) / 2, y + (size - sprite.getHeight()) / 2, null);
				g2.setComposite(AlphaComposite.SrcOver);
			}
			if (obtained && count > 1)
			{
				String quantity = String.valueOf(count);
				g2.setColor(Color.BLACK);
				g2.drawString(quantity, x + 1, y + ascent + 1);
				g2.setColor(TitleTooltip.CLOG_YELLOW);
				g2.drawString(quantity, x, y + ascent);
			}
		}
		return hitBoxes;
	}
}
