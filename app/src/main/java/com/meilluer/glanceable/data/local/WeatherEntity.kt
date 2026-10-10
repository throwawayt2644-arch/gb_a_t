package com.meilluer.glanceable.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_records")
data class WeatherEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val cityName: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,
    val feelsLike: Double,
    val tempMin: Double,
    val tempMax: Double,
    val weatherDescription: String,
    val weatherIcon: String,
    val aqi: Int,           // 1=Good, 2=Fair, 3=Moderate, 4=Poor, 5=Very Poor
    val uvIndex: Double,    // UV index e.g., 0.0 to 12.0+
    val humidity: Int,
    val windSpeed: Double
)
