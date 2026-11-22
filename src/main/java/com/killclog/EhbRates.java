package com.killclog;

import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Efficient Hours Bossed: the hiscores' kill counts divided by TempleOSRS's kill rates (templeosrs.com), which ship
 * with the plugin and are refreshed each release. A rate of zero means the boss gives no EHB in that mode; a boss the
 * hiscores don't rank gives none.
 */
@Slf4j
final class EhbRates
{
	private static final String RESOURCE = "ehb-rates.csv";

	// Boss name (hiscore form) -> [main rate, ironman rate].
	private static final Map<String, double[]> RATES = load();

	private EhbRates()
	{
	}

	/**
	 * The sum of kill count over rate for every rated boss, or -1 with no hiscore or no boss on it (a board's stand-in
	 * row). A Group Ironman is listed on the regular board only, so the account type the lookup settled on stands over
	 * the board's; every ironman mode uses the ironman rates.
	 */
	static double compute(HiscoreResult result, AccountType resolvedType)
	{
		if (result == null || result.getBossKills().isEmpty())
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

	private static Map<String, double[]> load()
	{
		Map<String, double[]> rates = new LinkedHashMap<>();
		try
		{
			for (String line : CatalogTsv.lines(EhbRates.class, RESOURCE))
			{
				// Boss names hold no commas; the last two fields are rates.
				int imSep = line.lastIndexOf(',');
				int mainSep = line.lastIndexOf(',', imSep - 1);
				String name = line.substring(0, mainSep);
				double main = Double.parseDouble(line.substring(mainSep + 1, imSep));
				double im = Double.parseDouble(line.substring(imSep + 1));
				rates.put(name, new double[]{main, im});
			}
		}
		catch (RuntimeException e)
		{
			log.warn("EHB rates failed to load", e);
		}
		return rates;
	}
}
