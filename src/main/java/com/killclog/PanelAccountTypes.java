package com.killclog;

import javax.annotation.Nullable;

final class PanelAccountTypes
{
	private final RuneProfileService runeProfileService;

	PanelAccountTypes(RuneProfileService runeProfileService)
	{
		this.runeProfileService = runeProfileService;
	}

	@Nullable
	AccountType current(@Nullable HiscoreResult hiscore, @Nullable ClogResult clog, @Nullable String player)
	{
		return current(hiscore != null ? hiscore.getAccountType() : null, clog, player);
	}

	@Nullable
	AccountType current(@Nullable AccountType fallback, @Nullable ClogResult clog, @Nullable String player)
	{
		if (clog != null && clog.getProviderAccountType() != null && clog.getProviderAccountType().isGroupIronman())
		{
			return clog.getProviderAccountType();
		}
		AccountType cachedProviderType = runeProfileGroupAccountType(player);
		if (cachedProviderType != null)
		{
			return cachedProviderType;
		}
		return fallback;
	}

	@Nullable
	AccountType display(@Nullable HiscoreResult hiscore, @Nullable ClogResult clog, @Nullable String player)
	{
		return AccountType.displayType(LookupQueries.accountType(hiscore, clog),
			runeProfileGroupAccountType(player));
	}

	@Nullable
	private AccountType runeProfileGroupAccountType(@Nullable String player)
	{
		if (player == null || player.isBlank() || "--".equals(player))
		{
			return null;
		}
		AccountType type = runeProfileService.getCachedAccountType(player);
		return type != null && type.isGroupIronman() ? type : null;
	}
}
