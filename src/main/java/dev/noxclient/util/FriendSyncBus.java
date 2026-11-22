package dev.noxclient.util;

import java.util.function.Predicate;

/** Cross-client friend sync bridge: the free Friends module queries
 *  isSynced(); FriendSync (premium pack) installs the predicate. */
public final class FriendSyncBus {
    private static volatile Predicate<String> synced;

    public static void syncedPredicate(Predicate<String> p) { synced = p; }

    public static boolean isSynced(String name) {
        Predicate<String> s = synced;
        return s != null && name != null && s.test(name);
    }

    private FriendSyncBus() {}
}
