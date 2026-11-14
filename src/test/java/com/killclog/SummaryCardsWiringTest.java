package com.killclog;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The real panel hands the Player Summary the log's sites and the Combat Summary its cape and Dizana's gear (Dylan
 * 11/14): which gear shows is the rule in LookupQueries, which of it the card is given is the panel's, held here.
 */
public class SummaryCardsWiringTest
{
	private static final String PLAYER = "Seeded Name";
	private final ClogService clogs = mock(ClogService.class);
	private final RuneProfileService runeProfile = mock(RuneProfileService.class);
	private final PlayerPortraits portraits = mock(PlayerPortraits.class);
	private KillClogPanel panel;

	@Before
	public void createPanel() throws Exception
	{
		when(clogs.warmCatalog()).thenReturn(new CompletableFuture<>());
		SpriteManager sprites = mock(SpriteManager.class);
		doAnswer(invocation ->
		{
			Consumer<BufferedImage> callback = invocation.getArgument(2);
			callback.accept(new BufferedImage(25, 25, BufferedImage.TYPE_INT_ARGB));
			return null;
		}).when(sprites).getSpriteAsync(anyInt(), anyInt(), ArgumentMatchers.<Consumer<BufferedImage>>any());
		ClientThread clientThread = mock(ClientThread.class);
		ItemManager items = mock(ItemManager.class);
		when(items.getImage(anyInt(), anyInt(), anyBoolean())).thenAnswer(invocation ->
			new AsyncBufferedImage(clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB));
		when(items.getImage(anyInt())).thenAnswer(invocation ->
			new AsyncBufferedImage(clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB));
		SwingUtilities.invokeAndWait(() ->
		{
			panel = new KillClogPanel(mock(HiscoreService.class), clogs, runeProfile, mock(KillclogService.class),
				new KillClogConfig()
				{
				}, mock(ConfigManager.class), sprites, items, clientThread, new SkillIconManager(), portraits);
			// The capes' sprites, as the client thread leaves them once loaded.
			PanelIconCache icons = TestViews.field(panel, "iconCache");
			TestViews.set(icons, "maxCapeTip", new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB));
			TestViews.set(icons, "infernalCapeTip", new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB));
			TestViews.set(icons, "infernalMaxCapeTip", new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB));
		});
	}

	@After
	public void stopPanel() throws Exception
	{
		SwingUtilities.invokeAndWait(panel::shutdown);
	}

	@Test
	public void theCombatSummaryCarriesTheCapeAndDizanasGearByTheRule() throws Exception
	{
		// maxed, an Inferno, a Fortis Colosseum completion, the quiver in the log: what the level row carries.
		assertEquals(List.of(), gear(false, false, 0, false));
		assertEquals(List.of("Dizana's quiver"), gear(false, false, 1, false));
		assertEquals(List.of("Dizana's quiver"), gear(false, false, 0, true));
		assertEquals(List.of("Infernal", "Dizana's quiver"), gear(false, true, 1, false));
		assertEquals(List.of("Maxed"), gear(true, false, 0, false));
		// Maxed, the quiver is Dizana's max cape in the plain max cape's place, beside an infernal max cape.
		assertEquals(List.of("Dizana's max cape"), gear(true, false, 1, false));
		assertEquals(List.of("Dizana's max cape"), gear(true, false, 0, true));
		assertEquals(List.of("Maxed Infernal", "Dizana's max cape"), gear(true, true, 1, true));
	}

	@Test
	public void aCompletionCountsWhenTheLogHoldsNoQuiver() throws Exception
	{
		// A synced log without the quiver (bought back from Minimus later, or a page the sync missed) and a completion.
		assertEquals(List.of("Dizana's quiver"), combatGear(hiscore(false, false, 1), pets(PLAYER)));
	}

	@Test
	public void thePlayerSummaryLinksTheLogsSitesAndLeavesPrestigeToACharacterlessCard() throws Exception
	{
		when(clogs.onTemple(PLAYER)).thenReturn(true);
		when(runeProfile.hasProfile(PLAYER)).thenReturn(true);
		HiscoreResult hiscore = hiscore(true, true, 0);
		ClogResult clog = pets(PLAYER).withSources(false, false, true);

		SummaryTooltip cape = player(hiscore, clog);
		assertEquals(List.of("View on Kill Clog", "View on TempleOSRS", "View on RuneProfile"), sites(cape));
		assertEquals("Maxed Infernal", TestViews.field(cape, "prestige"));

		// With a published character the cape and its name live on the Combat Summary instead.
		BufferedImage character = new BufferedImage(60, 120, BufferedImage.TYPE_INT_ARGB);
		character.setRGB(30, 0, 0xff302020);
		when(portraits.forSummary(any())).thenReturn(character);
		SummaryTooltip withCharacter = player(hiscore, clog);
		assertNull(TestViews.field(withCharacter, "prestige"));
		assertEquals(sites(cape), sites(withCharacter));

		// A lookup with no log has no sites to link.
		when(portraits.forSummary(any())).thenReturn(null);
		assertEquals(List.of(), sites(player(hiscore, null)));
	}

	private List<String> gear(boolean maxed, boolean infernal, int completions, boolean logQuiver) throws Exception
	{
		return combatGear(hiscore(maxed, infernal, completions), logQuiver ? quiver(PLAYER) : null);
	}

	private List<String> combatGear(HiscoreResult hiscore, ClogResult clog) throws Exception
	{
		AtomicReference<List<String>> names = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			seed(hiscore, clog);
			PvmSummaryTooltip card = (PvmSummaryTooltip) panel.combatCell().createToolTip();
			names.set(TestViews.field(card, "gearNames"));
		});
		return names.get();
	}

	private SummaryTooltip player(HiscoreResult hiscore, ClogResult clog) throws Exception
	{
		SummaryTooltip[] card = new SummaryTooltip[1];
		SwingUtilities.invokeAndWait(() ->
		{
			seed(hiscore, clog);
			JLabel name = TestViews.field(panel, "playerName");
			card[0] = (SummaryTooltip) name.createToolTip();
		});
		return card[0];
	}

	private void seed(HiscoreResult hiscore, ClogResult clog)
	{
		LookupSession session = TestViews.field(panel, "lookupSession");
		session.adoptState(hiscore, clog, null, PLAYER);
		Cells cells = TestViews.field(panel, "cells");
		cells.renderHiscore(hiscore);
		cells.renderClog(clog);
		cells.rebuildPrimaryTooltips();
		JLabel name = TestViews.field(panel, "playerName");
		name.setText(PLAYER);
	}

	private static List<String> sites(SummaryTooltip card)
	{
		List<CardSources.Source> sources = TestViews.field(card, "sources");
		return sources.stream().map(source -> source.name).collect(Collectors.toList());
	}

	private static HiscoreResult hiscore(boolean maxed, boolean infernal, int completions)
	{
		Map<String, Integer> kills = new HashMap<>();
		kills.put("TzKal-Zuk", infernal ? 3 : 0);
		kills.put(ColosseumGlory.BOSS_NAME, completions);
		Map<String, Long> xps = new LinkedHashMap<>();
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			xps.put(skill.getName().toLowerCase(), 14_000_000L);
			levels.put(skill.getName().toLowerCase(), 99);
		}
		return TestViews.hiscore(AccountType.REGULAR, HiscoreTable.STANDARD, kills, Map.of(), Map.of(), Map.of(),
			levels, Map.of(), xps, maxed ? PanelData.MAX_TOTAL_LEVEL : 2000, 1000L, 126.1, 886);
	}

	private static ClogResult quiver(String player)
	{
		return log(player, "fortis_colosseum", PanelData.DIZANAS_QUIVER_ITEM_ID);
	}

	private static ClogResult pets(String player)
	{
		return log(player, "all_pets", 13247);
	}

	private static ClogResult log(String player, String page, int held)
	{
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put(page, Collections.singletonList(new ClogResult.ClogItem(held, 1, null)));
		Map<String, List<Integer>> pages = new HashMap<>();
		pages.put(page, Arrays.asList(held, 12921));
		return new ClogResult(player, obtained, pages, new HashMap<>(), null, null);
	}
}
