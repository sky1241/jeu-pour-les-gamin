package org.supertux.supertux2;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.Display;

import org.libsdl.app.*;

import java.util.Locale;

public class MainActivity extends SDLActivity {
    private static final String TAG = "SuperTuxMulti";
    public static Locale currLocale;

    private DisplayManager mDisplayManager;
    private boolean mCastConnected = false;
    private boolean mControllerLaunched = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Listen for Miracast / WifiDisplay status changes (Samsung Smart View)
    private final BroadcastReceiver mWifiDisplayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "WifiDisplay broadcast: " + intent.getAction());
            // Try to read WifiDisplayStatus via reflection (hidden API)
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
            // Fallback: check DisplayManager
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
                mControllerLaunched = false; // Allow re-launch if TV reconnects
                // Restart polling so we detect reconnection
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
        // Register as not exported (Android 13+ requirement for dynamic receivers)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mWifiDisplayReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mWifiDisplayReceiver, filter);
        }

        // Check if already connected
        if (checkForExternalDisplay()) {
            Log.i(TAG, "External display already present at startup");
        } else {
            // Show instructions dialog after 1s
            mHandler.postDelayed(this::showTVDialog, 1000);
            // Start polling as backup
            mHandler.postDelayed(mDisplayPoller, 2000);
        }

        Log.i(TAG, "Display detection started. Displays: " + mDisplayManager.getDisplays().length);
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
        // Also check presentation displays specifically
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

    private void onExternalDisplayConnected() {
        if (mCastConnected) return;
        mCastConnected = true;
        Log.i(TAG, "External display connected — launching controller in 2s");
        mHandler.removeCallbacks(mDisplayPoller);
        mHandler.postDelayed(this::launchController, 2000);
    }

    private void showTVDialog() {
        if (mCastConnected || isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("Jouer sur la TV ?")
            .setMessage("Active Smart View depuis le panneau de notifications, sélectionne ta TV.\n\nLe contrôleur se lancera automatiquement dès que la TV est connectée.")
            .setPositiveButton("OK", null)
            .show();
    }

    private void launchController() {
        if (mControllerLaunched) return;
        mControllerLaunched = true;

        try {
            Intent controllerIntent = getPackageManager()
                .getLaunchIntentForPackage("com.sky1241.controller_app");

            if (controllerIntent != null) {
                controllerIntent.putExtra("auto_connect_host", "127.0.0.1");
                controllerIntent.putExtra("auto_connect_port", 9876);
                controllerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Log.i(TAG, "Launching controller app...");
                startActivity(controllerIntent);
            } else {
                Log.w(TAG, "Controller app not installed (com.sky1241.controller_app)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch controller: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
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
