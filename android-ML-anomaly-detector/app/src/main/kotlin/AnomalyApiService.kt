package com.example.anomalydetector

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnomalyApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMedia = "application/json".toMediaType()

    // POST {serverUrl}/detect 
    suspend fun detectAnomalies(serverUrl: String, req: DetectionRequest): DetectionResponse =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(req).toRequestBody(jsonMedia)
            val http = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/detect")
                .post(body)
                .header("Accept", "application/json")
                .build()

            client.newCall(http).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    throw Exception("HTTP ${resp.code}: ${resp.message} | $text")
                }
                gson.fromJson(text, DetectionResponse::class.java)
            }
        }

    // POST {serverUrl}/train 
    suspend fun trainModel(serverUrl: String, req: TrainingRequest): TrainingResponse =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(req).toRequestBody(jsonMedia)
            val http = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/train")
                .post(body)
                .header("Accept", "application/json")
                .build()

            client.newCall(http).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    throw Exception("HTTP ${resp.code}: ${resp.message} | $text")
                }
                gson.fromJson(text, TrainingResponse::class.java)
            }
        }
}

// DTO под FastAPI 

data class DetectionRequest(
    val data: List<List<Double>>,
    val threshold: Double? = null,
    @SerializedName("return_details") val return_details: Boolean = true
)

data class DetectionResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("total_samples") val total_samples: Int,
    @SerializedName("anomalies_detected") val anomalies_detected: Int,
    @SerializedName("anomaly_rate") val anomaly_rate: Double,
    @SerializedName("processing_time") val processing_time: Double,
    val details: List<AnomalyDetail>?
)

data class AnomalyDetail(
    val index: Int,
    @SerializedName("is_anomaly") val is_anomaly: Boolean,
    @SerializedName("anomaly_score") val anomaly_score: Double,
    @SerializedName("anomaly_probability") val anomaly_probability: Double,
    val severity: String,
    val confidence: Double,
    val timestamp: String
)

data class TrainingRequest(
    val  data: List<List<Double>>,
    @SerializedName("feature_names") val feature_names: List<String>? = null,
    @SerializedName("validation_split") val validation_split: Double = 0.2,
    val contamination: Double = 0.1,
    @SerializedName("n_estimators") val n_estimators: Int = 100
)

data class TrainingResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("training_samples") val training_samples: Int,
    @SerializedName("training_time") val training_time: Double,
    @SerializedName("model_info") val model_info: Map<String, Any>
)

data class ModelInfo(
    @SerializedName("is_fitted") val is_fitted: Boolean,
    @SerializedName("feature_names") val feature_names: List<String>?,
    @SerializedName("n_features") val n_features: Int?,
    @SerializedName("total_predictions") val total_predictions: Int,
    @SerializedName("total_anomalies") val total_anomalies: Int,
    @SerializedName("anomaly_rate") val anomaly_rate: Double
)
