package com.killclog;

import java.util.Locale;
import javax.annotation.Nullable;
import net.runelite.client.util.Text;

public enum AccountType
{
	REGULAR,
	IRONMAN,
	HARDCORE_IRONMAN,
	ULTIMATE_IRONMAN,
	GROUP_IRONMAN,
	HARDCORE_GROUP_IRONMAN,
	UNRANKED_GROUP_IRONMAN;

	boolean isGroupIronman()
	{
		return this == GROUP_IRONMAN
			|| this == HARDCORE_GROUP_IRONMAN
			|| this == UNRANKED_GROUP_IRONMAN;
	}

	@Nullable
	static AccountType displayType(@Nullable AccountType hiscoreType, @Nullable AccountType providerType)
	{
		// The first argument may already carry a higher-trust runtime or
		// first-party GIM subtype. Do not let a later provider cache replace it.
		if (hiscoreType != null && hiscoreType.isGroupIronman())
		{
			return hiscoreType;
		}
		if (providerType != null && (providerType.isGroupIronman() || hiscoreType == null))
		{
			return providerType;
		}
		return hiscoreType;
	}

	@Nullable
	static AccountType fromProviderId(int id)
	{
		// Provider ids follow the declaration order above.
		return id >= 0 && id < values().length ? values()[id] : null;
	}

	@Nullable
	static AccountType fromRuneLiteVarbit(int value)
	{
		switch (value)
		{
			case 1: return IRONMAN;
			case 2: return ULTIMATE_IRONMAN;
			case 3: return HARDCORE_IRONMAN;
			case 4: return GROUP_IRONMAN;
			case 5: return HARDCORE_GROUP_IRONMAN;
			case 6: return UNRANKED_GROUP_IRONMAN;
			default: return REGULAR;
		}
	}

	@Nullable
	static AccountType fromProviderValue(@Nullable String value)
	{
		if (value == null)
		{
			return null;
		}
		switch (value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'))
		{
			case "regular":
			case "normal":
				return REGULAR;
			case "ironman":
				return IRONMAN;
			case "hardcore_ironman":
				return HARDCORE_IRONMAN;
			case "ultimate_ironman":
				return ULTIMATE_IRONMAN;
			case "group_ironman":
			case "gim":
				return GROUP_IRONMAN;
			case "hardcore_group_ironman":
			case "hcgim":
				return HARDCORE_GROUP_IRONMAN;
			case "unranked_group_ironman":
			case "ugim":
				return UNRANKED_GROUP_IRONMAN;
			default:
				return null;
		}
	}

	@Nullable
	static AccountType fromTempleGameMode(@Nullable String gameMode)
	{
		if (gameMode == null)
		{
			return null;
		}
		try
		{
			return fromProviderId(Integer.parseInt(gameMode));
		}
		catch (NumberFormatException ignored)
		{
			return fromProviderValue(gameMode);
		}
	}

	/** "Hardcore Group Ironman" from HARDCORE_GROUP_IRONMAN. */
	String displayName()
	{
		return Text.titleCase(this);
	}
}
