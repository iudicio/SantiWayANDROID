package com.example.santiway.websocket;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ApkAssembler {
    private static final String TAG = "ApkAssembler";
    private static final String CHUNK_FILE_TEMPLATE = "chunk_%05d.part";

    private Context context;
    private File workDir;
    private File outDir;

    private Map<String, BuildInfo> builds = new ConcurrentHashMap<>();

    private static class BuildInfo {
        String buildId;
        String filename;
        int chunkCount = -1;
        Set<Integer> receivedChunks = new HashSet<>();
        File workDir;

        BuildInfo(String buildId, String filename, File workDir) {
            this.buildId = buildId;
            this.filename = filename;
            this.workDir = workDir;
        }
    }

    public ApkAssembler(Context context) {
        this.context = context;
        this.workDir = new File(context.getFilesDir(), "apk_chunks");
        this.outDir = new File(context.getFilesDir(), "apk_complete");

        if (!workDir.exists()) workDir.mkdirs();
        if (!outDir.exists()) outDir.mkdirs();
    }

    public void addChunk(String buildId, int chunkIndex, int chunkCount, String filename, byte[] data) {
        BuildInfo info = builds.get(buildId);
        if (info == null) {
            File buildWorkDir = new File(workDir, buildId);
            buildWorkDir.mkdirs();
            info = new BuildInfo(buildId, filename, buildWorkDir);
            builds.put(buildId, info);
        }

        if (chunkCount > 0) {
            info.chunkCount = chunkCount;
        }

        // Сохраняем чанк
        File chunkFile = new File(info.workDir, String.format(CHUNK_FILE_TEMPLATE, chunkIndex));
        try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
            fos.write(data);
            info.receivedChunks.add(chunkIndex);
            Log.d(TAG, "Saved chunk " + chunkIndex + " for " + buildId);
        } catch (IOException e) {
            Log.e(TAG, "Error saving chunk", e);
        }

        // Проверяем, собраны ли все чанки
        if (info.chunkCount > 0 && info.receivedChunks.size() >= info.chunkCount) {
            boolean allChunksPresent = true;
            for (int i = 0; i < info.chunkCount; i++) {
                if (!info.receivedChunks.contains(i)) {
                    allChunksPresent = false;
                    break;
                }
            }

            if (allChunksPresent) {
                assembleApk(info);
            }
        }
    }

    private void assembleApk(BuildInfo info) {
        File finalFile = new File(outDir, info.filename);

        try (FileOutputStream fos = new FileOutputStream(finalFile)) {
            for (int i = 0; i < info.chunkCount; i++) {
                File chunkFile = new File(info.workDir, String.format(CHUNK_FILE_TEMPLATE, i));
                if (!chunkFile.exists()) {
                    Log.e(TAG, "Missing chunk " + i + " for " + info.buildId);
                    return;
                }

                try (FileInputStream fis = new FileInputStream(chunkFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }

                // Удаляем чанк после записи
                chunkFile.delete();
            }

            Log.i(TAG, "APK assembled: " + finalFile.getAbsolutePath());

            // Удаляем рабочую директорию
            info.workDir.delete();

            // Удаляем из активных сборок
            builds.remove(info.buildId);

            // Отправляем broadcast о завершении
            android.content.Intent intent = new android.content.Intent(
                    WebSocketNotificationClient.ACTION_APK_COMPLETE);
            intent.putExtra(WebSocketNotificationClient.EXTRA_BUILD_ID, info.buildId);
            intent.putExtra(WebSocketNotificationClient.EXTRA_APK_PATH,
                    finalFile.getAbsolutePath());
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                    .getInstance(context).sendBroadcast(intent);

        } catch (IOException e) {
            Log.e(TAG, "Error assembling APK", e);
        }
    }

    public File getLastApk(String buildId) {
        File completeDir = outDir;
        File[] files = completeDir.listFiles((dir, name) -> name.endsWith(".apk"));
        if (files != null && files.length > 0) {
            // Возвращаем самый последний
            File latest = files[0];
            for (File f : files) {
                if (f.lastModified() > latest.lastModified()) {
                    latest = f;
                }
            }
            return latest;
        }
        return null;
    }
}
