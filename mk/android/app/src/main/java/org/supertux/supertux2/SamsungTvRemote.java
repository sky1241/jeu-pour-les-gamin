package org.supertux.supertux2;

import android.util.Base64;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Discovers Samsung Smart TVs on the local network via SSDP and opens a
 * URL in Samsung Internet using the Samsung TV Remote WebSocket API (port 8001).
 *
 * Supported: Samsung Smart TVs with Tizen OS (2016+).
 * First connection: TV shows a pairing dialog — user must accept once.
 * Subsequent connections: accepted automatically via stored token.
 */
public class SamsungTvRemote {

    private static final String TAG = "SamsungTvRemote";

    // Samsung TV Remote API port (plain WS, no TLS)
    private static final int TV_REMOTE_PORT = 8001;

    // SSDP multicast address
    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int    SSDP_PORT = 1900;

    public interface Callback {
        /** Called when a Samsung TV was found and the browser URL was sent. */
        void onSuccess(String tvIp);
        /** Called when no TV was found or an error occurred. */
        void onFailure(String reason);
    }

    private final AtomicBoolean mCancelled = new AtomicBoolean(false);

    public void cancel() { mCancelled.set(true); }

    /**
     * Discover the TV and open the URL, all in a background thread.
     * Callback is delivered on whichever thread this runs on (background).
     */
    public void discoverAndOpen(String gameUrl, Callback callback) {
        new Thread(() -> {
            String tvIp = discoverViaSsdp();
            if (tvIp == null) {
                callback.onFailure("Aucune TV Samsung trouvée sur le réseau WiFi.");
                return;
            }
            Log.i(TAG, "Samsung TV found at " + tvIp);
            boolean ok = openUrl(tvIp, gameUrl);
            if (ok) callback.onSuccess(tvIp);
            else     callback.onFailure("TV trouvée mais impossible d'ouvrir le navigateur.");
        }, "SamsungTvRemote").start();
    }

    // -------------------------------------------------------------------------
    // SSDP Discovery
    // -------------------------------------------------------------------------

    /** Send M-SEARCH and listen for Samsung TV response. Returns IP or null. */
    private String discoverViaSsdp() {
        String query =
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 3\r\n" +
            "ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n\r\n";

        // Use a plain DatagramSocket on an ephemeral port (0).
        // MulticastSocket bound to 1900 would conflict with system SSDP listener
        // and requires joinGroup(); for M-SEARCH we just send UDP and wait for
        // unicast responses on the same socket — DatagramSocket is correct here.
        try (DatagramSocket sock = new DatagramSocket(0)) {
            sock.setSoTimeout(4000);
            sock.setBroadcast(true);

            byte[] data = query.getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            DatagramPacket pkt = new DatagramPacket(data, data.length, group, SSDP_PORT);
            sock.send(pkt);
            Log.i(TAG, "SSDP M-SEARCH sent from port " + sock.getLocalPort());

            // Listen for response
            byte[] buf = new byte[2048];
            while (!mCancelled.get()) {
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                try {
                    sock.receive(resp);
                } catch (SocketTimeoutException e) {
                    break; // no response within 4 s
                }
                String body = new String(buf, 0, resp.getLength(), StandardCharsets.UTF_8);
                Log.d(TAG, "SSDP response from " + resp.getAddress().getHostAddress()
                    + ":\n" + body.substring(0, Math.min(body.length(), 200)));

                if (body.toUpperCase().contains("SAMSUNG") ||
                    body.toUpperCase().contains("REMOTECONTROLRECEIVER")) {
                    return resp.getAddress().getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "SSDP error: " + e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Samsung Remote API (WebSocket on port 8001)
    // -------------------------------------------------------------------------

    /**
     * Connect to TV WebSocket API and send a command to open Samsung Internet
     * at the given URL.
     */
    private boolean openUrl(String tvIp, String gameUrl) {
        try {
            // App name shown in the TV pairing dialog (must be Base64-encoded)
            String appName = Base64.encodeToString(
                "SuperTux".getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            String wsPath = "/api/v2/channels/samsung.remote.control?name=" + appName;

            // Minimal WebSocket handshake + text frame send
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress(tvIp, TV_REMOTE_PORT), 5000);
                sock.setSoTimeout(8000);

                OutputStream out = sock.getOutputStream();
                InputStream  in  = sock.getInputStream();

                // --- HTTP Upgrade ---
                String key = generateWsKey();
                String handshake =
                    "GET " + wsPath + " HTTP/1.1\r\n" +
                    "Host: " + tvIp + ":" + TV_REMOTE_PORT + "\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: " + key + "\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n";
                out.write(handshake.getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Read response until empty line (101 Switching Protocols)
                if (!readHttpResponse(in)) {
                    Log.w(TAG, "WS handshake failed");
                    return false;
                }
                Log.i(TAG, "WS handshake OK with " + tvIp);

                // Wait for TV "connect" message (may include pairing token)
                readWsFrame(in); // {"event":"ms.channel.connect","data":{...}}

                // --- Send open-browser command ---
                // Samsung Internet browser app ID on Tizen
                String json =
                    "{\"method\":\"ms.channel.emit\"," +
                    "\"params\":{" +
                        "\"event\":\"ed.apps.launch\"," +
                        "\"to\":\"host\"," +
                        "\"data\":{" +
                            "\"appId\":\"org.tizen.browser\"," +
                            "\"action_type\":\"NATIVE_LAUNCH\"," +
                            "\"metaTag\":\"" + escapeJson(gameUrl) + "\"" +
                        "}" +
                    "}}";
                sendWsTextFrame(out, json);

                Log.i(TAG, "Sent open-browser command: " + gameUrl);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Samsung Remote API error: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Minimal WebSocket helpers (RFC 6455, text frames only)
    // -------------------------------------------------------------------------

    /** Generate a random 16-byte Sec-WebSocket-Key (Base64-encoded). */
    private static String generateWsKey() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        return Base64.encodeToString(key, Base64.NO_WRAP);
    }

    /** Read HTTP response headers, return true if status is 101. */
    private static boolean readHttpResponse(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b, prev = -1;
        boolean got101 = false;
        while (true) {
            b = in.read();
            if (b < 0) break;
            sb.append((char) b);
            // Detect end of headers (\r\n\r\n)
            String s = sb.toString();
            if (s.contains("\r\n\r\n")) {
                got101 = s.contains("101");
                break;
            }
        }
        return got101;
    }

    /**
     * Read one WebSocket text frame (unmasked, server-to-client).
     * Supports payload up to 65535 bytes (more than enough for Samsung messages).
     * Returns the text payload, or empty string on error.
     */
    private static String readWsFrame(InputStream in) throws IOException {
        int b0 = in.read(); // FIN + opcode
        int b1 = in.read(); // MASK + length
        if (b0 < 0 || b1 < 0) return "";

        long len = b1 & 0x7F;
        if (len == 126) {
            len  = (in.read() & 0xFF) << 8;
            len |= (in.read() & 0xFF);
        } else if (len == 127) {
            // 8-byte length — skip (shouldn't happen for small JSON messages)
            for (int i = 0; i < 8; i++) in.read();
            return "";
        }

        byte[] payload = new byte[(int) len];
        int read = 0;
        while (read < (int) len) {
            int r = in.read(payload, read, (int) len - read);
            if (r < 0) break;
            read += r;
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    /**
     * Send a masked WebSocket text frame (client-to-server).
     * Masking is required by RFC 6455 for client frames.
     */
    private static void sendWsTextFrame(OutputStream out, String text) throws IOException {
        byte[] payload  = text.getBytes(StandardCharsets.UTF_8);
        byte[] maskKey  = new byte[4];
        new SecureRandom().nextBytes(maskKey);

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN=1, opcode=1 (text)

        int len = payload.length;
        if (len < 126) {
            frame.write(0x80 | len); // MASK=1
        } else {
            frame.write(0x80 | 126);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        }

        frame.write(maskKey);

        for (int i = 0; i < len; i++) {
            frame.write(payload[i] ^ maskKey[i % 4]);
        }

        out.write(frame.toByteArray());
        out.flush();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
