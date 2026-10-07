package com.killclog;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;

/**
 * Player summary tooltip on the summary-bar name label.
 * The player's name is the title, in white beside their account badge. Under it stand the
 * prestige cape or a Kill Clog syncer's character, centered, then the account and prestige
 * lines, then the obtained pets at full size; past ten rows only the pets scroll, the rest anchored.
 * Hovering a pet names it on the line above them and left-click opens its wiki
 * page - same contract as the boss grids.
 */
public class SummaryTooltip extends TitleTooltip
{
	private static final int PET_COLS = 5;
	private static final int SECTION_GAP = 6;
	private static final int BAND_PAD = 4;
	private static final int FIGURE_GAP = 4;
	private static final String PRESTIGE_LABEL = "Prestige: ";
	private static final String FORMER_TITLE = "Player Summary";

	private int overallRank;
	// The prestige cape, or a Kill Clog syncer's character (PlayerPortraits) standing in its place.
	private BufferedImage figure;
	private String accountLabel;
	private String prestige;

	// Pet data only includes obtained pets: the gallery shows what you have,
	// not the empty slots.
	private int totalPetCount;
	private List<Integer> petList;
	private Map<Integer, String> petNames;
	@Nullable
	private TooltipItemSprites petSprites;
	// The pets scroll at full size under the anchored top of the card, a sprite row a notch.
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.GRID_WINDOW, CardBody.GRID_CELL);

	public void setData(String rsn, int overallRank, BufferedImage figure,
						BufferedImage badgeIcon, String accountLabel, String prestige)
	{
		itemHover.clear();
		// The panel's own stand-in when a lookup has no name, so the card always keeps its title.
		setTitle(rsn != null ? rsn : "Player");
		setTitleIcon(badgeIcon);
		this.overallRank = overallRank;
		this.figure = withoutHeadroom(figure);
		this.accountLabel = accountLabel;
		this.prestige = prestige;
	}

	/** The player's name reads white, like the name it is, not an orange card label. */
	@Override
	protected Color titleColor()
	{
		return Color.WHITE;
	}

	public void setPets(List<Integer> allPetIds, Set<Integer> obtainedPetIds,
		ItemManager itemManager, IntFunction<String> nameLookup)
	{
		this.totalPetCount = allPetIds != null ? allPetIds.size() : 0;

		petList = new ArrayList<>();
		if (allPetIds != null && obtainedPetIds != null)
		{
			for (int id : allPetIds)
			{
				if (obtainedPetIds.contains(id))
				{
					petList.add(id);
				}
			}
		}

		// A pet without a name still hovers and links, under the name its id gives it.
		petNames = new HashMap<>();
		for (int id : petList)
		{
			petNames.put(id, nameLookup != null ? nameLookup.apply(id) : null);
		}
		petSprites = !petList.isEmpty() && itemManager != null
			? TooltipItemSprites.load(petList, petNames, itemManager, id -> 1, this) : null;
	}

	@Override
	CardBody.Scroll scroll()
	{
		return scroll;
	}

	private boolean hasRankLine()
	{
		return accountLabel != null || overallRank > 0;
	}

	private int getStatsLines()
	{
		return (hasRankLine() ? 1 : 0) + (prestige != null ? 1 : 0);
	}

	private boolean hasPets()
	{
		return petList != null && !petList.isEmpty();
	}

	/** A player without hiscores or a figure has nothing above the pets but the title. */
	private boolean hasBodyAbovePets()
	{
		return figure != null || getStatsLines() > 0;
	}

	@Override
	protected CardBody body()
	{
		// The cape or character stands centered under the name.
		CardBody body = new CardBody()
			.add(CardBody.minWidth(c -> getFontMetrics(getTitleFont()).stringWidth(FORMER_TITLE)));
		if (figure != null)
		{
			body.add(CardBody.row(figure.getHeight() + FIGURE_GAP, c -> figure.getWidth(),
				(c, y) -> c.g.drawImage(figure, (c.w - figure.getWidth()) / 2, y, null)));
		}
		// Account type plus rank, then prestige, each centered under the figure.
		if (hasRankLine())
		{
			body.add(centered(accountLabelText(), rankTail()));
		}
		if (prestige != null)
		{
			body.add(centered(PRESTIGE_LABEL, prestige));
		}
		if (totalPetCount > 0)
		{
			// Pets under a separator, or straight under the title's own when nothing stands between.
			if (hasBodyAbovePets())
			{
				body.add(CardBody.separator(SECTION_GAP));
			}
			int count = hasPets() ? petList.size() : 0;
			body.add(CardBody.line("Pets: ", String.valueOf(count), completionColor(count, totalPetCount)));
			if (hasPets())
			{
				// The pets scroll under the band their hovered one is named in, like a skill's log.
				body.add(CardBody.hoverBand(BAND_PAD)).add(CardBody.scroll(scroll, CardBody.grid(PET_COLS, petSprites,
					petNames, petList, new HashSet<>(petList), Collections.emptyMap())));
			}
		}
		return body;
	}

	private static CardBody.Part centered(String label, String value)
	{
		return CardBody.row(LINE_HEIGHT, c -> c.fm.stringWidth(label + value), (c, y) -> drawLabelValue(c.g, c.fm,
			(c.w - c.fm.stringWidth(label + value)) / 2, y + c.fm.getAscent(), label, value));
	}

	private String accountLabelText()
	{
		return accountLabel != null ? accountLabel : "";
	}

	/** " #1,234" after an account label, "#1,234" alone, nothing when unranked. */
	private String rankTail()
	{
		return overallRank > 0 ? (accountLabel != null ? " #" : "#") + grouped(overallRank) : "";
	}

	/**
	 * The figure cut to its first row of pixels: a character's portrait leaves room above the head
	 * for tall hats, which would read as a gap under the name.
	 */
	@Nullable
	static BufferedImage withoutHeadroom(@Nullable BufferedImage image)
	{
		if (image == null)
		{
			return null;
		}
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) != 0)
				{
					return y == 0 ? image : image.getSubimage(0, y, image.getWidth(), image.getHeight() - y);
				}
			}
		}
		return image;
	}
}
