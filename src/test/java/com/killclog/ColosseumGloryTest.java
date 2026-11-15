package com.killclog;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Sol Heredit's card leads with Colosseum Glory on both sides of a comparison, synced log or not, as the game's
 * own hiscores show it: nothing on them should send a player back there (Dylan, 2026-10-12).
 */
public class ColosseumGloryTest
{
	private static HiscoreResult withGlory(int glory)
	{
		return TestViews.hiscore(AccountType.REGULAR, Collections.emptyMap(), Collections.emptyMap(),
			Collections.singletonMap(ColosseumGlory.ACTIVITY_NAME, glory), Collections.emptyMap(),
			Collections.emptyMap(), 0, 0, 0, -1);
	}

	@Test
	public void solHereditLeadsWithGloryOnceThePlayerHasIt()
	{
		TooltipData sol = TooltipData.builder().name("Sol Heredit").kc(17).statLabel("Kills: ").statValue(17).build();
		TooltipData card = ColosseumGlory.onCard("Sol Heredit", sol, withGlory(18_420));
		// Where a synced card's lead line reads, and where a card with no log reads.
		assertEquals("Glory: ", card.kcLabel);
		assertEquals(18_420, card.kc);
		assertEquals("Glory: ", card.statLabel);
		assertEquals(18_420, card.statValue);
		assertEquals("in full, as every card's count", "18,420", TitleTooltip.grouped(card.kc));
		assertSame("no Glory yet: the card keeps its kills", sol, ColosseumGlory.onCard("Sol Heredit", sol, withGlory(-1)));
		assertSame("a Glory of 0 is none", sol, ColosseumGlory.onCard("Sol Heredit", sol, withGlory(0)));
		assertSame("no hiscores row at all", sol, ColosseumGlory.onCard("Sol Heredit", sol, null));
		assertSame("every other boss leads with kills", sol, ColosseumGlory.onCard("Zulrah", sol, withGlory(18_420)));
		assertNull("no card, nothing to lead", ColosseumGlory.onCard("Sol Heredit", null, withGlory(18_420)));
	}
}
