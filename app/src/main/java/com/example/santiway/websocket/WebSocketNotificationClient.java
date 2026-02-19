package com.example.santiway.websocket;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.santiway.upload_data.ApiConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebSocketNotificationClient {
    private static final String TAG = "WebSocketClient";
    private static final int RECONNECT_DELAY = 5000; // 5 seconds
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private Context context;
    private String serverUrl;
    private String apiKey;
    private WebSocket webSocket;
    private OkHttpClient client;
    private Handler handler;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private boolean shouldReconnect = true;

    // Broadcast actions
    public static final String ACTION_NOTIFICATION_RECEIVED = "com.example.santiway.NOTIFICATION_RECEIVED";
    public static final String ACTION_CONNECTION_STATUS = "com.example.santiway.WS_CONNECTION_STATUS";
    public static final String ACTION_APK_CHUNK_RECEIVED = "com.example.santiway.APK_CHUNK_RECEIVED";
    public static final String ACTION_APK_COMPLETE = "com.example.santiway.APK_COMPLETE";

    // Extra keys
    public static final String EXTRA_NOTIFICATION_DATA = "notification_data";
    public static final String EXTRA_CONNECTION_STATUS = "connection_status";
    public static final String EXTRA_CHUNK_DATA = "chunk_data";
    public static final String EXTRA_CHUNK_INDEX = "chunk_index";
    public static final String EXTRA_CHUNK_COUNT = "chunk_count";
    public static final String EXTRA_BUILD_ID = "build_id";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_APK_PATH = "apk_path";

    public WebSocketNotificationClient(Context context, String serverUrl, String apiKey) {
        this.context = context.getApplicationContext();
        this.apiKey = apiKey;
        this.handler = new Handler(Looper.getMainLooper());

        // Убеждаемся, что URL не содержит api_key в query параметрах
        // Оставляем только базовый URL WebSocket эндпоинта
        if (serverUrl.contains("?")) {
            serverUrl = serverUrl.substring(0, serverUrl.indexOf("?"));
        }

        // Убеждаемся, что URL заканчивается правильно
        if (!serverUrl.endsWith("/")) {
            serverUrl = serverUrl + "/";
        }

        // Формируем правильный путь для WebSocket
        if (!serverUrl.contains("/ws/")) {
            serverUrl = serverUrl + "ws/notifications/";
        }

        this.serverUrl = serverUrl;
        Log.d(TAG, "WebSocket URL: " + this.serverUrl);
        Log.d(TAG, "API Key will be sent in headers");
    }

    public void connect() {
        shouldReconnect = true;
        reconnectAttempts = 0;
        createWebSocket();
    }

    private void createWebSocket() {
        try {
            Log.d(TAG, "Attempting to connect to: " + serverUrl);
            Log.d(TAG, "API Key length: " + (apiKey != null ? apiKey.length() : 0));

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true);

            client = builder.build();

            // Важно: добавляем все необходимые заголовки для WebSocket upgrade
            Request request = new Request.Builder()
                    .url(serverUrl)
                    .addHeader("User-Agent", "Android-App")
                    .addHeader("X-API-Key", apiKey)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Connection", "Upgrade")  // Важно для WebSocket
                    .addHeader("Upgrade", "websocket")    // Важно для WebSocket
                    .addHeader("Sec-WebSocket-Version", "13")  // Версия протокола
                    .addHeader("Sec-WebSocket-Key", createWebSocketKey())  // Случайный ключ
                    .build();

            Log.d(TAG, "Request headers: " + request.headers());

            client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    Log.i(TAG, "✅ WebSocket connected successfully");
                    Log.d(TAG, "Response headers: " + response.headers());
                    Log.d(TAG, "Response code: " + response.code());

                    WebSocketNotificationClient.this.webSocket = webSocket;
                    isConnected = true;
                    reconnectAttempts = 0;
                    broadcastConnectionStatus(true);
                    sendPing();
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Log.e(TAG, "❌ WebSocket failure", t);

                    if (response != null) {
                        Log.e(TAG, "Response code: " + response.code());
                        Log.e(TAG, "Response message: " + response.message());
                        try {
                            if (response.body() != null) {
                                Log.e(TAG, "Response body: " + response.body().string());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading response body", e);
                        }
                    }

                    isConnected = false;
                    broadcastConnectionStatus(false);
                    scheduleReconnect();
                }

                // ... остальные методы
            });

        } catch (Exception e) {
            Log.e(TAG, "Error creating WebSocket", e);
            scheduleReconnect();
        }
    }

    private String createWebSocketKey() {
        // Генерируем случайный 16-байтный ключ и кодируем в base64
        byte[] nonce = new byte[16];
        new java.security.SecureRandom().nextBytes(nonce);
        return android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP);
    }

    private void handleMessage(String message) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(message);
            String type = json.optString("type", "");

            if ("system.connected".equals(type)) {
                Log.i(TAG, "Server acknowledged connection: " + json.toString());
                return;
            }

            if ("notification".equals(type) || json.has("notif_type")) {
                // Это уведомление
                processNotification(json);
            } else if ("pong".equals(type)) {
                Log.d(TAG, "Received pong");
            } else {
                Log.d(TAG, "Unknown message type: " + type);
            }

        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
        }
    }

    private void handleBinaryMessage(byte[] data) {
        // Обработка бинарных сообщений (чанки APK)
        try {
            // Пытаемся распарсить как JSON (если это метаданные)
            String jsonString = new String(data, "UTF-8");
            org.json.JSONObject json = new org.json.JSONObject(jsonString);

            if (json.has("type") && "apk_chunk".equals(json.getString("type"))) {
                processApkChunk(json);
            } else {
                processNotification(json);
            }
        } catch (Exception e) {
            // Это чистые бинарные данные (чанк APK без метаданных)
            Log.w(TAG, "Received raw binary data, size: " + data.length);
            // Здесь можно обработать как часть собираемого APK
        }
    }

    private void processNotification(JSONObject json) {
        try {
            String notifType = json.optString("notif_type", "INFO");
            String title = json.optString("title", "Уведомление");
            String text = json.optString("text", "");
            String recordedAt = json.optString("recorded_at", "");

            // Извлекаем координаты если есть
            JSONObject coords = json.optJSONObject("coords");

            // Извлекаем метаданные
            JSONObject meta = json.optJSONObject("meta");
            if (meta == null) {
                JSONObject payload = json.optJSONObject("payload");
                if (payload != null) {
                    meta = payload.optJSONObject("meta");
                }
            }

            // Проверяем наличие бинарных данных (APK чанки)
            JSONArray binaryContents = json.optJSONArray("binary_contents_b64");
            if (binaryContents == null) {
                binaryContents = json.optJSONArray("binary_contents");
            }

            JSONArray binaryTypes = json.optJSONArray("binary_types");

            if (binaryContents != null && binaryContents.length() > 0) {
                // Обрабатываем бинарные данные (APK)
                handleBinaryContent(binaryContents, binaryTypes, meta, recordedAt);
            }

            // Создаем Intent для отправки в Activity
            Intent intent = new Intent(ACTION_NOTIFICATION_RECEIVED);
            intent.putExtra("notif_type", notifType);
            intent.putExtra("title", title);
            intent.putExtra("text", text);
            intent.putExtra("recorded_at", recordedAt);

            if (coords != null) {
                intent.putExtra("latitude", coords.optDouble("latitude", 0));
                intent.putExtra("longitude", coords.optDouble("longitude", 0));
            }

            if (meta != null) {
                intent.putExtra("meta", meta.toString());
            }

            // Отправляем локально
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

            Log.i(TAG, String.format("Notification: [%s] %s - %s", notifType, title, text));

        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }

    private void handleBinaryContent(JSONArray binaryContents, JSONArray binaryTypes, JSONObject meta, String recordedAt) {
        try {
            // Проверяем наличие информации о чанках
            int chunkIndex = meta != null ? meta.optInt("chunk_index", -1) : -1;
            int chunkCount = meta != null ? meta.optInt("chunk_count", -1) : -1;
            String buildId = meta != null ? meta.optString("build_id", null) : null;
            String filename = meta != null ? meta.optString("filename", null) : null;

            // Определяем тип файла
            String fileExt = ".bin";
            if (binaryTypes != null && binaryTypes.length() > 0) {
                String type = binaryTypes.optString(0, "").toLowerCase();
                if (type.contains("apk") || type.contains("android.package-archive")) {
                    fileExt = ".apk";
                } else if (type.contains("jpeg") || type.contains("jpg")) {
                    fileExt = ".jpg";
                } else if (type.contains("png")) {
                    fileExt = ".png";
                }
            }

            // Декодируем каждый чанк из base64
            for (int i = 0; i < binaryContents.length(); i++) {
                String base64Data = binaryContents.optString(i);
                if (base64Data != null && !base64Data.isEmpty()) {
                    byte[] chunkData = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);

                    // Если это чанк APK
                    if (buildId != null && chunkIndex >= 0) {
                        Intent intent = new Intent(ACTION_APK_CHUNK_RECEIVED);
                        intent.putExtra(EXTRA_BUILD_ID, buildId);
                        intent.putExtra(EXTRA_CHUNK_INDEX, chunkIndex);
                        intent.putExtra(EXTRA_CHUNK_COUNT, chunkCount);
                        intent.putExtra(EXTRA_FILENAME, filename != null ? filename : buildId + ".apk");
                        intent.putExtra(EXTRA_CHUNK_DATA, chunkData);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

                        Log.d(TAG, String.format("APK chunk %d/%d received for %s",
                                chunkIndex + 1, chunkCount, buildId));
                    } else {
                        // Это одиночный файл (не чанк)
                        saveSingleFile(chunkData, filename, fileExt, recordedAt);
                    }
                }
            }

            // Проверяем сигнал о завершении
            if (meta != null && meta.optBoolean("apk_chunk_complete", false)) {
                Log.d(TAG, "APK transfer complete for build: " + buildId);
                Intent intent = new Intent(ACTION_APK_COMPLETE);
                intent.putExtra(EXTRA_BUILD_ID, buildId);
                intent.putExtra(EXTRA_FILENAME, filename);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling binary content", e);
        }
    }

    private void saveSingleFile(byte[] data, String filename, String extension, String recordedAt) {
        try {
            if (filename == null || filename.isEmpty()) {
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS")
                        .format(new java.util.Date());
                filename = "notification_" + timestamp + extension;
            }

            // Создаем директорию для файлов
            java.io.File filesDir = new java.io.File(context.getExternalFilesDir(null), "notifications");
            if (!filesDir.exists()) {
                filesDir.mkdirs();
            }

            java.io.File outputFile = new java.io.File(filesDir, filename);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile);
            fos.write(data);
            fos.close();

            Log.i(TAG, "File saved: " + outputFile.getAbsolutePath());

            // Если это APK, копируем в отдельную директорию
            if (extension.equals(".apk")) {
                java.io.File apkDir = new java.io.File(context.getExternalFilesDir(null), "apks/complete");
                if (!apkDir.exists()) {
                    apkDir.mkdirs();
                }
                java.io.File apkFile = new java.io.File(apkDir, filename);
                java.nio.file.Files.copy(outputFile.toPath(), apkFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.i(TAG, "APK copied to: " + apkFile.getAbsolutePath());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error saving file", e);
        }
    }

    private void processApkChunk(org.json.JSONObject json) {
        try {
            String buildId = json.getString("build_id");
            int chunkIndex = json.getInt("chunk_index");
            int chunkCount = json.getInt("chunk_count");
            String filename = json.optString("filename", buildId + ".apk");
            String dataBase64 = json.getString("data");

            byte[] chunkData = android.util.Base64.decode(dataBase64, android.util.Base64.DEFAULT);

            // Создаем Intent для передачи чанка
            Intent intent = new Intent(ACTION_APK_CHUNK_RECEIVED);
            intent.putExtra(EXTRA_BUILD_ID, buildId);
            intent.putExtra(EXTRA_CHUNK_INDEX, chunkIndex);
            intent.putExtra(EXTRA_CHUNK_COUNT, chunkCount);
            intent.putExtra(EXTRA_FILENAME, filename);
            intent.putExtra(EXTRA_CHUNK_DATA, chunkData);

            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

            Log.d(TAG, String.format("APK chunk %d/%d received for %s",
                    chunkIndex + 1, chunkCount, buildId));

        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error processing APK chunk", e);
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;

        reconnectAttempts++;
        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnection attempts reached");
            return;
        }

        int delay = RECONNECT_DELAY * reconnectAttempts;
        handler.postDelayed(() -> {
            if (shouldReconnect && !isConnected()) {
                Log.i(TAG, "Attempting to reconnect (attempt " + reconnectAttempts + ")");
                createWebSocket();
            }
        }, delay);
    }

    public void sendPing() {
        if (!isConnected || webSocket == null) return;

        try {
            org.json.JSONObject ping = new org.json.JSONObject();
            ping.put("type", "ping");
            ping.put("ts", System.currentTimeMillis());
            webSocket.send(ping.toString());

            // Schedule next ping
            handler.postDelayed(this::sendPing, 20000); // 20 seconds
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error creating ping", e);
        }
    }

    public void sendMessage(String message) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "Cannot send message: not connected");
            return;
        }
        webSocket.send(message);
    }

    public void disconnect() {
        shouldReconnect = false;
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnected");
            webSocket = null;
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
        isConnected = false;
        broadcastConnectionStatus(false);
    }

    public boolean isConnected() {
        return isConnected;
    }

    private void broadcastConnectionStatus(boolean connected) {
        Intent intent = new Intent(ACTION_CONNECTION_STATUS);
        intent.putExtra(EXTRA_CONNECTION_STATUS, connected);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}