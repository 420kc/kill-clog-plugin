package com.killclog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Efficient Hours Bossed: hiscore kill counts divided by community kill rates.
 * EHB rates by TempleOSRS (templeosrs.com), bundled as a versioned resource
 * and refreshed each release. A zero rate means the boss awards no EHB for
 * that mode; an unranked kill count awards nothing.
 */
@Slf4j
final class EhbRates
{
	private static final String RESOURCE = "ehb-rates.csv";

	// Boss name (hiscore CSV form) -> [main rate, ironman rate].
	private static final Map<String, double[]> RATES = load();

	private EhbRates()
	{
	}

	/**
	 * Sum of kc/rate across rated bosses, or -1 without hiscore data.
	 * GIMs only reach the hiscores through the regular table, so the
	 * resolved account type outranks the raw hiscore tag; every ironman
	 * mode uses the ironman column.
	 */
	static double compute(HiscoreResult result, AccountType resolvedType)
	{
		if (result == null)
		{
			return -1;
		}
		AccountType type = resolvedType != null ? resolvedType : result.getAccountType();
		boolean ironman = type != null && type != AccountType.REGULAR;
		double hours = 0;
		for (Map.Entry<String, double[]> entry : RATES.entrySet())
		{
			double rate = entry.getValue()[ironman ? 1 : 0];
			if (rate <= 0)
			{
				continue;
			}
			int kc = result.getKc(entry.getKey());
			if (kc > 0)
			{
				hours += kc / rate;
			}
		}
		return hours;
	}

	/** Read-only view for resource-integrity tests. */
	static Map<String, double[]> rates()
	{
		return Collections.unmodifiableMap(RATES);
	}

	private static Map<String, double[]> load()
	{
		Map<String, double[]> rates = new LinkedHashMap<>();
		try (InputStream in = EhbRates.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("EHB rates resource missing");
				return rates;
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
				// Boss names hold no commas; the last two fields are rates.
				int imSep = line.lastIndexOf(',');
				int mainSep = line.lastIndexOf(',', imSep - 1);
				String name = line.substring(0, mainSep);
				double main = Double.parseDouble(line.substring(mainSep + 1, imSep));
				double im = Double.parseDouble(line.substring(imSep + 1));
				rates.put(name, new double[]{main, im});
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("EHB rates failed to load", e);
		}
		return rates;
	}
}
