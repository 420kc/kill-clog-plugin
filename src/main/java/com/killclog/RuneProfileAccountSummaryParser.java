package com.killclog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.annotation.Nullable;
import net.runelite.api.clan.ClanTitle;

/**
 * Reads RuneProfile's account summary: the account type (its key, else its name, else its id), the clan's name and the
 * player's clan title.
 */
final class RuneProfileAccountSummaryParser
{
	private RuneProfileAccountSummaryParser()
	{
	}

	@Nullable
	static AccountType parseAccountType(JsonObject root)
	{
		if (!root.has("accountType") || root.get("accountType").isJsonNull()
			|| !root.get("accountType").isJsonObject())
		{
			return null;
		}
		JsonObject accountType = root.getAsJsonObject("accountType");
		AccountType parsed = parseValue(accountType, "key");
		if (parsed == null)
		{
			parsed = parseValue(accountType, "name");
		}
		if (parsed != null)
		{
			return parsed;
		}
		return accountType.has("id") && !accountType.get("id").isJsonNull()
			? AccountType.fromProviderId(accountType.get("id").getAsInt()) : null;
	}

	/** The clan's name, as RuneProfile's summary carries it; null without one. */
	@Nullable
	static String parseClan(JsonObject root)
	{
		JsonElement name = clanField(root, "name");
		return name != null && !name.getAsString().trim().isEmpty() ? name.getAsString().trim() : null;
	}

	/** The player's title in the clan: RuneProfile's icon is the title's id, which picks its clan-chat icon. */
	@Nullable
	static ClanTitle parseClanTitle(JsonObject root)
	{
		JsonElement icon = clanField(root, "icon");
		JsonElement title = clanField(root, "title");
		return icon != null ? new ClanTitle(icon.getAsInt(), title != null ? title.getAsString() : null) : null;
	}

	@Nullable
	private static JsonElement clanField(JsonObject root, String field)
	{
		JsonElement clan = root.get("clan");
		JsonElement value = clan != null && clan.isJsonObject() ? clan.getAsJsonObject().get(field) : null;
		return value != null && value.isJsonPrimitive() ? value : null;
	}

	@Nullable
	private static AccountType parseValue(JsonObject accountType, String field)
	{
		return accountType.has(field) && !accountType.get(field).isJsonNull()
			? AccountType.fromProviderValue(accountType.get(field).getAsString()) : null;
	}
}
