package com.killclog;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.annotation.Nullable;
import lombok.Setter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Combat Summary tooltip on the combat level cell: the combat level under the title, the band, then a scroll of PvM
 * (its rows and tiles), the raids' tiles, Slayer and its superiors, and PvP.
 */
public class PvmSummaryTooltip extends TitleTooltip
{
	private static final int WEAPON_PAD = 6;
	private static final int SECTION_GAP = 4;
	// The combat level leads the card beside the combat cell's own icon, with room around it; the CA tier's reward is
	// drawn at the same size, for its tile.
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

	private int[] weaponCounts = new int[PanelData.RAIDS.size()];
	@Nullable
	private TooltipItemSprites superiorSprites;
	private final int[] superiorCounts = new int[2];

	// The raids as the game's tiles, in raids.tsv's order (Dylan, 2026-11-07): each its megarare, faint until held, and
	// its log; with no log, its kills in both modes.
	private final int[] raidObtained = PanelData.RAIDS.stream().mapToInt(raid -> -1).toArray();
	private final int[] raidTotal = new int[PanelData.RAIDS.size()];
	// Each raid's kills, both modes together, which its tile shows while its log progress is unknown.
	private final int[] raidKills = new int[PanelData.RAIDS.size()];
	private final Image[] megarares = new Image[PanelData.RAIDS.size()];
	// Which tiles show: a raid that waits for its hiscore row (The Fractured Archive's, Dylan 2026-10-14) once the lookup
	// carries it, and before any lookup (the dormant panel) once the panel lists the raid (Dylan, 2026-11-08).
	private boolean[] raidOut = raidsOut(null);
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

	// Everything under the band scrolls, three lines a notch, in the window every long card shows (Dylan, 2026-11-07).
	private final CardBody.Scroll scroll = new CardBody.Scroll(this, CardBody.WINDOW, 3 * LINE_HEIGHT);

	@Setter
	private Image combatIcon;
	private CombatAchievementResult caResult;
	private BufferedImage caRewardSprite;
	// The Bosses tile's sprite, the Collection Log's Bosses tab's, and what a press on it opens.
	private Image bossesSprite;
	@Setter
	@Nullable
	private Consumer<MouseEvent> onOpenBosses;
	private static final int BOSSES = 1;
	// The most killed boss's tile: the boss, its icon and its log.
	@Nullable
	private HiscoreSkill mostKilledBoss;
	@Nullable
	private Image mostKilledIcon;
	private int mostKilledObtained = -1;
	private int mostKilledTotal;
	private static final int MOST_KILLED = 0;
	private final CardBody.ClickRows pvmRows = new CardBody.ClickRows(this, (e, key) ->
	{
		if (key == BOSSES && onOpenBosses != null)
		{
			onOpenBosses.accept(e);
			e.consume();
		}
		else if (key == MOST_KILLED && onOpenRaid != null && mostKilledBoss != null)
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

	/** Each raid's megarare held, in raids.tsv's order. */
	public void setMegarares(int[] counts, ItemManager itemManager)
	{
		bossesSprite = itemSprite(ClogSummaryTooltip.TAB_SPRITES.getOrDefault("Bosses", -1), itemManager);
		weaponCounts = counts;
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
		raidOut = raidsOut(hiscoreResult);
		for (int i = 0; i < raidKills.length; i++)
		{
			Raid raid = PanelData.RAIDS.get(i);
			raidKills[i] = Math.max(0, hiscoreResult.getKc(raid.getRow()))
				+ (raid.getHard() != null ? Math.max(0, hiscoreResult.getKc(raid.getHard().getName())) : 0);
			int[] counts = clogResult != null ? ClogHelper.clogCounts(raid.getPage(), clogResult) : null;
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
		// The title, the combat level and the band stay put (Dylan, 2026-11-07); PvM, Raids, Slayer and PvP scroll
		// under them, a long tab's height like the other cards.
		CardBody card = new CardBody()
			.add(CardBody.gap(LEVEL_GAP_ABOVE))
			.add(leadRow(combatIcon, combatText(combatLevel)))
			.add(CardBody.gap(LEVEL_GAP_BELOW));
		CardBody body = new CardBody().titled(PVM_HEADER)
			.add(CardBody.line("Total Kills: ", scoreText(totalKills)))
			.add(CardBody.line("EHB: ", ehbText(ehb)));
		// The PvM tiles: the CA tier once one is held, its points toward the next tier (a Grandmaster's bar full and
		// green); the most killed boss, its kills over its log, its name in the band, opening its card; and Bosses, the
		// bosses killed and their logs done, opening the Collection Log's Bosses tab.
		List<GameTile.Tile> pvm = new ArrayList<>();
		if (caResult != null && caResult.getTier() != null)
		{
			pvm.add(new GameTile.Tile(tierDisplayName(caResult), caRewardSprite, caResult.getTotalPoints(),
				caResult.nextTierPoints(), -1));
		}
		if (mostKilled != null)
		{
			pvm.add(new GameTile.Tile("Most Killed", mostKilledIcon, mostKilledObtained, mostKilledTotal,
				onOpenRaid != null && mostKilledBoss != null ? MOST_KILLED : -1)
				.count(grouped(mostKilledKc) + " kc").named(mostKilled));
		}
		String killed = "Killed: " + bossesWithKc + "/" + totalBosses;
		pvm.add(new GameTile.Tile("Bosses", bossesSprite, -1, 0, onOpenBosses != null ? BOSSES : -1).lines(
			bossesCompleted >= 0 ? new String[]{killed, "Logs: " + bossesCompleted + "/" + bossesWithClog} : new String[]{killed},
			new Color[]{completionColor(bossesWithKc, totalBosses), completionColor(bossesCompleted, bossesWithClog)}));
		body.add(CardBody.gap(SECTION_GAP)).add(GameTile.grid(pvmRows, pvm));
		// Raids lead the sections under the band.
		List<GameTile.Tile> raids = new ArrayList<>();
		for (int i = 0; i < raidOut.length; i++)
		{
			if (raidOut[i])
			{
				raids.add(new GameTile.Tile(PanelData.RAIDS.get(i).getTile(), megarares[i], raidObtained[i], raidTotal[i],
					onOpenRaid != null && raid(i) != null ? i : -1).faint(weaponCounts[i] <= 0)
					.named(PanelData.RAIDS.get(i).getMegarareName()).score(raidKills[i]));
			}
		}
		body.titled("Raids").add(GameTile.grid(raidRows, raids));
		// Slayer: XP and Rank from the hiscores, then clog progress when a synced log is known.
		body.titled("Slayer")
			.add(CardBody.line("XP: ", scoreText(slayerXp)))
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

	/** Whether each raid's tile shows, from a lookup's hiscores, or before any (null) from the panel's list. */
	private static boolean[] raidsOut(@Nullable HiscoreResult hiscores)
	{
		boolean[] out = new boolean[PanelData.RAIDS.size()];
		for (int i = 0; i < out.length; i++)
		{
			Raid raid = PanelData.RAIDS.get(i);
			out[i] = !raid.isWaits() || (hiscores != null ? hiscores.hasBossRow(raid.getRow()) : raid(i) != null);
		}
		return out;
	}

	/** The raid a row opens, null for one the panel has no popup for yet. */
	@Nullable
	private static HiscoreSkill raid(int row)
	{
		return PanelData.listed(PanelData.RAIDS.get(row).getSkill());
	}

	/** The combat level's row like the panel's combat cell: its icon centered in the icon column, then the level in bold white. */
	private CardBody.Part leadRow(@Nullable Image icon, String text)
	{
		int column = icon != null ? icon.getWidth(null) + LEVEL_ICON_GAP : 0;
		return CardBody.row(LEVEL_ROW_HEIGHT, c -> column + c.bfm.stringWidth(text), (c, y) ->
		{
			Graphics2D g2 = c.g;
			if (icon != null)
			{
				g2.drawImage(icon, c.inset(), y + (LEVEL_ROW_HEIGHT - icon.getHeight(null)) / 2, null);
			}
			g2.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics bfm = g2.getFontMetrics();
			g2.setColor(NativeTooltip.valueColor);
			g2.drawString(text, c.inset() + column,
				y + (LEVEL_ROW_HEIGHT + bfm.getAscent() - bfm.getDescent()) / 2);
			g2.setFont(FontManager.getRunescapeSmallFont());
		});
	}

	/** Vanilla's hiscore formatter: up to three decimals, trailing zeros dropped. */
	static String combatText(double combatLevel)
	{
		return combatLevel > 0 ? QuantityFormatter.formatNumber(combatLevel) : "--";
	}
}
