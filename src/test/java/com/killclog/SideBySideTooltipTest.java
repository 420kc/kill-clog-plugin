package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class SideBySideTooltipTest
{
	@Test
	public void wrapperReusesBothDisplayedIdentityIconsWithoutServices() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RecordingIcon blueBadge = new RecordingIcon(Color.CYAN);
			RecordingIcon redBadge = new RecordingIcon(Color.MAGENTA);
			JLabel blueName = new JLabel("Blue Player", blueBadge, JLabel.LEFT);
			JLabel redName = new JLabel("Red Player", redBadge, JLabel.RIGHT);
			ComparisonController controller = new ComparisonController(null, null, null, null,
				null, null, null, null, null);
			controller.setRenderTarget((ComparisonController.CellRenderTarget) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[]{ComparisonController.CellRenderTarget.class},
				(proxy, method, args) -> "playerName".equals(method.getName()) ? blueName
					: "clogInfoLabel".equals(method.getName()) ? redName : null));
			SideBySideTooltip pair = (SideBySideTooltip) controller.wrapSideBySide(new JPanel(), card(160), card(180));
			paint(pair);
			assertEquals(1, blueBadge.paints);
			assertEquals(1, redBadge.paints);
			assertTrue(blueBadge.x >= pair.sides()[0].getX());
			assertTrue(redBadge.x >= pair.sides()[1].getX());
			assertTrue(blueBadge.y + blueBadge.getIconHeight() <= pair.sides()[0].getY());
			assertTrue(redBadge.y + redBadge.getIconHeight() <= pair.sides()[1].getY());
		});
	}

	@Test
	public void longNamesAndMissingBadgeKeepSoloWidthsAndHeaderLanes() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SideBySideTooltip pair = new SideBySideTooltip("Very long blue player", card(90),
				"Very long red player", card(100));
			Dimension plain = pair.getPreferredSize();
			RecordingIcon badge = new RecordingIcon(Color.CYAN);
			pair.setAccountBadges(badge, null);
			BufferedImage painted = paint(pair);
			assertEquals(plain.width, pair.getWidth());
			assertEquals(90, pair.sides()[0].getWidth());
			assertEquals(100, pair.sides()[1].getWidth());
			int gapStart = pair.sides()[0].getX() + 90;
			int gapEnd = pair.sides()[1].getX();
			for (int y = NativeTooltip.getInset(); y < pair.sides()[0].getY(); y++)
			{
				for (int x = gapStart; x < gapEnd; x++)
				{
					assertNotEquals(TitleTooltip.COMPARE_BLUE.getRGB(), painted.getRGB(x, y));
					assertNotEquals(TitleTooltip.COMPARE_RED.getRGB(), painted.getRGB(x, y));
				}
			}
			pair.setAccountBadges(null, null);
			assertEquals(plain, pair.getPreferredSize());
		});
	}

	@Test
	public void rendersPreviewWithExistingAccountBadges() throws Exception
	{
		BufferedImage[] preview = new BufferedImage[1];
		SwingUtilities.invokeAndWait(() ->
		{
			SkillTooltip blue = new SkillTooltip();
			SkillTooltip red = new SkillTooltip();
			blue.setData(Skill.ATTACK, null, false);
			red.setData(Skill.ATTACK, null, false);
			SideBySideTooltip pair = new SideBySideTooltip("Iron Player", blue, "Hardcore", red);
			AccountBadgeResolver resolver = new AccountBadgeResolver(null);
			Icon blueBadge = resolver.labelIcon(AccountDisplay.of(AccountType.IRONMAN, HiscoreTable.STANDARD));
			Icon redBadge = resolver.labelIcon(AccountDisplay.of(AccountType.HARDCORE_IRONMAN, HiscoreTable.STANDARD));
			assertNotNull(blueBadge);
			assertNotNull(redBadge);
			pair.setAccountBadges(blueBadge, redBadge);
			preview[0] = paint(pair);
		});
		File output = new File("build/reports/comparison-badges-preview.png");
		assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
		assertTrue(ImageIO.write(preview[0], "png", output));
	}

	private static JToolTip card(int width)
	{
		JToolTip tip = new JToolTip();
		tip.setPreferredSize(new Dimension(width, 50));
		return tip;
	}

	private static BufferedImage paint(SideBySideTooltip tip)
	{
		tip.setSize(tip.getPreferredSize());
		tip.doLayout();
		BufferedImage image = new BufferedImage(tip.getWidth(), tip.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		tip.paint(g);
		g.dispose();
		return image;
	}

	private static final class RecordingIcon implements Icon
	{
		private final Color color;
		private int paints;
		private int x;
		private int y;

		private RecordingIcon(Color color)
		{
			this.color = color;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			paints++;
			this.x = x;
			this.y = y;
			g.setColor(color);
			g.fillRect(x, y, getIconWidth(), getIconHeight());
		}

		@Override
		public int getIconWidth()
		{
			return 12;
		}

		@Override
		public int getIconHeight()
		{
			return 15;
		}
	}
}
