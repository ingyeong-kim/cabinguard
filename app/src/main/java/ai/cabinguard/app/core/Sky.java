package ai.cabinguard.app.core;

/**
 * 주차 시간대의 하늘 상태. 기상청 단기예보의 하늘상태(SKY) 코드에 대응한다.
 * maxRiseC: 밀폐 차량 실내 공기가 외기보다 최대 몇 도까지 오르는지(초기값).
 * directSunExtraC: 대시보드처럼 직사광선을 받는 자리의 추가 상승폭(초기값).
 */
public enum Sky {
    /** 맑음. McLaren 외(Pediatrics 2005): 맑은 날 60분에 평균 약 40°F(22℃) 상승. */
    CLEAR("맑음", 22, 20),
    PARTLY_CLOUDY("구름많음", 13, 10),
    OVERCAST("흐림", 6, 2),
    /** 야간 또는 지하주차장. */
    NO_SUN("야간·지하", 1, 0);

    public final String label;
    public final double maxRiseC;
    public final double directSunExtraC;

    Sky(String label, double maxRiseC, double directSunExtraC) {
        this.label = label;
        this.maxRiseC = maxRiseC;
        this.directSunExtraC = directSunExtraC;
    }
}
