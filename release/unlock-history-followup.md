# 2.4 unlock-history follow-up

Historical receipt: the temporary branches/worktrees below were retired during
canonical consolidation. Current work is `C:/Users/dylan/plugins/kcpdev` on
`hive/2.4.0`; the release checklist links their recovery archive.

Base: previous candidate `85d71da9` / code `1952dfeb`, formerly preserved as
`hive/2.4.0-before-unlock-history`. Work was built in `killclog-240-unlock-dates`,
branch `hive/2.4.0-unlock-dates`, and adopted into `hive/2.4.0-candidate` after
independent review and Dylan's smoke approval on 2026-09-12. Current release truth
and remaining gates live in [kill-clog-next-release.md](kill-clog-next-release.md).

The Chompy bird hat report established two gaps: ambiguous live names discarded
their event time, and web sync carried no acquisition dates. Item 2991 was present
in the inspected local cache without a date. No user cache has been edited.

- Retain ambiguous live candidates with their original UTC time and account hash
  inside that account's cache, using the existing guarded disk writer. Duplicate
  personal/clan notifications coalesce. Storage is bounded to 32 pending groups,
  each with at most 256 candidates.
- Require a completed local capture before recording ambiguous candidates;
  incomplete provider snapshots cannot establish which items were missing.
  Pending evidence survives session gaps and account rename migration.
- On a complete first-party capture, one matching candidate gains the observed
  date in every category. No matches keep the pending event. Multiple matches
  cannot establish which item received that time and are discarded without
  inventing dates. A different account cannot consume another account's event.
- Existing dated items and undated historical imports keep their history. Unknown
  event names or failed initial catalog reads remain unsupported by this focused
  ambiguous-variant fix; never claim every historical unlock can be recovered.
- Sync sends valid optional `obtained_at`; first-party lookup reads it and still
  ignores server receipt timestamps. API companion work preserves the field and
  projects dated Recent entries for the existing web normalizer.

Verification: persisted pending-event round trip, duplicate notifications,
multi-category dedup, account isolation, ambiguous/historical captures, date
validation, payload/proof-view round trip, and unchanged-date repeat refresh.
Final code `e403c7d8` passed compile/Checkstyle/test/jar gates (624 tests) and
independent reviews. The follow-up quiet local update preserves the populated
panel without a new provider lookup; comparison colors and totals also refresh.
The exact jar and Dylan-approved smoke are recorded in the owning release note.

API companion: `C:/Users/dylan/.codex/worktrees/collection-unlock-dates`, branch
`hive/collection-unlock-dates`, based on `d6b7d2b5`. Deploy API support before the
plugin release relies on web date round trips. This slice neither deploys nor
repairs the already-missed hat. Its original time needs a separate evidence-based
repair; refreshing now cannot recreate a discarded event.

The architectural review's ownership-recovery finding F1 remains to be validated
separately before final submission. No package reorganization is included.
