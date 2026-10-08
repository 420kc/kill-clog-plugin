package com.killclog;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/** appearance-failures.tsv holds exactly the messages the publish failures read; each code still reaches its own. */
public class AppearanceFailuresCatalogTest
{
	@Test
	public void everyKnownCodeReadsItsMessage()
	{
		Map<String, String> golden = new LinkedHashMap<>();
		golden.put("invalid_client_version", "Client version could not be read. Restart RuneLite and check for updates.");
		golden.put("invalid_game_build", "Client version could not be read. Restart RuneLite and check for updates.");
		golden.put("invalid_gender", "Character body type is unsupported.");
		golden.put("invalid_colors", "Appearance colors are unsupported.");
		golden.put("invalid_equipment", "Equipment appearance is unsupported.");
		golden.put("invalid_overrides", "Equipment recolors are unsupported.");
		golden.put("invalid_follower_npc_id", "Follower is unsupported. Pick it up and retry.");
		golden.put("invalid_idle_pose_animation", "Character pose is unsupported.");
		golden.put("npc_transforms_not_supported", "Return to your normal player form and retry.");
		golden.put("invalid_manifest_shape", "Appearance format is unsupported.");
		golden.put("unsupported_manifest_schema", "Appearance format is unsupported.");
		golden.put("body_too_large", "Appearance request is too large.");
		golden.put("appearance_render_failed", "Server could not render the character. Try again later.");
		golden.put("follower_catalog_unavailable", "Follower service is unavailable. Try again later.");
		golden.put("opted_out", "Publication is disabled by your privacy setting.");
		golden.put("account_hash_mismatch", "Publication account does not match.");
		golden.put("missing_appearance_credential", "Publishing access could not be restored. Try again later.");
		golden.put("invalid_appearance_credential", "Publishing access could not be restored. Try again later.");
		golden.put("appearance_update_in_flight", "Another character update is in progress. Retry shortly.");
		golden.put("appearance_superseded", "Another character update is in progress. Retry shortly.");
		golden.put("rate_limited", "Too many requests. Please wait before retrying.");
		golden.put("appearance_registration_locked", "Publication is temporarily locked. Try again later.");
		golden.put("locked_out", "Publication is temporarily locked. Try again later.");

		Map<String, String> catalog = new LinkedHashMap<>();
		for (String[] row : CatalogTsv.rows(ProfileAppearanceFailure.class, "appearance-failures.tsv", 2))
		{
			catalog.put(row[0], row[1]);
		}
		assertEquals(golden, catalog);
		golden.forEach((code, message) ->
		{
			JsonObject body = new JsonObject();
			body.addProperty("error", code);
			ProfileAppearanceFailure failure = ProfileAppearanceFailure.fromResponse(400, body);
			assertEquals(code, failure.reason);
			assertEquals(message, failure.message);
		});
	}
}
