package com.example.geospatial

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Device compass headings for field navigation. The rotation-vector sensor is preferred because
 * it fuses the accelerometer, gyro, and magnetometer; callers must still present it as a
 * field-navigation aid rather than survey-grade equipment.
 */
class CompassHeadingTracker(context: Context) {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun headings(): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val heading = Math.toDegrees(orientation[0].toDouble()).toFloat()
                trySend(((heading % 360f) + 360f) % 360f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)) {
            close()
            return@callbackFlow
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}

/** Converts a true-north geographic bearing to the magnetic bearing shown by the phone compass. */
fun trueToMagneticBearingDegrees(
    trueBearingDegrees: Float,
    latitude: Double,
    longitude: Double,
    timeMillis: Long = System.currentTimeMillis(),
): Float {
    val declination = GeomagneticField(
        latitude.toFloat(),
        longitude.toFloat(),
        0f,
        timeMillis,
    ).declination
    return ((trueBearingDegrees - declination) % 360f + 360f) % 360f
}
