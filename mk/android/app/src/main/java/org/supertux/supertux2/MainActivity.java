package org.supertux.supertux2;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import org.libsdl.app.*;

import java.util.Locale;

public class MainActivity extends SDLActivity {
    private static final String TAG = "SuperTuxMulti";
    public static Locale currLocale;

    // SharedPreferences: remember that user has used TV so we skip the dialog next time
    private static final String PREFS_NAME = "supertux_prefs";
    private static final String PREF_TV_USED = "tv_connected_before";

    private DisplayManager mDisplayManager;
    private boolean mCastConnected = false;

    // Two-state controller launch tracking:
    //   mControllerDirectlyLaunched = true  → startActivity() succeeded (controller is open)
    //   mNotificationPosted         = true  → notification posted, controller may not be open yet
    private boolean mControllerDirectlyLaunched = false;
    private boolean mNotificationPosted = false;

    // Tracked so we can auto-dismiss when TV connects
    private AlertDialog mTVDialog;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Listen for Miracast / WifiDisplay status changes (Samsung Smart View)
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
                    // activeDisplayState: 0=not connected, 1=connecting, 2=connected
                    int state = (int) status.getClass()
                        .getMethod("getActiveDisplayState").invoke(status);
                    Log.i(TAG, "WifiDisplay state: " + state);
                    if (state == 2) {
                        onExternalDisplayConnected();
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "WifiDisplay status reflection failed: " + e.getMessage());
            }
            checkForExternalDisplay();
        }
    };

    private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            Display d = mDisplayManager.getDisplay(displayId);
            Log.i(TAG, "Display added: id=" + displayId + " name=" + (d != null ? d.getName() : "?"));
            if (displayId != Display.DEFAULT_DISPLAY) {
                onExternalDisplayConnected();
            }
        }
        @Override
        public void onDisplayRemoved(int displayId) {
            if (displayId != Display.DEFAULT_DISPLAY) {
                Log.i(TAG, "External display removed: " + displayId);
                mCastConnected = false;
                mControllerDirectlyLaunched = false;
                mNotificationPosted = false;
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.cancel(NOTIF_ID);
                mHandler.removeCallbacks(mDisplayPoller);
                mHandler.postDelayed(mDisplayPoller, 2000);
            }
        }
        @Override
        public void onDisplayChanged(int displayId) {}
    };

    // Polling backup: check every 2s in case DisplayListener missed the event
    private final Runnable mDisplayPoller = new Runnable() {
        @Override
        public void run() {
            if (!mCastConnected) {
                checkForExternalDisplay();
                mHandler.postDelayed(this, 2000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currLocale = Locale.getDefault();

        mDisplayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);

        // Listen for Samsung Smart View / Miracast broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mWifiDisplayReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mWifiDisplayReceiver, filter);
        }

        if (checkForExternalDisplay()) {
            Log.i(TAG, "External display already present at startup");
        } else {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean hadTvBefore = prefs.getBoolean(PREF_TV_USED, false);
            if (hadTvBefore) {
                // User has already paired with TV before — skip dialog, auto-open Smart View
                Log.i(TAG, "TV used before — auto-opening Smart View in 1s");
                mHandler.postDelayed(this::openSmartView, 1000);
            } else {
                // First time — show dialog with direct action button
                mHandler.postDelayed(this::showTVDialog, 1000);
            }
            mHandler.postDelayed(mDisplayPoller, 2000);
        }

        Log.i(TAG, "Display detection started. Displays: " + mDisplayManager.getDisplays().length);
    }

    /**
     * KEY FIX (Bug K): when SuperTux returns to foreground (user dismisses Smart View panel,
     * notification panel, or any other app), retry direct controller launch if:
     *   - TV is connected (mCastConnected)
     *   - Controller was NOT yet directly launched (mControllerDirectlyLaunched == false)
     *
     * Real-world flow:
     *   openSmartView() → user picks TV → SuperTux goes background
     *   display connects → launchController() → startActivity() fails (background) → notif posted
     *   user dismisses panel → SuperTux FOREGROUND → onResume() → direct launch SUCCEEDS
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (mCastConnected && !mControllerDirectlyLaunched) {
            Log.i(TAG, "onResume: TV connected but controller not yet launched — retrying direct launch");
            mHandler.post(this::launchController);
        }
    }

    /**
     * Open Samsung Smart View / Android Cast directly from the app.
     * Tries Samsung-specific intent first, then standard Android fallbacks.
     */
    private void openSmartView() {
        if (isFinishing()) return;
        String[] actions = {
            "com.samsung.android.airview.ACTION_VIEW",  // Samsung Smart View (direct)
            "android.settings.WIFI_DISPLAY_SETTINGS",   // Android Wifi Display settings
            Settings.ACTION_CAST_SETTINGS               // Generic Android cast
        };
        for (String action : actions) {
            try {
                startActivity(new Intent(action));
                Log.i(TAG, "Opened Smart View via: " + action);
                return;
            } catch (Exception ignored) {}
        }
        // Last resort
        try {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            Log.i(TAG, "Opened wireless settings as final fallback");
        } catch (Exception e) {
            Log.w(TAG, "Could not open any Smart View / cast settings: " + e.getMessage());
        }
    }

    private void showTVDialog() {
        if (mCastConnected || isFinishing()) return;
        mTVDialog = new AlertDialog.Builder(this)
            .setTitle("Jouer sur la TV ?")
            .setMessage("Appuie sur \"Ouvrir Smart View\", sélectionne ta TV.\n\nLe contrôleur se lancera automatiquement !")
            .setPositiveButton("Ouvrir Smart View", (d, w) -> openSmartView())
            .setNegativeButton("Plus tard", null)
            .create();
        mTVDialog.show();
    }

    private boolean checkForExternalDisplay() {
        Display[] allDisplays = mDisplayManager.getDisplays();
        for (Display d : allDisplays) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                Log.i(TAG, "External display found: " + d.getName());
                onExternalDisplayConnected();
                return true;
            }
        }
        Display[] presentations = mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (Display d : presentations) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                Log.i(TAG, "Presentation display found: " + d.getName());
                onExternalDisplayConnected();
                return true;
            }
        }
        return false;
    }

    private static final String NOTIF_CHANNEL_ID = "supertux_controller";
    private static final int NOTIF_ID = 1;

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL_ID,
                "SuperTux Contrôleur",
                NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Appuie pour ouvrir le contrôleur quand la TV est connectée");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private void onExternalDisplayConnected() {
        if (mCastConnected) return;
        mCastConnected = true;

        // Remember for future launches — next time we auto-open Smart View without any dialog
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(PREF_TV_USED, true).apply();

        // Auto-dismiss the dialog if still on screen (user pressed "Ouvrir Smart View" → TV connected)
        if (mTVDialog != null && mTVDialog.isShowing()) {
            mTVDialog.dismiss();
            mTVDialog = null;
        }

        Log.i(TAG, "External display connected — attempting controller launch");
        mHandler.removeCallbacks(mDisplayPoller);
        // Post immediately (no delay) so we're still considered foreground
        mHandler.post(this::launchController);
    }

    private void launchController() {
        if (mControllerDirectlyLaunched) return;

        Intent controllerIntent = getPackageManager()
            .getLaunchIntentForPackage("com.sky1241.controller_app");

        if (controllerIntent == null) {
            Log.w(TAG, "Controller app not installed (com.sky1241.controller_app)");
            return;
        }

        controllerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        // --- Attempt 1: direct startActivity() ---
        // Works when SuperTux is in foreground. Fails on Android 10+ from background.
        try {
            startActivity(controllerIntent);
            mControllerDirectlyLaunched = true;
            if (mNotificationPosted) {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.cancel(NOTIF_ID);
                mNotificationPosted = false;
            }
            Log.i(TAG, "Controller app launched directly");
            return;
        } catch (Exception e) {
            Log.w(TAG, "Direct launch failed (" + e.getMessage() + "), falling back to notification");
        }

        // --- Attempt 2: high-priority notification ---
        // Works from background. mControllerDirectlyLaunched stays false → onResume() retries.
        if (mNotificationPosted) {
            Log.i(TAG, "Notification already posted — skipping duplicate");
            return;
        }

        ensureNotificationChannel();

        PendingIntent pi = PendingIntent.getActivity(
            this, 0, controllerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, NOTIF_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_media_play)
               .setContentTitle("SuperTux — TV connectée !")
               .setContentText("Appuie ici pour ouvrir le contrôleur")
               .setContentIntent(pi)
               .setAutoCancel(true)
               .setPriority(Notification.PRIORITY_HIGH);

        NotificationManager nm = getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        nm.notify(NOTIF_ID, builder.build());
        mNotificationPosted = true;
        Log.i(TAG, "Controller notification posted — will retry direct launch on next resume");
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        // Dismiss dialog to prevent window leak
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

    public static String getLocale() { return currLocale.toString(); }
    public static String getCountry() { return currLocale.getCountry(); }
    public static String getLang() { return currLocale.getLanguage(); }

    @Override
    protected String[] getLibraries() { return new String[] {"supertux2"}; }

    @Override
    protected String getMainSharedObject() { return "libsupertux2.so"; }

    @Override
    protected String getMainFunction() { return "SDL_main"; }
}
