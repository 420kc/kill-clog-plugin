package com.killclog;

import okhttp3.Request;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KillClogEndpointTest
{
	@After
	public void clearProperties()
	{
		System.clearProperty(KillClogEndpoint.ENDPOINT_PROPERTY);
		System.clearProperty(KillClogEndpoint.STAGING_TOKEN_PROPERTY);
	}

	@Test
	public void releaseDefaultIsProduction()
	{
		assertEquals(KillClogEndpoint.PRODUCTION_API, KillClogEndpoint.apiBaseUrl());
	}

	@Test
	public void onlyExactStagingEndpointIsAccepted()
	{
		System.setProperty(KillClogEndpoint.ENDPOINT_PROPERTY,
			KillClogEndpoint.STAGING_API + "/");
		assertEquals(KillClogEndpoint.STAGING_API, KillClogEndpoint.apiBaseUrl());

		System.setProperty(KillClogEndpoint.ENDPOINT_PROPERTY, "https://example.invalid/api");
		assertEquals(KillClogEndpoint.PRODUCTION_API, KillClogEndpoint.apiBaseUrl());
	}

	@Test
	public void stagingTokenIsScopedToStagingRequests()
	{
		System.setProperty(KillClogEndpoint.STAGING_TOKEN_PROPERTY, "a".repeat(64));
		Request.Builder staging = new Request.Builder().url(KillClogEndpoint.STAGING_API + "/healthz");
		KillClogEndpoint.addStagingHeader(staging, KillClogEndpoint.STAGING_API + "/healthz");
		assertEquals("a".repeat(64), staging.build().header(KillClogEndpoint.STAGING_HEADER));

		Request.Builder production = new Request.Builder().url(KillClogEndpoint.PRODUCTION_API + "/healthz");
		KillClogEndpoint.addStagingHeader(production, KillClogEndpoint.PRODUCTION_API + "/healthz");
		assertNull(production.build().header(KillClogEndpoint.STAGING_HEADER));
	}

	@Test
	public void appearanceCredentialsCannotCrossBetweenStagingAndProduction()
	{
		assertEquals("ff.production.deviceSecret",
			ProfileAppearanceService.scopedKey(255L, "deviceSecret"));

		System.setProperty(KillClogEndpoint.ENDPOINT_PROPERTY, KillClogEndpoint.STAGING_API);
		assertEquals("ff.staging.deviceSecret",
			ProfileAppearanceService.scopedKey(255L, "deviceSecret"));
	}
}
