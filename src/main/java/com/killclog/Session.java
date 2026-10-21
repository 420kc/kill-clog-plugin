package com.killclog;

import javax.annotation.Nullable;
import lombok.Value;

/**
 * Who is logged in, for one login: the account (its hash, which a name change keeps) and the name it plays under.
 * The plugin keeps no other copy of it ({@link SessionTracker}): work that outlives the session it began in asks
 * whether that session is still current before it does anything. {@code number} only grows, so each login and each
 * logout is a session of its own, and nothing from one can pass for another.
 */
@Value
class Session
{
	long number;
	// Null while nobody is logged in.
	@Nullable
	String name;
	long accountHash;

	static Session none(long number)
	{
		return new Session(number, null, -1);
	}

	boolean loggedIn()
	{
		return name != null;
	}
}
