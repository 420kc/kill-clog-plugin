package com.killclog;

import javax.annotation.Nullable;

final class PanelAccountTypes
{
	private final RuneProfileService runeProfileService;

	PanelAccountTypes(RuneProfileService runeProfileService)
	{
		this.runeProfileService = runeProfileService;
	}

	AccountDisplay currentDisplay(@Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		@Nullable String player)
	{
		return display(hiscore != null ? hiscore.getAccountType() : null,
			hiscore != null ? hiscore.getHiscoreTable() : HiscoreTable.STANDARD,
			clog, player);
	}

	AccountDisplay currentDisplay(@Nullable AccountType fallback, @Nullable HiscoreResult hiscore,
		@Nullable ClogResult clog, @Nullable String player)
	{
		return display(fallback,
			hiscore != null ? hiscore.getHiscoreTable() : HiscoreTable.STANDARD,
			clog, player);
	}

	AccountDisplay displayIdentity(@Nullable HiscoreResult hiscore, @Nullable ClogResult clog,
		@Nullable String player)
	{
		return display(LookupQueries.accountType(hiscore, clog),
			hiscore != null ? hiscore.getHiscoreTable() : HiscoreTable.STANDARD,
			clog, player);
	}

	private AccountDisplay display(@Nullable AccountType accountType, @Nullable HiscoreTable hiscoreTable,
		@Nullable ClogResult clog, @Nullable String player)
	{
		AccountType type = AccountType.displayType(accountType, runeProfileGroupAccountType(player));
		if (type == null && clog != null)
		{
			type = clog.getProviderAccountType();
		}
		return AccountDisplay.of(type, hiscoreTable);
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
