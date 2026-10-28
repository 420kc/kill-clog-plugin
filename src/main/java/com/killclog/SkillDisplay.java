package com.killclog;

import lombok.RequiredArgsConstructor;

/**
 * Where the skill cells sit: the main grid or the activity tray.
 */
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
