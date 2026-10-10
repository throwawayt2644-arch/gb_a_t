package com.meilluer.glanceable.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meilluer.glanceable.data.api.OpenWeatherApi
import com.meilluer.glanceable.data.local.AppDatabase
import com.meilluer.glanceable.data.local.WeatherEntity
import com.meilluer.glanceable.data.repository.WeatherRepository
import com.meilluer.glanceable.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Idle : WeatherUiState
    object PermissionRequestNeeded : WeatherUiState
    object Loading : WeatherUiState
    data class Success(val currentRecord: WeatherEntity) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    private val repository: WeatherRepository
    private val locationTracker: LocationTracker

    val savedHistory: StateFlow<List<WeatherEntity>>

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _apiKey = MutableStateFlow(
        prefs.getString("api_key", "") ?: ""
    )
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        val api = OpenWeatherApi.create()
        repository = WeatherRepository(api, database.weatherDao())
        locationTracker = LocationTracker(application)

        savedHistory = repository.savedWeatherRecords.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        checkInitialPermissionState()
    }

    private fun checkInitialPermissionState() {
        val hasPermission = locationTracker.hasLocationPermission()
        _permissionGranted.value = hasPermission

        if (hasPermission) {
            fetchWeatherForCurrentLocation()
        } else {
            _uiState.value = WeatherUiState.PermissionRequestNeeded
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _permissionGranted.value = isGranted
        if (isGranted) {
            fetchWeatherForCurrentLocation()
        } else {
            _uiState.value = WeatherUiState.Error(
                "Location permission denied. You can search weather by city name below or update permissions in settings."
            )
        }
    }

    fun fetchWeatherForCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading

            val key = apiKey.value.ifBlank {
                _uiState.value = WeatherUiState.Error("Please configure your OpenWeatherMap API Key in settings.")
                return@launch
            }

            val location = locationTracker.getCurrentLocation()
            if (location != null) {
                val result = repository.fetchAndStoreWeatherByLocation(
                    lat = location.latitude,
                    lon = location.longitude,
                    apiKey = key
                )
                result.fold(
                    onSuccess = { entity ->
                        _uiState.value = WeatherUiState.Success(entity)
                    },
                    onFailure = { error ->
                        _uiState.value = WeatherUiState.Error(
                            error.localizedMessage ?: "Failed to fetch weather data from OpenWeatherMap."
                        )
                    }
                )
            } else {
                _uiState.value = WeatherUiState.Error(
                    "Unable to get current GPS location. Please try searching by city name."
                )
            }
        }
    }

    fun fetchWeatherByCity(cityName: String) {
        if (cityName.isBlank()) return
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading

            val key = apiKey.value.ifBlank {
                _uiState.value = WeatherUiState.Error("Please configure your OpenWeatherMap API Key in settings.")
                return@launch
            }

            val result = repository.fetchAndStoreWeatherByCity(
                cityName = cityName.trim(),
                apiKey = key
            )

            result.fold(
                onSuccess = { entity ->
                    _uiState.value = WeatherUiState.Success(entity)
                },
                onFailure = { error ->
                    _uiState.value = WeatherUiState.Error(
                        error.localizedMessage ?: "Failed to fetch weather for '$cityName'."
                    )
                }
            )
        }
    }

    fun saveApiKey(newKey: String) {
        val trimmedKey = newKey.trim()
        prefs.edit().putString("api_key", trimmedKey).apply()
        _apiKey.value = trimmedKey

        if (trimmedKey.isNotBlank()) {
            if (_permissionGranted.value) {
                fetchWeatherForCurrentLocation()
            }
        }
    }

    fun deleteHistoryRecord(entity: WeatherEntity) {
        viewModelScope.launch {
            repository.deleteRecord(entity)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
