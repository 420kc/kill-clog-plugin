package com.killclog;

import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileAppearanceFailureTest
{
	@Test
	public void knownRejectionIncludesSafeSupportReference()
	{
		JsonObject body = new JsonObject();
		body.addProperty("error", "invalid_colors");
		body.addProperty("request_id", "0123456789abcdef");
		ProfileAppearanceFailure failure = ProfileAppearanceFailure.fromResponse(400, body);
		assertEquals("invalid_colors", failure.reason);
		assertEquals("Appearance colors are unsupported. Reference: 0123456789abcdef.", failure.message);
	}

	@Test
	public void hostileServerBodyNeverBecomesTooltipOrLogText()
	{
		JsonObject body = new JsonObject();
		body.addProperty("error", "SECRET\n<html>untrusted</html>");
		body.addProperty("request_id", "SECRET\nreference");
		ProfileAppearanceFailure failure = ProfileAppearanceFailure.fromResponse(400, body);
		assertEquals("request_failed", failure.reason);
		assertEquals("", failure.reference);
		assertEquals("Character request failed. Click to retry.", failure.message);
		body.add("error", new JsonObject());
		assertEquals("request_failed", ProfileAppearanceFailure.fromResponse(400, body).reason);
	}

	@Test
	public void transportAndProxyFailuresRemainUsefulWithoutJson()
	{
		assertTrue(ProfileAppearanceFailure.fromResponse(-1, null).message.startsWith("Could not reach"));
		assertTrue(ProfileAppearanceFailure.fromResponse(429, null).message.startsWith("Too many requests"));
		assertTrue(ProfileAppearanceFailure.fromResponse(503, null).message.startsWith("Character service is unavailable"));
	}
}
