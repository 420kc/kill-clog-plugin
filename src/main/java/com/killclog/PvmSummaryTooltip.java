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
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * PvM summary tooltip on the combat level cell.
 * Stats at top, then Slayer/Superiors and Raids/Mega Rares sections.
 */
public class PvmSummaryTooltip extends TitleTooltip
{
	private static final int WEAPON_SIZE = 28;
	private static final int WEAPON_PAD = 6;
	private static final int SEPARATOR_PAD = 2;
	private static final int MOST_KILLED_GAP = 4;
	private static final int SUBHEADER_HEIGHT = 16;
	private static final int CA_ROW_HEIGHT = 18;
	private static final int CA_REWARD_GAP = 3;

	private int combatLevel;
	private int totalKills;
	private int bossesWithKc;
	private int totalBosses;
	private int slayerLevel = -1;
	private long slayerXp = -1;
	private int slayerRank = -1;
	private int slayerObtained = -1;
	private int slayerTotal;
	private String mostKilled;
	private int mostKilledKc;

	private int bossesCompleted = -1;
	private int bossesWithClog;
	private double ehb = -1;

	private final BufferedImage[] weaponSprites = new BufferedImage[3];
	private final int[] weaponCounts = new int[3];
	private final BufferedImage[] superiorSprites = new BufferedImage[2];
	private final int[] superiorCounts = new int[2];
	private final TooltipItemHover itemHover = new TooltipItemHover(this);

	private int coxKc;
	private int tobKc;
	private int toaKc;
	private int coxObtained = -1;
	private int coxTotal;
	private int tobObtained = -1;
	private int tobTotal;
	private int toaObtained = -1;
	private int toaTotal;

	private CombatAchievementResult caResult;
	private BufferedImage caRewardSprite;

	public void setData(int combatLevel, int totalKills, int bossesWithKc, int totalBosses,
						String mostKilled, int mostKilledKc)
	{
		setTitle("PvM Summary");
		this.combatLevel = combatLevel;
		this.totalKills = totalKills;
		this.bossesWithKc = bossesWithKc;
		this.totalBosses = totalBosses;
		this.mostKilled = mostKilled;
		this.mostKilledKc = mostKilledKc;
	}

	public void setCompletion(int completed, int total)
	{
		this.bossesCompleted = completed;
		this.bossesWithClog = total;
	}

	public void setEhb(double ehb)
	{
		this.ehb = ehb;
	}

	@Override
	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		super.setWikiLinksEnabled(wikiLinksEnabled);
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
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

	private String tierDisplayName()
	{
		return tierDisplayName(caResult);
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
		slayerLevel = hiscoreResult != null
			? hiscoreResult.getSkillLevel(PanelData.SLAYER_CATEGORY) : -1;
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
		coxKc = Math.max(0, hiscoreResult.getKc(PanelData.COX_HISCORE))
			+ Math.max(0, hiscoreResult.getKc(PanelData.COX_HISCORE_HARD));
		tobKc = Math.max(0, hiscoreResult.getKc(PanelData.TOB_HISCORE))
			+ Math.max(0, hiscoreResult.getKc(PanelData.TOB_HISCORE_HARD));
		toaKc = Math.max(0, hiscoreResult.getKc(PanelData.TOA_HISCORE))
			+ Math.max(0, hiscoreResult.getKc(PanelData.TOA_HISCORE_HARD));

		if (clogResult != null)
		{
			int[] cox = ClogHelper.clogCounts(PanelData.COX_CATEGORY, clogResult);
			if (cox != null)
			{
				coxObtained = cox[0];
				coxTotal = cox[1];
			}
			int[] tob = ClogHelper.clogCounts(PanelData.TOB_CATEGORY, clogResult);
			if (tob != null)
			{
				tobObtained = tob[0];
				tobTotal = tob[1];
			}
			int[] toa = ClogHelper.clogCounts(PanelData.TOA_CATEGORY, clogResult);
			if (toa != null)
			{
				toaObtained = toa[0];
				toaTotal = toa[1];
			}
		}
	}

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());

		// Stats section.
		int statsLines = 4; // Combat, Total Kills, EHB, Bosses
		if (bossesCompleted >= 0) statsLines++;
		int statsHeight = LINE_HEIGHT * statsLines;

		// Most killed section
		int mostKilledHeight = 0;
		if (mostKilled != null)
		{
			mostKilledHeight = MOST_KILLED_GAP + LINE_HEIGHT * 2;
		}

		int spriteRowWidth = 3 * WEAPON_SIZE + 2 * WEAPON_PAD;
		int superiorRowWidth = 2 * WEAPON_SIZE + WEAPON_PAD;

		// Slayer section.
		int slayerHeight = SUBHEADER_HEIGHT + LINE_HEIGHT * slayerRowCount()
			+ WEAPON_PAD + WEAPON_SIZE;

		// Raids section.
		int raidsHeight = SUBHEADER_HEIGHT + LINE_HEIGHT * 3
			+ WEAPON_PAD + WEAPON_SIZE;

		// Width: measure the real rendered strings, never a placeholder.
		int textWidth = 0;
		textWidth = Math.max(textWidth, fm.stringWidth("Combat: " + combatValue()));
		textWidth = Math.max(textWidth, fm.stringWidth("Total Kills: " + totalKillsValue()));
		textWidth = Math.max(textWidth, fm.stringWidth("EHB: " + ehbText(ehb)));
		textWidth = Math.max(textWidth, fm.stringWidth("XP: " + slayerXpText(slayerXp)));
		textWidth = Math.max(textWidth, fm.stringWidth("Rank: " + slayerRankValue()));
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
		textWidth = Math.max(textWidth, bfm.stringWidth("Slayer"));
		textWidth = Math.max(textWidth, bfm.stringWidth("Raids"));
		textWidth = Math.max(textWidth, raidLineWidth(fm, "CoX: ", coxKc, coxObtained, coxTotal));
		textWidth = Math.max(textWidth, raidLineWidth(fm, "ToB: ", tobKc, tobObtained, tobTotal));
		textWidth = Math.max(textWidth, raidLineWidth(fm, "ToA: ", toaKc, toaObtained, toaTotal));
		if (caResult != null)
		{
			int caWidth = fm.stringWidth("CA Tier: " + tierDisplayName())
				+ (caRewardSprite != null ? caRewardSprite.getWidth() + CA_REWARD_GAP : 0);
			textWidth = Math.max(textWidth, caWidth);
		}

		int contentWidth = Math.max(textWidth, Math.max(spriteRowWidth, superiorRowWidth));
		int separatorHeight = separatorHeight(SEPARATOR_PAD);
		int caHeight = caResult != null ? CA_ROW_HEIGHT : 0;
		int contentHeight = statsHeight + caHeight + mostKilledHeight
			+ separatorHeight + slayerHeight
			+ separatorHeight + raidsHeight;

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

		// CA Tier appears first when data is available.
		if (caResult != null)
		{
			int caTextY = y + fm.getAscent();
			String caLabel = "CA Tier: ";
			g2.setColor(OSRS_ORANGE);
			g2.drawString(caLabel, inset, caTextY);
			int cx = inset + fm.stringWidth(caLabel);
			String tierName = tierDisplayName();
			g2.setColor(Color.WHITE);
			g2.drawString(tierName, cx, caTextY);
			cx += fm.stringWidth(tierName);
			if (caRewardSprite != null)
			{
				cx += CA_REWARD_GAP;
				int rewardY = y + (CA_ROW_HEIGHT - caRewardSprite.getHeight()) / 2 - 2;
				g2.drawImage(caRewardSprite, cx, rewardY, null);
			}
			y += CA_ROW_HEIGHT;
		}

		// Combat
		drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Combat: ", combatValue());
		y += LINE_HEIGHT;

		// Total Kills
		drawLabelValue(g2, fm, inset, y + fm.getAscent(), "Total Kills: ", totalKillsValue());
		y += LINE_HEIGHT;

		// EHB (rates by TempleOSRS)
		drawLabelValue(g2, fm, inset, y + fm.getAscent(), "EHB: ", ehbText(ehb));
		y += LINE_HEIGHT;

		// Most Killed
		if (mostKilled != null)
		{
			y += MOST_KILLED_GAP;
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Most Killed:", inset, y + fm.getAscent());
			y += LINE_HEIGHT;
			g2.setColor(Color.WHITE);
			g2.drawString(mostKilledLine(), inset, y + fm.getAscent());
			y += LINE_HEIGHT;
		}

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

		// Separator: stats to Slayer.
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);

		// "Slayer" subheader.
		g2.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bfm = g2.getFontMetrics();
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Slayer", inset, y + bfm.getAscent());
		y += SUBHEADER_HEIGHT;

		// Slayer progress.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintSlayerLine(g2, fm, inset, y);
		y += LINE_HEIGHT * slayerRowCount();
		y += WEAPON_PAD;

		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintQuantitySpriteRow(g2, fm, inset, y, w - 2 * inset,
			superiorSprites, superiorCounts, WEAPON_SIZE, WEAPON_PAD);
		addRowHitBoxes(hitBoxes, 0, inset, y, w - 2 * inset,
			PanelData.SUPERIOR_ITEMS, PanelData.SUPERIOR_ITEM_NAMES, superiorCounts);
		y += WEAPON_SIZE;

		// Separator: Slayer to raids.
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);

		// "Raids" subheader.
		g2.setFont(FontManager.getRunescapeBoldFont());
		bfm = g2.getFontMetrics();
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Raids", inset, y + bfm.getAscent());
		y += SUBHEADER_HEIGHT;

		// Raid lines.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintRaidLine(g2, fm, inset, y, "CoX: ", coxKc, coxObtained, coxTotal);
		y += LINE_HEIGHT;
		paintRaidLine(g2, fm, inset, y, "ToB: ", tobKc, tobObtained, tobTotal);
		y += LINE_HEIGHT;
		paintRaidLine(g2, fm, inset, y, "ToA: ", toaKc, toaObtained, toaTotal);
		y += LINE_HEIGHT;
		y += WEAPON_PAD;

		// Center the three weapon sprites.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintQuantitySpriteRow(g2, fm, inset, y, w - 2 * inset,
			weaponSprites, weaponCounts, WEAPON_SIZE, WEAPON_PAD);
		addRowHitBoxes(hitBoxes, 1, inset, y, w - 2 * inset,
			PanelData.MEGARARE_ITEM_IDS, PanelData.MEGARARE_ITEM_NAMES, weaponCounts);

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
				new Rectangle(sx, y, WEAPON_SIZE, WEAPON_SIZE), counts[i] > 0));
		}
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

	private void paintRaidLine(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, int kc, int obtained, int total)
	{
		int textY = y + fm.getAscent();
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, x, textY);
		int lx = x + fm.stringWidth(label);

		String kcText = scoreText(kc);
		g2.setColor(Color.WHITE);
		g2.drawString(kcText, lx, textY);

		// Progress rides alongside a real kc; a "--" raid stays dash-only.
		if (kc > 0 && obtained >= 0)
		{
			paintWrappedProgressCount(g2, fm, lx + fm.stringWidth(kcText), textY, obtained, total);
		}
	}

	private void paintSlayerLine(Graphics2D g2, FontMetrics fm, int x, int y)
	{
		// Three rows: XP and Rank from the hiscores, then clog progress when
		// a synced log is known.
		int textY = y + fm.getAscent();
		g2.setColor(OSRS_ORANGE);
		g2.drawString("XP: ", x, textY);
		g2.setColor(Color.WHITE);
		g2.drawString(slayerXpText(slayerXp), x + fm.stringWidth("XP: "), textY);

		textY += LINE_HEIGHT;
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Rank: ", x, textY);
		g2.setColor(Color.WHITE);
		g2.drawString(slayerRankValue(), x + fm.stringWidth("Rank: "), textY);

		if (slayerObtained >= 0)
		{
			textY += LINE_HEIGHT;
			g2.setColor(OSRS_ORANGE);
			g2.drawString("Obtained: ", x, textY);
			g2.setColor(completionColor(slayerObtained, slayerTotal));
			g2.drawString(progressCountText(slayerObtained, slayerTotal),
				x + fm.stringWidth("Obtained: "), textY);
		}
	}

	private int slayerRowCount()
	{
		return slayerObtained >= 0 ? 3 : 2;
	}

	private String slayerRankValue()
	{
		return slayerRank > 0 ? String.format(Locale.US, "%,d", slayerRank) : "--";
	}

	/**
	 * Solo summary only: the exact amount, never rounded. Max slayer xp is
	 * 200,000,000 and the summary has the width for it. The comparison side
	 * keeps the shared compact form, where column width is the constraint.
	 */
	/* package */ static String slayerXpText(long xp)
	{
		return xp > 0 ? String.format(Locale.US, "%,d", xp) : "--";
	}

	private String combatValue()
	{
		return combatLevel > 0 ? String.valueOf(combatLevel) : "--";
	}

	private String totalKillsValue()
	{
		return scoreText(totalKills);
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
		return mostKilled + " (" + String.format(Locale.US, "%,d", mostKilledKc) + ")";
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
