package com.killclog;

import com.google.gson.*;
import javax.annotation.Nullable;

/**
 * What Kill Clog reads from TempleOSRS: one player's stats answer (player_stats.php's data) for their account type,
 * name and EHP, and the collection log answer's EHC. A figure Temple didn't send reads -1.
 */
final class TempleStats
{
	/** A mode other than regular, or a group ironman by its group; null when Temple says regular or nothing. */
	@Nullable
	final AccountType accountType;
	@Nullable
	final String canonicalName;
	/** Temple's EHP for the account's own mode. */
	final double ehp;

	TempleStats(JsonObject data)
	{
		JsonObject info = Json.object(data, "info");
		AccountType mode = AccountType.fromTempleGameMode(Json.stringField(info, "Game mode"));
		accountType = mode != null && mode != AccountType.REGULAR ? mode
			: number(info, "GIM") > 0 ? AccountType.GROUP_IRONMAN : null;
		canonicalName = Json.stringField(info, "player_name_with_capitalization");
		ehp = number(data, "Overall_ehp");
	}

	/** The collection log answer's EHC for the account's flavour: an ironman's own rates, or the main game's. */
	static double ehc(JsonObject clogData, @Nullable AccountType type)
	{
		return number(clogData, type != null && type != AccountType.REGULAR ? "ehc_im" : "ehc");
	}

	/** Temple sends its figures as text or numbers alike. */
	private static double number(@Nullable JsonObject object, String key)
	{
		try
		{
			return Math.max(-1, Double.parseDouble(Json.stringField(object, key)));
		}
		catch (NumberFormatException | NullPointerException e)
		{
			return -1;
		}
	}
}
