package com.killclog;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RsnInputPolicyTest
{
	@Test
	public void acceptsRuneScapeNameCharactersWithinTwelveCharacters()
	{
		assertTrue(RsnInputPolicy.isValid("420 kc"));
		assertTrue(RsnInputPolicy.isValid("A_B-C"));
		assertTrue(RsnInputPolicy.isValid(" Zezima "));
	}

	@Test
	public void rejectsNamesThatCannotBeRuneScapeNames()
	{
		assertFalse(RsnInputPolicy.isValid("1234567890123"));
		assertFalse(RsnInputPolicy.isValid("<b>name</b>"));
		assertFalse(RsnInputPolicy.isValid("___ ---"));
		assertFalse(RsnInputPolicy.isValid(""));
	}

	@Test
	public void everySpellingJagexAcceptsIsOneName()
	{
		org.junit.Assert.assertTrue(RsnInputPolicy.sameName("My_Name", "My Name"));
		org.junit.Assert.assertTrue(RsnInputPolicy.sameName("my-name", "My Name"));
		org.junit.Assert.assertFalse(RsnInputPolicy.sameName("My Name", "My Names"));
		org.junit.Assert.assertFalse(RsnInputPolicy.sameName(null, "My Name"));
	}
}
