# Roadmap Wiring Status

**Date:** 2026-08-06
**Branch:** `main` (worktree `F:\worktrees\find-it-main`)
**Supersedes:** [`PHASE_WIRING_AUDIT.md`](PHASE_WIRING_AUDIT.md) (2026-08-04, now stale — written before the
fix-and-wire-all pass below; kept for history, not current status).

This tracks, phase by phase, which [ROADMAP.md](../ROADMAP.md) items are backed by real production UI call
sites versus unit tests only — verified by reading the actual code, not by trusting the roadmap's own
labels. Two passes fed this file: an initial 4-agent audit (2026-08-05) that found 8 well-tested pieces of
logic with zero production callers, a "fix and wire all" implementation pass that closed every one of
them (2026-08-06), and a second independent 4-agent verification pass (2026-08-06) that re-checked the
fixes and caught one of them still broken in practice.

## Summary

| Phase | Engines / unit tests | Production UI | Fully wired? |
| ----- | --------------------- | -------------- | ------------- |
| 1 Core workflows | Yes | Yes | **Yes** |
| 2 Tile acquisition / mosaics | Yes | Yes (area picker, queue, resume) | **Mostly** — on-device multi-tile release validation still open (Sprint 2 item 4) |
| 3 Historic-feature analysis | Yes | Yes (AI workspace + evidence) | **Mostly** — field-area false-positive metrics require field data, not code |
| 4 Performance architecture | Partial | Partial | **Partial** (not re-audited this pass) |
| 5 Field verification | Yes | Yes | **Yes for offline record path** (AR guidance deferred, device-bound) |
| 6 Historic-map intelligence | Yes | Yes | **Yes** — including manual feature tracing (`HistoricMapFeatureBar`), AI-assisted auto-extraction (`HistoricMapFeatureExtractor`, confirm-write), and the ranking-adjustment feed into `MetalDetectingTargetRefiner` |
| 7 ML ranking | Yes | Yes | **Yes** — "Train ranker" now runs a real spatial holdout (`RankerHoldoutEvaluator`) and hard-negative mining, not just in-sample accuracy; regional datasets remain field-data dependent |
| 8 Advanced terrain tools | Yes | Yes | **Yes** — horizon-line UI (`HorizonCard`) now wired alongside viewshed/profile/compare |
| 9 Interop / cloud | Partial writers | Partial exports | **Mostly** — GeoTIFF/Shapefile/KMZ/QGIS-project/portable-archive all real and UI-reachable; GeoPackage export, image-bundle packaging, QR sharing, and cloud backup/sync remain not started. Conflict resolution during archive import is now genuinely reachable (see below), not just present. |

## What changed 2026-08-06 (fix-and-wire-all pass)

Eight previously well-tested, zero-production-caller pieces of logic were wired to real UI:

1. `MapTerrainAgreement.rankingAdjustment()` → `MetalDetectingTargetRefiner` (via saved `HistoricMapFeature`
   points sampled in `AiAnalysisWorkspace.kt`), with an evidence string on affected candidates.
2. `TerrainViewshedAnalyzer.horizon()` → "Horizon from here" button in `TerrainCellInspectionPanel`, new
   `HorizonCard` result display.
3. `SyncConflictResolver` → portable-archive import/merge (`HillshadeViewModel.importProjectArchive`),
   reachable from the Tools tab export dialog.
4. Directional photo bearings (`photoBearingsDegrees`) → real compass capture, displayed as "Facing NE 47°"
   in the find-edit dialog, persisted via Room migration v16→v17.
5. `HistoricMapFeature` manual entry → `HistoricMapFeatureBar` in the historic map panel: trace points on
   the map, save, scored against real terrain relief (`MapTerrainAgreement.rasterizePolyline`/`.score`).
6. `ReviewedCandidateExample` writes → `HillshadeViewModel.updateLoggedSignal` appends only on an actual
   outcome change, not every field edit.
7. `SpatialFoldSplitter` + `HardNegativeMiner` → "Train ranker" flow reports real held-out accuracy and
   hard-negative count via the new `RankerHoldoutEvaluator`.
8. Real on-device voice dictation (Android `SpeechRecognizer`) → mic button in `AiCloudPanel`, feeding the
   same draft box the structured-find tag parser already reads.

All 8 verified end-to-end (file:line traced) by a follow-up 4-agent audit, split as Phase 5&6, Phase 7&8,
Phase 9 + Site Package Pack, and AI packs + Product pack.

## What that audit caught still broken

- **Sync-conflict "review" path was unreachable.** The import call always passed
  `baseUpdatedAtMillis = null` to `SyncConflictResolver.resolve()`, which per the resolver's own logic can
  only return `LOCAL_WINS`/`REMOTE_WINS` on a null base — `MERGE_REQUIRED` could never fire, so a genuine
  both-sides-changed conflict silently picked a winner instead of being held for review, contradicting the
  roadmap's own claim. **Fixed:** `TargetSignal` gained real `updatedAtMillis` (bumped on every edit,
  distinct from the fixed-at-creation `timestamp`) and `lastSyncedAtMillis` (stamped on export and on fresh
  import — the common-ancestor base). Database bumped v17→v18. Archive codec carries `updatedAtMillis` with
  a fallback to `timestamp` for archives written before the field existed.
- **Stale doc contradiction.** `ROADMAP.md` and `docs/FEATURES_PRODUCT_PACK.md` both still said AI
  confirm-write was "later polish," while it had already shipped with the Site Package Pack
  (`AiCloudPanel.kt` "Confirm write" → `HillshadeViewModel.applyAiFindSuggestions`). Corrected in both
  files, plus a similarly stale "not merged to main" branch note in `docs/FEATURES_AI_PACK3_PLAN.md`.

## What changed 2026-08-06, later (AI-assisted feature extraction)

Closed one of the three future-work items below: automatic historic-map feature extraction.
`HistoricMapFeatureExtractor` (new, `com.example.ai`) sends the active georeferenced historic-map bitmap to
the configured cloud AI provider and asks it to trace visible roads/structures/walls/boundaries as
normalized-image-coordinate polylines, parsed from a strict `FEATURE|TYPE|x1,y1;x2,y2|description` line
format (7 unit tests covering well-formed input, `NONE`, unknown types, malformed lines, and out-of-range
coordinates). Proposals render on the map in a distinct color via a new
`HistoricMapAiFeatureReviewCard` and are **never** written to `historicMapFeatureDao` until the user taps
Save on each one individually — an accepted proposal is converted from image pixels to lat/lon via the
map's real `GeoReferenceTransform` and scored against real terrain relief with the same
`MapTerrainAgreement` path manual tracing already used, so an AI-accepted feature is indistinguishable in
quality from a hand-traced one. Requires the map to be georeferenced first (a transform must exist to place
the proposals) and a cloud AI provider configured; both are surfaced as disabled-state/error messages, not
silent failures.

## Still open (not code gaps — flagged, not yet acted on)

- **Possible PDF-export contradiction, unverified.** `ROADMAP.md` Phase 9 says "styled PDF report export
  remains future work," but `HillshadeViewModel.kt` already builds a `field-report.pdf`, and the Site
  Package Pack table separately claims "Styled field PDF... Done." Not yet checked which is accurate.

## Genuinely not started (confirmed, not a wiring gap)

- **Full ARCore dig guidance** (Phase 5) — device-bound future work. What exists today (compass ring,
  bearing/distance readout) is AR-lite only, not a camera-passthrough overlay.
- **Two-epoch change detection** (Phase 8) — comparing two LiDAR captures of the same extent from
  different dates to surface new/removed ground features. No engine, no UI, and no path today to acquire
  or pair two co-registered tile sets for the same area.
- GeoPackage export (Phase 9).
- Image bundles / annotated-map packaging (Phase 9).
- QR project sharing (Phase 9) — portable-archive payload exists, scan/share UI does not.
- Cloud backup / multi-device sync (Phase 9) — needs an external service; local sync queue and conflict
  resolver are the prerequisites already in place.
- On-device multi-tile release validation (Sprint 2 item 4) and the release-checklist items (interrupted-
  network recovery, large multi-tile reopening on a release APK, external-GIS export-file validation) — QA
  tasks, not code.
