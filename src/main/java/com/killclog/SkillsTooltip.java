package com.killclog;

import javax.annotation.Nullable;
import net.runelite.api.Experience;
import net.runelite.client.util.LinkBrowser;

/**
 * Experience &amp; Efficiency on the total level cell: total XP, overall rank and the XP left to max, then Efficient
 * Hours (Dylan, 2026-10-15): Played and Clogged, TempleOSRS's EHP and EHC, when Temple tracks the player, and Bossed,
 * the Combat Summary's EHB, for anyone. Each skill's own numbers live in its cell, in the grid or the activity tray.
 */
public class SkillsTooltip extends TitleTooltip
{
	static final String TITLE = "Experience & Efficiency";
	private static final long MAX_SKILL_XP = Experience.getXpForLevel(Experience.MAX_REAL_LEVEL);

	private HiscoreResult result;
	private double ehp = -1;
	private double ehc = -1;
	private double ehb = -1;
	// The player's TempleOSRS page, which the Efficient Hours lines open (null when the name isn't known).
	@Nullable
	private String temple;
	private final CardBody.ClickRows hourRows = new CardBody.ClickRows(this, (e, row) ->
	{
		if (temple != null && linkOpens(e))
		{
			LinkBrowser.browse(temple);
			hideTooltipTree(this);
			e.consume();
		}
	});
	private boolean templeRows;

	/** {@code templeRows}: show Temple's rows, "--" where this player has none, so a comparison pair lines up. */
	public void setData(HiscoreResult result, ClogResult clog, boolean templeRows)
	{
		this.result = result;
		ehp = clog != null ? clog.getTempleEhp() : -1;
		ehc = clog != null ? clog.getTempleEhc() : -1;
		ehb = LookupQueries.ehb(result, clog);
		temple = clog != null && clog.getPlayerName() != null ? TooltipItemLink.templeUrl(clog.getPlayerName()) : null;
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
				result != null && result.getTotalXp() >= 0 ? NativeTooltip.valueColor : KillClogColors.UNRANKED_COLOR))
			.add(CardBody.line("Rank: ", scoreText(displayedRank()), displayedRank() > 0 ? NativeTooltip.valueColor : KillClogColors.UNRANKED_COLOR))
			.add(CardBody.line("To max: ", toMax == 0 ? "Maxed" : toMax > 0 ? grouped(toMax) : "--",
				toMax == 0 ? CLOG_GREEN : NativeTooltip.valueColor));
		card.titled("Efficient Hours");
		if (templeRows)
		{
			card.add(hours(0, "Played: ", ehp)).add(hours(1, "Clogged: ", ehc));
		}
		return card.add(hours(2, "Bossed: ", ehb));
	}

	private CardBody.Part hours(int row, String label, double value)
	{
		String text = ehbText(value);
		return CardBody.clickRow(hourRows, row, c -> c.fm.stringWidth(label + text), (c, y, hovered) ->
			drawLabelValue(c.g, c.fm, c.inset(), y + c.fm.getAscent(), label, text,
				hovered && temple != null ? NativeTooltip.valueColor : labelColor, NativeTooltip.valueColor));
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
