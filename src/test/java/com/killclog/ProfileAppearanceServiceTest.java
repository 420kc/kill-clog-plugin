package com.killclog;

import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileAppearanceServiceTest
{
	@Test
	public void visibleFollowerNpcIdUsesTransformedComposition()
	{
		NPCComposition base = composition(9999, false, null);
		NPCComposition visible = composition(12293, true, new String[]{"Pick-up"});
		NPC follower = npc(9999, base, visible);

		assertEquals(12293, ProfileAppearanceService.visibleFollowerNpcId(follower));
	}

	@Test
	public void visibleFollowerNpcIdRejectsNonPetTransformation()
	{
		NPCComposition base = composition(9999, true, new String[]{"Pick-up"});
		NPCComposition visible = composition(12296, false, new String[]{"Talk-to"});

		assertEquals(-1, ProfileAppearanceService.visibleFollowerNpcId(
			npc(9999, base, visible)));
	}

	@Test
	public void visibleFollowerNpcIdFallsBackToNpcId()
	{
		NPC follower = npc(9399,
			composition(9399, true, new String[]{"Pick-up"}), null);

		assertEquals(9399, ProfileAppearanceService.visibleFollowerNpcId(follower));
		assertEquals(-1, ProfileAppearanceService.visibleFollowerNpcId(null));
		assertEquals(-1, ProfileAppearanceService.visibleFollowerNpcId(npc(12296,
			composition(12296, false, new String[]{"Talk-to"}), null)));
	}

	@Test
	public void incumbentCredentialCancelsPendingRecoveryBeforePublishing()
	{
		JsonObject pending = new JsonObject();
		pending.addProperty("error", "appearance_recovery_pending");

		assertTrue(ProfileAppearanceService.shouldCancelPendingRecovery(409, pending, true));
		assertFalse(ProfileAppearanceService.shouldCancelPendingRecovery(409, pending, false));
		assertFalse(ProfileAppearanceService.shouldCancelPendingRecovery(401, pending, true));
		assertTrue(ProfileAppearanceService.PUBLISH_RETRY_DELAY_MS > 2000L);
	}

	@Test
	public void missingOrUnverifiedProfileRequestsOneSyncPrerequisite()
	{
		JsonObject missing = new JsonObject();
		missing.addProperty("error", "no_bound_account");
		JsonObject unverified = new JsonObject();
		unverified.addProperty("error", "binding_not_verified");

		assertTrue(ProfileAppearanceService.isProfileRequired(409, missing));
		assertTrue(ProfileAppearanceService.isProfileRequired(409, unverified));
		assertFalse(ProfileAppearanceService.isProfileRequired(403, missing));
	}

	@Test
	public void appearanceCredentialsAreStrictLowercaseHex()
	{
		assertTrue(ProfileAppearanceService.validSecret("a".repeat(64)));
		assertFalse(ProfileAppearanceService.validSecret("A".repeat(64)));
		assertFalse(ProfileAppearanceService.validSecret("short"));
	}

	@Test
	public void onlyRenderedAppearanceIsReportedAsPublished()
	{
		JsonObject ready = new JsonObject();
		ready.addProperty("published", true);
		ready.addProperty("render_status", "ready");
		JsonObject pending = ready.deepCopy();
		pending.addProperty("render_status", "pending_renderer");

		assertTrue(ProfileAppearanceService.isRenderedPublishResponse(200, ready));
		assertFalse(ProfileAppearanceService.isRenderedPublishResponse(200, pending));
		assertFalse(ProfileAppearanceService.isRenderedPublishResponse(202, ready));
		assertFalse(ProfileAppearanceService.isRenderedPublishResponse(200, null));
	}

	@Test
	public void acceptedRendererTimeoutIsPendingRatherThanPublished()
	{
		JsonObject pending = new JsonObject();
		pending.addProperty("accepted", true);
		pending.addProperty("published", false);
		pending.addProperty("render_status", "pending_renderer");

		assertTrue(ProfileAppearanceService.isAcceptedPendingPublishResponse(202, pending));
		assertFalse(ProfileAppearanceService.isAcceptedPendingPublishResponse(200, pending));
		pending.addProperty("render_status", "ready");
		assertFalse(ProfileAppearanceService.isAcceptedPendingPublishResponse(202, pending));
	}

	@Test
	public void characterPublishStatusContractHasOnlySuccessOrFailureTerminals()
	{
		assertEquals("rendering...", KillClogPlugin.CHARACTER_RENDERING_STATUS);
		assertEquals("character published!", KillClogPlugin.CHARACTER_PUBLISHED_STATUS);
		assertEquals("Publish failed", KillClogPlugin.CHARACTER_FAILED_STATUS);
		for (ProfileAppearanceService.Outcome outcome : ProfileAppearanceService.Outcome.values())
		{
			assertEquals(outcome == ProfileAppearanceService.Outcome.PUBLISHED
				? "character published!" : "Publish failed",
				KillClogPlugin.characterPublishTerminalStatus(outcome));
		}
	}

	private static NPC npc(int id, NPCComposition base, NPCComposition transformed)
	{
		return (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[]{NPC.class},
			(instance, method, args) ->
			{
				if (method.getName().equals("getId"))
				{
					return id;
				}
				if (method.getName().equals("getTransformedComposition"))
				{
					return transformed;
				}
				if (method.getName().equals("getComposition"))
				{
					return base;
				}
				return defaultValue(method.getReturnType());
			});
	}

	private static NPCComposition composition(int id, boolean follower, String[] actions)
	{
		return (NPCComposition) Proxy.newProxyInstance(NPCComposition.class.getClassLoader(),
			new Class<?>[]{NPCComposition.class}, (instance, method, args) ->
			{
				if (method.getName().equals("getId"))
				{
					return id;
				}
				if (method.getName().equals("isFollower"))
				{
					return follower;
				}
				if (method.getName().equals("getActions"))
				{
					return actions;
				}
				return defaultValue(method.getReturnType());
			});
	}

	private static Object defaultValue(Class<?> returnType)
	{
		if (returnType.equals(boolean.class))
		{
			return false;
		}
		if (returnType.equals(int.class))
		{
			return 0;
		}
		return null;
	}
}
