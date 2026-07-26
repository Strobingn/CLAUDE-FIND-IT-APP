# Sprint 1 Core Workflow Audit

**Audit date:** 2026-07-26  
**Roadmap:** [ROADMAP.md](../ROADMAP.md)

This audit checks the production UI and persisted state paths against the Phase 1 definition of done. A feature is not marked complete solely because code exists.

| Workflow | Current evidence | Status | Next acceptance work |
|---|---|---|---|
| Manual terrain refinement | Terrain, AI, and Compare call `HillshadeViewModel.refineTerrain` directly. Manual controls are enabled whenever a reopenable source exists, independent of zoom. Automatic refinement retains zoom thresholds to avoid needless work. | Implemented; debug verified | Add an instrumented test at 1x and complete release-build validation. |
| Exact-cell inspection | Terrain Explore mode now maps a tap through the active zoom/pan transform to one source raster cell. The panel reports validity, elevation, bare earth, canopy height, slope, aspect, curvature, local relief, ruggedness, depression depth, openness, linearity, resolution, neighborhood support, and coordinates when georeferenced. | Implemented; emulator and tablet verified | Add instrumented tap/pan/zoom coverage and release-build validation. |
| AI dig-location markers | AI candidates are labeled on the terrain, can be written as saved finds, and carry both dataset and terrain keys. Saved markers are filtered to the active terrain source. | Implemented; debug verified | Add process-restart and migration instrumented tests. |
| Synchronized layer comparison | Compare renders two terrain layers from one grid with a shared zoom and pan state and supports manual visible-area refinement. | Implemented; debug verified | Add screenshot and gesture synchronization regression tests. |
| Multi-dataset candidate comparison | Analyzed dataset snapshots persist and can be compared by geographic proximity. | Implemented; unit coverage present | Validate with two independently imported, overlapping georeferenced datasets. |
| NYS/USGS tile discovery | The Import tab provides coordinate lookup through the USGS/NYS catalog and downloads an exact LAZ tile with progress and cancellation. | Partial | Replace point lookup with reusable rectangle/polygon/radius area selection, size estimate, multi-tile queue, retry, grouping, and mosaic open. |
| GPX/KML survey workflow | Field finds can be exported to GPX/KML. A complete survey import, map display, and project-persistence workflow is not exposed. | Partial | Define survey-layer storage, import validation, UI visibility, restart recovery, and round-trip tests. |
| Offline basemap regions | Online basemap tiles can be stitched for the active terrain extent. User-managed offline region download and recovery are not complete. | Partial | Add region records, size estimate, queue/cancel/retry, cache inspection, and airplane-mode tests. |
| Image and report export | CSV, GPX, KML, and GeoJSON field exports exist. Full terrain image, annotated comparison image, and PDF report workflows are absent. | Partial | Implement full-resolution image export first, then a versioned report schema and PDF renderer. |
| Multi-tile projects | Individual downloaded tiles persist and reopen. Logical project grouping, source-preserving mosaics, duplicate prevention, and partial-project recovery are incomplete. | Partial | Implement the Phase 2 tile/project schema before adding mosaic rendering. |
| Release validation | Debug unit tests and APK builds pass, and current workflows run on the emulator and Samsung tablet. | Partial | Add release build, instrumented suite, migration fixtures, and external GIS export checks to the release gate. |

## First completed increment

The first Sprint 1 increment implements exact-cell inspection from the production Terrain workspace.

Data-integrity behavior:

- Measurements come from the source `ElevationGrid`, not the rendered bitmap.
- The selected cell remains the same at any visual zoom.
- No-data cells are labeled and do not display terrain measurements.
- Geographic coordinates are shown only when the dataset has real geographic bounds.
- Local-grid data remains explicitly local.
- Inspection does not rerender terrain or reset the viewport.

Validation:

- Unit tests cover planar metrics and no-data handling.
- Full debug unit-test and APK build passes.
- Live emulator and Samsung tablet taps display the measurement panel over a real imported LAZ.

## Next implementation target

Build the reusable area-selection contract for NYS/USGS tile acquisition:

1. Represent rectangle, polygon, and radius selections.
2. Intersect selections with tile bounds.
3. Return exact tile identities, source URLs, and estimated sizes.
4. Reuse the same selector from every terrain-import entry point.
5. Preserve cancellation and existing-file reuse.
