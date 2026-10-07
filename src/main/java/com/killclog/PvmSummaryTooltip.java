package com.killclog;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import lombok.Setter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;
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
	// The combat level leads the card beside the combat cell's own icon, with room around it; the
	// CA tier leads the PvM rows the same way, its reward at the combat icon's size.
	static final int ICON_SIZE = 20;
	private static final String PVM_HEADER = "PvM";
	private static final String PVP_HEADER = "PvP";
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
	// Each row opens its raid's own popup.
	static final HiscoreSkill[] RAID_BOSSES = {HiscoreSkill.CHAMBERS_OF_XERIC,
		HiscoreSkill.THEATRE_OF_BLOOD, HiscoreSkill.TOMBS_OF_AMASCUT};
	private final int[] raidKc = new int[3];
	private final int[] raidObtained = {-1, -1, -1};
	private final int[] raidTotal = new int[3];
	@Nullable
	private BiConsumer<MouseEvent, HiscoreSkill> onOpenRaid;
	private final CardBody.ClickRows raidRows = new CardBody.ClickRows(this, (e, row) ->
	{
		if (onOpenRaid != null)
		{
			onOpenRaid.accept(e, RAID_BOSSES[row]);
			e.consume();
		}
	});

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

	/** Called with the raid when the player presses its row. */
	void setOnOpenRaid(@Nullable BiConsumer<MouseEvent, HiscoreSkill> onOpenRaid)
	{
		this.onOpenRaid = onOpenRaid;
	}

	int hoveredRaid()
	{
		return raidRows.hovered();
	}

	/** The raid row under a y coordinate, or -1. Rows are full width. */
	int raidAt(int y)
	{
		return raidRows.at(y);
	}

	@Override
	protected CardBody body()
	{
		// The combat level leads the card; PvM and PvP each follow under their own subheader.
		CardBody body = new CardBody()
			.add(CardBody.gap(LEVEL_GAP_ABOVE))
			.add(leadRow(combatIcon, combatText(combatLevel)))
			.add(CardBody.gap(LEVEL_GAP_BELOW))
			.add(CardBody.subheader(PVM_HEADER));
		// The CA tier leads the PvM rows, built like the combat level row above it, once one is held.
		if (hasTierRow())
		{
			body.add(CardBody.gap(LEVEL_GAP_ABOVE))
				.add(leadRow(caRewardSprite, tierDisplayName(caResult)))
				.add(CardBody.gap(LEVEL_GAP_BELOW));
		}
		body.add(CardBody.line("Total Kills: ", scoreText(totalKills)))
			.add(CardBody.line("EHB: ", ehbText(ehb)))
			.add(CardBody.line("Bosses Killed: ", bossesWithKc + "/" + totalBosses,
				completionColor(bossesWithKc, totalBosses)));
		if (bossesCompleted >= 0)
		{
			body.add(CardBody.line("Logs Completed: ", bossesCompleted + "/" + bossesWithClog,
				completionColor(bossesCompleted, bossesWithClog)));
		}
		// Most Killed closes the PvM rows, then a small gap before the divider like every section.
		if (mostKilled != null)
		{
			body.add(CardBody.gap(SECTION_GAP))
				.add(CardBody.text("Most Killed:", OSRS_ORANGE, false))
				.add(CardBody.text(mostKilled + " (" + grouped(mostKilledKc) + ")", Color.WHITE, true));
		}
		// Slayer: XP and Rank from the hiscores, then clog progress when a synced log is known.
		body.add(CardBody.gap(SECTION_GAP))
			.add(CardBody.separator(SEPARATOR_PAD))
			.add(CardBody.subheader("Slayer"))
			.add(CardBody.line("XP: ", slayerXpText(slayerXp)))
			.add(CardBody.line("Rank: ", scoreText(slayerRank)));
		if (slayerObtained >= 0)
		{
			body.add(CardBody.line("Obtained: ", progressCountText(slayerObtained, slayerTotal),
				completionColor(slayerObtained, slayerTotal)));
		}
		body.add(CardBody.gap(WEAPON_PAD))
			.add(CardBody.sprites(0, superiorSprites, PanelData.SUPERIOR_ITEMS, PanelData.SUPERIOR_ITEM_NAMES,
				superiorCounts, WEAPON_SIZE, WEAPON_PAD))
			.add(CardBody.hoverLine(0, PanelData.SUPERIOR_ITEM_NAMES))
			.add(CardBody.separator(SEPARATOR_PAD))
			.add(CardBody.subheader("Raids"));
		for (int i = 0; i < RAID_LABELS.length; i++)
		{
			body.add(raidRow(i));
		}
		return body.add(CardBody.gap(WEAPON_PAD))
			.add(CardBody.sprites(1, weaponSprites, PanelData.MEGARARE_ITEM_IDS, PanelData.MEGARARE_ITEM_NAMES,
				weaponCounts, WEAPON_SIZE, WEAPON_PAD))
			.add(CardBody.hoverLine(1, PanelData.MEGARARE_ITEM_NAMES))
			.add(CardBody.separator(SEPARATOR_PAD))
			.add(CardBody.subheader(PVP_HEADER))
			.add(pvpRows.part());
	}

	/** A raid's kills and collection progress; the row opens the raid's own popup. */
	private CardBody.Part raidRow(int row)
	{
		return CardBody.clickRow(raidRows, row, c ->
		{
			int width = c.fm.stringWidth(RAID_LABELS[row]) + c.fm.stringWidth(scoreText(raidKc[row]));
			return raidKc[row] > 0 && raidObtained[row] >= 0
				? width + wrappedProgressCountWidth(c.fm, raidObtained[row], raidTotal[row]) : width;
		}, (c, y, hovered) ->
		{
			Graphics2D g2 = c.g;
			int textY = y + c.fm.getAscent();
			// No pointer cursor in this UI: the hovered row answers in white instead.
			g2.setColor(hovered ? Color.WHITE : OSRS_ORANGE);
			g2.drawString(RAID_LABELS[row], c.inset(), textY);
			int end = c.inset() + c.fm.stringWidth(RAID_LABELS[row]);
			String kc = scoreText(raidKc[row]);
			g2.setColor(Color.WHITE);
			g2.drawString(kc, end, textY);
			end += c.fm.stringWidth(kc);
			// Progress rides alongside a real kc; a "--" raid stays dash-only.
			if (raidKc[row] > 0 && raidObtained[row] >= 0)
			{
				paintWrappedProgressCount(g2, c.fm, end, textY, raidObtained[row], raidTotal[row]);
			}
		});
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

	/**
	 * A lead row like the panel's combat cell: an icon centered in the icon column, then its text in
	 * bold white. The combat level and the CA tier share the column, so their text lines up.
	 */
	private CardBody.Part leadRow(@Nullable Image icon, String text)
	{
		return CardBody.row(LEVEL_ROW_HEIGHT, c -> iconColumnWidth() + c.bfm.stringWidth(text), (c, y) ->
		{
			Graphics2D g2 = c.g;
			if (icon != null)
			{
				int column = iconColumnWidth() - LEVEL_ICON_GAP;
				g2.drawImage(icon, c.inset() + (column - icon.getWidth(null)) / 2,
					y + (LEVEL_ROW_HEIGHT - icon.getHeight(null)) / 2, null);
			}
			g2.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics bfm = g2.getFontMetrics();
			g2.setColor(Color.WHITE);
			g2.drawString(text, c.inset() + iconColumnWidth(),
				y + (LEVEL_ROW_HEIGHT + bfm.getAscent() - bfm.getDescent()) / 2);
			g2.setFont(FontManager.getRunescapeSmallFont());
		});
	}

	/** The tier row waits for a tier: a player still short of Easy has nothing to lead with. */
	private boolean hasTierRow()
	{
		return caResult != null && caResult.getTier() != null;
	}

	/** The icon column the lead rows share: as wide as the wider icon, then the gap. */
	private int iconColumnWidth()
	{
		int width = Math.max(combatIcon != null ? combatIcon.getWidth(null) : 0,
			hasTierRow() && caRewardSprite != null ? caRewardSprite.getWidth() : 0);
		return width > 0 ? width + LEVEL_ICON_GAP : 0;
	}

	/** Vanilla's hiscore formatter: up to three decimals, trailing zeros dropped. */
	static String combatText(double combatLevel)
	{
		return combatLevel > 0 ? QuantityFormatter.formatNumber(combatLevel) : "--";
	}
}
