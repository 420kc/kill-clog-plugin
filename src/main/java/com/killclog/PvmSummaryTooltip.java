package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * PvM summary tooltip on the combat level cell.
 * Stats at top, most-killed, raids, then megarare weapon sprites at bottom.
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

	private static final Color QTY_COLOR = new Color(255, 255, 0);
	private static final Color QTY_SHADOW = new Color(0, 0, 0);

	private static final int TBOW_ID = 20997;
	private static final int SCYTHE_ID = 22486;
	private static final int SHADOW_ID = 27277;

	private int combatLevel;
	private int totalKills;
	private int bossesWithKc;
	private int totalBosses;
	private String mostKilled;
	private int mostKilledKc;

	private int bossesCompleted = -1;
	private int bossesWithClog;

	private final BufferedImage[] weaponSprites = new BufferedImage[3];
	private final int[] weaponCounts = new int[3];

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
		CombatAchievementTier tier = caResult != null ? caResult.getTier() : null;
		if (tier == null)
		{
			return "None";
		}
		String name = tier.name();
		return name.charAt(0) + name.substring(1).toLowerCase();
	}

	public void setMegarares(int tbowCount, int scytheCount,
		int shadowCount, ItemManager itemManager)
	{
		weaponCounts[0] = tbowCount;
		weaponCounts[1] = scytheCount;
		weaponCounts[2] = shadowCount;

		int[] ids = {TBOW_ID, SCYTHE_ID, SHADOW_ID};
		for (int i = 0; i < 3; i++)
		{
			BufferedImage img = itemManager.getImage(ids[i], 1, false);
			weaponSprites[i] = ImageUtil.resizeImage(img, WEAPON_SIZE, WEAPON_SIZE);
			if (img instanceof AsyncBufferedImage)
			{
				final int idx = i;
				((AsyncBufferedImage) img).onLoaded(() ->
					SwingUtilities.invokeLater(() ->
					{
						weaponSprites[idx] = ImageUtil.resizeImage(img, WEAPON_SIZE, WEAPON_SIZE);
						repaint();
					}));
			}
		}
	}

	public void setRaids(HiscoreResult hiscoreResult, ClogResult clogResult)
	{
		coxKc = Math.max(0, hiscoreResult.getKc("Chambers of Xeric"))
			+ Math.max(0, hiscoreResult.getKc("Chambers of Xeric: Challenge Mode"));
		tobKc = Math.max(0, hiscoreResult.getKc("Theatre of Blood"))
			+ Math.max(0, hiscoreResult.getKc("Theatre of Blood: Hard Mode"));
		toaKc = Math.max(0, hiscoreResult.getKc("Tombs of Amascut"))
			+ Math.max(0, hiscoreResult.getKc("Tombs of Amascut: Expert Mode"));

		if (clogResult != null)
		{
			int[] cox = ClogHelper.clogCounts("chambers_of_xeric", clogResult);
			if (cox != null)
			{
				coxObtained = cox[0];
				coxTotal = cox[1];
			}
			int[] tob = ClogHelper.clogCounts("theatre_of_blood", clogResult);
			if (tob != null)
			{
				tobObtained = tob[0];
				tobTotal = tob[1];
			}
			int[] toa = ClogHelper.clogCounts("tombs_of_amascut", clogResult);
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

		// Stats section
		int statsLines = 3; // Combat, Total Kills, Bosses
		if (bossesCompleted >= 0) statsLines++;
		int statsHeight = LINE_HEIGHT * statsLines;

		// Most killed section
		int mostKilledHeight = 0;
		if (mostKilled != null)
		{
			mostKilledHeight = MOST_KILLED_GAP + LINE_HEIGHT * 2;
		}

		// Raids section.
		int raidsHeight = SUBHEADER_HEIGHT + LINE_HEIGHT * 3;

		// Mega rares section.
		int spriteRowWidth = 3 * WEAPON_SIZE + 2 * WEAPON_PAD;
		int megarareHeight = SUBHEADER_HEIGHT + WEAPON_PAD + WEAPON_SIZE;

		// Width: measure the real rendered strings, never a placeholder.
		int textWidth = 0;
		textWidth = Math.max(textWidth, fm.stringWidth("Combat: " + combatValue()));
		textWidth = Math.max(textWidth, fm.stringWidth("Total Kills: " + totalKillsValue()));
		textWidth = Math.max(textWidth, fm.stringWidth("Bosses Killed: " + bossesValue()));
		if (bossesCompleted >= 0)
		{
			textWidth = Math.max(textWidth, fm.stringWidth("Logs Completed: " + logsValue()));
		}
		if (mostKilled != null)
		{
			textWidth = Math.max(textWidth, fm.stringWidth(mostKilledLine()));
		}
		textWidth = Math.max(textWidth, bfm.stringWidth("Raids"));
		textWidth = Math.max(textWidth, raidLineWidth(fm, "CoX: ", coxKc, coxObtained, coxTotal));
		textWidth = Math.max(textWidth, raidLineWidth(fm, "ToB: ", tobKc, tobObtained, tobTotal));
		textWidth = Math.max(textWidth, raidLineWidth(fm, "ToA: ", toaKc, toaObtained, toaTotal));
		textWidth = Math.max(textWidth, bfm.stringWidth("Mega Rares"));
		if (caResult != null)
		{
			int caWidth = fm.stringWidth("CA Tier: " + tierDisplayName())
				+ (caRewardSprite != null ? caRewardSprite.getWidth() + CA_REWARD_GAP : 0);
			textWidth = Math.max(textWidth, caWidth);
		}

		int contentWidth = Math.max(textWidth, spriteRowWidth);
		int separatorHeight = SEPARATOR_PAD + 1 + SEPARATOR_PAD;
		int caHeight = caResult != null ? CA_ROW_HEIGHT : 0;
		int contentHeight = statsHeight + caHeight + mostKilledHeight
			+ separatorHeight + raidsHeight
			+ separatorHeight + megarareHeight;

		return new Dimension(contentWidth, contentHeight);
	}

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
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

		// Separator: stats to raids.
		y += SEPARATOR_PAD;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, y, w - inset - 1, y);
		y += 1 + SEPARATOR_PAD;

		// "Raids" subheader.
		g2.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bfm = g2.getFontMetrics();
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

		// Separator: raids to megarares.
		y += SEPARATOR_PAD;
		g2.setColor(SEPARATOR_COLOR);
		g2.drawLine(inset, y, w - inset - 1, y);
		y += 1 + SEPARATOR_PAD;

		// "Mega Rares" subheader.
		g2.setFont(FontManager.getRunescapeBoldFont());
		bfm = g2.getFontMetrics();
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Mega Rares", inset, y + bfm.getAscent());
		y += SUBHEADER_HEIGHT + WEAPON_PAD;

		// Center the three weapon sprites.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		int spriteRowWidth = 3 * WEAPON_SIZE + 2 * WEAPON_PAD;
		int spriteStartX = inset + (w - 2 * inset - spriteRowWidth) / 2;
		for (int i = 0; i < 3; i++)
		{
			int sx = spriteStartX + i * (WEAPON_SIZE + WEAPON_PAD);
			BufferedImage sprite = weaponSprites[i];
			if (sprite != null)
			{
				boolean obtained = weaponCounts[i] > 0;
				g2.setComposite(obtained
					? AlphaComposite.SrcOver
					: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				g2.drawImage(sprite, sx, y, null);
				g2.setComposite(AlphaComposite.SrcOver);

				// Quantity overlay matches clog tooltips.
				if (obtained && weaponCounts[i] > 1)
				{
					String qtyText = String.valueOf(weaponCounts[i]);
					g2.setColor(QTY_SHADOW);
					g2.drawString(qtyText, sx + 1, y + fm.getAscent() + 1);
					g2.setColor(QTY_COLOR);
					g2.drawString(qtyText, sx, y + fm.getAscent());
				}
			}
		}
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
		return mostKilled + " (" + String.format("%,d", mostKilledKc) + ")";
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

	private void drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value)
	{
		drawLabelValue(g2, fm, x, y, label, value, Color.WHITE);
	}

	private void drawLabelValue(Graphics2D g2, FontMetrics fm, int x, int y,
		String label, String value, Color valueColor)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, x, y);
		g2.setColor(valueColor);
		g2.drawString(value, x + fm.stringWidth(label), y);
	}
}
