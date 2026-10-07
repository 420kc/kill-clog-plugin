package com.killclog;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;

/**
 * Player summary tooltip on the summary-bar name label.
 * The player's name is the title, in white beside their account badge. Under it stand the
 * prestige cape or a Kill Clog syncer's character, centered, then the account and prestige
 * lines, then obtained pet sprites.
 * Hovering a pet names it under the grid and left-click opens its wiki
 * page - same contract as the PvM summary sprites.
 */
public class SummaryTooltip extends TitleTooltip
{
	private static final int PET_SIZE = 15;
	private static final int PET_PAD = 2;
	private static final int PET_COLS = 10;
	private static final int SECTION_GAP = 6;
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
	private String[] petNames;
	private BufferedImage[] petSprites;

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

		if (petList.isEmpty())
		{
			return;
		}

		petNames = new String[petList.size()];
		petSprites = new BufferedImage[petList.size()];

		for (int i = 0; i < petList.size(); i++)
		{
			int id = petList.get(i);
			petNames[i] = nameLookup != null ? nameLookup.apply(id) : null;
			loadItemSprite(id, PET_SIZE, petSprites, i, itemManager);
		}
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
		return petList != null && !petList.isEmpty() && petSprites != null;
	}

	private int getPetGridHeight()
	{
		if (!hasPets()) return 0;
		int rows = (petList.size() + PET_COLS - 1) / PET_COLS;
		return rows * (PET_SIZE + PET_PAD) - PET_PAD;
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
			body.add(pets());
		}
		return body;
	}

	private static CardBody.Part centered(String label, String value)
	{
		return CardBody.row(LINE_HEIGHT, c -> c.fm.stringWidth(label + value), (c, y) -> drawLabelValue(c.g, c.fm,
			(c.w - c.fm.stringWidth(label + value)) / 2, y + c.fm.getAscent(), label, value));
	}

	/**
	 * The pet count, then the full pet gallery: obtained at strength, unobtained dimmed. Hit boxes
	 * share the draw geometry so hover-name and wiki-click track exactly.
	 */
	private CardBody.Part pets()
	{
		int count = hasPets() ? petList.size() : 0;
		return CardBody.part(c -> count > 0 ? Math.min(count, PET_COLS) * (PET_SIZE + PET_PAD) - PET_PAD : 0,
			c -> c.fm.getHeight() + PET_PAD + (hasPets() ? getPetGridHeight() + hoverRowHeight(c.fm) : 0), (c, y) ->
			{
				int headerY = y + c.fm.getAscent();
				drawLabelValue(c.g, c.fm, c.inset(), headerY, "Pets: ", String.valueOf(count),
					completionColor(count, totalPetCount));
				if (!hasPets())
				{
					return;
				}
				int gridY = headerY + PET_PAD + c.bfm.getDescent();
				int cellSize = PET_SIZE + PET_PAD;
				for (int i = 0; i < petList.size(); i++)
				{
					int px = c.inset() + (i % PET_COLS) * cellSize;
					int py = gridY + (i / PET_COLS) * cellSize;
					if (petSprites[i] != null)
					{
						c.g.drawImage(petSprites[i], px, py, null);
					}
					if (petNames[i] != null)
					{
						c.hits.add(new TooltipItemHover.HitBox(0, petList.get(i), petNames[i],
							new Rectangle(px, py, PET_SIZE, PET_SIZE), true));
					}
				}
				paintHeaderHoverLine(c.g, c.fm, c.w, gridY + getPetGridHeight() + c.fm.getAscent());
			});
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
