package com.example.simplemusictuner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Главный экран тюнера
 */
@Composable
fun TunerScreen(
    viewModel: TunerViewModel
) {
    val currentFrequency by viewModel.currentFrequency.collectAsState()
    val noteInfo by viewModel.noteInfo.collectAsState()
    val frequencyRange by viewModel.frequencyRange.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val waveformData by viewModel.waveformData.collectAsState()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
             // Заголовок
            Text(
                text = "Музыкальный тюнер",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Основная информация
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // График звуковой волны
                 WaveformGraph(
                    waveformData = waveformData,
                    isActive = isRecording
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Текущая частота
                 FrequencyDisplay(
                    frequency = currentFrequency,
                    isActive = isRecording && currentFrequency > 0
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Индикатор уровня сигнала/уверенности (всегда показываем)
                SignalStrengthIndicator(confidence = confidence)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Информация о ноте (всегда показываем)
                NoteCard(
                    noteInfo = noteInfo,
                    isActive = noteInfo != null
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Индикатор отклонения (всегда показываем)
                TuningIndicator(centsOffset = noteInfo?.centsOffset ?: 0.0)
            }
            
             // Ползунок чувствительности
            SensitivitySlider(
                sensitivity = sensitivity,
                onSensitivityChange = { viewModel.setSensitivity(it) },
                onReset = { viewModel.setSensitivity(SettingsManager.DEFAULT_SENSITIVITY) }
            )
            
            // Кнопка переключения диапазона
            Button(
                onClick = { viewModel.toggleFrequencyRange() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text(
                    text = viewModel.getRangeName(frequencyRange),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * График звуковой волны в реальном времени
 */
@Composable
fun WaveformGraph(
    waveformData: List<Float>,
    isActive: Boolean
) {
     Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (waveformData.isEmpty() || !isActive) {
                // Показываем плейсхолдер когда нет данных
                Text(
                    text = if (!isActive) "Ожидание..." else "Нет сигнала",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                // Рисуем график
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2
                    
                    // Рисуем центральную линию
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 1f
                    )
                    
                    // Рисуем волну
                    if (waveformData.size > 1) {
                        val path = Path()
                        val stepX = width / waveformData.size.toFloat()
                        
                        // Начинаем с первой точки
                        val firstY = centerY - (waveformData[0] * (height / 2) * 0.9f)
                        path.moveTo(0f, firstY)
                        
                        // Рисуем остальные точки
                        for (i in 1 until waveformData.size) {
                            val x = i * stepX
                            val y = centerY - (waveformData[i] * (height / 2) * 0.9f) // 0.9 для отступа
                            path.lineTo(x, y)
                        }
                        
                        // Рисуем путь
                        drawPath(
                            path = path,
                            color = Color(0xFF00C853), // Зеленый цвет для волны
                            style = Stroke(
                                width = 2f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Отображение текущей частоты
 */
@Composable
fun FrequencyDisplay(frequency: Double, isActive: Boolean) {
    val displayFrequency = if (frequency > 0) String.format("%.1f", frequency) else "---"
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = displayFrequency,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Гц",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Карточка с информацией о ноте
 */
@Composable
fun NoteCard(noteInfo: PitchDetector.NoteInfo?, isActive: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = noteInfo?.noteName ?: "---",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (noteInfo != null) String.format("%.2f Гц", noteInfo.frequency) else "0.00 Гц",
                fontSize = 12.sp,
                color = if (isActive) 
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            
            // Место для струны гитары (всегда резервируем)
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (noteInfo?.guitarString != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "🎸 ${noteInfo.guitarString.name}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Индикатор настройки (отклонение в центах)
 */
@Composable
fun TuningIndicator(centsOffset: Double) {
    // Анимация для плавного перехода
    val animatedOffset by animateFloatAsState(
        targetValue = centsOffset.toFloat().coerceIn(-50f, 50f),
        animationSpec = tween(durationMillis = 100),
        label = "cents_offset"
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Текстовое отображение отклонения
        val offsetText = if (centsOffset >= 0) {
            "+${String.format("%.0f", centsOffset)}"
        } else {
            String.format("%.0f", centsOffset)
        }
        
         Text(
            text = "$offsetText центов",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = getTuningColor(abs(centsOffset))
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Визуальный индикатор
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 16.dp)
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2
            
            // Рисуем фон индикатора (градиент от красного через желтый к зеленому)
            val barHeight = 20f
            val barTop = centerY - barHeight / 2
            
            // Красная зона слева
            drawRect(
                color = Color.Red.copy(alpha = 0.3f),
                topLeft = Offset(0f, barTop),
                size = androidx.compose.ui.geometry.Size(width * 0.3f, barHeight)
            )
            
            // Желтая зона слева
            drawRect(
                color = Color(0xFFFFB300).copy(alpha = 0.3f),
                topLeft = Offset(width * 0.3f, barTop),
                size = androidx.compose.ui.geometry.Size(width * 0.2f, barHeight)
            )
            
            // Зеленая зона в центре
            drawRect(
                color = Color.Green.copy(alpha = 0.4f),
                topLeft = Offset(width * 0.4f, barTop),
                size = androidx.compose.ui.geometry.Size(width * 0.2f, barHeight)
            )
            
            // Желтая зона справа
            drawRect(
                color = Color(0xFFFFB300).copy(alpha = 0.3f),
                topLeft = Offset(width * 0.6f, barTop),
                size = androidx.compose.ui.geometry.Size(width * 0.2f, barHeight)
            )
            
            // Красная зона справа
            drawRect(
                color = Color.Red.copy(alpha = 0.3f),
                topLeft = Offset(width * 0.7f, barTop),
                size = androidx.compose.ui.geometry.Size(width * 0.3f, barHeight)
            )
            
            // Деления
            for (i in -5..5) {
                val x = centerX + (i * width / 10)
                val lineHeight = if (i == 0) 30f else 15f
                drawLine(
                    color = Color.Gray,
                    start = Offset(x, centerY - lineHeight / 2),
                    end = Offset(x, centerY + lineHeight / 2),
                    strokeWidth = if (i == 0) 3f else 1f
                )
            }
            
             // Стрелка-индикатор
            val indicatorX = centerX + (animatedOffset / 50f) * (width / 2)
            val arrowSize = 10f
            
            val arrowPath = Path().apply {
                moveTo(indicatorX, barTop - 10f)
                lineTo(indicatorX - arrowSize, barTop - 10f - arrowSize)
                lineTo(indicatorX + arrowSize, barTop - 10f - arrowSize)
                close()
            }
            
            drawPath(
                path = arrowPath,
                color = getTuningColor(abs(centsOffset.toDouble())),
                style = Fill
            )
            
            // Линия от стрелки
            drawLine(
                color = getTuningColor(abs(centsOffset.toDouble())),
                start = Offset(indicatorX, barTop - 10f),
                end = Offset(indicatorX, barTop + barHeight + 10f),
                strokeWidth = 3f
            )
        }
        
         // Подсказки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "♭",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "В тон",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (abs(centsOffset) < 5) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "♯",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Ползунок чувствительности
 */
@Composable
fun SensitivitySlider(
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Чувствительность:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when {
                        sensitivity < 0.5f -> "Минимум"
                        sensitivity < 1.0f -> "Низкая"
                        sensitivity < 1.5f -> "Средняя"
                        sensitivity < 2.5f -> "Высокая"
                        else -> "Максимум"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                // Кнопка сброса
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Сброс",
                        fontSize = 11.sp
                    )
                }
            }
        }
        
        Slider(
            value = sensitivity,
            onValueChange = onSensitivityChange,
            valueRange = 0.1f..5.0f,
            steps = 48, // 50 позиций между 0.1 и 5.0 (шаг 0.1)
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Почти ничего",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = "Все звуки",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Индикатор уровня сигнала (уверенности)
 */
@Composable
fun SignalStrengthIndicator(confidence: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Сигнал:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Полоски индикатора уровня
        for (i in 1..5) {
            val threshold = i / 5.0
            val isActive = confidence >= threshold - 0.1
            
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height((12 + i * 4).dp)
                    .padding(horizontal = 1.dp)
                    .background(
                        color = if (isActive) {
                            when {
                                i <= 2 -> Color.Red
                                i <= 3 -> Color(0xFFFFB300) // Оранжевый
                                else -> Color.Green
                            }
                        } else {
                            Color.Gray.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = when {
                confidence < 0.4 -> Color.Red
                confidence < 0.7 -> Color(0xFFFFB300)
                else -> Color.Green
            },
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Возвращает цвет в зависимости от точности настройки
 */
fun getTuningColor(absCents: Double): Color {
    return when {
        absCents < 5 -> Color.Green
        absCents < 15 -> Color(0xFFFFB300) // Оранжевый
        else -> Color.Red
    }
}

