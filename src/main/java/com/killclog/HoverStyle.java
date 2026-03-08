package com.killclog;

public enum HoverStyle
{
	OUTLINE("Outline"),
	TINT("Tint"),
	NONE("None");

	private final String label;

	HoverStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
