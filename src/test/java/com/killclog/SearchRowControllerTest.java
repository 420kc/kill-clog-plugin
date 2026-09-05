package com.killclog;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchRowControllerTest
{
	@Test
	public void disablingComparisonHidesAndRestoresAvailableControl() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JLabel compareLabel = new JLabel();
			SearchRowController controller = controller(compareLabel,
				new AtomicBoolean(), new AtomicInteger());
			controller.install();
			controller.setCompareVisible(true);

			assertTrue(compareLabel.isVisible());
			assertTrue(controller.compareIconWidth() > 0);

			controller.setComparisonEnabled(false);

			assertFalse(compareLabel.isVisible());
			assertEquals(0, controller.compareIconWidth());
			controller.setComparisonEnabled(true);
			assertTrue(compareLabel.isVisible());
		});
	}

	@Test
	public void disablingComparisonExitsEntryAndActiveComparisonModes() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AtomicBoolean comparisonMode = new AtomicBoolean();
			AtomicInteger exits = new AtomicInteger();
			SearchRowController controller = controller(new JLabel(), comparisonMode, exits);
			controller.install();
			controller.setCompareVisible(true);
			controller.toggleEntry();
			assertTrue(controller.isCompareEntryMode());

			controller.setComparisonEnabled(false);
			assertFalse(controller.isCompareEntryMode());

			controller.setComparisonEnabled(true);
			comparisonMode.set(true);
			controller.setComparisonEnabled(false);
			assertEquals(1, exits.get());
		});
	}

	@Test
	public void disabledComparisonCannotEnterFromStaleInput() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SearchRowController controller = controller(new JLabel(),
				new AtomicBoolean(), new AtomicInteger());
			controller.install();
			controller.setComparisonEnabled(false);
			controller.toggleEntry();
			assertFalse(controller.isCompareEntryMode());
		});
	}

	private static SearchRowController controller(JLabel compareLabel,
		AtomicBoolean comparisonMode, AtomicInteger exits)
	{
		JPanel searchRow = new JPanel();
		IconTextField searchBar = new IconTextField();
		searchRow.add(searchBar);
		searchRow.add(compareLabel);
		return new SearchRowController(
			searchRow,
			searchBar,
			compareLabel,
			null,
			Color.LIGHT_GRAY,
			comparisonMode::get,
			() -> false,
			() ->
			{
			},
			() ->
			{
			},
			() ->
			{
				exits.incrementAndGet();
				comparisonMode.set(false);
			},
			() ->
			{
			},
			() ->
			{
			},
			() ->
			{
			});
	}
}
