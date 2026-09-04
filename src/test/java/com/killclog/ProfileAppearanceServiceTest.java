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
		NPCComposition visible = proxy(NPCComposition.class, 12293, null);
		NPC follower = proxy(NPC.class, 9999, visible);

		assertEquals(12293, ProfileAppearanceService.visibleFollowerNpcId(follower));
	}

	@Test
	public void visibleFollowerNpcIdFallsBackToNpcId()
	{
		NPC follower = proxy(NPC.class, 9399, null);

		assertEquals(9399, ProfileAppearanceService.visibleFollowerNpcId(follower));
		assertEquals(-1, ProfileAppearanceService.visibleFollowerNpcId(null));
		assertEquals(-1, ProfileAppearanceService.visibleFollowerNpcId(proxy(NPC.class, 12296, null)));
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

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, int id, NPCComposition transformed)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
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
					return proxy(NPCComposition.class, id, null);
				}
				if (method.getName().equals("getActions"))
				{
					return new String[]{id == 12296 ? "Talk-to" : "Pick-up"};
				}
				Class<?> returnType = method.getReturnType();
				if (returnType.equals(boolean.class))
				{
					return true;
				}
				if (returnType.equals(int.class))
				{
					return 0;
				}
				return null;
			});
	}
}
