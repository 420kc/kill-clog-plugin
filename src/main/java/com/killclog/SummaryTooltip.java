package com.killclog;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;

/**
 * Player summary tooltip on the summary-bar name label.
 * The player's name is the title, in white beside their account badge. Under it stand the
 * prestige cape or a Kill Clog syncer's character, centered, then the account and prestige
 * lines, then the obtained pets at full size. Like the other summaries, only the title and the hover band
 * under it stay put: the rest scrolls as one once it outgrows the window. Hovering a pet names it in that band
 * and left-click opens its wiki page - same contract as the boss grids.
 */
public class SummaryTooltip extends TitleTooltip
{
	private static final int PET_COLS = 5;
	private static final int FIGURE_GAP = 4;
	private static final String PRESTIGE_LABEL = "Prestige: ";
	private static final String FORMER_TITLE = "Player Summary";
	// The header keeps room for the name and, clear of it in the corner, the prestige cape.
	private static final int CAPE_ROOM = 44;
	private static final int CAPE_SECTION = 7;

	private int overallRank;
	// The prestige cape, or a Kill Clog syncer's character (PlayerPortraits) standing in its place.
	private BufferedImage figure;
	private String accountLabel;
	private String prestige;
	private String name;
	// With a character standing in, the prestige cape sits small in the header's corner and names its prestige.
	@Nullable
	private BufferedImage cape;

	// Pet data only includes obtained pets: the gallery shows what you have,
	// not the empty slots.
	private int totalPetCount;
	private List<Integer> petList;
	private Map<Integer, String> petNames;
	private Map<Integer, Integer> petCounts = Collections.emptyMap();
	@Nullable
	private TooltipItemSprites petSprites;
	// Everything under the band scrolls in the standard window, a sprite row a notch, as a skill's log does.
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.WINDOW, CardBody.GRID_CELL);

	public void setData(String rsn, int overallRank, BufferedImage figure,
						BufferedImage badgeIcon, String accountLabel, String prestige)
	{
		itemHover.clear();
		// The panel's own stand-in when a lookup has no name, so the card always keeps its title.
		name = rsn != null ? rsn : "Player";
		setTitle(name);
		setTitleIcon(badgeIcon);
		this.overallRank = overallRank;
		this.figure = withoutHeadroom(figure);
		this.accountLabel = accountLabel;
		this.prestige = prestige;
		// Account type and rank read under the name, above the title's divider.
		if (hasRankLine())
		{
			setSubtitle(accountLabelText(), rankTail(), Color.WHITE);
		}
		else
		{
			clearSubtitle();
		}
	}

	/** A published character's prestige cape, drawn small in the header; null when the cape is the figure. */
	void setCape(@Nullable BufferedImage cape)
	{
		this.cape = cape;
	}

	/** The player's name reads white, like the name it is, not an orange card label. */
	@Override
	protected Color titleColor()
	{
		return Color.WHITE;
	}

	/** The player's name at the size the grid cards title in. */
	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	/** The pets held, in the log's order, each with its count for the corner like any item sprite. */
	public void setPets(List<Integer> allPetIds, Map<Integer, Integer> obtainedPetCounts,
		ItemManager itemManager, IntFunction<String> nameLookup)
	{
		this.totalPetCount = allPetIds != null ? allPetIds.size() : 0;
		petCounts = obtainedPetCounts != null ? obtainedPetCounts : Collections.emptyMap();

		petList = new ArrayList<>();
		if (allPetIds != null)
		{
			for (int id : allPetIds)
			{
				if (petCounts.containsKey(id))
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

	private boolean hasPets()
	{
		return petList != null && !petList.isEmpty();
	}

	/** Without a figure or prestige, nothing stands between the title and the pets. */
	private boolean hasBodyAbovePets()
	{
		return figure != null || prestige != null;
	}

	@Override
	protected CardBody body()
	{
		// The cape or character stands centered under the name; with a character, the cape names its prestige from
		// the header's corner instead of a line under the figure.
		boolean capeInHeader = cape != null && prestige != null;
		CardBody card = new CardBody().add(CardBody.minWidth(c -> getFontMetrics(getTitleFont()).stringWidth(FORMER_TITLE)));
		if (capeInHeader)
		{
			card.add(CardBody.row(0, c -> getFontMetrics(getTitleFont()).stringWidth(name) + CAPE_ROOM, (c, y) ->
			{
				int x = c.w - c.inset() - cape.getWidth();
				int top = c.inset() + (getHeaderHeight() - cape.getHeight()) / 2;
				c.g.drawImage(cape, x, top, null);
				c.hits.add(new TooltipItemHover.HitBox(CAPE_SECTION, 0, prestige,
					new Rectangle(x, top, cape.getWidth(), cape.getHeight()), true));
			}));
		}
		if (figure != null)
		{
			card.add(CardBody.row(figure.getHeight() + FIGURE_GAP, c -> figure.getWidth(),
				(c, y) -> c.g.drawImage(figure, (c.w - figure.getWidth()) / 2, y, null)));
		}
		// Prestige centered under the figure, when the figure is the cape.
		if (prestige != null && !capeInHeader)
		{
			card.add(centered(PRESTIGE_LABEL, prestige));
		}
		if (totalPetCount > 0)
		{
			// A pet's name shows in the band over the pets, straight under the title's divider when nothing stands
			// between, and the pets scroll under it.
			int count = hasPets() ? petList.size() : 0;
			CardBody pets = new CardBody().add(CardBody.line("Pets: ", String.valueOf(count),
				completionColor(count, totalPetCount)));
			if (hasPets())
			{
				pets.add(CardBody.grid(PET_COLS, petSprites, petNames, petList, new HashSet<>(petList), petCounts));
			}
			card.add(hasBodyAbovePets() ? CardBody.hoverBand() : CardBody.headerHoverBand())
				.add(CardBody.scroll(scroll, pets.asPart()));
		}
		else if (capeInHeader)
		{
			card.add(CardBody.hoverBand());
		}
		return card;
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
