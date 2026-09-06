package com.killclog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class FirstPartyFeedbackTest
{
	private static final class Settings implements KillClogConfig
	{
		boolean silentAutomatic = true;
		boolean manualText;

		@Override
		public boolean silentAutomaticSync()
		{
			return silentAutomatic;
		}

		@Override
		public boolean showManualSyncStatusMessages()
		{
			return manualText;
		}
	}

	private final Settings config = new Settings();
	private final List<String> events = new ArrayList<>();
	private final FirstPartyFeedback feedback = feedback("sync failed");

	private FirstPartyFeedback feedback(String failureText)
	{
		return new FirstPartyFeedback(config,
			(text, autoClear) -> events.add(text), () -> events.add("flash"), failureText);
	}

	@Test
	public void automaticSyncIsSilentThroughProgressRetryFailureAndSuccess()
	{
		feedback.progress(false, "syncing...", false);
		feedback.progress(false, "retrying...", false);
		feedback.complete(false, false, "Server unavailable");
		assertEquals("Server unavailable", feedback.lastFailure());
		assertTrue(events.isEmpty());
		feedback.complete(false, true, "Synced");
		assertNull(feedback.lastFailure());
		assertTrue(events.isEmpty());
	}

	@Test
	public void manualSyncOnlyFlashesButRetainsFailuresForHover()
	{
		feedback.progress(true, "syncing...", false);
		feedback.complete(true, false, "Account opted out");
		assertTrue(events.isEmpty());
		assertEquals("Account opted out", feedback.lastFailure());
		feedback.complete(true, true, "Synced");
		assertEquals(Collections.singletonList("flash"), events);
		assertNull(feedback.lastFailure());
	}

	@Test
	public void manualTextPreferenceDoesNotUnmuteAutomaticSync()
	{
		config.manualText = true;
		feedback.progress(false, "syncing...", false);
		feedback.complete(false, false, "Unavailable");
		assertTrue(events.isEmpty());
		feedback.progress(true, "syncing...", false);
		feedback.complete(true, false, "Unavailable");
		feedback.complete(true, true, "Synced");
		assertEquals(java.util.Arrays.asList("syncing...", "sync failed", "flash"), events);
	}

	@Test
	public void automaticOptInDoesNotEnableManualText()
	{
		config.silentAutomatic = false;
		feedback.progress(false, "syncing...", false);
		feedback.complete(false, true, "Synced");
		feedback.progress(true, "syncing...", false);
		assertEquals(java.util.Arrays.asList("syncing...", "flash"), events);
	}

	@Test
	public void characterUploadSharesManualPreferencesAndKeepsItsOwnFailure()
	{
		FirstPartyFeedback character = feedback("Publish failed");
		character.progress(true, "rendering...", false);
		character.complete(true, false, "Character upload failed");
		assertTrue(events.isEmpty());
		feedback.complete(false, true, "Synced prerequisite");
		assertEquals("Character upload failed", character.lastFailure());
		character.complete(true, true, "Published");
		assertEquals(Collections.singletonList("flash"), events);
		assertNull(character.lastFailure());
		events.clear();
		config.manualText = true;
		character.progress(true, "rendering...", false);
		character.complete(true, false, "Unavailable");
		assertEquals(java.util.Arrays.asList("rendering...", "Publish failed"), events);
	}

	@Test
	public void changedPreferencesApplyToThePendingResultAndResetForgetsFailure()
	{
		config.silentAutomatic = false;
		feedback.progress(false, "syncing...", false);
		events.clear();
		config.silentAutomatic = true;
		feedback.complete(false, false, "Unavailable");
		assertTrue(events.isEmpty());
		feedback.reset();
		assertNull(feedback.lastFailure());
	}
}
