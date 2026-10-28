package com.killclog;

import lombok.RequiredArgsConstructor;

/**
 * When cards open: on hover, or on a click.
 */
@RequiredArgsConstructor
public enum TooltipMode
{
	HOVER("Hover"),
	CLICK("Click");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
