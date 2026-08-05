package com.example.data.field

/**
 * An AI-ranked terrain target the user wants to walk to, in real coordinates. Set from the AI
 * workspace target list; consumed by the Terrain tab navigation HUD and the arrival alerter.
 */
data class NavigationTarget(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Fires exactly once when the device comes within [enterMeters] of the destination, and only
 * re-arms after the device leaves past [exitMeters]. The enter/exit gap is hysteresis: GPS
 * jitter around the boundary must not machine-gun arrival pings at the user.
 */
class ProximityAlerter(
    private val enterMeters: Float = DEFAULT_ENTER_METERS,
    private val exitMeters: Float = DEFAULT_EXIT_METERS,
) {
    private var inside = false

    /** Returns true on the entry edge only. */
    fun offer(distanceMeters: Float): Boolean {
        if (inside) {
            if (distanceMeters > exitMeters) inside = false
            return false
        }
        if (distanceMeters <= enterMeters) {
            inside = true
            return true
        }
        return false
    }

    /** True while the device is between the entry and exit thresholds. */
    val isInside: Boolean get() = inside

    /** New destination (or cleared navigation) starts disarmed. */
    fun reset() {
        inside = false
    }

    companion object {
        const val DEFAULT_ENTER_METERS = 15f
        const val DEFAULT_EXIT_METERS = 30f
    }
}
