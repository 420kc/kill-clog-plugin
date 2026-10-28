package com.killclog;

/**
 * The kinds of Kill Clog chat line, which decide whether the chat setting can hide one ({@link KillClogChatNotifier}).
 */
enum ChatNotice
{
	NEW_DROP,
	SYNC_HELP,
	SYNC_RESULT,
	WARNING
}
