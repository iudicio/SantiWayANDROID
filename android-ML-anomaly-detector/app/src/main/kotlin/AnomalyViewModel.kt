package com.example.anomalydetector

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnomalyViewModel(
    private val appContext: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AnomalyUiState())
    val uiState: StateFlow<AnomalyUiState> = _uiState.asStateFlow()
    
    private val apiService = AnomalyApiService()
    private val onnx by lazy { AnomalyOnnx(appContext) }
    
    fun detectAnomaliesOffline(inputData: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                mode = "Офлайн ONNX"
            )
            
            try {
                val startTime = System.currentTimeMillis()
                
                // Парсинг входных данных
                val batch = parseInputData(inputData)
                Log.d("AnomalyViewModel", "Parsed ${batch.size} samples")
                
                // Офлайн предсказание через ONNX
                val results = withContext(Dispatchers.Default) {
                    onnx.predictBatch(batch)
                }
                
                val processingTime = (System.currentTimeMillis() - startTime) / 1000.0
                val anomaliesCount = results.count { it.isAnomaly }
                
                // Конвертация в UI модель
                val details = results.map { result ->
                    AnomalyDetail(
                        index = result.index,
                        is_anomaly = result.isAnomaly,
                        anomaly_score = result.score.toDouble(),
                        anomaly_probability = if (result.isAnomaly) 0.8 else 0.2,
                        severity = result.severity,
                        confidence = result.confidence.toDouble(),
                        timestamp = System.currentTimeMillis().toString()
                    )
                }
                
                val response = DetectionResponse(
                    success = true,
                    message = "Обработано офлайн через ONNX",
                    total_samples = batch.size,
                    anomalies_detected = anomaliesCount,
                    anomaly_rate = if (batch.isNotEmpty()) anomaliesCount.toDouble() / batch.size else 0.0,
                    processing_time = processingTime,
                    details = details
                )
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    detectionResult = response,
                    anomalyDetails = details
                )
                
            } catch (e: Exception) {
                Log.e("AnomalyViewModel", "Ошибка офлайн детекции", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Ошибка офлайн детекции: ${e.message}"
                )
            }
        }
    }
    
    fun detectAnomaliesOnline(serverUrl: String, inputData: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                mode = "Онлайн API"
            )
            
            try {
                val parsedData = parseInputDataForApi(inputData)
                val request = DetectionRequest(
                    data = parsedData,
                    return_details = true
                )
                
                val response = apiService.detectAnomalies(serverUrl, request)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    detectionResult = response,
                    anomalyDetails = response.details
                )
                
            } catch (e: Exception) {
                Log.e("AnomalyViewModel", "Ошибка онлайн детекции", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Ошибка онлайн детекции: ${e.message}"
                )
            }
        }
    }
    
    private fun parseInputData(input: String): List<FloatArray> {
        return input.trim().lines()
            .filter { it.isNotBlank() }
            .map { line ->
                line.split(",")
                    .map { it.trim().replace(',', '.').toFloat() }
                    .toFloatArray()
            }
    }
    
    // Парсинг чисел для онлайн режима с учетом запятой как десятичный разделитель
    private fun parseInputDataForApi(input: String): List<List<Double>> {
        return input.trim().lines()
            .filter { it.isNotBlank() }
            .map { line ->
                line.split(",").map { it.trim().replace(',', '.').toDouble() }
            }
    }
    
    override fun onCleared() {
        super.onCleared()
        if (::onnx.isInitialized) {
            onnx.close()
        }
    }
}

data class AnomalyUiState(
    val isLoading: Boolean = false,
    val detectionResult: DetectionResponse? = null,
    val anomalyDetails: List<AnomalyDetail>? = null,
    val errorMessage: String? = null,
    val mode: String = ""
)
