package org.supertux.supertux2;

import android.os.Bundle;

import org.libsdl.app.*;

import java.util.Locale;

/**
 * SDL game activity — runs SuperTux.
 *
 * Launched by LauncherActivity, either on the TV external display
 * (via ActivityOptions.setLaunchDisplayId) or directly on the phone
 * if no TV is available.
 *
 * Smart View detection and controller launching are handled in
 * LauncherActivity so that this activity stays focused on the game.
 */
public class MainActivity extends SDLActivity {

    // Initialized statically so JNI calls are safe even before onCreate
    public static Locale currLocale = Locale.getDefault();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currLocale = Locale.getDefault();
    }

    // Called from C++ via JNI
    public static String getLocale()  { return currLocale.toString(); }
    public static String getCountry() { return currLocale.getCountry(); }
    public static String getLang()    { return currLocale.getLanguage(); }

    @Override
    protected String[] getLibraries() { return new String[] {"supertux2"}; }

    @Override
    protected String getMainSharedObject() { return "libsupertux2.so"; }

    @Override
    protected String getMainFunction() { return "SDL_main"; }
}
