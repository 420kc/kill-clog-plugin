package com.killclog;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.client.hiscore.HiscoreSkill;

/** A Clue Summary rare row, one line of clue-rare-rows.tsv: the collection it counts, its card, page and icon. */
@Value
class RareRow
{
	String key;
	String name;
	String label;
	/** The clue tier whose completed clues its card reads; null for the clue-wide sets. */
	@Nullable
	HiscoreSkill tier;
	String wikiPage;
	/** The game's Collection Log page the row stands for; null where none does. */
	@Nullable
	String page;
	int[] items;
	int iconItemId;
}
