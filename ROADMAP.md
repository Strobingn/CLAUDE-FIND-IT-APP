# Find It App Roadmap

**Status:** Active  
**Repository:** <https://github.com/Strobingn/Find-It-App>  
**Last reviewed:** 2026-08-03

## Product objective

Find It is an offline-capable Android LiDAR and terrain-analysis application focused on locating historic human activity in wooded and overgrown terrain, primarily from the 1500s through the 1800s.

The core workflow is:

1. Select an area.
2. Download the correct LAS/LAZ tiles.
3. Build the best available bare-earth terrain model.
4. Generate terrain-analysis layers.
5. Detect likely historic human-made features.
6. Rank targets by historic-site value.
7. Review explainable candidates.
8. Navigate to selected targets.
9. Record field outcomes.
10. Use verified results to improve future ranking.

LiDAR ranks surface morphology and historic-activity context. It does not directly identify buried metal, artifact age, composition, or exact depth.

## Product priorities

Find It should prioritize:

- Historic foundations, platforms, and terraces
- Cellar holes
- Old wagon roads, cart paths, and abandoned lanes
- Stone walls and old field boundaries
- Trash pits and refuse zones
- Old homesites and related feature clusters
- Cuts, fills, disturbed ground, and remote signs of occupation
- Pre-Civil-War and Civil War-era activity
- Locations with strong potential for older coins, buttons, buckles, tools, military relics, and household artifacts

## Roadmap principles

### Improve without regression

- Do not remove a working feature to simplify a new one.
- Do not replace production workflows with placeholders or mock data.
- Preserve zoom, pan, rotation, and the current image while new work is processing.
- Avoid expensive rerenders when only the viewport changes.
- Cancel stale jobs and prevent them from overwriting newer results.
- Do not fabricate coordinates, classifications, metal type, age, or depth.
- Report uncertainty honestly.
- Treat broken CI, builds, imports, migrations, and rendering as release blockers.

### Historic-site value comes first

Major work should improve one or more of:

- Historic-site discovery
- Tile selection and acquisition
- Candidate accuracy and explainability
- Natural and modern false-positive rejection
- Terrain rendering performance
- Field navigation and documentation
- GIS export and long-term project portability

### Ground quality is critical

Bare-earth quality is more important than canopy visualization. Processing preference:

1. Source-classified ground
2. Reliable automatic ground fallback
3. Multi-scale smoothing and denoising
4. Ground-quality reporting
5. Visual comparison with highest-return surface

### Field verification remains mandatory

Every candidate should expose:

- Candidate type
- Confidence or priority score
- Supporting evidence
- Negative evidence and plausible natural explanation
- Approximate search radius
- Geographic coordinates when available
- Processing and model versions
- Field-verification status

## Verified current baseline

The current application includes:

- Professional landing dashboard and earth-tone Material 3 interface
- LAS, LAZ, GeoTIFF, HTTPS, and ZIP import workflows
- On-device LAZ decompression
- Source-classified ground, automatic lowest-return ground, and highest-return DSM modes
- Large aspect-correct terrain rasters with no-data handling
- Pinch zoom, two-axis pan, rotation, reset, and level-of-detail rendering
- Source-based visible-area refinement
- Stable AI terrain viewport
- Local terrain intelligence and explainable candidate summaries
- AI-generated target marker workflow
- Per-dataset saved markers
- Persistent imported-terrain and derived-layer recovery
- Google Maps terrain overlay with per-file position, width, height, rotation, and opacity alignment
- Historic map image import with manual position, scale, rotation, opacity, and visibility alignment
- NYS/USGS coordinate-to-tile lookup and LAZ download
- Rectangle-based USGS 3DEP area selection, source-preserving multi-tile mosaics, and resumable partial-project recovery
- Side-by-side layer comparison
- Multi-dataset candidate comparison
- Saved finds and photo attachments
- Phone magnetometer anomaly mode
- CSV, GPX, KML, and GeoJSON field-data export
- Memory and persistent caches
- Automated unit tests and debug APK builds

Items in this baseline are not considered complete unless their full acceptance criteria below are met in a release build.

## Development priorities

### Priority 1 — Accurate historic-target ranking

**Objective:** Keep ranking focused on unexpected signs of human activity in modern wooded terrain.

Highest-value algorithm work:

- Improved ground-class handling
- Multi-scale Local Relief Model
- Foundation-edge and platform geometry
- Cellar-hole center, depression, and rim geometry
- Continuous wagon-road detection
- Stone-wall continuity
- Refuse-pit context near possible homesites
- Historic-map agreement
- Terrain-age and human-activity context
- Natural and modern-disturbance suppression
- Candidate clustering and duplicate suppression

Suggested scoring model:

```text
candidate score =
    cellar geometry
  + foundation/platform evidence
  + wagon-road proximity and continuity
  + stone-wall continuity
  + refuse-pit context
  + historic-map agreement
  + terrain-age context
  + nearby human-feature clustering
  - natural-feature probability
  - modern-disturbance probability
```

Acceptance criteria:

- Ranking combines multiple features rather than one raster threshold.
- Every candidate has a calibrated confidence score and strongest supporting reasons.
- Natural depressions, drainage channels, root throws, wetlands, and modern grading are explicitly penalized.
- Roads, walls, platforms, cellar geometry, and refuse features reinforce one another when clustered.
- Verified rejected targets lower similar future scores.
- Verified productive targets raise similar future scores.
- Ranking works across dense, sparse, and mixed-quality point clouds.
- Candidate locations remain spatially stable across zoom levels.

### Priority 2 — Tile-to-area download and mosaics

**Objective:** Let the user select an area and receive the exact tiles covering it.

Required components:

- Map rectangle, polygon, and radius selection
- Tile-index ingestion and caching
- Bounds intersection
- Exact filename and source resolution
- Multi-tile selection
- File-count and storage estimate
- Download queue with progress
- Pause, cancellation, retry, and failure recovery
- Existing-tile reuse and duplicate prevention
- Dataset grouping and mosaic opening
- Partial-project recovery
- Offline project reopening
- Source metadata attached to every tile and project

Workflow:

```mermaid
flowchart TD
    A[User selects area] --> B[Resolve data source]
    B --> C[Load tile index]
    C --> D[Intersect area with tile bounds]
    D --> E[Resolve exact filenames]
    E --> F[Estimate count and size]
    F --> G[User confirms]
    G --> H[Queue downloads]
    H --> I[Validate files]
    I --> J[Group tiles into project]
    J --> K[Open logical mosaic]
```

Acceptance criteria:

- The same area picker is reachable from every terrain-import path.
- Selected boundaries and intersecting tiles are clearly visible.
- File count and expected storage are shown before download.
- Cancellation cannot corrupt the project.
- Failed files can be retried individually.
- Existing valid tiles are reused.
- Completed tiles open as one logical mosaic.
- Acquisition and coordinate-reference metadata remain attached.

### Priority 3 — Maximum render performance

**Objective:** Support large projects without reducing analytical accuracy or visual stability.

Data loading:

- Stream large point-cloud files.
- Decode only required sections when possible.
- Reuse decoded tile buffers.
- Prevent duplicate loading of shared tiles.
- Make loading jobs cancellable.
- Check memory pressure before expensive work.

Raster generation:

- Cache hillshade, slope, LRM, curvature, openness, and related derived layers.
- Rerasterize only affected bounds.
- Use zoom-aware output resolution.
- Cancel obsolete raster jobs.
- Preserve the prior raster until its replacement is ready.

Rendering:

- Use GPU-backed layer composition where beneficial.
- Select level of detail by zoom and device capability.
- Avoid full-screen redraws for local changes.
- Synchronize comparison viewports without duplicate processing.
- Prevent zoom snapping or viewport movement during loading.
- Add frame-time, memory, cache-hit, and cancellation diagnostics.

Validation:

- Benchmark small tiles, large mosaics, sparse clouds, dense clouds, and low-memory devices.
- Exercise repeated zoom, pan, rotation, layer switching, and refinement.
- Record open time, render latency, peak memory, frame time, cache-hit rate, and cancellation rate.

### Priority 4 — Offline field workflow

**Objective:** Move from analysis to field checking without losing project context.

Target lifecycle:

```mermaid
stateDiagram-v2
    [*] --> Unreviewed
    Unreviewed --> Selected
    Selected --> Navigating
    Navigating --> Checked
    Checked --> Rejected
    Checked --> Productive
    Checked --> Inconclusive
    Rejected --> [*]
    Productive --> ModelFeedback
    Inconclusive --> FollowUp
    ModelFeedback --> [*]
```

Required capabilities:

- GPS breadcrumbs with pause and resume
- Distance, bearing, compass-oriented navigation, and accuracy
- AR guidance with a reliable non-AR fallback
- Voice notes and directional photos
- Excavation and target-state logging
- Property and permission boundaries
- Ranked-target route optimization
- Offline target, note, photo, and boundary access
- Append-safe synchronization when connectivity returns
- Immediate project-statistics updates
- Verified outcome export for field reports, GIS, and model training

## Planned phases

### Phase 1 — Finish incomplete core workflows

- Audit every partially implemented feature against the definition of done.
- Finish GPX/KML survey import, display, and persistence.
- Complete offline basemap-region downloads.
- Expose the NYS/USGS area picker across every import path.
- Enable manual refinement at every zoom level.
- Complete AI dig-location marker creation and persistence.
- Finish exact-cell inspection.
- Finish synchronized side-by-side comparison.
- Complete image and report export.
- Stabilize large multi-tile project reopening.

Exit criteria:

- No import path bypasses the area picker.
- Every partial feature has a complete UI workflow and explicit failure states.
- Large projects reopen without rebuilding unchanged derived data.
- No item is marked complete until it works in a release build.

### Phase 2 — Tile acquisition and mosaics

- Implement tile-index ingestion and geometric intersection.
- Resolve exact filenames and source URLs.
- Add source detection, estimates, queueing, cancellation, retry, and validation.
- Group tiles and open seamless logical mosaics.
- Persist download, source, tile, and project metadata.

Exit criteria:

- A selected area opens the correct completed mosaic.
- Interrupted downloads recover cleanly.
- Duplicate files are not downloaded.
- Projects remain usable offline.

### Phase 3 — Historic-feature analysis

- Improve classification and fallback ground extraction.
- Build multi-scale LRM.
- Add cellar-hole, foundation/platform, road, wall, and refuse-context geometry.
- Add natural-feature and modern-disturbance rejection.
- Add historic-map agreement.
- Produce a reproducible, explainable combined score.

Exit criteria:

- Candidate explanations identify contributing and negative features.
- False positives are measurably reduced on verified test areas.
- Historic human-feature clusters rank above isolated natural anomalies.

### Phase 4 — Performance architecture

- Add cancellable background jobs and stale-work prevention.
- Reuse decoded data and cache derived layers.
- Add focused rerasterization, zoom-based LOD, GPU composition, and memory budgets.
- Add benchmarks and performance regression tests.

Exit criteria:

- Zoom and pan remain stable during processing.
- Memory remains bounded.
- Cached projects reopen quickly.
- Working analytical features and accuracy do not regress.

### Phase 5 — Field verification

- Add breadcrumbs, compass navigation, AR guidance, voice notes, and directional photos.
- Add target states, excavation logs, boundaries, route optimization, and offline sync queue.

Exit criteria:

- A complete field visit can be recorded without connectivity.
- Every observation remains tied to its project and target.
- Synchronization does not duplicate or lose data.

### Phase 6 — Historic-map intelligence

- Add automatic georeferencing with manual control points.
- Add opacity, side-by-side, and swipe alignment tools.
- Extract roads, structures, walls, and boundaries.
- Score map-to-terrain agreement and georeferencing confidence.
- Preserve source and alignment metadata.

Exit criteria:

- Alignment quality is visible and correctable.
- Low-confidence georeferencing is clearly labeled.
- Map agreement informs ranking without overpowering terrain evidence.

### Phase 7 — Machine-learning ranking

- Define a reviewed-example schema.
- Build Hudson Valley cellar-hole and road datasets.
- Train an XGBoost or comparable explainable candidate ranker.
- Use spatially separated training and evaluation areas.
- Add hard-negative mining, model versioning, calibration, rollback, and explanations.

Exit criteria:

- Productive, rejected, and ambiguous examples are retained.
- Models are compared against a rule-based baseline.
- Production models never change silently.
- Every ranked target remains explainable.

### Phase 8 — Advanced terrain tools

- Viewshed analysis
- Horizon-line calculation
- Elevation profile along a selected path
- Adaptive terrain sampling
- Multi-threaded ray processing
- Multi-dataset analysis
- Measurement and profile export

Exit criteria:

- Tools work across single tiles and mosaics.
- Calculations are cancellable and saveable as project layers.
- Exports preserve units and coordinate-reference information.

### Phase 9 — Interoperability and cloud services

- Full terrain-image and report export
- Shapefile, GeoPackage, KMZ, GeoTIFF, and PDF export
- Image bundles and annotated maps
- QR project sharing
- QGIS auto-project creation
- Portable project archives
- Optional cloud backup and multi-device synchronization
- Conflict detection and resolution

Exit criteria:

- Projects move between devices without data loss.
- GIS exports open with correct coordinates, units, attributes, styles, and legends.
- Cloud connectivity is never required for field use.

## Candidate data model

```text
Candidate
├── id
├── projectId
├── geometry
├── latitude
├── longitude
├── elevation
├── candidateType
├── confidence
├── rank
├── status
├── featureEvidence[]
├── negativeEvidence[]
├── historicMapAgreement
├── naturalFeatureProbability
├── modernDisturbanceProbability
├── modelVersion
├── processingVersion
├── createdAt
├── reviewedAt
├── checkedAt
└── observations[]
```

Candidate states:

- `UNREVIEWED`
- `SELECTED`
- `FIELD_CHECK_REQUIRED`
- `CHECKED`
- `REJECTED`
- `PRODUCTIVE`
- `INCONCLUSIVE`
- `FOLLOW_UP_REQUIRED`

## Architecture guardrails

### Data integrity

- Preserve original source files and classifications.
- Record source agency, dataset, acquisition date, resolution, accuracy, units, and CRS.
- Track every derived layer to its source inputs and processing parameters.
- Version processing algorithms and candidate-ranking models.
- Keep field edits in an append-safe audit trail.
- Validate exported coordinates and units.
- Make project migrations recoverable.

### Offline-first behavior

- Projects must open without a network connection.
- Downloaded basemaps must remain available.
- Selected targets, notes, photos, tracks, and boundaries must remain visible.
- Local changes must queue safely.
- Synchronization must resume without duplication.
- Download and processing failures must not corrupt projects.

### Performance safety

- Bound memory use and enforce eviction policies.
- Preserve current imagery during regeneration.
- Never allow stale work to replace current state.
- Prefer visible-area processing and reuse.
- Provide a safe CPU path when GPU capabilities are unavailable.

## Testing strategy

Required datasets:

- Small single-tile project
- Large contiguous mosaic
- Sparse and dense point clouds
- Mixed-quality classification
- Steep and flat terrain
- Wetland and drainage-heavy terrain
- Modern disturbed terrain
- Verified cellar-hole, wagon-road, and stone-wall areas
- Areas with known natural false positives

Functional coverage:

- Every import path and area picker
- Tile selection, cancellation, retry, and recovery
- Mosaic grouping and reopening
- Layer generation and comparison
- Exact-cell inspection and measurement
- Candidate creation and field-state transitions
- Every export format

Performance coverage:

- Cold and warm project open
- Raster generation and layer-switch latency
- Pan and zoom frame time
- Peak Java and native memory
- Cache-hit rate
- Cancellation responsiveness
- Large-project stability

Accuracy coverage:

- Cellar-hole precision and recall
- Wagon-road and wall continuity
- Natural and modern-disturbance false-positive rates
- Candidate-ranking quality
- Historic-map alignment quality

Field reliability:

- Airplane-mode operation
- GPS loss and recovery
- Process restart
- Low-storage behavior
- Camera and microphone failure recovery
- Interrupted synchronization
- Duplicate prevention
- Battery-use testing

## Release checklist

Before every release:

- Release build compiles.
- Unit and instrumented tests pass.
- Small-tile and mosaic projects open.
- Existing saved projects migrate successfully.
- Zoom remains stable during loading.
- Current imagery remains visible during rerender.
- Stale jobs are canceled.
- Offline project access works.
- Exported files open in an external GIS viewer.
- No placeholder or mock-data release paths are exposed.
- No secrets, tokens, keys, or private URLs are committed.

## Success metrics

Detection quality:

- Candidate precision and recall
- Natural and modern false-positive rates
- Productive-target discovery rate
- Average rank of verified productive targets
- Agreement between explanations and field observations

Performance:

- Project-open time
- Layer-generation and switching latency
- Frame time during interaction
- Peak memory
- Cache-hit rate
- Stale-job cancellation rate

Field use:

- Time from analysis to navigation
- Percentage of visits completed offline
- Observation synchronization success
- Number of reviewed training examples
- Productive-to-rejected target ratio
- Distance traveled per verified target

## Immediate work plan

### Sprint 1 — Complete existing workflows

Sprint 1 acceptance pass completed 2026-08-03. The production UI, persistence paths,
unit coverage, release build, and connected-phone reachability were verified for each
workflow below:

1. Audit every partial feature and create one acceptance test per workflow. **Complete.**
2. Finish NYS/USGS area selection across import paths. **Complete.**
3. Finish GPX/KML rendering and persistence. **Complete.**
4. Enable manual refinement at every zoom level. **Complete.**
5. Finish AI marker creation and per-project persistence. **Complete.**
6. Finish exact-cell inspection. **Complete.**
7. Finish synchronized comparison. **Complete.**
8. Finish image and report export. **Complete.**

Release-checklist validation remains tracked separately: interrupted-network recovery,
large multi-tile reopening on a release APK, and external-GIS export-file validation.

### Sprint 2 — Build tile-to-area pipeline

1. Complete polygon and radius selection alongside geographic rectangles. **Implemented; unit verified.**
2. Make the same area selector directly available from every terrain-import path. **Complete.** The map area picker now opens directly inside the tile picker ("Pick area on map"), in addition to the LiDAR tab and the Google-Map bounds hand-off.
3. Add instrumentation for cancellation, per-tile retry, partial-project resumption, and mosaic reopening. **Complete; unit verified.** `LazDownloadQueueCancellationTest`, `LazDownloadQueueRetryTest`, `MosaicProjectResumeTest`, and `MosaicProjectEntityTest` pin cancellation timing, per-tile retry, pause/resume state transitions, recovery messages, and manifest round-trips for reopening.
4. Validate a multi-tile project through the release build on device.

### Sprint 3 — Establish ranking baseline

1. Define the reviewed candidate-example format. **Complete; unit verified.** `ReviewedCandidateExample` plus the append-only `ReviewedExampleStore` in the analysis package; productive, rejected, and ambiguous verdicts are all retained with model/processing versions.
2. Improve ground filtering.
3. Implement multi-scale LRM. **Complete; unit verified.** `MULTI_SCALE_RELIEF` layer with per-scale standardization so cellar- and platform-sized features both survive.
4. Add cellar, platform, road, and wall geometry. **Implemented; unit verified.** `cellarRimGeometry`, `platformEdgeGeometry`, and `linearContinuity` shape checks now adjust candidate scores and surface as supporting/negative evidence.
5. Add natural and modern-disturbance penalties. **Implemented; unit verified.** A `MODERN_DISTURBANCE_PENALTY` layer joins the existing natural-feature penalty; both apply as bounded, explainable score adjustments per detector type.
6. Produce explainable baseline scores. **Implemented.** Candidates carry per-feature evidence plus penalty percentages and geometry findings, so every score adjustment is traceable.

## Milestones

### Milestone A — Reliable historic-site scout

A user can select an area, obtain the correct data, generate bare-earth terrain, review ranked candidates, navigate to them, and save field notes and photos.

### Milestone B — Professional terrain-analysis tool

A user can inspect exact cell values, measure features, compare layers, work across mosaics, export analysis, and reopen a project without recomputing unchanged data.

### Milestone C — Historic research platform

The app can align historic maps, rank by map agreement, learn from reviewed field outcomes, generate QGIS-ready projects, and support versioned regional models.

## Definition of done

A feature is complete only when:

- It is reachable from the production UI.
- It uses real data.
- It handles loading, empty, error, cancellation, and recovery states.
- It survives process restart when persistence is expected.
- It has automated tests.
- It passes CI and release-build validation.
- It does not regress working features.
- It has clear user-facing labels and limitations.
- It reports uncertainty honestly.
- It provides measurable field or research value.
- Its status is updated in this roadmap.

## Decision log

- **2026-07-26:** Historic human-activity detection remains the central product objective.
- **2026-07-26:** Performance work must preserve working features and analytical accuracy.
- **2026-07-26:** Tile-to-area selection must be reachable across terrain-import workflows.
- **2026-07-26:** Field outcomes must feed future ranking through reviewed, versioned data.
- **2026-07-26:** Complete field workflows must remain offline-capable.
- **2026-07-26:** Candidate rankings must remain explainable and versioned.
- **2026-08-03:** GPU terrain previews render at 1,024 cells or finer on every path; coarse progressive stubs and sub-1,024 cache restores are not acceptable render quality.
- **2026-08-03:** Candidate scoring combines per-cell response with shape-verified geometry and bounded natural/modern-disturbance penalties; every adjustment must appear in candidate evidence or notes.
