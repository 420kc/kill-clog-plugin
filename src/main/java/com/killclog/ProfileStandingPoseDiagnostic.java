package com.killclog;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/** Temporary local-only standing pose capture. Remove after the parity comparison. */
@Slf4j
final class ProfileStandingPoseDiagnostic
{
	private static final int MAGIC = 0x4B435044;
	private static final int VERSION = 1;

	private ProfileStandingPoseDiagnostic()
	{
	}

	static void write(ProfileCardPlayerModel.Snapshot model, int idlePose)
	{
		Path directory = RuneLite.RUNELITE_DIR.toPath()
			.resolve("kill-clog").resolve("diagnostics");
		Path output = directory.resolve("standing-pose.kcpd");
		Path temporary = directory.resolve("standing-pose.kcpd.tmp");
		try
		{
			Files.createDirectories(directory);
			try (DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(
				Files.newOutputStream(temporary))))
			{
				stream.writeInt(MAGIC);
				stream.writeInt(VERSION);
				stream.writeInt(idlePose);
				stream.writeInt(model.x.length);
				stream.writeInt(model.a.length);
				for (int vertex = 0; vertex < model.x.length; vertex++)
				{
					stream.writeFloat(model.x[vertex]);
					stream.writeFloat(model.y[vertex]);
					stream.writeFloat(model.z[vertex]);
				}
				for (int face = 0; face < model.a.length; face++)
				{
					stream.writeInt(model.a[face]);
					stream.writeInt(model.b[face]);
					stream.writeInt(model.c[face]);
				}
			}
			Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
			log.info("Kill Clog standing pose captured locally: {}", output);
		}
		catch (IOException e)
		{
			log.warn("Kill Clog standing pose diagnostic failed", e);
		}
	}
}
