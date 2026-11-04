package com.killclog;

import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * A Hardcore Main wears the game's own chat badge, the skull on a tombstone (mod icon 56, 11/4), at the game's size;
 * RuneLite's empty tombstone stands in only before the client has its icons.
 */
public class HardcoreMainBadgeTest
{
	private static final AccountDisplay HARDCORE_MAIN = AccountDisplay.of(AccountType.REGULAR, HiscoreTable.HARDCORE_MAIN);

	private static void icons(int count, IndexedSprite at56)
	{
		IndexedSprite[] icons = new IndexedSprite[count];
		if (count > 56)
		{
			icons[56] = at56;
		}
		Client client = mock(Client.class);
		when(client.getModIcons()).thenReturn(icons);
		GimBadgeLoader.load(client);
	}

	private static IndexedSprite sprite()
	{
		IndexedSprite sprite = mock(IndexedSprite.class);
		when(sprite.getWidth()).thenReturn(13);
		when(sprite.getHeight()).thenReturn(13);
		when(sprite.getPixels()).thenReturn(new byte[13 * 13]);
		when(sprite.getPalette()).thenReturn(new int[]{0});
		return sprite;
	}

	@After
	public void forget()
	{
		icons(0, null);
	}

	@Test
	public void theGameBadgeOnceTheClientHasIt()
	{
		icons(60, sprite());
		BufferedImage badge = AccountBadgeResolver.badge(HARDCORE_MAIN);
		assertSame(GimBadgeLoader.getHardcoreMainBadge(), badge);
		assertEquals("the game's size, not brought down to 15", 13, AccountBadgeResolver.labelIcon(HARDCORE_MAIN).getIconWidth());
	}

	@Test
	public void runeLitesTombstoneBefore()
	{
		icons(0, null);
		assertNull(GimBadgeLoader.getHardcoreMainBadge());
		assertNotNull(AccountBadgeResolver.badge(HARDCORE_MAIN));
		assertEquals(15, AccountBadgeResolver.labelIcon(HARDCORE_MAIN).getIconWidth());
	}
}
