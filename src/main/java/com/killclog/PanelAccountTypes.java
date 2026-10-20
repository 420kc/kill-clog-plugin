package com.killclog;

import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PanelAccountTypes
{
	private final RuneProfileService runeProfileService;

	/**
	 * Your own account wears RuneLite's live type, read from the game: a provider's copy can be stale, like a group
	 * a player has left. Anyone else's comes from the hiscores and the providers ({@link #currentDisplay}).
	 */
	AccountDisplay own(AccountType selfType, @Nullable HiscoreResult hiscore)
	{
		return AccountDisplay.of(selfType, hiscore != null ? hiscore.getHiscoreTable() : HiscoreTable.STANDARD);
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
		return currentDisplay(LookupQueries.accountType(hiscore, clog), hiscore, clog, player);
	}

	private AccountDisplay display(@Nullable AccountType accountType, @Nullable HiscoreTable hiscoreTable,
		@Nullable ClogResult clog, @Nullable String player)
	{
		AccountType type = AccountType.displayType(accountType,
			clog != null ? clog.getProviderAccountType() : null);
		type = AccountType.displayType(type, runeProfileGroupAccountType(player));
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
