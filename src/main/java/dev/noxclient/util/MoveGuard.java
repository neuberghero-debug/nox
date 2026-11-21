package dev.noxclient.util;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Mirrors the server's per-window move rule: in 26.3 the server resets
 * `receivedPositionThisTick` on every ServerboundClientTickEndPacket, so at
 * most ONE position-bearing packet may leave between two TickEnds — a second
 * one disconnects with multiplayer.disconnect.invalid_player_movement.
 *
 * Wall-clock spacing does NOT model this (client catch-up bursts legally put
 * several Pos+TickEnd pairs into 50 ms; a lagging server can also stretch a
 * window). So we track the window itself:
 *  - TickEnd seen on the wire -> window opens, queued pos packet released.
 *  - A second pos packet inside the window -> newest wins, earlier dropped
 *    (the newer one carries the newest position anyway), released next window.
 *  - requestHop(dy): the next pos packet that passes is rewritten in-flight
 *    with y+dy — one packet stays one packet.
 */
public final class MoveGuard {
    public enum Verdict { PASS, CANCEL, REPLACE }

    private static final long PENDING_TTL_NS = 300_000_000L;  // ~6 client ticks
    private static final long QUIET_NS = 100_000_000L;        // ~2 windows idle

    private static volatile long lastVanillaPos = Long.MIN_VALUE;
    private static boolean windowHasPos;
    private static volatile ServerboundMovePlayerPacket queuedPos;
    private static volatile double pendingDy;
    private static volatile long pendingAt;
    private static volatile double appliedDy;
    private static volatile float pendYaw, pendPitch;
    private static volatile long pendRotAt;
    private static volatile boolean rotPending;
    private static volatile float appliedYaw, appliedPitch;
    private static volatile boolean appliedRot;
    private static boolean injecting;
    private static volatile boolean ogPending, appliedOg;
    private static volatile boolean pendOgVal;
    private static volatile long pendOgAt;

    private MoveGuard() {}

    /** Wrap injected sends so they don't count as vanilla traffic. */
    public static void beginInject() { injecting = true; }
    public static void endInject()   { injecting = false; }

    /** Forget window/queue/pending state — call when the connection drops so a
     *  stale queued pos packet never releases into a fresh server. */
    public static void reset() {
        windowHasPos = false;
        queuedPos = null;
        pendingDy = 0; pendingAt = 0;
        rotPending = false; pendRotAt = 0;
        ogPending = false; pendOgAt = 0;
        injecting = false;
        trace("RESET");
    }

    /** Offset the next outgoing position packet by dy (fresh for ~6 ticks). */
    public static void requestHop(double dy) { pendingDy = dy; pendingAt = System.nanoTime(); }

    /** Force the onGround flag of the next outgoing move packet — NoFall-grade
     *  spoofing without sending an extra move packet. */
    public static void requestOnGround(boolean og) {
        pendOgVal = og; pendOgAt = System.nanoTime(); ogPending = true;
    }

    /**
     * Rewrite the rotation of outgoing move packets to (yaw, pitch) — silent
     * aim without sending a single extra packet (packet-rate checks like
     * GrimAC Timer kick on extras).
     */
    public static void requestRot(float yaw, float pitch) {
        pendYaw = yaw; pendPitch = pitch;
        pendRotAt = System.nanoTime();
        rotPending = true;
    }

    /** True when a vanilla position packet left within ~2 windows. */
    public static boolean vanillaPosRecently() {
        return System.nanoTime() - lastVanillaPos < QUIET_NS;
    }

    // ---- diagnostics ----
    public static int wireCount, injected, replaced, canceled;
    private static final java.util.ArrayDeque<String> TRACE = new java.util.ArrayDeque<>();
    private static final boolean VERBOSE =
        Boolean.getBoolean("nox.moveguard.trace") || "1".equals(System.getenv("NOX_MOVEGUARD_TRACE"));
    private static long t0 = System.nanoTime();

    private static void trace(String ev) {
        String line = "[MoveGuard] t=" + (System.nanoTime() - t0) / 1_000_000L + "ms " + ev;
        if (VERBOSE) System.out.println(line);
        TRACE.addLast(line);
        while (TRACE.size() > 80) TRACE.pollFirst();
    }

    public static void dumpTrace() { for (var l : TRACE) System.out.println(l); }

    public static String dump() {
        return "wire=" + wireCount + " inj=" + injected + " rep=" + replaced
            + " can=" + canceled + " queued=" + (queuedPos != null)
            + " win=" + windowHasPos;
    }

    /** Mirror of the server's containsInvalidValues — log bad packets before they kick us. */
    private static void sanity(ServerboundMovePlayerPacket p) {
        double x = p.getX(0), y = p.getY(0), z = p.getZ(0);
        float yr = p.getYRot(0), xr = p.getXRot(0);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
            || !Float.isFinite(yr) || !Float.isFinite(xr))
            trace("INVALID-VALUES " + p.getClass().getSimpleName()
                + " x=" + x + " y=" + y + " z=" + z + " yr=" + yr + " xr=" + xr);
    }

    /** From the send mixin: decide what happens to an outgoing move packet. */
    public static Verdict decide(ServerboundMovePlayerPacket p) {
        sanity(p);
        long now = System.nanoTime();
        boolean rot = rotPending && now - pendRotAt < PENDING_TTL_NS;
        if (!rot) rotPending = false;
        boolean ogR = ogPending && now - pendOgAt < PENDING_TTL_NS;
        if (!ogR) ogPending = false;
        appliedRot = false;
        appliedOg = false;

        if (!p.hasPosition()) {
            // Rot/StatusOnly — rewrite or upgrade rotation/onGround so silent
            // aim + nofall reach the server on packets the client sends anyway
            if (rot || ogR) {
                if (rot) {
                    rotPending = false;
                    appliedRot = true;
                    appliedYaw = pendYaw; appliedPitch = pendPitch;
                }
                if (ogR) { ogPending = false; appliedOg = true; }
                replaced++;
                trace("WIRE-ROTSILENT " + p.getClass().getSimpleName());
                return Verdict.REPLACE;
            }
            return Verdict.PASS;
        }

        String cls = p.getClass().getSimpleName();
        if (windowHasPos) {          // second pos in one window -> kick. keep newest.
            queuedPos = p;
            canceled++;
            trace("HOLD " + cls + " (win occupied) can=" + canceled);
            return Verdict.CANCEL;
        }
        if (injecting) { injected++; windowHasPos = true; wireCount++; trace("WIRE-INJ " + cls); return Verdict.PASS; }
        lastVanillaPos = now;
        windowHasPos = true;
        wireCount++;

        boolean hop = pendingDy != 0 && now - pendingAt < PENDING_TTL_NS;
        appliedDy = hop ? pendingDy : 0;
        pendingDy = 0;
        if (hop || rot || ogR) {
            if (rot) { appliedRot = true; appliedYaw = pendYaw; appliedPitch = pendPitch; rotPending = false; }
            if (ogR) { ogPending = false; appliedOg = true; }
            replaced++;
            trace("WIRE-REP " + cls + (hop ? " dy=" + appliedDy : "") + (appliedRot ? " rot" : "") + " rep=" + replaced);
            return Verdict.REPLACE;
        }
        trace("WIRE " + cls);
        return Verdict.PASS;
    }

    /**
     * From the send mixin, TAIL of a TickEnd send: the window just opened.
     * Release the queued position packet first into the fresh window.
     */
    public static void tickEnd() {
        windowHasPos = false;
        trace("TICKEND win opened" + (queuedPos != null ? " releasing" : ""));
        var q = queuedPos;
        if (q == null) return;
        queuedPos = null;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().send(q);   // re-enters decide() → fills window
    }

    /** Transformed copy of the packet just decided REPLACE (hop/rot/og applied). */
    public static ServerboundMovePlayerPacket offsetCopy(ServerboundMovePlayerPacket p) {
        boolean og = appliedOg ? pendOgVal : p.isOnGround();
        boolean hc = p.horizontalCollision();
        if (!p.hasPosition()) {
            if (appliedRot)
                return new ServerboundMovePlayerPacket.Rot(appliedYaw, appliedPitch, og, hc);
            // keep the original type — StatusOnly stays StatusOnly, Rot stays Rot
            if (p instanceof ServerboundMovePlayerPacket.StatusOnly)
                return new ServerboundMovePlayerPacket.StatusOnly(og, hc);
            return new ServerboundMovePlayerPacket.Rot(
                p.getYRot(0), p.getXRot(0), og, hc);
        }
        double x = p.getX(0), y = p.getY(0) + appliedDy, z = p.getZ(0);
        if (appliedRot)
            return new ServerboundMovePlayerPacket.PosRot(x, y, z, appliedYaw, appliedPitch, og, hc);
        if (p.hasRotation())
            return new ServerboundMovePlayerPacket.PosRot(
                x, y, z, p.getYRot(0), p.getXRot(0), og, hc);
        return new ServerboundMovePlayerPacket.Pos(x, y, z, og, hc);
    }
}
