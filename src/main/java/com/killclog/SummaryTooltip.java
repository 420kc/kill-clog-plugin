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
import java.util.Locale;
import java.util.Set;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Player summary tooltip on the summary-bar name label.
 * Single-column stats, optional cape icon, then obtained pet sprites.
 * Hovering a pet swaps the title to its name and left-click opens its wiki
 * page - same contract as the PvM summary sprites.
 */
public class SummaryTooltip extends TitleTooltip
{
	private static final int PET_SIZE = 15;
	private static final int PET_PAD = 2;
	private static final int PET_COLS = 10;
	private static final int SECTION_GAP = 6;
	private static final int CAPE_PAD = 4;

	private static final int BADGE_SIZE = 13;
	private static final int BADGE_GAP = 3;

	private final TooltipItemHover itemHover = new TooltipItemHover(this);

	private String rsn;
	private int overallRank;
	private BufferedImage capeIcon;
	private BufferedImage badgeIcon;
	private String accountLabel;
	private String prestige;

	// Pet data only includes obtained pets: the gallery shows what you have,
	// not the empty slots.
	private int totalPetCount;
	private List<Integer> petList;
	private String[] petNames;
	private BufferedImage[] petSprites;

	public void setData(String rsn, int overallRank, BufferedImage capeIcon,
						BufferedImage badgeIcon, String accountLabel, String prestige)
	{
		setTitle("Player Summary");
		this.rsn = rsn;
		this.overallRank = overallRank;
		this.capeIcon = capeIcon;
		this.badgeIcon = resizeBadge(badgeIcon);
		this.accountLabel = accountLabel;
		this.prestige = prestige;
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

			BufferedImage img = itemManager.getImage(id, 1, false);
			petSprites[i] = ImageUtil.resizeImage(img, PET_SIZE, PET_SIZE);
			if (img instanceof AsyncBufferedImage)
			{
				final int idx = i;
				((AsyncBufferedImage) img).onLoaded(() ->
					SwingUtilities.invokeLater(() ->
					{
						petSprites[idx] = ImageUtil.resizeImage(img, PET_SIZE, PET_SIZE);
						repaint();
					}));
			}
		}
	}

	private int getStatsLines()
	{
		int lines = 1; // RSN
		if (accountLabel != null || overallRank > 0) lines++;
		if (prestige != null) lines += 2;
		return lines;
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
		if (rsn != null)
		{
			int rsnW = fm.stringWidth(rsn);
			if (badgeIcon != null) rsnW += badgeIcon.getWidth() + BADGE_GAP;
			tw = Math.max(tw, rsnW);
		}
		String rankLine = buildRankLine();
		if (rankLine != null) tw = Math.max(tw, fm.stringWidth(rankLine));
		if (prestige != null)
		{
			tw = Math.max(tw, fm.stringWidth("Prestige:"));
			tw = Math.max(tw, fm.stringWidth(prestige));
		}
		return tw;
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

		int textWidth = getTextWidth(fm);
		int statsHeight = LINE_HEIGHT * getStatsLines();

		// Cape column beside stats.
		int capeColWidth = 0;
		if (capeIcon != null)
		{
			capeColWidth = CAPE_PAD + capeIcon.getWidth();
		}

		// Pet grid.
		int petCount = hasPets() ? petList.size() : 0;
		int petGridWidth = petCount > 0
			? Math.min(petCount, PET_COLS) * (PET_SIZE + PET_PAD) - PET_PAD
			: 0;

		int contentWidth = Math.max(textWidth + capeColWidth, petGridWidth);
		int contentHeight = capeIcon != null
			? Math.max(statsHeight, capeIcon.getHeight())
			: statsHeight;

		if (totalPetCount > 0)
		{
			contentHeight += SECTION_GAP + 1 + SECTION_GAP
				+ fm.getHeight() + PET_PAD;
			if (hasPets())
			{
				contentHeight += getPetGridHeight();
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
		int lineY = startY + fm.getAscent();

		// RSN with optional badge.
		if (rsn != null)
		{
			int rsnX = inset;
			if (badgeIcon != null)
			{
				int iconY = lineY - fm.getAscent() + (LINE_HEIGHT - badgeIcon.getHeight()) / 2;
				g2.drawImage(badgeIcon, rsnX, iconY, null);
				rsnX += badgeIcon.getWidth() + BADGE_GAP;
			}
			g2.setColor(Color.WHITE);
			g2.drawString(rsn, rsnX, lineY);
		}
		lineY += LINE_HEIGHT;

		// Account type plus rank.
		if (accountLabel != null || overallRank > 0)
		{
			int x = inset;
			if (accountLabel != null)
			{
				g2.setColor(OSRS_ORANGE);
				g2.drawString(accountLabel, x, lineY);
				x += fm.stringWidth(accountLabel);
			}
			if (overallRank > 0)
			{
				String rankText = " #" + String.format(Locale.US, "%,d", overallRank);
				if (accountLabel == null) rankText = "#" + String.format(Locale.US, "%,d", overallRank);
				g2.setColor(Color.WHITE);
				g2.drawString(rankText, x, lineY);
			}
			lineY += LINE_HEIGHT;
		}

		// Prestige.
		if (prestige != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Prestige:", inset, lineY);
			lineY += LINE_HEIGHT;
			g2.setColor(Color.WHITE);
			g2.drawString(prestige, inset, lineY);
			lineY += LINE_HEIGHT;
		}

		// Cape icon in the right column, centered against stats.
		int sectionBottom = lineY - fm.getAscent();
		if (capeIcon != null)
		{
			int textRight = inset + getTextWidth(fm);
			int rightEdge = w - inset;
			int capeX = textRight + (rightEdge - textRight - capeIcon.getWidth()) / 2;
			int statsBlockHeight = LINE_HEIGHT * getStatsLines();
			int capeY = startY + (statsBlockHeight - capeIcon.getHeight()) / 2;
			g2.drawImage(capeIcon, capeX, capeY, null);
			sectionBottom = Math.max(sectionBottom, startY + Math.max(statsBlockHeight, capeIcon.getHeight()));
		}

		if (totalPetCount <= 0) return;

		// Separator.
		int sepY = sectionBottom + SECTION_GAP;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, sepY, w - inset - 1, sepY);

		// Pets header.
		FontMetrics sfm = g2.getFontMetrics();
		int petsHeaderY = sepY + 1 + SECTION_GAP + sfm.getAscent();
		String petsLabel = "Pets: ";
		g2.setColor(OSRS_ORANGE);
		g2.drawString(petsLabel, inset, petsHeaderY);
		g2.setColor(completionColor(petList != null ? petList.size() : 0, totalPetCount));
		g2.drawString(String.valueOf(petList != null ? petList.size() : 0), inset + sfm.stringWidth(petsLabel), petsHeaderY);

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
				hitBoxes.add(new TooltipItemHover.HitBox(petList.get(i), petNames[i],
					new Rectangle(px, py, PET_SIZE, PET_SIZE), true));
			}
		}
		itemHover.setHitBoxes(hitBoxes);
	}

	@Override
	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		super.setWikiLinksEnabled(wikiLinksEnabled);
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
	}

	@Override
	protected String getTitleHoverText()
	{
		return itemHover.hoveredItemName();
	}

	@Override
	protected Color getTitleHoverColor()
	{
		return itemHover.hoveredItemObtained() ? CLOG_GREEN : CLOG_RED;
	}

	private String buildRankLine()
	{
		if (accountLabel != null && overallRank > 0)
		{
			return accountLabel + " #" + String.format(Locale.US, "%,d", overallRank);
		}
		if (accountLabel != null) return accountLabel;
		if (overallRank > 0) return "#" + String.format(Locale.US, "%,d", overallRank);
		return null;
	}

	@Nullable
	private static BufferedImage resizeBadge(@Nullable BufferedImage badge)
	{
		if (badge == null || badge.getHeight() <= 0)
		{
			return null;
		}
		int width = Math.max(1, (int) Math.round((double) badge.getWidth() / badge.getHeight() * BADGE_SIZE));
		return ImageUtil.resizeImage(badge, width, BADGE_SIZE);
	}
}
