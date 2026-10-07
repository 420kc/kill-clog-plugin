package com.killclog;

import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import lombok.AccessLevel;
import lombok.Setter;

final class TooltipItemHover
{
	private final JComponent component;
	private List<HitBox> hitBoxes = Collections.emptyList();
	private int hoveredItemId = -1;
	private int hoveredSection = -1;
	private boolean hoveredObtained;
	private String hoveredItemName;
	// Where the mouse last was over the card, so a scroll can hover what now sits under it.
	private int lastX = -1;
	private int lastY = -1;
	private boolean rehover;
	@Setter(AccessLevel.PACKAGE)
	private boolean wikiLinksEnabled = true;

	TooltipItemHover(JComponent component)
	{
		this.component = component;
		install();
	}

	void setHitBoxes(List<HitBox> hitBoxes)
	{
		this.hitBoxes = hitBoxes != null ? hitBoxes : Collections.emptyList();
		if (rehover)
		{
			rehover = false;
			updateHoveredItem(lastX, lastY);
		}
	}

	/** After a scroll the item now under the still mouse becomes the hovered one, once the card repaints. */
	void rehoverAfterPaint()
	{
		rehover = true;
	}

	void clear()
	{
		if (hoveredItemId != -1 || hoveredItemName != null || hoveredSection != -1)
		{
			hoveredItemId = -1;
			hoveredItemName = null;
			hoveredSection = -1;
			hoveredObtained = false;
			component.repaint();
		}
	}

	String hoveredItemName()
	{
		return hoveredItemName;
	}

	boolean isSectionHovered(int section)
	{
		return hoveredSection == section;
	}

	int hoveredSection()
	{
		return hoveredSection;
	}

	boolean hoveredItemObtained()
	{
		return hoveredItemId > 0 && hoveredObtained;
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
				lastX = -1;
				lastY = -1;
				clear();
			}
		});
	}

	private void updateHoveredItem(int mx, int my)
	{
		lastX = mx;
		lastY = my;
		HitBox hitBox = findHitBox(mx, my);
		int nextId = hitBox != null ? hitBox.itemId : -1;
		int nextSection = hitBox != null ? hitBox.section : -1;
		boolean nextObtained = hitBox != null && hitBox.obtained;
		if (nextId == hoveredItemId && nextSection == hoveredSection
			&& nextObtained == hoveredObtained
			&& Objects.equals(hitBox != null ? hitBox.itemName : null, hoveredItemName))
		{
			return;
		}
		hoveredItemId = nextId;
		hoveredItemName = hitBox != null ? hitBox.itemName : null;
		hoveredSection = nextSection;
		hoveredObtained = nextObtained;
		component.repaint();
	}

	private void closeTooltip()
	{
		clear();
		NativeTooltip.hideTooltipTree(component);
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

		HitBox(int section, int itemId, String itemName, Rectangle bounds, boolean obtained)
		{
			this.section = section;
			this.itemId = itemId;
			this.itemName = TooltipItemLink.displayName(itemId, itemName);
			this.bounds = bounds;
			this.obtained = obtained;
		}

		/** The part of this box a window shows, or null when it shows none of it. */
		@Nullable
		HitBox within(Rectangle window)
		{
			Rectangle shown = bounds.intersection(window);
			return shown.isEmpty() ? null : new HitBox(section, itemId, itemName, shown, obtained);
		}
	}
}
