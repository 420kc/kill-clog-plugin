package com.killclog;

/** Panel feedback for one first-party control. All access stays on the EDT. */
final class FirstPartyFeedback
{
	/** Receives the messages this control is allowed to show. */
	interface Status
	{
		void show(StatusMessage.Kind kind, String text, boolean autoClear);
	}

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
			show(manual, StatusMessage.Kind.RESULT, failureText, true);
		}
	}

	String lastFailure()
	{
		return lastFailure;
	}

	void reset()
	{
		lastFailure = null;
	}
}
