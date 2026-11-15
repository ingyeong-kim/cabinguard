# 캐빈가드 (CabinGuard)

주차 직전 외기온도, 날씨, 예상 주차시간으로 차량 실내 온도를 예측해, 차 안에 두면 위험한 보조배터리, 스프레이, 라이터 등을 하차하는 순간 음성과 화면으로 챙겨 주는 차량용 앱입니다.

현대자동차그룹 차량용 앱 개발 공모전 1차 제출용 프로토타입입니다.

## APK 파일 위치

`apk/CabinGuard_캐빈가드_release.apk` (GitHub Actions가 release 빌드 후 자동으로 올립니다)

## 실행 방법

1. APK 설치: `adb install -r apk/CabinGuard_캐빈가드_release.apk`
2. 앱 목록에서 **캐빈가드** 실행 (가로 화면)
3. 아래 순서로 누르면 핵심 흐름을 볼 수 있습니다.

| 순서 | 조작 | 확인할 동작 |
|---|---|---|
| 1 | `+ 보조배터리`, `+ 스프레이` 누르기 | 물품이 위험 카테고리로 자동 분류됨 |
| 2 | 보조배터리의 `그늘 자리`를 눌러 `직사광선 자리`로 변경 | 자리별 예상 온도가 달라짐 |
| 3 | `기어 D (출발)` | 주행 중에는 등록 UI가 잠기고 주차 위험만 미리 계산함 |
| 4 | `기어 P` → `주차브레이크 걸기/풀기` | 하차 알림이 뜨고 위험한 물품만 체크리스트로 나옴 (음성 안내) |
| 5 | 스프레이만 체크하고 `운전석 문 열기/닫기` | 남은 물품을 한 번 더 알림 |
| 6 | 보조배터리 체크 | 알림 종료 |
| 7 | `주차 후 재탑승` | 열기 배출 가이드와 차량별 예측 보정 |
| 8 | 외기온도를 22℃로 낮추고 하늘상태 `흐림`, 주차시간 `10분`으로 바꾼 뒤 4번 반복 | 위험하지 않으므로 알림을 생략함 |

소스에서 직접 빌드하려면 JDK 17에서 `./gradlew assembleRelease`를 실행합니다.

## 테스트 환경 및 필요한 설정

- minSdk 28, targetSdk 34, Gradle 8.7, Android Gradle Plugin 8.5.2, Java
- 별도 계정이나 API 키가 필요 없습니다. 외부 라이브러리 의존성도 없습니다.
- **차량 신호 연결**: Android Automotive OS 기기에서는 AAOS Car API(`CarPropertyManager`)로 기어, 주차브레이크, 시동, 외기온도를 실제로 구독합니다. 차량 기능이 없는 기기(일반 에뮬레이터, 태블릿)에서는 자동으로 시뮬레이션 모드가 되어 화면 왼쪽 버튼으로 같은 신호를 넣습니다. 화면 왼쪽 위에 현재 모드가 표시됩니다.
- 음성 안내는 기기의 한국어 TTS 엔진을 사용합니다. 엔진이 없으면 같은 문장을 화면에만 표시합니다.
- 필요한 권한: `android.car.permission.CAR_POWERTRAIN`, `android.car.permission.CAR_EXTERIOR_ENVIRONMENT` (설치 시 자동 부여되는 일반 권한)

### 검증 범위 (1차 제출 시점)

- 위험 판정 로직: 단위 테스트 6건 (`app/src/test`), 빌드할 때마다 자동 실행
- 앱 실행: GitHub Actions의 Android 14(API 34) 에뮬레이터에서 위 1~8번 흐름을 자동으로 눌러 보고 화면을 저장합니다. 결과는 `docs/ci/smoke-test.txt`, 화면은 `docs/ci/*.png`에 있습니다.
- Pleos Connect Emulator 2.1.0에서의 실행 검증과 Pleos Connect SDK 연동은 2차 개발 범위입니다.

## 개발 대상 Pleos Connect 에뮬레이터 버전과 화면 규격

- 대상: Pleos Connect Emulator 2.1.0 (Android API 34 시스템 이미지, Automotive)
- 화면: 가로 고정. 비율 기반 레이아웃(왼쪽 3 : 오른쪽 7)이라 해상도가 달라져도 배치가 유지됩니다. Pleos Connect 개발 장비 규격(17인치, 2,560 x 1,440)을 기준으로 설계했습니다.

## 구조

```
app/src/main/java/ai/cabinguard/app/
├── MainActivity.java          화면과 흐름(등록 → 예측 → 하차 알림 → 재알림 → 재탑승)
├── core/
│   ├── CabinTempModel.java    주차 중 실내 온도 예측과 차량별 보정
│   ├── RiskEngine.java        물품별 안전·주의·위험 판정
│   ├── HazardCategory.java    위험 카테고리와 기준온도
│   ├── ItemClassifier.java    물품명과 발화에서 카테고리 분류
│   ├── Sky.java               하늘상태별 상승폭
│   └── StoredItem.java
└── signal/
    ├── VehicleSignals.java    차량 신호 상태(출처와 무관하게 같은 인터페이스)
    └── CarApiSource.java      AAOS Car API 구독
```

## 실내 온도 예측 방식

맑은 날 밀폐 차량의 실내 온도는 60분에 평균 약 22℃(40°F) 오르고, 그중 80%가 처음 30분에 오른다는 측정 연구(McLaren, Null, Quinn, *Pediatrics* 2005;116:e109)를 포화형 곡선으로 옮겼습니다.

```
실내 공기 예상온도 = 외기온도 + 최대상승폭(하늘상태) × (1 − e^(−주차시간/18.6분)) + 차량별 보정값
```

대시보드처럼 직사광선을 받는 자리는 추가 상승폭을 더합니다. 물품별 기준온도는 제품 표시사항(가압가스 용기 40℃ 이상 보관 금지 등)에서 가져온 초기값입니다. 재탑승 때 실측한 실내 온도와 예측값의 차이로 차량별 보정값을 갱신합니다.

## 1차 프로토타입에 구현한 것과 2차 개발 계획

| 기능 | 1차 프로토타입 | 2차 개발 (PT 심사 전) |
|---|---|---|
| 기어, 주차브레이크, 시동, 외기온도 | AAOS Car API 구독 구현 | Pleos Connect Emulator에서 검증 |
| 운전석 문 열림 | 시뮬레이션 버튼 | Pleos Vehicle SDK `Door` |
| 실내 온도 실측, 냉방 설정 | 시뮬레이션 값 | Pleos Vehicle SDK `HVAC` |
| 예상 주차시간 | 직접 선택 | Pleos NaviHelper SDK 목적지 정보 |
| 하늘상태 | 직접 선택 | 기상청 단기예보 API |
| 음성 등록, 음성 안내 | Android 표준 음성 인식과 TTS | Pleos SpeechToText SDK, TextToSpeech SDK |
| 물품 분류 | 키워드 사전 | 사전에 없는 물품은 Gleo AI LLM으로 보강 |
