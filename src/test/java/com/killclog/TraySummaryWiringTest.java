package com.killclog;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The real panel's summary cells hand their modals the right player's data. */
public class TraySummaryWiringTest
{
	private SkillDisplay skillDisplay = SkillDisplay.FIXED;
	private TooltipMode mode = TooltipMode.CLICK;
	private int previewsDismissed;
	private final KillClogConfig config = new KillClogConfig()
	{
		@Override
		public TooltipMode tooltipMode()
		{
			return mode;
		}

		@Override
		public SkillDisplay skillDisplay()
		{
			return skillDisplay;
		}
	};
	private final List<JToolTip> pinned = new ArrayList<>();
	private final List<JComponent> pinnedOn = new ArrayList<>();
	private KillClogPanel panel;
	private Cells cells;
	private JLabel clues;

	@Before
	public void createPanel() throws Exception
	{
		ClogService catalog = mock(ClogService.class);
		when(catalog.warmCatalog()).thenReturn(new CompletableFuture<>());
		SpriteManager sprites = mock(SpriteManager.class);
		doAnswer(invocation ->
		{
			Consumer<BufferedImage> callback = invocation.getArgument(2);
			callback.accept(new BufferedImage(25, 25, BufferedImage.TYPE_INT_ARGB));
			return null;
		}).when(sprites).getSpriteAsync(anyInt(), anyInt(), ArgumentMatchers.<Consumer<BufferedImage>>any());
		ClientThread clientThread = mock(ClientThread.class);
		ItemManager items = mock(ItemManager.class);
		when(items.getImage(anyInt(), anyInt(), anyBoolean())).thenAnswer(invocation ->
			new AsyncBufferedImage(clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB));
		edt(() ->
		{
			panel = new KillClogPanel(mock(HiscoreService.class), catalog,
				mock(RuneProfileService.class), mock(KillclogService.class),
				config, mock(ConfigManager.class), sprites,
				items, clientThread, new SkillIconManager(), mock(Client.class));
			cells = field(panel, "cells", Cells.class);
			clues = cells.getActivityLabels().get(HiscoreSkill.CLUE_SCROLL_ALL);
			// Nothing here is on a screen, so the pin is recorded instead of shown.
			set(cells, "tooltipController", new TooltipController(config)
			{
				@Override
				void pinTooltip(JComponent source, JPanel cell, JToolTip tip)
				{
					pinnedOn.add(source);
					pinned.add(tip);
				}

				@Override
				void dismissHoverPreview(MouseEvent event)
				{
					previewsDismissed++;
				}
			});
		});
	}

	@After
	public void stopPanel() throws Exception
	{
		edt(panel::shutdown);
	}

	@Test
	public void theClueSummaryReadsEachLineFromItsOwnPage() throws Exception
	{
		edt(() ->
		{
			seedSelf(clog("Seeded", 1, true));
			ClueSummaryTooltip tip = (ClueSummaryTooltip) clues.createToolTip();
			int[] obtained = field(tip, "obtained", int[].class);
			int[] total = field(tip, "total", int[].class);
			// Only the hard page is in this log: line 4, and nothing on its neighbours.
			assertEquals(2, obtained[4]);
			assertEquals(PanelData.THIRD_AGE_ITEMS.length + 1, total[4]);
			assertEquals(-1, obtained[3]);
			assertEquals(-1, obtained[5]);
			// The Mimic's one slot is the 3rd age ring, which this player has.
			assertEquals(1, obtained[7]);
			assertEquals(1, total[7]);

			seedSelf(clog("Seeded", 1, false));
			tip = (ClueSummaryTooltip) clues.createToolTip();
			assertEquals(0, field(tip, "obtained", int[].class)[7]);
		});
	}

	@Test
	public void everyRareRowOpensItsOwnCollectionOnTheCluesCell() throws Exception
	{
		edt(() ->
		{
			seedSelf(clog("Seeded", 1, true));
			for (int row = 0; row < PanelData.RARE_NAMES.length; row++)
			{
				ClueSummaryTooltip tip = (ClueSummaryTooltip) clues.createToolTip();
				openRare(tip, row);
				assertEquals(row + 1, pinned.size());
				assertSame(clues, pinnedOn.get(row));
				assertEquals(PanelData.RARE_NAMES[row], ((TitleTooltip) pinned.get(row)).getTitle());
			}
			// A pinned summary was already closed by the press; there is no preview to take down.
			assertEquals(0, previewsDismissed);
		});
	}

	@Test
	public void aRareRowTakesTheHoverPreviewDownAndKeepsTheCellOutlined() throws Exception
	{
		edt(() ->
		{
			seedSelf(clog("Seeded", 1, true));
			JPanel cell = (JPanel) clues.getParent();
			assertTrue(!(cell.getBorder() instanceof MatteBorder));

			mode = TooltipMode.HOVER;
			openRare((ClueSummaryTooltip) clues.createToolTip(), 1);
			// The preview is a window of its own: it goes the way any press takes it down.
			assertEquals(1, previewsDismissed);
			assertEquals(1, pinned.size());
			// The summary's closing cleared the cell's outline; the new modal's cell has it back.
			assertTrue(cell.getBorder() instanceof MatteBorder);
		});
	}

	@Test
	public void aComparisonPairShowsEachPlayersOwnClueProgress() throws Exception
	{
		edt(() ->
		{
			seedSelf(clog("Seeded", 1, false));
			seedRival(clog("Rival", 3, true));
			List<ClueSummaryTooltip> cards = new ArrayList<>();
			JToolTip pair = clues.createToolTip();
			for (Component child : pair.getComponents())
			{
				if (child instanceof ClueSummaryTooltip)
				{
					cards.add((ClueSummaryTooltip) child);
				}
			}
			assertEquals(2, cards.size());
			ClueSummaryTooltip blue = cards.get(0);
			ClueSummaryTooltip red = cards.get(1);
			assertEquals(1, field(blue, "obtained", int[].class)[4]);
			assertEquals(4, field(red, "obtained", int[].class)[4]);
			assertEquals(0, field(blue, "obtained", int[].class)[7]);
			assertEquals(1, field(red, "obtained", int[].class)[7]);
			// 3rd Age is the first rare row, and the Mimic's ring is not part of that set.
			assertEquals(1, field(blue, "rareObtained", int[].class)[0]);
			assertEquals(3, field(red, "rareObtained", int[].class)[0]);

			// Either side's row opens the pair, not one player's modal.
			openRare(red, 0);
			assertTrue(pinned.get(0) instanceof SideBySideTooltip);
		});
	}

	@Test
	public void theCombatSummaryCarriesThePlayersPvpScores() throws Exception
	{
		edt(() ->
		{
			seedSelf(clog("Seeded", 1, false));
			PvmSummaryTooltip tip = (PvmSummaryTooltip) panel.combatCell().createToolTip();
			PvpSummaryRows rows = field(tip, "pvpRows", PvpSummaryRows.class);
			assertArrayEquals(new int[]{-1, 1_234_567, -1, 42, -1}, field(rows, "scores", int[].class));
		});
	}

	@Test
	public void theSkillGridSitsWhereTheSettingSaysItDoes() throws Exception
	{
		edt(() ->
		{
			JPanel fixed = field(panel, "fixedSkillsHost", JPanel.class);
			JPanel tray = field(panel, "traySkillsHost", JPanel.class);
			assertEquals(1, fixed.getComponentCount());
			assertEquals(0, tray.getComponentCount());

			skillDisplay = SkillDisplay.TRAY;
			panel.onConfigChanged("skillDisplay");
			assertEquals(0, fixed.getComponentCount());
			assertEquals(1, tray.getComponentCount());
		});
	}

	@Test
	public void theRareCollectionsLineUpWithTheirItemLists()
	{
		assertArrayEquals(new String[]{PanelData.CLOG_THIRD_AGE, PanelData.CLOG_GILDED,
			PanelData.RARE_HARD, PanelData.RARE_ELITE, PanelData.RARE_MASTER}, PanelData.RARE_KEYS);
		assertSame(PanelData.THIRD_AGE_ITEMS, PanelData.RARE_ITEMS[0]);
		assertSame(PanelData.GILDED_ITEMS, PanelData.RARE_ITEMS[1]);
		assertSame(PanelData.HARD_RARE_ITEMS, PanelData.RARE_ITEMS[2]);
		assertSame(PanelData.ELITE_RARE_ITEMS, PanelData.RARE_ITEMS[3]);
		assertSame(PanelData.MASTER_RARE_ITEMS, PanelData.RARE_ITEMS[4]);
		assertEquals(PanelData.RARE_KEYS.length, PanelData.RARE_NAMES.length);
		assertEquals(PanelData.RARE_KEYS.length, PanelData.RARE_ICON_ITEM_IDS.length);
		assertEquals(PanelData.RARE_KEYS.length, ClueSummaryTooltip.RARE_LABELS.length);
	}

	@SuppressWarnings("unchecked")
	private static void openRare(ClueSummaryTooltip tip, int row)
	{
		MouseEvent press = new MouseEvent(tip, MouseEvent.MOUSE_PRESSED, 0L, 0, 5, 5, 1, false, MouseEvent.BUTTON1);
		((ObjIntConsumer<MouseEvent>) field(tip, "onOpenRare", ObjIntConsumer.class)).accept(press, row);
	}

	private void seedSelf(ClogResult clog)
	{
		HiscoreResult hiscore = hiscore();
		field(panel, "lookupSession", LookupSession.class).adoptState(hiscore, clog, null, "Seeded");
		cells.renderHiscore(hiscore);
		cells.renderClog(clog);
		cells.rebuildPrimaryTooltips("SomeoneElse");
	}

	private void seedRival(ClogResult clog)
	{
		try
		{
			ComparisonController comparison = field(panel, "comparison", ComparisonController.class);
			Constructor<?> ctor = Class.forName("com.killclog.ComparisonController$ComparedPlayer")
				.getDeclaredConstructors()[0];
			ctor.setAccessible(true);
			set(comparison, "compared", ctor.newInstance(1, hiscore(), clog, null, "Rival"));
			set(comparison, "comparisonMode", true);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private static HiscoreResult hiscore()
	{
		Map<String, Integer> activities = new HashMap<>();
		activities.put(HiscoreSkill.CLUE_SCROLL_HARD.getName(), 3);
		activities.put(HiscoreSkill.CLUE_SCROLL_ALL.getName(), 3);
		activities.put("Soul Wars Zeal", 1_234_567);
		activities.put("Bounty Hunter - Hunter", 42);
		return new HiscoreResult(AccountType.REGULAR, new HashMap<>(), Collections.emptyMap(),
			activities, Collections.emptyMap(), Collections.emptyMap(), 0, 0L, 0, 0);
	}

	/** A hard clue page of the 3rd age set plus its ring; the player owns some pieces and maybe the ring. */
	private static ClogResult clog(String name, int thirdAge, boolean ring)
	{
		List<Integer> page = new ArrayList<>();
		List<ClogResult.ClogItem> owned = new ArrayList<>();
		for (int id : PanelData.THIRD_AGE_ITEMS)
		{
			if (id != PanelData.THIRD_AGE_RING_ITEM_ID)
			{
				page.add(id);
				if (owned.size() < thirdAge)
				{
					owned.add(new ClogResult.ClogItem(id, 1, null));
				}
			}
		}
		page.add(PanelData.THIRD_AGE_RING_ITEM_ID);
		if (ring)
		{
			owned.add(new ClogResult.ClogItem(PanelData.THIRD_AGE_RING_ITEM_ID, 1, null));
		}
		Map<String, List<ClogResult.ClogItem>> obtained = new HashMap<>();
		obtained.put("hard_treasure_trails", owned);
		Map<String, List<Integer>> categories = new HashMap<>();
		categories.put("hard_treasure_trails", page);
		return new ClogResult(name, obtained, categories, new HashMap<>(), null, null);
	}

	private static <T> T field(Object target, String name, Class<T> type)
	{
		try
		{
			return type.cast(declared(target, name).get(target));
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private static void set(Object target, String name, Object value)
	{
		try
		{
			declared(target, name).set(target, value);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private static Field declared(Object target, String name) throws NoSuchFieldException
	{
		for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
		{
			try
			{
				Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			}
			catch (NoSuchFieldException e)
			{
				// Keep climbing: the field may belong to a parent.
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static void edt(Runnable action) throws Exception
	{
		SwingUtilities.invokeAndWait(action);
	}
}
