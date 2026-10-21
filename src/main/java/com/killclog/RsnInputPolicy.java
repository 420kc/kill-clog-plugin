package com.killclog;

import java.util.Locale;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Player;
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
	/** The local player's name as the client has it now, or null while it doesn't know it yet. */
	@Nullable
	static String localName(Client client)
	{
		Player local = client.getLocalPlayer();
		return local != null ? local.getName() : null;
	}

	static boolean sameName(@Nullable String a, @Nullable String b)
	{
		return a != null && b != null && key(a).equals(key(b));
	}

	/**
	 * The one key for a player, by the same rule: every cache, index and file keyed by name uses it, so each spelling
	 * Jagex accepts finds the same entry ("Foo_Bar" and "foo-bar" are "foo bar").
	 */
	static String key(String name)
	{
		return Text.toJagexName(name).toLowerCase(Locale.ROOT);
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
