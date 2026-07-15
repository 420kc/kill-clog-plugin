package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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
 * 3-column PvM comparison tooltip. Left column: labels (orange).
 * Middle column: blue player values. Right column: red player values.
 * Inlay separators split the stat, Slayer/Superiors, and Raids/Mega Rares sections.
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

	// One player's column of the comparison; the tooltip holds a blue and a
	// red of these so every stat lives once instead of as name-mirrored twins.
	private static final class Side
	{
		String name;
		int combat;
		int totalKills;
		int bossesKc;
		int totalBosses;
		int slayerLevel = -1;
		long slayerXp = -1;
		int slayerRank = -1;
		int slayerObt = -1;
		int slayerTotal;
		String mostKilled;
		int mostKilledKc;
		int bossesCompleted = -1;
		int bossesWithClog;
		double ehb = -1;
		final BufferedImage[] weaponSprites = new BufferedImage[3];
		final int[] weaponCounts = new int[3];
		final BufferedImage[] superiorSprites = new BufferedImage[2];
		final int[] superiorCounts = new int[2];
		int coxKc;
		int tobKc;
		int toaKc;
		int coxObt = -1;
		int coxTotal;
		int tobObt = -1;
		int tobTotal;
		int toaObt = -1;
		int toaTotal;
		CombatAchievementResult ca;
		BufferedImage rewardSprite;
	}

	private final Side blue = new Side();
	private final Side red = new Side();
	private final TooltipItemHover itemHover = new TooltipItemHover(this);

	@Override
	protected Font getTitleFont()
	{
		return TITLE_FONT_SMALL;
	}

	@Override
	public void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		super.setWikiLinksEnabled(wikiLinksEnabled);
		itemHover.setWikiLinksEnabled(wikiLinksEnabled);
	}

	@Override
	protected String getHeaderRightText()
	{
		return itemHover.hoveredItemName();
	}

	@Override
	protected Color getHeaderRightColor()
	{
		return itemHover.hoveredItemObtained() ? CLOG_GREEN : CLOG_RED;
	}

	public void setBlueData(String name, int combat, int totalKills,
		int bossesKc, int totalBosses, String mostKilled, int mostKilledKc)
	{
		setTitle("PvM Summary");
		setData(blue, name, combat, totalKills, bossesKc, totalBosses, mostKilled, mostKilledKc);
	}

	public void setRedData(String name, int combat, int totalKills,
		int bossesKc, int totalBosses, String mostKilled, int mostKilledKc)
	{
		setData(red, name, combat, totalKills, bossesKc, totalBosses, mostKilled, mostKilledKc);
	}

	private static void setData(Side side, String name, int combat, int totalKills,
		int bossesKc, int totalBosses, String mostKilled, int mostKilledKc)
	{
		side.name = name;
		side.combat = combat;
		side.totalKills = totalKills;
		side.bossesKc = bossesKc;
		side.totalBosses = totalBosses;
		side.mostKilled = mostKilled;
		side.mostKilledKc = mostKilledKc;
	}

	public void setBlueEhb(double ehb)
	{
		blue.ehb = ehb;
	}

	public void setRedEhb(double ehb)
	{
		red.ehb = ehb;
	}

	public void setBlueCompletion(int completed, int withClog)
	{
		blue.bossesCompleted = completed;
		blue.bossesWithClog = withClog;
	}

	public void setRedCompletion(int completed, int withClog)
	{
		red.bossesCompleted = completed;
		red.bossesWithClog = withClog;
	}

	public void setBlueCa(CombatAchievementResult ca, BufferedImage rewardSprite)
	{
		blue.ca = ca;
		blue.rewardSprite = rewardSprite;
	}

	public void setRedCa(CombatAchievementResult ca, BufferedImage rewardSprite)
	{
		red.ca = ca;
		red.rewardSprite = rewardSprite;
	}

	private boolean hasCaRow()
	{
		return blue.ca != null || red.ca != null;
	}

	public void setBlueRaids(HiscoreResult hs, ClogResult clog)
	{
		setRaids(blue, hs, clog);
	}

	public void setRedRaids(HiscoreResult hs, ClogResult clog)
	{
		setRaids(red, hs, clog);
	}

	private static void setRaids(Side side, HiscoreResult hs, ClogResult clog)
	{
		side.coxKc = raidKc(hs, PanelData.COX_HISCORE, PanelData.COX_HISCORE_HARD);
		side.tobKc = raidKc(hs, PanelData.TOB_HISCORE, PanelData.TOB_HISCORE_HARD);
		side.toaKc = raidKc(hs, PanelData.TOA_HISCORE, PanelData.TOA_HISCORE_HARD);
		if (clog == null)
		{
			return;
		}
		int[] cox = ClogHelper.clogCounts(PanelData.COX_CATEGORY, clog);
		if (cox != null)
		{
			side.coxObt = cox[0];
			side.coxTotal = cox[1];
		}
		int[] tob = ClogHelper.clogCounts(PanelData.TOB_CATEGORY, clog);
		if (tob != null)
		{
			side.tobObt = tob[0];
			side.tobTotal = tob[1];
		}
		int[] toa = ClogHelper.clogCounts(PanelData.TOA_CATEGORY, clog);
		if (toa != null)
		{
			side.toaObt = toa[0];
			side.toaTotal = toa[1];
		}
	}

	public void setBlueMegarares(int tbow, int scythe, int shadow, ItemManager itemManager)
	{
		loadWeapons(blue.weaponCounts, blue.weaponSprites, tbow, scythe, shadow, itemManager);
	}

	public void setRedMegarares(int tbow, int scythe, int shadow, ItemManager itemManager)
	{
		loadWeapons(red.weaponCounts, red.weaponSprites, tbow, scythe, shadow, itemManager);
	}

	public void setBlueSlayer(HiscoreResult hs, ClogResult clog)
	{
		setSlayer(blue, hs, clog);
	}

	public void setRedSlayer(HiscoreResult hs, ClogResult clog)
	{
		setSlayer(red, hs, clog);
	}

	private static void setSlayer(Side side, HiscoreResult hs, ClogResult clog)
	{
		side.slayerLevel = hs != null ? hs.getSkillLevel(PanelData.SLAYER_CATEGORY) : -1;
		side.slayerXp = hs != null ? hs.getSkillXp(PanelData.SLAYER_CATEGORY) : -1;
		side.slayerRank = hs != null ? hs.getSkillRank(PanelData.SLAYER_CATEGORY) : -1;
		side.slayerObt = -1;
		side.slayerTotal = 0;
		int[] slayer = ClogHelper.clogCounts(PanelData.SLAYER_CATEGORY, clog);
		if (slayer != null)
		{
			side.slayerObt = slayer[0];
			side.slayerTotal = slayer[1];
		}
	}

	public void setBlueSuperiors(int imbuedHeart, int eternalGem, ItemManager itemManager)
	{
		loadSuperiors(blue.superiorCounts, blue.superiorSprites, imbuedHeart, eternalGem, itemManager);
	}

	public void setRedSuperiors(int imbuedHeart, int eternalGem, ItemManager itemManager)
	{
		loadSuperiors(red.superiorCounts, red.superiorSprites, imbuedHeart, eternalGem, itemManager);
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
		w = Math.max(w, fm.stringWidth("EHB"));
		w = Math.max(w, fm.stringWidth("Slayer"));
		w = Math.max(w, fm.stringWidth("Obtained"));
		w = Math.max(w, fm.stringWidth("Most Killed"));
		w = Math.max(w, fm.stringWidth("Bosses Killed"));
		w = Math.max(w, fm.stringWidth("Logs Completed"));
		w = Math.max(w, bfm.stringWidth("Raids"));
		w = Math.max(w, fm.stringWidth("CoX"));
		w = Math.max(w, fm.stringWidth("ToB"));
		w = Math.max(w, fm.stringWidth("ToA"));
		return w;
	}

	private int measureValueWidth(FontMetrics fm)
	{
		int w = 0;
		// Player names.
		if (blue.name != null) w = Math.max(w, fm.stringWidth(blue.name));
		if (red.name != null) w = Math.max(w, fm.stringWidth(red.name));

		// CA tier: actual tier name plus its reward sprite.
		if (hasCaRow())
		{
			w = Math.max(w, caValueWidth(fm, blue.ca, blue.rewardSprite));
			w = Math.max(w, caValueWidth(fm, red.ca, red.rewardSprite));
		}

		// Stats: combat and total kills as scores, bosses/logs as counts.
		int[] scores = {blue.combat, red.combat, blue.totalKills, red.totalKills};
		w = Math.max(w, widestValue(fm, scores, TitleTooltip::scoreText));
		w = Math.max(w, fm.stringWidth(ehbText(blue.ehb)));
		w = Math.max(w, fm.stringWidth(ehbText(red.ehb)));
		w = Math.max(w, fm.stringWidth(slayerText(blue.slayerLevel, blue.slayerXp,
			blue.slayerRank, blue.slayerObt, blue.slayerTotal)));
		w = Math.max(w, fm.stringWidth(slayerText(red.slayerLevel, red.slayerXp,
			red.slayerRank, red.slayerObt, red.slayerTotal)));
		w = Math.max(w, fm.stringWidth(bossesText(blue.bossesKc, blue.totalBosses)));
		w = Math.max(w, fm.stringWidth(bossesText(red.bossesKc, red.totalBosses)));
		w = Math.max(w, fm.stringWidth(logsText(blue.bossesCompleted, blue.bossesWithClog)));
		w = Math.max(w, fm.stringWidth(logsText(red.bossesCompleted, red.bossesWithClog)));

		// Most killed.
		w = Math.max(w, fm.stringWidth(mostKilledText(blue.mostKilled, blue.mostKilledKc)));
		w = Math.max(w, fm.stringWidth(mostKilledText(red.mostKilled, red.mostKilledKc)));

		// Raid kc with clog progress.
		w = Math.max(w, raidValueWidth(fm, blue.coxKc, blue.coxObt, blue.coxTotal));
		w = Math.max(w, raidValueWidth(fm, red.coxKc, red.coxObt, red.coxTotal));
		w = Math.max(w, raidValueWidth(fm, blue.tobKc, blue.tobObt, blue.tobTotal));
		w = Math.max(w, raidValueWidth(fm, red.tobKc, red.tobObt, red.tobTotal));
		w = Math.max(w, raidValueWidth(fm, blue.toaKc, blue.toaObt, blue.toaTotal));
		w = Math.max(w, raidValueWidth(fm, red.toaKc, red.toaObt, red.toaTotal));

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

		// Slayer section.
		h += LINE_HEIGHT * 2 + WEAPON_PAD + WEAPON_SIZE;

		// Separator.
		h += separatorHeight(SEPARATOR_PAD);

		// Raids section.
		h += LINE_HEIGHT * 4 + WEAPON_PAD + WEAPON_SIZE;

		return h;
	}

	// Painting.

	@Override
	protected void paintBody(Graphics2D g2, int w, int h, int startY)
	{
		itemHover.setHitBoxes(Collections.emptyList());
		List<TooltipItemHover.HitBox> hitBoxes = new ArrayList<>();
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
		g2.drawString(blue.name != null ? blue.name : "--", blueX, y + fm.getAscent());
		g2.setColor(COMPARE_RED);
		g2.drawString(red.name != null ? red.name : "--", redX, y + fm.getAscent());
		y += fm.getHeight() + HEADER_GAP;

		if (hasCaRow())
		{
			g2.setColor(OSRS_ORANGE);
			g2.drawString("CA Tier", labelX, y + fm.getAscent());
			paintCaValue(g2, fm, blueX, y, blue.ca, blue.rewardSprite, COMPARE_BLUE);
			paintCaValue(g2, fm, redX, y, red.ca, red.rewardSprite, COMPARE_RED);
			y += CA_ROW_HEIGHT;
		}

		// Combat.
		paintRow(g2, fm, labelX, blueX, redX, y, "Combat",
			scoreText(blue.combat), scoreText(red.combat),
			COMPARE_BLUE, COMPARE_RED);
		y += LINE_HEIGHT;

		// Total kills.
		paintRow(g2, fm, labelX, blueX, redX, y, "Total Kills",
			scoreText(blue.totalKills), scoreText(red.totalKills),
			COMPARE_BLUE, COMPARE_RED);
		y += LINE_HEIGHT;

		// EHB (rates by TempleOSRS).
		paintRow(g2, fm, labelX, blueX, redX, y, "EHB",
			ehbText(blue.ehb), ehbText(red.ehb),
			COMPARE_BLUE, COMPARE_RED);
		y += LINE_HEIGHT;

		// Most killed.
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Most Killed", labelX, y + fm.getAscent());
		g2.setColor(COMPARE_BLUE);
		g2.drawString(mostKilledText(blue.mostKilled, blue.mostKilledKc), blueX, y + fm.getAscent());
		g2.setColor(COMPARE_RED);
		g2.drawString(mostKilledText(red.mostKilled, red.mostKilledKc), redX, y + fm.getAscent());
		y += LINE_HEIGHT;

		// Bosses Killed.
		paintRow(g2, fm, labelX, blueX, redX, y, "Bosses Killed",
			bossesText(blue.bossesKc, blue.totalBosses),
			bossesText(red.bossesKc, red.totalBosses),
			completionColor(blue.bossesKc, blue.totalBosses),
			completionColor(red.bossesKc, red.totalBosses));
		y += LINE_HEIGHT;

		// Logs Completed.
		if (blue.bossesCompleted >= 0 || red.bossesCompleted >= 0)
		{
			paintRow(g2, fm, labelX, blueX, redX, y, "Logs Completed",
				logsText(blue.bossesCompleted, blue.bossesWithClog),
				logsText(red.bossesCompleted, red.bossesWithClog),
				blue.bossesCompleted >= 0 ? completionColor(blue.bossesCompleted, blue.bossesWithClog) : COMPARE_BLUE,
				red.bossesCompleted >= 0 ? completionColor(red.bossesCompleted, red.bossesWithClog) : COMPARE_RED);
		}
		y += LINE_HEIGHT;

		// Separator.
		y = paintSeparator(g2, w, y, SEPARATOR_PAD);

		// Slayer.
		g2.setFont(FontManager.getRunescapeBoldFont());
		g2.setColor(OSRS_ORANGE);
		g2.drawString("Slayer", labelX, y + g2.getFontMetrics().getAscent());
		y += LINE_HEIGHT;

		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintSlayerRow(g2, fm, labelX, blueX, redX, y);
		y += LINE_HEIGHT;
		y += WEAPON_PAD;

		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintQuantitySpriteRow(g2, fm, blueX, y, valueW,
			blue.superiorSprites, blue.superiorCounts, WEAPON_SIZE, WEAPON_PAD);
		paintQuantitySpriteRow(g2, fm, redX, y, valueW,
			red.superiorSprites, red.superiorCounts, WEAPON_SIZE, WEAPON_PAD);
		addRowHitBoxes(hitBoxes, 0, blueX, y, valueW,
			PanelData.SUPERIOR_ITEMS, PanelData.SUPERIOR_ITEM_NAMES, blue.superiorCounts);
		addRowHitBoxes(hitBoxes, 1, redX, y, valueW,
			PanelData.SUPERIOR_ITEMS, PanelData.SUPERIOR_ITEM_NAMES, red.superiorCounts);
		y += WEAPON_SIZE;

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
			blue.coxKc, blue.coxObt, blue.coxTotal,
			red.coxKc, red.coxObt, red.coxTotal);
		y += LINE_HEIGHT;
		paintRaidRow(g2, fm, labelX, blueX, redX, y, "ToB",
			blue.tobKc, blue.tobObt, blue.tobTotal,
			red.tobKc, red.tobObt, red.tobTotal);
		y += LINE_HEIGHT;
		paintRaidRow(g2, fm, labelX, blueX, redX, y, "ToA",
			blue.toaKc, blue.toaObt, blue.toaTotal,
			red.toaKc, red.toaObt, red.toaTotal);
		y += LINE_HEIGHT;
		y += WEAPON_PAD;

		// Weapon sprites.
		g2.setFont(FontManager.getRunescapeSmallFont());
		fm = g2.getFontMetrics();
		paintQuantitySpriteRow(g2, fm, blueX, y, valueW,
			blue.weaponSprites, blue.weaponCounts, WEAPON_SIZE, WEAPON_PAD);
		paintQuantitySpriteRow(g2, fm, redX, y, valueW,
			red.weaponSprites, red.weaponCounts, WEAPON_SIZE, WEAPON_PAD);
		addRowHitBoxes(hitBoxes, 2, blueX, y, valueW,
			PanelData.MEGARARE_ITEM_IDS, PanelData.MEGARARE_ITEM_NAMES, blue.weaponCounts);
		addRowHitBoxes(hitBoxes, 3, redX, y, valueW,
			PanelData.MEGARARE_ITEM_IDS, PanelData.MEGARARE_ITEM_NAMES, red.weaponCounts);

		itemHover.setHitBoxes(hitBoxes);
	}

	/**
	 * Hover hit boxes matching paintQuantitySpriteRow's centered geometry,
	 * so both players' sprites hover-name and wiki-link like the grids do.
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
		g2.drawString(slayerLabelText(), labelX, y + fm.getAscent());
		paintSlayerValue(g2, fm, blueX, y, blue.slayerLevel, blue.slayerXp,
			blue.slayerRank, blue.slayerObt, blue.slayerTotal, COMPARE_BLUE);
		paintSlayerValue(g2, fm, redX, y, red.slayerLevel, red.slayerXp,
			red.slayerRank, red.slayerObt, red.slayerTotal, COMPARE_RED);
	}

	private String slayerLabelText()
	{
		return blue.slayerObt >= 0 || red.slayerObt >= 0 ? "Obtained" : "Slayer";
	}

	private void paintSlayerValue(Graphics2D g2, FontMetrics fm, int x, int y,
		int level, long xp, int rank, int obtained, int total, Color playerColor)
	{
		int textY = y + fm.getAscent();
		String base = PvmSummaryTooltip.slayerBaseText(level, xp, rank, obtained);
		if (obtained >= 0)
		{
			String progress = progressCountText(obtained, total);
			g2.setColor(completionColor(obtained, total));
			g2.drawString(progress, x, textY);
			return;
		}

		g2.setColor("--".equals(base) ? dim(playerColor) : Color.WHITE);
		g2.drawString(base, x, textY);
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
		return name + " (" + String.format(Locale.US, "%,d", kc) + ")";
	}

	private static String bossesText(int kc, int total)
	{
		return kc + "/" + total;
	}

	private static String slayerText(int level, long xp, int rank, int obtained, int total)
	{
		String text = PvmSummaryTooltip.slayerBaseText(level, xp, rank, obtained);
		if (obtained >= 0)
		{
			return progressCountText(obtained, total);
		}
		return text;
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

	private void loadWeapons(int[] counts, BufferedImage[] sprites,
		int tbow, int scythe, int shadow, ItemManager itemManager)
	{
		counts[0] = tbow;
		counts[1] = scythe;
		counts[2] = shadow;

		loadItemSprites(PanelData.MEGARARE_ITEM_IDS, WEAPON_SIZE, sprites, itemManager);
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
