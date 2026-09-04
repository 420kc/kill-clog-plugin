package com.killclog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SkillDisplay
{
	TOOLTIP("Tooltip only"),
	FIXED("Fixed above boss grid"),
	TRAY("Above clues in tray");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
