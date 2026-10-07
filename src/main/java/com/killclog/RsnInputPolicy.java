package com.killclog;

import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.client.util.Text;

/** RSN shape validation for names entered in the panel search box. */
final class RsnInputPolicy
{
	static final int MAX_LENGTH = 12;

	private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_ -]+");
	private static final Pattern HAS_ALPHANUMERIC = Pattern.compile(".*[A-Za-z0-9].*");

	private RsnInputPolicy()
	{
	}

	/** One player whatever the spelling Jagex accepts: an underscore, hyphen or non-breaking space for a space. */
	static boolean sameName(@Nullable String a, @Nullable String b)
	{
		return a != null && b != null && Text.toJagexName(a).equalsIgnoreCase(Text.toJagexName(b));
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
