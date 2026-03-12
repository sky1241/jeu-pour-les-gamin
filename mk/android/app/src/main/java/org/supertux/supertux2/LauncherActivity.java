package org.supertux.supertux2;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

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

    private DisplayManager mDisplayManager;
    private boolean mGameLaunched = false;
    private AlertDialog mTVDialog;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

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
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Display detection
    // -------------------------------------------------------------------------
    private boolean checkForExternalDisplay() {
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
            .setMessage("Appuie sur \"Ouvrir Smart View\" et sélectionne ta TV.\n\n"
                + "Le jeu s'affichera sur la TV,\nle contrôleur sur ce téléphone !")
            .setPositiveButton("Ouvrir Smart View", (d, w) -> openSmartView())
            .setNegativeButton("Jouer sur le téléphone", (d, w) -> launchGameLocally())
            .setCancelable(false)
            .create();
        mTVDialog.show();
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
