package com.example.simplemusictuner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel для управления состоянием тюнера
 */
class TunerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioAnalyzer = AudioAnalyzer()
    private val pitchDetector = PitchDetector()
    private val settingsManager = SettingsManager(application)
    
    // Текущий диапазон частот
    private val _frequencyRange = MutableStateFlow(PitchDetector.FrequencyRange.STANDARD)
    val frequencyRange: StateFlow<PitchDetector.FrequencyRange> = _frequencyRange.asStateFlow()
    
    // Текущая частота с микрофона
    val currentFrequency: StateFlow<Double> = audioAnalyzer.frequency
    
    // Уверенность в результате (0.0 - 1.0)
    val confidence: StateFlow<Double> = audioAnalyzer.confidence
    
    // Данные для графика звуковой волны
    val waveformData: StateFlow<List<Float>> = audioAnalyzer.waveformData
    
    // Информация о ближайшей ноте
    val noteInfo: StateFlow<PitchDetector.NoteInfo?> = currentFrequency
        .combine(_frequencyRange) { freq, range ->
            if (freq > 0) {
                pitchDetector.detectNote(freq, range)
            } else {
                null
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    // Статус записи
    val isRecording: StateFlow<Boolean> = audioAnalyzer.isRecording
    
    // Чувствительность
    val sensitivity: StateFlow<Float> = settingsManager.sensitivity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsManager.DEFAULT_SENSITIVITY
        )
    
    init {
        // Подписываемся на изменения чувствительности
        viewModelScope.launch {
            sensitivity.collect { newSensitivity ->
                audioAnalyzer.setSensitivity(newSensitivity)
            }
        }
    }
    
    /**
     * Запускает анализ аудио
     */
    fun startAnalysis() {
        audioAnalyzer.start(viewModelScope)
    }
    
    /**
     * Устанавливает чувствительность
     */
    fun setSensitivity(value: Float) {
        viewModelScope.launch {
            settingsManager.setSensitivity(value)
        }
    }
    
    /**
     * Останавливает анализ аудио
     */
    fun stopAnalysis() {
        audioAnalyzer.stop()
    }
    
    /**
     * Переключает диапазон частот
     */
    fun toggleFrequencyRange() {
        _frequencyRange.value = when (_frequencyRange.value) {
            PitchDetector.FrequencyRange.STANDARD -> PitchDetector.FrequencyRange.EXTENDED
            PitchDetector.FrequencyRange.EXTENDED -> PitchDetector.FrequencyRange.STANDARD
        }
    }
    
    /**
     * Получает название текущего диапазона для отображения
     */
    fun getRangeName(range: PitchDetector.FrequencyRange): String {
        return when (range) {
            PitchDetector.FrequencyRange.STANDARD -> "Стандартный диапазон (C2-C6)"
            PitchDetector.FrequencyRange.EXTENDED -> "Расширенный диапазон (C0-C8)"
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        audioAnalyzer.stop()
    }
}

