package dev.noxclient.replay;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * .donutreplay playback (Modul 22/D) — parses DNRP files and provides an
 * interpolated entity view at any timestamp.
 *
 * Entity poses lerp between adjacent snapshots. Event frames (0xF0) land on
 * the marker timeline. The playback screen consumes {@link #sample(double)}.
 */
public final class ReplayPlayer {
    public record Ent(int id, int tag, float x, float y, float z, float yaw, float pitch, int flags) {}
    public record Event(long ms, String kind, String data) {}
    private record Snap(long ms, List<Ent> ents) {}

    private final TreeMap<Long, Snap> snaps = new TreeMap<>();
    private final List<Event> events = new ArrayList<>();
    private long duration;
    private String clientVersion = "";

    /** Playback cursor: position ms, speed multiplier, playing flag. */
    public double cursor, speed = 1.0;
    public boolean playing = true;

    public static ReplayPlayer load(Path f) throws IOException {
        ReplayPlayer p = new ReplayPlayer();
        try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(f)))) {
            if (in.readInt() != 0x444E5250) throw new IOException("not a DNRP file");
            int ver = in.readShort();
            if (ver != 1) throw new IOException("DNRP v" + ver + " unsupported");
            p.clientVersion = in.readUTF();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                byte[] fr = new byte[in.readInt()];
                in.readFully(fr);
                p.frame(fr);
            }
        }
        p.duration = p.snaps.isEmpty() ? 0 : p.snaps.lastKey();
        return p;
    }

    private void frame(byte[] fr) throws IOException {
        var d = new DataInputStream(new java.io.ByteArrayInputStream(fr));
        long ms = Integer.toUnsignedLong(d.readInt());
        if (d.available() > 0 && d.markSupported()) {
            d.mark(2);
            if (d.readByte() == (byte) 0xF0) {
                events.add(new Event(ms, d.readUTF(), d.readUTF()));
                return;
            }
            d.reset();
        }
        int cnt = d.readShort();
        List<Ent> ents = new ArrayList<>(cnt);
        for (int i = 0; i < cnt; i++)
            ents.add(new Ent(d.readInt(), d.readByte(), d.readFloat(), d.readFloat(),
                d.readFloat(), d.readFloat(), d.readFloat(), d.readByte()));
        snaps.put(ms, new Snap(ms, ents));
    }

    public long duration() { return duration; }
    public List<Event> events() { return events; }
    public String clientVersion() { return clientVersion; }

    /** Advance the cursor by real elapsed ms (call per frame render). */
    public void tick(long realMs) {
        if (playing) cursor += realMs * speed;
        if (cursor > duration) cursor = duration;   // hold at end
        if (cursor < 0) cursor = 0;
    }

    public void seek(double ms) { cursor = Math.max(0, Math.min(duration, ms)); }

    /** Interpolated entity list at the current cursor. */
    public List<Ent> sample() { return sample(cursor); }

    public List<Ent> sample(double ms) {
        var lo = snaps.floorEntry((long) ms);
        var hi = snaps.ceilingEntry((long) ms);
        if (lo == null) return hi == null ? List.of() : hi.getValue().ents();
        if (hi == null || hi.getKey() == lo.getKey()) return lo.getValue().ents();
        float t = (float) ((ms - lo.getKey()) / (double) (hi.getKey() - lo.getKey()));
        var a = lo.getValue().ents(); var b = new java.util.HashMap<Integer, Ent>();
        for (Ent e : hi.getValue().ents()) b.put(e.id(), e);
        List<Ent> out = new ArrayList<>(a.size());
        for (Ent ea : a) {
            Ent eb = b.get(ea.id());
            if (eb == null) { out.add(ea); continue; }
            out.add(new Ent(ea.id(), ea.tag(),
                ea.x() + (eb.x() - ea.x()) * t,
                ea.y() + (eb.y() - ea.y()) * t,
                ea.z() + (eb.z() - ea.z()) * t,
                ea.yaw() + (eb.yaw() - ea.yaw()) * t,
                ea.pitch() + (eb.pitch() - ea.pitch()) * t,
                ea.flags()));
        }
        return out;
    }
}
