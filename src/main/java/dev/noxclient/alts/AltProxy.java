package dev.noxclient.alts;

import io.netty.channel.ChannelHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import java.net.InetSocketAddress;

/** Per-connection proxy spec applied to the multiplayer Netty pipeline. */
public class AltProxy {
    public enum Type { OFF, SOCKS5, SOCKS4, HTTP }

    public Type type = Type.OFF;
    public String host = "", user = "", pass = "";
    public int port = 1080;

    public static AltProxy off() { return new AltProxy(); }

    public boolean active() { return type != Type.OFF && !host.isEmpty() && port > 0; }

    public ChannelHandler handler() {
        if (!active()) return null;
        InetSocketAddress addr = new InetSocketAddress(host, port);
        String u = user.isEmpty() ? null : user;
        String p = pass.isEmpty() ? null : pass;
        return switch (type) {
            case SOCKS5 -> new Socks5ProxyHandler(addr, u, p);
            case SOCKS4 -> new Socks4ProxyHandler(addr, u);
            case HTTP -> new HttpProxyHandler(addr, u, p);
            default -> null;
        };
    }

    public String describe() {
        return !active() ? "direct" : type + " " + host + ":" + port;
    }
}
