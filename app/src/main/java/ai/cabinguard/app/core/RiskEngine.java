package ai.cabinguard.app.core;

import java.util.ArrayList;
import java.util.List;

/** 물품별 위험 3단계 판정. */
public final class RiskEngine {

    public enum Level { SAFE("안전"), CAUTION("주의"), DANGER("위험");
        public final String label;
        Level(String label) { this.label = label; }
    }

    public static final class Assessment {
        public final StoredItem item;
        public final double zoneTempC;
        public final Level level;
        Assessment(StoredItem item, double zoneTempC, Level level) {
            this.item = item; this.zoneTempC = zoneTempC; this.level = level;
        }
    }

    private RiskEngine() {}

    public static Level levelFor(HazardCategory category, double zoneTempC) {
        if (!Double.isNaN(category.dangerC) && zoneTempC >= category.dangerC) return Level.DANGER;
        if (!Double.isNaN(category.cautionC) && zoneTempC >= category.cautionC) return Level.CAUTION;
        return Level.SAFE;
    }

    public static List<Assessment> assess(List<StoredItem> items, double outsideC, Sky sky,
                                          int parkedMinutes, double calibrationOffsetC) {
        List<Assessment> result = new ArrayList<>();
        for (StoredItem item : items) {
            double zone = CabinTempModel.predictZoneC(outsideC, sky, parkedMinutes, item.directSun, calibrationOffsetC);
            result.add(new Assessment(item, zone, levelFor(item.category, zone)));
        }
        return result;
    }

    /** 하차 알림에 올릴 물품만 골라낸다(주의 이상). 안전한 물품은 알리지 않아 알림 피로를 줄인다. */
    public static List<Assessment> alertTargets(List<Assessment> all) {
        List<Assessment> targets = new ArrayList<>();
        for (Assessment a : all) if (a.level == Level.DANGER) targets.add(a);
        for (Assessment a : all) if (a.level == Level.CAUTION) targets.add(a);
        return targets;
    }

    public static Level worst(List<Assessment> all) {
        Level worst = Level.SAFE;
        for (Assessment a : all) if (a.level.ordinal() > worst.ordinal()) worst = a.level;
        return worst;
    }
}
