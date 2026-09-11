package com.killclog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankSelectorLayoutTest
{
	@Test
	public void footerStaysOutsideScrollAtNativeWidthsAndRendersPreview() throws Exception
	{
		BufferedImage preview = new BufferedImage(242, 180, BufferedImage.TYPE_INT_ARGB);
		SwingUtilities.invokeAndWait(() ->
		{
			RankSelector selector = new RankSelector((name, table) -> new CompletableFuture<>(), RankSelectorLayoutTest::noop);
			HiscoreResult base = RankSelectorTest.result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
			selector.update(true, "Test Player", base, null, null);
			PluginPanel panel = new PluginPanel()
			{
			};
			panel.setPreferredSize(new Dimension(225, 1200));
			JPanel wrapper = panel.getWrappedPanel();
			wrapper.add(selector, BorderLayout.SOUTH);
			for (int width : new int[]{225, 242, 320})
			{
				wrapper.setSize(width, 500);
				wrapper.doLayout();
				selector.doLayout();
				assertSame(selector, ((BorderLayout) wrapper.getLayout()).getLayoutComponent(BorderLayout.SOUTH));
				assertEquals(32, selector.getHeight());
				assertEquals(500, selector.getY() + selector.getHeight());
				for (Component button : selector.getComponents())
				{
					assertEquals(3, button.getY());
					assertTrue(button.getX() >= 0 && button.getX() + button.getWidth() <= width);
				}
			}
			Graphics2D g = preview.createGraphics();
			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
			g.setFont(FontManager.getRunescapeSmallFont());
			RankLeaderboard[] selections = {RankLeaderboard.IRONMAN, RankLeaderboard.NORMAL, RankLeaderboard.HARDCORE};
			for (int i = 0; i < selections.length; i++)
			{
				selector.select(selections[i]);
				selector.setSize(242, 32);
				selector.doLayout();
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.drawString(selections[i].label + " ranks selected", 12, i * 60 + 17);
				Graphics2D row = (Graphics2D) g.create(0, i * 60 + 24, 242, 32);
				selector.printAll(row);
				row.dispose();
			}
			g.dispose();
			assertEquals("Normal Hiscores", ((JButton) selector.getComponent(0)).getToolTipText());
		});
		File output = new File("build/reports/leaderboard-selector-preview.png");
		assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
		assertTrue(ImageIO.write(preview, "png", output));
	}

	@Test
	public void footerContinuesScrollbarGutterWithoutChangingButtonLayout() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RankSelector selector = new RankSelector((name, table) -> new CompletableFuture<>(), RankSelectorLayoutTest::noop);
			selector.update(true, "Test Player",
				RankSelectorTest.result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000), null, null);
			for (int width : new int[]{225, 242, 320})
			{
				selector.setSize(width, 32);
				selector.doLayout();
				assertEquals(0, selector.getInsets().right);
				BufferedImage painted = new BufferedImage(width, 32, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = painted.createGraphics();
				selector.printAll(g);
				g.dispose();
				for (int y = 0; y < 32; y++)
				{
					assertEquals(ColorScheme.DARK_GRAY_COLOR.getRGB(),
						painted.getRGB(width - MinimalScrollBarUI.WIDTH - 1, y));
					for (int x = width - MinimalScrollBarUI.WIDTH; x < width; x++)
					{
						assertEquals(ColorScheme.DARKER_GRAY_COLOR.getRGB(), painted.getRGB(x, y));
					}
				}
			}
		});
	}

	@Test
	public void primaryAndComparisonAccessorsProjectWithoutReplacingStoredResults() throws Exception
	{
		HiscoreResult nativeBlue = RankSelectorTest.result(AccountType.IRONMAN, HiscoreTable.STANDARD, 10, 10000);
		HiscoreResult nativeRed = RankSelectorTest.result(AccountType.HARDCORE_IRONMAN, HiscoreTable.STANDARD, 20, 10000);
		HiscoreResult chosenRanks = RankSelectorTest.result(AccountType.REGULAR, HiscoreTable.STANDARD, 100, 10000);
		LookupTestFixture fixture = new LookupTestFixture();
		LookupSession session = fixture.primary;
		ComparisonController comparison = fixture.comparison;
		LookupTestFixture.edt(() ->
		{
			session.adoptState(nativeBlue, null, null, "Blue");
			comparison.doCompareLookup("Red", "Blue");
		});
		fixture.hiscores.get("Red").complete(nativeRed);
		LookupTestFixture.edt(() -> fixture.clogs.get("Red").complete(null));
		LookupTestFixture.edt(RankSelectorLayoutTest::noop);
		session.setRankView(result -> result.withRanks(chosenRanks));
		assertEquals(100, session.getHiscoreResult().getOverallRank());
		assertEquals(100, comparison.getCompareHiscoreResult().getOverallRank());
		assertEquals(AccountType.IRONMAN, session.getHiscoreResult().getAccountType());
		assertEquals(AccountType.HARDCORE_IRONMAN, comparison.getCompareHiscoreResult().getAccountType());
		assertSame(nativeBlue, session.getNativeHiscoreResult());
		assertSame(nativeRed, comparison.getNativeCompareHiscoreResult());
	}

	private static void noop()
	{
	}
}
