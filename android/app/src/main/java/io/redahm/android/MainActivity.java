package io.redahm.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    public static final String EXTRA_GRAPHICS_PROFILE = "io.redahm.android.GRAPHICS_PROFILE";

    private static final String PREFS = "redahm_launcher";
    private static final String KEY_GAME_DIR = "game_dir";
    private static final String KEY_ISO = "iso_path";
    private static final String KEY_DRIVER_DIR = "vulkan_driver_dir";
    private static final String KEY_DRIVER_SO = "vulkan_driver_so";
    private static final String KEY_GRAPHICS_PROFILE = "graphics_profile";

    private static final int GRAPHICS_PROFILE_PERFORMANCE = 0;
    private static final int GRAPHICS_PROFILE_QUALITY = 1;

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
    private int graphicsProfile;

    private TextView gameDirText;
    private TextView isoText;
    private TextView driverText;
    private TextView graphicsProfileText;
    private Button graphicsDownButton;
    private Button graphicsUpButton;

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
        graphicsProfile = Math.max(GRAPHICS_PROFILE_PERFORMANCE, Math.min(GRAPHICS_PROFILE_QUALITY,
                prefs.getInt(KEY_GRAPHICS_PROFILE, GRAPHICS_PROFILE_PERFORMANCE)));

        setContentView(buildLayout());
        refreshSelectionDisplay();
    }

    private View buildLayout() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(20));
        page.setBackground(gradient(0xFF020B10, 0xFF061C27, 0xFF02080E, GradientDrawable.Orientation.TL_BR, 0));

        TextView logo = label("DESTROY ALL\nHUMANS!", 30, 0xFFFFB000, Typeface.BOLD);
        logo.setLineSpacing(0, 0.86f);
        page.addView(logo);
        TextView gameName = label("PATH OF THE FURON", 18, 0xFFA35CFF, Typeface.BOLD);
        gameName.setLetterSpacing(0.08f);
        page.addView(gameName, topMargin(dp(2)));
        TextView version = label("ReDAHM  •  ANDROID LAUNCHER", 11, 0xFF65D8FF, Typeface.BOLD);
        version.setLetterSpacing(0.12f);
        page.addView(version, topMargin(dp(10)));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(18), 0, dp(4));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(sectionLabel("SESSÃO DE JOGO"));
        LinearLayout gamePanel = panel();
        gamePanel.addView(sectionTitle("Dados extraídos"));
        gamePanel.addView(bodyText("Selecione a pasta com default.xex e KronosGame."), topMargin(dp(5)));
        Button pickDir = neonButton("SELECIONAR PASTA", false);
        pickDir.setOnClickListener(v -> openFolderPicker());
        gamePanel.addView(pickDir, topMargin(dp(14)));
        gameDirText = statusText();
        gamePanel.addView(gameDirText, topMargin(dp(10)));
        content.addView(gamePanel, topMargin(dp(8)));

        LinearLayout isoPanel = panel();
        isoPanel.addView(sectionTitle("Imagem ISO"));
        isoPanel.addView(bodyText("Guarde a ISO selecionada como referência; para iniciar, o jogo deve estar extraído."), topMargin(dp(5)));
        Button pickIso = neonButton("SELECIONAR ISO", false);
        pickIso.setOnClickListener(v -> openIsoPicker());
        isoPanel.addView(pickIso, topMargin(dp(14)));
        isoText = statusText();
        isoPanel.addView(isoText, topMargin(dp(10)));
        content.addView(isoPanel, topMargin(dp(12)));

        content.addView(sectionLabel("GRÁFICOS"), topMargin(dp(22)));
        LinearLayout graphicsPanel = panel();
        graphicsPanel.addView(sectionTitle("Qualidade visual"));
        graphicsPanel.addView(bodyText("Ajuste real do desfoque de movimento. Menor qualidade reduz pós-processamento; maior qualidade o preserva."), topMargin(dp(5)));
        LinearLayout graphicsControls = new LinearLayout(this);
        graphicsControls.setGravity(Gravity.CENTER_VERTICAL);
        graphicsControls.setPadding(0, dp(12), 0, 0);
        graphicsDownButton = compactButton("−");
        graphicsDownButton.setOnClickListener(v -> changeGraphicsProfile(-1));
        graphicsControls.addView(graphicsDownButton, squareParams());
        graphicsProfileText = label("", 16, 0xFFEAFBFF, Typeface.BOLD);
        graphicsProfileText.setGravity(Gravity.CENTER);
        graphicsControls.addView(graphicsProfileText, weightedParams());
        graphicsUpButton = compactButton("+");
        graphicsUpButton.setOnClickListener(v -> changeGraphicsProfile(1));
        graphicsControls.addView(graphicsUpButton, squareParams());
        graphicsPanel.addView(graphicsControls);
        content.addView(graphicsPanel, topMargin(dp(8)));

        content.addView(sectionLabel("DRIVER VULKAN"), topMargin(dp(22)));
        LinearLayout driverPanel = panel();
        driverPanel.addView(bodyText("Importe um driver personalizado para GPUs Adreno: pacotes .zip (AdrenoTools) ou bibliotecas .so."));
        Button importDriver = neonButton("IMPORTAR DRIVER", false);
        importDriver.setOnClickListener(v -> openDriverPicker());
        driverPanel.addView(importDriver, topMargin(dp(14)));
        driverText = statusText();
        driverPanel.addView(driverText, topMargin(dp(10)));
        Button chooseDriver = neonButton("TROCAR DRIVER", false);
        chooseDriver.setOnClickListener(v -> openDriverChooser());
        driverPanel.addView(chooseDriver, topMargin(dp(10)));
        content.addView(driverPanel, topMargin(dp(8)));

        Button start = neonButton("INICIAR JOGO", true);
        start.setOnClickListener(v -> startGame());
        content.addView(start, topMargin(dp(22)));
        refreshGraphicsProfileDisplay();
        return page;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable gradient(int startColor, int centerColor, int endColor,
                                      GradientDrawable.Orientation orientation, int radius) {
        GradientDrawable drawable = new GradientDrawable(orientation, new int[]{startColor, centerColor, endColor});
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(15), dp(16), dp(15));
        GradientDrawable background = gradient(0xE60A2633, 0xE6091827, 0xE602101A,
                GradientDrawable.Orientation.LEFT_RIGHT, 10);
        background.setStroke(dp(1), 0xFF168CA8);
        panel.setBackground(background);
        return panel;
    }

    private TextView label(String text, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif-condensed", style));
        return view;
    }

    private TextView sectionLabel(String text) {
        TextView view = label(text, 11, 0xFF45DFFF, Typeface.BOLD);
        view.setLetterSpacing(0.16f);
        return view;
    }

    private TextView sectionTitle(String text) {
        return label(text, 19, 0xFFF4FCFF, Typeface.BOLD);
    }

    private TextView bodyText(String text) {
        TextView view = label(text, 14, 0xFFABCCD8, Typeface.NORMAL);
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private TextView statusText() {
        TextView view = label("", 12, 0xFF66E3FF, Typeface.NORMAL);
        view.setMaxLines(2);
        view.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        return view;
    }

    private Button neonButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTextColor(primary ? 0xFFFFFFFF : 0xFF58E9FF);
        button.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        button.setLetterSpacing(0.08f);
        button.setAllCaps(false);
        GradientDrawable background = gradient(primary ? 0xFF6B25B8 : 0xFF0B3748,
                primary ? 0xFF382393 : 0xFF071925,
                primary ? 0xFF0B4D70 : 0xFF020E16,
                GradientDrawable.Orientation.LEFT_RIGHT, 8);
        background.setStroke(dp(1), primary ? 0xFFB95CFF : 0xFF19DFF2);
        button.setBackground(background);
        button.setMinHeight(dp(46));
        return button;
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        button.setTextColor(0xFFFFFFFF);
        button.setAllCaps(false);
        GradientDrawable background = gradient(0xFF203A52, 0xFF102033, 0xFF071019,
                GradientDrawable.Orientation.TL_BR, 8);
        background.setStroke(dp(1), 0xFF36DDF6);
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = margin;
        return params;
    }

    private LinearLayout.LayoutParams squareParams() {
        return new LinearLayout.LayoutParams(dp(50), dp(46));
    }

    private LinearLayout.LayoutParams weightedParams() {
        return new LinearLayout.LayoutParams(0, dp(46), 1f);
    }

    private void changeGraphicsProfile(int direction) {
        int newProfile = Math.max(GRAPHICS_PROFILE_PERFORMANCE,
                Math.min(GRAPHICS_PROFILE_QUALITY, graphicsProfile + direction));
        if (newProfile == graphicsProfile) {
            return;
        }
        graphicsProfile = newProfile;
        prefs.edit().putInt(KEY_GRAPHICS_PROFILE, graphicsProfile).apply();
        refreshGraphicsProfileDisplay();
    }

    private void refreshGraphicsProfileDisplay() {
        if (graphicsProfileText == null) {
            return;
        }
        String profile;
        String effect;
        if (graphicsProfile == GRAPHICS_PROFILE_QUALITY) {
            profile = "QUALIDADE";
            effect = "desfoque de movimento ligado";
        } else {
            profile = "DESEMPENHO";
            effect = "desfoque de movimento desligado";
        }
        graphicsProfileText.setText(profile + "\n" + effect);
        graphicsProfileText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        graphicsDownButton.setEnabled(graphicsProfile > GRAPHICS_PROFILE_PERFORMANCE);
        graphicsUpButton.setEnabled(graphicsProfile < GRAPHICS_PROFILE_QUALITY);
        graphicsDownButton.setAlpha(graphicsDownButton.isEnabled() ? 1f : 0.35f);
        graphicsUpButton.setAlpha(graphicsUpButton.isEnabled() ? 1f : 0.35f);
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
        // Android does not expose a universal ISO MIME type. Let every document
        // provider list the file, then enforce the .iso extension after selection.
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
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

        Uri selectedUri = data.getData();
        if (requestCode == REQUEST_PICK_ISO) {
            String displayName = getDisplayName(getContentResolver(), selectedUri);
            if (!isIsoFile(displayName)) {
                Toast.makeText(this, "Selecione um arquivo com extensão .iso.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            try {
                getContentResolver().takePersistableUriPermission(selectedUri,
                        data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            } catch (SecurityException e) {
                // Some document providers do not offer persistable grants. The
                // URI is still usable for the active process and its name is saved.
                Log.w(TAG, "Persistable ISO permission unavailable", e);
            }
            isoPath = selectedUri.toString();
            prefs.edit().putString(KEY_ISO, isoPath).apply();
            refreshSelectionDisplay();
            Toast.makeText(this, "ISO selecionada: " + displayName, Toast.LENGTH_SHORT).show();
            return;
        }

        String path = uriToPath(selectedUri);
        if (path == null) {
            Toast.makeText(this, "Não foi possível obter o caminho da pasta selecionada.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_PICK_FOLDER) {
            gameDirPath = path;
            prefs.edit().putString(KEY_GAME_DIR, path).apply();
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
                File out = new File(targetDir, name);
                if (out.exists()) {
                    // Keep the first (root-level) copy; device-variant subfolder
                    // builds may carry the same basename in several folders.
                    continue;
                }
                copyStream(zip, out);
                out.setExecutable(true, false);
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
        for (String preferred : new String[]{"libvulkan_freedreno.so", "libvulkan_turnip.so",
                "libvulkan.so.qualcomm"}) {
            for (File f : files) {
                if (f.getName().equals(preferred)) {
                    return f.getName();
                }
            }
        }
        for (File f : files) {
            if (f.getName().endsWith(".so")) {
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
                ? "Pasta selecionada: " + gameDirPath
                : "Nenhuma pasta selecionada");
        isoText.setText(isoPath != null
                ? "ISO selecionada: " + getIsoDisplayName(isoPath)
                : "Nenhuma ISO selecionada");
        if (driverDir != null && driverSo != null && new File(driverDir).isDirectory()) {
            driverText.setText("Driver: " + driverSo + " (" + driverDir + ")");
        } else {
            driverText.setText("Driver: do sistema (padrão)");
        }
    }

    private static boolean isIsoFile(String displayName) {
        return displayName != null && displayName.toLowerCase().endsWith(".iso");
    }

    private String getIsoDisplayName(String storedUri) {
        try {
            Uri uri = Uri.parse(storedUri);
            String displayName = getDisplayName(getContentResolver(), uri);
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve ISO display name", e);
        }
        return new File(storedUri).getName();
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
            String message = "Selecione a pasta com o conteúdo extraído de "
                    + "'Destroy All Humans! Path of the Furon (USA)' (default.xex + KronosGame).";
            if (isoPath != null) {
                message += "\n\nA ISO foi selecionada, mas o motor requer os dados extraídos "
                        + "em uma pasta antes de iniciar.";
            }
            new AlertDialog.Builder(this)
                    .setTitle("Dados do jogo não encontrados")
                    .setMessage(message)
                    .setPositiveButton("Entendi", null)
                    .show();
            return;
        }

        File userRoot = new File(getExternalFilesDir(null), "user");
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(EXTRA_GAME_DATA_ROOT, gameDataRoot);
        intent.putExtra(EXTRA_USER_DATA_ROOT, userRoot.getAbsolutePath());
        intent.putExtra(EXTRA_GRAPHICS_PROFILE, graphicsProfile);
        if (driverDir != null && driverSo != null && new File(driverDir).isDirectory()) {
            intent.putExtra(EXTRA_VULKAN_DRIVER_DIR, driverDir);
            intent.putExtra(EXTRA_VULKAN_DRIVER_SO, driverSo);
        }
        startActivity(intent);
    }

    private String resolveGameDataRoot() {
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

    private static boolean looksLikeGameData(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File xex = new File(dir, "default.xex");
        File kronos = new File(dir, "KronosGame");
        return xex.isFile() && kronos.isDirectory();
    }
}