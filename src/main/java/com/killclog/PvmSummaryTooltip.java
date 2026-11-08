package com.killclog;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
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

	private final int[] weaponCounts = new int[4];
	@Nullable
	private TooltipItemSprites superiorSprites;
	private final int[] superiorCounts = new int[2];

	// CoX, ToB and ToA, then The Fractured Archive, as the game's tiles (Dylan, 2026-11-07): each its megarare, faint
	// until held, and its log. Their kills are on each raid's own card.
	private static final String[] RAID_NAMES = {"CoX", "ToB", "ToA", "TFA"};
	private static final String[] RAID_CATEGORIES = {PanelData.COX_CATEGORY, PanelData.TOB_CATEGORY,
		PanelData.TOA_CATEGORY, PanelData.TFA_CATEGORY};
	private final int[] raidObtained = {-1, -1, -1, -1};
	private final int[] raidTotal = new int[RAID_NAMES.length];
	// Each raid's kills, both modes together, which its tile shows while its log progress is unknown.
	private final int[] raidKills = new int[RAID_NAMES.length];
	private final Image[] megarares = new Image[RAID_NAMES.length];
	// The Fractured Archive's row waits for its hiscore row (Dylan, 2026-10-14): drawn from the lookup that carries it.
	private boolean tfaOut;
	@Nullable
	private BiConsumer<MouseEvent, HiscoreSkill> onOpenRaid;
	private final CardBody.ClickRows raidRows = new CardBody.ClickRows(this, (e, row) ->
	{
		// Each tile opens its raid's own popup, once the client knows the raid.
		if (onOpenRaid != null && raid(row) != null)
		{
			onOpenRaid.accept(e, raid(row));
			e.consume();
		}
	});

	// Everything under the anchored band scrolls, three lines a notch, in a window that keeps the whole card a long
	// tab's height: the level and PvM rows above the band already take a third of it (Dylan, 2026-10-08).
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, 16 * LINE_HEIGHT, 3 * LINE_HEIGHT);

	@Setter
	private Image combatIcon;
	private CombatAchievementResult caResult;
	private BufferedImage caRewardSprite;
	// The PvM tiles' own: the Bosses tab's sprite, the Collection Log, and the most killed boss's icon and log.
	private Image bossesSprite;
	private Image logsSprite;
	@Nullable
	private HiscoreSkill mostKilledBoss;
	@Nullable
	private Image mostKilledIcon;
	private int mostKilledObtained = -1;
	private int mostKilledTotal;
	private static final int MOST_KILLED = 0;
	private final CardBody.ClickRows pvmRows = new CardBody.ClickRows(this, (e, key) ->
	{
		if (onOpenRaid != null && mostKilledBoss != null)
		{
			onOpenRaid.accept(e, mostKilledBoss);
			e.consume();
		}
	});
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

	/** The most killed boss as its tile shows it: its icon, and its log when one is known (-1, unknown). */
	void setMostKilled(@Nullable HiscoreSkill boss, @Nullable Image icon, int obtained, int total)
	{
		mostKilledBoss = boss;
		mostKilledIcon = icon;
		mostKilledObtained = obtained;
		mostKilledTotal = total;
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
		int shadowCount, int obligatorCount, ItemManager itemManager)
	{
		bossesSprite = itemSprite(ClogSummaryTooltip.TAB_SPRITES.getOrDefault("Bosses", -1), itemManager);
		logsSprite = itemSprite(PanelData.COLLECTION_LOG_ITEM_ID, itemManager);
		weaponCounts[0] = tbowCount;
		weaponCounts[1] = scytheCount;
		weaponCounts[2] = shadowCount;
		weaponCounts[3] = obligatorCount;

		for (int i = 0; i < megarares.length; i++)
		{
			megarares[i] = itemSprite(PanelData.MEGARARE_ITEM_IDS[i], itemManager);
		}
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
		tfaOut = hiscoreResult.hasBossRow(PanelData.TFA_HISCORE);
		for (int i = 0; i < RAID_NAMES.length; i++)
		{
			raidKills[i] = Math.max(0, hiscoreResult.getKc(hasHardMode(i) ? PanelData.RAIDS[i].getName() : PanelData.TFA_HISCORE))
				+ (hasHardMode(i) ? Math.max(0, hiscoreResult.getKc(PanelData.RAID_HARD_MODES[i].getName())) : 0);
			int[] counts = clogResult != null ? ClogHelper.clogCounts(RAID_CATEGORIES[i], clogResult) : null;
			if (counts != null)
			{
				raidObtained[i] = counts[0];
				raidTotal[i] = counts[1];
			}
		}
	}

	/** Called with the raid when the player presses its tile. */
	void setOnOpenRaid(@Nullable BiConsumer<MouseEvent, HiscoreSkill> onOpenRaid)
	{
		this.onOpenRaid = onOpenRaid;
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
		card.add(CardBody.line("Total Kills: ", scoreText(totalKills)))
			.add(CardBody.line("EHB: ", ehbText(ehb)));
		// Then the PvM tiles (Dylan, 2026-11-07): the CA tier once one is held, the bosses killed, their logs done
		// once a log is known, and the most killed boss, its name in the band and its log on the bar.
		List<GameTile.Tile> pvm = new ArrayList<>();
		if (hasTierRow())
		{
			pvm.add(new GameTile.Tile(tierDisplayName(caResult), caRewardSprite, -1, 0, -1)
				.count(grouped(caResult.getTotalPoints()) + " pts"));
		}
		pvm.add(new GameTile.Tile("Bosses", bossesSprite, bossesWithKc, totalBosses, -1));
		if (bossesCompleted >= 0)
		{
			pvm.add(new GameTile.Tile("Boss Logs", logsSprite, bossesCompleted, bossesWithClog, -1));
		}
		if (mostKilled != null)
		{
			pvm.add(new GameTile.Tile("Most Killed", mostKilledIcon, mostKilledObtained, mostKilledTotal,
				onOpenRaid != null && mostKilledBoss != null ? MOST_KILLED : -1)
				.count(grouped(mostKilledKc) + " kc").item(-1, mostKilled));
		}
		card.add(CardBody.gap(SECTION_GAP)).add(GameTile.grid(pvmRows, pvm));
		// Raids lead the sections under the band.
		List<GameTile.Tile> raids = new ArrayList<>();
		for (int i = 0; i < raidRowCount(); i++)
		{
			raids.add(new GameTile.Tile(RAID_NAMES[i], megarares[i], raidObtained[i], raidTotal[i],
				onOpenRaid != null && raid(i) != null ? i : -1).faint(weaponCounts[i] <= 0)
				.item(PanelData.MEGARARE_ITEM_IDS[i], PanelData.MEGARARE_ITEM_NAMES[i]).score(raidKills[i]));
		}
		CardBody body = new CardBody().titled("Raids").add(GameTile.grid(raidRows, raids));
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

	/** The tiles drawn: CoX, ToB and ToA, then The Fractured Archive once the hiscores carry its row. */
	private int raidRowCount()
	{
		return tfaOut ? RAID_NAMES.length : PanelData.RAID_HARD_MODES.length;
	}

	/** CoX, ToB and ToA pair a hard mode; The Fractured Archive, the row after them, has none. */
	private static boolean hasHardMode(int row)
	{
		return row < PanelData.RAID_HARD_MODES.length;
	}

	/** The raid a row opens, null for one the panel has no popup for yet. */
	@Nullable
	private static HiscoreSkill raid(int row)
	{
		return hasHardMode(row) ? PanelData.RAIDS[row] : PanelData.listed(PanelData.TFA);
	}

	/** The exact amount, never rounded: max slayer xp is 200,000,000 and the summary has the width for it. */
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
			g2.setColor(NativeTooltip.valueColor);
			g2.drawString(text, c.inset() + iconColumnWidth(),
				y + (LEVEL_ROW_HEIGHT + bfm.getAscent() - bfm.getDescent()) / 2);
			g2.setFont(FontManager.getRunescapeSmallFont());
		});
	}

	/** The CA tile waits for a tier: a player still short of Easy has nothing to show there. */
	private boolean hasTierRow()
	{
		return caResult != null && caResult.getTier() != null;
	}

	/** The combat level row's icon column: its icon, then the gap. */
	private int iconColumnWidth()
	{
		int width = combatIcon != null ? combatIcon.getWidth(null) : 0;
		return width > 0 ? width + LEVEL_ICON_GAP : 0;
	}

	/** Vanilla's hiscore formatter: up to three decimals, trailing zeros dropped. */
	static String combatText(double combatLevel)
	{
		return combatLevel > 0 ? QuantityFormatter.formatNumber(combatLevel) : "--";
	}
}
