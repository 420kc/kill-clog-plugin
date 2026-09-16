package com.killclog;

import java.awt.Color;
import javax.swing.Timer;

/**
 * One occupant of the panel status row: who wrote it, how it shares the row,
 * and the expiry that belongs to it. Two messages with the same text are still
 * two messages, so a stale expiry can never clear a newer one.
 */
final class StatusMessage
{
	enum Owner
	{
		LOOKUP, SYNC, CHARACTER
	}

	enum Kind
	{
		/** A control's hover line; yields to anything and clears when the pointer leaves. */
		HOVER,
		/** An actionable hint; yields to anything and keeps the controls visible while it expires. */
		NOTICE,
		/** Work in flight; holds the row until its owner replaces it. */
		PROGRESS,
		/** An outcome; holds the row until it expires or its owner replaces it. */
		RESULT
	}

	final Owner owner;
	final Kind kind;
	final String text;
	final Color color;
	private Timer expiry;

	StatusMessage(Owner owner, Kind kind, String text, Color color)
	{
		this.owner = owner;
		this.kind = kind;
		this.text = text;
		this.color = color;
	}

	/** Hover lines and notices leave the row free for the next writer. */
	boolean yields()
	{
		return kind == Kind.HOVER || kind == Kind.NOTICE;
	}

	/** Runs the action once on the EDT after the delay unless {@link #cancelExpiry} comes first. */
	void expireAfter(int delayMs, Runnable action)
	{
		cancelExpiry();
		expiry = new Timer(delayMs, e -> action.run());
		expiry.setRepeats(false);
		expiry.start();
	}

	void cancelExpiry()
	{
		if (expiry != null)
		{
			expiry.stop();
			expiry = null;
		}
	}
}
