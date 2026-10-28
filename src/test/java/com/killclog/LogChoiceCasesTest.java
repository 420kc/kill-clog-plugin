package com.killclog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Runs cases/log-choice.tsv, the one copy of the answers for which provider's log a lookup shows and whose dates fill
 * Recent. killclog-api runs the same rows against the website's rule, so the plugin and killclog.com can't disagree.
 */
public class LogChoiceCasesTest
{
	@Test
	public void everySharedCaseHolds() throws Exception
	{
		List<String[]> rows = rows();
		assertFalse("the shared cases are present", rows.isEmpty());
		for (String[] row : rows)
		{
			String name = row[0];
			ClogResult temple = log(row[1], "temple", true, false, false);
			ClogResult rp = log(row[2], "runeprofile", false, true, false);
			ClogResult kc = log(row[3], "killclog", false, false, true);

			ClogResult shown = ClogResult.pickFullest(ClogResult.pickFullerProvider(temple, rp), kc);

			assertEquals(name + ": grid", row[4], shown == null ? "none" : shown.getPlayerName());
			List<ClogResult.ClogItem> recent = LookupQueries.getRecentItems(shown, 4);
			String recentFrom = recent.isEmpty() ? "none" : from(shown, recent.get(0));
			assertEquals(name + ": recent", row[5], recentFrom);
		}
	}

	/** A provider cell: "-" no log, "900" a log of 900 items without dates, "900@2026-10-01" with its newest date. */
	private static ClogResult log(String cell, String provider, boolean temple, boolean runeProfile, boolean killclog)
	{
		if (cell.equals("-"))
		{
			return null;
		}
		String[] parts = cell.split("@", 2);
		int items = Integer.parseInt(parts[0]);
		String newest = parts.length > 1 ? parts[1] + " 12:00:00" : null;
		List<ClogResult.ClogItem> obtained = new ArrayList<>();
		List<Integer> ids = new ArrayList<>();
		for (int i = 1; i <= items; i++)
		{
			// Every provider's items share ids, so coverage compares like for like; only the newest carries the date.
			ids.add(i);
			obtained.add(new ClogResult.ClogItem(i, 1, i == items ? newest : null));
		}
		ClogResult result = new ClogResult(provider, Collections.singletonMap("page", obtained),
			Collections.singletonMap("page", ids), new HashMap<>(), null, null);
		result.setUniqueObtained(items);
		return result.withSources(temple, runeProfile, killclog);
	}

	/** Which provider's log the first Recent item came from, by its date's owner in the test's own logs. */
	private static String from(ClogResult shown, ClogResult.ClogItem first)
	{
		ClogResult source = shown.recentItemsSource();
		assertNotNull(source);
		return source.getPlayerName();
	}

	private static List<String[]> rows() throws Exception
	{
		List<String[]> rows = new ArrayList<>();
		try (InputStream in = LogChoiceCasesTest.class.getResourceAsStream("/com/killclog/cases/log-choice.tsv"))
		{
			assertNotNull("cases/log-choice.tsv is on the test classpath", in);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] row = line.split("\t", -1);
				assertEquals("six columns: " + line, 6, row.length);
				rows.add(row);
			}
		}
		return rows;
	}
}
