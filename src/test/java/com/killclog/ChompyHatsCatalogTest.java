package com.killclog;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/** chompy-hats.tsv names every chompy bird hat by its kills and rank, and nothing else. */
public class ChompyHatsCatalogTest
{
	@Test
	public void everyHatReadsItsKillsAndRank()
	{
		Map<Integer, String> golden = new LinkedHashMap<>();
		golden.put(2978, "30: Ogre Bowman");
		golden.put(2979, "40: Bowman");
		golden.put(2980, "50: Ogre Yeoman");
		golden.put(2981, "70: Yeoman");
		golden.put(2982, "95: Ogre Marksman");
		golden.put(2983, "125: Marksman");
		golden.put(2984, "170: Ogre Woodsman");
		golden.put(2985, "225: Woodsman");
		golden.put(2986, "300: Ogre Forester");
		golden.put(2987, "400: Forester");
		golden.put(2988, "550: Ogre Bowmaster");
		golden.put(2989, "700: Bowmaster");
		golden.put(2990, "1000: Ogre Expert");
		golden.put(2991, "1300: Expert");
		golden.put(2992, "1700: Ogre Dragon Archer");
		golden.put(2993, "2250: Dragon Archer");
		golden.put(2994, "3000: Expert Ogre Dragon Archer");
		golden.put(2995, "4000: Expert Dragon Archer");

		Map<Integer, String> catalog = new LinkedHashMap<>();
		for (String[] row : CatalogTsv.rows(TooltipItemLink.class, "chompy-hats.tsv", 2))
		{
			catalog.put(Integer.parseInt(row[0]), row[1]);
		}
		assertEquals(golden, catalog);
		golden.forEach((id, name) -> assertEquals(name, TooltipItemLink.displayName(id, "Item")));
		assertEquals("Item", TooltipItemLink.displayName(2977, "Item"));
		assertEquals("Item", TooltipItemLink.displayName(2996, "Item"));
	}
}
