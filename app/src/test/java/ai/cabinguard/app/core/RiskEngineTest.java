package ai.cabinguard.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RiskEngineTest {

    @Test
    public void modelMatchesPublishedStudy() {
        // McLaren 외(2005): 맑은 날 60분에 약 22℃ 상승, 그중 80%가 처음 30분에 발생
        double at30 = CabinTempModel.predictCabinAirC(30, Sky.CLEAR, 30, 0) - 30;
        double at60 = CabinTempModel.predictCabinAirC(30, Sky.CLEAR, 60, 0) - 30;
        assertEquals(0.80 * 22, at30, 0.1);
        assertTrue(at60 > 20.5 && at60 <= 22);
    }

    @Test
    public void summerMartParkingIsDangerous() {
        // 시나리오 1: 외기 34℃, 맑음, 1시간 30분 주차
        List<StoredItem> items = new ArrayList<>();
        items.add(new StoredItem("보조배터리", HazardCategory.LITHIUM_BATTERY, true));   // 대시보드 거치대
        items.add(new StoredItem("스프레이", HazardCategory.PRESSURIZED_GAS, false));    // 도어 포켓
        List<RiskEngine.Assessment> result = RiskEngine.assess(items, 34, Sky.CLEAR, 90, 0);
        assertEquals(RiskEngine.Level.DANGER, result.get(0).level);
        assertEquals(RiskEngine.Level.DANGER, result.get(1).level);
        assertEquals(2, RiskEngine.alertTargets(result).size());
    }

    @Test
    public void shortStopOnCloudyDayStaysSilent() {
        // 시나리오 3: 외기 22℃, 흐림, 10분 정차 → 알림 없음
        List<StoredItem> items = new ArrayList<>();
        items.add(new StoredItem("보조배터리", HazardCategory.LITHIUM_BATTERY, true));
        items.add(new StoredItem("스프레이", HazardCategory.PRESSURIZED_GAS, false));
        List<RiskEngine.Assessment> result = RiskEngine.assess(items, 22, Sky.OVERCAST, 10, 0);
        assertEquals(0, RiskEngine.alertTargets(result).size());
        assertEquals(RiskEngine.Level.SAFE, RiskEngine.worst(result));
    }

    @Test
    public void medicineNeverExceedsCaution() {
        assertEquals(RiskEngine.Level.CAUTION, RiskEngine.levelFor(HazardCategory.MEDICINE, 80));
        assertEquals(RiskEngine.Level.SAFE, RiskEngine.levelFor(HazardCategory.OTHER, 80));
    }

    @Test
    public void classifierAndUtterance() {
        assertEquals(HazardCategory.LITHIUM_BATTERY, ItemClassifier.classify("보조 배터리"));
        assertEquals(HazardCategory.PRESSURIZED_GAS, ItemClassifier.classify("부탄가스"));
        assertEquals(HazardCategory.OTHER, ItemClassifier.classify("우산"));
        List<StoredItem> found = ItemClassifier.extractFromUtterance("보조배터리랑 스프레이 차에 둘게");
        assertEquals(2, found.size());
    }

    @Test
    public void calibrationMovesTowardMeasurement() {
        double offset = CabinTempModel.updateCalibration(0, 56, 58);
        assertEquals(0.6, offset, 1e-9);
        assertEquals(10, CabinTempModel.updateCalibration(9.9, 40, 80), 1e-9);
    }
}
