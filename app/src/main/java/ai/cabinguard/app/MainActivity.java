package ai.cabinguard.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.cabinguard.app.core.CabinTempModel;
import ai.cabinguard.app.core.HazardCategory;
import ai.cabinguard.app.core.ItemClassifier;
import ai.cabinguard.app.core.RiskEngine;
import ai.cabinguard.app.core.Sky;
import ai.cabinguard.app.core.StoredItem;
import ai.cabinguard.app.signal.CarApiSource;
import ai.cabinguard.app.signal.VehicleSignals;

/**
 * 캐빈가드 메인 화면.
 *
 * 흐름: 물품 등록(P단 정차 중) → 주행 중 주차 위험 예측 → P단 + 주차브레이크 시 하차 알림
 *      → 운전석 문 열림 시 재알림 → 재탑승 시 열기 배출 가이드와 예측 보정.
 */
public class MainActivity extends Activity implements VehicleSignals.Listener {

    private static final int REQUEST_VOICE = 1001;
    private static final int[] PARK_MINUTES = {10, 30, 90, 720};
    private static final String[] PARK_LABELS = {"10분", "30분", "1시간 30분", "하룻밤"};
    private static final String[] QUICK_ITEMS = {"보조배터리", "스프레이", "라이터", "부탄가스", "탄산음료", "상비약"};
    private static final String PREFS = "cabinguard";

    private final VehicleSignals signals = new VehicleSignals();
    private final List<StoredItem> items = new ArrayList<>();
    private final List<CheckBox> alertChecks = new ArrayList<>();
    private CarApiSource carApiSource;
    private boolean carApiConnected;
    private TextToSpeech tts;
    private boolean ttsReady;

    private Sky sky = Sky.CLEAR;
    private int parkIndex = 2;
    private double calibrationOffsetC;

    private boolean wasParked = true;
    private boolean wasDoorOpen;
    private boolean alertActive;
    private boolean hasParkPrediction;
    private double lastPredictedAirC;

    private TextView tvSource, tvIgn, tvGear, tvPb, tvDoor, tvOut, tvSky, tvPark;
    private TextView tvStatus, tvPredTitle, tvPredDetail, tvLock, tvVoice;
    private TextView tvAlertTitle, tvAlertSub, tvGuideTitle, tvGuideBody;
    private EditText etItem;
    private Button btnAdd, btnVoice;
    private LinearLayout chips, itemList, alertList;
    private View alertOverlay, guideOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        loadState();
        signals.setListener(this);
        connectVehicle();
        setupTts();
        setupControls();
        refreshUi();
    }

    // ------------------------------------------------------------------ 초기화

    private void bindViews() {
        tvSource = findViewById(R.id.tv_source);
        tvIgn = findViewById(R.id.tv_ign);
        tvGear = findViewById(R.id.tv_gear);
        tvPb = findViewById(R.id.tv_pb);
        tvDoor = findViewById(R.id.tv_door);
        tvOut = findViewById(R.id.tv_out);
        tvSky = findViewById(R.id.tv_sky);
        tvPark = findViewById(R.id.tv_park);
        tvStatus = findViewById(R.id.tv_status);
        tvPredTitle = findViewById(R.id.tv_pred_title);
        tvPredDetail = findViewById(R.id.tv_pred_detail);
        tvLock = findViewById(R.id.tv_lock);
        tvVoice = findViewById(R.id.tv_voice);
        tvAlertTitle = findViewById(R.id.tv_alert_title);
        tvAlertSub = findViewById(R.id.tv_alert_sub);
        tvGuideTitle = findViewById(R.id.tv_guide_title);
        tvGuideBody = findViewById(R.id.tv_guide_body);
        etItem = findViewById(R.id.et_item);
        btnAdd = findViewById(R.id.btn_add);
        btnVoice = findViewById(R.id.btn_voice);
        chips = findViewById(R.id.chips);
        itemList = findViewById(R.id.item_list);
        alertList = findViewById(R.id.alert_list);
        alertOverlay = findViewById(R.id.alert_overlay);
        guideOverlay = findViewById(R.id.guide_overlay);
    }

    /** 실제 차량(AAOS)이면 Car API로 신호를 받고, 아니면 시뮬레이션 모드로 동작한다. */
    private void connectVehicle() {
        boolean automotive = getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        if (automotive) {
            try {
                carApiSource = new CarApiSource(signals);
                carApiConnected = carApiSource.connect(this);
            } catch (Throwable t) {
                carApiConnected = false;
            }
        }
        tvSource.setText(carApiConnected
                ? "AAOS Car API 연결됨 (버튼으로도 조작 가능)"
                : "시뮬레이션 모드 (아래 버튼으로 조작)");
    }

    private void setupTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        int result = tts.setLanguage(Locale.KOREAN);
                        ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                                && result != TextToSpeech.LANG_NOT_SUPPORTED;
                    }
                }
            });
        } catch (Throwable t) {
            ttsReady = false;
        }
    }

    private void setupControls() {
        click(R.id.btn_gear_d, new Runnable() { public void run() {
            signals.setParkingBrakeOn(false);
            signals.setDriverDoorOpen(false);
            signals.setIgnitionOn(true);
            signals.setGearPark(false);
        }});
        click(R.id.btn_gear_p, new Runnable() { public void run() { signals.setGearPark(true); }});
        click(R.id.btn_pb, new Runnable() { public void run() { signals.setParkingBrakeOn(!signals.isParkingBrakeOn()); }});
        click(R.id.btn_door, new Runnable() { public void run() { signals.setDriverDoorOpen(!signals.isDriverDoorOpen()); }});
        click(R.id.btn_temp_down, new Runnable() { public void run() { signals.setOutsideTempC(signals.getOutsideTempC() - 2); }});
        click(R.id.btn_temp_up, new Runnable() { public void run() { signals.setOutsideTempC(signals.getOutsideTempC() + 2); }});
        click(R.id.btn_sky, new Runnable() { public void run() {
            sky = Sky.values()[(sky.ordinal() + 1) % Sky.values().length];
            refreshUi();
        }});
        click(R.id.btn_park_time, new Runnable() { public void run() {
            parkIndex = (parkIndex + 1) % PARK_MINUTES.length;
            refreshUi();
        }});
        click(R.id.btn_reboard, new Runnable() { public void run() { onReboard(); }});
        click(R.id.btn_add, new Runnable() { public void run() { addFromInput(); }});
        click(R.id.btn_voice, new Runnable() { public void run() { startVoiceInput(); }});
        click(R.id.btn_alert_skip, new Runnable() { public void run() { closeAlert("알림을 닫았어요"); }});
        click(R.id.btn_guide_ok, new Runnable() { public void run() { guideOverlay.setVisibility(View.GONE); }});

        etItem.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    addFromInput();
                    return true;
                }
                return false;
            }
        });

        for (final String name : QUICK_ITEMS) {
            Button chip = new Button(this);
            chip.setText("+ " + name);
            chip.setTextSize(13);
            chip.setTextColor(getColor(R.color.text));
            chip.setBackgroundResource(R.drawable.bg_button);
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
            lp.setMarginEnd(dp(6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addItem(name);
                }
            });
            chips.addView(chip);
        }
    }

    private void click(int id, final Runnable action) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
    }

    // ------------------------------------------------------------------ 차량 신호 처리

    @Override
    public void onSignalsChanged(VehicleSignals s) {
        boolean parked = s.isParked();
        boolean doorOpen = s.isDriverDoorOpen();
        if (parked && !wasParked) onParked();
        if (!parked && wasParked) onDeparted();
        if (doorOpen && !wasDoorOpen) onDriverDoorOpened();
        wasParked = parked;
        wasDoorOpen = doorOpen;
        refreshUi();
    }

    /** P단 + 주차브레이크: 하차 직전. 위험한 물품만 골라 알린다. */
    private void onParked() {
        List<RiskEngine.Assessment> all = assessNow();
        List<RiskEngine.Assessment> targets = RiskEngine.alertTargets(all);
        lastPredictedAirC = predictedAirNow();
        hasParkPrediction = true;
        if (targets.isEmpty()) {
            say(null);
            tvStatus.setText("주차 감지 · 위험한 물품이 없어 알림을 생략했어요");
            return;
        }
        showAlert(targets);
        List<String> names = new ArrayList<>();
        for (RiskEngine.Assessment a : targets) names.add(a.item.name);
        say(PARK_LABELS[parkIndex] + " 주차 예상입니다. " + joinNames(names) + ", 들고 내려 주세요.");
    }

    private void onDeparted() {
        if (alertActive) closeAlert("주행을 시작해 알림을 닫았어요");
    }

    /** 체크하지 않은 물품이 남은 채 운전석 문이 열리면 한 번 더 알린다. */
    private void onDriverDoorOpened() {
        if (!alertActive) return;
        List<String> remaining = new ArrayList<>();
        for (CheckBox box : alertChecks) {
            if (!box.isChecked()) remaining.add(String.valueOf(box.getTag()));
        }
        if (remaining.isEmpty()) return;
        tvAlertTitle.setText("아직 남아 있어요");
        tvAlertSub.setText("운전석 문 열림 감지 · 확인하지 않은 물품 " + remaining.size() + "개");
        say(joinNames(remaining) + ", 아직 남아 있어요.");
    }

    /** 주차 후 재탑승. 실내 온도 실측값으로 열기 배출을 안내하고 예측을 보정한다. */
    private void onReboard() {
        if (!hasParkPrediction) {
            Toast.makeText(this, "먼저 주차(기어 P + 주차브레이크)를 한 번 진행해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (alertActive) closeAlert("");
        signals.setDriverDoorOpen(false);
        signals.setIgnitionOn(true);
        // 시뮬레이션에서는 예측보다 2℃ 높게 측정된 상황을 가정한다.
        // 실제 차량에서는 Pleos Vehicle SDK(HVAC)의 실내 온도를 사용한다(2차 개발).
        double measured = lastPredictedAirC + 2;
        double before = calibrationOffsetC;
        calibrationOffsetC = CabinTempModel.updateCalibration(before, lastPredictedAirC, measured);
        saveState();

        StringBuilder body = new StringBuilder();
        if (measured >= 40) {
            tvGuideTitle.setText("실내가 뜨거워요 · 실측 " + fmt(measured) + "℃");
            body.append("1. 조수석 창문을 열어 주세요\n");
            body.append("2. 운전석 문을 3~4회 여닫아 주세요\n");
            body.append("3. 창문을 닫고 냉방을 시작해요\n\n");
            say("실내가 " + fmt(measured) + "도예요. 조수석 창문을 열고 운전석 문을 몇 번 여닫아 주세요.");
        } else {
            tvGuideTitle.setText("실내 온도 " + fmt(measured) + "℃");
            body.append("열기 배출이 필요하지 않은 온도예요.\n\n");
            say(null);
        }
        body.append("예측 보정: 주차 당시 예측 ").append(fmt(lastPredictedAirC)).append("℃, 탑승 실측 ")
                .append(fmt(measured)).append("℃\n이 차량의 보정값 ")
                .append(String.format(Locale.KOREA, "%+.1f", before)).append("℃ → ")
                .append(String.format(Locale.KOREA, "%+.1f", calibrationOffsetC)).append("℃");
        tvGuideBody.setText(body.toString());
        guideOverlay.setVisibility(View.VISIBLE);
        hasParkPrediction = false;
        refreshUi();
    }

    // ------------------------------------------------------------------ 하차 알림

    private void showAlert(List<RiskEngine.Assessment> targets) {
        alertChecks.clear();
        alertList.removeAllViews();
        tvAlertTitle.setText("들고 내려 주세요");
        tvAlertSub.setText("외기 " + fmt(signals.getOutsideTempC()) + "℃ · " + sky.label + " · "
                + PARK_LABELS[parkIndex] + " 주차 예상 → 실내 약 " + fmt(predictedAirNow()) + "℃ 예상");
        for (RiskEngine.Assessment a : targets) {
            CheckBox box = new CheckBox(this);
            box.setTag(a.item.name);
            box.setText(a.item.name + "   [" + a.level.label + " · 이 자리 약 " + fmt(a.zoneTempC) + "℃]   "
                    + a.item.category.reason);
            box.setTextSize(19);
            box.setTextColor(getColor(R.color.text));
            box.setBackgroundResource(R.drawable.bg_card);
            box.setPadding(dp(8), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            box.setLayoutParams(lp);
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    onAlertChecked();
                }
            });
            alertChecks.add(box);
            alertList.addView(box);
        }
        alertActive = true;
        alertOverlay.setVisibility(View.VISIBLE);
    }

    private void onAlertChecked() {
        for (CheckBox box : alertChecks) {
            if (!box.isChecked()) return;
        }
        closeAlert("모두 챙겼어요 · 안전하게 다녀오세요");
        say("모두 챙기셨네요. 안전하게 다녀오세요.");
    }

    private void closeAlert(String status) {
        alertActive = false;
        alertOverlay.setVisibility(View.GONE);
        tvStatus.setText(status);
    }

    // ------------------------------------------------------------------ 물품 등록

    private void addFromInput() {
        String name = etItem.getText().toString().trim();
        if (name.isEmpty()) return;
        addItem(name);
        etItem.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etItem.getWindowToken(), 0);
    }

    private void addItem(String name) {
        for (StoredItem existing : items) {
            if (existing.name.equals(name)) return;
        }
        items.add(new StoredItem(name, ItemClassifier.classify(name), false));
        saveState();
        refreshUi();
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "차에 둘 물품을 말해 주세요");
        try {
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "이 기기에는 음성 인식 서비스가 없어요. 입력창이나 버튼으로 등록해 주세요",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VOICE || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        List<StoredItem> found = ItemClassifier.extractFromUtterance(results.get(0));
        if (found.isEmpty()) {
            say("등록할 물품을 찾지 못했어요.");
            return;
        }
        List<String> names = new ArrayList<>();
        for (StoredItem item : found) {
            addItem(item.name);
            names.add(item.name);
        }
        say(joinNames(names) + ", 등록했어요.");
    }

    // ------------------------------------------------------------------ 화면 갱신

    private List<RiskEngine.Assessment> assessNow() {
        return RiskEngine.assess(items, signals.getOutsideTempC(), sky, PARK_MINUTES[parkIndex], calibrationOffsetC);
    }

    private double predictedAirNow() {
        return CabinTempModel.predictCabinAirC(signals.getOutsideTempC(), sky, PARK_MINUTES[parkIndex], calibrationOffsetC);
    }

    private void refreshUi() {
        tvIgn.setText(signals.isIgnitionOn() ? "ON" : "OFF");
        tvGear.setText(signals.isGearPark() ? "P" : "D");
        tvPb.setText(signals.isParkingBrakeOn() ? "체결" : "해제");
        tvDoor.setText(signals.isDriverDoorOpen() ? "열림" : "닫힘");
        tvOut.setText(fmt(signals.getOutsideTempC()) + "℃");
        tvSky.setText(sky.label);
        tvPark.setText(PARK_LABELS[parkIndex]);

        // 운전자 주의 분산 방지: 주행 중(P단이 아닐 때)에는 입력 UI를 잠근다.
        boolean editable = signals.isGearPark();
        etItem.setEnabled(editable);
        btnAdd.setEnabled(editable);
        btnVoice.setEnabled(editable);
        for (int i = 0; i < chips.getChildCount(); i++) chips.getChildAt(i).setEnabled(editable);
        chips.setAlpha(editable ? 1f : 0.4f);
        tvLock.setText(editable
                ? "말하거나 입력해서 등록하면 위험 카테고리로 자동 분류합니다"
                : "주행 중에는 등록과 수정을 잠급니다 (P단에서 가능)");
        if (!alertActive && !signals.isGearPark()) tvStatus.setText("주행 중 · 화면 알림 꺼짐");

        List<RiskEngine.Assessment> all = assessNow();
        RiskEngine.Level worst = RiskEngine.worst(all);
        int color = worst == RiskEngine.Level.DANGER ? R.color.danger
                : worst == RiskEngine.Level.CAUTION ? R.color.caution : R.color.safe;
        tvPredTitle.setTextColor(getColor(color));
        tvPredTitle.setText("주차 위험 예측: " + worst.label);
        tvPredDetail.setText("외기 " + fmt(signals.getOutsideTempC()) + "℃ · " + sky.label + " · "
                + PARK_LABELS[parkIndex] + " 주차 → 실내 공기 약 " + fmt(predictedAirNow()) + "℃, 직사광선 자리 약 "
                + fmt(CabinTempModel.predictZoneC(signals.getOutsideTempC(), sky, PARK_MINUTES[parkIndex], true, calibrationOffsetC))
                + "℃ 예상\n차량별 보정값 " + String.format(Locale.KOREA, "%+.1f", calibrationOffsetC) + "℃");

        itemList.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("등록한 물품이 없어요");
            empty.setTextColor(getColor(R.color.sub));
            empty.setTextSize(15);
            itemList.addView(empty);
        }
        for (int i = 0; i < all.size(); i++) {
            itemList.addView(buildItemRow(i, all.get(i), editable));
        }
    }

    private View buildItemRow(final int index, RiskEngine.Assessment a, boolean editable) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(14), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(6);
        row.setLayoutParams(rowLp);

        TextView name = new TextView(this);
        name.setText(a.item.name + "\n" + a.item.category.label);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(16);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        TextView level = new TextView(this);
        level.setText(a.item.category == HazardCategory.OTHER
                ? "판정 제외" : a.level.label + " · 약 " + fmt(a.zoneTempC) + "℃");
        level.setTextSize(16);
        level.setTextColor(getColor(a.level == RiskEngine.Level.DANGER ? R.color.danger
                : a.level == RiskEngine.Level.CAUTION ? R.color.caution : R.color.safe));
        level.setPadding(dp(8), 0, dp(12), 0);
        row.addView(level);

        Button zone = new Button(this);
        zone.setText(a.item.directSun ? "직사광선 자리" : "그늘 자리");
        zone.setTextSize(13);
        zone.setTextColor(getColor(R.color.text));
        zone.setBackgroundResource(R.drawable.bg_button);
        zone.setEnabled(editable);
        zone.setLayoutParams(new LinearLayout.LayoutParams(dp(120), dp(40)));
        zone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StoredItem old = items.get(index);
                items.set(index, old.withDirectSun(!old.directSun));
                saveState();
                refreshUi();
            }
        });
        row.addView(zone);

        Button remove = new Button(this);
        remove.setText("삭제");
        remove.setTextSize(13);
        remove.setTextColor(getColor(R.color.sub));
        remove.setBackgroundResource(R.drawable.bg_button);
        remove.setEnabled(editable);
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(64), dp(40));
        removeLp.setMarginStart(dp(6));
        remove.setLayoutParams(removeLp);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                items.remove(index);
                saveState();
                refreshUi();
            }
        });
        row.addView(remove);
        return row;
    }

    // ------------------------------------------------------------------ 음성 안내, 저장, 유틸

    /** 음성 안내. 화면에도 같은 문장을 보여 준다. null이면 안내 영역을 숨긴다. */
    private void say(String text) {
        if (text == null) {
            tvVoice.setVisibility(View.GONE);
            return;
        }
        tvVoice.setText("음성 안내: \"" + text + "\"");
        tvVoice.setVisibility(View.VISIBLE);
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cabinguard");
        }
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        calibrationOffsetC = prefs.getFloat("calibration", 0f);
        String saved = prefs.getString("items", "");
        items.clear();
        for (String line : saved.split("\n")) {
            String[] parts = line.split("\\|");
            if (parts.length != 3) continue;
            try {
                items.add(new StoredItem(parts[0], HazardCategory.valueOf(parts[1]), "1".equals(parts[2])));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveState() {
        StringBuilder sb = new StringBuilder();
        for (StoredItem item : items) {
            sb.append(item.name.replace("|", " ").replace("\n", " ")).append('|')
                    .append(item.category.name()).append('|')
                    .append(item.directSun ? "1" : "0").append('\n');
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("items", sb.toString())
                .putFloat("calibration", (float) calibrationOffsetC)
                .apply();
    }

    private static String joinNames(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    private static String fmt(double value) {
        return String.valueOf(Math.round(value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (carApiSource != null) carApiSource.disconnect();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
