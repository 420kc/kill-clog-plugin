package com.killclog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClogSource
{
	TEMPLE("TempleOSRS"),
	LOCAL("Local"),
	BOTH("Both");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
