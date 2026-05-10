package com.killclog;

// DEV-ONLY: revert before squash to master. Lets a developer test GIM helmet badge rendering
// without owning a GIM/HCGIM/Unranked GIM account.
// Public + public method visibility required because the config interface uses this as a
// return type, and RuneLite's runtime config proxy lives in the unnamed module — can't
// access package-private types from named packages under JDK 9+ module access rules.
public enum GimDebugOverride
{
	OFF,
	GIM,
	HCGIM,
	UNRANKED_GIM;

	public AccountType toAccountType()
	{
		switch (this)
		{
			case GIM: return AccountType.GROUP_IRONMAN;
			case HCGIM: return AccountType.HARDCORE_GROUP_IRONMAN;
			case UNRANKED_GIM: return AccountType.UNRANKED_GROUP_IRONMAN;
			default: return null;
		}
	}
}
