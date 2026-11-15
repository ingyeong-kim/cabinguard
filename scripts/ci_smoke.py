#!/usr/bin/env python3
"""에뮬레이터에서 앱을 설치하고 핵심 흐름(시나리오 1)을 실제로 눌러 보며 화면을 저장한다.

사용법: python3 scripts/ci_smoke.py <apk 경로> <결과 폴더>
화면의 글자를 uiautomator로 찾아 누르므로 해상도와 무관하게 동작한다.
"""
import os
import re
import subprocess
import sys
import time

PKG = "ai.cabinguard.app"
apk, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
log = []


def adb(*args, check=True, binary=False):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=120)
    if check and r.returncode != 0:
        raise RuntimeError("adb %s 실패: %s" % (args, r.stderr.decode(errors="replace")))
    return r.stdout if binary else r.stdout.decode(errors="replace")


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    return adb("shell", "cat", "/sdcard/ui.xml", check=False)


def texts():
    return re.findall(r'text="([^"]*)"', dump())


def tap(label):
    xml = dump()
    for node in re.findall(r"<node [^>]*>", xml):
        t = re.search(r'text="([^"]*)"', node)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if t and b and label in t.group(1):
            x1, y1, x2, y2 = map(int, b.groups())
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(1.2)
            return
    raise RuntimeError("화면에서 '%s' 를 찾지 못함" % label)


def shot(name):
    png = adb("exec-out", "screencap", "-p", binary=True)
    with open(os.path.join(out, name + ".png"), "wb") as f:
        f.write(png)


def expect(label, step):
    ok = any(label in t for t in texts())
    log.append("%s  %s  (기대 문구: %s)" % ("PASS" if ok else "FAIL", step, label))
    return ok


failed = False
try:
    adb("install", "-r", apk)
    adb("shell", "pm", "clear", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", PKG + "/.MainActivity")
    time.sleep(4)
    failed |= not expect("캐빈가드", "앱 실행")
    shot("01_start")

    tap("+ 보조배터리")
    tap("+ 스프레이")
    tap("그늘 자리")  # 첫 번째 물품(보조배터리)을 직사광선 자리로 변경
    failed |= not expect("리튬이온 배터리", "물품 등록과 자동 분류")
    shot("02_items_registered")

    tap("기어 D")
    failed |= not expect("주행 중", "주행 중 입력 잠금")
    failed |= not expect("주차 위험 예측: 위험", "주차 위험 예측")
    shot("03_driving_prediction")

    tap("기어 P")
    tap("주차브레이크 걸기")
    failed |= not expect("들고 내려 주세요", "P단 + 주차브레이크에서 하차 알림")
    shot("04_exit_alert")

    tap("스프레이   [")
    # 알림이 왼쪽 패널을 가리지 않으므로 문 버튼을 그대로 누를 수 있다
    tap("운전석 문 열기")
    failed |= not expect("아직 남아 있어요", "문 열림 시 재알림")
    shot("05_door_reminder")

    tap("보조배터리   [")
    failed |= not expect("모두 챙겼어요", "전부 확인 후 알림 종료")
    shot("06_all_checked")

    tap("주차 후 재탑승")
    failed |= not expect("예측 보정", "재탑승 가이드와 예측 보정")
    shot("07_reboard_guide")
    tap("확인했어요")

    # 시나리오 3: 흐린 날 짧은 정차는 알림 없음
    for _ in range(6):
        tap("외기 −2℃")
    tap("하늘상태 변경")
    tap("하늘상태 변경")
    tap("주차시간 변경")
    tap("주차시간 변경")
    tap("기어 D")
    tap("기어 P")
    tap("주차브레이크 걸기")
    failed |= not expect("알림을 생략", "흐린 날 짧은 정차는 알림 생략")
    shot("08_safe_no_alert")
except Exception as e:  # noqa: BLE001
    failed = True
    log.append("ERROR  %s" % e)
    try:
        shot("99_error")
    except Exception:  # noqa: BLE001
        pass

crash = adb("logcat", "-d", "-s", "AndroidRuntime:E", check=False)
if "FATAL EXCEPTION" in crash:
    failed = True
    log.append("ERROR  앱 비정상 종료\n" + crash[-3000:])

log.append("RESULT  " + ("FAIL" if failed else "PASS"))
with open(os.path.join(out, "smoke-test.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(log) + "\n")
print("\n".join(log))
sys.exit(1 if failed else 0)
