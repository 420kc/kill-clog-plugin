package com.killclog;

import javax.annotation.Nullable;

/**
 * Account identity as shown in the panel. AccountType remains the account mode;
 * HiscoreTable names the rank table when regular pures/skillers refine.
 */
final class AccountDisplay
{
	/** The mode an account left, worn on the frozen board it left behind. */
	enum Former
	{
		DEAD_HARDCORE("Dead Hardcore", "hardcore_ironman.png", "dead-hardcore-overlay.png"),
		DE_IRONED("De-ironed", "ironman.png", "de-ironed-overlay.png");

		final String label;
		final String helm;
		final String overlay;

		Former(String label, String helm, String overlay)
		{
			this.label = label;
			this.helm = helm;
			this.overlay = overlay;
		}
	}

	private final AccountType accountType;
	private final HiscoreTable hiscoreTable;
	@Nullable
	private final Former former;

	private AccountDisplay(@Nullable AccountType accountType, @Nullable HiscoreTable hiscoreTable,
		@Nullable Former former)
	{
		this.accountType = accountType;
		this.hiscoreTable = hiscoreTable != null ? hiscoreTable : HiscoreTable.STANDARD;
		this.former = former;
	}

	static AccountDisplay of(@Nullable AccountType accountType, @Nullable HiscoreTable hiscoreTable)
	{
		HiscoreTable table = accountType == AccountType.REGULAR
			? hiscoreTable : HiscoreTable.STANDARD;
		return new AccountDisplay(accountType, table, null);
	}

	/**
	 * This account as a board's row shows it. Only a recorded frozen row wears the mode left;
	 * a board still loading, unanswered, or without this player keeps the account's own badge.
	 */
	AccountDisplay shownOn(@Nullable HiscoreResult view, @Nullable RankLeaderboard board)
	{
		return view != null && view.isFrozen() && view.getTotalLevel() > 0 ? frozenOn(board) : this;
	}

	/**
	 * This account on a board its stats are frozen on. A Hardcore who died is an Ironman frozen
	 * on the Hardcore board; an iron who became a main is frozen on the iron boards. Any other
	 * frozen board, like an Ultimate who stepped down, keeps the account's own badge.
	 */
	AccountDisplay frozenOn(@Nullable RankLeaderboard board)
	{
		Former left = board == RankLeaderboard.HARDCORE && accountType == AccountType.IRONMAN ? Former.DEAD_HARDCORE
			: accountType == AccountType.REGULAR && (board == RankLeaderboard.IRONMAN
			|| board == RankLeaderboard.HARDCORE || board == RankLeaderboard.ULTIMATE) ? Former.DE_IRONED : null;
		return left != null ? new AccountDisplay(accountType, hiscoreTable, left) : this;
	}

	@Nullable
	Former former()
	{
		return former;
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
		if (former != null)
		{
			return former.label;
		}
		if (hiscoreTable.isSpecial())
		{
			return hiscoreTable.displayName();
		}
		return accountType != null ? ClogHelper.accountLabel(accountType) : null;
	}
}
