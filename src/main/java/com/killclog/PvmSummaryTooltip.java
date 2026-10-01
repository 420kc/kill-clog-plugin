package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Setter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Combat Summary tooltip on the combat level cell.
 * Stats at top, then Slayer/Superiors, Raids/Mega Rares and the PvP rows.
 */
public class PvmSummaryTooltip extends TitleTooltip
{
	private static final int WEAPON_SIZE = 28;
	private static final int WEAPON_PAD = 6;
	private static final int SEPARATOR_PAD = 2;
	private static final int SECTION_GAP = 4;
	private static final int CA_ROW_HEIGHT = 18;
	private static final int CA_REWARD_GAP = 3;
	// The combat level leads the card beside the combat cell's own icon, with room around it.
	private static final int LEVEL_ROW_HEIGHT = 22;
	private static final int LEVEL_GAP_ABOVE = 4;
	private static final int LEVEL_GAP_BELOW = 6;
	private static final int LEVEL_ICON_GAP = 4;

	private double combatLevel;
	private int totalKills;
	private int bossesWithKc;
	private int totalBosses;
	private long slayerXp = -1;
	private int slayerRank = -1;
	private int slayerObtained = -1;
	private int slayerTotal;
	private String mostKilled;
	private int mostKilledKc;

	private int bossesCompleted = -1;
	private int bossesWithClog;
	@Setter
	private double ehb = -1;

	private final BufferedImage[] weaponSprites = new BufferedImage[3];
	private final int[] weaponCounts = new int[3];
	private final BufferedImage[] superiorSprites = new BufferedImage[2];
	private final int[] superiorCounts = new int[2];

	// CoX, ToB and ToA: normal plus hard-mode kills, and the raid's collection progress.
	private static final String[] RAID_LABELS = {"CoX: ", "ToB: ", "ToA: "};
	private static final String[][] RAID_HISCORES = {
		{PanelData.COX_HISCORE, PanelData.COX_HISCORE_HARD},
		{PanelData.TOB_HISCORE, PanelData.TOB_HISCORE_HARD},
		{PanelData.TOA_HISCORE, PanelData.TOA_HISCORE_HARD}};
	private static final String[] RAID_CATEGORIES = {PanelData.COX_CATEGORY, PanelData.TOB_CATEGORY, PanelData.TOA_CATEGORY};
	private final int[] raidKc = new int[3];
	private final int[] raidObtained = {-1, -1, -1};
	private final int[] raidTotal = new int[3];

	@Setter
	private Image combatIcon;
	private CombatAchievementResult caResult;
	private BufferedImage caRewardSprite;
	private final PvpSummaryRows pvpRows = new PvpSummaryRows();

	public void setData(double combatLevel, int totalKills, int bossesWithKc, int totalBosses,
						String mostKilled, int mostKilledKc)
	{
		itemHover.clear();
		setTitle("Combat Summary");
		this.combatLevel = combatLevel;
		this.totalKills = totalKills;
		this.bossesWithKc = bossesWithKc;
		this.totalBosses = totalBosses;
		this.mostKilled = mostKilled;
		this.mostKilledKc = mostKilledKc;
	}

	/** The five PvP activities close out the card under their own subheader. */
	void setPvp(HiscoreResult hiscore, ClogResult clog, BufferedImage[] icons)
	{
		pvpRows.setIcons(icons);
		pvpRows.setData(hiscore, clog);
	}

	public void setCompletion(int completed, int total)
	{
		this.bossesCompleted = completed;
		this.bossesWithClog = total;
	}

	/**
	 * Combat Achievement tier for the CA section, with a pre-sized tier reward sprite.
	 * A null result omits the section entirely.
	 */
	public void setCombatAchievements(CombatAchievementResult ca, BufferedImage rewardSprite)
	{
		this.caResult = ca;
		this.caRewardSprite = rewardSprite;
	}

	public void setMegarares(int tbowCount, int scytheCount,
		int shadowCount, ItemManager itemManager)
	{
		weaponCounts[0] = tbowCount;
		weaponCounts[1] = scytheCount;
		weaponCounts[2] = shadowCount;

		loadItemSprites(PanelData.MEGARARE_ITEM_IDS, WEAPON_SIZE, weaponSprites, itemManager);
	}

	public void setSuperiors(int imbuedHeartCount, int eternalGemCount,
		ItemManager itemManager)
	{
		superiorCounts[0] = imbuedHeartCount;
		superiorCounts[1] = eternalGemCount;

		loadItemSprites(PanelData.SUPERIOR_ITEMS, WEAPON_SIZE, superiorSprites, itemManager);
	}

	public void setSlayer(HiscoreResult hiscoreResult, ClogResult clogResult)
	{
		slayerXp = hiscoreResult != null
			? hiscoreResult.getSkillXp(PanelData.SLAYER_CATEGORY) : -1;
		slayerRank = hiscoreResult != null
			? hiscoreResult.getSkillRank(PanelData.SLAYER_CATEGORY) : -1;
		slayerObtained = -1;
		slayerTotal = 0;
		int[] slayer = ClogHelper.clogCounts(PanelData.SLAYER_CATEGORY, clogResult);
		if (slayer != null)
		{
			slayerObtained = slayer[0];
			slayerTotal = slayer[1];
		}
	}

	public void setRaids(HiscoreResult hiscoreResult, ClogResult clogResult)
	{
		for (int i = 0; i < 3; i++)
		{
			raidKc[i] = Math.max(0, hiscoreResult.getKc(RAID_HISCORES[i][0]))
				+ Math.max(0, hiscoreResult.getKc(RAID_HISCORES[i][1]));
			int[] counts = clogResult != null ? ClogHelper.clogCounts(RAID_CATEGORIES[i], clogResult) : null;
			if (counts != null)
			{
				raidObtained[i] = counts[0];
				raidTotal[i] = counts[1];
			}
		}
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());

		// Stats section.
		int statsLines = 3; // Total Kills, EHB, Bosses
		if (bossesCompleted >= 0) statsLines++;
		int statsHeight = LINE_HEIGHT * statsLines;

		// Most killed section
		int mostKilledHeight = 0;
		if (mostKilled != null)
		{
			mostKilledHeight = SECTION_GAP + LINE_HEIGHT * 2;
		}

		int spriteRowWidth = 3 * WEAPON_SIZE + 2 * WEAPON_PAD;
		int superiorRowWidth = 2 * WEAPON_SIZE + WEAPON_PAD;

		// Slayer section.
		int slayerHeight = SUBHEADER_HEIGHT + LINE_HEIGHT * slayerRowCount()
			+ WEAPON_PAD + WEAPON_SIZE + hoverRowHeight(fm);

		// Raids section.
		int raidsHeight = SUBHEADER_HEIGHT + LINE_HEIGHT * 3
			+ WEAPON_PAD + WEAPON_SIZE + hoverRowHeight(fm);

		// Width: measure the real rendered strings, never a placeholder.
		int textWidth = 0;
		textWidth = Math.max(textWidth, iconWidth() + bfm.stringWidth(combatText(combatLevel)));
		textWidth = Math.max(textWidth, fm.stringWidth("Total Kills: " + scoreText(totalKills)));
		textWidth = Math.max(textWidth, fm.stringWidth("EHB: " + ehbText(ehb)));
		textWidth = Math.max(textWidth, fm.stringWidth("XP: " + slayerXpText(slayerXp)));
		textWidth = Math.max(textWidth, fm.stringWidth("Rank: " + scoreText(slayerRank)));
		if (slayerObtained >= 0)
		{
			textWidth = Math.max(textWidth,
				fm.stringWidth("Obtained: " + progressCountText(slayerObtained, slayerTotal)));
		}
		textWidth = Math.max(textWidth, fm.stringWidth("Bosses Killed: " + bossesValue()));
		if (bossesCompleted >= 0)
		{
			textWidth = Math.max(textWidth, fm.stringWidth("Logs Completed: " + logsValue()));
		}
		if (mostKilled != null)
		{
			textWidth = Math.max(textWidth, fm.stringWidth(mostKilledLine()));
		}
		textWidth = Math.max(textWidth, bfm.stringWidth("PvM Summary"));
		textWidth = Math.max(textWidth, bfm.stringWidth("PvP Summary"));
		textWidth = Math.max(textWidth, bfm.stringWidth("Slayer"));
		textWidth = Math.max(textWidth, bfm.stringWidth("Raids"));
		for (int i = 0; i < 3; i++)
		{
			textWidth = Math.max(textWidth, raidLineWidth(fm, RAID_LABELS[i], raidKc[i], raidObtained[i], raidTotal[i]));
		}
		if (caResult != null)
		{
			int caWidth = fm.stringWidth("CA Tier: " + tierDisplayName(caResult))
				+ (caRewardSprite != null ? caRewardSprite.getWidth() + CA_REWARD_GAP : 0);
			textWidth = Math.max(textWidth, caWidth);
		}

		for (String name : PanelData.MEGARARE_ITEM_NAMES)
		{
			textWidth = Math.max(textWidth, fm.stringWidth(name));
		}
		for (String name : PanelData.SUPERIOR_ITEM_NAMES)
		{
			textWidth = Math.max(textWidth, fm.stringWidth(name));
		}
		Dimension pvpSize = pvpRows.size(fm);
		int contentWidth = Math.max(Math.max(textWidth, pvpSize.width),
			Math.max(spriteRowWidth, superiorRowWidth));
		int separatorHeight = separatorHeight(SEPARATOR_PAD);
		int caHeight = caResult != null ? CA_ROW_HEIGHT : 0;
		int contentHeight = LEVEL_GAP_ABOVE + LEVEL_ROW_HEIGHT + LEVEL_GAP_BELOW
			+ SUBHEADER_HEIGHT + statsHeight + caHeight + mostKilledHeight + SECTION_GAP
			+ separatorHeight + slayerHeight
			+ separatorHeight + raidsHeight
			+ separatorHeight + SUBHEADER_HEIGHT + pvpSize.height;

		return new Dimension(contentWidth, contentHeight);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		itemHover.setHitBoxes(Collections.emptyList());
		List<TooltipItemHover.HitBox> hitBoxes = new ArrayList<>();
		int inset = getInset();
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int y = startY;

		// The combat level leads the card; PvM and PvP each follow under their own subheader.
		y = paintLevel(g2, inset, y + LEVEL_GAP_ABOVE) + LEVEL_GAP_BELOW;
		y = paintSubheader(g2, y, "PvM Summary");

		// CA Tier appears first when data is available.
		if (caResult != null)
		{
			int caTextY = y + fm.getAscent();
			int cx = inset + drawLabelValue(g2, fm, inset, caTextY, "CA Tier: ", tierDisplayName(caResult));
			if (caRewardSprite != null)
			{
				cx += CA_REWARD_GAP;
				int rewardY = y + (CA_ROW_HEIGHT - caRewardSprite.getHeight()) / 2 - 2;
				g2.drawImage(caRewardSprite, cx, rewardY, null);
			}
			y += CA_ROW_HEIGHT;
		}

		// Total Kills
		drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Total Kills: ", scoreText(totalKills));
		y += LINE_HEIGHT;

		// EHB (rates by TempleOSRS)
		drawLabelValue(g2, fm, inset, y + fm.getAscent(), "EHB: ", ehbText(ehb));
		y += LINE_HEIGHT;

		// Bosses Killed
		drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Bosses Killed: ",
			bossesValue(), completionColor(bossesWithKc, totalBosses));
		y += LINE_HEIGHT;

		// Logs Completed
		if (bossesCompleted >= 0)
		{
			drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Logs Completed: ",
				logsValue(), completionColor(bossesCompleted, bossesWithClog));
			y += LINE_HEIGHT;
		}

		// Most Killed closes the PvM rows, then a small gap before the divider like every section.
		if (mostKilled != null)
		{
			y += SECTION_GAP;
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Most Killed:", inset, y + fm.getAscent());
			y += LINE_HEIGHT;
			g2.setColor(Color.WHITE);
			g2.drawString(mostKilledLine(), inset, y + fm.getAscent());
			y += LINE_HEIGHT;
		}
		y += SECTION_GAP;

		// Separator: stats to Slayer.
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);

		y = paintSubheader(g2, y, "Slayer");
		paintSlayerLine(g2, fm, inset, y);
		y += LINE_HEIGHT * slayerRowCount();
		y += WEAPON_PAD;
		paintQuantitySpriteRow(g2, fm, inset, y, w - 2 * inset,
			superiorSprites, superiorCounts, WEAPON_SIZE, WEAPON_PAD);
		addRowHitBoxes(hitBoxes, 0, inset, y, w - 2 * inset,
			PanelData.SUPERIOR_ITEMS, PanelData.SUPERIOR_ITEM_NAMES, superiorCounts);
		y += WEAPON_SIZE;
		paintSectionHoverLine(g2, fm, w, y, 0);
		y += hoverRowHeight(fm);

		// Separator: Slayer to raids.
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);

		y = paintSubheader(g2, y, "Raids");
		for (int i = 0; i < 3; i++)
		{
			paintRaidLine(g2, fm, inset, y, RAID_LABELS[i], raidKc[i], raidObtained[i], raidTotal[i]);
			y += LINE_HEIGHT;
		}
		y += WEAPON_PAD;

		// Center the three weapon sprites.
		paintQuantitySpriteRow(g2, fm, inset, y, w - 2 * inset,
			weaponSprites, weaponCounts, WEAPON_SIZE, WEAPON_PAD);
		addRowHitBoxes(hitBoxes, 1, inset, y, w - 2 * inset,
			PanelData.MEGARARE_ITEM_IDS, PanelData.MEGARARE_ITEM_NAMES, weaponCounts);
		paintSectionHoverLine(g2, fm, w, y + WEAPON_SIZE, 1);
		y += WEAPON_SIZE + hoverRowHeight(fm);

		y = paintSeparator(g2, w, y, SEPARATOR_PAD);
		y = paintSubheader(g2, y, "PvP Summary");
		pvpRows.paint(g2, fm, inset, y);

		itemHover.setHitBoxes(hitBoxes);
	}

	/**
	 * Hover hit boxes matching paintQuantitySpriteRow's centered geometry,
	 * so the summary sprites hover-name and wiki-link like the grids do.
	 */
	private void addRowHitBoxes(List<TooltipItemHover.HitBox> hitBoxes, int section,
		int x, int y, int colWidth, int[] itemIds, String[] itemNames, int[] counts)
	{
		int count = Math.min(itemIds.length, counts.length);
		int spriteRowWidth = count * WEAPON_SIZE + (count - 1) * WEAPON_PAD;
		int startX = x + (colWidth - spriteRowWidth) / 2;
		for (int i = 0; i < count; i++)
		{
			int sx = startX + i * (WEAPON_SIZE + WEAPON_PAD);
			hitBoxes.add(new TooltipItemHover.HitBox(section, itemIds[i], itemNames[i],
				new Rectangle(sx, y, WEAPON_SIZE, WEAPON_SIZE), counts[i] > 0, 1));
		}
	}

	private void paintRaidLine(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, int kc, int obtained, int total)
	{
		int textY = y + fm.getAscent();
		int end = x + drawLabelValue(g2, fm, x, textY, label, scoreText(kc));

		// Progress rides alongside a real kc; a "--" raid stays dash-only.
		if (kc > 0 && obtained >= 0)
		{
			paintWrappedProgressCount(g2, fm, end, textY, obtained, total);
		}
	}

	private void paintSlayerLine(Graphics2D g2, FontMetrics fm, int x, int y)
	{
		// Three rows: XP and Rank from the hiscores, then clog progress when
		// a synced log is known.
		int textY = y + fm.getAscent();
		drawLabelValue(g2, fm, x, textY, "XP: ", slayerXpText(slayerXp));
		textY += LINE_HEIGHT;
		drawLabelValue(g2, fm, x, textY, "Rank: ", scoreText(slayerRank));
		if (slayerObtained >= 0)
		{
			textY += LINE_HEIGHT;
			drawLabelValue(g2, fm, x, textY, "Obtained: ", progressCountText(slayerObtained, slayerTotal),
				completionColor(slayerObtained, slayerTotal));
		}
	}

	private int slayerRowCount()
	{
		return slayerObtained >= 0 ? 3 : 2;
	}

	/**
	 * Solo summary only: the exact amount, never rounded. Max slayer xp is
	 * 200,000,000 and the summary has the width for it. The comparison side
	 * keeps the shared compact form, where column width is the constraint.
	 */
	/* package */ static String slayerXpText(long xp)
	{
		return scoreText(xp);
	}

	/** The combat icon and level in bold, exactly like the panel cell. */
	private int paintLevel(Graphics2D g2, int inset, int y)
	{
		int x = inset + iconWidth();
		if (combatIcon != null)
		{
			g2.drawImage(combatIcon, inset, y + (LEVEL_ROW_HEIGHT - combatIcon.getHeight(null)) / 2, null);
		}
		g2.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bfm = g2.getFontMetrics();
		g2.setColor(Color.WHITE);
		g2.drawString(combatText(combatLevel), x, y + (LEVEL_ROW_HEIGHT + bfm.getAscent() - bfm.getDescent()) / 2);
		g2.setFont(FontManager.getRunescapeSmallFont());
		return y + LEVEL_ROW_HEIGHT;
	}

	private int iconWidth()
	{
		return combatIcon != null ? combatIcon.getWidth(null) + LEVEL_ICON_GAP : 0;
	}

	/** Vanilla's hiscore formatter: up to three decimals, trailing zeros dropped. */
	static String combatText(double combatLevel)
	{
		return combatLevel > 0 ? QuantityFormatter.formatNumber(combatLevel) : "--";
	}

	private String bossesValue()
	{
		return bossesWithKc + "/" + totalBosses;
	}

	private String logsValue()
	{
		return bossesCompleted + "/" + bossesWithClog;
	}

	private String mostKilledLine()
	{
		return mostKilled + " (" + grouped(mostKilledKc) + ")";
	}

	private static int raidLineWidth(FontMetrics fm, String label, int kc, int obtained, int total)
	{
		int w = fm.stringWidth(label) + fm.stringWidth(scoreText(kc));
		if (kc > 0 && obtained >= 0)
		{
			w += wrappedProgressCountWidth(fm, obtained, total);
		}
		return w;
	}

}
