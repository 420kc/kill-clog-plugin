package com.killclog;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileCardTest
{
	@Test
	public void rendersFixedCanvasAndOptionalPreview() throws Exception
	{
		ProfileCard.Data data = sampleData();
		BufferedImage card = ProfileCard.render(data);

		assertEquals(ProfileCard.WIDTH, card.getWidth());
		assertEquals(ProfileCard.HEIGHT, card.getHeight());
		assertTrue((card.getRGB(20, 60) >>> 24) > 0);

		String previewPath = System.getenv("KILLCLOG_PROFILE_CARD_PREVIEW");
		if (previewPath != null && !previewPath.isBlank())
		{
			ImageIO.write(card, "png", new File(previewPath));
		}
	}

	private static ProfileCard.Data sampleData()
	{
		ProfileCard.Data data = new ProfileCard.Data();
		data.rsn = "420 kc";
		data.accountLabel = "Ironman";
		data.accountIcon = sampleSprite(new Color(180, 55, 35), 18, 0);
		data.pluginIcon = sampleSprite(new Color(255, 87, 0), 18, 1);
		data.overallRank = 669;
		data.obtained = 1189;
		data.total = 1712;
		data.tierName = "Rune";
		data.tierIcon = sampleSprite(new Color(104, 186, 196), 28, 2);
		data.combatLevel = 126;
		data.totalLevel = 2277;
		data.totalXp = 1_190_481_702L;
		data.questPoints = 324;
		data.prestige = "Maxed Infernal";
		data.ehb = 867.3;
		data.caTier = "MASTER";
		data.combatTasksCompleted = 336;
		data.totalCombatTasks = 646;
		data.bossesWithKc = 69;
		data.totalBosses = 69;
		data.totalClues = 867;
		data.pets = 10;
		data.petSprites = new BufferedImage[]{
			sampleSprite(new Color(91, 119, 89), 16, 0),
			sampleSprite(new Color(129, 96, 62), 16, 1),
			sampleSprite(new Color(135, 82, 145), 16, 2),
			sampleSprite(new Color(74, 112, 148), 16, 0),
			sampleSprite(new Color(190, 109, 50), 16, 1),
			sampleSprite(new Color(100, 145, 111), 16, 2),
		};
		data.rareSprites = new BufferedImage[]{
			sampleSprite(new Color(186, 139, 72), 40, 0),
			sampleSprite(new Color(170, 178, 188), 40, 1),
			sampleSprite(new Color(94, 57, 55), 40, 2),
			sampleSprite(new Color(127, 71, 58), 40, 0),
			sampleSprite(new Color(210, 205, 190), 40, 1),
			sampleSprite(new Color(207, 178, 95), 40, 2),
		};
		data.createdDate = "August 7, 2026";
		data.updated = "Aug 7";
		data.profileUrl = "killclog.com/p/420-kc";
		data.playerModel = samplePlayerModel();
		return data;
	}

	private static BufferedImage samplePlayerModel()
	{
		BufferedImage image = new BufferedImage(
			ProfileCardPlayerModel.WIDTH, ProfileCardPlayerModel.HEIGHT,
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0, 0, 0, 110));
		g.fillOval(33, 229, 112, 14);
		g.setColor(new Color(47, 37, 31));
		g.fillPolygon(new int[]{57, 119, 143, 128, 106, 70, 44, 30},
			new int[]{82, 82, 151, 160, 119, 119, 160, 151}, 8);
		g.setColor(new Color(183, 139, 73));
		g.fillPolygon(new int[]{61, 115, 124, 106, 70, 52},
			new int[]{85, 85, 151, 164, 164, 151}, 6);
		g.setColor(new Color(213, 184, 145));
		g.fillOval(67, 30, 42, 48);
		g.setColor(new Color(72, 65, 58));
		g.fillPolygon(new int[]{62, 112, 103, 70},
			new int[]{38, 38, 20, 20}, 4);
		g.setColor(new Color(146, 116, 68));
		g.fillPolygon(new int[]{67, 88, 84, 59},
			new int[]{164, 164, 232, 232}, 4);
		g.fillPolygon(new int[]{91, 112, 119, 93},
			new int[]{164, 164, 232, 232}, 4);
		g.dispose();
		return image;
	}

	private static BufferedImage sampleSprite(Color color, int size, int shape)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0, 0, 0, 90));
		g.fillOval(3, 5, size - 5, size - 5);
		g.setColor(color);
		if (shape == 0)
		{
			g.fillRoundRect(4, 3, size - 8, size - 7, 5, 5);
		}
		else if (shape == 1)
		{
			g.fillOval(4, 3, size - 8, size - 8);
		}
		else
		{
			g.fillPolygon(new Polygon(
				new int[]{size / 2, size - 4, 4},
				new int[]{3, size - 5, size - 5}, 3));
		}
		g.dispose();
		return image;
	}
}
