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
	private static final int WEAPON_PAD = 6;
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

	@Nullable
	private TooltipItemSprites weaponSprites;
	private final int[] weaponCounts = new int[3];
	@Nullable
	private TooltipItemSprites superiorSprites;
	private final int[] superiorCounts = new int[2];

	// CoX, ToB and ToA: normal and hard-mode kills as pairs, like a boss popup's KC and PB, then the raid's log.
	private static final String[] RAID_LABELS = {"CoX: ", "ToB: ", "ToA: "};
	private static final int PAIR_GAP = 8;
	private static final String[] RAID_CATEGORIES = {PanelData.COX_CATEGORY, PanelData.TOB_CATEGORY, PanelData.TOA_CATEGORY};
	private final int[] raidKc = new int[3];
	private final int[] hardKc = new int[3];
	private final int[] raidObtained = {-1, -1, -1};
	private final int[] raidTotal = new int[3];
	@Nullable
	private BiConsumer<MouseEvent, HiscoreSkill> onOpenRaid;
	private final CardBody.ClickRows raidRows = new CardBody.ClickRows(this, (e, row) ->
	{
		if (onOpenRaid != null)
		{
			// Each row opens its raid's own popup.
			onOpenRaid.accept(e, PanelData.RAIDS[row]);
			e.consume();
		}
	});

	// Everything under the anchored band scrolls once the card grows past a long tab's height, three lines a notch.
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.WINDOW, 3 * LINE_HEIGHT);

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

		weaponSprites = TooltipItemSprites.load(TooltipData.itemList(PanelData.MEGARARE_ITEM_IDS), null, itemManager,
			id -> 1, this);
	}

	public void setSuperiors(int imbuedHeartCount, int eternalGemCount,
		ItemManager itemManager)
	{
		superiorCounts[0] = imbuedHeartCount;
		superiorCounts[1] = eternalGemCount;

		superiorSprites = TooltipItemSprites.load(TooltipData.itemList(PanelData.SUPERIOR_ITEMS), null, itemManager,
			id -> 1, this);
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
			raidKc[i] = Math.max(0, hiscoreResult.getKc(PanelData.RAIDS[i].getName()));
			hardKc[i] = Math.max(0, hiscoreResult.getKc(PanelData.RAID_HARD_MODES[i].getName()));
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
	CardBody.Scroll scroll()
	{
		return scroll;
	}

	@Override
	protected CardBody body()
	{
		// The combat level leads the card; PvM and PvP each follow under their own subheader. A sprite's name shows
		// in the band over Raids, the first section with sprites, and everything from there scrolls.
		CardBody card = new CardBody()
			.add(CardBody.gap(LEVEL_GAP_ABOVE))
			.add(leadRow(combatIcon, combatText(combatLevel)))
			.add(CardBody.gap(LEVEL_GAP_BELOW))
			.add(CardBody.section(PVM_HEADER));
		// The CA tier leads the PvM rows, built like the combat level row above it, once one is held.
		if (hasTierRow())
		{
			card.add(CardBody.gap(LEVEL_GAP_ABOVE))
				.add(leadRow(caRewardSprite, tierDisplayName(caResult)))
				.add(CardBody.gap(LEVEL_GAP_BELOW));
		}
		card.add(CardBody.line("Total Kills: ", scoreText(totalKills)))
			.add(CardBody.line("EHB: ", ehbText(ehb)))
			.add(CardBody.line("Bosses Killed: ", bossesWithKc + "/" + totalBosses,
				completionColor(bossesWithKc, totalBosses)));
		if (bossesCompleted >= 0)
		{
			card.add(CardBody.line("Logs Completed: ", bossesCompleted + "/" + bossesWithClog,
				completionColor(bossesCompleted, bossesWithClog)));
		}
		// Most Killed closes the PvM rows.
		if (mostKilled != null)
		{
			card.add(CardBody.gap(SECTION_GAP))
				.add(CardBody.text("Most Killed:", OSRS_ORANGE, false))
				.add(CardBody.text(mostKilled + " (" + grouped(mostKilledKc) + ")", Color.WHITE, true));
		}
		// Raids lead the sections under the band, their megarares under the rows.
		CardBody body = new CardBody().titled("Raids");
		for (int i = 0; i < RAID_LABELS.length; i++)
		{
			body.add(raidRow(i));
		}
		body.add(CardBody.gap(WEAPON_PAD))
			.add(CardBody.sprites(1, weaponSprites, PanelData.MEGARARE_ITEM_IDS, PanelData.MEGARARE_ITEM_NAMES,
				weaponCounts, WEAPON_PAD));
		// Slayer: XP and Rank from the hiscores, then clog progress when a synced log is known.
		body.titled("Slayer")
			.add(CardBody.line("XP: ", slayerXpText(slayerXp)))
			.add(CardBody.line("Rank: ", scoreText(slayerRank)));
		if (slayerObtained >= 0)
		{
			body.add(CardBody.line("Obtained: ", progressCountText(slayerObtained, slayerTotal),
				completionColor(slayerObtained, slayerTotal)));
		}
		body.add(CardBody.gap(WEAPON_PAD))
			.add(CardBody.sprites(0, superiorSprites, PanelData.SUPERIOR_ITEMS, PanelData.SUPERIOR_ITEM_NAMES,
				superiorCounts, WEAPON_PAD))
			.titled(PVP_HEADER)
			.add(pvpRows.part());
		return card.add(CardBody.hoverBand()).add(CardBody.scroll(scroll, body.asPart()));
	}

	/** Progress rides alongside a real kc in either mode; a raid never run stays dash-only. */
	private boolean raidLog(int row)
	{
		return raidKc[row] + hardKc[row] > 0 && raidObtained[row] >= 0;
	}

	private static String hardLabel(int row)
	{
		return PanelData.RAID_HARD_LABELS[row] + ": ";
	}

	/** A raid's normal and hard-mode kills, then its log; the row opens the raid's own popup. */
	private CardBody.Part raidRow(int row)
	{
		return CardBody.clickRow(raidRows, row, c -> c.fm.stringWidth(RAID_LABELS[row] + scoreText(raidKc[row])) + PAIR_GAP
			+ c.fm.stringWidth(hardLabel(row) + scoreText(hardKc[row]))
			+ (raidLog(row) ? wrappedProgressCountWidth(c.fm, raidObtained[row], raidTotal[row]) : 0), (c, y, hovered) ->
		{
			int textY = y + c.fm.getAscent();
			// No pointer cursor in this UI: the hovered row answers in white instead.
			Color label = hovered ? Color.WHITE : OSRS_ORANGE;
			int end = c.inset() + drawLabelValue(c.g, c.fm, c.inset(), textY, RAID_LABELS[row], scoreText(raidKc[row]),
				label, Color.WHITE) + PAIR_GAP;
			end += drawLabelValue(c.g, c.fm, end, textY, hardLabel(row), scoreText(hardKc[row]), label, Color.WHITE);
			if (raidLog(row))
			{
				paintWrappedProgressCount(c.g, c.fm, end, textY, raidObtained[row], raidTotal[row]);
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
