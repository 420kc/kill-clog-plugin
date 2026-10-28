package com.killclog;

import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanelSearchBoxTest
{
	@Test
	public void theSearchBarTakesWhatTheCompareButtonAndLeagueSwitchLeave()
	{
		IconTextField search = new IconTextField();
		JLabel league = new JLabel();
		JLabel compare = new JLabel();
		int[] compareWidth = {40};
		JPanel row = PanelSearchBox.buildSearchRow(search, league, compare, () -> compareWidth[0]);
		row.setSize(220, PanelSearchBox.HEIGHT);

		row.doLayout();
		assertEquals(220 - 40 - 22, search.getWidth());
		assertEquals(220 - 40 - 22, league.getX());
		assertEquals(180, compare.getX());
		assertEquals(PanelSearchBox.HEIGHT, search.getHeight());

		// Off a League world the switch hides and the bar takes its room.
		league.setVisible(false);
		compareWidth[0] = 0;
		row.doLayout();
		assertEquals(220, search.getWidth());
		assertEquals(0, league.getWidth());
	}
}
