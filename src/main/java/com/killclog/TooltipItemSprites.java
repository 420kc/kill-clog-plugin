package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * A card's item pictures: loaded once per card, counted while any is still on its way (so a copy of the card waits for
 * it), and painted as a grid with what isn't held dimmed.
 */
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
			whenLoaded(img, repaintTarget, () ->
			{
				loaded.sprites[idx] = img;
				repaintTarget.repaint();
			});
			loaded.sprites[i] = img;
		}
		return loaded;
	}

	// How many of a card's pictures are still on their way, kept on the card itself.
	private static final String LOADING = "killclog.card.loading";

	/**
	 * Runs on the Swing thread once a picture the card asked for has drawn; until then the card counts it as loading,
	 * so a copy of the card waits for it. A picture already there needs nothing.
	 */
	static void whenLoaded(BufferedImage image, JComponent card, Runnable loaded)
	{
		if (!(image instanceof AsyncBufferedImage))
		{
			return;
		}
		count(card, 1);
		((AsyncBufferedImage) image).onLoaded(() -> SwingUtilities.invokeLater(() ->
		{
			count(card, -1);
			loaded.run();
		}));
	}

	/** Whether a card, or either card of a comparison, still waits on a picture. Swing thread. */
	static boolean loading(JComponent card)
	{
		Object pending = card.getClientProperty(LOADING);
		if (pending instanceof Integer && (Integer) pending > 0)
		{
			return true;
		}
		for (java.awt.Component child : card.getComponents())
		{
			if (child instanceof JComponent && loading((JComponent) child))
			{
				return true;
			}
		}
		return false;
	}

	private static void count(JComponent card, int change)
	{
		Object pending = card.getClientProperty(LOADING);
		card.putClientProperty(LOADING, (pending instanceof Integer ? (Integer) pending : 0) + change);
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
				g2.setColor(atCap(itemId, count) ? TitleTooltip.CLOG_GREEN : TitleTooltip.CLOG_YELLOW);
				g2.drawString(quantity, x, y + ascent);
			}
		}
		return hitBoxes;
	}

	/** A count the game stops at, 250 for most log items and 65,535 for the rest, reads green: there's no more to log. */
	static boolean atCap(int itemId, int count)
	{
		return count >= 65535 || count == 250 && Arrays.stream(PanelData.COUNTS_PAST_250_ITEMS).noneMatch(id -> id == itemId);
	}
}
