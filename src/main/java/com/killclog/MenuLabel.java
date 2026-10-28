package com.killclog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The words of Kill Clog's right-click option.
 */
@Getter
@RequiredArgsConstructor
public enum MenuLabel
{
	KILL_CLOG("Kill Clog"),
	LOOKUP("Lookup");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
