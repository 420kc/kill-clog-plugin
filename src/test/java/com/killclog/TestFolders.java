package com.killclog;

import java.io.File;
import net.runelite.client.util.Filepath;

/** A temporary directory as the plugin's data folder. */
final class TestFolders
{
	private TestFolders()
	{
	}

	static Filepath folder(File dir)
	{
		return Filepath.Unchecked.getRooted(dir.toPath());
	}
}
