package io.redahm.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher for the ReDAHM (Destroy All Humans! Path of the Furon) recompilation
 * on Android.
 *
 * Responsibilities:
 *  - Request "All files access" on Android 11+ so the extracted game data can
 *    be read from shared storage.
 *  - Detect the game data folder (must contain default.xex and KronosGame/).
 *  - Pass game_data_root / user_data_root to the native SDL_main as arguments.
 *  - Hand the rest to SDL3 (native SDL_main in libmain.so).
 */
public class MainActivity extends SDLActivity {
    private static final String TAG = "ReDAHM";
    public static final String APP_NAME = "redahm";

    /** Well-known shared-storage locations checked in order. */
    private static final String[] GAME_DATA_CANDIDATES = {
            "/storage/emulated/0/redahm/game",
            "/storage/emulated/0/Download/redahm/game",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hand the app's storage paths to the native runtime (native library
        // dir, files dirs, cache). Safe to call: libs are already loaded by
        // super.onCreate() -> SDLActivity.loadLibraries(), and the SDL main
        // thread only starts after onCreate returns.
        setupNativePaths();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageExternalStorageIfNeeded();
        }
    }

    /** Implemented in libmain.so (src/android_bridge.cpp). */
    private native void setupNativePaths();

    private void requestManageExternalStorageIfNeeded() {
        if (Environment.isExternalStorageManager()) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Could not open all-files-access settings", e);
        }
    }

    /** Locate a folder that looks like the extracted US game data. */
    private String findGameDataRoot() {
        List<String> candidates = new ArrayList<>();
        for (String candidate : GAME_DATA_CANDIDATES) {
            candidates.add(candidate);
        }
        File externalFiles = getExternalFilesDir(null);
        if (externalFiles != null) {
            candidates.add(new File(externalFiles, "game").getAbsolutePath());
        }
        for (String candidate : candidates) {
            if (looksLikeGameData(new File(candidate))) {
                Log.i(TAG, "Found game data at " + candidate);
                return candidate;
            }
        }
        return null;
    }

    private static boolean looksLikeGameData(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File xex = new File(dir, "default.xex");
        File kronos = new File(dir, "KronosGame");
        return xex.isFile() && kronos.isDirectory();
    }

    @Override
    protected String[] getArguments() {
        String gameDataRoot = findGameDataRoot();
        if (gameDataRoot == null) {
            Log.w(TAG, "No game data found. Copy the extracted 'Destroy All Humans! "
                    + "Path of the Furon (USA)' contents to /storage/emulated/0/redahm/game/");
            return new String[0];
        }
        File userRoot = new File(getExternalFilesDir(null), "user");
        return new String[] {
                "--game_data_root=" + gameDataRoot,
                "--user_data_root=" + userRoot.getAbsolutePath(),
                "--gpu_plugin=xenos",
        };
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL3", "main"};
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }
}