package com.killclog;

public enum SkillColorMode
{
	LEVEL_COMPLETION("99+ Completion"),
	CLOG_PROGRESSION("Clog Progression"),
	SKILL_COLOR("Skill Color");

	private final String label;

	SkillColorMode(String label)
	{
		this.label = label;
	}

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
