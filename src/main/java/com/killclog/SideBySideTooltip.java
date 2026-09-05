package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.swing.JToolTip;

/**
 * Comparison tooltip: the two players' complete single tooltips side by side
 * on one parchment card, blue (primary) on the left, red (compared) on the
 * right, each under its player's name. Comparison mode renders through the
 * exact tooltip code players see solo, so every tooltip feature carries into
 * comparison automatically and the two modes can never drift apart.
 */
public class SideBySideTooltip extends NativeTooltip
{
	private static final int CARD_GAP = 8;
	private static final int NAME_GAP = 4;

	private final String blueName;
	private final String redName;
	private final JToolTip blueTip;
	private final JToolTip redTip;

	public SideBySideTooltip(String blueName, JToolTip blueTip, String redName, JToolTip redTip)
	{
		this.blueName = blueName;
		this.blueTip = blueTip;
		this.redName = redName;
		this.redTip = redTip;
		setLayout(null);
		add(blueTip);
		add(redTip);
	}

	/** The two live child tooltips, for hover wiring and tests. */
	public JToolTip[] sides()
	{
		return new JToolTip[]{blueTip, redTip};
	}

	private int nameStripHeight()
	{
		return getFontMetrics(TitleTooltip.TITLE_FONT_SMALL).getHeight() + NAME_GAP;
	}

	// Child getPreferredSize is not a pure query (ImgTooltip caches grid
	// geometry from it), so sizing, layout, and paint must all ask with the
	// same inputs; do not add state-dependent sizing here.
	@Override
	public Dimension getPreferredSize()
	{
		Dimension blue = blueTip.getPreferredSize();
		Dimension red = redTip.getPreferredSize();
		int inset = getInset();
		return new Dimension(inset * 2 + blue.width + CARD_GAP + red.width,
			inset * 2 + nameStripHeight() + Math.max(blue.height, red.height));
	}

	@Override
	public void doLayout()
	{
		int inset = getInset();
		int top = inset + nameStripHeight();
		Dimension blue = blueTip.getPreferredSize();
		Dimension red = redTip.getPreferredSize();
		blueTip.setBounds(inset, top, blue.width, blue.height);
		redTip.setBounds(inset + blue.width + CARD_GAP, top, red.width, red.height);
	}

	@Override
	protected void paintComponent(java.awt.Graphics g)
	{
		// Popups validate before painting; direct paints (tests, previews) may
		// not, and unlaid-out children would render as nothing.
		if (blueTip.getWidth() == 0)
		{
			doLayout();
		}
		super.paintComponent(g);
	}

	@Override
	protected void paintContent(Graphics2D g2, int w, int h)
	{
		g2.setFont(TitleTooltip.TITLE_FONT_SMALL);
		FontMetrics fm = g2.getFontMetrics();
		int inset = getInset();
		int top = inset + fm.getAscent();
		Dimension blue = blueTip.getPreferredSize();
		Dimension red = redTip.getPreferredSize();
		paintName(g2, fm, blueName, TitleTooltip.COMPARE_BLUE, inset, blue.width, top);
		paintName(g2, fm, redName, TitleTooltip.COMPARE_RED,
			inset + blue.width + CARD_GAP, red.width, top);
	}

	private static void paintName(Graphics2D g2, FontMetrics fm, String name,
		Color color, int cardX, int cardWidth, int baseline)
	{
		if (name == null || name.isEmpty())
		{
			return;
		}
		// A name wider than its card trims to fit rather than bleeding into
		// the neighbor card's lane or the iron border.
		String shown = name;
		while (shown.length() > 1 && fm.stringWidth(shown) > cardWidth)
		{
			shown = shown.substring(0, shown.length() - (shown.endsWith("..") ? 3 : 2)) + "..";
		}
		g2.setColor(color);
		int x = cardX + Math.max(0, (cardWidth - fm.stringWidth(shown)) / 2);
		g2.drawString(shown, x, baseline);
	}
}
