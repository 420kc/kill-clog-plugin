package com.killclog;

import java.awt.Color;
import javax.swing.JLabel;

public enum FourTwentyMode
{
	OFF,
	GREEN_420S,    // KCs naturally at 420 turn green
	CAP_420,       // All KCs display min(kc, 420)
	ALL_420;       // All KCs with kc > 0 display "420"

	public static final Color GREEN = new Color(30, 200, 30);

	/**
	 * Apply this mode's per-cell overrides to {@code label}, given the
	 * underlying {@code kc} value the base renderer just wrote. No-op for
	 * {@link #OFF}.
	 */
	public void applyOverrides(JLabel label, int kc)
	{
		switch (this)
		{
			case GREEN_420S:
				if (kc == 420)
				{
					label.setForeground(GREEN);
				}
				break;
			case CAP_420:
				if (kc > 0)
				{
					int display = Math.min(kc, 420);
					label.setText(ClogHelper.pad(ClogHelper.formatKc(display)));
					if (display == 420)
					{
						label.setForeground(GREEN);
					}
				}
				break;
			case ALL_420:
				if (kc > 0)
				{
					label.setText(ClogHelper.pad("420"));
					label.setForeground(GREEN);
				}
				break;
			case OFF:
			default:
				break;
		}
	}
}
