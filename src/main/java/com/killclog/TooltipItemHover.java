package com.killclog;

import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

final class TooltipItemHover
{
	private final JComponent component;
	private List<HitBox> hitBoxes = Collections.emptyList();
	private int hoveredItemId = -1;
	private int hoveredSection = -1;
	private boolean hoveredObtained;
	private int hoveredItemCount;
	private String hoveredItemName;
	private boolean wikiLinksEnabled = true;

	TooltipItemHover(JComponent component)
	{
		this.component = component;
		install();
	}

	void setWikiLinksEnabled(boolean wikiLinksEnabled)
	{
		this.wikiLinksEnabled = wikiLinksEnabled;
	}

	void setHitBoxes(List<HitBox> hitBoxes)
	{
		this.hitBoxes = hitBoxes != null ? hitBoxes : Collections.emptyList();
	}

	void clear()
	{
		if (hoveredItemId != -1 || hoveredItemName != null || hoveredSection != -1)
		{
			hoveredItemId = -1;
			hoveredItemName = null;
			hoveredSection = -1;
			hoveredObtained = false;
			hoveredItemCount = 0;
			component.repaint();
		}
	}

	String hoveredItemName()
	{
		return hoveredItemName;
	}

	boolean hoveredItemObtained()
	{
		return hoveredItemId > 0 && hoveredObtained;
	}

	String hoveredDuplicateCountText()
	{
		return hoveredItemObtained() ? duplicateCountText(hoveredItemCount) : null;
	}

	static String duplicateCountText(int count)
	{
		if (count <= 1)
		{
			return null;
		}
		if (count >= 10_000)
		{
			long thousands = Math.round(count / 1000.0);
			return "x" + String.format(Locale.US, "%,d", thousands) + "k";
		}
		return "x" + String.format(Locale.US, "%,d", count);
	}

	private void install()
	{
		component.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				updateHoveredItem(e.getX(), e.getY());
			}
		});
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				updateHoveredItem(e.getX(), e.getY());
				if (wikiLinksEnabled && e.getButton() == MouseEvent.BUTTON1 && hoveredItemId > 0)
				{
					TooltipItemLink.openWiki(hoveredItemId);
					closeTooltip();
					e.consume();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				clear();
			}
		});
	}

	private void updateHoveredItem(int mx, int my)
	{
		HitBox hitBox = findHitBox(mx, my);
		int nextId = hitBox != null ? hitBox.itemId : -1;
		int nextSection = hitBox != null ? hitBox.section : -1;
		boolean nextObtained = hitBox != null && hitBox.obtained;
		int nextCount = hitBox != null ? hitBox.count : 0;
		if (nextId == hoveredItemId && nextSection == hoveredSection
			&& nextObtained == hoveredObtained && nextCount == hoveredItemCount)
		{
			return;
		}
		hoveredItemId = nextId;
		hoveredItemName = hitBox != null ? hitBox.itemName : null;
		hoveredSection = nextSection;
		hoveredObtained = nextObtained;
		hoveredItemCount = nextCount;
		component.repaint();
	}

	private void closeTooltip()
	{
		clear();
		component.setVisible(false);
	}

	private HitBox findHitBox(int mx, int my)
	{
		for (HitBox hitBox : hitBoxes)
		{
			if (hitBox.bounds.contains(mx, my))
			{
				return hitBox;
			}
		}
		return null;
	}

	static final class HitBox
	{
		private final int section;
		private final int itemId;
		private final String itemName;
		private final Rectangle bounds;
		private final boolean obtained;
		private final int count;

		HitBox(int itemId, String itemName, Rectangle bounds)
		{
			this(0, itemId, itemName, bounds, false, 1);
		}

		HitBox(int section, int itemId, String itemName, Rectangle bounds)
		{
			this(section, itemId, itemName, bounds, false, 1);
		}

		HitBox(int itemId, String itemName, Rectangle bounds, boolean obtained)
		{
			this(0, itemId, itemName, bounds, obtained, 1);
		}

		HitBox(int section, int itemId, String itemName, Rectangle bounds, boolean obtained)
		{
			this(section, itemId, itemName, bounds, obtained, 1);
		}

		HitBox(int itemId, String itemName, Rectangle bounds, boolean obtained, int count)
		{
			this(0, itemId, itemName, bounds, obtained, count);
		}

		HitBox(int section, int itemId, String itemName, Rectangle bounds,
			boolean obtained, int count)
		{
			this.section = section;
			this.itemId = itemId;
			this.itemName = itemName;
			this.bounds = bounds;
			this.obtained = obtained;
			this.count = Math.max(count, 0);
		}
	}
}
