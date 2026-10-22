package com.example.simplemusictuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simplemusictuner.ui.theme.SimpleMusicTunerTheme

class MainActivity : ComponentActivity() {
    
    private var hasAudioPermission by mutableStateOf(false)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasAudioPermission = isGranted
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Проверяем разрешение
        checkAudioPermission()
        
        setContent {
            SimpleMusicTunerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MainContent(
                            hasPermission = hasAudioPermission,
                            onRequestPermission = { requestAudioPermission() },
                            application = application
                        )
                    }
                }
            }
        }
    }
    
    private fun checkAudioPermission() {
        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestAudioPermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
fun MainContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    application: android.app.Application
) {
    if (hasPermission) {
        val viewModel: TunerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
            factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )
        
        // Автоматически запускаем анализ
        LaunchedEffect(Unit) {
            viewModel.startAnalysis()
        }
        
        // Останавливаем анализ при выходе
        DisposableEffect(Unit) {
            onDispose {
                viewModel.stopAnalysis()
            }
        }
        
        TunerScreen(viewModel = viewModel)
    } else {
        // Экран запроса разрешения
        PermissionRequestScreen(onRequestPermission = onRequestPermission)
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Необходимо разрешение на использование микрофона",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        Text(
            text = "Приложение использует микрофон для анализа звука и определения музыкальных нот",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Предоставить разрешение",
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}