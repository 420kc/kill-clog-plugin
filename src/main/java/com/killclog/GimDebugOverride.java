package com.killclog;

// DEV-ONLY: revert before squash to master. Lets a developer test GIM helmet badge rendering
// without owning a GIM/HCGIM/Unranked GIM account.
enum GimDebugOverride
{
	OFF,
	GIM,
	HCGIM,
	UNRANKED_GIM;

	AccountType toAccountType()
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
