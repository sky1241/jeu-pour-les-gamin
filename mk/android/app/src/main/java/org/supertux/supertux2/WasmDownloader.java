package org.supertux.supertux2;

import android.content.Context;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads WebAssembly game files from GitHub Pages to internal storage.
 * Files are cached so subsequent launches skip the download.
 *
 * Downloads:
 *   index.html       → wasm/supertux2.html
 *   supertux2.js     → wasm/supertux2.js
 *   supertux2.wasm   → wasm/supertux2.wasm
 *   supertux2.data   → wasm/supertux2.data  (can be 100+ MB)
 */
public class WasmDownloader {

    private static final String TAG = "WasmDownloader";

    // GitHub Pages URL for the wasm build
    private static final String BASE_URL =
        "https://sky1241.github.io/jeu-pour-les-gamin/";

    // Remote filename → local filename inside wasm/ dir
    private static final String[][] FILES = {
        { "index.html",     "supertux2.html" },
        { "supertux2.js",   "supertux2.js"   },
        { "supertux2.wasm", "supertux2.wasm" },
        { "supertux2.data", "supertux2.data" },
    };

    public interface ProgressCallback {
        /** Called periodically during each file download (on a background thread). */
        void onProgress(String filename, int fileIndex, int totalFiles,
                        long bytesDownloaded, long totalBytes);
        /** Called when all files are downloaded successfully. */
        void onComplete();
        /** Called on any error; partial files are deleted. */
        void onError(String message);
    }

    private final File mWasmDir;
    private volatile boolean mCancelled = false;

    public WasmDownloader(Context context) {
        mWasmDir = new File(context.getFilesDir(), "wasm");
    }

    /** Returns true if all expected files exist locally. */
    public boolean isDownloaded() {
        for (String[] pair : FILES) {
            if (!new File(mWasmDir, pair[1]).exists()) return false;
        }
        return true;
    }

    /** Deletes the cached wasm directory (force re-download). */
    public void clearCache() {
        deleteDir(mWasmDir);
    }

    /** Start downloading in a background thread. Callbacks may arrive on any thread. */
    public void download(ProgressCallback callback) {
        mCancelled = false;
        new Thread(() -> doDownload(callback), "WasmDownloader").start();
    }

    public void cancel() {
        mCancelled = true;
    }

    // -------------------------------------------------------------------------

    private void doDownload(ProgressCallback callback) {
        if (!mWasmDir.exists() && !mWasmDir.mkdirs()) {
            callback.onError("Impossible de créer le dossier de cache.");
            return;
        }

        for (int i = 0; i < FILES.length; i++) {
            if (mCancelled) return;

            String remoteName = FILES[i][0];
            String localName  = FILES[i][1];
            File   dest       = new File(mWasmDir, localName);

            // Skip already downloaded file
            if (dest.exists() && dest.length() > 0) {
                callback.onProgress(localName, i, FILES.length, dest.length(), dest.length());
                continue;
            }

            String urlStr = BASE_URL + remoteName;
            Log.i(TAG, "Downloading: " + urlStr);

            File   tmp = new File(mWasmDir, localName + ".tmp");
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(60_000);
                conn.connect();

                int status = conn.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    conn.disconnect();
                    tmp.delete();
                    callback.onError("Erreur HTTP " + status + " pour " + remoteName);
                    return;
                }

                long total = conn.getContentLengthLong();

                try (InputStream  in  = conn.getInputStream();
                     OutputStream fos = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    long downloaded = 0;
                    int  n;
                    while ((n = in.read(buf)) != -1) {
                        if (mCancelled) {
                            tmp.delete();
                            return;
                        }
                        fos.write(buf, 0, n);
                        downloaded += n;
                        callback.onProgress(localName, i, FILES.length, downloaded, total);
                    }
                }
                conn.disconnect();

                // Atomic rename
                if (!tmp.renameTo(dest)) {
                    // Fallback: copy then delete
                    copyFile(tmp, dest);
                    tmp.delete();
                }
                Log.i(TAG, "Saved: " + localName + " (" + dest.length() + " bytes)");

            } catch (IOException e) {
                tmp.delete();
                Log.e(TAG, "Failed: " + remoteName + " — " + e.getMessage());
                callback.onError("Erreur lors du téléchargement de " + remoteName
                    + ":\n" + e.getMessage());
                return;
            }
        }

        Log.i(TAG, "All wasm files downloaded.");
        callback.onComplete();
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream  in  = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        dir.delete();
    }
}
