package com.meilluer.glanceable.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// --- OpenWeatherMap Remote API Models ---

data class WeatherResponse(
    @SerializedName("coord") val coord: Coord?,
    @SerializedName("weather") val weather: List<WeatherDescription>?,
    @SerializedName("main") val main: MainData?,
    @SerializedName("wind") val wind: WindData?,
    @SerializedName("clouds") val clouds: CloudsData?,
    @SerializedName("dt") val dt: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("sys") val sys: SysData?
)

data class CloudsData(
    @SerializedName("all") val cloudiness: Int
)

data class Coord(
    @SerializedName("lon") val lon: Double,
    @SerializedName("lat") val lat: Double
)

data class WeatherDescription(
    @SerializedName("id") val id: Int,
    @SerializedName("main") val main: String,
    @SerializedName("description") val description: String,
    @SerializedName("icon") val icon: String
)

data class MainData(
    @SerializedName("temp") val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    @SerializedName("temp_min") val tempMin: Double,
    @SerializedName("temp_max") val tempMax: Double,
    @SerializedName("pressure") val pressure: Int,
    @SerializedName("humidity") val humidity: Int
)

data class WindData(
    @SerializedName("speed") val speed: Double
)

data class SysData(
    @SerializedName("country") val country: String?,
    @SerializedName("sunrise") val sunrise: Long?,
    @SerializedName("sunset") val sunset: Long?
)

// Air Pollution / AQI Response
data class AirPollutionResponse(
    @SerializedName("list") val list: List<AirPollutionItem>?
)

data class AirPollutionItem(
    @SerializedName("main") val main: AirQualityMain?,
    @SerializedName("components") val components: Map<String, Double>?
)

data class AirQualityMain(
    @SerializedName("aqi") val aqi: Int // 1 = Good, 2 = Fair, 3 = Moderate, 4 = Poor, 5 = Very Poor
)

// UV Index Response
data class UviResponse(
    @SerializedName("value") val value: Double
)


// --- Retrofit API Service ---

interface OpenWeatherApi {

    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): WeatherResponse

    @GET("data/2.5/weather")
    suspend fun getCurrentWeatherByCity(
        @Query("q") cityName: String,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): WeatherResponse

    @GET("data/2.5/air_pollution")
    suspend fun getAirPollution(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): AirPollutionResponse

    @GET("data/2.5/uvi")
    suspend fun getUvi(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): UviResponse

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/"

        fun create(): OpenWeatherApi {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenWeatherApi::class.java)
        }
    }
}
