package com.meilluer.glanceable.data.repository

import com.meilluer.glanceable.data.api.OpenWeatherApi
import com.meilluer.glanceable.data.api.WeatherResponse
import com.meilluer.glanceable.data.local.WeatherDao
import com.meilluer.glanceable.data.local.WeatherEntity
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.util.Date
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class WeatherRepository(
    private val api: OpenWeatherApi,
    private val weatherDao: WeatherDao
) {

    val savedWeatherRecords: Flow<List<WeatherEntity>> = weatherDao.getAllWeatherRecords()

    suspend fun fetchAndStoreWeatherByLocation(
        lat: Double,
        lon: Double,
        apiKey: String
    ): Result<WeatherEntity> {
        return try {
            val weatherResponse = api.getCurrentWeather(lat = lat, lon = lon, apiKey = apiKey)
            processAndStoreWeather(weatherResponse, lat, lon, apiKey)
        } catch (e: Exception) {
            Result.failure(mapApiError(e))
        }
    }

    suspend fun fetchAndStoreWeatherByCity(
        cityName: String,
        apiKey: String
    ): Result<WeatherEntity> {
        return try {
            val weatherResponse = api.getCurrentWeatherByCity(cityName = cityName, apiKey = apiKey)
            val lat = weatherResponse.coord?.lat ?: 0.0
            val lon = weatherResponse.coord?.lon ?: 0.0
            processAndStoreWeather(weatherResponse, lat, lon, apiKey)
        } catch (e: Exception) {
            Result.failure(mapApiError(e))
        }
    }

    private suspend fun processAndStoreWeather(
        weatherResponse: WeatherResponse,
        fallbackLat: Double,
        fallbackLon: Double,
        apiKey: String
    ): Result<WeatherEntity> {
        val effectiveLat = weatherResponse.coord?.lat ?: fallbackLat
        val effectiveLon = weatherResponse.coord?.lon ?: fallbackLon

        // 1. Fetch AQI from Free OpenWeatherMap Air Pollution API (/data/2.5/air_pollution)
        val aqi = try {
            val pollutionRes = api.getAirPollution(lat = effectiveLat, lon = effectiveLon, apiKey = apiKey)
            pollutionRes.list?.firstOrNull()?.main?.aqi ?: 1
        } catch (e: Exception) {
            1 // Default Good (1) if free tier air pollution call fails or is unavailable
        }

        // 2. Fetch UV Index from OpenWeatherMap UVI API, or fallback to solar estimate for 2.5 free keys
        val uvi = try {
            val uviRes = api.getUvi(lat = effectiveLat, lon = effectiveLon, apiKey = apiKey)
            uviRes.value
        } catch (e: Exception) {
            // Free tier key fallback calculation: calculate UV index based on latitude, solar angle, and cloud cover
            estimateUvIndex(
                lat = effectiveLat,
                cloudinessPercent = weatherResponse.clouds?.cloudiness ?: 0,
                sunrise = weatherResponse.sys?.sunrise ?: 0L,
                sunset = weatherResponse.sys?.sunset ?: 0L,
                currentTimeSec = weatherResponse.dt ?: (System.currentTimeMillis() / 1000)
            )
        }

        val entity = WeatherEntity(
            timestamp = Date().time,
            cityName = weatherResponse.name ?: "Unknown Location",
            country = weatherResponse.sys?.country ?: "",
            latitude = effectiveLat,
            longitude = effectiveLon,
            temperature = weatherResponse.main?.temp ?: 0.0,
            feelsLike = weatherResponse.main?.feelsLike ?: 0.0,
            tempMin = weatherResponse.main?.tempMin ?: 0.0,
            tempMax = weatherResponse.main?.tempMax ?: 0.0,
            weatherDescription = weatherResponse.weather?.firstOrNull()?.description ?: "N/A",
            weatherIcon = weatherResponse.weather?.firstOrNull()?.icon ?: "",
            aqi = aqi,
            uvIndex = Math.round(uvi * 10.0) / 10.0,
            humidity = weatherResponse.main?.humidity ?: 0,
            windSpeed = weatherResponse.wind?.speed ?: 0.0
        )

        // Save to local Room database
        weatherDao.insertWeatherRecord(entity)
        return Result.success(entity)
    }

    /**
     * Estimates UV Index for free tier OpenWeatherMap keys when /data/2.5/uvi is restricted/deprecated.
     * Uses solar zenith angle (based on lat, time between sunrise/sunset) & cloud cover percentage.
     */
    private fun estimateUvIndex(
        lat: Double,
        cloudinessPercent: Int,
        sunrise: Long,
        sunset: Long,
        currentTimeSec: Long
    ): Double {
        if (sunrise == 0L || sunset == 0L || currentTimeSec <= sunrise || currentTimeSec >= sunset) {
            return 0.0 // Night time
        }

        val dayLength = (sunset - sunrise).toDouble()
        if (dayLength <= 0) return 0.0

        val timeProgress = (currentTimeSec - sunrise) / dayLength // 0.0 to 1.0
        val solarElevationSin = sin(Math.PI * timeProgress) // peak at midday

        // Max solar radiation based on latitude (Equator ~ 11.5, Poles ~ 3.0)
        val maxUvForLat = max(2.5, 11.5 * cos(Math.toRadians(abs(lat))))

        // Cloud attenuation factor (1.0 for clear sky, ~0.35 for 100% clouds)
        val cloudAttenuation = 1.0 - (0.65 * (cloudinessPercent / 100.0))

        val estimatedUv = maxUvForLat * solarElevationSin * cloudAttenuation
        return max(0.0, estimatedUv)
    }

    private fun mapApiError(e: Exception): Exception {
        return if (e is HttpException) {
            when (e.code()) {
                401 -> Exception("Invalid or unactivated OpenWeatherMap API key (401). Note: New free API keys can take 10-15 minutes to activate after signup.")
                404 -> Exception("City or location weather data not found (404).")
                429 -> Exception("OpenWeatherMap free API rate limit exceeded (429). Please try again later.")
                else -> Exception("OpenWeatherMap HTTP error ${e.code()}: ${e.message()}")
            }
        } else {
            e
        }
    }

    suspend fun deleteRecord(entity: WeatherEntity) {
        weatherDao.deleteWeatherRecord(entity)
    }

    suspend fun clearHistory() {
        weatherDao.clearAllRecords()
    }
}
