package com.killclog;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercise the real panel's pre-lookup reset; no network or game client is started. */
public class PanelCellResetCharacterizationTest
{
	private static final String DASH = ClogHelper.pad("--");

	private final KillClogConfig config = new KillClogConfig()
	{
	};
	private KillClogPanel panel;
	private Cells cells;
	private ProgressHighlighter highlighter;
	private SearchRowController searchRow;
	private JLabel compareLabel;

	@Before
	public void createPanel() throws Exception
	{
		ClogService catalog = mock(ClogService.class);
		// Keep catalog completion out of reset tests.
		when(catalog.warmCatalog()).thenReturn(new CompletableFuture<>());
		SpriteManager sprites = mock(SpriteManager.class);
		doAnswer(invocation ->
		{
			Consumer<BufferedImage> callback = invocation.getArgument(2);
			callback.accept(new BufferedImage(25, 25, BufferedImage.TYPE_INT_ARGB));
			return null;
		}).when(sprites).getSpriteAsync(anyInt(), anyInt(), ArgumentMatchers.<Consumer<BufferedImage>>any());
		edt(() ->
		{
			panel = new KillClogPanel(mock(HiscoreService.class), catalog,
				mock(RuneProfileService.class), mock(KillclogService.class),
				config, mock(ConfigManager.class), sprites,
				mock(ItemManager.class), mock(ClientThread.class), new SkillIconManager(), mock(Client.class));
			cells = field("cells", Cells.class);
			highlighter = field("highlighter", ProgressHighlighter.class);
			searchRow = field("searchRowController", SearchRowController.class);
			compareLabel = field("compareLabel", JLabel.class);
		});
		drain();
	}

	@After
	public void stopPanel() throws Exception
	{
		if (panel != null)
		{
			edt(panel::shutdown);
			drain();
		}
	}

	@Test
	public void lookupStartReturnsEveryCellAndTheHeaderToRest() throws Exception
	{
		edt(() ->
		{
			seedRenderedLookup();
			JLabel zulrah = cells.getBossLabel(HiscoreSkill.ZULRAH);
			JLabel vorkath = cells.getBossLabel(HiscoreSkill.VORKATH);
			assertEquals(ClogHelper.pad("5"), zulrah.getText());
			assertEquals(Cells.KC_COLOR, zulrah.getForeground());
			assertSame(cells.getOriginalIcons().get(HiscoreSkill.ZULRAH), zulrah.getIcon());
			assertSame(cells.getDimmedIcons().get(HiscoreSkill.VORKATH), vorkath.getIcon());
			assertNotEquals(ColorScheme.LIGHT_GRAY_COLOR, config.emptyClogColor());
			assertEquals(config.emptyClogColor(), vorkath.getForeground());
			assertEquals(ClogHelper.pad("3"), cells.getClueTierLabels().get(HiscoreSkill.CLUE_SCROLL_HARD).getText());
			assertEquals(ClogHelper.pad("2"), cells.getPvpSummaryCell().getText());
			assertEquals(ClogHelper.pad("1"), cells.getThirdAgeCell().getText());
			assertEquals(" ", cells.getThirdAgeCell().getToolTipText());
			assertFalse(cells.getTooltipDataMap().isEmpty());
			assertEquals(5, cells.getRareTooltips().size());
			assertTrue(compareLabel.isVisible());

			panel.onLookupStart("Next", false, false);

			for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getBossLabels().entrySet())
			{
				assertResting(entry.getValue(), " ");
				ImageIcon original = cells.getOriginalIcons().get(entry.getKey());
				assertNotNull(original);
				assertSame(original, entry.getValue().getIcon());
			}
			for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getActivityLabels().entrySet())
			{
				assertResting(entry.getValue(), entry.getKey().getName());
			}
			for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getClueTierLabels().entrySet())
			{
				assertResting(entry.getValue(), entry.getKey().getName());
			}
			assertResting(cells.getPvpSummaryCell(), "PvP Summary");
			assertResting(cells.getThirdAgeCell(), "3rd Age");
			assertResting(cells.getGildedCell(), "Gilded");
			assertResting(cells.getHardRare(), "Hard Treasure (Rare)");
			assertResting(cells.getEliteRare(), "Elite Treasure (Rare)");
			assertResting(cells.getMasterRare(), "Master Treasure (Rare)");
			assertTrue(cells.getTooltipDataMap().isEmpty());
			assertTrue(cells.getRareTooltips().isEmpty());

			assertEquals(" ", panel.playerName().getText());
			assertNull(panel.playerName().getIcon());
			assertNull(panel.playerName().getToolTipText());
			assertEquals("", panel.clogInfoLabel().getText());
			assertNull(panel.clogInfoLabel().getIcon());
			assertNull(panel.clogInfoLabel().getToolTipText());
			assertEquals(DASH, panel.combatCell().getText());
			assertEquals(ColorScheme.LIGHT_GRAY_COLOR, panel.combatCell().getForeground());
			assertEquals(DASH, panel.totalLvlCell().getText());
			assertEquals(ColorScheme.LIGHT_GRAY_COLOR, panel.totalLvlCell().getForeground());
			assertNull(panel.totalLvlCell().getToolTipText());
			assertFalse(compareLabel.isVisible());
		});
	}

	@Test
	public void lookupFailureResetsCellsTheSameWay() throws Exception
	{
		edt(() ->
		{
			seedRenderedLookup();
			panel.onError("Next", new RuntimeException("offline"));
			JLabel zulrah = cells.getBossLabel(HiscoreSkill.ZULRAH);
			assertResting(zulrah, " ");
			assertSame(cells.getOriginalIcons().get(HiscoreSkill.VORKATH), cells.getBossLabel(HiscoreSkill.VORKATH).getIcon());
			// Rare cells are rebuilt as catalog previews right after this reset; see rebuildPrimaryTooltips.
			assertEquals(" ", panel.playerName().getText());
			assertEquals("Lookup failed", field("statusRow", PanelStatusRow.class).statusText());
		});
	}

	/** Render a small result set and colour it the way a finished lookup would. */
	private void seedRenderedLookup()
	{
		Map<String, Integer> kills = new HashMap<>();
		kills.put(HiscoreSkill.ZULRAH.getName(), 5);
		Map<String, Integer> activities = new HashMap<>();
		activities.put(HiscoreSkill.CLUE_SCROLL_HARD.getName(), 3);
		activities.put("Bounty Hunter - Hunter", 2);
		HiscoreResult hiscore = new HiscoreResult(AccountType.REGULAR, kills, Collections.emptyMap(),
			activities, Collections.emptyMap(), Collections.emptyMap(), 0, 0L, 0, 0);
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("hard_treasure_trails", Collections.singletonList(
			new ClogResult.ClogItem(PanelData.THIRD_AGE_ITEM_ID, 1, null)));
		ClogResult clog = new ClogResult("Seeded", obtained, Collections.emptyMap(), new HashMap<>(), null, null);

		cells.renderHiscore(hiscore, FourTwentyMode.OFF);
		cells.renderClog(clog, config);
		cells.getTooltipDataMap().put(HiscoreSkill.ZULRAH,
			new TooltipDataBuilder(null).buildClueRareData("3rd Age", PanelData.CLOG_THIRD_AGE, clog));
		highlighter.colorEmptyCells();
		searchRow.setCompareVisible(true);
		panel.playerName().setText("Seeded");
		panel.playerName().setIcon(new ImageIcon(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)));
		panel.playerName().setToolTipText("Seeded");
		panel.clogInfoLabel().setText("1234");
		panel.clogInfoLabel().setIcon(new ImageIcon(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)));
		panel.combatCell().setText(ClogHelper.pad("126"));
		panel.combatCell().setForeground(Cells.KC_COLOR);
		panel.totalLvlCell().setText(ClogHelper.pad("2277"));
		panel.totalLvlCell().setForeground(Cells.KC_COLOR);
		panel.totalLvlCell().setToolTipText(" ");
	}

	private static void assertResting(JLabel label, String tooltip)
	{
		assertNotNull(label);
		assertEquals(DASH, label.getText());
		assertEquals(ColorScheme.LIGHT_GRAY_COLOR, label.getForeground());
		assertEquals(tooltip, label.getToolTipText());
	}

	private <T> T field(String name, Class<T> type)
	{
		try
		{
			Field field = KillClogPanel.class.getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(panel));
		}
		catch (ReflectiveOperationException exception)
		{
			throw new AssertionError(exception);
		}
	}

	private static void drain() throws Exception
	{
		edt(() ->
		{
		});
	}

	private static void edt(Runnable action) throws Exception
	{
		SwingUtilities.invokeAndWait(action);
	}
}
