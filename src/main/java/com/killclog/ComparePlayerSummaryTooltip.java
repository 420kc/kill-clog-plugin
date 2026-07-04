package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Stacked player comparison tooltip. Blue block on top, red block below,
 * separated by an inlay line. Each block renders
 * the player's name (badge + RSN) followed by account/rank/prestige/pets.
 */
public class ComparePlayerSummaryTooltip extends TitleTooltip
{
	private static final int PET_SIZE = 15;
	private static final int PET_PAD = 2;
	private static final int PET_COLS = 5;
	private static final int SEPARATOR_PAD = 3;
	private static final int COL_GAP = 10;
	private static final int HEADER_GAP = 4;
	private static final int BADGE_SIZE = 13;
	private static final int BADGE_GAP = 3;


	// One player's block of stats; the tooltip holds a blue and a red side.
	private static final class Side
	{
		String rsn;
		int rank;
		BufferedImage badge;
		BufferedImage cape;
		String accountLabel;
		String prestige;
		int totalPets;
		List<Integer> obtainedPets;
		BufferedImage[] petSprites;
	}

	private final Side blue = new Side();
	private final Side red = new Side();

	public void setBlueData(String rsn, int rank, BufferedImage badge,
		String accountLabel, String prestige)
	{
		setBlueData(rsn, rank, badge, accountLabel, prestige, null);
	}

	public void setBlueData(String rsn, int rank, BufferedImage badge,
		String accountLabel, String prestige, BufferedImage cape)
	{
		setTitle("Player Summary");
		setData(blue, rsn, rank, badge, accountLabel, prestige, cape);
	}

	public void setRedData(String rsn, int rank, BufferedImage badge,
		String accountLabel, String prestige)
	{
		setRedData(rsn, rank, badge, accountLabel, prestige, null);
	}

	public void setRedData(String rsn, int rank, BufferedImage badge,
		String accountLabel, String prestige, BufferedImage cape)
	{
		setData(red, rsn, rank, badge, accountLabel, prestige, cape);
	}

	private static void setData(Side side, String rsn, int rank, BufferedImage badge,
		String accountLabel, String prestige, BufferedImage cape)
	{
		side.rsn = rsn;
		side.rank = rank;
		side.badge = resizeBadge(badge);
		side.cape = cape;
		side.accountLabel = accountLabel;
		side.prestige = prestige;
	}

	public void setBluePets(List<Integer> allPetIds, Set<Integer> obtainedPetIds,
		ItemManager itemManager)
	{
		setPets(blue, allPetIds, obtainedPetIds, itemManager);
	}

	public void setRedPets(List<Integer> allPetIds, Set<Integer> obtainedPetIds,
		ItemManager itemManager)
	{
		setPets(red, allPetIds, obtainedPetIds, itemManager);
	}

	private void setPets(Side side, List<Integer> allPetIds, Set<Integer> obtainedPetIds,
		ItemManager itemManager)
	{
		side.totalPets = allPetIds != null ? allPetIds.size() : 0;
		side.obtainedPets = filterObtained(allPetIds, obtainedPetIds);
		side.petSprites = loadPetSprites(side.obtainedPets, itemManager);
	}

	// Sizing.

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

		int labelW = measureLabelWidth(fm);
		int valueW = measureValueWidth(fm);
		int blockWidth = labelW + COL_GAP + valueW;

		int blueH = measureBlockHeight(fm, blue);
		int redH = measureBlockHeight(fm, red);
		int totalHeight = blueH + SEPARATOR_PAD + 1 + SEPARATOR_PAD + redH;

		return new Dimension(blockWidth, totalHeight);
	}

	private int measureLabelWidth(FontMetrics fm)
	{
		int w = 0;
		w = Math.max(w, fm.stringWidth("Account"));
		w = Math.max(w, fm.stringWidth("Rank"));
		if (blue.prestige != null || red.prestige != null) w = Math.max(w, fm.stringWidth("Prestige"));
		w = Math.max(w, fm.stringWidth("Pets"));
		if (blue.cape != null) w = Math.max(w, blue.cape.getWidth());
		if (red.cape != null) w = Math.max(w, red.cape.getWidth());
		return w;
	}

	private int measureValueWidth(FontMetrics fm)
	{
		int w = Math.max(sideValueWidth(fm, blue), sideValueWidth(fm, red));
		w = Math.max(w, fm.stringWidth("Normal"));
		// Pet sprites.
		int petGridW = PET_COLS * (PET_SIZE + PET_PAD) - PET_PAD;
		return Math.max(w, petGridW);
	}

	private static int sideValueWidth(FontMetrics fm, Side side)
	{
		// Player name includes badge width.
		int w = fm.stringWidth(side.rsn != null ? side.rsn : "--");
		if (side.badge != null) w += side.badge.getWidth() + BADGE_GAP;
		// Account type.
		if (side.accountLabel != null) w = Math.max(w, fm.stringWidth(side.accountLabel));
		// Rank: measure the real rank string, not a placeholder.
		w = Math.max(w, fm.stringWidth(rankValueText(side.rank)));
		// Prestige.
		if (side.prestige != null) w = Math.max(w, fm.stringWidth(side.prestige));
		return w;
	}

	private int measureBlockHeight(FontMetrics fm, Side side)
	{
		int h = 0;
		// Name.
		h += fm.getHeight() + HEADER_GAP;
		// Account type.
		h += LINE_HEIGHT;
		// Rank.
		h += LINE_HEIGHT;
		// Prestige.
		if (side.prestige != null) h += LINE_HEIGHT;
		// Pets.
		if (side.totalPets > 0)
		{
			h += LINE_HEIGHT; // "Pets" count row
			int petAreaH = Math.max(petGridHeight(side.obtainedPets),
				side.cape != null ? side.cape.getHeight() : 0);
			if (petAreaH > 0)
			{
				h += petAreaH;
			}
		}
		return h;
	}

	private int petRows(List<Integer> pets)
	{
		if (pets == null || pets.isEmpty()) return 0;
		return (pets.size() + PET_COLS - 1) / PET_COLS;
	}

	private int petGridHeight(List<Integer> pets)
	{
		int rows = petRows(pets);
		return rows > 0 ? rows * (PET_SIZE + PET_PAD) - PET_PAD : 0;
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		int contentWidth = w - 2 * inset;

		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();

		int labelW = measureLabelWidth(fm);
		int valueX = inset + labelW + COL_GAP;
		int labelX = inset;
		int y = startY;

		// Blue block.
		y = paintBlock(g2, fm, labelX, valueX, y, w, COMPARE_BLUE, blue);

		// Separator.
		y += SEPARATOR_PAD;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, y, w - inset - 1, y);
		y += 1 + SEPARATOR_PAD;

		// Red block.
		paintBlock(g2, fm, labelX, valueX, y, w, COMPARE_RED, red);
	}

	private int paintBlock(Graphics2D g2, FontMetrics fm,
		int labelX, int valueX, int y, int w,
		Color playerColor, Side side)
	{
		// Name with optional badge.
		int nx = labelX;
		if (side.badge != null)
		{
			int iconY = y + (fm.getHeight() - side.badge.getHeight()) / 2;
			g2.drawImage(side.badge, nx, iconY, null);
			nx += side.badge.getWidth() + BADGE_GAP;
		}
		g2.setColor(playerColor);
		g2.drawString(side.rsn != null ? side.rsn : "--", nx, y + fm.getAscent());
		y += fm.getHeight() + HEADER_GAP;

		// Account type.
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Account", labelX, y + fm.getAscent());
		g2.setColor(playerColor);
		g2.drawString(side.accountLabel != null ? side.accountLabel : "Normal", valueX, y + fm.getAscent());
		y += LINE_HEIGHT;

		// Rank.
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Rank", labelX, y + fm.getAscent());
		g2.setColor(playerColor);
		g2.drawString(rankValueText(side.rank), valueX, y + fm.getAscent());
		y += LINE_HEIGHT;

		// Prestige.
		if (side.prestige != null)
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Prestige", labelX, y + fm.getAscent());
			g2.setColor(playerColor);
			g2.drawString(side.prestige, valueX, y + fm.getAscent());
			y += LINE_HEIGHT;
		}

		// Pets.
		if (side.totalPets > 0)
		{
			int obt = side.obtainedPets != null ? side.obtainedPets.size() : 0;
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Pets", labelX, y + fm.getAscent());
			g2.setColor(completionColor(obt, side.totalPets));
			g2.drawString(String.valueOf(obt), valueX, y + fm.getAscent());
			y += LINE_HEIGHT;

			int labelW = valueX - labelX - COL_GAP;
			int petAreaH = Math.max(petGridHeight(side.obtainedPets),
				side.cape != null ? side.cape.getHeight() : 0);
			if (side.cape != null && petAreaH > 0)
			{
				int capeX = labelX + (labelW - side.cape.getWidth()) / 2;
				int capeY = y + (petAreaH - side.cape.getHeight()) / 2;
				g2.drawImage(side.cape, capeX, capeY, null);
			}

			paintPetGrid(g2, valueX, y, side.petSprites);
			if (petAreaH > 0)
			{
				y += petAreaH;
			}
		}

		return y;
	}

	private void paintPetGrid(Graphics2D g2, int x, int y, BufferedImage[] sprites)
	{
		if (sprites == null || sprites.length == 0) return;
		int cellSize = PET_SIZE + PET_PAD;
		for (int i = 0; i < sprites.length; i++)
		{
			int col = i % PET_COLS;
			int row = i / PET_COLS;
			int px = x + col * cellSize;
			int py = y + row * cellSize;
			if (sprites[i] != null)
			{
				g2.drawImage(sprites[i], px, py, null);
			}
		}
	}

	// Helpers.

	private static String rankValueText(int rank)
	{
		return rank > 0 ? "#" + String.format(Locale.US, "%,d", rank) : "Unranked";
	}

	private static List<Integer> filterObtained(List<Integer> allIds, Set<Integer> obtainedIds)
	{
		List<Integer> result = new ArrayList<>();
		if (allIds != null && obtainedIds != null)
		{
			for (int id : allIds)
			{
				if (obtainedIds.contains(id))
				{
					result.add(id);
				}
			}
		}
		return result;
	}

	private BufferedImage[] loadPetSprites(List<Integer> petIds, ItemManager itemManager)
	{
		if (petIds == null || petIds.isEmpty()) return null;
		BufferedImage[] sprites = new BufferedImage[petIds.size()];
		for (int i = 0; i < petIds.size(); i++)
		{
			BufferedImage img = itemManager.getImage(petIds.get(i), 1, false);
			sprites[i] = ImageUtil.resizeImage(img, PET_SIZE, PET_SIZE);
			if (img instanceof AsyncBufferedImage)
			{
				final int idx = i;
				final BufferedImage[] localSprites = sprites;
				((AsyncBufferedImage) img).onLoaded(() ->
					SwingUtilities.invokeLater(() ->
					{
						localSprites[idx] = ImageUtil.resizeImage(img, PET_SIZE, PET_SIZE);
						repaint();
					}));
			}
		}
		return sprites;
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
