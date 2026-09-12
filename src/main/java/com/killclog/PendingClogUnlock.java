package com.killclog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;

/** Unresolved live evidence, stored with its owning player's cache. */
@EqualsAndHashCode
final class PendingClogUnlock
{
	final List<Integer> candidates;
	final String date;
	final String ownerHash;

	PendingClogUnlock(List<Integer> candidates, String date, String ownerHash)
	{
		this.candidates = new ArrayList<>(new java.util.TreeSet<>(candidates));
		this.date = date;
		this.ownerHash = ownerHash;
	}

	/** Reconcile only a unique candidate. Multiple matches cannot establish order. */
	static List<PendingClogUnlock> reconcile(List<PendingClogUnlock> pending,
		Map<String, List<ClogResult.ClogItem>> obtained, String ownerHash)
	{
		// A session transition cannot consume evidence without its account anchor.
		// Preserve null too, so an unchanged capture stays unchanged after upgrade.
		if (pending == null || obtained == null || ownerHash == null) return pending;
		List<PendingClogUnlock> remaining = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		for (List<ClogResult.ClogItem> items : obtained.values())
		{
			for (ClogResult.ClogItem item : items) ids.add(item.getId());
		}
		Map<Integer, String> resolved = new HashMap<>();
		for (PendingClogUnlock event : pending)
		{
			if (event == null || event.candidates == null) continue;
			if (!ownerHash.equals(event.ownerHash)) continue;
			String iso = ClogDates.iso(event.date);
			if (iso == null) continue;
			List<Integer> matches = new ArrayList<>();
			for (int id : event.candidates) if (ids.contains(id)) matches.add(id);
			if (matches.isEmpty()) remaining.add(event);
			else if (matches.size() == 1)
			{
				resolved.merge(matches.get(0), ClogDates.local(iso),
					(a, b) -> a.compareTo(b) <= 0 ? a : b);
			}
		}
		for (Map.Entry<String, List<ClogResult.ClogItem>> category : obtained.entrySet())
		{
			List<ClogResult.ClogItem> updated = new ArrayList<>();
			for (ClogResult.ClogItem item : category.getValue())
			{
				String date = resolved.get(item.getId());
				updated.add(item.getDate() == null && date != null
					? new ClogResult.ClogItem(item.getId(), item.getCount(), date,
						item.getObtainedAtKc(), item.getObtainedFrom()) : item);
			}
			category.setValue(updated);
		}
		return remaining;
	}
}
