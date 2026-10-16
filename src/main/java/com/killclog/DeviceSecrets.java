package com.killclog;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Filepath;

/**
 * Where the character-publish credentials live: a file in Kill Clog's own data folder, so a secret made for this
 * device stays on it. RuneLite config travels with profile sync, to RuneLite's cloud and every PC on the profile.
 * Values held in config by earlier releases move into the file the first time they're read, and leave config.
 * A session without a data folder (memory-only) keeps using config, as before.
 */
@Slf4j
final class DeviceSecrets
{
	static final String FILE = "device-secrets.json";

	private final ConfigManager configManager;
	private final String configGroup;
	private final LocalClogCache store;
	private final Gson gson;
	private Map<String, String> held;

	DeviceSecrets(ConfigManager configManager, String configGroup, LocalClogCache store, Gson gson)
	{
		this.configManager = configManager;
		this.configGroup = configGroup;
		this.store = store;
		this.gson = gson;
	}

	@Nullable
	synchronized String read(String key)
	{
		Filepath folder = store.folder().getNow(null);
		String inConfig = configManager.getConfiguration(configGroup, key, String.class);
		if (folder == null)
		{
			return inConfig;
		}
		Map<String, String> values = load(folder);
		if (values.containsKey(key))
		{
			return values.get(key);
		}
		if (inConfig != null)
		{
			write(key, inConfig);
		}
		return inConfig;
	}

	synchronized void write(String key, @Nullable String value)
	{
		Filepath folder = store.folder().getNow(null);
		if (folder == null)
		{
			if (value == null) configManager.unsetConfiguration(configGroup, key);
			else configManager.setConfiguration(configGroup, key, value);
			return;
		}
		Map<String, String> values = new HashMap<>(load(folder));
		if (value == null) values.remove(key);
		else values.put(key, value);
		try
		{
			StoreMigration.write(gson, folder, folder.join(FILE), values);
			held = values;
			configManager.unsetConfiguration(configGroup, key);
		}
		catch (Exception e)
		{
			// The file couldn't be written: config keeps the value rather than lose it.
			log.warn("Kill Clog could not save its device credentials: {}", e.getMessage());
			if (value == null) configManager.unsetConfiguration(configGroup, key);
			else configManager.setConfiguration(configGroup, key, value);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> load(Filepath folder)
	{
		if (held == null)
		{
			Filepath file = folder.join(FILE);
			Map<String, String> read = file.exists() ? StoreMigration.read(gson, file, Map.class) : null;
			held = read != null ? read : new HashMap<>();
		}
		return held;
	}
}
