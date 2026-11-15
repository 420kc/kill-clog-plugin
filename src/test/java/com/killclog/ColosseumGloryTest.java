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
		TooltipData sol = TooltipData.builder().name("Sol Heredit").kc(17).build();
		TooltipData card = ColosseumGlory.onCard("Sol Heredit", sol, withGlory(18_420));
		// Where every card's lead line reads, synced log or not.
		assertEquals("Glory: ", card.kcLabel);
		assertEquals(18_420, card.kc);
		assertEquals("in full, as every card's count", "18,420", TitleTooltip.grouped(card.kc));
		assertSame("no Glory yet: the card keeps its kills", sol, ColosseumGlory.onCard("Sol Heredit", sol, withGlory(-1)));
		assertSame("a Glory of 0 is none", sol, ColosseumGlory.onCard("Sol Heredit", sol, withGlory(0)));
		assertSame("no hiscores row at all", sol, ColosseumGlory.onCard("Sol Heredit", sol, null));
		assertSame("every other boss leads with kills", sol, ColosseumGlory.onCard("Zulrah", sol, withGlory(18_420)));
		assertNull("no card, nothing to lead", ColosseumGlory.onCard("Sol Heredit", null, withGlory(18_420)));
	}

	@Test
	public void theCellCardsOneBuilderLeadsSolHereditWithGlory()
	{
		// 11/15 review: leaving Glory out of the one boss card builder, which both sides of a comparison take, passed
		// every test.
		TooltipData sol = TooltipData.builder().name("Sol Heredit").kc(17).build();
		TooltipData card = TooltipDataBuilder.bossCellCard(sol, net.runelite.client.hiscore.HiscoreSkill.SOL_HEREDIT,
			withGlory(18_420), name -> null, null);
		assertEquals("Glory: ", card.kcLabel);
		assertEquals(18_420, card.kc);
	}
}
