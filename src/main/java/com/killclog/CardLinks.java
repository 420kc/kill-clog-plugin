package com.killclog;

import java.awt.event.InputEvent;
import lombok.RequiredArgsConstructor;

/** When a card's links (the OSRS Wiki, Temple, RuneProfile, killclog.com) open: on a click, on a shift-click, or never. */
@RequiredArgsConstructor
public enum CardLinks
{
	ON("On"),
	SHIFT("Shift-click only"),
	OFF("Off");

	private final String label;

	/** Whether this press opens the link under it. */
	boolean opens(InputEvent press)
	{
		return this == ON || this == SHIFT && press.isShiftDown();
	}

	@Override
	public String toString()
	{
		return label;
	}
}
