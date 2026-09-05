package com.killclog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader for the plugin's bundled catalog tables: tab-separated rows, one per
 * line, {@code #} comment lines skipped. Catalog data ships as versioned
 * resources beside the classes that own it, in the same shape as
 * {@code ehb-rates.csv}.
 */
@Slf4j
final class CatalogTsv
{
	private CatalogTsv()
	{
	}

	/**
	 * Rows with exactly {@code columns} fields. A malformed row is logged and
	 * skipped rather than failing the load; the catalog-parity tests pin the
	 * complete expected contents at build time.
	 */
	static List<String[]> rows(Class<?> owner, String resource, int columns)
	{
		List<String[]> rows = new ArrayList<>();
		try (InputStream in = owner.getResourceAsStream(resource))
		{
			if (in == null)
			{
				log.warn("catalog resource missing: {}", resource);
				return rows;
			}
			BufferedReader reader = new BufferedReader(
				new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] fields = line.split("\t", -1);
				if (fields.length != columns)
				{
					log.warn("catalog row in {} has {} fields, expected {}: {}",
						resource, fields.length, columns, line);
					continue;
				}
				rows.add(fields);
			}
		}
		catch (IOException e)
		{
			log.warn("catalog resource unreadable: {}", resource, e);
		}
		return Collections.unmodifiableList(rows);
	}
}
