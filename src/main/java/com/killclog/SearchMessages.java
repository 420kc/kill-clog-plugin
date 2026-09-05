package com.killclog;

import java.awt.Color;

/** Search status message pools: lookup, comparison, not-found, and self flavor text. */
final class SearchMessages
{
	private SearchMessages()
	{
	}

	static final String INVALID_NAME = "That name didn't seem to take.";

	static final String[] SEARCH = pool("search");

	static final String[] NOT_FOUND = pool("not_found");

	static final String[] SELF = pool("self");

	static final String[] SELF_RARE = pool("self_rare");

	static final String[] SELF_ULTRA = pool("self_ultra");

	static final Color SELF_COLOR = new Color(0x4c, 0xaf, 0x6e);

	static final String[] COMPARE_SEARCH = pool("compare_search");

	static final String[] COMPARE_NOT_FOUND = pool("compare_not_found");

	static final String[] COMPARE_MIRROR = pool("compare_mirror");

	static final String[] COMPARE_SELF_MIRROR = pool("compare_self_mirror");

	static final String[] COMPARE_SELF_BLUE = pool("compare_self_blue");

	static final String[] COMPARE_SELF_RED = pool("compare_self_red");
	/** Bundled pool rows of this kind; the parity test pins contents. */
	private static String[] pool(String kind)
	{
		java.util.List<String> texts =
			CatalogTsv.values(SearchMessages.class, "search-messages.tsv", kind);
		if (texts.isEmpty())
		{
			// A broken catalog degrades to a plain line, never an empty pool:
			// every consumer draws by random index.
			return new String[]{"Searching for %s..."};
		}
		return texts.toArray(new String[0]);
	}
}
