package com.killclog;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * How skill cells are coloured, as the setting names it.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum SkillColorMode
{
	LEVEL_COMPLETION("99+ Completion"),
	CLOG_PROGRESSION("Clog Progression"),
	SKILL_COLOR("Skill Color");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}

	static SkillColorMode fromLegacyCompletionColor(String value)
	{
		return Boolean.parseBoolean(value) ? LEVEL_COMPLETION : SKILL_COLOR;
	}
}
