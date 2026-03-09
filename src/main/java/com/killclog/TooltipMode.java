package com.killclog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
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
