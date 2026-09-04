package com.killclog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SkillDisplay
{
	TOOLTIP("Skill Summary only"),
	FIXED("Main Grid"),
	TRAY("Activity Tray");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
