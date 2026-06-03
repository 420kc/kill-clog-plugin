package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Player summary tooltip on the summary-bar name label.
 * Single-column stats, optional cape icon, then obtained pet sprites.
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

	private String rsn;
	private int overallRank;
	private BufferedImage capeIcon;
	private BufferedImage badgeIcon;
	private String accountLabel;
	private String prestige;

	// Pet data only includes obtained pets.
	private int totalPetCount;
	private List<Integer> obtainedPetList;
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

	public void setPets(List<Integer> allPetIds, Set<Integer> obtainedPetIds, ItemManager itemManager)
	{
		this.totalPetCount = allPetIds != null ? allPetIds.size() : 0;

		obtainedPetList = new ArrayList<>();
		if (allPetIds != null && obtainedPetIds != null)
		{
			for (int id : allPetIds)
			{
				if (obtainedPetIds.contains(id))
				{
					obtainedPetList.add(id);
				}
			}
		}

		if (obtainedPetList.isEmpty()) return;

		petSprites = new BufferedImage[obtainedPetList.size()];
		for (int i = 0; i < obtainedPetList.size(); i++)
		{
			BufferedImage img = itemManager.getImage(obtainedPetList.get(i), 1, false);
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
		return obtainedPetList != null && !obtainedPetList.isEmpty() && petSprites != null;
	}

	private int getPetGridHeight()
	{
		if (!hasPets()) return 0;
		int rows = (obtainedPetList.size() + PET_COLS - 1) / PET_COLS;
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
		int petCount = hasPets() ? obtainedPetList.size() : 0;
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
				String rankText = " #" + String.format("%,d", overallRank);
				if (accountLabel == null) rankText = "#" + String.format("%,d", overallRank);
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
		int obtainedCount = obtainedPetList != null ? obtainedPetList.size() : 0;
		g2.setColor(OSRS_ORANGE);
		g2.drawString(petsLabel, inset, petsHeaderY);
		g2.setColor(completionColor(obtainedCount, totalPetCount));
		g2.drawString(String.valueOf(obtainedCount), inset + sfm.stringWidth(petsLabel), petsHeaderY);

		if (!hasPets()) return;

		FontMetrics bfm = g2.getFontMetrics(FontManager.getRunescapeBoldFont());

		// Obtained pet sprite grid.
		int gridY = petsHeaderY + PET_PAD + bfm.getDescent();
		int cellSize = PET_SIZE + PET_PAD;

		for (int i = 0; i < obtainedPetList.size(); i++)
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
		}
	}

	private String buildRankLine()
	{
		if (accountLabel != null && overallRank > 0)
		{
			return accountLabel + " #" + String.format("%,d", overallRank);
		}
		if (accountLabel != null) return accountLabel;
		if (overallRank > 0) return "#" + String.format("%,d", overallRank);
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
