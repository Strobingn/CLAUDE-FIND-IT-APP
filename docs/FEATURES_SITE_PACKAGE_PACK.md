# Site Package Pack (KIMIV6) — Done

Ten field/LAZ/export features that finish the highest-ROI unfinished pipeline after AI packs 1 & 3,
field packs 1–2, and the GROKV5 product pack. Dual surface, boundary clip refine, relative surface Z,
class filter, mosaic open UX, clipped LAS, site package zip, field PDF (via package), boundary GPS
alert, and confirm-write AI tags — without rehashing prior packs.

**Branch:** `KIMIV6`  
**Status:** Done (shipping on KIMIV6)

| # | Feature | Track | What it does |
|---|---------|--------|--------------|
| 1 | **Dual surface (ground vs first-return)** | LAZ | Classified ground, auto-lowest, or highest-return DSM chips re-decode the open LiDAR. Terrain geometry only — not metal. |
| 2 | **Clip refine to survey boundary** | LAZ | Re-rasterizes the source cloud into a survey-boundary AOI via `BoundaryFocusMapper` + refine. |
| 3 | **Sample surface Z under find** | Field | Relative ΔZ vs local bare-earth mean + slope bucket on each georeferenced find. Not dig/metal depth. |
| 4 | **Class filter overlay (LAS classes)** | LAZ | ASPRS preset chips (all / ground / vegetation / building / unclassified) for the next re-decode. |
| 5 | **Multi-tile mosaic open UX** | LAZ | Saved mosaic cards use `MosaicOpenUx` title/status/action/details (open / resume / retry). |
| 6 | **Clipped LAZ write** | Export | Surface-sample LAS 1.2 from the elevation grid (not original pulses) for field handoff. |
| 7 | **Site package export (zip)** | Export | Offline zip: summary, targets, digs, boundaries, trails, PNG/PDF, optional clipped LAS. |
| 8 | **Styled field PDF report** | Export | Field report PDF rides in the site package / project export path (annotated map + records). |
| 9 | **Boundary proximity alert (GPS)** | Field | Live GPS vs survey polygons: inside / near edge / outside banner on Terrain when relevant. |
| 10 | **Confirm-write AI metal/outcome tags** | AI UX | Parses `METAL_TYPE` / `OUTCOME` / `STATUS` / `NOTES`; confirm card never auto-writes. |

## Design rules

- **LiDAR ≠ metal** — no age/depth/metal claims as fact from terrain alone.
- **Relative Z only** — surface context under finds is local ΔZ / slope; never buried-object depth.
- **Writes only on explicit user action** — AI suggestions require Confirm write; Dismiss is always available.
- **Offline-first** where possible — site package zip, clipped LAS, dual surface re-decode on-device.
- Honest uncertainty language in UI copy and scorecard lines.

## UI surfaces

| Surface | Wiring |
|---------|--------|
| `LidarControlPanel` | Dual surface chips + class filter + reload message |
| Terrain tab | Boundary proximity banner, refine-to-boundary quick action, surface props |
| Tools tab | Site Package tool cards (dual surface, clip refine, export, GPS alert, surface Z) |
| Finds / `TargetLoggerPanel` | `find_surface_z_card` on edit when lat/lon present |
| AI / `AiCloudPanel` | `ai_confirm_write_card` after last MODEL message with tags |
| Import / `NysLazTilePicker` | `MosaicOpenUx` for saved multi-tile projects |

## Related packs

- Field UX pack 1: [../GROKV5/docs/FEATURES_GROKV5.md](../GROKV5/docs/FEATURES_GROKV5.md)
- Field finds pack 2: [../GROKV5/docs/FEATURES_PACK2.md](../GROKV5/docs/FEATURES_PACK2.md)
- AI pack 1: [../GROKV5/docs/FEATURES_AI_PACK.md](../GROKV5/docs/FEATURES_AI_PACK.md)
- AI pack 3: [../GROKV5/docs/FEATURES_AI_PACK3.md](../GROKV5/docs/FEATURES_AI_PACK3.md)
- Product pack: [../GROKV5/docs/FEATURES_PRODUCT_PACK.md](../GROKV5/docs/FEATURES_PRODUCT_PACK.md)
