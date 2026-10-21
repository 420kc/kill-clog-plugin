package com.killclog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import org.junit.Test;

public class NativeTooltipPackTest
{
	// The card's parchment, one of the sprites it wears.
	private static final int PARCHMENT = 297;

	@Test
	public void aPackAppliedLateStillRedrawsTheCards()
	{
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		Client client = mock(Client.class);
		when(client.getSpriteOverrides()).thenReturn(overrides);
		NativeTooltip.wornSpritesChanged(client);
		assertFalse("nothing replaced, nothing to redraw", NativeTooltip.wornSpritesChanged(client));

		// The pack lands some ticks after it was chosen.
		overrides.put(PARCHMENT, mock(SpritePixels.class));
		assertTrue(NativeTooltip.wornSpritesChanged(client));
		assertFalse("once per change", NativeTooltip.wornSpritesChanged(client));

		// Another pack, then none: each swap redraws.
		overrides.put(PARCHMENT, mock(SpritePixels.class));
		assertTrue(NativeTooltip.wornSpritesChanged(client));
		overrides.remove(PARCHMENT);
		assertTrue(NativeTooltip.wornSpritesChanged(client));
		assertFalse(NativeTooltip.wornSpritesChanged(client));
	}
}
