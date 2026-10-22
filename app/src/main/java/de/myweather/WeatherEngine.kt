package de.myweather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

object WeatherEngine {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 18000
        c.requestMethod = "GET"
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    fun geocode(query: String): List<Pair<String, Pair<Double, Double>>> {
        val q = URLEncoder.encode(query, "UTF-8")
        val raw = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=de&format=json")
        val a = JSONObject(raw).optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull {
            val o = a.optJSONObject(it) ?: return@mapNotNull null
            val name = listOfNotNull(
                o.optString("name", null),
                o.optString("admin1", null),
                o.optString("country", null)
            ).distinct().joinToString(", ")
            name to (o.optDouble("latitude") to o.optDouble("longitude"))
        }
    }

    fun forecast(lat: Double, lon: Double, provider: Provider): Forecast {
        val models = provider.modelId ?: "best_match"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,precipitation_probability,wind_speed_10m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                "&current=temperature_2m,weather_code,wind_speed_10m,precipitation,weather_code,is_day" +
                "&forecast_days=16&timezone=auto&wind_speed_unit=kmh&models=$models"
        val j = JSONObject(get(url))
        val cur = j.getJSONObject("current")
        val h = j.getJSONObject("hourly")
        val d = j.getJSONObject("daily")
        val times = h.getJSONArray("time")
        val ht = h.getJSONArray("temperature_2m")
        val hp = h.getJSONArray("precipitation_probability")
        val hw = h.getJSONArray("wind_speed_10m")
        val hc = h.getJSONArray("weather_code")
        val hd = h.getJSONArray("is_day")
        val hours = (0 until minOf(times.length(), 48)).map {
            Hour(
                times.getString(it),
                ht.optDouble(it),
                hp.optInt(it),
                hw.optDouble(it),
                hc.optInt(it),
                hd.optInt(it) == 1
            )
        }
        val days = (0 until d.getJSONArray("time").length()).map {
            Day(
                d.getJSONArray("time").getString(it),
                d.getJSONArray("temperature_2m_min").optDouble(it),
                d.getJSONArray("temperature_2m_max").optDouble(it),
                d.getJSONArray("precipitation_probability_max").optInt(it),
                d.getJSONArray("weather_code").optInt(it)
            )
        }
        return Forecast(
            location = "Mein Standort",
            currentTemp = cur.optDouble("temperature_2m"),
            currentCode = cur.optInt("weather_code"),
            currentIsDay = cur.optInt("is_day") == 1,
            currentWind = cur.optDouble("wind_speed_10m"),
            currentRainProb = cur.optInt("precipitation_probability", 0),
            hours = hours,
            days = days
        )
    }

    fun alerts(lat: Double, lon: Double): List<WeatherAlert> {
        // DWD Open Data JSON endpoint. It is public and suitable for district-level status.
        val raw = get("https://www.dwd.de/DWD/warnungen/cap-feed/de/atom.xml")
        val out = mutableListOf<WeatherAlert>()
        // Keep parsing deliberately defensive: DWD changes individual feed fields.
        Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL).findAll(raw)
            .map { it.groupValues[1].replace("<![CDATA[","").replace("]]>","").trim() }
            .filter { it.isNotBlank() && !it.equals("DWD Unwetterwarnungen", true) }
            .take(12)
            .forEach { title -> out += WeatherAlert(title, title, "DWD-Warnfeed aktiv. Öffnen Sie die Warnung für Details.", "warn") }
        return out.distinctBy { it.event }
    }

    fun operaRadarUrl(lat: Double, lon: Double): String =
        "https://api.meteogate.eu/eu-eumetnet-weather-radar/collections/observations/locations/0-20010-0-OPERA" +
        "?f=CoverageJSON&standard_name=DBZH&format=CoverageJSON&bbox=${lon-0.3},${lat-0.3},${lon+0.3},${lat+0.3}"

    fun dwdMesoLatest(): List<Pair<Double, Double>> {
        val raw = get("https://opendata.dwd.de/weather/radar/mesocyclones/meso_latest.xml")
        val points = mutableListOf<Pair<Double, Double>>()
        val regex = Regex("""lat(?:itude)?="([0-9.+-]+)"[^>]*lon(?:gitude)?="([0-9.+-]+)"""", RegexOption.IGNORE_CASE)
        regex.findAll(raw).forEach { m -> points += m.groupValues[1].toDouble() to m.groupValues[2].toDouble() }
        if (points.isEmpty()) {
            Regex("<lat>\\s*([0-9.+-]+)\\s*</lat>.*?<lon>\\s*([0-9.+-]+)\\s*</lon>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(raw).forEach { m -> points += m.groupValues[1].toDouble() to m.groupValues[2].toDouble() }
        }
        return points
    }

    fun nearbyMeso(lat: Double, lon: Double, radiusKm: Double = 35.0): Boolean {
        return dwdMesoLatest().any { (la, lo) ->
            val dLat = Math.toRadians(la - lat)
            val dLon = Math.toRadians(lo - lon)
            val a = kotlin.math.sin(dLat/2).let { it*it } + kotlin.math.cos(Math.toRadians(lat))*kotlin.math.cos(Math.toRadians(la))*kotlin.math.sin(dLon/2).let { it*it }
            val km = 6371.0 * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1-a))
            km <= radiusKm
        }
    }

    fun weightedTemperature(values: Map<Provider, Double>, weights: Map<Provider, Int>): Double {
        val valid = values.filterKeys { it != Provider.MIX }
        val total = valid.keys.sumOf { weights[it] ?: 5 }.coerceAtLeast(1)
        return valid.entries.sumOf { (p, v) -> v * (weights[p] ?: 5) } / total
    }

    fun icon(code: Int, night: Boolean): String = when (code) {
        0 -> if (night) "☾" else "☀"
        1,2 -> if (night) "☾☁" else "🌤"
        3 -> "☁"
        45,48 -> "🌫"
        51,53,55,56,57 -> "🌦"
        61,63,65,66,67 -> "🌧"
        71,73,75,77 -> "❄"
        80,81,82 -> "🌧"
        85,86 -> "🌨"
        95 -> "⛈"
        96,99 -> "⛈"
        else -> "☁"
    }

    fun tempColorHex(t: Double): Long = when {
        t < -5 -> 0xFF123E8A
        t <= 0 -> 0xFF75B8FF
        t <= 10 -> 0xFF1B7A3A
        t <= 15 -> 0xFF7AD66A
        t <= 25 -> 0xFFFF7676
        else -> 0xFFB51E2A
    }
}
