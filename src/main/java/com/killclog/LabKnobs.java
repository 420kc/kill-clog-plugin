package com.killclog;

import java.awt.Graphics2D;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** LAB ONLY, never committed: spacing knobs for the consistency mockups. */
final class LabKnobs
{
	// 0 today, 1 air after a subheader, 2 a short underline, 3 the section title owns a full rule.
	static int style;
	static int gapAfter;
	static int dividerPad = -1;
	static int sectionGap = 8;
	static final Set<CardBody.Part> SEPARATORS = Collections.newSetFromMap(new WeakHashMap<>());
	static final Set<CardBody.Part> SUBHEADERS = Collections.newSetFromMap(new WeakHashMap<>());

	/** Height a heading gains under the knobs: its rule, then the gap after it. */
	static int headingExtra()
	{
		return (style >= 2 ? 3 : 0) + gapAfter;
	}

	/** The rule under a heading whose line box ends at y, as wide as its text or the card. */
	static void rule(Graphics2D g, int y, int x, int textWidth, int cardWidth, int inset)
	{
		if (style < 2)
		{
			return;
		}
		g.setColor(TitleTooltip.SEPARATOR_COLOR);
		g.drawLine(x, y + 1, style == 2 ? x + textWidth - 1 : cardWidth - inset - 1, y + 1);
	}

	private LabKnobs()
	{
	}
}
