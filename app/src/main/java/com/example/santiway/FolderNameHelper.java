package com.example.santiway;

import android.content.Context;

/** Keeps database folder identifiers stable while localizing system folder names in the UI. */
public final class FolderNameHelper {
    public static final String MAIN_FOLDER_INTERNAL = "Основная";

    private FolderNameHelper() {}

    public static boolean isMainFolder(String folderName) {
        return MAIN_FOLDER_INTERNAL.equals(folderName);
    }

    public static String getDisplayName(Context context, String folderName) {
        if (isMainFolder(folderName)) {
            return context.getString(R.string.main_folder_name);
        }
        return folderName;
    }
}
