package com.killclog;

import java.util.function.BiConsumer;

/** Panel feedback for one first-party control. All access stays on the EDT. */
final class FirstPartyFeedback
{
	private final KillClogConfig config;
	private final BiConsumer<String, Boolean> status;
	private final Runnable successFlash;
	private final String failureText;
	private String lastFailure;

	FirstPartyFeedback(KillClogConfig config, BiConsumer<String, Boolean> status,
		Runnable successFlash, String failureText)
	{
		this.config = config;
		this.status = status;
		this.successFlash = successFlash;
		this.failureText = failureText;
	}

	void progress(boolean manual, String text, boolean autoClear)
	{
		if (manual || !config.silentAutomaticSync())
		{
			status.accept(text, autoClear);
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
			progress(manual, failureText, true);
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
