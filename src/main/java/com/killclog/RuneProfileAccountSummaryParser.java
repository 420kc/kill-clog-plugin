package com.killclog;

import com.google.gson.JsonObject;
import javax.annotation.Nullable;

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

	@Nullable
	private static AccountType parseValue(JsonObject accountType, String field)
	{
		return accountType.has(field) && !accountType.get(field).isJsonNull()
			? AccountType.fromProviderValue(accountType.get(field).getAsString()) : null;
	}
}
