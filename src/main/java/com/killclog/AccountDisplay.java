package com.killclog;

import javax.annotation.Nullable;

/**
 * Account identity as shown in the panel. AccountType remains the account mode;
 * HiscoreTable names the rank table when regular pures/skillers refine.
 */
final class AccountDisplay
{
	private final AccountType accountType;
	private final HiscoreTable hiscoreTable;

	private AccountDisplay(@Nullable AccountType accountType, @Nullable HiscoreTable hiscoreTable)
	{
		this.accountType = accountType;
		this.hiscoreTable = hiscoreTable != null ? hiscoreTable : HiscoreTable.STANDARD;
	}

	static AccountDisplay of(@Nullable AccountType accountType, @Nullable HiscoreTable hiscoreTable)
	{
		HiscoreTable table = accountType == AccountType.REGULAR
			? hiscoreTable : HiscoreTable.STANDARD;
		return new AccountDisplay(accountType, table);
	}

	@Nullable
	AccountType accountType()
	{
		return accountType;
	}

	HiscoreTable hiscoreTable()
	{
		return hiscoreTable;
	}

	@Nullable
	String label()
	{
		if (hiscoreTable.isSpecial())
		{
			return hiscoreTable.displayName();
		}
		return accountType != null ? ClogHelper.accountLabel(accountType) : null;
	}
}
