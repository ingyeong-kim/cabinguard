package ai.cabinguard.app.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 자유롭게 말하거나 입력한 물품명을 위험 카테고리로 분류한다.
 * 1차 프로토타입은 키워드 사전으로 분류한다. 사전에 없는 물품은 2차 개발에서
 * Pleos Gleo AI SDK의 LLM으로 분류를 보강할 계획이다(사전 우선, LLM 보조).
 */
public final class ItemClassifier {

    private static final String[][] DICTIONARY = {
            // {카테고리, 키워드...}
            {"LITHIUM_BATTERY", "보조배터리", "보조 배터리", "배터리", "전자담배", "이어폰", "노트북", "태블릿", "휴대폰", "핸드폰", "스마트폰", "손선풍기", "블랙박스 보조"},
            {"PRESSURIZED_GAS", "스프레이", "부탄", "가스", "라이터", "방향제", "탈취제", "소화기", "살충제", "모기", "선스프레이", "헤어", "쿨링", "에어로졸"},
            {"CARBONATED_DRINK", "탄산", "콜라", "사이다", "맥주", "캔음료", "에너지드링크", "스파클링"},
            {"MEDICINE", "상비약", "감기약", "두통약", "멀미약", "약통", "연고", "인슐린", "영양제", "안약"},
    };

    private ItemClassifier() {}

    public static HazardCategory classify(String rawName) {
        if (rawName == null) return HazardCategory.OTHER;
        String name = rawName.replace(" ", "").toLowerCase();
        for (String[] row : DICTIONARY) {
            for (int i = 1; i < row.length; i++) {
                if (name.contains(row[i].replace(" ", ""))) {
                    return HazardCategory.valueOf(row[0]);
                }
            }
        }
        return HazardCategory.OTHER;
    }

    /**
     * "보조배터리랑 스프레이 차에 둘게" 같은 발화에서 물품을 뽑아낸다.
     * 사전의 키워드가 문장에 들어 있으면 그 키워드를 물품명으로 등록한다.
     */
    public static List<StoredItem> extractFromUtterance(String utterance) {
        List<StoredItem> found = new ArrayList<>();
        if (utterance == null) return found;
        String text = utterance.replace(" ", "");
        List<HazardCategory> seen = new ArrayList<>();
        for (String[] row : DICTIONARY) {
            HazardCategory category = HazardCategory.valueOf(row[0]);
            for (int i = 1; i < row.length; i++) {
                String keyword = row[i].replace(" ", "");
                if (text.contains(keyword) && !seen.contains(category)) {
                    found.add(new StoredItem(row[i], category, false));
                    seen.add(category);
                    break;
                }
            }
        }
        return found;
    }
}
