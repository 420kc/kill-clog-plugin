package com.killclog;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class FirstPartyFeedbackTest
{
	private static final class Settings implements KillClogConfig
	{
		boolean silentAutomatic = true;

		@Override
		public boolean silentAutomaticSync()
		{
			return silentAutomatic;
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
	public void manualSyncShowsProgressAndFailureButOnlyFlashesForSuccess()
	{
		feedback.progress(true, "syncing...", false);
		feedback.complete(true, false, "Account opted out");
		assertEquals(java.util.Arrays.asList("syncing...", "sync failed"), events);
		assertEquals("Account opted out", feedback.lastFailure());
		feedback.complete(true, true, "Synced");
		assertEquals(java.util.Arrays.asList("syncing...", "sync failed", "flash"), events);
		assertNull(feedback.lastFailure());
	}

	@Test
	public void manualActionsDoNotUnmuteAutomaticSync()
	{
		feedback.progress(false, "syncing...", false);
		feedback.complete(false, false, "Unavailable");
		assertTrue(events.isEmpty());
		feedback.progress(true, "syncing...", false);
		feedback.complete(true, false, "Unavailable");
		feedback.complete(true, true, "Synced");
		feedback.progress(false, "retrying...", false);
		assertEquals(java.util.Arrays.asList("syncing...", "sync failed", "flash"), events);
	}

	@Test
	public void automaticFeedbackCanBeEnabledWithoutChangingManualFeedback()
	{
		config.silentAutomatic = false;
		feedback.progress(false, "syncing...", false);
		feedback.complete(false, true, "Synced");
		feedback.progress(true, "syncing...", false);
		feedback.complete(true, true, "Synced");
		assertEquals(java.util.Arrays.asList("syncing...", "flash", "syncing...", "flash"), events);
	}

	@Test
	public void characterUploadAlwaysShowsManualFeedbackAndKeepsItsOwnFailure()
	{
		FirstPartyFeedback character = feedback("Publish failed");
		character.progress(true, "rendering...", false);
		character.complete(true, false, "Character upload failed");
		assertEquals(java.util.Arrays.asList("rendering...", "Publish failed"), events);
		feedback.complete(false, true, "Synced prerequisite");
		assertEquals("Character upload failed", character.lastFailure());
		character.complete(true, true, "Published");
		assertEquals(java.util.Arrays.asList("rendering...", "Publish failed", "flash"), events);
		assertNull(character.lastFailure());
		events.clear();
		config.silentAutomatic = false;
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
