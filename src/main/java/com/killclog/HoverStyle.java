package com.killclog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
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
