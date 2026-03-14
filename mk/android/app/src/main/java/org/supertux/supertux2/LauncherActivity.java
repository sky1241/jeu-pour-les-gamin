package org.supertux.supertux2;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.net.InetAddress;
import java.net.Inet4Address;

/**
 * Entry point when the user taps the SuperTux icon.
 *
 * Responsibilities:
 *   1. Open Samsung Smart View / Android Cast so the user can pick the TV.
 *   2. When the TV display is detected, launch MainActivity (SDL game) on that
 *      display via ActivityOptions.setLaunchDisplayId().
 *   3. Launch the controller app (com.sky1241.controller_app) on the phone screen.
 *   4. Finish itself — phone shows controller, TV shows the game.
 *
 * If no TV is available the user can tap "Plus tard" and the game launches
 * normally on the phone.
 */
public class LauncherActivity extends Activity {

    private static final String TAG = "SuperTuxLauncher";
    private static final String PREFS_NAME = "supertux_prefs";
    private static final String PREF_TV_USED = "tv_connected_before";

    private DisplayManager  mDisplayManager;
    private boolean         mGameLaunched = false;
    private AlertDialog     mTVDialog;
    private final Handler   mHandler = new Handler(Looper.getMainLooper());

    // WebAssembly TV mode
    private WasmHttpServer  mWasmServer;
    private WasmDownloader  mWasmDownloader;
    private SamsungTvRemote mSamsungRemote;

    // -------------------------------------------------------------------------
    // Samsung Smart View / Miracast broadcast
    // -------------------------------------------------------------------------
    private final BroadcastReceiver mWifiDisplayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "WifiDisplay broadcast: " + intent.getAction());
            try {
                Parcelable status;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    status = intent.getParcelableExtra(
                        "android.hardware.display.extra.WIFI_DISPLAY_STATUS",
                        Parcelable.class);
                } else {
                    status = intent.getParcelableExtra(
                        "android.hardware.display.extra.WIFI_DISPLAY_STATUS");
                }
                if (status != null) {
                    int state = (int) status.getClass()
                        .getMethod("getActiveDisplayState").invoke(status);
                    Log.i(TAG, "WifiDisplay state: " + state);
                    if (state == 2) {
                        checkForExternalDisplay();
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "WifiDisplay reflection failed: " + e.getMessage());
            }
            checkForExternalDisplay();
        }
    };

    // -------------------------------------------------------------------------
    // DisplayManager listener
    // -------------------------------------------------------------------------
    private final DisplayManager.DisplayListener mDisplayListener =
        new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Display d = mDisplayManager.getDisplay(displayId);
                Log.i(TAG, "Display added: id=" + displayId
                    + " name=" + (d != null ? d.getName() : "?"));
                if (displayId != Display.DEFAULT_DISPLAY) {
                    launchOnExternalDisplay(displayId);
                }
            }
            @Override
            public void onDisplayRemoved(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) {
                    Log.i(TAG, "External display removed: " + displayId);
                    mGameLaunched = false;
                }
            }
            @Override
            public void onDisplayChanged(int displayId) {}
        };

    // Polling backup every 2 s
    private final Runnable mDisplayPoller = new Runnable() {
        @Override
        public void run() {
            if (!mGameLaunched) {
                checkForExternalDisplay();
                mHandler.postDelayed(this, 2000);
            }
        }
    };

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDisplayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (mDisplayManager == null) {
            // Should never happen, but guard against it
            launchGameLocally();
            return;
        }
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mWifiDisplayReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mWifiDisplayReceiver, filter);
        }

        if (!checkForExternalDisplay()) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (prefs.getBoolean(PREF_TV_USED, false)) {
                // TV already used before — open Smart View automatically after 500 ms
                mHandler.postDelayed(this::openSmartView, 500);
            } else {
                // First time — show the dialog
                mHandler.postDelayed(this::showTVDialog, 800);
            }
            mHandler.postDelayed(mDisplayPoller, 2000);
        }
    }

    /**
     * When the user comes back from the Smart View panel, if the TV connected
     * while we were in the background (Android 10+ blocks startActivity from bg),
     * retry the launch now that we are in the foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!mGameLaunched) {
            checkForExternalDisplay();
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mTVDialog != null && mTVDialog.isShowing()) {
            mTVDialog.dismiss();
            mTVDialog = null;
        }
        if (mDisplayManager != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
        try { unregisterReceiver(mWifiDisplayReceiver); } catch (Exception ignored) {}
        // WasmHttpServer keeps running in background (daemon thread) so the TV
        // can finish loading files after the launcher finishes itself.
        // It will be GC'd when the process ends.
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Display detection
    // -------------------------------------------------------------------------
    private boolean checkForExternalDisplay() {
        if (mDisplayManager == null) return false;
        Display[] all = mDisplayManager.getDisplays();
        for (Display d : all) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                launchOnExternalDisplay(d.getDisplayId());
                return true;
            }
        }
        Display[] pres = mDisplayManager.getDisplays(
            DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (Display d : pres) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                launchOnExternalDisplay(d.getDisplayId());
                return true;
            }
        }
        return false;
    }

    private void launchOnExternalDisplay(int displayId) {
        if (mGameLaunched) return;
        mGameLaunched = true;

        // Remember for next launch
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(PREF_TV_USED, true).apply();

        if (mTVDialog != null && mTVDialog.isShowing()) {
            mTVDialog.dismiss();
            mTVDialog = null;
        }
        mHandler.removeCallbacks(mDisplayPoller);

        Log.i(TAG, "Launching game on display " + displayId);

        // 1 — Launch SuperTux on the TV display
        Intent gameIntent = new Intent(this, MainActivity.class);
        gameIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchDisplayId(displayId);
            startActivity(gameIntent, opts.toBundle());
        } else {
            // Pre-8.0 fallback: just launch game normally
            startActivity(gameIntent);
        }

        // 2 — Launch controller app on the phone (primary display)
        //     Small delay so the game activity has time to register before
        //     the controller tries to connect via WebSocket.
        mHandler.postDelayed(this::launchController, 1500);
    }

    private void launchController() {
        Intent ctrlIntent = getPackageManager()
            .getLaunchIntentForPackage("com.sky1241.controller_app");
        if (ctrlIntent != null) {
            ctrlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(ctrlIntent);
            Log.i(TAG, "Controller app launched on phone");
        } else {
            Log.w(TAG, "Controller app not installed — phone will show nothing");
        }
        // Launcher done — game is on TV, controller (or home) is on phone
        finish();
    }

    // -------------------------------------------------------------------------
    // Smart View / Cast
    // -------------------------------------------------------------------------
    private void openSmartView() {
        if (isFinishing()) return;
        String[] actions = {
            "com.samsung.android.airview.ACTION_VIEW",  // Samsung Smart View
            "android.settings.WIFI_DISPLAY_SETTINGS",   // Android Wi-Fi display
            Settings.ACTION_CAST_SETTINGS               // Generic cast
        };
        for (String action : actions) {
            try {
                startActivity(new Intent(action));
                Log.i(TAG, "Opened Smart View via: " + action);
                return;
            } catch (Exception ignored) {}
        }
        try {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        } catch (Exception ignored) {}
    }

    private void showTVDialog() {
        if (mGameLaunched || isFinishing()) return;
        mTVDialog = new AlertDialog.Builder(this)
            .setTitle("Jouer sur la TV ?")
            .setMessage("Choisis comment afficher le jeu sur la TV :\n\n"
                + "• Smart View (Miracast) — le téléphone projette l'écran\n"
                + "• Via navigateur (Web) — la TV charge le jeu directement\n")
            .setPositiveButton("Ouvrir Smart View", (d, w) -> openSmartView())
            .setNeutralButton("Via navigateur (Web)", (d, w) -> startWasmMode())
            .setNegativeButton("Jouer ici", (d, w) -> launchGameLocally())
            .setCancelable(false)
            .create();
        mTVDialog.show();
    }

    // -------------------------------------------------------------------------
    // WebAssembly TV mode
    // -------------------------------------------------------------------------

    /**
     * "Jouer sur la TV (Web)" flow:
     *   1. Download wasm files from GitHub Pages if not cached (~100 MB, once).
     *   2. Start a local HTTP server on port 8080.
     *   3. Show the URL so the user can open it on the TV browser.
     */
    private void startWasmMode() {
        if (isFinishing()) return;
        mWasmDownloader = new WasmDownloader(this);

        if (mWasmDownloader.isDownloaded()) {
            startHttpServerAndShowUrl();
            return;
        }

        // Show download progress dialog
        ProgressDialog prog = new ProgressDialog(this);
        prog.setTitle("Téléchargement du jeu Web");
        prog.setMessage("Téléchargement en cours…");
        prog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        prog.setMax(100);
        prog.setCancelable(true);
        prog.setOnCancelListener(d -> {
            if (mWasmDownloader != null) mWasmDownloader.cancel();
        });
        prog.show();

        mWasmDownloader.download(new WasmDownloader.ProgressCallback() {
            @Override
            public void onProgress(String filename, int fileIndex, int totalFiles,
                                   long bytesDownloaded, long totalBytes) {
                // Overall progress: (completed files + current file fraction) / total files
                int filesDone = fileIndex; // files before this one are done
                float fraction = totalBytes > 0 ? (float) bytesDownloaded / totalBytes : 0f;
                int overall = (int) ((filesDone + fraction) * 100 / totalFiles);

                mHandler.post(() -> {
                    if (!prog.isShowing()) return;
                    prog.setProgress(overall);
                    // Show MB downloaded for large files
                    String msg = "Fichier " + (fileIndex + 1) + "/" + totalFiles
                        + ": " + filename;
                    if (totalBytes > 1024 * 1024) {
                        msg += "\n" + (bytesDownloaded / 1048576) + " / "
                            + (totalBytes / 1048576) + " Mo";
                    }
                    prog.setMessage(msg);
                });
            }

            @Override
            public void onComplete() {
                mHandler.post(() -> {
                    prog.dismiss();
                    startHttpServerAndShowUrl();
                });
            }

            @Override
            public void onError(String message) {
                mHandler.post(() -> {
                    prog.dismiss();
                    if (!isFinishing()) {
                        new AlertDialog.Builder(LauncherActivity.this)
                            .setTitle("Erreur de téléchargement")
                            .setMessage(message + "\n\nVérifie ta connexion internet et réessaie.")
                            .setPositiveButton("Réessayer", (d, w) -> startWasmMode())
                            .setNegativeButton("Annuler", null)
                            .show();
                    }
                });
            }
        });
    }

    private void startHttpServerAndShowUrl() {
        if (mWasmServer == null || !mWasmServer.isRunning()) {
            mWasmServer = new WasmHttpServer(this);
            try {
                mWasmServer.start();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start HTTP server: " + e.getMessage());
                if (!isFinishing()) {
                    new AlertDialog.Builder(this)
                        .setTitle("Erreur serveur")
                        .setMessage("Impossible de démarrer le serveur HTTP :\n" + e.getMessage())
                        .setPositiveButton("OK", null)
                        .show();
                }
                return;
            }
        }

        String ip  = getLocalIpAddress();
        String url = "http://" + ip + ":" + WasmHttpServer.PORT + "/";

        // Try Samsung TV auto-open first, then fall back to manual URL dialog
        tryAutoOpenSamsungTv(url);
    }

    /**
     * Attempt SSDP discovery + Samsung Remote API to auto-open the browser on the TV.
     * Shows a "Recherche TV..." dialog during discovery, then either:
     *   - success → brief toast + manual URL dialog (in case TV needs interaction)
     *   - failure → manual URL dialog directly
     */
    private void tryAutoOpenSamsungTv(String gameUrl) {
        if (isFinishing()) return;

        android.app.ProgressDialog searching = new android.app.ProgressDialog(this);
        searching.setTitle("Recherche de ta TV...");
        searching.setMessage("Scan du réseau WiFi pour une TV Samsung...");
        searching.setCancelable(true);
        searching.setOnCancelListener(d -> {
            if (mSamsungRemote != null) mSamsungRemote.cancel();
        });
        searching.show();

        mSamsungRemote = new SamsungTvRemote();
        mSamsungRemote.discoverAndOpen(gameUrl, new SamsungTvRemote.Callback() {
            @Override
            public void onSuccess(String tvIp) {
                mHandler.post(() -> {
                    searching.dismiss();
                    if (!isFinishing()) {
                        Toast.makeText(LauncherActivity.this,
                            "TV trouvée (" + tvIp + ") — navigateur ouvert !",
                            Toast.LENGTH_LONG).show();
                        // Also show manual URL in case user needs to retry
                        showUrlDialog(gameUrl);
                    }
                });
            }
            @Override
            public void onFailure(String reason) {
                mHandler.post(() -> {
                    searching.dismiss();
                    Log.w(TAG, "Samsung auto-open failed: " + reason);
                    // No TV found — fall back to manual URL
                    if (!isFinishing()) showUrlDialog(gameUrl);
                });
            }
        });
    }

    /** Show the URL dialog so the user can copy/open the game URL manually. */
    private void showUrlDialog(String url) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("Jeu prêt sur la TV !")
            .setMessage("Ouvre cette adresse dans le navigateur de ta TV :\n\n"
                + url + "\n\n"
                + "(La TV Samsung devrait l'avoir ouvert automatiquement.)")
            .setPositiveButton("Copier l'URL", (d, w) -> {
                ClipboardManager cm = (ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("SuperTux URL", url));
                    Toast.makeText(this, "URL copiée !", Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("Ouvrir ici", (d, w) ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))))
            .setNegativeButton("Fermer", null)
            .show();
    }

    private String getLocalIpAddress() {
        // Prefer WiFi address
        try {
            WifiManager wm = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);
            if (wm != null) {
                int ip4 = wm.getConnectionInfo().getIpAddress();
                if (ip4 != 0) {
                    return String.format("%d.%d.%d.%d",
                        (ip4 & 0xff), (ip4 >> 8 & 0xff),
                        (ip4 >> 16 & 0xff), (ip4 >> 24 & 0xff));
                }
            }
        } catch (Exception ignored) {}

        // Fallback: enumerate network interfaces
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "localhost";
    }

    /** Fallback: no TV, just launch the game on the phone. */
    private void launchGameLocally() {
        if (mGameLaunched) return;
        mGameLaunched = true;
        Intent gameIntent = new Intent(this, MainActivity.class);
        gameIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(gameIntent);
        finish();
    }
}
