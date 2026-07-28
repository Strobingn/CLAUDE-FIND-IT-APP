package com.example.geospatial

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementFormatTest {
    @Test
    fun oneMeterIsJustOverThreeFeet() {
        assertEquals(3.28084, MeasurementFormat.metersToFeet(1.0), 0.0001)
    }

    @Test
    fun subFootLengthsReadInInches() {
        // A 0.15 m LiDAR cell is ~5.9 in; showing "0.5 ft" would lose the useful precision.
        assertEquals("5.9 in", MeasurementFormat.length(0.15))
        assertEquals("11.8 in", MeasurementFormat.length(0.3))
    }

    @Test
    fun everydayLengthsReadInFeet() {
        // Whole feet above 10 ft; a tenth of a foot is noise at that range.
        assertEquals("3.3 ft", MeasurementFormat.length(1.0))
        assertEquals("33 ft", MeasurementFormat.length(10.0))
        assertEquals("328 ft", MeasurementFormat.length(100.0))
        // Still under a mile, so still feet.
        assertEquals("3281 ft", MeasurementFormat.length(1_000.0))
    }

    @Test
    fun aMileAndAboveReadsInMiles() {
        // 5280 ft is exactly one mile.
        assertEquals("1.00 mi", MeasurementFormat.length(5280.0 / MeasurementFormat.FEET_PER_METER))
        assertEquals("3.11 mi", MeasurementFormat.length(5_000.0))
        assertEquals("6.21 mi", MeasurementFormat.length(10_000.0))
    }

    @Test
    fun theFootMileBoundaryDoesNotSkipAUnit() {
        val justUnderAMile = (MeasurementFormat.FEET_PER_MILE - 1.0) / MeasurementFormat.FEET_PER_METER
        assertEquals("5279 ft", MeasurementFormat.length(justUnderAMile))
    }

    @Test
    fun elevationAlwaysStaysInFeet() {
        // Elevation is quoted in feet at every magnitude, including below a foot.
        assertEquals("0.5 ft", MeasurementFormat.feet(0.15))
        assertEquals("1640.4 ft", MeasurementFormat.feet(500.0))
        assertEquals("500.00 ft", MeasurementFormat.feet(152.4, decimals = 2))
    }

    @Test
    fun signedLengthsKeepTheirDirection() {
        assertEquals("+3.3 ft", MeasurementFormat.signedLength(1.0))
        assertEquals("−3.3 ft", MeasurementFormat.signedLength(-1.0))
    }

    @Test
    fun perMeterRatesBecomePerFootRates() {
        // A curvature of 1 per meter is a smaller number per foot, since a foot is shorter.
        assertEquals("0.3048 ft⁻¹", MeasurementFormat.perFoot(1.0))
    }

    @Test
    fun nonFiniteValuesDoNotRenderAsNumbers() {
        assertEquals("—", MeasurementFormat.length(Double.NaN))
        assertEquals("—", MeasurementFormat.feet(Double.POSITIVE_INFINITY))
        assertEquals("—", MeasurementFormat.perFoot(Double.NaN))
        assertEquals("—", MeasurementFormat.signedLength(Double.NaN))
    }
}
