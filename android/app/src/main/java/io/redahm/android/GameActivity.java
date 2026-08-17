package io.redahm.android;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The game activity. Started by {@link MainActivity} with the game data root
 * as an intent extra; drives the native SDL_main in libmain.so.
 *
 * Runs in its own process (android:process=":game") so each launch starts with
 * a fresh SDL native state.
 */
public class GameActivity extends SDLActivity {
    private static final String TAG = "ReDAHM";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Safe to call here: super.onCreate() already ran
        // SDLActivity.loadLibraries() (which loads libSDL3.so and libmain.so),
        // and the SDL main thread only starts after onCreate returns.
        setupNativePaths();
    }

    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        List<String> args = new ArrayList<>();

        String gameDataRoot = intent == null ? null : intent.getStringExtra(MainActivity.EXTRA_GAME_DATA_ROOT);
        if (gameDataRoot != null && !gameDataRoot.isEmpty()) {
            args.add("--game_data_root=" + gameDataRoot);
            Log.i(TAG, "Game data root: " + gameDataRoot);
        }

        String userDataRoot = intent == null ? null : intent.getStringExtra(MainActivity.EXTRA_USER_DATA_ROOT);
        if (userDataRoot == null || userDataRoot.isEmpty()) {
            File userRoot = new File(getExternalFilesDir(null), "user");
            userDataRoot = userRoot.getAbsolutePath();
        }
        args.add("--user_data_root=" + userDataRoot);

        args.add("--gpu_plugin=xenos");
        return args.toArray(new String[0]);
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL3", "main"};
    }

    /** Implemented in libmain.so (src/android_bridge.cpp). */
    private native void setupNativePaths();
}