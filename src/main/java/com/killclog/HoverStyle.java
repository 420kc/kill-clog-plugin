package com.killclog;

import lombok.RequiredArgsConstructor;

/**
 * How a cell shows the mouse over it: an outline, a tint or nothing.
 */
@RequiredArgsConstructor
public enum HoverStyle
{
	OUTLINE("Outline"),
	TINT("Tint"),
	NONE("None");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
