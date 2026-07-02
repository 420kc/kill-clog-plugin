package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * 3-column PvM comparison tooltip. Left column: labels (orange).
 * Middle column: blue player values. Right column: red player values.
 * Inlay separators split the stat, raid, and mega-rare sections.
 */
public class ComparePvmSummaryTooltip extends TitleTooltip
{
	private static final int COL_GAP = 10;
	private static final int WEAPON_SIZE = 24;
	private static final int WEAPON_PAD = 4;
	private static final int SEPARATOR_PAD = 3;
	private static final int CA_ROW_HEIGHT = 16;
	private static final int CA_REWARD_GAP = 3;
	private static final int HEADER_GAP = 4;

	// Blue player values.
	private String blueName;
	private int blueCombat;
	private int blueTotalKills;
	private int blueBossesKc;
	private int blueTotalBosses;
	private int blueSlayerLevel = -1;
	private long blueSlayerXp = -1;
	private int blueSlayerRank = -1;
	private int blueSlayerObt = -1;
	private int blueSlayerTotal;
	private String blueMostKilled;
	private int blueMostKilledKc;
	private int blueBossesCompleted = -1;
	private int blueBossesWithClog;
	private final BufferedImage[] blueWeaponSprites = new BufferedImage[3];
	private final int[] blueWeaponCounts = new int[3];
	private final BufferedImage[] blueSuperiorSprites = new BufferedImage[2];
	private final int[] blueSuperiorCounts = new int[2];
	private int blueCoxKc, blueTobKc, blueToaKc;
	private int blueCoxObt = -1, blueCoxTotal;
	private int blueTobObt = -1, blueTobTotal;
	private int blueToaObt = -1, blueToaTotal;
	private CombatAchievementResult blueCa;
	private BufferedImage blueRewardSprite;

	// Red player values.
	private String redName;
	private int redCombat;
	private int redTotalKills;
	private int redBossesKc;
	private int redTotalBosses;
	private int redSlayerLevel = -1;
	private long redSlayerXp = -1;
	private int redSlayerRank = -1;
	private int redSlayerObt = -1;
	private int redSlayerTotal;
	private String redMostKilled;
	private int redMostKilledKc;
	private int redBossesCompleted = -1;
	private int redBossesWithClog;
	private final BufferedImage[] redWeaponSprites = new BufferedImage[3];
	private final int[] redWeaponCounts = new int[3];
	private final BufferedImage[] redSuperiorSprites = new BufferedImage[2];
	private final int[] redSuperiorCounts = new int[2];
	private int redCoxKc, redTobKc, redToaKc;
	private int redCoxObt = -1, redCoxTotal;
	private int redTobObt = -1, redTobTotal;
	private int redToaObt = -1, redToaTotal;
	private CombatAchievementResult redCa;
	private BufferedImage redRewardSprite;

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	public void setBlueData(String name, int combat, int totalKills,
		int bossesKc, int totalBosses, String mostKilled, int mostKilledKc)
	{
		setTitle("PvM Summary");
		this.blueName = name;
		this.blueCombat = combat;
		this.blueTotalKills = totalKills;
		this.blueBossesKc = bossesKc;
		this.blueTotalBosses = totalBosses;
		this.blueMostKilled = mostKilled;
		this.blueMostKilledKc = mostKilledKc;
	}

	public void setRedData(String name, int combat, int totalKills,
		int bossesKc, int totalBosses, String mostKilled, int mostKilledKc)
	{
		this.redName = name;
		this.redCombat = combat;
		this.redTotalKills = totalKills;
		this.redBossesKc = bossesKc;
		this.redTotalBosses = totalBosses;
		this.redMostKilled = mostKilled;
		this.redMostKilledKc = mostKilledKc;
	}

	public void setBlueCompletion(int completed, int withClog)
	{
		this.blueBossesCompleted = completed;
		this.blueBossesWithClog = withClog;
	}

	public void setRedCompletion(int completed, int withClog)
	{
		this.redBossesCompleted = completed;
		this.redBossesWithClog = withClog;
	}

	public void setBlueCa(CombatAchievementResult ca, BufferedImage rewardSprite)
	{
		this.blueCa = ca;
		this.blueRewardSprite = rewardSprite;
	}

	public void setRedCa(CombatAchievementResult ca, BufferedImage rewardSprite)
	{
		this.redCa = ca;
		this.redRewardSprite = rewardSprite;
	}

	private boolean hasCaRow()
	{
		return blueCa != null || redCa != null;
	}

	public void setBlueRaids(HiscoreResult hs, ClogResult clog)
	{
		blueCoxKc = raidKc(hs, "Chambers of Xeric", "Chambers of Xeric: Challenge Mode");
		blueTobKc = raidKc(hs, "Theatre of Blood", "Theatre of Blood: Hard Mode");
		blueToaKc = raidKc(hs, "Tombs of Amascut", "Tombs of Amascut: Expert Mode");
		loadRaidClog(clog, true);
	}

	public void setRedRaids(HiscoreResult hs, ClogResult clog)
	{
		redCoxKc = raidKc(hs, "Chambers of Xeric", "Chambers of Xeric: Challenge Mode");
		redTobKc = raidKc(hs, "Theatre of Blood", "Theatre of Blood: Hard Mode");
		redToaKc = raidKc(hs, "Tombs of Amascut", "Tombs of Amascut: Expert Mode");
		loadRaidClog(clog, false);
	}

	public void setBlueMegarares(int tbow, int scythe, int shadow, ItemManager itemManager)
	{
		loadWeapons(blueWeaponCounts, blueWeaponSprites, tbow, scythe, shadow, itemManager);
	}

	public void setRedMegarares(int tbow, int scythe, int shadow, ItemManager itemManager)
	{
		loadWeapons(redWeaponCounts, redWeaponSprites, tbow, scythe, shadow, itemManager);
	}

	public void setBlueSlayer(HiscoreResult hs, ClogResult clog)
	{
		blueSlayerLevel = hs != null ? hs.getSkillLevel(PanelData.SLAYER_CATEGORY) : -1;
		blueSlayerXp = hs != null ? hs.getSkillXp(PanelData.SLAYER_CATEGORY) : -1;
		blueSlayerRank = hs != null ? hs.getSkillRank(PanelData.SLAYER_CATEGORY) : -1;
		blueSlayerObt = -1;
		blueSlayerTotal = 0;
		int[] slayer = ClogHelper.clogCounts(PanelData.SLAYER_CATEGORY, clog);
		if (slayer != null)
		{
			blueSlayerObt = slayer[0];
			blueSlayerTotal = slayer[1];
		}
	}

	public void setRedSlayer(HiscoreResult hs, ClogResult clog)
	{
		redSlayerLevel = hs != null ? hs.getSkillLevel(PanelData.SLAYER_CATEGORY) : -1;
		redSlayerXp = hs != null ? hs.getSkillXp(PanelData.SLAYER_CATEGORY) : -1;
		redSlayerRank = hs != null ? hs.getSkillRank(PanelData.SLAYER_CATEGORY) : -1;
		redSlayerObt = -1;
		redSlayerTotal = 0;
		int[] slayer = ClogHelper.clogCounts(PanelData.SLAYER_CATEGORY, clog);
		if (slayer != null)
		{
			redSlayerObt = slayer[0];
			redSlayerTotal = slayer[1];
		}
	}

	public void setBlueSuperiors(int imbuedHeart, int eternalGem, ItemManager itemManager)
	{
		loadSuperiors(blueSuperiorCounts, blueSuperiorSprites, imbuedHeart, eternalGem, itemManager);
	}

	public void setRedSuperiors(int imbuedHeart, int eternalGem, ItemManager itemManager)
	{
		loadSuperiors(redSuperiorCounts, redSuperiorSprites, imbuedHeart, eternalGem, itemManager);
	}

	// Sizing.

	@Override
	protected Dimension getContentSize(int availableWidth)
	{
		FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());
		FontMetrics bfm = getFontMetrics(FontManager.getRunescapeBoldFont());

		int labelW = measureLabelWidth(fm, bfm);
		int valueW = measureValueWidth(fm);
		int totalWidth = labelW + COL_GAP + valueW + COL_GAP + valueW;
		int totalHeight = measureTableHeight(fm);

		return new Dimension(totalWidth, totalHeight);
	}

	private int measureLabelWidth(FontMetrics fm, FontMetrics bfm)
	{
		int w = 0;
		if (hasCaRow())
		{
			w = Math.max(w, fm.stringWidth("CA Tier"));
		}
		w = Math.max(w, fm.stringWidth("Combat"));
		w = Math.max(w, fm.stringWidth("Total Kills"));
		w = Math.max(w, fm.stringWidth("Slayer"));
		w = Math.max(w, fm.stringWidth("Most Killed"));
		w = Math.max(w, fm.stringWidth("Bosses Killed"));
		w = Math.max(w, fm.stringWidth("Logs Completed"));
		w = Math.max(w, bfm.stringWidth("Raids"));
		w = Math.max(w, fm.stringWidth("CoX"));
		w = Math.max(w, fm.stringWidth("ToB"));
		w = Math.max(w, fm.stringWidth("ToA"));
		w = Math.max(w, bfm.stringWidth("Mega Rares"));
		w = Math.max(w, bfm.stringWidth("Superiors"));
		return w;
	}

	private int measureValueWidth(FontMetrics fm)
	{
		int w = 0;
		// Player names.
		if (blueName != null) w = Math.max(w, fm.stringWidth(blueName));
		if (redName != null) w = Math.max(w, fm.stringWidth(redName));

		// CA tier: actual tier name plus its reward sprite.
		if (hasCaRow())
		{
			w = Math.max(w, caValueWidth(fm, blueCa, blueRewardSprite));
			w = Math.max(w, caValueWidth(fm, redCa, redRewardSprite));
		}

		// Stats: combat and total kills as scores, bosses/logs as counts.
		int[] scores = {blueCombat, redCombat, blueTotalKills, redTotalKills};
		w = Math.max(w, widestValue(fm, scores, TitleTooltip::scoreText));
		w = Math.max(w, fm.stringWidth(slayerText(blueSlayerLevel, blueSlayerXp,
			blueSlayerRank, blueSlayerObt, blueSlayerTotal)));
		w = Math.max(w, fm.stringWidth(slayerText(redSlayerLevel, redSlayerXp,
			redSlayerRank, redSlayerObt, redSlayerTotal)));
		w = Math.max(w, fm.stringWidth(bossesText(blueBossesKc, blueTotalBosses)));
		w = Math.max(w, fm.stringWidth(bossesText(redBossesKc, redTotalBosses)));
		w = Math.max(w, fm.stringWidth(logsText(blueBossesCompleted, blueBossesWithClog)));
		w = Math.max(w, fm.stringWidth(logsText(redBossesCompleted, redBossesWithClog)));

		// Most killed.
		w = Math.max(w, fm.stringWidth(mostKilledText(blueMostKilled, blueMostKilledKc)));
		w = Math.max(w, fm.stringWidth(mostKilledText(redMostKilled, redMostKilledKc)));

		// Raid kc with clog progress.
		w = Math.max(w, raidValueWidth(fm, blueCoxKc, blueCoxObt, blueCoxTotal));
		w = Math.max(w, raidValueWidth(fm, redCoxKc, redCoxObt, redCoxTotal));
		w = Math.max(w, raidValueWidth(fm, blueTobKc, blueTobObt, blueTobTotal));
		w = Math.max(w, raidValueWidth(fm, redTobKc, redTobObt, redTobTotal));
		w = Math.max(w, raidValueWidth(fm, blueToaKc, blueToaObt, blueToaTotal));
		w = Math.max(w, raidValueWidth(fm, redToaKc, redToaObt, redToaTotal));

		// Weapon sprite row.
		w = Math.max(w, 3 * WEAPON_SIZE + 2 * WEAPON_PAD);
		return w;
	}

	private int measureTableHeight(FontMetrics fm)
	{
		int h = 0;

		// Header row.
		h += fm.getHeight() + HEADER_GAP;

		if (hasCaRow())
		{
			h += CA_ROW_HEIGHT;
		}

		// Stats.
		h += LINE_HEIGHT * 5;

		// Most killed.
		h += LINE_HEIGHT;

		// Separator.
		h += separatorHeight(SEPARATOR_PAD);

		// Raids.
		h += LINE_HEIGHT + LINE_HEIGHT * 3;

		// Separator.
		h += separatorHeight(SEPARATOR_PAD);

		// Rare item sections.
		h += LINE_HEIGHT + WEAPON_PAD + WEAPON_SIZE
			+ WEAPON_PAD + LINE_HEIGHT + WEAPON_PAD + WEAPON_SIZE;

		return h;
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		int inset = getInset();
		int contentWidth = w - 2 * inset;

		FontMetrics fm = g2.getFontMetrics(FontManager.getRunescapeSmallFont());
		FontMetrics bfm = g2.getFontMetrics(FontManager.getRunescapeBoldFont());

		int labelW = measureLabelWidth(fm, bfm);
		int valueW = (contentWidth - labelW - COL_GAP * 2) / 2;
		int labelX = inset;
		int blueX = inset + labelW + COL_GAP;
		int redX = blueX + valueW + COL_GAP;

		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		int y = startY;

		// Header row.
		g2.setColor(COMPARE_BLUE);
		g2.drawString(blueName != null ? blueName : "--", blueX, y + fm.getAscent());
		g2.setColor(COMPARE_RED);
		g2.drawString(redName != null ? redName : "--", redX, y + fm.getAscent());
		y += fm.getHeight() + HEADER_GAP;

		if (hasCaRow())
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("CA Tier", labelX, y + fm.getAscent());
			paintCaValue(g2, fm, blueX, y, blueCa, blueRewardSprite, COMPARE_BLUE);
			paintCaValue(g2, fm, redX, y, redCa, redRewardSprite, COMPARE_RED);
			y += CA_ROW_HEIGHT;
		}

		// Combat.
		paintRow(g2, fm, labelX, blueX, redX, y, "Combat",
			scoreText(blueCombat), scoreText(redCombat),
			COMPARE_BLUE, COMPARE_RED);
		y += LINE_HEIGHT;

		// Total kills.
		paintRow(g2, fm, labelX, blueX, redX, y, "Total Kills",
			scoreText(blueTotalKills), scoreText(redTotalKills),
			COMPARE_BLUE, COMPARE_RED);
		y += LINE_HEIGHT;

		// Slayer.
		paintSlayerRow(g2, fm, labelX, blueX, redX, y);
		y += LINE_HEIGHT;

		// Most killed.
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Most Killed", labelX, y + fm.getAscent());
		g2.setColor(COMPARE_BLUE);
		g2.drawString(mostKilledText(blueMostKilled, blueMostKilledKc), blueX, y + fm.getAscent());
		g2.setColor(COMPARE_RED);
		g2.drawString(mostKilledText(redMostKilled, redMostKilledKc), redX, y + fm.getAscent());
		y += LINE_HEIGHT;

		// Bosses Killed.
		paintRow(g2, fm, labelX, blueX, redX, y, "Bosses Killed",
			bossesText(blueBossesKc, blueTotalBosses),
			bossesText(redBossesKc, redTotalBosses),
			completionColor(blueBossesKc, blueTotalBosses),
			completionColor(redBossesKc, redTotalBosses));
		y += LINE_HEIGHT;

		// Logs Completed.
		if (blueBossesCompleted >= 0 || redBossesCompleted >= 0)
		{
			paintRow(g2, fm, labelX, blueX, redX, y, "Logs Completed",
				logsText(blueBossesCompleted, blueBossesWithClog),
				logsText(redBossesCompleted, redBossesWithClog),
				blueBossesCompleted >= 0 ? completionColor(blueBossesCompleted, blueBossesWithClog) : COMPARE_BLUE,
				redBossesCompleted >= 0 ? completionColor(redBossesCompleted, redBossesWithClog) : COMPARE_RED);
		}
		y += LINE_HEIGHT;

		// Separator.
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);

		// Raids.
		g2.setFont(FontManager.getRunescapeBoldFont());
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Raids", labelX, y + g2.getFontMetrics().getAscent());
		y += LINE_HEIGHT;

		// Raid rows.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintRaidRow(g2, fm, labelX, blueX, redX, y, "CoX",
			blueCoxKc, blueCoxObt, blueCoxTotal,
			redCoxKc, redCoxObt, redCoxTotal);
		y += LINE_HEIGHT;
		paintRaidRow(g2, fm, labelX, blueX, redX, y, "ToB",
			blueTobKc, blueTobObt, blueTobTotal,
			redTobKc, redTobObt, redTobTotal);
		y += LINE_HEIGHT;
		paintRaidRow(g2, fm, labelX, blueX, redX, y, "ToA",
			blueToaKc, blueToaObt, blueToaTotal,
			redToaKc, redToaObt, redToaTotal);
		y += LINE_HEIGHT;

		// Separator.
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);

		// Mega rares.
		g2.setFont(FontManager.getRunescapeBoldFont());
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Mega Rares", labelX, y + g2.getFontMetrics().getAscent());
		y += LINE_HEIGHT + WEAPON_PAD;

		// Weapon sprites.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintQuantitySpriteRow(g2, fm, blueX, y, valueW,
			blueWeaponSprites, blueWeaponCounts, WEAPON_SIZE, WEAPON_PAD);
		paintQuantitySpriteRow(g2, fm, redX, y, valueW,
			redWeaponSprites, redWeaponCounts, WEAPON_SIZE, WEAPON_PAD);
		y += WEAPON_SIZE + WEAPON_PAD;

		// Superiors.
		g2.setFont(FontManager.getRunescapeBoldFont());
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Superiors", labelX, y + g2.getFontMetrics().getAscent());
		y += LINE_HEIGHT + WEAPON_PAD;

		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintQuantitySpriteRow(g2, fm, blueX, y, valueW,
			blueSuperiorSprites, blueSuperiorCounts, WEAPON_SIZE, WEAPON_PAD);
		paintQuantitySpriteRow(g2, fm, redX, y, valueW,
			redSuperiorSprites, redSuperiorCounts, WEAPON_SIZE, WEAPON_PAD);
	}

	// Row painters.

	private void paintRow(Graphics2D g2, FontMetrics fm,
		int labelX, int blueX, int redX, int y,
		String label, String blueVal, String redVal,
		Color blueColor, Color redColor)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, labelX, y + fm.getAscent());
		g2.setColor(compareValueColor(blueVal, blueColor));
		g2.drawString(blueVal, blueX, y + fm.getAscent());
		g2.setColor(compareValueColor(redVal, redColor));
		g2.drawString(redVal, redX, y + fm.getAscent());
	}

	private void paintSlayerRow(Graphics2D g2, FontMetrics fm,
		int labelX, int blueX, int redX, int y)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Slayer", labelX, y + fm.getAscent());
		paintSlayerValue(g2, fm, blueX, y, blueSlayerLevel, blueSlayerXp,
			blueSlayerRank, blueSlayerObt, blueSlayerTotal, COMPARE_BLUE);
		paintSlayerValue(g2, fm, redX, y, redSlayerLevel, redSlayerXp,
			redSlayerRank, redSlayerObt, redSlayerTotal, COMPARE_RED);
	}

	private void paintSlayerValue(Graphics2D g2, FontMetrics fm, int x, int y,
		int level, long xp, int rank, int obtained, int total, Color playerColor)
	{
		int textY = y + fm.getAscent();
		String base = slayerBaseText(level, xp, rank, obtained);
		g2.setColor("--".equals(base) ? dim(playerColor) : Color.WHITE);
		g2.drawString(base, x, textY);

		if (obtained >= 0)
		{
			paintWrappedProgressCount(g2, fm, x + fm.stringWidth(base), textY,
				obtained, total);
		}
	}

	private void paintCaValue(Graphics2D g2, FontMetrics fm, int x, int y,
		CombatAchievementResult ca, BufferedImage rewardSprite, Color playerColor)
	{
		String text = caValueText(ca);
		g2.setColor(compareValueColor(text, playerColor));
		g2.drawString(text, x, y + fm.getAscent());
		if (ca != null && rewardSprite != null)
		{
			int cx = x + fm.stringWidth(text) + CA_REWARD_GAP;
			int rewardY = y + (CA_ROW_HEIGHT - rewardSprite.getHeight()) / 2;
			g2.drawImage(rewardSprite, cx, rewardY, null);
		}
	}

	private void paintRaidRow(Graphics2D g2, FontMetrics fm,
		int labelX, int blueX, int redX, int y, String label,
		int blueKc, int blueObt, int blueTotal,
		int redKc, int redObt, int redTotal)
	{
		g2.setColor(OSRS_ORANGE);
		g2.drawString(label, labelX, y + fm.getAscent());
		paintRaidValue(g2, fm, blueX, y, blueKc, blueObt, blueTotal, COMPARE_BLUE);
		paintRaidValue(g2, fm, redX, y, redKc, redObt, redTotal, COMPARE_RED);
	}

	private void paintRaidValue(Graphics2D g2, FontMetrics fm, int x, int y,
		int kc, int obtained, int total, Color playerColor)
	{
		int textY = y + fm.getAscent();
		String kcText = scoreText(kc);
		g2.setColor(compareValueColor(kcText, playerColor));
		g2.drawString(kcText, x, textY);

		// Progress rides alongside a real kc; a "--" raid stays dash-only.
		if (kc > 0 && obtained >= 0)
		{
			paintWrappedProgressCount(g2, fm, x + fm.stringWidth(kcText), textY, obtained, total);
		}
	}

	// Data helpers.

	private static String mostKilledText(String name, int kc)
	{
		if (name == null) return "--";
		return name + " (" + String.format("%,d", kc) + ")";
	}

	private static String bossesText(int kc, int total)
	{
		return kc + "/" + total;
	}

	private static String slayerText(int level, long xp, int rank, int obtained, int total)
	{
		String text = slayerBaseText(level, xp, rank, obtained);
		if (obtained >= 0)
		{
			return text + wrappedProgressCountText(obtained, total);
		}
		return text;
	}

	private static String slayerBaseText(int level, long xp, int rank, int obtained)
	{
		if (obtained >= 0)
		{
			return level > 0 ? String.valueOf(level) : "--";
		}
		String xpText = SkillsTooltip.skillXpText(xp);
		if (!"--".equals(xpText))
		{
			return "XP: " + xpText + (rank > 0 ? rankTailText(rank) : "");
		}
		return level > 0 ? String.valueOf(level) : "--";
	}

	private static String logsText(int completed, int withClog)
	{
		return completed >= 0 ? completed + "/" + withClog : "--";
	}

	private static int raidValueWidth(FontMetrics fm, int kc, int obtained, int total)
	{
		int w = fm.stringWidth(scoreText(kc));
		if (kc > 0 && obtained >= 0)
		{
			w += wrappedProgressCountWidth(fm, obtained, total);
		}
		return w;
	}

	private static String caValueText(CombatAchievementResult ca)
	{
		return ca == null ? "--" : tierName(ca);
	}

	private static int caValueWidth(FontMetrics fm, CombatAchievementResult ca, BufferedImage reward)
	{
		int w = fm.stringWidth(caValueText(ca));
		if (ca != null && reward != null)
		{
			w += CA_REWARD_GAP + reward.getWidth();
		}
		return w;
	}

	private static String tierName(CombatAchievementResult ca)
	{
		return tierDisplayName(ca);
	}

	private static int raidKc(HiscoreResult hs, String normal, String hard)
	{
		if (hs == null)
		{
			return 0;
		}
		return Math.max(0, hs.getKc(normal)) + Math.max(0, hs.getKc(hard));
	}

	private void loadRaidClog(ClogResult clog, boolean blue)
	{
		if (clog == null) return;
		int[] cox = ClogHelper.clogCounts("chambers_of_xeric", clog);
		int[] tob = ClogHelper.clogCounts("theatre_of_blood", clog);
		int[] toa = ClogHelper.clogCounts("tombs_of_amascut", clog);
		if (blue)
		{
			if (cox != null)
			{
				blueCoxObt = cox[0];
				blueCoxTotal = cox[1];
			}
			if (tob != null)
			{
				blueTobObt = tob[0];
				blueTobTotal = tob[1];
			}
			if (toa != null)
			{
				blueToaObt = toa[0];
				blueToaTotal = toa[1];
			}
		}
		else
		{
			if (cox != null)
			{
				redCoxObt = cox[0];
				redCoxTotal = cox[1];
			}
			if (tob != null)
			{
				redTobObt = tob[0];
				redTobTotal = tob[1];
			}
			if (toa != null)
			{
				redToaObt = toa[0];
				redToaTotal = toa[1];
			}
		}
	}

	private void loadWeapons(int[] counts, BufferedImage[] sprites,
		int tbow, int scythe, int shadow, ItemManager itemManager)
	{
		counts[0] = tbow;
		counts[1] = scythe;
		counts[2] = shadow;

		loadItemSprites(MEGARARE_ITEM_IDS, WEAPON_SIZE, sprites, itemManager);
	}

	private void loadSuperiors(int[] counts, BufferedImage[] sprites,
		int imbuedHeart, int eternalGem, ItemManager itemManager)
	{
		counts[0] = imbuedHeart;
		counts[1] = eternalGem;

		loadItemSprites(PanelData.SUPERIOR_ITEMS, WEAPON_SIZE, sprites, itemManager);
	}

	// Collection-log counts use the native OSRS stoplight colors from TitleTooltip.
}
