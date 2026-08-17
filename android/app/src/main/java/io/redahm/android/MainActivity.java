package io.redahm.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    private static final String PREFS = "redahm_launcher";
    private static final String KEY_GAME_DIR = "game_dir";
    private static final String KEY_ISO = "iso_path";

    private static final int REQUEST_PICK_FOLDER = 1;
    private static final int REQUEST_PICK_ISO = 2;

    private static final String[] GAME_DATA_CANDIDATES = {
            "/storage/emulated/0/redahm/game",
            "/storage/emulated/0/Download/redahm/game",
    };

    private SharedPreferences prefs;
    private String gameDirPath;
    private String isoPath;

    private TextView gameDirText;
    private TextView isoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageExternalStorageIfNeeded();
        }

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        gameDirPath = prefs.getString(KEY_GAME_DIR, null);
        isoPath = prefs.getString(KEY_ISO, null);

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
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

    private void refreshSelectionDisplay() {
        gameDirText.setText(gameDirPath != null
                ? "Pasta: " + gameDirPath
                : "Pasta: (nenhuma selecionada)");
        isoText.setText(isoPath != null
                ? "ISO: " + isoPath
                : "ISO: (nenhum selecionado)");
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