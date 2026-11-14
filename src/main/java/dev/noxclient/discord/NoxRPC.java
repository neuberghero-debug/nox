package dev.noxclient.discord;

import dev.noxclient.NoxClient;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/**
 * Minimal Discord Rich Presence via Discord's local IPC (no GameSDK jar):
 * handshake → SET_ACTIVITY frame over the discord-ipc socket. Degrades
 * silently when Discord isn't running.
 */
public class NoxRPC {
    private static final String CLIENT_ID = "1570175537941454848"; // NOX app
    private volatile boolean enabled = true, running;
    private RandomAccessFile pipe; // windows named-pipe path
    private java.nio.channels.SocketChannel chan;
    private java.net.http.WebSocket ws;
    private OutputStream out;
    private InputStream in;

    public void start() {
        running = true;
        Thread t = new Thread(this::loop, "nox-rpc");
        t.setDaemon(true); t.start();
    }

    public void stop() { running = false; }

    private void loop() {
        while (running) {
            try {
                if (!enabled) { sleep(2_000); continue; }
                if (!connected()) {
                    closeQuietly();
                    if (!connect()) { sleep(10_000); continue; }
                }
                sendActivity();
                sleep(15_000);
            } catch (Throwable t) {
                log("dropped: " + t.getMessage());
                closeQuietly(); sleep(10_000);
            }
        }
    }

    private boolean connected() { return out != null || ws != null; }

    private boolean connect() {
        try {
            // Linux/Mac: $XDG_RUNTIME_DIR/discord-ipc-0 ... Windows: \\.\pipe\discord-ipc-0
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                for (int i = 0; i < 4 && out == null; i++) {
                    try {
                        pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                        out = new OutputStream() { public void write(int b) throws IOException { pipe.write(b); }
                            public void write(byte[] b, int o, int l) throws IOException { pipe.write(b, o, l); } };
                        in = new InputStream() { public int read() throws IOException { return pipe.read(); }
                            public int read(byte[] b, int o, int l) throws IOException { return pipe.read(b, o, l); } };
                    } catch (FileNotFoundException ignored) {}
                }
                if (out != null) { handshake(); return true; }
            } else {
                // scan every place discord-ipc-* can live: native, flatpak
                // (Discord/Vesktop/WebCord/ArmCord/PTB/Canary), snap, macOS tmp
                // try EVERY candidate socket — inside flatpak sandboxes the
                // first hit is often a dangling symlink, the real one later
                java.util.List<Path> socks = findIpcSockets();
                if (socks.isEmpty()) log("no discord-ipc socket found");
                int tried = 0;
                for (Path sock : socks) {
                    tried++;
                    try {
                        chan = java.nio.channels.SocketChannel.open(
                            java.net.UnixDomainSocketAddress.of(sock));
                        out = java.nio.channels.Channels.newOutputStream(chan);
                        in = java.nio.channels.Channels.newInputStream(chan);
                        handshake();
                        log("connected via " + sock);
                        return true;
                    } catch (Throwable t) {
                        closeQuietly();   // dead socket → next candidate
                    }
                }
                if (tried > 0) log("all " + tried + " discord-ipc sockets dead — retrying later");
            }
            // nothing reachable — WS-RPC parks vesktop's single-client slot
            // while staying unserviced, so only use it when explicitly wanted
            if (System.getProperty("nox.rpcWs") != null) return connectWebSocket();
            return false;
        } catch (Throwable t) { return false; }
    }

    /** Discord/arRPC WebSocket-RPC: ws://127.0.0.1:646X/?v=1&client_id=... */
    private boolean connectWebSocket() {
        for (int port = 6463; port <= 6472; port++) {
            try {
                var ws = java.net.http.HttpClient.newHttpClient().newWebSocketBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(2))
                    .buildAsync(java.net.URI.create(
                        "ws://127.0.0.1:" + port + "/?v=1&client_id=" + CLIENT_ID
                        + "&encoding=json&origin=https://discord.com"),
                        new java.net.http.WebSocket.Listener() {
                            public java.util.concurrent.CompletionStage<?> onText(
                                    java.net.http.WebSocket w, CharSequence data, boolean last) {
                                String s = data.toString();
                                if (s.contains("\"code\"") || s.contains("rror"))
                                    System.err.println("[NOX-RPC] " + s);
                                return java.net.http.WebSocket.Listener.super.onText(w, data, last);
                            }
                        })
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);
                this.ws = ws;
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /** All candidate discord-ipc-* sockets across native/flatpak/snap/tmp dirs. */
    private static java.util.List<Path> findIpcSockets() {
        java.util.List<Path> hits = new java.util.ArrayList<>();
        String xdg = System.getenv("XDG_RUNTIME_DIR");
        String uid = String.valueOf(new com.sun.security.auth.module.UnixSystem().getUid());
        java.util.List<Path> dirs = new java.util.ArrayList<>();
        if (xdg != null) dirs.add(Paths.get(xdg));
        String tmpenv = System.getenv("TMPDIR");          // macOS Discord socket dir
        if (tmpenv != null) dirs.add(Paths.get(tmpenv));
        dirs.add(Paths.get("/run/user", uid));
        dirs.add(Paths.get("/tmp"));
        dirs.add(Paths.get(System.getProperty("java.io.tmpdir")));
        String[] apps = {"app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary",
            "app/com.discordapp.DiscordPTB", "app/com.discordapp.DiscordDevelopment",
            "app/dev.vencord.vesktop", "app/dev.vencord.Vesktop",
            "app/io.github.spacingbat3.webcord", "app/com.armcord.ArmCord",
            "app/xyz.armcord.ArmCord", "snap.discord", "snap.discord-canary"};
        for (Path d : new java.util.ArrayList<>(dirs))
            for (String a : apps) dirs.add(d.resolve(a));
        // host view of every flatpak app's private xdg-run (where vesktop really writes)
        for (Path d : new java.util.ArrayList<>(dirs))
            try (var s = Files.list(d.resolve(".flatpak"))) {
                s.filter(Files::isDirectory).forEach(p -> dirs.add(p.resolve("xdg-run")));
            } catch (Throwable ignored) {}
        for (Path d : dirs) {
            try (var s = Files.list(d)) {
                s.filter(p -> p.getFileName().toString().startsWith("discord-ipc-"))
                 .forEach(hits::add);
            } catch (Throwable ignored) {}
        }
        return hits;
    }

    private void frame(int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        var d = new DataOutputStream(b);
        d.writeInt(Integer.reverseBytes(op));
        d.writeInt(Integer.reverseBytes(body.length));
        b.write(body);
        out.write(b.toByteArray()); out.flush();
    }

    /** Read exactly one RPC frame: 8-byte LE header (op,len) + body. */
    private String readFrame() throws IOException { return readFrame(0); }

    /** readFrame with an optional deadline — a socket that accepts but never
        answers (dangling flatpak ipc) must not park the rpc thread forever. */
    private String readFrame(long timeoutMs) throws IOException {
        byte[] hdr = readN(8, timeoutMs);
        if (hdr.length < 8) throw new EOFException("socket closed");
        int len = (hdr[4] & 0xFF) | (hdr[5] & 0xFF) << 8
            | (hdr[6] & 0xFF) << 16 | (hdr[7] & 0xFF) << 24;   // u32 LE
        if (len < 0 || len > 1 << 20) throw new IOException("bad frame len " + len);
        byte[] body = readN(len, timeoutMs);
        if (body.length < len) throw new EOFException("socket closed mid-frame");
        return new String(body, StandardCharsets.UTF_8);
    }

    /** readNBytes with a deadline on unix sockets (non-blocking poll); falls
        back to a plain blocking read for windows pipes. */
    private byte[] readN(int n, long timeoutMs) throws IOException {
        if (timeoutMs <= 0 || chan == null) return in.readNBytes(n);
        long end = System.currentTimeMillis() + timeoutMs;
        ByteArrayOutputStream acc = new ByteArrayOutputStream(n);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(n);
        try { chan.configureBlocking(false); } catch (Throwable ignored) {}
        try {
            while (acc.size() < n) {
                buf.clear();
                buf.limit(Math.min(n - acc.size(), n));
                int r = chan.read(buf);
                if (r < 0) break;                       // EOF
                if (r > 0) { acc.write(buf.array(), 0, r); continue; }
                if (System.currentTimeMillis() >= end) throw new IOException("rpc read timed out");
                try { Thread.sleep(10); } catch (InterruptedException ie) { break; }
            }
        } finally {
            try { chan.configureBlocking(true); } catch (Throwable ignored) {}
        }
        return acc.toByteArray();
    }

    private void handshake() throws IOException {
        frame(0, "{\"v\":1,\"client_id\":\"" + CLIENT_ID + "\"}");
        String ready = readFrame(4000);  // DISPATCH/READY — real size, no blind 4k read
        if (ready.contains("Invalid Client ID")) throw new IOException("invalid client id");
        if (!ready.contains("READY")) log("handshake: " + ready.substring(0, Math.min(120, ready.length())));
    }

    private void sendActivity() throws IOException {
        String state = "on DonutSMP";
        String server = currentServer();
        if (server != null) state = "playing " + server;
        // no "buttons" — arRPC (Vesktop/WebCord) rejects SET_ACTIVITY with it
        // ("Origin is not allowed"). type/name/instance keep strict parsers happy.
        String json = "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":"
            + ProcessHandle.current().pid() + ",\"activity\":{\"type\":0,"
            + "\"name\":\"NOX Client\",\"instance\":true,\"details\":\"NOX "
            + NoxClient.VERSION + "\",\"state\":\"" + state
            + "\",\"assets\":{\"large_image\":\"nox\",\"large_text\":\"NOX Client\"}"
            + "}},\"nonce\":\"" + UUID.randomUUID() + "\"}";
        if (ws != null) { ws.sendText(json, true); return; }
        frame(1, json);
        String ack = readFrame(5000);    // SET_ACTIVITY echo / error — keeps stream aligned
        if (ack.contains("\"code\"") || ack.contains("rror"))
            log("activity: " + ack.substring(0, Math.min(160, ack.length())));
    }

    private static void log(String s) {
        System.out.println("[NOX-RPC] " + s);   // surfaces in latest.log
    }

    private String currentServer() {
        var d = Minecraft.getInstance().getCurrentServer();
        return d == null ? null : d.ip;
    }

    private void closeQuietly() {
        try { if (pipe != null) pipe.close(); } catch (IOException ignored) {}
        try { if (chan != null) chan.close(); } catch (IOException ignored) {}
        try { if (ws != null) ws.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, ""); } catch (Exception ignored) {}
        pipe = null; chan = null; ws = null; out = null; in = null;
    }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    public void setEnabled(boolean v) { enabled = v; if (!v) closeQuietly(); }
}
