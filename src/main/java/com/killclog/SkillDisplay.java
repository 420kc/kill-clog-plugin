package com.killclog;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum SkillDisplay
{
	FIXED("Main Grid"),
	TRAY("Activity Tray");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
