package com.killclog;

import lombok.RequiredArgsConstructor;

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
