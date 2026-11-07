#!/usr/bin/env python3
"""Miteru 再生スモークテスト（エミュレータ + 実 KonomiTV サーバー）。

3 つの再生パス (ライブ / 録画 / 任意でオフライン開始) を起動し、
`adb shell dumpsys media_session` の state と位置前進で合否を判定する。
サーバーログの確認 (--server-ssh) を付けると、ライブの ONAir と
keep-alive 204 連続 / 422 ゼロ も検証する。

前提:
  - エミュレータが起動済み (`adb devices` で 1 台)
  - アプリがインストール済み & サーバー設定済み (or --fresh でセットアップから実行)

使い方:
  python3 tools/smoke-test.py                      # 設定済みインストールに対して実行
  python3 tools/smoke-test.py --apk <apk>          # APK を入れ直してから実行
  python3 tools/smoke-test.py --fresh              # アプリデータを消して初期セットアップから
  python3 tools/smoke-test.py --server-ssh root@10.77.0.10 --server-ct 119

座標は 1080x2340 / 本アプリのレイアウト前提。ADIFFERENT 画面なら要調整。
"""
import argparse
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", os.path.expanduser("~/android-sdk/platform-tools/adb"))
PKG = "io.github.satoru2727.miteru"
ACTIVITY = f"{PKG}/.MainActivity"

# 1080x2340 前提のレイアウト座標
NAV_RECORDINGS = (756, 2131)   # ボトムナビ「録画」
FIRST_CHANNEL = (355, 761)     # ライブ一覧の左上カード
FIRST_RECORDING = (738, 895)   # 録画一覧の 1 件目タイトル
PLAYER_QUALITY_BTN = (589, 213)

RESULTS = []


def sh(*args):
    r = subprocess.run([ADB, *args], capture_output=True)
    return r.stdout.decode("utf-8", "replace")


def sh_bytes(*args):
    r = subprocess.run([ADB, *args], capture_output=True)
    return r.stdout


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))
    time.sleep(0.6)


def key(code):
    sh("shell", "input", "keyevent", str(code))
    time.sleep(0.5)


def dump():
    for _ in range(6):
        out = sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        if "dumped" in out or "UI hierarchy" in out:
            xml = sh("exec-out", "cat", "/sdcard/ui.xml")
            return xml
        time.sleep(1.5)
    return ""


def texts(xml):
    return [m for m in re.findall(r'text="([^"]*)"', xml) if m]


def find_bounds(xml, text):
    m = re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(text), xml)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def media_state():
    out = sh("shell", "dumpsys", "media_session")
    # 対象パッケージ（.debug を除外）のセッションの state 行を探す
    m = re.search(
        r"package=io\.github\.satoru2727\.miteru(?![.\w]).*?"
        r"state=PlaybackState \{state=(\d+), position=(-?\d+)",
        out, re.S,
    )
    if not m:
        return None, None
    return int(m.group(1)), int(m.group(2))


def wait_for_any_text(needles, timeout=45):
    deadline = time.time() + timeout
    while time.time() < deadline:
        tx = texts(dump())
        for n in needles:
            for t in tx:
                if n in t:
                    return t
        time.sleep(3)
    return None


def wait_for_state(targets, timeout=45):
    """指定の PlaybackState になるまで待つ。returns (state, position) or (None, None)."""
    deadline = time.time() + timeout
    last = (None, None)
    while time.time() < deadline:
        last = media_state()
        if last[0] in targets:
            return last
        time.sleep(3)
    return last


def check(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}")


def screenshot(path):
    with open(path, "wb") as f:
        f.write(sh_bytes("exec-out", "screencap", "-p"))
    print(f"       screenshot: {path}")


def server_log(params, pattern, n=6):
    """ssh でサーバーログを tail する (--server-ssh 指定時のみ)."""
    if not params.server_ssh:
        return ""
    cmd = (f"pct exec {params.server_ct} -- bash -lc "
           f"\"grep -H -E '{pattern}' /root/.pm2/logs/KonomiTV-out.log "
           f"/root/.pm2/logs/KonomiTV-error.log 2>/dev/null | tail -{n}\"")
    r = subprocess.run(
        ["ssh", params.server_ssh, cmd], capture_output=True, text=True, timeout=60
    )
    return r.stdout


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apk", help="install this APK first")
    p.add_argument("--fresh", action="store_true", help="clear app data and run first-time setup")
    p.add_argument("--server", default="https://tv.home.satoru.fyi", help="server URL for --fresh setup")
    p.add_argument("--server-ssh", default=None, help="e.g. root@10.77.0.10 (enables server-log checks)")
    p.add_argument("--server-ct", default="119", help="PVE LXC id of the KonomiTV server")
    p.add_argument("--out-dir", default="/tmp/miteru-smoke", help="screenshots output dir")
    p.add_argument("--record-seconds", type=int, default=90)
    params = p.parse_args()

    os.makedirs(params.out_dir, exist_ok=True)

    # 0. device check
    devs = sh("devices")
    if "device" not in devs:
        print("no emulator device attached; start one first"); sys.exit(2)

    # 1. install
    if params.apk:
        out = sh("install", "-r", params.apk)
        check("install APK", "Success" in out, out.strip()[-60:])

    # 1.5 定常状態から始める: 対象アプリを前面で再起動（debug パッケージは止める）
    if not params.fresh:
        sh("shell", "am", "force-stop", "io.github.satoru2727.miteru.debug")
        sh("shell", "am", "force-stop", PKG)
        time.sleep(1)
        sh("shell", "am", "start", "-n", ACTIVITY)
        time.sleep(8)

    # 2. fresh clear + setup
    if params.fresh:
        sh("shell", "pm", "clear", PKG)
        sh("shell", "am", "force-stop", PKG)
        time.sleep(1)
        sh("shell", "am", "start", "-n", ACTIVITY)
        time.sleep(8)
        xml = dump()
        field = find_bounds(xml, "e.g. http://192.168.1.10:7000 · https://konomi.example.com")
        if not field:
            # プレースホルダが違う場合、URL フィールドをテキストで探す
            for t in texts(xml):
                if "e.g." in t:
                    field = find_bounds(xml, t)
                    break
        if not field:
            print("setup screen not found (already configured?)"); sys.exit(2)
        tap(*field)
        time.sleep(1)
        # URL は chunk 分割で入力（長い文字列は truncate されることがある）
        url = params.server
        for i in range(0, len(url), 10):
            sh("shell", "input", "text", url[i:i + 10])
            time.sleep(0.6)
        key(4)  # hide keyboard
        b = find_bounds(dump(), "Connect")
        if b:
            tap(*b)
            time.sleep(6)
            b = find_bounds(dump(), "Get started")
            if b:
                tap(*b)
        time.sleep(8)

    # 3. channel list must load automatically (no manual refresh)
    got = wait_for_any_text(["NHK", "テレビ", "チャンネル"], timeout=45)
    check("channel list auto-loads after setup", got is not None, f"({got})")
    check("channel count is not 0", not any(t.strip().startswith("0 channel") for t in texts(dump())))

    # 4. live playback
    xml = dump()
    b = find_bounds(xml, "NHK総合1・奈良") or find_bounds(xml, "NHK")
    tap(*(b or FIRST_CHANNEL))
    time.sleep(10)
    # エミュレータは 1080p をリアルタイムデコードできないため 540p に落とす
    tap(*PLAYER_QUALITY_BTN)
    time.sleep(2)
    b = find_bounds(dump(), "540p")
    if b:
        tap(*b)
        time.sleep(12)
    state, _ = wait_for_state({3}, timeout=40)
    check("live playback reaches PLAYING", state == 3, f"state={state}")
    screenshot(os.path.join(params.out_dir, "live.png"))
    if params.server_ssh:
        log = server_log(params, "Live: ")
        check("server relay reached ONAir", "ONAir" in log)

    # 5. recorded playback (90 s durability + position advance)
    key(4)          # back to list (mini player keeps audio)
    time.sleep(2)
    tap(*NAV_RECORDINGS)
    time.sleep(5)
    xml = dump()
    titles = [t for t in texts(xml) if "·" in t or re.search(r"[ぁ-んァ-ヶ一-龠]", t)]
    b = None
    for t in titles:
        b = find_bounds(xml, t)
        if b and b[1] > 700:  # ヘッダ行より下（一覧のカード）
            break
        b = None
    tap(*(b or FIRST_RECORDING))
    time.sleep(6)
    # 「続きから再生」ダイアログ（レジューム）が出たら「最初から」を選んで決定的にする
    xml2 = dump()
    tx2 = texts(xml2)
    if any(("Resume playback" in t) or ("Continue from" in t) for t in tx2):
        b2 = find_bounds(xml2, "Start over") or find_bounds(xml2, "Resume")
        print("       resume dialog detected; tapping:", "Start over" if b2 else "n/a")
        if b2:
            tap(*b2)
            time.sleep(4)
    time.sleep(15)
    s1 = media_state()
    check("recorded position is a real value (not -1)", s1[1] is not None and s1[1] >= 0, f"pos={s1[1]}")
    time.sleep(min(params.record_seconds, 90))
    s2 = media_state()
    check("recorded playback state ok (3/6)", s2[0] in (3, 6), f"state={s2[0]}")
    check("recorded position advances",
          s1[1] is not None and s2[1] is not None and s2[1] > s1[1],
          f"{s1[1]} -> {s2[1]} ms")
    screenshot(os.path.join(params.out_dir, "recorded.png"))
    if params.server_ssh:
        ka = server_log(params, "keep-alive", 40)
        check("zero keep-alive 422s in recent log", "422" not in ka)

    # summary
    failed = [r for r in RESULTS if not r[1]]
    print(f"\n{'=' * 50}\n{len(RESULTS) - len(failed)}/{len(RESULTS)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
