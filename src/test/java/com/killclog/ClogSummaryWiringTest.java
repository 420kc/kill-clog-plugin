package com.killclog;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The real panel hands the Clog Summary its tabs and its tier sprite. */
public class ClogSummaryWiringTest
{
	private final ItemManager items = mock(ItemManager.class);
	private KillClogPanel panel;

	@Before
	public void createPanel() throws Exception
	{
		ClogService catalog = mock(ClogService.class);
		when(catalog.warmCatalog()).thenReturn(new CompletableFuture<>());
		SpriteManager sprites = mock(SpriteManager.class);
		doAnswer(invocation ->
		{
			Consumer<BufferedImage> callback = invocation.getArgument(2);
			callback.accept(new BufferedImage(25, 25, BufferedImage.TYPE_INT_ARGB));
			return null;
		}).when(sprites).getSpriteAsync(anyInt(), anyInt(), ArgumentMatchers.<Consumer<BufferedImage>>any());
		SwingUtilities.invokeAndWait(() -> panel = new KillClogPanel(mock(HiscoreService.class), catalog,
			mock(RuneProfileService.class), mock(KillclogService.class),
			new KillClogConfig()
			{
			}, mock(ConfigManager.class), sprites,
			items, mock(ClientThread.class), new SkillIconManager(), mock(Client.class)));
	}

	@After
	public void stopPanel() throws Exception
	{
		SwingUtilities.invokeAndWait(panel::shutdown);
	}

	@Test
	public void aSyncedPlayersCardCarriesItsTabsAndAsksForItsTierSprite() throws Exception
	{
		// 120 slots across two tabs; the player has 110 of them.
		List<Integer> bosses = ids(1000, 80);
		List<Integer> other = ids(2000, 40);
		Map<String, List<Integer>> categories = new LinkedHashMap<>();
		categories.put("zulrah", bosses);
		categories.put("slayer", other);
		Map<String, List<String>> tabs = new LinkedHashMap<>();
		tabs.put("Bosses", Collections.singletonList("zulrah"));
		tabs.put("Other", Collections.singletonList("slayer"));
		ClogIndex index = new ClogIndex();
		index.publishForTest(categories, Collections.emptyMap(), tabs, Collections.emptyMap());

		Map<String, List<ClogResult.ClogItem>> obtained = new LinkedHashMap<>();
		obtained.put("zulrah", owned(bosses.subList(0, 75)));
		obtained.put("slayer", owned(other.subList(0, 35)));
		ClogResult clog = new ClogResult("Probe", obtained, categories, Collections.emptyMap(), null, null);

		Method build = KillClogPanel.class.getDeclaredMethod("buildClogSummaryTooltip",
			JComponent.class, HiscoreResult.class, ClogResult.class, String.class, String.class);
		build.setAccessible(true);
		ClogSummaryTooltip[] tip = new ClogSummaryTooltip[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				panel.setClogIndex(index);
				tip[0] = (ClogSummaryTooltip) build.invoke(panel, new JLabel(), null, clog, "Probe", null);
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});

		Field field = ClogSummaryTooltip.class.getDeclaredField("tabs");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, int[]> shown = (Map<String, int[]>) field.get(tip[0]);
		assertEquals(Arrays.asList("Bosses", "Other"), new ArrayList<>(shown.keySet()));
		assertArrayEquals(new int[]{75, 80}, shown.get("Bosses"));
		assertArrayEquals(new int[]{35, 40}, shown.get("Other"));
		assertTrue(tip[0].completionText().startsWith("91."));
		// Which tier that is belongs to the tooltip's own tests; here it only has to be asked for.
		verify(items).getImage(PanelData.CLOG_TIER_ITEM_IDS[ClogHelper.tierIndex(110, 120)]);
	}

	private static List<Integer> ids(int from, int count)
	{
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			ids.add(from + i);
		}
		return ids;
	}

	private static List<ClogResult.ClogItem> owned(List<Integer> ids)
	{
		List<ClogResult.ClogItem> items = new ArrayList<>();
		for (int id : ids)
		{
			items.add(new ClogResult.ClogItem(id, 1, null));
		}
		return items;
	}
}
