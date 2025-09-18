package com.yourpackage.anomalydetector.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.yourpackage.anomalydetector.R
import com.yourpackage.anomalydetector.data.models.DetectedDevice
import com.yourpackage.anomalydetector.domain.detector.MovementAnomalyDetector
import com.yourpackage.anomalydetector.utils.toReadableDistance
import com.yourpackage.anomalydetector.utils.toReadableSpeed
import com.yourpackage.anomalydetector.utils.toReadableTime

class MainActivity : AppCompatActivity() {
    
    private val anomalyDetector = MovementAnomalyDetector()
    
    // UI элементы
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var startTestButton: Button
    private lateinit var clearDataButton: Button
    private lateinit var showSessionsButton: Button
    
    // Константы для разрешений
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupClickListeners()
        checkPermissions()
        
        // Запускаем периодическую очистку старых записей
        startPeriodicCleanup()
        
        // Обновляем статистику каждую секунду
        startStatsUpdater()
    }
    
    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        statsText = findViewById(R.id.statsText)
        startTestButton = findViewById(R.id.startTestButton)
        clearDataButton = findViewById(R.id.clearDataButton)
        showSessionsButton = findViewById(R.id.showSessionsButton)
        
        updateStatus("Готов к работе")
    }
    
    private fun setupClickListeners() {
        startTestButton.setOnClickListener {
            startTestScenario()
        }
        
        clearDataButton.setOnClickListener {
            clearAllData()
        }
        
        showSessionsButton.setOnClickListener {
            showActiveSessions()
        }
    }
    
    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        // Android 12+ (API 31+) разрешения для Bluetooth
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        // Android 13+ (API 33+) разрешения для Wi-Fi
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add("android.permission.NEARBY_WIFI_DEVICES")
        }
        
        return permissions.toTypedArray()
    }
    
    private fun checkPermissions() {
        val missingPermissions = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, 
                missingPermissions.toTypedArray(), 
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            updateStatus("Разрешения получены, готов к работе")
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<out String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    updateStatus("Разрешения получены, готов к работе")
                } else {
                    updateStatus("Требуются разрешения для работы с местоположением")
                    showPermissionExplanation()
                }
            }
        }
    }
    
    private fun showPermissionExplanation() {
        AlertDialog.Builder(this)
            .setTitle("Требуются разрешения")
            .setMessage("Для работы детектора необходимы разрешения на:\n" +
                    "• Местоположение (для определения координат)\n" +
                    "• Bluetooth (для сканирования устройств)\n" +
                    "• Wi-Fi (для обнаружения устройств)")
            .setPositiveButton("Предоставить") { _, _ -> checkPermissions() }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    // Функция вызывается при обнаружении нового устройства

    fun onDeviceDetected(
        macAddress: String, 
        lat: Double, 
        lon: Double, 
        deviceType: String,
        accuracyMeters: Float? = null
    ) {
        val device = DetectedDevice(
            macAddress = macAddress,
            latitude = lat,
            longitude = lon,
            timestamp = System.currentTimeMillis(),
            deviceClass = deviceType,
            accuracyMeters = accuracyMeters
        )
        
        Log.d("DeviceDetection", 
            "Detected: $macAddress at ($lat, $lon), accuracy: ${accuracyMeters}m, type: $deviceType")
        
        // Проверяем на аномалии
        val anomalyResult = anomalyDetector.processDetectedDevice(device)
        
        anomalyResult?.let { result ->
            if (result.isAnomalous) {
                showAnomalyAlert(result.message)
                Log.w("AnomalyDetector", result.message)
                
                // Обновляем статус
                updateStatus("АНОМАЛИЯ ОБНАРУЖЕНА!")
                
                // Показываем toast
                Toast.makeText(this, "Аномальное перемещение!", Toast.LENGTH_LONG).show()
            }
        }
        
        // Обновляем статистику
        updateStatsDisplay()
    }
    
    private fun showAnomalyAlert(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Обнаружена аномалия")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> 
                    dialog.dismiss()
                    updateStatus("Готов к работе")
                }
                .setNeutralButton("Показать сессии") { dialog, _ ->
                    dialog.dismiss()
                    showActiveSessions()
                }
                .setCancelable(false)
                .show()
        }
    }
    
    private fun updateStatus(status: String) {
        runOnUiThread {
            statusText.text = "Статус: $status"
        }
    }
    
    private fun updateStatsDisplay() {
        runOnUiThread {
            val stats = anomalyDetector.getDetectorStats()
            val statsString = buildString {
                appendLine("📊 Статистика детектора:")
                appendLine("Активных сессий: ${stats.activeSessions}")
                appendLine("Пеших: ${stats.walkingSessions}")
                appendLine("На авто: ${stats.drivingSessions}")
                appendLine("С алертами: ${stats.alertedSessions}")
                appendLine("Общая дистанция: ${stats.totalDistanceTracked.toReadableDistance()}")
            }
            statsText.text = statsString
        }
    }
    
    private fun startTestScenario() {
        updateStatus("Запуск тестового сценария...")
        startTestButton.isEnabled = false
        
        lifecycleScope.launch {
            try {
                runTestScenario()
            } catch (e: Exception) {
                Log.e("TestScenario", "Error in test scenario", e)
                updateStatus("Ошибка в тестовом сценарии")
            } finally {
                startTestButton.isEnabled = true
            }
        }
    }
    
    private suspend fun runTestScenario() {
        val testMac = "AA:BB:CC:DD:EE:FF"
        
        updateStatus("Тест: первое обнаружение...")
        // Первое обнаружение - Красная площадь, Москва
        onDeviceDetected(testMac, 55.7539, 37.6208, "Wi-Fi", 15f)
        delay(2000)
        
        updateStatus("Тест: мелкие перемещения (должны игнорироваться)...")
        // Серия небольших перемещений (< 10м) - должны игнорироваться
        onDeviceDetected(testMac, 55.7540, 37.6209, "Wi-Fi", 12f)
        delay(1000)
        
        onDeviceDetected(testMac, 55.7541, 37.6210, "Wi-Fi", 18f)
        delay(1000)
        
        updateStatus("Тест: значимое пешее перемещение...")
        // Значимое перемещение - к Большому театру (~500м)
        onDeviceDetected(testMac, 55.7596, 37.6189, "Wi-Fi", 25f)
        delay(3000)
        
        updateStatus("Тест: продолжение пешего маршрута...")
        // Еще одно перемещение - к метро Театральная (~300м)
        onDeviceDetected(testMac, 55.7587, 37.6212, "Wi-Fi", 20f)
        delay(2000)
        
        updateStatus("Тест: большое перемещение (должно вызвать аномалию)...")
        // Большое перемещение - к Парку Горького (~3км от начала)
        // Это должно превысить порог в 1км для пешего хода и вызвать ОДНОРАЗОВЫЙ алерт
        onDeviceDetected(testMac, 55.7312, 37.6016, "Wi-Fi", 30f)
        delay(1000)
        
        updateStatus("Тест: повторное превышение (алерт НЕ должен сработать)...")
        // Еще одно перемещение после алерта - алерт НЕ должен повториться
        onDeviceDetected(testMac, 55.7300, 37.6000, "Wi-Fi", 35f)
        delay(1000)
        
        updateStatus("Тестовый сценарий завершен")
    }
    
    private fun clearAllData() {
        AlertDialog.Builder(this)
            .setTitle("Очистка данных")
            .setMessage("Очистить все данные о сессиях и аномалиях?")
            .setPositiveButton("Да") { _, _ ->
                anomalyDetector.reset()
                updateStatsDisplay()
                updateStatus("Данные очищены")
                Toast.makeText(this, "Все данные очищены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showActiveSessions() {
        val sessions = anomalyDetector.getAllActiveSessions()
        
        if (sessions.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Активные сессии")
                .setMessage("Нет активных сессий")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        val sessionInfo = buildString {
            appendLine("📱 Активные сессии (${sessions.size}):")
            appendLine()
            
            sessions.forEachIndexed { index, session ->
                appendLine("${index + 1}. MAC: ${session.macAddress}")
                appendLine("   Тип: ${getMovementTypeText(session.currentMovementType)}")
                appendLine("   Дистанция: ${session.totalDistance.toReadableDistance()}")
                appendLine("   Средняя скорость: ${session.averageSpeed.toReadableSpeed()}")
                appendLine("   Макс. скорость: ${session.maxSpeed.toReadableSpeed()}")
                appendLine("   Алерт: ${if (session.alerted) "Сработал" else "Не сработал"}")
                
                val duration = session.lastActivityTime - session.startTime
                appendLine("   Длительность: ${duration.toReadableTime()}")
                appendLine()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Активные сессии")
            .setMessage(sessionInfo)
            .setPositiveButton("OK", null)
            .setNeutralButton("Очистить все") { _, _ -> clearAllData() }
            .show()
    }
    
    private fun getMovementTypeText(type: com.yourpackage.anomalydetector.domain.enums.MovementType): String {
        return when (type) {
            com.yourpackage.anomalydetector.domain.enums.MovementType.WALKING -> "Пешком"
            com.yourpackage.anomalydetector.domain.enums.MovementType.DRIVING -> "На авто"
        }
    }
    
    private fun startPeriodicCleanup() {
        lifecycleScope.launch {
            while (true) {
                delay(5 * 60 * 1000L) // каждые 5 минут
                anomalyDetector.cleanOldRecords()
                Log.d("Cleanup", "Periodic cleanup completed")
            }
        }
    }
    
    private fun startStatsUpdater() {
        lifecycleScope.launch {
            while (true) {
                updateStatsDisplay()
                delay(1000L) // каждую секунду
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        anomalyDetector.cleanOldRecords()
        Log.d("MainActivity", "Activity destroyed, cleanup completed")
    }
    
    override fun onPause() {
        super.onPause()
        // Можно добавить логику приостановки сканирования
    }
    
    override fun onResume() {
        super.onResume()
        updateStatsDisplay()
        // Можно добавить логику возобновления сканирования
    }
}
