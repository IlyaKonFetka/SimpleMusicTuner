package com.example.simplemusictuner

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

/**
 * Класс для определения музыкальных нот по частоте
 */
class PitchDetector {
    
    companion object {
        // A4 = 440 Hz (стандартная настройка)
        private const val A4_FREQUENCY = 440.0
        private const val A4_MIDI_NOTE = 69
        
        // Названия нот
        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }
    
    /**
     * Диапазон частот для определения нот
     */
    enum class FrequencyRange(val minMidiNote: Int, val maxMidiNote: Int) {
        STANDARD(36, 84),    // C2 (65.41 Hz) - C6 (1046.50 Hz)
        EXTENDED(24, 96)     // C0 (16.35 Hz) - C8 (4186.01 Hz)
    }
    
    /**
     * Информация о ноте
     */
    data class NoteInfo(
        val noteName: String,           // Название ноты с октавой (например, "A4")
        val frequency: Double,          // Идеальная частота ноты в Гц
        val centsOffset: Double,        // Отклонение от идеальной частоты в центах
        val midiNote: Int,             // MIDI номер ноты
        val guitarString: GuitarString? // Информация о струне гитары, если совпадает
    )
    
    /**
     * Информация о струне гитары
     */
    data class GuitarString(
        val number: Int,                // Номер струны (1-6)
        val name: String               // Название (например, "1-я (E4)")
    )
    
    /**
     * Определяет ближайшую ноту по частоте
     * @param frequency частота в Гц
     * @param range диапазон частот
     * @return информация о ближайшей ноте или null, если частота вне диапазона
     */
    fun detectNote(frequency: Double, range: FrequencyRange = FrequencyRange.STANDARD): NoteInfo? {
        if (frequency <= 0) return null
        
        // Вычисляем MIDI номер ноты
        val midiNote = frequencyToMidiNote(frequency)
        
        // Проверяем, что нота в допустимом диапазоне
        if (midiNote < range.minMidiNote || midiNote > range.maxMidiNote) {
            return null
        }
        
        // Получаем идеальную частоту ближайшей ноты
        val idealFrequency = midiNoteToFrequency(midiNote)
        
        // Вычисляем отклонение в центах (1 полутон = 100 центов)
        val centsOffset = 1200 * ln(frequency / idealFrequency) / ln(2.0)
        
        // Формируем название ноты
        val noteName = getNoteName(midiNote)
        
        // Проверяем, соответствует ли это струне гитары
        val guitarString = getGuitarString(midiNote, centsOffset)
        
        return NoteInfo(noteName, idealFrequency, centsOffset, midiNote, guitarString)
    }
    
    /**
     * Определяет, соответствует ли нота струне классической гитары
     * Стандартный строй: E2 (82.41Hz), A2 (110Hz), D3 (146.83Hz), 
     *                    G3 (196Hz), B3 (246.94Hz), E4 (329.63Hz)
     */
    private fun getGuitarString(midiNote: Int, centsOffset: Double): GuitarString? {
        // Проверяем, что отклонение небольшое (в пределах ±30 центов)
        if (kotlin.math.abs(centsOffset) > 30) {
            return null
        }
        
        return when (midiNote) {
            40 -> GuitarString(6, "6-я (E2)") // E2 - самая толстая струна
            45 -> GuitarString(5, "5-я (A2)") // A2
            50 -> GuitarString(4, "4-я (D3)") // D3
            55 -> GuitarString(3, "3-я (G3)") // G3
            59 -> GuitarString(2, "2-я (B3)") // B3
            64 -> GuitarString(1, "1-я (E4)") // E4 - самая тонкая струна
            else -> null
        }
    }
    
    /**
     * Преобразует частоту в MIDI номер ноты (с округлением)
     */
    private fun frequencyToMidiNote(frequency: Double): Int {
        val midiNote = 12 * ln(frequency / A4_FREQUENCY) / ln(2.0) + A4_MIDI_NOTE
        return round(midiNote).toInt()
    }
    
    /**
     * Преобразует MIDI номер в частоту
     */
    private fun midiNoteToFrequency(midiNote: Int): Double {
        return A4_FREQUENCY * 2.0.pow((midiNote - A4_MIDI_NOTE) / 12.0)
    }
    
    /**
     * Получает название ноты с октавой по MIDI номеру
     */
    private fun getNoteName(midiNote: Int): String {
        val noteIndex = midiNote % 12
        val octave = (midiNote / 12) - 1
        return "${NOTE_NAMES[noteIndex]}$octave"
    }
    
    /**
     * Проверяет, находится ли частота в допустимом диапазоне
     */
    fun isInRange(frequency: Double, range: FrequencyRange): Boolean {
        if (frequency <= 0) return false
        val minFreq = midiNoteToFrequency(range.minMidiNote)
        val maxFreq = midiNoteToFrequency(range.maxMidiNote)
        return frequency in minFreq..maxFreq
    }
    
    /**
     * Получает минимальную частоту диапазона
     */
    fun getMinFrequency(range: FrequencyRange): Double {
        return midiNoteToFrequency(range.minMidiNote)
    }
    
    /**
     * Получает максимальную частоту диапазона
     */
    fun getMaxFrequency(range: FrequencyRange): Double {
        return midiNoteToFrequency(range.maxMidiNote)
    }
}

