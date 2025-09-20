# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Gson
-keep class com.google.gson.** { *; }

# Keep для всех наших моделей
-keep class com.example.anomalydetector.** { *; }

# Предотвращение предупреждений
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
