package com.killclog;

import com.google.gson.Gson;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class VisibleClogCategoryReaderTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void headerDeficitNeverSelectsAnUnobservedItem() throws Exception
	{
		VisibleClogCategory page = read("Obtained: 1/2", item(1, 128, 1), item(2, 128, 1));
		assertEquals(List.of(1, 2), page.allItemIds());
		assertTrue(page.obtained().isEmpty());
		LocalClogCache cache = cache();
		cache.cacheFirstPartyResult(result(List.of()));
		cache.mergeCategory("Tester", page.key(), page.allItemIds(), page.obtained());
		assertFalse(cache.hasFirstPartyDataFor("Tester"));
		assertTrue(cache.toFirstPartySyncResult("Tester").getObtainedItems().isEmpty());
	}

	@Test
	public void recapturePreservesHiddenClientEvidenceWithoutPromotingProviderItems() throws Exception
	{
		LocalClogCache cache = cache();
		cache.cacheResult(result(List.of(new ClogResult.ClogItem(3, 9, null))));
		ClogResult.ClogItem previous = new ClogResult.ClogItem(1, 4, "2026-09-01", 70, "Zulrah");
		cache.mergeCategory("Tester", "zulrah", List.of(1, 2, 3), List.of(previous));
		cache.cacheResult(result(List.of(new ClogResult.ClogItem(3, 9, null))));
		VisibleClogCategory page = read("Obtained: 2/3", null,
			item(1, 128, 0), item(2, 0, 2), item(3, 128, 0));
		assertEquals(2, page.obtained().get(0).getId());
		cache.mergeCategory("Tester", page.key(), page.allItemIds(), page.obtained());
		List<ClogResult.ClogItem> sent = cache.toFirstPartySyncResult("Tester")
			.getObtainedItems().get("zulrah");
		assertEquals(2, sent.size());
		assertTrue(sent.stream().noneMatch(entry -> entry.getId() == 3));
		ClogResult.ClogItem retained = sent.stream().filter(entry -> entry.getId() == 1).findFirst().get();
		assertEquals(4, retained.getCount());
		assertEquals("2026-09-01", retained.getDate());
		assertEquals(70, retained.getObtainedAtKc());
		assertEquals("Zulrah", retained.getObtainedFrom());
		cache.mergeCategory("Tester", page.key(), page.allItemIds(), List.of());
		assertEquals(2, cache.toFirstPartySyncResult("Tester").getObtainedItems().get("zulrah").size());
	}

	private LocalClogCache cache() throws Exception
	{
		return new LocalClogCache(new Gson(), new InlineScheduledExecutorService(), temporaryFolder.newFolder());
	}

	private static ClogResult result(List<ClogResult.ClogItem> obtained)
	{
		return new ClogResult("Tester", Map.of("zulrah", obtained),
			Map.of("zulrah", List.of(1, 2, 3)), Map.of(), null, null);
	}

	private static VisibleClogCategory read(String count, Widget... items)
	{
		Widget header = widget(Map.of("getDynamicChildren", new Widget[]{
			widget(Map.of("getText", "Zulrah")), widget(Map.of("getText", count))}));
		Widget grid = widget(Map.of("getChildren", items));
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
			new Class<?>[]{Client.class}, (proxy, method, args) ->
			{
				assertEquals("getWidget", method.getName());
				return (int) args[1] == 20 ? header : grid;
			});
		return new VisibleClogCategoryReader().read(client).get();
	}

	private static Widget item(int id, int opacity, int quantity)
	{
		return widget(Map.of("getItemId", id, "getOpacity", opacity, "getItemQuantity", quantity));
	}

	private static Widget widget(Map<String, Object> values)
	{
		return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
			new Class<?>[]{Widget.class}, (proxy, method, args) -> values.get(method.getName()));
	}
}
