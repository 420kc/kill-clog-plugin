package com.killclog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.annotation.Nullable;

/** Only known codes and a bounded support reference may reach logs or tooltips. */
final class ProfileAppearanceFailure
{
	final String reason;
	final String reference;
	final String message;

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
		String message;
		switch (code)
		{
			case "invalid_client_version":
			case "invalid_game_build": message = "Client version could not be read. Restart RuneLite and check for updates."; break;
			case "invalid_gender": message = "Character body type is unsupported."; break;
			case "invalid_colors": message = "Appearance colors are unsupported."; break;
			case "invalid_equipment": message = "Equipment appearance is unsupported."; break;
			case "invalid_overrides": message = "Equipment recolors are unsupported."; break;
			case "invalid_follower_npc_id": message = "Follower is unsupported. Pick it up and retry."; break;
			case "invalid_idle_pose_animation": message = "Character pose is unsupported."; break;
			case "npc_transforms_not_supported": message = "Return to your normal player form and retry."; break;
			case "invalid_manifest_shape":
			case "unsupported_manifest_schema": message = "Appearance format is unsupported."; break;
			case "body_too_large": message = "Appearance request is too large."; break;
			case "appearance_render_failed": message = "Server could not render the character. Try again later."; break;
			case "follower_catalog_unavailable": message = "Follower service is unavailable. Try again later."; break;
			case "opted_out": message = "Publication is disabled by your privacy setting."; break;
			case "account_hash_mismatch": message = "Publication account does not match."; break;
			case "missing_appearance_credential":
			case "invalid_appearance_credential": message = "Publishing access could not be restored. Try again later."; break;
			case "appearance_update_in_flight":
			case "appearance_superseded": message = "Another character update is in progress. Retry shortly."; break;
			case "rate_limited": message = "Too many requests. Please wait before retrying."; break;
			case "appearance_registration_locked":
			case "locked_out": message = "Publication is temporarily locked. Try again later."; break;
			default:
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
