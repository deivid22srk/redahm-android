package io.redahm.android;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.appbar.MaterialToolbar;

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
        int spacing16 = dp(16);
        int spacing24 = dp(24);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(MaterialColors.getColor(page,
                com.google.android.material.R.attr.colorSurface));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("ReDAHM");
        toolbar.setSubtitle("Launcher do jogo");
        toolbar.setTitleCentered(false);
        page.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(spacing16, spacing24, spacing16, spacing24);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MaterialTextView title = heading("Prepare sua sessão");
        content.addView(title);

        MaterialTextView subtitle = bodyText(
                "Selecione os dados extraídos do seu jogo ou escolha um arquivo ISO. "
                        + "Os dados extraídos precisam conter default.xex e a pasta KronosGame.");
        subtitle.setPadding(0, dp(6), 0, spacing16);
        content.addView(subtitle);

        MaterialCardView gameCard = sectionCard();
        LinearLayout gameContent = cardContent(gameCard);
        gameContent.addView(sectionTitle("Dados do jogo"));
        gameContent.addView(bodyText("Escolha uma pasta já extraída, pronta para iniciar."));
        MaterialButton pickDir = outlinedButton("Selecionar pasta do jogo");
        pickDir.setOnClickListener(v -> openFolderPicker());
        gameContent.addView(pickDir, topMargin(dp(16)));
        gameDirText = statusText();
        gameContent.addView(gameDirText, topMargin(dp(12)));
        content.addView(gameCard, topMargin(dp(8)));

        MaterialCardView isoCard = sectionCard();
        LinearLayout isoContent = cardContent(isoCard);
        isoContent.addView(sectionTitle("Imagem ISO"));
        isoContent.addView(bodyText("O seletor exibirá arquivos .iso. A ISO escolhida é salva para "
                + "referência; o jogo ainda precisa estar extraído em uma pasta para ser iniciado."));
        MaterialButton pickIso = outlinedButton("Selecionar arquivo ISO");
        pickIso.setOnClickListener(v -> openIsoPicker());
        isoContent.addView(pickIso, topMargin(dp(16)));
        isoText = statusText();
        isoContent.addView(isoText, topMargin(dp(12)));
        content.addView(isoCard, topMargin(dp(16)));

        MaterialButton start = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonStyle);
        start.setText("Iniciar jogo");
        start.setTextAllCaps(false);
        start.setOnClickListener(v -> startGame());
        content.addView(start, topMargin(dp(24)));

        MaterialDivider divider = new MaterialDivider(this);
        content.addView(divider, topMargin(dp(28)));

        MaterialTextView driverTitle = heading("Driver Vulkan");
        driverTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        content.addView(driverTitle, topMargin(dp(24)));
        content.addView(bodyText("Opcional: importe um driver Vulkan personalizado para GPUs Adreno. "
                + "São aceitos pacotes .zip (AdrenoTools) e bibliotecas .so. Use somente fontes confiáveis."));

        MaterialCardView driverCard = sectionCard();
        LinearLayout driverContent = cardContent(driverCard);
        MaterialButton importDriver = outlinedButton("Importar driver Vulkan");
        importDriver.setOnClickListener(v -> openDriverPicker());
        driverContent.addView(importDriver);
        driverText = statusText();
        driverContent.addView(driverText, topMargin(dp(12)));
        MaterialButton chooseDriver = textButton("Trocar driver");
        chooseDriver.setOnClickListener(v -> openDriverChooser());
        driverContent.addView(chooseDriver, topMargin(dp(8)));
        content.addView(driverCard, topMargin(dp(12)));

        return page;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private MaterialCardView sectionCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorSurfaceContainerLow));
        card.setRadius(dp(20));
        return card;
    }

    private LinearLayout cardContent(MaterialCardView card) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.addView(content);
        return content;
    }

    private MaterialTextView heading(String text) {
        MaterialTextView view = new MaterialTextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(MaterialColors.getColor(view,
                com.google.android.material.R.attr.colorOnSurface));
        return view;
    }

    private MaterialTextView sectionTitle(String text) {
        MaterialTextView view = heading(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        return view;
    }

    private MaterialTextView bodyText(String text) {
        MaterialTextView view = new MaterialTextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setLineSpacing(dp(3), 1f);
        view.setTextColor(MaterialColors.getColor(view,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        return view;
    }

    private MaterialTextView statusText() {
        MaterialTextView view = new MaterialTextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setMaxLines(2);
        view.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        view.setTextColor(MaterialColors.getColor(view,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        return view;
    }

    private MaterialButton outlinedButton(String text) {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setTextAllCaps(false);
        return button;
    }

    private MaterialButton textButton(String text) {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonTextStyle);
        button.setText(text);
        button.setTextAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = margin;
        return params;
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
        new MaterialAlertDialogBuilder(this)
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
            new MaterialAlertDialogBuilder(this)
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