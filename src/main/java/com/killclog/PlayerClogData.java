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
	/**
	 * Whether this client completed the full Collection Log Search walk.
	 * Null is a pre-2.3.3 file; those use the first-party marker migration rule.
	 * False keeps provider-only and live-unlock-only stores in setup.
	 */
	Boolean firstPartySetupComplete;
	/**
	 * Per-category item ids this CLIENT observed first-hand (bulk
	 * capture, page capture, live unlock). The sync payload ships only
	 * records marked IN THEIR OWN CATEGORY - a global id mark would let
	 * a provider record of the same item in another category launder
	 * through (multi-category items are routine: clue rares span pages).
	 * Null means a legacy pre-marking store file: those were built by
	 * this client's own captures, so the store ships whole and the first
	 * capture grandfathers everything obtained at that moment. Live
	 * provider writes initialize the field EMPTY instead, which never
	 * grandfathers.
	 */
	Map<String, List<Integer>> firstPartyByCategory;
}
