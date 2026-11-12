package com.oasis.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Lee el acelerómetro y avisa cuántos grados de "inclinación" hay (-1..1 en cada eje). Sin permisos. */
class TiltParallax(ctx: Context, private val onTilt: (Float, Float) -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var x = 0f
    private var y = 0f

    fun start() {
        val s = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm?.unregisterListener(this)
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(e: SensorEvent) {
        // filtro suave para que el movimiento sea fluido y no tiemble
        val tx = (-e.values[0] / 9.8f).coerceIn(-1f, 1f)
        val ty = ((e.values[1] - 6f) / 9.8f).coerceIn(-1f, 1f)
        x += (tx - x) * 0.08f
        y += (ty - y) * 0.08f
        onTilt(x, y)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
