package com.killclog;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameAutocompleterTest
{
	@Test
	public void testSearchHistoryWinsOverClientSnapshot()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.setClientNameSnapshot(Arrays.asList("rng shango", "rng stranger"));
		autocompleter.addToSearchHistory("rng history");

		assertEquals("rng history", autocompleter.findAutofillCandidate("rng"));
	}

	@Test
	public void testClientSnapshotSuppliesMatchWithoutClientThreadRead()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.setClientNameSnapshot(Arrays.asList("cbc", "420 kc"));

		assertEquals("420 kc", autocompleter.findAutofillCandidate("420"));
	}

	@Test
	public void testSeparatorCharactersMatchEachOther()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.setClientNameSnapshot(Arrays.asList("iron" + Character.toString('\u00a0') + "whiff"));

		assertEquals("iron" + Character.toString('\u00a0') + "whiff",
			autocompleter.findAutofillCandidate("iron "));
	}

	@Test
	public void testClearClientSnapshotRemovesClientMatches()
	{
		NameAutocompleter autocompleter = new NameAutocompleter(null);
		autocompleter.setClientNameSnapshot(Arrays.asList("cbc"));
		autocompleter.clearClientSnapshot();

		assertNull(autocompleter.findAutofillCandidate("cb"));
	}
}
