package com.example.anomalydetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anomalydetector.ui.theme.AnomalyDetectorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnomalyDetectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnomalyDetectorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnomalyDetectorScreen() {
    val context = LocalContext.current
    val viewModel: AnomalyViewModel = viewModel { AnomalyViewModel(context.applicationContext) }
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var inputData by remember { mutableStateOf("1.5,2.3,4.1\n2.1,1.8,3.9\n0.8,5.2,1.3") }
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8000") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Anomaly Detector",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Режим работы
        if (uiState.mode.isNotEmpty()) {
            Text(
                text = "Режим: ${uiState.mode}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // URL сервера для онлайн режима
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL (для онлайн режима)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Поле для данных
        OutlinedTextField(
            value = inputData,
            onValueChange = { inputData = it },
            label = { Text("Введите данные (CSV формат)") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            placeholder = { Text("1.5,2.3,4.1\n2.1,1.8,3.9\n...") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        viewModel.detectAnomaliesOffline(inputData)
                    }
                },
                enabled = !uiState.isLoading && inputData.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Офлайн ONNX")
            }
            
            Button(
                onClick = {
                    coroutineScope.launch {
                        viewModel.detectAnomaliesOnline(serverUrl, inputData)
                    }
                },
                enabled = !uiState.isLoading && inputData.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Онлайн API")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Индикатор загрузки
        if (uiState.isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Результаты
        if (uiState.detectionResult != null) {
            ResultCard(result = uiState.detectionResult!!)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Детали аномалий
        if (!uiState.anomalyDetails.isNullOrEmpty()) {
            Text(
                text = "Детальная информация (показано первые 10):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.height(400.dp)
            ) {
                items(uiState.anomalyDetails!!.take(10)) { detail ->
                    AnomalyDetailCard(detail = detail)
                }
            }
        }
        
        // Ошибки
        if (uiState.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
            ) {
                Text(
                    text = "Ошибка: ${uiState.errorMessage}",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
fun ResultCard(result: DetectionResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.anomaly_rate > 0.1) 
                Color.Red.copy(alpha = 0.1f) else Color.Green.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Результаты детекции",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Сообщение: ${result.message}")
            Text("Всего образцов: ${result.total_samples}")
            Text("Аномалий найдено: ${result.anomalies_detected}")
            Text("Доля аномалий: ${String.format("%.2f", result.anomaly_rate * 100)}%")
            Text("Время обработки: ${String.format("%.3f", result.processing_time)}с")
        }
    }
}

@Composable
fun AnomalyDetailCard(detail: AnomalyDetail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (detail.is_anomaly) 
                Color.Red.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#${detail.index}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (detail.is_anomaly) "АНОМАЛИЯ" else "НОРМА",
                    color = if (detail.is_anomaly) Color.Red else Color.Green,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text("Скор: ${String.format("%.3f", detail.anomaly_score)}")
            Text("Серьезность: ${detail.severity}")
            if (detail.confidence > 0) {
                Text("Уверенность: ${String.format("%.3f", detail.confidence)}")
            }
        }
    }
}
