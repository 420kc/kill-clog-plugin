package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

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

	private int getTextWidth(FontMetrics fm)
	{
		int tw = 0;
		if (hasRankLine())
		{
			tw = fm.stringWidth(accountLabelText() + rankTail());
		}
		if (prestige != null)
		{
			tw = Math.max(tw, fm.stringWidth(PRESTIGE_LABEL + prestige));
		}
		return tw;
	}

	/** A player without hiscores or a figure has nothing above the pets but the title. */
	private boolean hasBodyAbovePets()
	{
		return figure != null || getStatsLines() > 0;
	}

	private int getFigureHeight()
	{
		return figure != null ? figure.getHeight() + FIGURE_GAP : 0;
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

		// Pet grid.
		int petCount = hasPets() ? petList.size() : 0;
		int petGridWidth = petCount > 0
			? Math.min(petCount, PET_COLS) * (PET_SIZE + PET_PAD) - PET_PAD
			: 0;

		int contentWidth = Math.max(Math.max(getTextWidth(fm), figure != null ? figure.getWidth() : 0),
			petGridWidth);
		// Never thinner than the card was when "Player Summary" titled it, whatever the name.
		contentWidth = Math.max(contentWidth, getFontMetrics(getTitleFont()).stringWidth(FORMER_TITLE));
		int contentHeight = getFigureHeight() + LINE_HEIGHT * getStatsLines();

		if (totalPetCount > 0)
		{
			contentHeight += (hasBodyAbovePets() ? SECTION_GAP + 1 + SECTION_GAP : 0)
				+ fm.getHeight() + PET_PAD;
			if (hasPets())
			{
				contentHeight += getPetGridHeight() + hoverRowHeight(fm);
			}
		}

		return new Dimension(contentWidth, contentHeight);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		// The cape or character stands centered under the name.
		if (figure != null)
		{
			g2.drawImage(figure, (w - figure.getWidth()) / 2, startY, null);
		}
		int lineY = startY + getFigureHeight() + fm.getAscent();

		// Account type plus rank, then prestige, each centered under the figure.
		if (hasRankLine())
		{
			String line = accountLabelText() + rankTail();
			drawLabelValue(g2, fm, (w - fm.stringWidth(line)) / 2, lineY, accountLabelText(), rankTail());
			lineY += LINE_HEIGHT;
		}
		if (prestige != null)
		{
			drawLabelValue(g2, fm, (w - fm.stringWidth(PRESTIGE_LABEL + prestige)) / 2, lineY,
				PRESTIGE_LABEL, prestige);
			lineY += LINE_HEIGHT;
		}

		if (totalPetCount <= 0) return;

		// Pets header under a separator, or straight under the title's own when nothing stands between.
		int petsTop = hasBodyAbovePets() ? paintSeparator(g2, w, lineY - fm.getAscent(), SECTION_GAP) : startY;
		int petsHeaderY = petsTop + fm.getAscent();
		int petCount = petList != null ? petList.size() : 0;
		drawLabelValue(g2, fm, inset, petsHeaderY, "Pets: ", String.valueOf(petCount),
			completionColor(petCount, totalPetCount));

		if (!hasPets())
		{
			itemHover.setHitBoxes(Collections.emptyList());
			return;
		}

		FontMetrics bfm = g2.getFontMetrics(FontManager.getRunescapeBoldFont());

		// Full pet gallery: obtained at strength, unobtained dimmed. Hit boxes
		// share the draw geometry so hover-name and wiki-click track exactly.
		int gridY = petsHeaderY + PET_PAD + bfm.getDescent();
		int cellSize = PET_SIZE + PET_PAD;
		List<TooltipItemHover.HitBox> hitBoxes = new ArrayList<>();

		for (int i = 0; i < petList.size(); i++)
		{
			int col = i % PET_COLS;
			int row = i / PET_COLS;
			int px = inset + col * cellSize;
			int py = gridY + row * cellSize;

			BufferedImage sprite = petSprites[i];
			if (sprite != null)
			{
				g2.drawImage(sprite, px, py, null);
			}
			if (petNames[i] != null)
			{
				hitBoxes.add(new TooltipItemHover.HitBox(0, petList.get(i), petNames[i],
					new Rectangle(px, py, PET_SIZE, PET_SIZE), true, 1));
			}
		}
		paintHeaderHoverLine(g2, fm, w, gridY + getPetGridHeight() + fm.getAscent());
		itemHover.setHitBoxes(hitBoxes);
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
