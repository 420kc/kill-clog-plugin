package com.killclog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** Only known codes and a bounded support reference may reach logs or tooltips. */
final class ProfileAppearanceFailure
{
	final String reason;
	final String reference;
	final String message;

	// Each known code's plain message, from appearance-failures.tsv; its parity test pins them.
	private static final Map<String, String> MESSAGES = new HashMap<>();

	static
	{
		for (String[] row : CatalogTsv.rows(ProfileAppearanceFailure.class, "appearance-failures.tsv", 2))
		{
			MESSAGES.put(row[0], row[1]);
		}
	}

	private ProfileAppearanceFailure(String reason, String reference, String message)
	{
		this.reason = reason;
		this.reference = reference;
		this.message = message + (reference.isEmpty() ? "" : " Reference: " + reference + ".");
	}

	static ProfileAppearanceFailure fromResponse(int status, @Nullable JsonObject body)
	{
		String code = field(body, "error");
		String reference = field(body, "request_id");
		if (!reference.matches("[a-f0-9]{16}"))
		{
			reference = "";
		}
		String message = "update_required".equals(code) ? FirstPartyFeedback.UPDATE_REQUIRED : MESSAGES.get(code);
		if (message == null)
		{
			code = status < 0 ? "network_failure" : "request_failed";
			message = status < 0 ? "Could not reach Kill Clog. Try again."
				: status == 429 ? "Too many requests. Please wait before retrying."
				: status >= 500 ? "Character service is unavailable. Try again later."
				: "Character request failed. Click to retry.";
		}
		return new ProfileAppearanceFailure(code, reference, message);
	}

	private static String field(@Nullable JsonObject body, String key)
	{
		JsonElement value = body != null ? body.get(key) : null;
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
			? value.getAsString() : "";
	}
}
