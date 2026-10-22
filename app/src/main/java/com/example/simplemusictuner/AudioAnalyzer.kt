package com.example.simplemusictuner

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Класс для захвата и анализа аудио с микрофона
 */
class AudioAnalyzer {
    
    companion object {
        private const val SAMPLE_RATE = 44100  // Частота дискретизации
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val UPDATE_INTERVAL_MS = 50L  // Обновление ~20 раз в секунду
        
        // Параметры стабилизации
        private const val STABILITY_WINDOW_SIZE = 5  // Количество последних измерений
        private const val STABILITY_THRESHOLD = 0.05 // 5% разброс - стабильная частота
        private const val MIN_CONFIDENCE = 0.4  // Минимальная уверенность для отображения
    }
    
    private var audioRecord: AudioRecord? = null
    private var analysisJob: Job? = null
    private val fftHelper = FFTHelper()
    
    // История последних частот для проверки стабильности
    private val recentFrequencies = mutableListOf<Double>()
    
    private val _frequency = MutableStateFlow(0.0)
    val frequency: StateFlow<Double> = _frequency.asStateFlow()
    
    private val _confidence = MutableStateFlow(0.0)
    val confidence: StateFlow<Double> = _confidence.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    // Данные для графика (последние 1024 сэмпла)
    private val _waveformData = MutableStateFlow<List<Float>>(emptyList())
    val waveformData: StateFlow<List<Float>> = _waveformData.asStateFlow()
    
    // Текущая чувствительность
    private var currentSensitivity = 1.0f
    
    /**
     * Устанавливает чувствительность анализатора
     */
    fun setSensitivity(sensitivity: Float) {
        currentSensitivity = sensitivity.coerceIn(0.1f, 5.0f)
    }
    
    /**
     * Запускает захват и анализ аудио
     */
    fun start(scope: CoroutineScope) {
        if (_isRecording.value) return
        
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ).coerceAtLeast(8192)
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord не инициализирован")
            }
            
            audioRecord?.startRecording()
            _isRecording.value = true
            
            // Запускаем анализ в отдельной корутине
            analysisJob = scope.launch {
                analyzeAudio(bufferSize)
            }
            
        } catch (e: SecurityException) {
            // Нет разрешения на запись аудио
            e.printStackTrace()
            stop()
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }
    
    /**
     * Останавливает захват и анализ аудио
     */
    fun stop() {
        _isRecording.value = false
        analysisJob?.cancel()
        analysisJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
        }
        
        recentFrequencies.clear()
        _frequency.value = 0.0
        _confidence.value = 0.0
        _waveformData.value = emptyList()
    }
    
    /**
     * Анализирует аудио данные
     */
    private suspend fun analyzeAudio(bufferSize: Int) = withContext(Dispatchers.IO) {
        // Размер буфера для FFT (степень двойки)
        var fftSize = 2048
        while (fftSize < bufferSize) {
            fftSize *= 2
        }
        
        val buffer = ShortArray(fftSize)
        
        while (isActive && _isRecording.value) {
            try {
                // Читаем данные с микрофона
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (readSize > 0) {
                    // Обновляем данные для графика (берем первые 1024 сэмпла)
                    val waveformSize = 1024.coerceAtMost(readSize)
                    val waveform = List(waveformSize) { i ->
                        buffer[i].toFloat() / Short.MAX_VALUE // Нормализуем к [-1, 1]
                    }
                    _waveformData.value = waveform
                    
                    // Приводим к степени двойки, если нужно
                    val processedBuffer = if (readSize < fftSize) {
                        ShortArray(fftSize) { i ->
                            if (i < readSize) buffer[i] else 0
                        }
                    } else {
                        buffer
                    }
                    
                    // Определяем частоту через FFT с уверенностью
                    val result = fftHelper.detectFrequency(processedBuffer, SAMPLE_RATE, currentSensitivity)
                    
                    if (result != null && result.confidence >= MIN_CONFIDENCE) {
                        // Проверяем стабильность частоты
                        val stableFrequency = checkFrequencyStability(result.frequency)
                        
                        if (stableFrequency != null) {
                            // Обновляем состояние только если частота стабильна
                            _frequency.value = stableFrequency
                            _confidence.value = result.confidence
                        } else {
                            // Частота нестабильна - сбрасываем
                            _frequency.value = 0.0
                            _confidence.value = 0.0
                        }
                    } else {
                        // Недостаточная уверенность или не обнаружено
                        recentFrequencies.clear()
                        _frequency.value = 0.0
                        _confidence.value = 0.0
                    }
                }
                
                // Задержка перед следующим обновлением
                delay(UPDATE_INTERVAL_MS)
                
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Проверяет стабильность частоты на основе истории измерений
     * @return стабильная частота или null если нестабильна
     */
    private fun checkFrequencyStability(newFrequency: Double): Double? {
        // Добавляем новую частоту в историю
        recentFrequencies.add(newFrequency)
        
        // Ограничиваем размер окна
        if (recentFrequencies.size > STABILITY_WINDOW_SIZE) {
            recentFrequencies.removeAt(0)
        }
        
        // Нужно минимум 3 измерения для проверки стабильности
        if (recentFrequencies.size < 3) {
            return null
        }
        
        // Вычисляем среднее и стандартное отклонение
        val average = recentFrequencies.average()
        val variance = recentFrequencies.map { (it - average) * (it - average) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        // Коэффициент вариации (относительное стандартное отклонение)
        val coefficientOfVariation = stdDev / average
        
        // Если разброс небольшой - частота стабильна
        return if (coefficientOfVariation <= STABILITY_THRESHOLD) {
            average  // Возвращаем среднее для сглаживания
        } else {
            // Частота нестабильна - возможно шум или переходный процесс
            // Очищаем историю и начинаем заново
            recentFrequencies.clear()
            null
        }
    }
    
    /**
     * Проверяет, инициализирован ли AudioRecord
     */
    fun isInitialized(): Boolean {
        return audioRecord?.state == AudioRecord.STATE_INITIALIZED
    }
}

