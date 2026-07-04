package com.killclog;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

final class PanelIconCache
{
	private static final int SYNC_ICON_SIZE = 12;

	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final SpriteManager spriteManager;
	private final Map<String, ImageIcon> clogTierIcons = new LinkedHashMap<>();

	private BufferedImage maxCapeTip;
	private BufferedImage infernalCapeTip;
	private BufferedImage infernalMaxCapeTip;
	private BufferedImage riftsClosedIcon;
	private BufferedImage syncNoticeIcon;

	PanelIconCache(ItemManager itemManager, ClientThread clientThread, SpriteManager spriteManager)
	{
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.spriteManager = spriteManager;
	}

	BufferedImage syncNoticeIcon()
	{
		if (syncNoticeIcon == null)
		{
			syncNoticeIcon = KillClogIcons.resizedPluginIcon(
				SYNC_ICON_SIZE, SYNC_ICON_SIZE, itemManager);
		}
		return syncNoticeIcon;
	}

	ImageIcon clogTierIcon(@Nullable String tierName)
	{
		return tierName != null ? clogTierIcons.get(tierName) : null;
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

	BufferedImage riftsClosedIcon()
	{
		return riftsClosedIcon;
	}

	BufferedImage capeFor(@Nullable HiscoreResult result)
	{
		if (result == null)
		{
			return null;
		}
		boolean maxed = result.getTotalLevel() >= PanelData.MAX_TOTAL_LEVEL;
		boolean infernal = result.getKc("TzKal-Zuk") > 0;
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
					cells.getClueIcons()[idx] = ImageUtil.resizeImage(
						ImageUtil.resizeCanvas(img, 25, 25), 13, 13));
			}
			loadItemImage(23184, img ->
				cells.getClueIcons()[7] = ImageUtil.resizeImage(
					ImageUtil.resizeCanvas(img, 25, 25), 13, 13));
		});

		loadClueAllIcon(cells);
		loadRiftsIcon();
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
						cells.getClueIcons()[0] = ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
					}
				}));
		}
	}

	private void loadRiftsIcon()
	{
		int riftsSpriteId = HiscoreSkill.RIFTS_CLOSED.getSpriteId();
		if (riftsSpriteId != -1)
		{
			spriteManager.getSpriteAsync(riftsSpriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						riftsClosedIcon = ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
					}
				}));
		}
	}

	private void loadItemIcon(int itemId, int w, int h, Consumer<ImageIcon> setter)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() ->
					setter.accept(new ImageIcon(ImageUtil.resizeImage(img, w, h)))));
		}
		else
		{
			SwingUtilities.invokeLater(() ->
				setter.accept(new ImageIcon(ImageUtil.resizeImage(img, w, h))));
		}
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
}
