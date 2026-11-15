package ai.cabinguard.app.core;

/**
 * 차량에 두고 내리는 물품의 위험 카테고리.
 * cautionC / dangerC 는 물품이 놓인 자리의 예상 온도(섭씨)와 비교하는 기준값이다.
 * 제품 표시사항과 일반적인 보관 조건에서 가져온 초기값이며, 2차 개발에서 물품별로 세분화한다.
 */
public enum HazardCategory {
    /** 스프레이, 부탄가스, 라이터. 제품 표시: "40℃ 이상 되는 장소에 보관하지 말 것". */
    PRESSURIZED_GAS("가압가스 용기", 35, 40, "온도가 오르면 내부 압력이 높아져 파열 위험"),
    /** 보조배터리, 전자담배, 무선 이어폰. 일반적인 보관 상한 45℃, 60℃ 이상 노출 금지. */
    LITHIUM_BATTERY("리튬이온 배터리", 45, 60, "고온에서 전해질이 분해되어 발화 위험"),
    /** 탄산음료 캔과 병. */
    CARBONATED_DRINK("탄산음료", 40, 50, "내부 압력 상승으로 용기 파열 위험"),
    /** 상비약. 실온(1~30℃) 보관 기준. 안전 사고가 아닌 품질 문제라 최대 '주의'까지만 판정한다. */
    MEDICINE("의약품", 30, Double.NaN, "고온에서 변질되어 효능 저하"),
    /** 분류되지 않은 물품. 판정하지 않는다. */
    OTHER("기타", Double.NaN, Double.NaN, "");

    public final String label;
    public final double cautionC;
    public final double dangerC;
    public final String reason;

    HazardCategory(String label, double cautionC, double dangerC, String reason) {
        this.label = label;
        this.cautionC = cautionC;
        this.dangerC = dangerC;
        this.reason = reason;
    }
}
