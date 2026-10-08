package com.killclog;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/** adv-log-renames.tsv holds exactly the scroll sections that store under vanilla's other names. */
public class AdvLogRenamesCatalogTest
{
	@Test
	public void everyRenameLandsOnVanillasStoreName()
	{
		Map<String, String> golden = new LinkedHashMap<>();
		golden.put("tzhaar fight cave", "tztok-jad");
		golden.put("inferno", "tzkal-zuk");
		golden.put("fortis colosseum", "sol heredit");
		golden.put("lunar chests", "lunar chest");
		golden.put("doom", "doom of mokhaiotl");
		golden.put("chambers of xeric - challenge mode", "chambers of xeric challenge mode");
		golden.put("theatre of blood - hard mode", "theatre of blood hard mode");
		golden.put("theatre of blood - entry mode", "theatre of blood entry mode");
		golden.put("theatre of blood - story mode", "theatre of blood entry mode");
		golden.put("tombs of amascut - entry", "tombs of amascut entry mode");
		golden.put("tombs of amascut - expert", "tombs of amascut expert mode");

		Map<String, String> catalog = new LinkedHashMap<>();
		for (String[] row : CatalogTsv.rows(AdvLogPbs.class, "adv-log-renames.tsv", 2))
		{
			catalog.put(row[0], row[1]);
		}
		assertEquals(golden, catalog);
		golden.forEach((section, store) -> assertEquals(store, AdvLogPbs.canonicalSection(section)));
	}
}
