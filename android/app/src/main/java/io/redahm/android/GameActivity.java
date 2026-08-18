package io.redahm.android;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLControllerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The game activity. Started by {@link MainActivity} with the game data root
 * as an intent extra; drives the native SDL_main in libmain.so.
 *
 * Runs in its own process (android:process=":game") so each launch starts with
 * a fresh SDL native state.
 */
public class GameActivity extends SDLActivity implements InputManager.InputDeviceListener {
    private static final String TAG = "ReDAHM";
    private static final int VIRTUAL_JOYSTICK_ID = -31337;

    private VirtualGamepadView virtualGamepad;
    private boolean virtualJoystickRegistered;
    private InputManager inputManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        // Safe to call here: super.onCreate() already ran
        // SDLActivity.loadLibraries() (which loads libSDL3.so and libmain.so),
        // and the SDL main thread only starts after onCreate returns.
        setupNativePaths();
        installDefaultConfig();

        Intent intent = getIntent();
        String driverDir = intent == null ? null : intent.getStringExtra(MainActivity.EXTRA_VULKAN_DRIVER_DIR);
        String driverSo = intent == null ? null : intent.getStringExtra(MainActivity.EXTRA_VULKAN_DRIVER_SO);
        if (driverDir != null && driverSo != null) {
            setVulkanDriver(driverDir, driverSo);
            Log.i(TAG, "Using custom Vulkan driver: " + driverSo + " (" + driverDir + ")");
        }

        installVirtualGamepad();
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, null);
        updateVirtualGamepadVisibility();
    }

    /**
     * Copies the bundled {@code assets/redahm.toml} performance profile to
     * {@code <user_data_root>/redahm.toml} on first launch (or whenever the
     * bundled profile is newer). The native runtime loads that file before
     * starting the game, so the file must be in place before the SDL main
     * thread runs (i.e. before onCreate returns).
     */
    private void installDefaultConfig() {
        final int bundledVersion = 2;
        try {
            File userRoot = new File(getExternalFilesDir(null), "user");
            File configFile = new File(userRoot, "redahm.toml");
            if (configFile.exists() && installedConfigVersion(configFile) >= bundledVersion) {
                return;
            }
            if (!userRoot.exists() && !userRoot.mkdirs()) {
                Log.w(TAG, "Unable to create user data dir: " + userRoot);
                return;
            }
            AssetManager assets = getAssets();
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assets.open("redahm.toml");
                out = new FileOutputStream(configFile);
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                Log.i(TAG, "Installed default performance config v" + bundledVersion + ": " + configFile);
            } finally {
                if (in != null) in.close();
                if (out != null) out.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to install default performance config", e);
        }
    }

    private static int installedConfigVersion(File configFile) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile("config_version\\s*=\\s*(\\d+)").matcher(content);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void installVirtualGamepad() {
        View content = SDLActivity.getContentView();
        if (!(content instanceof ViewGroup)) {
            Log.w(TAG, "SDL content layout unavailable; virtual gamepad not installed");
            return;
        }
        virtualGamepad = new VirtualGamepadView(this);
        virtualGamepad.setListener(new VirtualGamepadView.Listener() {
            @Override public void onButton(String id, boolean pressed) {
                SDLControllerManager.dispatchVirtualButton(VIRTUAL_JOYSTICK_ID, keyCodeFor(id), pressed);
            }

            @Override public void onAxis(String id, float x, float y) {
                if ("LS".equals(id)) {
                    SDLControllerManager.dispatchVirtualAxis(VIRTUAL_JOYSTICK_ID, 0, x);
                    SDLControllerManager.dispatchVirtualAxis(VIRTUAL_JOYSTICK_ID, 1, y);
                } else if ("RS".equals(id)) {
                    SDLControllerManager.dispatchVirtualAxis(VIRTUAL_JOYSTICK_ID, 2, x);
                    SDLControllerManager.dispatchVirtualAxis(VIRTUAL_JOYSTICK_ID, 3, y);
                }
            }
        });
        ((ViewGroup) content).addView(virtualGamepad, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static int keyCodeFor(String id) {
        switch (id) {
            case "A": return KeyEvent.KEYCODE_BUTTON_A;
            case "B": return KeyEvent.KEYCODE_BUTTON_B;
            case "X": return KeyEvent.KEYCODE_BUTTON_X;
            case "Y": return KeyEvent.KEYCODE_BUTTON_Y;
            case "LB": return KeyEvent.KEYCODE_BUTTON_L1;
            case "RB": return KeyEvent.KEYCODE_BUTTON_R1;
            case "LT": return KeyEvent.KEYCODE_BUTTON_L2;
            case "RT": return KeyEvent.KEYCODE_BUTTON_R2;
            case "L3": return KeyEvent.KEYCODE_BUTTON_THUMBL;
            case "R3": return KeyEvent.KEYCODE_BUTTON_THUMBR;
            case "VIEW": return KeyEvent.KEYCODE_BUTTON_SELECT;
            case "SHARE": return KeyEvent.KEYCODE_BUTTON_SELECT;
            case "MENU": return KeyEvent.KEYCODE_BUTTON_START;
            case "GUIDE": return KeyEvent.KEYCODE_BUTTON_MODE;
            case "DPAD_U": return KeyEvent.KEYCODE_DPAD_UP;
            case "DPAD_D": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "DPAD_L": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "DPAD_R": return KeyEvent.KEYCODE_DPAD_RIGHT;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    private boolean hasPhysicalGamepad() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual()) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                    || (sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) {
                return true;
            }
        }
        return false;
    }

    private void updateVirtualGamepadVisibility() {
        if (virtualGamepad == null) return;
        boolean show = !hasPhysicalGamepad();
        virtualGamepad.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && !virtualJoystickRegistered) {
            SDLControllerManager.addVirtualJoystick(VIRTUAL_JOYSTICK_ID, "ReDAHM Touch Gamepad");
            virtualJoystickRegistered = true;
        } else if (!show && virtualJoystickRegistered) {
            SDLControllerManager.removeVirtualJoystick(VIRTUAL_JOYSTICK_ID);
            virtualJoystickRegistered = false;
        }
        Log.i(TAG, show ? "Virtual gamepad shown" : "Physical gamepad detected; virtual gamepad hidden");
    }

    @Override public void onInputDeviceAdded(int deviceId) { updateVirtualGamepadVisibility(); }
    @Override public void onInputDeviceRemoved(int deviceId) { updateVirtualGamepadVisibility(); }
    @Override public void onInputDeviceChanged(int deviceId) { updateVirtualGamepadVisibility(); }

    @Override protected void onDestroy() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        if (virtualJoystickRegistered) SDLControllerManager.removeVirtualJoystick(VIRTUAL_JOYSTICK_ID);
        virtualJoystickRegistered = false;
        super.onDestroy();
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

    /** Implemented in libmain.so (src/android_bridge.cpp). */
    private native void setVulkanDriver(String driverDir, String driverName);
}