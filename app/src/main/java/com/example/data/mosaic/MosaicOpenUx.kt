package com.example.data.mosaic

/**
 * Operator-facing card model for multi-tile mosaic open UX.
 * Pure presentation helpers over [MosaicProject] + on-device readiness counts.
 */
data class MosaicOpenCard(
    val title: String,
    val statusLine: String,
    val actionLabel: String, // "Open mosaic" | "Resume download" | "Retry missing tiles"
    val detailLines: List<String>,
    val isPrimaryActionEnabled: Boolean,
)

/**
 * Builds the open / resume / retry card for a saved multi-tile mosaic project.
 * Uses [MosaicProjectState] and [MosaicProjectResume] rules so copy matches resume behavior.
 */
object MosaicOpenUx {
    fun cardFor(project: MosaicProject, readyTileCount: Int, totalTiles: Int): MosaicOpenCard {
        val total = totalTiles.coerceAtLeast(0).let { declared ->
            if (declared > 0) declared else project.tiles.size
        }.coerceAtLeast(project.tiles.size)
        val ready = readyTileCount.coerceIn(0, total.coerceAtLeast(0))
        val missing = (total - ready).coerceAtLeast(0)
        val fullyReady = project.state == MosaicProjectState.READY &&
            missing == 0 &&
            total > 0
        val canResume = MosaicProjectResume.canResume(project, availableSourceCount = ready)

        val actionLabel = when {
            fullyReady -> "Open mosaic"
            ready == 0 || project.state == MosaicProjectState.NEEDS_ATTENTION -> "Retry missing tiles"
            canResume -> "Resume download"
            else -> "Open mosaic"
        }

        val statusLine = when {
            fullyReady -> "All $total tiles ready"
            total == 0 -> "No tiles in this project"
            project.state == MosaicProjectState.DOWNLOADING ->
                "Downloading — $ready of $total tiles ready"
            project.state == MosaicProjectState.NEEDS_ATTENTION ->
                "Needs attention — $ready of $total tiles ready"
            missing > 0 -> "$ready of $total tiles on this device"
            else -> "Ready to open"
        }

        val detailLines = buildList {
            if (total > 0) {
                add(
                    if (missing == 0) {
                        "$ready/$total source files present"
                    } else {
                        "$ready/$total source files present · $missing missing"
                    },
                )
            } else {
                add("Add or re-select an area to build a mosaic.")
            }
            project.areaSelectionDescription?.takeIf { it.isNotBlank() }?.let {
                add("Area: $it")
            }
            project.recoveryMessage?.takeIf { it.isNotBlank() }?.let {
                add(it)
            }
            when {
                fullyReady -> add("Tap Open mosaic to load the combined terrain.")
                ready == 0 && total > 0 ->
                    add("None of the source tiles are on this device yet.")
                canResume && missing > 0 ->
                    add(MosaicProjectResume.pausedMessage(ready, total))
                project.state == MosaicProjectState.NEEDS_ATTENTION ->
                    add("Retry the missing tiles, then open the mosaic.")
            }
        }

        val isPrimaryActionEnabled = when {
            total == 0 -> false
            fullyReady -> true
            canResume -> true
            else -> ready > 0
        }

        return MosaicOpenCard(
            title = project.displayName.ifBlank { "Mosaic project" },
            statusLine = statusLine,
            actionLabel = actionLabel,
            detailLines = detailLines,
            isPrimaryActionEnabled = isPrimaryActionEnabled,
        )
    }
}
