package com.killclog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * This repository is public. Everything in it is read by players and Plugin
 * Hub reviewers, so the only documents here are the ones written for them.
 */
public class RepoLayoutTest
{
	private static final Set<String> PLAYER_DOCS = new HashSet<>(Arrays.asList(
		"README.md", "docs/chat-commands.md"));
	private static final Set<String> TOOL_DIRECTORIES = new HashSet<>(Arrays.asList(
		".git", ".gradle", ".idea", ".claude", "build", "out", "bin", "node_modules"));
	private static final List<String> NOT_FOR_PLAYERS = Arrays.asList(
		"hive/", "c:/users", "c:\\users", "worktree", "refactor");

	@Test
	public void onlyPlayerDocumentsLiveInThisRepository() throws IOException
	{
		Path root = root();
		List<String> strays = new ArrayList<>();
		Files.walkFileTree(root, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
			{
				return !dir.equals(root) && TOOL_DIRECTORIES.contains(dir.getFileName().toString())
					? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
			{
				String relative = root.relativize(file).toString().replace('\\', '/');
				if (relative.toLowerCase(Locale.ROOT).endsWith(".md") && !PLAYER_DOCS.contains(relative))
				{
					strays.add(relative);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		assertTrue("Working notes do not belong in this public repository, tracked or not. "
			+ "Keep them outside this checkout: " + strays, strays.isEmpty());
	}

	@Test
	public void readmeSpeaksToPlayersOnly() throws IOException
	{
		String readme = new String(Files.readAllBytes(root().resolve("README.md")),
			StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
		List<String> found = new ArrayList<>();
		for (String phrase : NOT_FOR_PLAYERS)
		{
			if (readme.contains(phrase))
			{
				found.add(phrase);
			}
		}
		assertTrue("The readme is for players: no branches, local paths or development notes. Found "
			+ found, found.isEmpty());
	}

	private static Path root() throws IOException
	{
		Path root = Paths.get("").toAbsolutePath();
		assertTrue("expected to run from the repository root: " + root,
			Files.exists(root.resolve("README.md")) && Files.exists(root.resolve("build.gradle")));
		return root;
	}
}
