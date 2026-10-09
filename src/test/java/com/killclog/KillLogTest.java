package com.killclog;

import java.util.List;
import java.util.stream.Stream;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The chat lines are RuneLite's own test fixtures for these encounters. */
public class KillLogTest
{
	private Client client;
	private ConfigManager configManager;
	private KillLog log;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		configManager = mock(ConfigManager.class);
		log = new KillLog(configManager);
	}

	private void game(int tick, String message)
	{
		log.chat(client, ChatMessageType.GAMEMESSAGE, message, tick);
	}

	private void kept(String key, String tag, double seconds)
	{
		verify(configManager).setRSProfileConfiguration("killclog", "livepb." + key, seconds);
		verify(configManager).setRSProfileConfiguration("killclog", "livepbtag." + key, tag);
	}

	@Test
	public void theNightmaresTeamSizeComesFromItsMessage()
	{
		game(1, "Your Nightmare kill count is: <col=ff0000>1130</col>");
		game(1, "Team size: <col=ff0000>5 players</col> Fight duration: <col=ff0000>3:28</col> (new personal best)");
		kept("nightmare 5 players", "208.0|5||kill|1130", 208.0);

		// Not a new PB: the game's PB for that size, without this kill's count.
		game(9, "Your Nightmare kill count is: <col=ff0000>1,131</col>");
		game(9, "Team size: <col=ff0000>Solo</col> Fight duration: <col=ff0000>10:47</col>. Personal best: 8:44");
		kept("nightmare solo", "524.0|1||kill|", 524.0);
	}

	@Test
	public void theRaidsTakeTheirTeamSizeAndTheirChallengeTime()
	{
		log.chat(client, ChatMessageType.FRIENDSCHATNOTIFICATION, "<col=ef20ff>Congratulations - your raid is complete!</col><br>"
			+ "Team size: <col=ff0000>24+ players</col> Duration:</col> <col=ff0000>37:04.20</col> (new personal best)</col>>", 1);
		game(1, "Your completed Chambers of Xeric count is: <col=ff0000>51</col>.");
		kept("chambers of xeric 24+ players", "2224.2|||overall|51", 2224.2);

		when(client.getVarbitValue(VarbitID.TOB_CLIENT_P0)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TOB_CLIENT_P1)).thenReturn(15);
		game(5, "Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>2:42.0</col><br>"
			+ "Theatre of Blood completion time: <col=ff0000>17:00.20</col> (new personal best)");
		game(5, "Theatre of Blood total completion time: <col=ff0000>24:40.20</col>. Personal best: 20:45.00");
		game(5, "Your completed Theatre of Blood count is: <col=ff0000>73</col>.");
		kept("theatre of blood 2 players", "1020.2|2||challenge|73", 1020.2);

		when(client.getVarbitValue(VarbitID.TOA_CLIENT_P0)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL)).thenReturn(300);
		game(9, "Challenge complete: The Wardens. Duration: <col=ef1020>8:30</col><br>"
			+ "Tombs of Amascut challenge completion time: <col=ef1020>8:31</col> (new personal best)");
		game(9, "Tombs of Amascut total completion time: <col=ef1020>0:01</col> (new personal best)");
		game(9, "Your completed Tombs of Amascut count is: <col=ff0000>1</col>.");
		kept("tombs of amascut solo", "511.0|1|300|challenge|1", 511.0);

		// A PB set earlier: its raid level and kill count aren't this run's.
		game(20, "Challenge complete: The Wardens. Duration: <col=ef1020>9:40</col><br>"
			+ "Tombs of Amascut: Expert Mode challenge completion time: <col=ef1020>9:40</col>. Personal best: 8:31");
		game(20, "Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>2</col>.");
		kept("tombs of amascut expert mode solo", "511.0|1||challenge|", 511.0);
	}

	@Test
	public void aTimeBeforeItsKillCountAndAPbAlreadyKept()
	{
		game(1, "Subdued in <col=ef1020>6:35</col> (new personal best).");
		game(2, "Your Tempoross kill count is: <col=ff0000>60</col>.");
		kept("tempoross", "395.0|||kill|60", 395.0);

		doReturn(55.8).when(configManager).getRSProfileConfiguration("killclog", "livepb.zulrah", double.class);
		game(7, "Your Zulrah kill count is: <col=ff0000>5</col>.");
		game(7, "Fight duration: <col=ff0000>0:59.20</col>. Personal best: 0:55.80");
		verify(configManager, never()).setRSProfileConfiguration("killclog", "livepb.zulrah", 55.8);
	}

	@Test
	public void aTimeNeverPairsWithAnotherKillsCount()
	{
		game(1, "Your Vorkath kill count is: <col=ff0000>500</col>.");
		game(4, "Fight duration: <col=ff0000>1:02.40</col> (new personal best).");
		game(9, "Fight duration: <col=ff0000>1:05.40</col>. Personal best: 1:02.40");
		game(12, "Your Vorkath kill count is: <col=ff0000>501</col>.");
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void titansAndYamaCountTheInstanceOnlyOnANewPb()
	{
		WorldView view = mock(WorldView.class);
		IndexedObjectSet<?> players = mock(IndexedObjectSet.class);
		doReturn(Stream.of(mock(Player.class), mock(Player.class))).when(players).stream();
		doReturn(players).when(view).players();
		when(client.getTopLevelWorldView()).thenReturn(view);

		game(1, "Your Yama success count is: <col=ff0000>12</col>.");
		game(1, "Fight duration: <col=ff0000>2:31.80</col>. Personal best: 2:10.20");
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());

		game(9, "Your Yama success count is: <col=ff0000>13</col>.");
		game(9, "Fight duration: <col=ff0000>2:04.20</col> (new personal best).");
		kept("yama 2 players", "124.2|2||kill|13", 124.2);
	}

	@Test
	public void theSyncReadsTheLabelsOfTheKeptTime()
	{
		when(configManager.getConfiguration("killclog", "p1", "livepbtag.nightmare 5 players")).thenReturn("208.0|5||kill|1130");
		SyncService.DetailedPb pb = KillLog.labeled(configManager, List.of("p1"), "the nightmare 5 players", 208.0);
		assertEquals("live", pb.source);
		assertEquals(Integer.valueOf(5), pb.team);
		assertNull(pb.scale);
		assertEquals("kill", pb.timer);
		assertEquals(Integer.valueOf(1130), pb.kc);

		SyncService.DetailedPb other = KillLog.labeled(configManager, List.of("p1"), "the nightmare 5 players", 200.4);
		assertNull(other.team);
		assertNull(other.kc);
	}
}
