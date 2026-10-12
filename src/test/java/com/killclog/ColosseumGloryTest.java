package com.killclog;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Sol Heredit's card leads with Colosseum Glory on both sides of a comparison, synced log or not, as the game's
 * own hiscores show it: nothing on them should send a player back there (Dylan, 2026-10-12).
 */
public class ColosseumGloryTest
{
	private static HiscoreResult withGlory(int glory)
	{
		return new HiscoreResult(AccountType.REGULAR, Collections.emptyMap(), Collections.emptyMap(),
			Collections.singletonMap(ColosseumGlory.ACTIVITY_NAME, glory), Collections.emptyMap(),
			Collections.emptyMap(), 0, 0, 0, -1);
	}

	@Test
	public void solHereditLeadsWithGloryOnceThePlayerHasIt()
	{
		assertTrue(ColosseumGlory.leadsCard("Sol Heredit", withGlory(18420)));
		assertFalse("no Glory yet: the card keeps its kills", ColosseumGlory.leadsCard("Sol Heredit", withGlory(-1)));
		assertFalse("no hiscores row at all", ColosseumGlory.leadsCard("Sol Heredit", null));
		assertFalse("every other boss leads with kills", ColosseumGlory.leadsCard("Zulrah", withGlory(18420)));
	}
}
