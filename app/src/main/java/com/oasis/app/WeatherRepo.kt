package com.oasis.app

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Clima por ciudad con Open-Meteo (gratis, sin clave). Se guarda en caché 30 min. */
object WeatherRepo {
    data class Weather(val city: String, val temp: Int, val code: Int, val max: Int, val min: Int, val ts: Long)

    private const val TTL = 30 * 60 * 1000L
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)
    fun city(ctx: Context): String = prefs(ctx).getString("weather_city", "") ?: ""

    fun cached(ctx: Context): Weather? = try {
        val raw = prefs(ctx).getString("weather_json", "") ?: throw IllegalStateException("sin caché")
        val o = JSONObject(raw) 
     if (o.optString("city") != city(ctx)) null
        else Weather(o.getString("city"), o.getInt("temp"), o.getInt("code"), o.getInt("max"), o.getInt("min"), o.getLong("ts"))
    } catch (e: Exception) { null }

    fun isFresh(w: Weather?) = w != null && System.currentTimeMillis() - w.ts < TTL

    /** Llama a [onDone] en el hilo principal con el clima (o el último guardado si falla la red). */
    fun refresh(ctx: Context, force: Boolean, onDone: (Weather?) -> Unit) {
        val app = ctx.applicationContext
        val old = cached(app)
        if (!force && isFresh(old)) { onDone(old); return }
        val city = city(app)
        if (city.isBlank()) { onDone(null); return }
        Thread {
            val fresh = try { fetch(app, city) } catch (e: Exception) { null }
            main.post { onDone(fresh ?: old) }
        }.start()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun fetch(ctx: Context, city: String): Weather {
        val p = prefs(ctx)
        var lat = p.getString("weather_lat", "") ?: ""
        var lon = p.getString("weather_lon", "") ?: ""
        var name = p.getString("weather_name", "") ?: ""
        if (lat.isBlank() || p.getString("weather_geo_for", "") != city) {
            val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=" +
                URLEncoder.encode(city, "UTF-8") + "&count=1&language=es&format=json"))
            val r = g.getJSONArray("results").getJSONObject(0)
            lat = r.getDouble("latitude").toString(); lon = r.getDouble("longitude").toString()
            name = r.optString("name", city)
            p.edit().putString("weather_lat", lat).putString("weather_lon", lon)
                .putString("weather_name", name).putString("weather_geo_for", city).apply()
        }
        val f = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"))
        val cur = f.getJSONObject("current")
        val daily = f.getJSONObject("daily")
        val w = Weather(
            city,
            cur.getDouble("temperature_2m").roundToInt(),
            cur.getInt("weather_code"),
            daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            System.currentTimeMillis()
        )
        p.edit().putString("weather_json", JSONObject()
            .put("city", w.city).put("temp", w.temp).put("code", w.code)
            .put("max", w.max).put("min", w.min).put("ts", w.ts).toString()).apply()
        return w
    }

    fun displayName(ctx: Context): String =
        prefs(ctx).getString("weather_name", "")?.takeIf { it.isNotBlank() } ?: city(ctx)

    /** Código WMO -> (icono, descripción). */
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to "Despejado"
        1 -> "🌤️" to "Casi despejado"
        2 -> "⛅" to "Parcialmente nublado"
        3 -> "☁️" to "Nublado"
        45, 48 -> "🌫️" to "Niebla"
        in 51..57 -> "🌦️" to "Llovizna"
        in 61..67 -> "🌧️" to "Lluvia"
        in 71..77, 85, 86 -> "❄️" to "Nieve"
        in 80..82 -> "🌧️" to "Chubascos"
        in 95..99 -> "⛈️" to "Tormenta"
        else -> "🌡️" to "Clima"
    }

    fun promptCity(activity: Activity, onDone: () -> Unit) {
        val input = EditText(activity).apply {
            hint = "Ej: Bogotá"
            setText(city(activity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSelectAllOnFocus(true)
            setPadding(56, 32, 56, 16)
        }
        AlertDialog.Builder(activity)
            .setTitle("Tu ciudad para el clima")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val c = input.text.toString().trim()
                prefs(activity).edit().putString("weather_city", c)
                    .remove("weather_json").remove("weather_lat").remove("weather_lon")
                    .remove("weather_name").remove("weather_geo_for").apply()
                if (c.isBlank()) Toast.makeText(activity, "Ciudad borrada", Toast.LENGTH_SHORT).show()
                onDone()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
