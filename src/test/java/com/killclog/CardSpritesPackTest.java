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

public class CardSpritesPackTest
{
	// The card's parchment, one of the sprites it wears.
	private static final int PARCHMENT = 297;

	@Test
	public void aPackAppliedLateStillRedrawsTheCards()
	{
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		Client client = mock(Client.class);
		when(client.getSpriteOverrides()).thenReturn(overrides);
		CardSprites.changed(client);
		assertFalse("nothing replaced, nothing to redraw", CardSprites.changed(client));

		// The pack lands some ticks after it was chosen.
		overrides.put(PARCHMENT, mock(SpritePixels.class));
		assertTrue(CardSprites.changed(client));
		assertFalse("once per change", CardSprites.changed(client));

		// Another pack, then none: each swap redraws.
		overrides.put(PARCHMENT, mock(SpritePixels.class));
		assertTrue(CardSprites.changed(client));
		overrides.remove(PARCHMENT);
		assertTrue(CardSprites.changed(client));
		assertFalse(CardSprites.changed(client));
	}
}
