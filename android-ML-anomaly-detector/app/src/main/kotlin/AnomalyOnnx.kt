package com.example.anomalydetector

import android.content.Context
import ai.onnxruntime.*
import org.json.JSONObject
import java.io.BufferedInputStream
import java.util.zip.GZIPInputStream

class AnomalyOnnx(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val thresholds: Map<String, Float>
    private val nFeatures: Int

    init {
        // Загрузка ONNX модели из assets
        val onnxBytes = context.assets.open("anomaly.onnx").use { it.readBytes() }
        session = env.createSession(onnxBytes)
        inputName = session.inputNames.first()
        
        // Загрузка метаданных
        val metadata = loadMetadata(context)
        thresholds = metadata.first
        nFeatures = metadata.second
        
        // Диагностическое логирование
        android.util.Log.d("AnomalyOnnx", "ONNX loaded: input=$inputName, features=$nFeatures")
        android.util.Log.d("AnomalyOnnx", "Output names: ${session.outputInfo.keys}")
        session.outputInfo.forEach { (name, info) ->
            android.util.Log.d("AnomalyOnnx", " $name -> $info")
        }
    }

    private fun loadMetadata(context: Context): Pair<Map<String, Float>, Int> {
        try {
            val gz = GZIPInputStream(BufferedInputStream(context.assets.open("metadata.json.gz")))
            val json = gz.reader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(json)
            
            // Пороги серьезности
            val severityObj = obj.getJSONObject("severity_thresholds")
            val thresholds = mapOf(
                "CRITICAL" to severityObj.optDouble("CRITICAL", -0.65).toFloat(),
                "HIGH" to severityObj.optDouble("HIGH", -0.45).toFloat(),
                "MEDIUM" to severityObj.optDouble("MEDIUM", -0.20).toFloat()
            )
            
            val nFeatures = obj.optInt("n_features", 10)
            
            return Pair(thresholds, nFeatures)
        } catch (e: Exception) {
            android.util.Log.w("AnomalyOnnx", "Failed to load metadata, using defaults: ${e.message}")
            return Pair(
                mapOf("CRITICAL" to -0.65f, "HIGH" to -0.45f, "MEDIUM" to -0.20f),
                10
            )
        }
    }

    fun predictBatch(batch: List<FloatArray>): List<AnomalyResult> {
        require(batch.isNotEmpty()) { "Batch не может быть пустым" }
        
        val actualNFeatures = batch.first().size
        
        // Валидация числа признаков
        require(actualNFeatures == nFeatures) {
            "Ожидалось $nFeatures признаков, получено $actualNFeatures"
        }
        
        val shape = longArrayOf(batch.size.toLong(), actualNFeatures.toLong())
        val flatData = FloatArray(batch.size * actualNFeatures)
        
        // Упаковка данных в плоский массив
        var idx = 0
        for (row in batch) {
            require(row.size == actualNFeatures) { "Неверное количество признаков в строке" }
            System.arraycopy(row, 0, flatData, idx, actualNFeatures)
            idx += actualNFeatures
        }

        return OnnxTensor.createTensor(env, flatData, shape).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { result ->
                
                // Итерация по OrtSession.Result
                val outputNames = session.outputInfo.keys.toList()
                var labels: IntArray? = null
                var scores: FloatArray? = null
                
                result.forEachIndexed { idx, onnxValue ->
                    val name = outputNames.getOrNull(idx) ?: "out$idx"
                    val v = onnxValue.value
                    android.util.Log.d("AnomalyOnnx", "Output $name: ${v.javaClass}")

                    when (v) {
                        is Array<*> -> when (val first = v.firstOrNull()) {
                            is LongArray -> labels = first.map { it.toInt() }.toIntArray()
                            is FloatArray -> scores = first
                        }
                        is LongArray -> labels = v.map { it.toInt() }.toIntArray()
                        is FloatArray -> scores = v
                    }
                }
                
                val finalLabels = labels ?: IntArray(batch.size) { 1 }     // норм по умолчанию
                val finalScores = scores ?: FloatArray(batch.size) { 0f }  // скоры по умолчанию
                
                // Создание результатов
                List(batch.size) { i ->
                    val score = finalScores[i]
                    AnomalyResult(
                        index = i,
                        isAnomaly = finalLabels[i] == -1,
                        score = score,
                        severity = getSeverity(score),
                        confidence = calculateConfidence(score)
                    )
                }
            }
        }
    }

    private fun getSeverity(score: Float): String {
        return when {
            score < thresholds.getValue("CRITICAL") -> "CRITICAL"
            score < thresholds.getValue("HIGH") -> "HIGH"
            score < thresholds.getValue("MEDIUM") -> "MEDIUM"
            else -> "LOW"
        }
    }
    
    private fun calculateConfidence(score: Float): Float {
        // Простая оценка уверенности на основе расстояния от 0
        return kotlin.math.min(kotlin.math.abs(score), 2.0f) / 2.0f
    }

    override fun close() {
        session.close()
    }

    data class AnomalyResult(
        val index: Int,
        val isAnomaly: Boolean,
        val score: Float,
        val severity: String,
        val confidence: Float
    )
}
