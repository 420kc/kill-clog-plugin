package com.killclog;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import net.runelite.client.hiscore.HiscoreSkill;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The boss list's search row behaves like the panel's search bar (2.6.3): a text cursor, and it lightens on hover. */
public class BossListSearchTest
{
	@Test
	public void theSearchRowShowsTheTextCursorAndLightensUnderTheMouse()
	{
		BossListView list = new BossListView(mock(TooltipController.class), mock(Cells.class), () -> false);
		JTextField field = TestViews.field(list, "search");
		JPanel row = (JPanel) field.getParent();
		assertEquals(Cursor.TEXT_CURSOR, field.getCursor().getType());
		// Selected text reads as it does in the panel's search bar: white on the brand orange.
		assertEquals(java.awt.Color.WHITE, field.getSelectedTextColor());
		assertEquals(ColorScheme.BRAND_ORANGE_TRANSPARENT, field.getSelectionColor());
		assertEquals(ColorScheme.DARKER_GRAY_COLOR, row.getBackground());

		for (MouseListener listener : field.getMouseListeners())
		{
			listener.mouseEntered(new MouseEvent(field, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
		}
		assertEquals(ColorScheme.DARK_GRAY_HOVER_COLOR, row.getBackground());
		for (MouseListener listener : field.getMouseListeners())
		{
			listener.mouseExited(new MouseEvent(field, MouseEvent.MOUSE_EXITED, 0, 0, 1, 1, 0, false));
		}
		assertEquals(ColorScheme.DARKER_GRAY_COLOR, row.getBackground());
	}

	@Test
	public void theSearchRowAndItsDividerMatchThePanelsSearchBarAndSeparator()
	{
		BossListView list = new BossListView(mock(TooltipController.class), mock(Cells.class), () -> false);
		JTextField field = TestViews.field(list, "search");
		JPanel row = (JPanel) field.getParent();
		assertEquals(30, row.getPreferredSize().height);
		assertEquals(30, row.getMaximumSize().height);
		java.awt.Container root = row.getParent();
		java.awt.Component divider = root.getComponent(1);
		assertEquals(7, divider.getPreferredSize().height);
		assertEquals(7, divider.getMaximumSize().height);
	}

	@Test
	public void aSearchWithADigitAlsoFindsBossesByTheirCountAsTheRowShowsIt()
	{
		// Dylan 10/13: "147k" finds Marni's Callisto; "420" on 420 kc's board finds every count with 420 in it.
		Map<HiscoreSkill, JLabel> grid = new EnumMap<>(HiscoreSkill.class);
		Cells cells = mock(Cells.class);
		for (HiscoreSkill boss : PanelData.BOSSES)
		{
			JLabel label = new JLabel("--");
			grid.put(boss, label);
			when(cells.getBossLabel(boss)).thenReturn(label);
		}
		BossListView list = new BossListView(mock(TooltipController.class), cells, () -> true);
		grid.get(HiscoreSkill.CALLISTO).setText("147k");
		grid.get(HiscoreSkill.ARTIO).setText("1420");
		grid.get(HiscoreSkill.VORKATH).setText("420");
		grid.get(HiscoreSkill.ZULRAH).setText("4200");
		list.resyncAll();

		list.filter("147k");
		assertEquals(List.of("Callisto"), shown(list));
		list.filter("420");
		assertEquals(List.of("Artio", "Vorkath", "Zulrah"), shown(list));
		list.filter("142");
		assertEquals(List.of("Artio"), shown(list));
		// Letters alone keep to names, so "k" never brings every "147k".
		list.filter("k");
		assertEquals(false, shown(list).contains("Callisto"));
		// A count landing under a typed search joins it.
		JTextField field = TestViews.field(list, "search");
		field.setText("420");
		grid.get(HiscoreSkill.CALLISTO).setText("420");
		assertEquals(List.of("Artio", "Callisto", "Vorkath", "Zulrah"), shown(list));
	}

	private static List<String> shown(BossListView list)
	{
		List<String> names = new ArrayList<>();
		Map<HiscoreSkill, Object> rows = TestViews.field(list, "rows");
		rows.forEach((boss, row) ->
		{
			JPanel panel = TestViews.field(row, "panel");
			if (panel.isVisible())
			{
				names.add(boss.getName());
			}
		});
		names.sort(String::compareTo);
		return names;
	}
}
