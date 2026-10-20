package com.killclog;

/**
 * What a character publish tells the panel. The meaning travels as the value
 * and the words live only here, so rewording a status never changes what
 * the panel does with it.
 */
enum CharacterStatus
{
	/** Withdraws the character's message. */
	CLEAR(" ", null),
	RENDERING("updating character...", StatusMessage.Kind.PROGRESS),
	/** Completes the request as a success. */
	PUBLISHED("character updated!", null),
	/** Completes the request as a failure. */
	FAILED("Character failed", null),
	APPEARANCE("Change equipment, then retry", StatusMessage.Kind.NOTICE),
	PENDING("Still rendering...", StatusMessage.Kind.NOTICE),
	RECOVERY("Publishing on hold", StatusMessage.Kind.NOTICE),
	DISABLED("Publishing unavailable", StatusMessage.Kind.NOTICE),
	UNKNOWN("Check your profile", StatusMessage.Kind.NOTICE),
	BUSY("Finishing previous request...", StatusMessage.Kind.RESULT),
	DELETING("deleting character...", StatusMessage.Kind.PROGRESS),
	DELETED("character deleted", StatusMessage.Kind.RESULT),
	NO_CHARACTER("no character to delete", StatusMessage.Kind.RESULT);

	final String text;
	/** How the row shows it; null for the three that withdraw or complete instead. */
	final StatusMessage.Kind kind;

	CharacterStatus(String text, StatusMessage.Kind kind)
	{
		this.text = text;
		this.kind = kind;
	}

	static CharacterStatus terminal(ProfileAppearanceService.Outcome outcome)
	{
		switch (outcome)
		{
			case PUBLISHED: return PUBLISHED;
			case DELETED: return DELETED;
			case NO_CHARACTER: return NO_CHARACTER;
			case RENDERING: return PENDING;
			case RECOVERY_PENDING: return RECOVERY;
			case DISABLED: return DISABLED;
			case BUSY: return BUSY;
			case UNKNOWN: return UNKNOWN;
			case APPEARANCE_PENDING: return APPEARANCE;
			case CANCELLED: return CLEAR;
			default: return FAILED;
		}
	}
}
