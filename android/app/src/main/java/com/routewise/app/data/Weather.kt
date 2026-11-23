package com.routewise.app.data

import com.routewise.app.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import kotlin.math.roundToInt

/** Forecast of one day: temperatures in degrees Celsius, chance of precipitation in percent, WMO weather code. */
data class Weather(val date: LocalDate, val minC: Int, val maxC: Int, val rainPercent: Int?, val code: Int)

/** Weather at the destination on the day of arrival. Returns null when there is no forecast; it is never an error. */
interface WeatherSource {
    suspend fun forecast(lat: Double, lon: Double, date: LocalDate): Weather?
}

/** Free forecast service without a key (open-meteo.com, CC BY 4.0, non-commercial use). Forecasts reach about 16 days ahead. */
class OpenMeteoWeather(private val client: OkHttpClient, private val logger: Logger) : WeatherSource {
    override suspend fun forecast(lat: Double, lon: Double, date: LocalDate): Weather? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&start_date=$date&end_date=$date"
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parse(response.body?.string().orEmpty(), date)
            }
        } catch (e: Exception) {
            logger.warn("Weather is not available")
            null
        }
    }

    companion object {
        fun parse(body: String, date: LocalDate): Weather? {
            val daily = Json.parseToJsonElement(body).jsonObject["daily"] as? JsonObject ?: return null
            fun first(name: String) = daily[name]?.jsonArray?.firstOrNull()?.jsonPrimitive
            val max = first("temperature_2m_max")?.doubleOrNull ?: return null
            val min = first("temperature_2m_min")?.doubleOrNull ?: return null
            val code = first("weather_code")?.intOrNull ?: return null
            return Weather(date, min.roundToInt(), max.roundToInt(), first("precipitation_probability_max")?.intOrNull, code)
        }
    }
}

/** Fixed answer for tests. */
class FakeWeatherSource(private val answer: Weather? = null) : WeatherSource {
    override suspend fun forecast(lat: Double, lon: Double, date: LocalDate): Weather? = answer?.copy(date = date)
}
