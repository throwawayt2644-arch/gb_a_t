package com.meilluer.glanceable.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Query("SELECT * FROM weather_records ORDER BY timestamp DESC")
    fun getAllWeatherRecords(): Flow<List<WeatherEntity>>

    @Query("SELECT * FROM weather_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestWeatherRecord(): WeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherRecord(weatherEntity: WeatherEntity)

    @Delete
    suspend fun deleteWeatherRecord(weatherEntity: WeatherEntity)

    @Query("DELETE FROM weather_records")
    suspend fun clearAllRecords()
}
