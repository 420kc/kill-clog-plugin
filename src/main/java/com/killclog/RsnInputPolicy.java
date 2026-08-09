package com.killclog;

import java.util.Locale;
import java.util.regex.Pattern;

/** Strict boundary for user-authored names entered in the panel search box. */
final class RsnInputPolicy
{
	static final int MAX_LENGTH = 12;

	private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_ -]+");
	private static final Pattern HAS_ALPHANUMERIC = Pattern.compile(".*[A-Za-z0-9].*");
	private static final String[] BLOCKED = {
		"fuck", "nigger", "faggot", "kike", "chink", "retard"
	};

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

		String compact = value.toLowerCase(Locale.ROOT)
			.replace('0', 'o')
			.replace('1', 'i')
			.replace('3', 'e')
			.replace('4', 'a')
			.replace('5', 's')
			.replace('7', 't')
			.replaceAll("[_ -]", "");
		for (String blocked : BLOCKED)
		{
			if (compact.contains(blocked))
			{
				return false;
			}
		}
		return true;
	}
}
