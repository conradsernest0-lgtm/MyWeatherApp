package de.myweather

data class Hour(
    val time: String,
    val temp: Double,
    val rainProb: Int,
    val wind: Double,
    val code: Int,
    val isDay: Boolean
)

data class Day(
    val date: String,
    val min: Double,
    val max: Double,
    val rainProb: Int,
    val code: Int
)

data class Forecast(
    val location: String,
    val currentTemp: Double,
    val currentCode: Int,
    val currentIsDay: Boolean,
    val currentWind: Double,
    val currentRainProb: Int,
    val hours: List<Hour>,
    val days: List<Day>
)

data class WeatherAlert(
    val event: String,
    val headline: String,
    val description: String,
    val severity: String,
    val official: Boolean = true
)

enum class Provider(val label: String, val modelId: String? = null) {
    MIX("Gemischt"),
    DWD("DWD ICON D2", "dwd_icon_d2"),
    DWD_EU("DWD ICON EU", "dwd_icon_eu"),
    ECMWF("ECMWF IFS", "ecmwf_ifs025"),
    AIFS("ECMWF AIFS", "ecmwf_aifs025_single"),
    NOAA("NOAA GFS", "gfs_seamless"),
    GEM("Environment Canada GEM", "gem_seamless"),
    JMA("JMA", "jma_seamless"),
    KMA("KMA", "kma_seamless"),
    METNO("MET Norway", "metno_seamless"),
    KNMI("KNMI", "knmi_seamless"),
    UKMO("UK Met Office", "ukmo_seamless"),
    DMI("DMI", "dmi_seamless"),
    METEOSWISS("MeteoSwiss", "meteoswiss_icon_seamless"),
    GEOSPHERE("GeoSphere Austria", "geosphere_seamless")
}
