package com.refsix.wear.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

class HeartRateTracker(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val hrSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private var listener: SensorEventListener? = null

    val isAvailable: Boolean get() = hrSensor != null

    fun startTracking(onHeartRate: (Int) -> Unit) {
        if (listener != null || hrSensor == null) return
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                    val bpm = event.values[0].toInt()
                    if (bpm > 0) onHeartRate(bpm)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        listener = l
        sensorManager.registerListener(
            l, hrSensor, SensorManager.SENSOR_DELAY_NORMAL,
            Handler(Looper.getMainLooper())
        )
    }

    fun stopTracking() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
    }
}
