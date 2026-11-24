package dev.noxclient.module.donutsmp;

import dev.noxclient.module.donutsmp.SurveyBook.Family;
import dev.noxclient.module.donutsmp.SurveyBook.Point;
import dev.noxclient.module.donutsmp.SurveyBook.Sign;

import java.util.EnumSet;

public final class BaseSurveyTest {
    public static void main(String[] args) {
        var b = new SurveyBook();
        var all = EnumSet.allOf(Family.class);
        var chest = new Sign(Family.STORAGE,false); var shulker = new Sign(Family.STORAGE,true);
        var torch = new Sign(Family.LIGHT,false);
        check(SurveyBook.acceptsServer("PLAY.DONUTSMP.NET.:25565") && SurveyBook.acceptsServer("donutsmp.net"), "scope");
        for (String bad : new String[]{null,"localhost","evildonutsmp.net","donutsmp.net.example","donutsmp.net:99999","play..donutsmp.net"}) check(!SurveyBook.acceptsServer(bad), "reject " + bad);
        b.observe(p(15,64),chest,0); b.observe(p(16,64),torch,1); b.observe(p(16,64),torch,2);
        check(b.candidates(2,20,4,all,3,2,false).isEmpty(), "same block is one observation");
        b.observe(p(17,64),chest,3);
        check(b.candidates(3,20,4,all,3,2,true).isEmpty(), "village-like mix not distinctive");
        b.observe(p(17,64),shulker,4);
        var f = b.candidates(4,20,4,all,3,2,true);
        check(f.size()==1 && f.getFirst().blocks()==3 && f.getFirst().families().size()==2, "cross-chunk evidence and replacement");
        check(b.candidates(4,20,4,EnumSet.of(Family.STORAGE),3,2,true).isEmpty(), "disabled family");
        b.observe(p(16,64),null,5); b.observe(p(16,84),torch,6);
        check(b.candidates(6,20,4,all,3,2,true).isEmpty(), "no vertical bridging or replaced blocks");
        b.observe(p(16,64),torch,7);
        check(b.candidates(20,20,4,all,3,2,true).isEmpty(), "expiry at boundary");
        b.clear(); check(b.candidates(0,20,4,all,1,1,false).isEmpty(), "session reset");
        System.out.println("BaseSurvey checks passed");
    }

    static Point p(int x,int y) { return new Point(x,y,0); }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
