package com.example.simplemusictuner

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Класс для выполнения быстрого преобразования Фурье (FFT)
 * и определения доминирующей частоты в аудио-сигнале
 */
class FFTHelper {
    
    /**
     * Результат определения частоты с метрикой уверенности
     */
    data class FrequencyResult(
        val frequency: Double,
        val confidence: Double  // 0.0 - 1.0, где 1.0 = очень уверены
    )
    
    /**
     * Выполняет FFT и возвращает доминирующую частоту с уверенностью
     * @param audioData массив аудио-данных
     * @param sampleRate частота дискретизации
     * @param sensitivity коэффициент чувствительности (0.5-2.0, где 1.0 = стандартная)
     * @return результат с частотой и уверенностью, или null если не обнаружено
     */
    fun detectFrequency(audioData: ShortArray, sampleRate: Int, sensitivity: Float = 1.0f): FrequencyResult? {
        val size = audioData.size
        
        // Преобразуем short в double и применяем окно Ханна
        val signal = DoubleArray(size) { i ->
            val window = 0.5 * (1 - cos(2 * PI * i / (size - 1)))
            audioData[i].toDouble() * window
        }
        
        // Применяем FFT
        val fftResult = fft(signal)
        
        // Находим пиковую частоту с анализом уверенности
        return findPeakFrequencyWithConfidence(fftResult, sampleRate, audioData, sensitivity)
    }
    
    /**
     * Быстрое преобразование Фурье
     */
    private fun fft(signal: DoubleArray): Array<Complex> {
        val n = signal.size
        
        // Проверяем, что размер - степень двойки
        if (n and (n - 1) != 0) {
            throw IllegalArgumentException("Размер массива должен быть степенью двойки")
        }
        
        // Базовый случай
        if (n == 1) {
            return arrayOf(Complex(signal[0], 0.0))
        }
        
        // Разделяем на четные и нечетные
        val even = DoubleArray(n / 2) { signal[it * 2] }
        val odd = DoubleArray(n / 2) { signal[it * 2 + 1] }
        
        // Рекурсивно вычисляем FFT
        val fftEven = fft(even)
        val fftOdd = fft(odd)
        
        // Объединяем результаты
        val result = Array(n) { Complex(0.0, 0.0) }
        
        for (k in 0 until n / 2) {
            val angle = -2.0 * PI * k / n
            val w = Complex(cos(angle), sin(angle))
            val t = w * fftOdd[k]
            
            result[k] = fftEven[k] + t
            result[k + n / 2] = fftEven[k] - t
        }
        
        return result
    }
    
    /**
     * Находит пиковую частоту с анализом уверенности
     */
    private fun findPeakFrequencyWithConfidence(
        fftResult: Array<Complex>, 
        sampleRate: Int,
        audioData: ShortArray,
        sensitivity: Float
    ): FrequencyResult? {
        val n = fftResult.size
        val magnitudes = DoubleArray(n / 2) { fftResult[it].magnitude() }
        
        // Игнорируем очень низкие частоты (ниже 60 Гц - шум, гул)
        val minIndex = (60.0 * n / sampleRate).toInt().coerceAtLeast(1)
        // Игнорируем очень высокие частоты (выше 4000 Гц)
        val maxFreqIndex = (4000.0 * n / sampleRate).toInt().coerceAtMost(magnitudes.size - 1)
        
        // Находим индекс максимальной магнитуды
        var maxIndex = minIndex
        var maxMagnitude = magnitudes[minIndex]
        
        for (i in minIndex..maxFreqIndex) {
            if (magnitudes[i] > maxMagnitude) {
                maxMagnitude = magnitudes[i]
                maxIndex = i
            }
        }
        
        // Проверяем RMS (Root Mean Square) исходного сигнала
        val rms = calculateRMS(audioData)
        
        // Минимальный порог RMS - отфильтровываем тихие звуки
        // Чем выше чувствительность, тем ниже порог (обратная зависимость)
        val rmsThreshold = 500.0 / sensitivity
        if (rms < rmsThreshold) {
            return null
        }
        
        // Минимальный порог магнитуды - отфильтровываем шум
        val magnitudeThreshold = 200.0 / sensitivity
        if (maxMagnitude < magnitudeThreshold) {
            return null
        }
        
        // Вычисляем среднюю магнитуду для сравнения
        val avgMagnitude = magnitudes.slice(minIndex..maxFreqIndex).average()
        
        // Пик должен быть значительно выше среднего (SNR - Signal-to-Noise Ratio)
        val snr = maxMagnitude / avgMagnitude
        // Чем выше чувствительность, тем ниже требуемый SNR
        val snrThreshold = 3.0 / sensitivity
        if (snr < snrThreshold) {
            return null  // Слишком много шума
        }
        
        // Проверяем "остроту" пика - музыкальные ноты имеют острые пики
        val peakSharpness = calculatePeakSharpness(magnitudes, maxIndex)
        // Чем выше чувствительность, тем ниже требуемая острота
        val sharpnessThreshold = 0.3 / sensitivity
        if (peakSharpness < sharpnessThreshold) {
            return null  // Пик слишком размытый (шум)
        }
        
        // Интерполяция для более точного определения частоты
        val frequency = if (maxIndex > 0 && maxIndex < magnitudes.size - 1) {
            val alpha = magnitudes[maxIndex - 1]
            val beta = magnitudes[maxIndex]
            val gamma = magnitudes[maxIndex + 1]
            
            val p = 0.5 * (alpha - gamma) / (alpha - 2 * beta + gamma)
            val interpolatedIndex = maxIndex + p
            
            interpolatedIndex * sampleRate / n
        } else {
            maxIndex.toDouble() * sampleRate / n
        }
        
        // Вычисляем уверенность на основе нескольких факторов
        val confidence = calculateConfidence(snr, peakSharpness, rms)
        
        return FrequencyResult(frequency, confidence)
    }
    
    /**
     * Вычисляет RMS (среднеквадратичное значение) сигнала
     */
    private fun calculateRMS(audioData: ShortArray): Double {
        var sum = 0.0
        for (sample in audioData) {
            sum += sample * sample
        }
        return sqrt(sum / audioData.size)
    }
    
    /**
     * Вычисляет "остроту" пика - насколько он выделяется
     */
    private fun calculatePeakSharpness(magnitudes: DoubleArray, peakIndex: Int): Double {
        if (peakIndex < 3 || peakIndex >= magnitudes.size - 3) {
            return 0.0
        }
        
        val peak = magnitudes[peakIndex]
        
        // Смотрим на окрестности пика
        val neighbors = listOf(
            magnitudes[peakIndex - 3],
            magnitudes[peakIndex - 2],
            magnitudes[peakIndex - 1],
            magnitudes[peakIndex + 1],
            magnitudes[peakIndex + 2],
            magnitudes[peakIndex + 3]
        )
        
        val avgNeighbor = neighbors.average()
        
        // Отношение пика к окружению
        return (peak - avgNeighbor) / peak
    }
    
    /**
     * Вычисляет общую уверенность в результате
     */
    private fun calculateConfidence(snr: Double, peakSharpness: Double, rms: Double): Double {
        // SNR компонент (0-1)
        val snrComponent = (snr / 10.0).coerceIn(0.0, 1.0)
        
        // Острота пика (0-1)
        val sharpnessComponent = peakSharpness.coerceIn(0.0, 1.0)
        
        // RMS компонент (0-1) - сильные сигналы = больше уверенности
        val rmsComponent = (rms / 5000.0).coerceIn(0.0, 1.0)
        
        // Взвешенная комбинация
        return (snrComponent * 0.4 + sharpnessComponent * 0.4 + rmsComponent * 0.2)
    }
    
    /**
     * Приводит размер массива к степени двойки
     */
    fun padToPowerOfTwo(data: ShortArray): ShortArray {
        var size = 1
        while (size < data.size) {
            size *= 2
        }
        
        if (size == data.size) {
            return data
        }
        
        return ShortArray(size) { i ->
            if (i < data.size) data[i] else 0
        }
    }
    
    /**
     * Класс для представления комплексного числа
     */
    private data class Complex(val real: Double, val imag: Double) {
        operator fun plus(other: Complex) = Complex(real + other.real, imag + other.imag)
        operator fun minus(other: Complex) = Complex(real - other.real, imag - other.imag)
        operator fun times(other: Complex) = Complex(
            real * other.real - imag * other.imag,
            real * other.imag + imag * other.real
        )
        
        fun magnitude() = sqrt(real * real + imag * imag)
    }
}

