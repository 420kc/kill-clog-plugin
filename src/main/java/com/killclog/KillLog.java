package com.killclog;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * Kill Clog's own record of the kills it sees. A kill-count message and its time pair only within two ticks of each
 * other, so a time can never land on a later kill as RuneLite's leftover one can. What it keeps is the PB the game
 * states at the kill, keyed by the team size the game gives (the raids, the Nightmare) or the players in the instance
 * (Royal Titans, Yama), with the timer and, from a kill that set a new PB, the raid level and kill count. The sync
 * sends it as source "live".
 */
final class KillLog
{
	/* package */ static final String KEY_PREFIX = "livepb.";
	private static final String TAG_PREFIX = "livepbtag.";
	// RuneLite's window: a kill count and its time arrive on the same tick, now and then a tick or two apart.
	private static final int PAIR_TICKS = 2;
	private static final String TIME = "\\d+(?::\\d{2})+(?:\\.\\d+)?";
	private static final Pattern KILL_COUNT = Pattern.compile("Your (?:completion count for |subdued |completed )?"
		+ "(?<boss>.+?) (?:(?:kill|harvest|lap|completion|success) )?(?:count )?is: ?(?<kc>[0-9,]+)");
	private static final Pattern DURATION = Pattern.compile("(?i)(?:Team size: (?<team>Solo|\\d+(?:\\+|-\\d+)? players?) )?"
		+ "(?:(?:fight |challenge |corrupted challenge )?duration:|subdued in|(?<total>total )?completion time:) "
		+ "(?<time>" + TIME + ")(?:\\.? personal best: (?<pb>" + TIME + ")| \\(new personal best\\))");
	private static final int[] TOB_PARTY = {VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2,
		VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4};
	private static final int[] TOA_PARTY = {VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2,
		VarbitID.TOA_CLIENT_P3, VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6,
		VarbitID.TOA_CLIENT_P7};

	private final ConfigManager configManager;
	private String boss;
	private int kc;
	private int kcTick = -PAIR_TICKS - 1;
	private double pb;
	private boolean fresh;
	private String team;
	private int pbTick = -PAIR_TICKS - 1;
	// What the game showed when the time arrived: the raid parties and level, and the players in the instance. A party
	// that changed before the kill count leaves the time without a team size (the site keeps such a time hidden).
	private int tobAtTime;
	private int toaAtTime;
	private int toaLevelAtTime;
	private int playersAtTime;
	// The account the half-seen kill belongs to: another account's message never pairs with it.
	private long account = -1;

	KillLog(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** A chat message; client thread. */
	void chat(Client client, ChatMessageType type, String message, int tick)
	{
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM
			&& type != ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			return;
		}
		long you = client.getAccountHash();
		if (you != account)
		{
			reset();
			account = you;
		}
		String text = Text.removeTags(message.replace("<br>", " ")).replaceAll("@[a-z_]+@", "");
		// A raid's total time counts its waiting too; the challenge time in the message before it is the record.
		Matcher duration = DURATION.matcher(text);
		if (duration.find() && duration.group("total") == null)
		{
			fresh = duration.group("pb") == null;
			pb = AdvLogPbs.timeToSeconds(duration.group(fresh ? "time" : "pb"));
			team = duration.group("team");
			pbTick = tick;
			tobAtTime = party(client, TOB_PARTY);
			toaAtTime = party(client, TOA_PARTY);
			toaLevelAtTime = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
			playersAtTime = instancePlayers(client);
		}
		Matcher count = KILL_COUNT.matcher(text);
		if (count.find() && !count.group("boss").contains("(Echo)"))
		{
			boss = count.group("boss").replace(":", "").toLowerCase(Locale.ROOT);
			kc = Integer.parseInt(count.group("kc").replace(",", ""));
			kcTick = tick;
		}
		if (Math.abs(kcTick - pbTick) <= PAIR_TICKS && tick - kcTick <= PAIR_TICKS)
		{
			record(client);
			kcTick = pbTick = -PAIR_TICKS - 1;
		}
	}

	private void record(Client client)
	{
		boolean tob = boss.startsWith("theatre of blood");
		boolean toa = boss.startsWith("tombs of amascut");
		boolean instanced = boss.equals("royal titans") || boss.equals("yama");
		// Titans and Yama keep one PB at any team size, so only a kill that set a new one says which size set it.
		if (instanced && !fresh)
		{
			return;
		}
		String size = team;
		if (tob || toa || instanced)
		{
			int atTime = tob ? tobAtTime : toa ? toaAtTime : playersAtTime;
			int now = tob ? party(client, TOB_PARTY) : toa ? party(client, TOA_PARTY) : instancePlayers(client);
			size = now == atTime ? teamLabel(atTime) : null;
			if (size == null && instanced)
			{
				return;
			}
		}
		String key = size != null ? boss + " " + size.toLowerCase(Locale.ROOT) : boss;
		Double kept = configManager.getRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, KEY_PREFIX + key, double.class);
		if (!fresh && kept != null && kept == pb)
		{
			return;
		}
		String exact = size == null ? "" : size.equals("Solo") ? "1" : size.matches("\\d+ players?") ? size.split(" ")[0] : "";
		String timer = tob || toa ? "challenge" : boss.startsWith("chambers of xeric") ? "overall" : "kill";
		configManager.setRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, KEY_PREFIX + key, pb);
		configManager.setRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, TAG_PREFIX + key, pb + "|" + exact + "|"
			+ (fresh && toa ? toaLevelAtTime : "") + "|" + timer + "|" + (fresh ? kc : ""));
	}

	/** Drops a kill half seen: at logout, and when another account's messages arrive. */
	void reset()
	{
		kcTick = pbTick = -PAIR_TICKS - 1;
		boss = null;
		team = null;
	}

	private static int instancePlayers(Client client)
	{
		WorldView view = client.getTopLevelWorldView();
		return view == null ? 0 : (int) Math.min(2, view.players().stream().count());
	}

	private static int party(Client client, int[] slots)
	{
		int count = 0;
		for (int slot : slots)
		{
			count += Math.min(client.getVarbitValue(slot), 1);
		}
		return count;
	}

	@Nullable
	private static String teamLabel(int players)
	{
		return players < 1 ? null : players == 1 ? "Solo" : players + " players";
	}

	/** A kept PB with what the game showed at the kill that set it, from a profile holding that same time. */
	static SyncService.DetailedPb labeled(ConfigManager configManager, List<String> profileKeys, String key, double seconds)
	{
		SyncService.DetailedPb pb = new SyncService.DetailedPb(seconds, "live");
		for (String profileKey : profileKeys)
		{
			for (String stored : PersonalBests.keyCandidates(key))
			{
				String tag = configManager.getConfiguration(AdvLogPbs.CONFIG_GROUP, profileKey, TAG_PREFIX + stored);
				String[] parts = tag != null ? tag.split("\\|", -1) : new String[0];
				try
				{
					if (parts.length == 5 && Double.parseDouble(parts[0]) == seconds)
					{
						pb.team = number(parts[1]);
						pb.scale = number(parts[2]);
						pb.timer = parts[3];
						pb.kc = number(parts[4]);
						return pb;
					}
				}
				catch (NumberFormatException e)
				{
					// A label that doesn't read is skipped: the time still syncs, unlabeled.
					pb.team = null;
					pb.scale = null;
					pb.timer = null;
					pb.kc = null;
				}
			}
		}
		return pb;
	}

	@Nullable
	private static Integer number(String text)
	{
		return text.isEmpty() ? null : Integer.valueOf(text);
	}
}
