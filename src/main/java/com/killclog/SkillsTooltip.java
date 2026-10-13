package com.killclog;

import java.awt.Color;
import net.runelite.api.Experience;

/**
 * Experience &amp; Efficiency on the total level cell: total XP, overall rank and the XP left to max, then
 * TempleOSRS's EHP and EHC under its own name when Temple tracks the player (Dylan, 2026-10-13). EHB stays on the
 * Combat Summary. Each skill's own numbers live in its cell, in the grid or the activity tray.
 */
public class SkillsTooltip extends TitleTooltip
{
	static final String TITLE = "Experience & Efficiency";
	private static final long MAX_SKILL_XP = Experience.getXpForLevel(Experience.MAX_REAL_LEVEL);
	private static final Color UNRANKED_COLOR = new Color(128, 128, 128);

	private HiscoreResult result;
	private double ehp = -1;
	private double ehc = -1;
	private boolean templeRows;

	/** {@code templeRows}: show Temple's rows, "--" where this player has none, so a comparison pair lines up. */
	public void setData(HiscoreResult result, ClogResult clog, boolean templeRows)
	{
		this.result = result;
		ehp = clog != null ? clog.getTempleEhp() : -1;
		ehc = clog != null ? clog.getTempleEhc() : -1;
		this.templeRows = templeRows;
		setTitle(TITLE);
	}

	static boolean onTemple(ClogResult clog)
	{
		return clog != null && (clog.getTempleEhp() >= 0 || clog.getTempleEhc() >= 0);
	}

	@Override
	protected CardBody body()
	{
		long toMax = xpToMax(result);
		CardBody card = new CardBody()
			.add(CardBody.line("XP: ", displayedXpText(),
				result != null && result.getTotalXp() >= 0 ? Color.WHITE : UNRANKED_COLOR))
			.add(CardBody.line("Rank: ", scoreText(displayedRank()), displayedRank() > 0 ? Color.WHITE : UNRANKED_COLOR))
			.add(CardBody.line("To max: ", toMax == 0 ? "Maxed" : toMax > 0 ? grouped(toMax) : "--",
				toMax == 0 ? CLOG_GREEN : Color.WHITE));
		if (templeRows)
		{
			card.titled("TempleOSRS").add(CardBody.line("EHP: ", ehbText(ehp))).add(CardBody.line("EHC: ", ehbText(ehc)));
		}
		return card;
	}

	/** XP left until every skill is 99: 0 once maxed, -1 when a skill isn't on the hiscores. */
	static long xpToMax(HiscoreResult result)
	{
		if (result == null)
		{
			return -1;
		}
		long left = 0;
		for (String skill : HiscoreService.skillNames())
		{
			long xp = result.getSkillXp(skill);
			if (xp < 0)
			{
				return -1;
			}
			left += Math.max(0, MAX_SKILL_XP - xp);
		}
		return left;
	}

	String displayedXpText()
	{
		long xp = result != null ? result.getTotalXp() : -1;
		return xp >= 0 ? grouped(xp) : "--";
	}

	int displayedRank()
	{
		return result != null ? result.getOverallRank() : -1;
	}
}
