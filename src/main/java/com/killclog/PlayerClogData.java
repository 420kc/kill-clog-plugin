package com.killclog;

import java.util.List;
import java.util.Map;

/** One player's locally cached collection log, as persisted by
 *  {@link LocalClogCache} (one JSON file per player). */
class PlayerClogData
{
	String playerName;
	String lastUpdated;
	String lastChanged;
	// Which account hash wrote this file: claims in the identity ledger
	// order NAMES, but the bytes belong to whoever wrote them. Parking
	// targets this over any claim-derived guess. Null on legacy files and
	// pure lookup caches.
	String ownerHash;
	AccountType providerAccountType;
	int uniqueObtained = -1;
	int uniqueTotal = -1;
	Map<String, List<Integer>> categories;
	Map<String, List<ClogResult.ClogItem>> obtained;
	List<PendingClogUnlock> pendingUnlocks;
	/**
	 * Whether this client completed the full Collection Log Search walk. False (and a missing or null field in an
	 * older file) keeps provider-only and live-unlock-only stores in setup.
	 */
	boolean firstPartySetupComplete;
	// Hardcore Worlds: the character's total XP as last seen there, and when a reset started this log over and at
	// what XP (null and 0 for the first run). XP only ever grows, so a fall is a reset.
	long overallXp;
	String runStartedAt;
	long runStartXp;
}
