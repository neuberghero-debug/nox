package dev.noxclient.media;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Anonymous Twitch chat reader — justinfan login on irc.chat.twitch.tv:6667,
 * no OAuth key needed. Ring-buffers the last N "<user>: <msg>" lines for the
 * HUD, answers PINGs, auto-reconnects, restarts when the channel changes.
 * Stream status via decapi.me uptime (keyless).
 */
public final class TwitchService {
    private static final Deque<String> lines = new ArrayDeque<>();
    private static volatile String channel = "", status = "offline";
    private static volatile boolean started, live;
    private static volatile int cap = 6;
    private static Socket sock;
    private static BufferedWriter out;
    private static String joined = "";

    public static void start(String chan, int maxLines) {
        cap = Math.max(1, maxLines);
        channel = chan == null ? "" : chan.trim().toLowerCase().replaceFirst("^#", "");
        if (!started) {
            started = true;
            Thread t = new Thread(TwitchService::loop, "nox-twitch");
            t.setDaemon(true);
            t.start();
        }
    }

    public static java.util.List<String> lines() {
        synchronized (lines) { return java.util.List.copyOf(lines); }
    }
    public static boolean live() { return live; }
    public static String status() { return status; }

    private static void loop() {
        while (true) {
            try {
                if (channel.isEmpty()) { status = "no channel"; live = false; close(); joined = ""; sleep(1500); continue; }
                if (sock == null || sock.isClosed() || !channel.equals(joined)) connect();
                if (sock != null) readLines();
                sleep(400);
            } catch (Throwable e) { status = "reconnecting…"; close(); joined = ""; sleep(4000); }
        }
    }

    private static void connect() throws IOException {
        close();
        joined = channel;
        status = "connecting…";
        sock = new Socket("irc.chat.twitch.tv", 6667);
        out = new BufferedWriter(new OutputStreamWriter(
            sock.getOutputStream(), StandardCharsets.UTF_8));
        send("NICK justinfan" + (10000 + (int) (Math.random() * 89999)));
        send("JOIN #" + joined);
        status = "connected";
        new Thread(TwitchService::pollLive, "nox-twitch-live").start();
    }

    private static void readLines() throws IOException {
        sock.setSoTimeout(400);
        InputStream in = sock.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (channel.equals(joined) && sock != null && !sock.isClosed()) {
            int b;
            try { b = in.read(); }
            catch (java.net.SocketTimeoutException e) {
                synchronized (lines) { while (lines.size() > cap) lines.pollFirst(); }
                continue;
            }
            if (b < 0) throw new EOFException("closed");
            if (b == '\n') {
                String line = buf.toString(StandardCharsets.UTF_8).strip(); buf.reset();
                if (line.startsWith("PING")) send("PONG " + line.substring(4).strip());
                else if (line.contains(" PRIVMSG ")) {
                    int bang = line.indexOf('!');
                    int colon = line.lastIndexOf(" :");
                    if (bang > 0 && colon > bang) {
                        String who = line.substring(1, bang);
                        String msg = line.substring(colon + 2);
                        synchronized (lines) {
                            lines.addLast(who + ": " + msg);
                            while (lines.size() > cap) lines.pollFirst();
                        }
                    }
                }
                continue;
            }
            buf.write(b);
            if (buf.size() > 8192) buf.reset();
        }
    }

    /** decapi.me uptime — empty body = offline. Polled every 30s. */
    private static void pollLive() {
        String ch = joined;
        while (ch.equals(joined) && !ch.isEmpty()) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://decapi.me/twitch/uptime/" + ch).openConnection();
                c.setConnectTimeout(5000); c.setReadTimeout(5000);
                String body = new String(c.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).strip();
                live = c.getResponseCode() == 200 && !body.isEmpty()
                    && !body.contains("offline") && !body.contains("not found");
            } catch (Throwable e) { live = false; }
            sleep(30_000);
        }
    }

    private static void send(String s) throws IOException {
        out.write(s + "\r\n"); out.flush();
    }

    private static void close() {
        try { if (sock != null) sock.close(); } catch (Exception ignored) {}
        sock = null; out = null;
        synchronized (lines) { lines.clear(); }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
