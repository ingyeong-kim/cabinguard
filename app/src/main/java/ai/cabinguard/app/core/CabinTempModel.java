package ai.cabinguard.app.core;

/**
 * 주차 중 차량 실내 온도 예측 모델 (1단계: 규칙 기반).
 *
 * 근거: McLaren, Null, Quinn. "Heat Stress From Enclosed Vehicles", Pediatrics 2005;116:e109.
 *  - 맑은 날 밀폐 차량의 실내 온도는 60분에 평균 약 40°F(22℃) 상승
 *  - 상승분의 80%가 처음 30분에 발생, 외기온도와 무관하게 상승 속도는 비슷
 * 이를 포화형 곡선  상승폭(t) = 최대상승폭 × (1 − e^(−t/τ))  로 옮겼다.
 * 30분에 80%가 되려면 τ = 30 / ln(5) ≈ 18.6분.
 *
 * calibrationOffsetC 는 재탑승 시 실측한 실내 온도와 예측값의 차이로 갱신하는 차량별 보정값이다.
 */
public final class CabinTempModel {

    /** 30분에 최대 상승폭의 80%에 도달하는 시정수(분). */
    public static final double TAU_MIN = 30.0 / Math.log(5.0);

    private CabinTempModel() {}

    private static double saturation(int parkedMinutes) {
        if (parkedMinutes <= 0) return 0;
        return 1.0 - Math.exp(-parkedMinutes / TAU_MIN);
    }

    /** 주차 중 실내 공기의 예상 최고온도(℃). */
    public static double predictCabinAirC(double outsideC, Sky sky, int parkedMinutes, double calibrationOffsetC) {
        return outsideC + sky.maxRiseC * saturation(parkedMinutes) + calibrationOffsetC;
    }

    /** 물품이 놓인 자리의 예상 최고온도(℃). 직사광선 자리(대시보드 등)는 실내 공기보다 더 뜨겁다. */
    public static double predictZoneC(double outsideC, Sky sky, int parkedMinutes, boolean directSun, double calibrationOffsetC) {
        double air = predictCabinAirC(outsideC, sky, parkedMinutes, calibrationOffsetC);
        return directSun ? air + sky.directSunExtraC * saturation(parkedMinutes) : air;
    }

    /** 실측값으로 보정값 갱신. 한 번의 측정에 과하게 끌려가지 않도록 지수이동평균을 쓴다. */
    public static double updateCalibration(double currentOffsetC, double predictedAirC, double measuredAirC) {
        double error = measuredAirC - predictedAirC;
        double next = currentOffsetC + 0.3 * error;
        return Math.max(-10, Math.min(10, next));
    }
}
