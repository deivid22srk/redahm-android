package io.redahm.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Launcher screen (pure Java, no native libraries) shown before the game
 * starts. Lets the user pick the extracted game folder or an ISO and press
 * "Iniciar Jogo"; only then is the SDL/ReDAHM game activity launched with the
 * chosen game data root.
 */
public class MainActivity extends Activity {
    private static final String TAG = "ReDAHM";
    public static final String APP_NAME = "redahm";

    public static final String EXTRA_GAME_DATA_ROOT = "io.redahm.android.GAME_DATA_ROOT";
    public static final String EXTRA_USER_DATA_ROOT = "io.redahm.android.USER_DATA_ROOT";
    public static final String EXTRA_VULKAN_DRIVER_DIR = "io.redahm.android.VULKAN_DRIVER_DIR";
    public static final String EXTRA_VULKAN_DRIVER_SO = "io.redahm.android.VULKAN_DRIVER_SO";

    private static final String PREFS = "redahm_launcher";
    private static final String KEY_GAME_DIR = "game_dir";
    private static final String KEY_ISO = "iso_path";
    private static final String KEY_DRIVER_DIR = "vulkan_driver_dir";
    private static final String KEY_DRIVER_SO = "vulkan_driver_so";

    private static final int REQUEST_PICK_FOLDER = 1;
    private static final int REQUEST_PICK_ISO = 2;
    private static final int REQUEST_PICK_DRIVER = 3;

    private static final String[] GAME_DATA_CANDIDATES = {
            "/storage/emulated/0/redahm/game",
            "/storage/emulated/0/Download/redahm/game",
    };

    private SharedPreferences prefs;
    private String gameDirPath;
    private String isoPath;
    private String driverDir;
    private String driverSo;

    private TextView gameDirText;
    private TextView isoText;
    private TextView driverText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageExternalStorageIfNeeded();
        }

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        gameDirPath = prefs.getString(KEY_GAME_DIR, null);
        isoPath = prefs.getString(KEY_ISO, null);
        driverDir = prefs.getString(KEY_DRIVER_DIR, null);
        driverSo = prefs.getString(KEY_DRIVER_SO, null);

        setContentView(buildLayout());
        refreshSelectionDisplay();
    }

    private View buildLayout() {
        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * dp);
        int margin = (int) (12 * dp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, (int) (24 * dp), pad, pad);

        TextView title = new TextView(this);
        title.setText("ReDAHM");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Destroy All Humans! Path of the Furon (recompilação)\n"
                + "Selecione a pasta com o jogo extraído (default.xex + KronosGame) ou um ISO.");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setPadding(0, (int) (6 * dp), 0, (int) (16 * dp));
        root.addView(subtitle);

        Button pickDir = new Button(this);
        pickDir.setText("Selecionar Pasta do Jogo");
        pickDir.setOnClickListener(v -> openFolderPicker());
        root.addView(pickDir, layoutParams(margin));

        gameDirText = new TextView(this);
        gameDirText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        gameDirText.setPadding(0, (int) (4 * dp), 0, (int) (10 * dp));
        root.addView(gameDirText);

        Button pickIso = new Button(this);
        pickIso.setText("Selecionar ISO");
        pickIso.setOnClickListener(v -> openIsoPicker());
        root.addView(pickIso, layoutParams(margin));

        isoText = new TextView(this);
        isoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        isoText.setPadding(0, (int) (4 * dp), 0, (int) (10 * dp));
        root.addView(isoText);

        TextView hint = new TextView(this);
        hint.setText("A pasta do jogo deve conter default.xex e a subpasta KronosGame.\n"
                + "Para um ISO, extraia o conteúdo para uma pasta (com o mesmo nome do ISO) "
                + "e selecione a pasta.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setPadding(0, 0, 0, (int) (14 * dp));
        root.addView(hint);

        Button start = new Button(this);
        start.setText("Iniciar Jogo");
        start.setOnClickListener(v -> startGame());
        root.addView(start, layoutParams(margin));

        TextView driverTitle = new TextView(this);
        driverTitle.setText("Driver Vulkan");
        driverTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        driverTitle.setGravity(Gravity.CENTER);
        driverTitle.setPadding(0, (int) (20 * dp), 0, (int) (4 * dp));
        root.addView(driverTitle);

        TextView driverHint = new TextView(this);
        driverHint.setText("Opcional: importe um driver Vulkan personalizado (ex.: Mesa Turnip) "
                + "para GPUs Adreno. Aceita um .zip de driver (pacote AdrenoTools) ou um .so."
                + "\nUse apenas drivers de fontes confiáveis — um driver incompatível pode travar.");
        driverHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        driverHint.setPadding(0, 0, 0, (int) (8 * dp));
        root.addView(driverHint);

        Button importDriver = new Button(this);
        importDriver.setText("Importar Driver Vulkan (.zip / .so)");
        importDriver.setOnClickListener(v -> openDriverPicker());
        root.addView(importDriver, layoutParams(margin));

        driverText = new TextView(this);
        driverText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        driverText.setPadding(0, (int) (4 * dp), 0, (int) (10 * dp));
        root.addView(driverText);

        Button chooseDriver = new Button(this);
        chooseDriver.setText("Trocar Driver");
        chooseDriver.setOnClickListener(v -> openDriverChooser());
        root.addView(chooseDriver, layoutParams(margin));

        scroll.addView(root);
        return scroll;
    }

    private LinearLayout.LayoutParams layoutParams(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = margin;
        lp.bottomMargin = margin;
        return lp;
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            startActivityForResult(intent, REQUEST_PICK_FOLDER);
        } catch (Exception e) {
            Log.w(TAG, "Folder picker unavailable", e);
            Toast.makeText(this, "Seletor de pastas indisponível", Toast.LENGTH_LONG).show();
        }
    }

    private void openIsoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_ISO);
        } catch (Exception e) {
            Log.w(TAG, "ISO picker unavailable", e);
            Toast.makeText(this, "Seletor de arquivos indisponível", Toast.LENGTH_LONG).show();
        }
    }

    private void openDriverPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_PICK_DRIVER);
        } catch (Exception e) {
            Log.w(TAG, "Driver picker unavailable", e);
            Toast.makeText(this, "Seletor de arquivos indisponível", Toast.LENGTH_LONG).show();
        }
    }

    /** Lets the user pick between the system driver and each imported driver. */
    private void openDriverChooser() {
        List<File> drivers = listImportedDrivers();
        final String[] labels = new String[drivers.size() + 1];
        final String[] dirs = new String[drivers.size() + 1];
        final String[] sos = new String[drivers.size() + 1];
        labels[0] = "Driver do sistema (padrão)";
        for (int i = 0; i < drivers.size(); i++) {
            labels[i + 1] = drivers.get(i).getName();
            dirs[i + 1] = drivers.get(i).getAbsolutePath();
            sos[i + 1] = findDriverSo(drivers.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle("Selecionar driver Vulkan")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        setDriver(null, null);
                    } else if (dirs[which] != null && sos[which] != null) {
                        setDriver(dirs[which], sos[which]);
                    } else {
                        Toast.makeText(this, "Driver inválido (nenhum .so encontrado).",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void setDriver(String dir, String so) {
        driverDir = dir;
        driverSo = so;
        prefs.edit()
                .putString(KEY_DRIVER_DIR, dir)
                .putString(KEY_DRIVER_SO, so)
                .apply();
        refreshSelectionDisplay();
        Toast.makeText(this, so != null ? "Driver: " + so : "Driver do sistema", Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_DRIVER) {
            // Driver import uses the content stream directly (never needs a
            // real filesystem path).
            if (importDriver(data.getData())) {
                Toast.makeText(this, "Driver importado e selecionado.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Não foi possível importar o driver.",
                        Toast.LENGTH_LONG).show();
            }
            refreshSelectionDisplay();
            return;
        }

        String path = uriToPath(data.getData());
        if (path == null) {
            Toast.makeText(this, "Não foi possível obter o caminho do armazenamento.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_PICK_FOLDER) {
            gameDirPath = path;
            isoPath = null;
            prefs.edit().putString(KEY_GAME_DIR, path).remove(KEY_ISO).apply();
        } else if (requestCode == REQUEST_PICK_ISO) {
            isoPath = path;
            gameDirPath = null;
            prefs.edit().putString(KEY_ISO, path).remove(KEY_GAME_DIR).apply();
        }
        refreshSelectionDisplay();
    }

    /**
     * Copies an imported driver into the app's internal storage: a plain .so is
     * copied as-is, a .zip (AdrenoTools-style package) is extracted. The main
     * driver .so is auto-detected and the driver becomes the selected one.
     * Returns false on failure.
     */
    private boolean importDriver(Uri uri) {
        if (uri == null) {
            return false;
        }
        try {
            ContentResolver resolver = getContentResolver();
            String displayName = getDisplayName(resolver, uri);
            if (displayName == null || displayName.isEmpty()) {
                displayName = "driver";
            }
            File targetDir = new File(driversRoot(), sanitizeName(displayName));
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                Log.w(TAG, "Could not create driver dir: " + targetDir);
                return false;
            }

            String mainSo = null;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) {
                    return false;
                }
                if (displayName.toLowerCase().endsWith(".zip")) {
                    extractDriverZip(in, targetDir);
                    mainSo = findDriverSo(targetDir);
                } else {
                    String soName = sanitizeName(displayName);
                    if (!soName.toLowerCase().endsWith(".so")) {
                        soName = soName + ".so";
                    }
                    if (!copyStream(in, new File(targetDir, soName))) {
                        return false;
                    }
                    mainSo = soName;
                }
            }
            if (mainSo == null) {
                Log.w(TAG, "Driver import: no .so found in " + targetDir);
                return false;
            }
            setDriver(targetDir.getAbsolutePath(), mainSo);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Driver import failed", e);
            return false;
        }
    }

    private void extractDriverZip(InputStream in, File targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = new File(entry.getName()).getName();
                if (!name.endsWith(".so")) {
                    continue;
                }
                copyStream(zip, new File(targetDir, name));
                zip.closeEntry();
            }
        }
    }

    private static boolean copyStream(InputStream in, File out) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                bos.write(buffer, 0, read);
            }
        }
        return out.length() > 0;
    }

    private String getDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read display name", e);
        }
        return null;
    }

    private File driversRoot() {
        return new File(getFilesDir(), "vulkan_drivers");
    }

    /** Imported driver folders (each holding at least one .so). */
    private List<File> listImportedDrivers() {
        List<File> result = new ArrayList<>();
        File root = driversRoot();
        File[] children = root.listFiles();
        if (children == null) {
            return result;
        }
        for (File child : children) {
            if (child.isDirectory() && findDriverSo(child) != null) {
                result.add(child);
            }
        }
        return result;
    }

    /** Picks the main driver .so inside a driver folder. */
    private static String findDriverSo(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return null;
        }
        for (String preferred : new String[]{"libvulkan_turnip.so", "libvulkan.so.qualcomm"}) {
            for (File f : files) {
                if (f.getName().equals(preferred)) {
                    return f.getName();
                }
            }
        }
        for (File f : files) {
            if (f.getName().endsWith(".so") && !f.getName().contains("freedreno")) {
                return f.getName();
            }
        }
        return null;
    }

    private static String sanitizeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_").replaceFirst("\\.zip$", "");
    }

    private void refreshSelectionDisplay() {
        gameDirText.setText(gameDirPath != null
                ? "Pasta: " + gameDirPath
                : "Pasta: (nenhuma selecionada)");
        isoText.setText(isoPath != null
                ? "ISO: " + isoPath
                : "ISO: (nenhum selecionado)");
        if (driverDir != null && driverSo != null && new File(driverDir).isDirectory()) {
            driverText.setText("Driver: " + driverSo + " (" + driverDir + ")");
        } else {
            driverText.setText("Driver: do sistema (padrão)");
        }
    }

    /** Best-effort mapping of a SAF content URI back to a real filesystem path. */
    private static String uriToPath(Uri uri) {
        if (uri == null) {
            return null;
        }
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            List<String> segments = uri.getPathSegments();
            String key = null;
            if (segments.size() >= 2 && "tree".equals(segments.get(0))) {
                key = segments.get(1);
            } else if (segments.size() >= 2 && "document".equals(segments.get(0))) {
                key = segments.get(1);
            }
            if (key != null) {
                String decoded = Uri.decode(key);
                int colon = decoded.indexOf(':');
                if (colon >= 0) {
                    String volume = decoded.substring(0, colon);
                    String rel = decoded.substring(colon + 1);
                    if ("primary".equals(volume)) {
                        return "/storage/emulated/0/" + rel;
                    }
                    return "/storage/" + volume + "/" + rel;
                }
            }
        }
        return null;
    }

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

    private void startGame() {
        String gameDataRoot = resolveGameDataRoot();
        if (gameDataRoot == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Jogo não encontrado")
                    .setMessage("Selecione a pasta com o conteúdo extraído de "
                            + "'Destroy All Humans! Path of the Furon (USA)' (default.xex + "
                            + "KronosGame), ou um ISO extraído para uma pasta.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        File userRoot = new File(getExternalFilesDir(null), "user");
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(EXTRA_GAME_DATA_ROOT, gameDataRoot);
        intent.putExtra(EXTRA_USER_DATA_ROOT, userRoot.getAbsolutePath());
        if (driverDir != null && driverSo != null && new File(driverDir).isDirectory()) {
            intent.putExtra(EXTRA_VULKAN_DRIVER_DIR, driverDir);
            intent.putExtra(EXTRA_VULKAN_DRIVER_SO, driverSo);
        }
        startActivity(intent);
    }

    private String resolveGameDataRoot() {
        if (isoPath != null) {
            File derived = isoDerivedFolder(isoPath);
            if (derived != null && looksLikeGameData(derived)) {
                return derived.getAbsolutePath();
            }
        }
        if (gameDirPath != null && looksLikeGameData(new File(gameDirPath))) {
            return gameDirPath;
        }
        return autoDetectGameData();
    }

    private String autoDetectGameData() {
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
                return candidate;
            }
        }
        return null;
    }

    private static File isoDerivedFolder(String iso) {
        int dot = iso.lastIndexOf('.');
        if (dot > 0) {
            return new File(iso.substring(0, dot));
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
}