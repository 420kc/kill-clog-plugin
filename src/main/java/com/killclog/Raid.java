package com.killclog;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.client.hiscore.HiscoreSkill;

/** A raid, one line of raids.tsv: its tile, its hiscore rows and hard mode, its log page and its megarare. */
@Value
class Raid
{
	String tile;
	/** RuneLite's entry for the raid; null while this client build doesn't carry it, and its tile opens nothing. */
	@Nullable
	HiscoreSkill skill;
	String row;
	/** Its hard mode, which shares its log and card; null for a raid without one. */
	@Nullable
	HiscoreSkill hard;
	String hardLabel;
	/** Whether its tile waits for the hiscores to carry its row, as a launch day's may not. */
	boolean waits;
	String page;
	int megarare;
	String megarareName;
}
