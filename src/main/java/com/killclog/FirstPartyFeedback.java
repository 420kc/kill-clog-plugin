package com.killclog;

/** Panel feedback for one first-party control. All access stays on the EDT. */
final class FirstPartyFeedback
{
	/** Receives the messages this control is allowed to show. */
	interface Status
	{
		void show(StatusMessage.Kind kind, String text, boolean autoClear);
	}

	// A server that refuses this version: retrying cannot help, only an update can.
	static final String UPDATE_REQUIRED = "Kill Clog needs an update to keep syncing. Restart RuneLite to update it.";

	private final KillClogConfig config;
	private final Status status;
	private final Runnable successFlash;
	private final String failureText;
	private String lastFailure;

	FirstPartyFeedback(KillClogConfig config, Status status, Runnable successFlash, String failureText)
	{
		this.config = config;
		this.status = status;
		this.successFlash = successFlash;
		this.failureText = failureText;
	}

	void progress(boolean manual, String text, boolean autoClear)
	{
		show(manual, StatusMessage.Kind.PROGRESS, text, autoClear);
	}

	/** Manual actions always speak; automatic ones only when the user opted into their feedback. */
	void show(boolean manual, StatusMessage.Kind kind, String text, boolean autoClear)
	{
		if (manual || !config.silentAutomaticSync())
		{
			status.show(kind, text, autoClear);
		}
	}

	void complete(boolean manual, boolean ok, String message)
	{
		lastFailure = ok ? null : message != null ? message : failureText;
		if (ok)
		{
			if (manual || !config.silentAutomaticSync())
			{
				successFlash.run();
			}
		}
		else
		{
			show(manual, StatusMessage.Kind.RESULT, updateRequired() ? "update needed" : failureText, true);
		}
	}

	String lastFailure()
	{
		return lastFailure;
	}

	/** The control's hover label: its usual one, its failure one, or a call to update. */
	String hoverText(String normal, String failed)
	{
		return lastFailure == null ? normal : updateRequired() ? "update needed - restart RuneLite" : failed;
	}

	private boolean updateRequired()
	{
		return lastFailure != null && lastFailure.startsWith(UPDATE_REQUIRED);
	}

	void reset()
	{
		lastFailure = null;
	}
}
