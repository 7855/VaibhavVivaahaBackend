import com.uravugal.matrimony.utils.*;
import com.uravugal.matrimony.dtos.*;
import java.util.*;

public class PoruthamCheck {
    static int fails = 0;
    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + "  " + detail);
        if (!ok) fails++;
    }
    static String g(PoruthamResultDto r) { return r.getGrade(); }
    public static void main(String[] a) {
        // ---- tables ----
        Map<Integer,Integer> rc = new TreeMap<>();
        for (int s=1;s<=27;s++) rc.merge(StarInfo.RAJJU.get(s),1,Integer::sum);
        check("rajju covers 27 stars, 6/6/6/6/3", rc.toString().equals("{1=6, 2=6, 3=6, 4=6, 5=3}"), rc.toString());
        Map<String,Integer> yc = new TreeMap<>();
        for (int s=1;s<=27;s++) yc.merge(StarInfo.YONI.get(s),1,Integer::sum);
        check("yoni 14 animals, 27 stars", yc.size()==14 && yc.values().stream().mapToInt(i->i).sum()==27 && yc.get("Mongoose")==1, yc.toString());
        Map<String,Integer> gc = new TreeMap<>();
        for (int s=1;s<=27;s++) gc.merge(StarInfo.GANA.get(s),1,Integer::sum);
        check("gana 9/9/9", gc.toString().equals("{Deva=9, Manushya=9, Rakshasa=9}"), gc.toString());
        boolean sym = true; int edges=0;
        for (int s=1;s<=27;s++) for (int t : StarInfo.VEDHA.get(s)) { edges++; if (!StarInfo.VEDHA.get(t).contains(s)) sym=false; }
        check("vedhai symmetric, 15 pairs", sym && edges==30, "edges="+edges);
        check("vedhai Aswini-Kettai", StarInfo.VEDHA.get(1).contains(18), "");
        check("vedhai Mirugasiridham-Chithirai-Avittam mutual", StarInfo.VEDHA.get(5).contains(14) && StarInfo.VEDHA.get(5).contains(23) && StarInfo.VEDHA.get(14).contains(23), "");
        check("vedhai Hastham-Sathayam", StarInfo.VEDHA.get(13).contains(24), "");
        check("vedhai Magam-Revathi", StarInfo.VEDHA.get(10).contains(27), "");
        // aliases
        String[] vocab = {"Aswini","Bharani","Krithikai","Rohini","Mirugasiridham","Thiruvathirai","Punarpusam","Poosam","Ayilyam","Magam","Pooram","Uthiram","Hastham","Chithirai","Swathi","Visagam","Anusham","Kettai","Moolam","Pooradam","Uthiradam","Thiruvonam","Avittam","Sathayam","Poorattathi","Uthirattathi","Revathi"};
        boolean okv = true; for (int i=0;i<27;i++) if (StarInfo.starId(vocab[i])!=i+1) { okv=false; System.out.println("  bad "+vocab[i]); }
        check("all 27 DB star names resolve in order", okv, "");
        check("legacy star spellings", StarInfo.starId("Karthigai")==3 && StarInfo.starId("Punarpoosam")==7 && StarInfo.starId("Maham")==10 && StarInfo.starId("  thiruvadhirai ")==6, "");
        String[] rv = {"Mesham","Rishabam","Mithunam","Kadagam","Simmam","Kanni","Thulam","Vrichigam","Dhanushu","Makaram","Kumbam","Meenam"};
        boolean okr = true; for (int i=0;i<12;i++) if (StarInfo.rasiId(rv[i])!=i+1) { okr=false; System.out.println("  bad "+rv[i]); }
        check("edit-page moon sign spellings resolve", okr, "");
        check("old star-match rasi spellings resolve", StarInfo.rasiId("viruchagam")==8 && StarInfo.rasiId("dhanusu")==9 && StarInfo.rasiId("magaram")==10 && StarInfo.rasiId("katagam")==4, "");
        check("unknown star → 0", StarInfo.starId("nonsense")==0 && StarInfo.starId(null)==0, "");

        // ---- counting ----
        check("count girl 1 → boy 4 = 4 (inclusive)", PoruthamEngine.countStars(1,4)==4, "");
        check("count girl 27 → boy 1 = 2", PoruthamEngine.countStars(27,1)==2, "");
        check("count same = 1", PoruthamEngine.countStars(9,9)==1, "");

        // ---- Dina ----
        check("dina 1→2 (count 2) U", g(PoruthamEngine.dina(1,2)).equals("UTHAMAM"), PoruthamEngine.dina(1,2).getReason());
        check("dina 1→3 (count 3) A", g(PoruthamEngine.dina(1,3)).equals("ADHAMAM"), "");
        check("dina 1→10 (count 10, rem 1) A", g(PoruthamEngine.dina(1,10)).equals("ADHAMAM"), "");
        check("dina 1→9 (count 9, rem 0) U", g(PoruthamEngine.dina(1,9)).equals("UTHAMAM"), "");
        check("dina 1→27 (count 27, rem 0) U", g(PoruthamEngine.dina(1,27)).equals("UTHAMAM"), "");
        check("dina same Rohini U", g(PoruthamEngine.dina(4,4)).equals("UTHAMAM"), "");
        check("dina same Aswini M", g(PoruthamEngine.dina(1,1)).equals("MADHIMAM"), "");
        check("dina same Ayilyam A", g(PoruthamEngine.dina(9,9)).equals("ADHAMAM"), "");
        // ---- Mahendra ----
        check("mahendra 1→4 U", g(PoruthamEngine.mahendra(1,4)).equals("UTHAMAM"), "");
        check("mahendra 1→25 U", g(PoruthamEngine.mahendra(1,25)).equals("UTHAMAM"), "");
        check("mahendra 1→5 A", g(PoruthamEngine.mahendra(1,5)).equals("ADHAMAM"), "");
        check("mahendra 20→23 (count 4) U", g(PoruthamEngine.mahendra(20,23)).equals("UTHAMAM"), "");
        // ---- Stree Dheerga ----
        check("stree 1→15 (14) U", g(PoruthamEngine.streeDheerga(1,15)).equals("UTHAMAM"), "");
        check("stree 1→13 (13) M", g(PoruthamEngine.streeDheerga(1,13)).equals("MADHIMAM"), "");
        check("stree 1→8 (8) M", g(PoruthamEngine.streeDheerga(1,8)).equals("MADHIMAM"), "");
        check("stree 1→7 (7) A", g(PoruthamEngine.streeDheerga(1,7)).equals("ADHAMAM"), "");
        // ---- Gana ----
        check("gana Deva girl / Manushya boy U", g(PoruthamEngine.gana(1,2)).equals("UTHAMAM"), "");
        check("gana Manushya girl / Deva boy M", g(PoruthamEngine.gana(2,1)).equals("MADHIMAM"), "");
        check("gana Deva girl / Rakshasa boy A", g(PoruthamEngine.gana(1,3)).equals("ADHAMAM"), "");
        check("gana Rakshasa girl / Deva boy A", g(PoruthamEngine.gana(3,1)).equals("ADHAMAM"), "");
        check("gana same Rakshasa U", g(PoruthamEngine.gana(3,9)).equals("UTHAMAM"), "");
        // ---- Yoni ----
        check("yoni Horse/Buffalo enemy A", g(PoruthamEngine.yoni(1,13)).equals("ADHAMAM"), PoruthamEngine.yoni(1,13).getReason());
        check("yoni Horse M / Horse F U", g(PoruthamEngine.yoni(24,1)).equals("UTHAMAM"), "");
        check("yoni Snake/Mongoose enemy A", g(PoruthamEngine.yoni(4,21)).equals("ADHAMAM"), "");
        check("yoni Cat/Rat enemy A", g(PoruthamEngine.yoni(7,10)).equals("ADHAMAM"), "");
        check("yoni Horse/Elephant friendly U", g(PoruthamEngine.yoni(1,2)).equals("UTHAMAM"), "");
        // ---- Rasi ----
        check("rasi Mesham→Thulam (7) U", g(PoruthamEngine.rasi(1,7)).equals("UTHAMAM"), "");
        check("rasi Mesham→Rishabam (2) A", g(PoruthamEngine.rasi(1,2)).equals("ADHAMAM"), "");
        check("rasi Mesham→Mithunam (3) M", g(PoruthamEngine.rasi(1,3)).equals("MADHIMAM"), "");
        check("rasi Mesham→Kanni (6) A", g(PoruthamEngine.rasi(1,6)).equals("ADHAMAM"), "");
        check("rasi Mesham→Viruchigam (8) A", g(PoruthamEngine.rasi(1,8)).equals("ADHAMAM"), "");
        check("rasi Mesham→Meenam (12) U", g(PoruthamEngine.rasi(1,12)).equals("UTHAMAM"), "");
        check("rasi same U", g(PoruthamEngine.rasi(5,5)).equals("UTHAMAM"), "");
        // ---- Rasi Adhipathi ----
        check("adhipathi Mars/Sun U", g(PoruthamEngine.rasiAdhipathi(1,5)).equals("UTHAMAM"), "");
        check("adhipathi Mars/Mercury A", g(PoruthamEngine.rasiAdhipathi(1,3)).equals("ADHAMAM"), "");
        check("adhipathi Mercury/Venus U", g(PoruthamEngine.rasiAdhipathi(3,2)).equals("UTHAMAM"), "");
        check("adhipathi Moon/Venus A", g(PoruthamEngine.rasiAdhipathi(4,2)).equals("ADHAMAM"), "");
        check("adhipathi Jupiter/Moon: Jup friend Moon, Moon neutral Jup → M", g(PoruthamEngine.rasiAdhipathi(9,4)).equals("MADHIMAM"), PoruthamEngine.rasiAdhipathi(9,4).getReason());
        check("adhipathi same lord U", g(PoruthamEngine.rasiAdhipathi(10,11)).equals("UTHAMAM"), "");
        // ---- Vasya ----
        check("vasya girl Mesham / boy Simmam U", g(PoruthamEngine.vasya(1,5)).equals("UTHAMAM"), "");
        check("vasya girl Simmam / boy Mesham M", g(PoruthamEngine.vasya(5,1)).equals("MADHIMAM"), "");
        check("vasya girl Mesham / boy Kanni A", g(PoruthamEngine.vasya(1,6)).equals("ADHAMAM"), "");
        check("vasya same rasi M", g(PoruthamEngine.vasya(3,3)).equals("MADHIMAM"), "");
        // ---- Rajju ----
        check("rajju Aswini/Magam both Pada A", g(PoruthamEngine.rajju(1,10)).equals("ADHAMAM"), "");
        check("rajju Aswini/Bharani U", g(PoruthamEngine.rajju(1,2)).equals("UTHAMAM"), "");
        check("rajju same Rohini M (exempt)", g(PoruthamEngine.rajju(4,4)).equals("MADHIMAM"), "");
        check("rajju same Aswini A", g(PoruthamEngine.rajju(1,1)).equals("ADHAMAM"), "");
        // ---- Vedhai ----
        check("vedhai Aswini/Kettai A", g(PoruthamEngine.vedhai(1,18)).equals("ADHAMAM"), "");
        check("vedhai Aswini/Bharani U", g(PoruthamEngine.vedhai(1,2)).equals("UTHAMAM"), "");

        // ---- full evaluations ----
        MatchResponseDto r1 = PoruthamEngine.evaluate(1,18,1,7);
        check("Aswini/Kettai → NOT RECOMMENDED (Vedhai)", r1.getVerdict().equals("NOT RECOMMENDED") && r1.getDoshams().contains("Vedhai Dosham"), r1.getVerdict()+" "+r1.getDoshams());
        MatchResponseDto r2 = PoruthamEngine.evaluate(1,10,1,7);
        check("Aswini/Magam → NOT RECOMMENDED (Rajju)", r2.getVerdict().equals("NOT RECOMMENDED") && r2.getDoshams().contains("Rajju Dosham"), r2.getVerdict()+" "+r2.getDoshams());
        // girl Aswini(1) Mesham, boy Rohini(4) Rishabam
        MatchResponseDto r3 = PoruthamEngine.evaluate(1,4,1,2);
        System.out.println("  r3 points="+r3.getPoruthamPoints()+" verdict="+r3.getVerdict()+" essentials="+r3.getEssentialsPassed()+"/"+r3.getEssentialsTotal());
        for (PoruthamResultDto r : r3.getResults()) System.out.println("     "+r.getKey()+" "+r.getGrade()+" ("+r.getPoints()+") "+r.getReason());
        double sum=0; for (PoruthamResultDto r : r3.getResults()) sum+=r.getPoints();
        check("points sum matches", sum==r3.getPoruthamPoints() && r3.getResults().size()==10 && r3.getEssentialsTotal()==5, "");
        check("percentage = points*10", r3.getPercentage()==(int)Math.round(sum*10), r3.getPercentage()+"");
        check("nadi is extra, not counted", r3.getExtras().size()==1 && !r3.getExtras().get(0).isCounted(), "");
        // an excellent pair: girl Rohini(4) Rishabam(2), boy Uthiradam(21) Magaram(10): count 18 → dina rem 0 U, mahendra? 18 no, stree >13 U, gana Manushya/Manushya U, yoni Snake/Mongoose enemy A... pick another
        MatchResponseDto r4 = PoruthamEngine.evaluate(4,22,2,4); // girl Rohini Rishabam, boy Thiruvonam Kadagam
        System.out.println("  r4 points="+r4.getPoruthamPoints()+" verdict="+r4.getVerdict()+" note="+r4.getVerdictNote());
        for (PoruthamResultDto r : r4.getResults()) System.out.println("     "+r.getKey()+" "+r.getGrade()+" "+r.getReason());
        // unknown rasi still evaluates star poruthams
        MatchResponseDto r5 = PoruthamEngine.evaluate(1,2,0,0);
        check("unknown rasi → rasi rows UNKNOWN, no crash", r5.getResults().stream().filter(x->x.getGrade().equals("UNKNOWN")).count()==3, "");
        System.out.println(fails==0 ? "\nALL PASS" : "\nFAILS: "+fails);
        System.exit(fails==0?0:1);
    }
}
