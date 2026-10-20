package com.killclog;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PanelIconCache
{
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final SpriteManager spriteManager;
	private final Map<String, ImageIcon> clogTierIcons = new LinkedHashMap<>();

	private BufferedImage maxCapeTip;
	private BufferedImage infernalCapeTip;
	private BufferedImage infernalMaxCapeTip;

	/** The log tier's icon for obtained and total slots, or none before the total is known. */
	ImageIcon clogTierIcon(int[] totals)
	{
		return totals[1] > 0 ? clogTierIcons.get(ClogHelper.getClogTierName(totals[0], totals[1])) : null;
	}

	Map<String, BufferedImage> clogTierImages()
	{
		Map<String, BufferedImage> icons = new LinkedHashMap<>();
		for (Map.Entry<String, ImageIcon> entry : clogTierIcons.entrySet())
		{
			icons.put(entry.getKey(), ClogHelper.iconToImage(entry.getValue()));
		}
		return icons;
	}

	BufferedImage capeFor(@Nullable HiscoreResult result)
	{
		if (result == null)
		{
			return null;
		}
		boolean maxed = LookupQueries.maxed(result);
		boolean infernal = LookupQueries.infernal(result);
		if (maxed && infernal)
		{
			return infernalMaxCapeTip;
		}
		if (maxed)
		{
			return maxCapeTip;
		}
		if (infernal)
		{
			return infernalCapeTip;
		}
		return null;
	}

	void loadRuntimeIcons(Cells cells, CaRewardSprites caRewardSprites)
	{
		// Cape, CA reward, and clog tier icons via ItemManager.
		clientThread.invokeLater(() ->
		{
			// Tooltip cape icons - native aspect ratio, taller.
			loadItemImage(PanelData.MAX_CAPE_ITEM_ID, img -> maxCapeTip = img);
			loadItemImage(PanelData.INFERNAL_CAPE_ITEM_ID, img -> infernalCapeTip = img);
			loadItemImage(PanelData.INFERNAL_MAX_CAPE_ITEM_ID, img -> infernalMaxCapeTip = img);

			caRewardSprites.preloadAll();

			for (int i = 0; i < ClogHelper.CLOG_TIERS.length; i++)
			{
				final String tier = ClogHelper.CLOG_TIERS[i];
				final int itemId = PanelData.CLOG_TIER_ITEM_IDS[i];
				loadItemIcon(itemId, 13, 13, icon ->
					clogTierIcons.put(tier, icon));
			}

			// Clue summary tooltip icons: 1-6=tier scrolls, 7=Mimic (0=All loaded via spriteManager below).
			for (int i = 0; i < PanelData.CLUE_TIER_ITEM_IDS.length; i++)
			{
				final int idx = i + 1;
				loadItemImage(PanelData.CLUE_TIER_ITEM_IDS[i], img ->
					cells.getClueIcons()[idx] = cellIcon(img, 13));
			}
			loadItemImage(23184, img ->
				cells.getClueIcons()[7] = cellIcon(img, 13));
			for (int i = 0; i < PanelData.RARE_ICON_ITEM_IDS.length; i++)
			{
				final int idx = i;
				loadItemImage(PanelData.RARE_ICON_ITEM_IDS[i], img ->
					cells.getRareIcons()[idx] = cellIcon(img, 13));
			}
		});

		loadClueAllIcon(cells);
	}

	private void loadClueAllIcon(Cells cells)
	{
		// Clue All icon via spriteManager (game sprite, not item).
		int allSpriteId = HiscoreSkill.CLUE_SCROLL_ALL.getSpriteId();
		if (allSpriteId != -1)
		{
			spriteManager.getSpriteAsync(allSpriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						cells.getClueIcons()[0] = cellIcon(sprite, 13);
					}
				}));
		}
	}

	/** An item image resized into an icon, delivered on the Swing thread once it has loaded. */
	private void loadItemIcon(int itemId, int w, int h, Consumer<ImageIcon> setter)
	{
		loadItemImage(itemId, img -> setter.accept(new ImageIcon(ImageUtil.resizeImage(img, w, h))));
	}

	private void loadItemImage(int itemId, Consumer<BufferedImage> setter)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() -> setter.accept(img)));
		}
		else
		{
			SwingUtilities.invokeLater(() -> setter.accept(img));
		}
	}

	/** A game icon centred on the 25 px cell canvas, then scaled to the size shown. */
	static BufferedImage cellIcon(BufferedImage icon, int size)
	{
		return ImageUtil.resizeImage(ImageUtil.resizeCanvas(icon, 25, 25), size, size);
	}
}
