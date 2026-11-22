package com.killclog;

import java.awt.*;

/** The fixed colors, and the dim, more than one class draws with; a color only one class draws stays in that class. */
final class KillClogColors
{
	/** A cell's kill count, score or level once it has one: light gray-white. */
	static final Color KC_COLOR = new Color(215, 215, 215);
	static final Color TEXT_DIM = new Color(160, 160, 160);
	static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);
	static final Color COMPARE_BLUE = new Color(91, 164, 207);
	static final Color COMPARE_RED = new Color(224, 86, 86);
	static final Color SELF_COLOR = new Color(0x4c, 0xaf, 0x6e);
	/** Art dimmed to 30%: an item not held, a tier not reached, a faint tile, a dimmed cell icon. */
	static final AlphaComposite DIM = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);

	private KillClogColors()
	{
	}
}
