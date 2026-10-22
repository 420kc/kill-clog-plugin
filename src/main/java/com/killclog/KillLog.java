package com.killclog;

import java.util.Locale;
import java.util.Map;
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
 * other (the Grotesque Guardians' time waits up to ten for its count), so a time can never land on a later kill as
 * RuneLite's leftover one can. What it keeps is the PB the game
 * states at the kill, keyed by the team size the game gives (the raids, the Nightmare) or the players in the instance
 * (Royal Titans, Yama, The Fractured Archive), with the timer and, from a kill that set a new PB, the raid level and kill count. The sync
 * sends it as source "live".
 */
final class KillLog
{
	/* package */ static final String KEY_PREFIX = "livepb.";
	/* package */ static final String TAG_PREFIX = "livepbtag.";
	// RuneLite's window: a kill count and its time arrive on the same tick, now and then a tick or two apart.
	private static final int PAIR_TICKS = 2;
	// The Grotesque Guardians' kill count waits out Dusk's death animation, seconds after the time (none of 362 stored
	// Guardians times came from a kill, 10/22), so their time waits up to ten ticks for it, as Dink's pairing does.
	private static final int LATE_COUNT_TICKS = 10;
	private static final String LATE_COUNT_BOSS = "grotesque guardians";
	// No half of a kill seen: outside every window, so nothing pairs with it.
	private static final int NONE = -LATE_COUNT_TICKS - PAIR_TICKS - 1;
	private static final String TIME = "\\d+(?::\\d{2})+(?:\\.\\d+)?";
	private static final Pattern KILL_COUNT = Pattern.compile("Your (?:completion count for |subdued |completed )?"
		+ "(?<boss>.+?) (?:(?:kill|harvest|lap|completion|success) )?(?:count )?is: ?(?<kc>[0-9,]+)");
	private static final Pattern DURATION = Pattern.compile("(?i)(?:Team size: (?<team>Solo|\\d+(?:\\+|-\\d+)? players?) )?"
		+ "(?:(?:fight |challenge |corrupted challenge )?duration:|subdued in"
		+ "|(?<record>the fractured archive: )?(?<total>total )?completion time:) "
		+ "(?<time>" + TIME + ")(?:\\.? personal best: (?<pb>" + TIME + ")| \\(new personal best\\))");
	private static final int[] TOB_PARTY = {VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2,
		VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4};
	private static final int[] TOA_PARTY = {VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2,
		VarbitID.TOA_CLIENT_P3, VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6,
		VarbitID.TOA_CLIENT_P7};
	// The encounters with no team size in their message: the most players the instance counts toward one.
	private static final Map<String, Integer> INSTANCE_TEAMS = Map.of("royal titans", 2, "yama", 2, "fractured archive", 8);

	private final ConfigManager configManager;
	private String boss;
	private int kc;
	private int kcTick = NONE;
	private double pb;
	private boolean fresh;
	private String team;
	private int pbTick = NONE;
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
		// A raid's total time counts its waiting too; the challenge time in the message before it is the record. The
		// Fractured Archive's total is its record, as RuneLite keeps it.
		Matcher duration = DURATION.matcher(text);
		if (duration.find() && (duration.group("total") == null || duration.group("record") != null))
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
		// A count arriving after its time can come late; a time after its count can't (the count must be this tick's).
		int window = LATE_COUNT_BOSS.equals(boss) ? LATE_COUNT_TICKS : PAIR_TICKS;
		if (Math.abs(kcTick - pbTick) <= window && tick - kcTick <= PAIR_TICKS)
		{
			record(client);
			kcTick = pbTick = NONE;
		}
	}

	private void record(Client client)
	{
		boolean tob = boss.startsWith("theatre of blood");
		boolean toa = boss.startsWith("tombs of amascut");
		Integer most = INSTANCE_TEAMS.get(boss);
		boolean instanced = most != null;
		// These keep one PB at any team size, so only a kill that set a new one says which size set it.
		if (instanced && !fresh)
		{
			return;
		}
		String size = team;
		if (tob || toa || instanced)
		{
			int atTime = tob ? tobAtTime : toa ? toaAtTime : Math.min(most, playersAtTime);
			int now = tob ? party(client, TOB_PARTY) : toa ? party(client, TOA_PARTY) : Math.min(most, instancePlayers(client));
			size = now == atTime ? teamLabel(atTime) : null;
			if (size == null && instanced)
			{
				return;
			}
		}
		String key = size != null ? boss + " " + size.toLowerCase(Locale.ROOT) : boss;
		// A raid's kill states its team size's record, even when the party changed too late to say which size.
		retirePageReading(size != null || tob || toa);
		Double kept = configManager.getRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, KEY_PREFIX + key, double.class);
		// With precise timing off the game states the same record without its tenths: the kept time and its kill
		// count stay. A whole second kept and a tenths time stated is the same record told more exactly: kept anew.
		if (!fresh && kept != null && (kept == pb || pb == Math.rint(pb) && PbRecord.sameRecord(kept, pb)))
		{
			return;
		}
		String exact = size == null ? "" : size.equals("Solo") ? "1" : size.matches("\\d+ players?") ? size.split(" ")[0] : "";
		String timer = tob || toa ? "challenge"
			: boss.startsWith("chambers of xeric") || boss.equals("fractured archive") ? "overall" : "kill";
		// The same record told more exactly keeps the labels its kept time had: the kill count and raid level that set it.
		PbRecord before = !fresh && kept != null && PbRecord.sameRecord(kept, pb)
			? PbRecord.live(kept, configManager.getRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, TAG_PREFIX + key)) : null;
		String scale = fresh && toa ? String.valueOf(toaLevelAtTime) : label(before == null ? null : before.scale);
		String setAt = fresh ? String.valueOf(kc) : label(before == null ? null : before.kc);
		configManager.setRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, KEY_PREFIX + key, pb);
		configManager.setRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, TAG_PREFIX + key, PbRecord.tag(pb, exact,
			scale, timer, setAt));
	}

	private static String label(@Nullable Integer value)
	{
		return value == null ? "" : String.valueOf(value);
	}

	/**
	 * A kill states the game's record as it is now, a new PB or the one already held. A log page reading that differs
	 * from it is older (a PB set since, or set while the page was closed, on another client or in a release that kept
	 * no such rule), so it gives way until the page is next opened; it would otherwise win over this kill
	 * (PbRecord.pick). The same record shown without its tenths is the same record. A team size's kill states only
	 * that size's record, while the page shows the fastest of any size (a raid's, Dylan 10/21), so only a faster one
	 * makes the page's older.
	 */
	private void retirePageReading(boolean teamSized)
	{
		String page = ClogPbs.KEY_PREFIX + PersonalBests.encounter(boss);
		Double read = configManager.getRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, page, double.class);
		if (read != null && !PbRecord.sameRecord(read, pb) && (!teamSized || pb < read))
		{
			configManager.unsetRSProfileConfiguration(AdvLogPbs.CONFIG_GROUP, page);
		}
	}

	/** Drops a kill half seen: at logout, and when another account's messages arrive. */
	void reset()
	{
		kcTick = pbTick = NONE;
		boss = null;
		team = null;
	}

	private static int instancePlayers(Client client)
	{
		WorldView view = client.getTopLevelWorldView();
		return view == null ? 0 : (int) view.players().stream().count();
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
}
