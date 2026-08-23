package com.killclog;

import java.util.regex.Pattern;

/** RSN shape validation for names entered in the panel search box. */
final class RsnInputPolicy
{
	static final int MAX_LENGTH = 12;

	private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_ -]+");
	private static final Pattern HAS_ALPHANUMERIC = Pattern.compile(".*[A-Za-z0-9].*");

	private RsnInputPolicy()
	{
	}

	static boolean isValid(String raw)
	{
		if (raw == null)
		{
			return false;
		}
		String value = raw.replace('\u00a0', ' ').trim();
		if (value.isEmpty() || value.length() > MAX_LENGTH
			|| !ALLOWED.matcher(value).matches()
			|| !HAS_ALPHANUMERIC.matcher(value).matches())
		{
			return false;
		}

		return true;
	}
}
