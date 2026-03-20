package com.killclog;

public enum AccountType
{
	REGULAR,
	IRONMAN,
	HARDCORE_IRONMAN,
	ULTIMATE_IRONMAN,
	GROUP_IRONMAN,
	HARDCORE_GROUP_IRONMAN;

	boolean isGroupIronman()
	{
		return this == GROUP_IRONMAN || this == HARDCORE_GROUP_IRONMAN;
	}
}
