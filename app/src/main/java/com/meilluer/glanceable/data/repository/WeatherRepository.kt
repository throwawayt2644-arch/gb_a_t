package com.meilluer.glanceable.data.repository

import com.meilluer.glanceable.data.api.OpenWeatherApi
import com.meilluer.glanceable.data.local.WeatherDao
import com.meilluer.glanceable.data.local.WeatherEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

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

            val effectiveLat = weatherResponse.coord?.lat ?: lat
            val effectiveLon = weatherResponse.coord?.lon ?: lon

            val aqi = try {
                val pollutionRes = api.getAirPollution(lat = effectiveLat, lon = effectiveLon, apiKey = apiKey)
                pollutionRes.list?.firstOrNull()?.main?.aqi ?: 1
            } catch (e: Exception) {
                1 // Fallback AQI if endpoint fails
            }

            val uvi = try {
                val uviRes = api.getUvi(lat = effectiveLat, lon = effectiveLon, apiKey = apiKey)
                uviRes.value
            } catch (e: Exception) {
                // Fallback estimate based on solar elevation / mock or 0.0 if unavailable on free tier key
                0.0
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
                uvIndex = uvi,
                humidity = weatherResponse.main?.humidity ?: 0,
                windSpeed = weatherResponse.wind?.speed ?: 0.0
            )

            // Save to Room database
            weatherDao.insertWeatherRecord(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
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

            val aqi = try {
                if (lat != 0.0 || lon != 0.0) {
                    val pollutionRes = api.getAirPollution(lat = lat, lon = lon, apiKey = apiKey)
                    pollutionRes.list?.firstOrNull()?.main?.aqi ?: 1
                } else 1
            } catch (e: Exception) {
                1
            }

            val uvi = try {
                if (lat != 0.0 || lon != 0.0) {
                    val uviRes = api.getUvi(lat = lat, lon = lon, apiKey = apiKey)
                    uviRes.value
                } else 0.0
            } catch (e: Exception) {
                0.0
            }

            val entity = WeatherEntity(
                timestamp = Date().time,
                cityName = weatherResponse.name ?: cityName,
                country = weatherResponse.sys?.country ?: "",
                latitude = lat,
                longitude = lon,
                temperature = weatherResponse.main?.temp ?: 0.0,
                feelsLike = weatherResponse.main?.feelsLike ?: 0.0,
                tempMin = weatherResponse.main?.tempMin ?: 0.0,
                tempMax = weatherResponse.main?.tempMax ?: 0.0,
                weatherDescription = weatherResponse.weather?.firstOrNull()?.description ?: "N/A",
                weatherIcon = weatherResponse.weather?.firstOrNull()?.icon ?: "",
                aqi = aqi,
                uvIndex = uvi,
                humidity = weatherResponse.main?.humidity ?: 0,
                windSpeed = weatherResponse.wind?.speed ?: 0.0
            )

            // Save to Room database
            weatherDao.insertWeatherRecord(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecord(entity: WeatherEntity) {
        weatherDao.deleteWeatherRecord(entity)
    }

    suspend fun clearHistory() {
        weatherDao.clearAllRecords()
    }
}
