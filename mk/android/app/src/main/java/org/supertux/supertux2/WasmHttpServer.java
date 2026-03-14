package org.supertux.supertux2;

import android.content.Context;
import android.util.Log;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal HTTP server that serves WebAssembly game files from internal storage.
 * Started when the user taps "Jouer sur la TV (Web)".
 * The TV browser opens http://PHONE_IP:8080/ to run SuperTux natively.
 */
public class WasmHttpServer {

    private static final String TAG = "WasmHttpServer";
    public static final int PORT = 8080;

    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    static {
        MIME_TYPES.put("html", "text/html; charset=utf-8");
        MIME_TYPES.put("js",   "application/javascript");
        MIME_TYPES.put("wasm", "application/wasm");
        MIME_TYPES.put("data", "application/octet-stream");
    }

    private ServerSocket   mServerSocket;
    private Thread         mServerThread;
    private ExecutorService mPool;
    private final File     mWasmDir;
    private volatile boolean mRunning = false;

    public WasmHttpServer(Context context) {
        mWasmDir = new File(context.getFilesDir(), "wasm");
    }

    public void start() throws IOException {
        mServerSocket = new ServerSocket(PORT);
        mRunning      = true;
        mPool         = Executors.newCachedThreadPool();
        mServerThread = new Thread(this::acceptLoop, "WasmHttpServer");
        mServerThread.setDaemon(true);
        mServerThread.start();
        Log.i(TAG, "HTTP server started on port " + PORT);
    }

    public void stop() {
        mRunning = false;
        try { if (mServerSocket != null) mServerSocket.close(); } catch (IOException ignored) {}
        if (mPool != null) mPool.shutdown();
        Log.i(TAG, "HTTP server stopped");
    }

    public boolean isRunning() { return mRunning; }

    // -------------------------------------------------------------------------

    private void acceptLoop() {
        while (mRunning) {
            try {
                Socket client = mServerSocket.accept();
                mPool.execute(() -> handleClient(client));
            } catch (IOException e) {
                if (mRunning) Log.w(TAG, "Accept error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(10_000);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream()));
            OutputStream out = client.getOutputStream();

            // Parse request line: GET /path HTTP/1.1
            String requestLine = reader.readLine();
            if (requestLine == null) { client.close(); return; }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) { client.close(); return; }
            String path = parts[1];

            // Consume headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {}

            // Resolve path → file
            if (path.equals("/") || path.equals("/index.html")) path = "/supertux2.html";
            // Strip query string
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);

            File file = new File(mWasmDir, path.substring(1));
            String canon    = file.getCanonicalPath();
            String dirCanon = mWasmDir.getCanonicalPath() + File.separator;
            if (!file.exists() || !canon.startsWith(dirCanon)) {
                send404(out);
                client.close();
                return;
            }

            String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "";
            String mime = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

            // Required for SharedArrayBuffer + wasm threads
            String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + mime + "\r\n"
                + "Content-Length: " + file.length() + "\r\n"
                + "Cross-Origin-Opener-Policy: same-origin\r\n"
                + "Cross-Origin-Embedder-Policy: require-corp\r\n"
                + "Cross-Origin-Resource-Policy: cross-origin\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Connection: close\r\n"
                + "\r\n";
            out.write(headers.getBytes("UTF-8"));

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            out.flush();
        } catch (IOException e) {
            Log.w(TAG, "Client I/O error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void send404(OutputStream out) throws IOException {
        byte[] body = "404 Not Found".getBytes("UTF-8");
        String headers = "HTTP/1.1 404 Not Found\r\n"
            + "Content-Type: text/plain\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Connection: close\r\n\r\n";
        out.write(headers.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }
}
