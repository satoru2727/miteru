# デバッグ手順（再生トラブルの切り分け）

アプリと実サーバーの両側から証拠を集める。**まずサーバーログを見る**のが最短。

## 1. どこで詰まっているか（切り分けフロー）

| 症状 | まず見る場所 | 期待 |
| --- | --- | --- |
| チャンネルが 0 件 | アプリの logcat（下記） | `Api: HTTP ...` エラーが出ていれば接続問題 |
| ライブが始まらない | サーバーログ `[Live: <ch>-<q>]` | `ONAir` まで進めばサーバーは正常 → プレイヤー側 |
| 録画が途中で止まる | サーバーログの `keep-alive` | `204` が続いていれば正常。`422` ならセッション切れ |

## 2. アプリのログ（logcat）

**release ビルドでも** `adb logcat` に出る（タグ `Miteru`）。

```bash
adb logcat -s Miteru            # アプリの全ログ（再生・API・エラー）
adb logcat -s Miteru | grep -E "Player|Api"   # 再生・通信だけ
```

ログはアプリ内の「バグ報告」画面と同じ内容（URL はマスク済み）。
`Player: video size WxH` が出ていれば「映像が実際に描画された」証拠。

## 3. 再生状態（エミュレータ）

```bash
adb shell dumpsys media_session | grep -A2 "package=io.github.satoru2727.miteru"
```

`state=3` 再生中 / `state=6` バッファリング / `state=1` 停止。
録画は 1 分空けて 2 回サンプルし、`position` が前進していることを確認する。

## 4. サーバーログ（pve の CT119）

```bash
ssh root@10.77.0.10 'pct exec 119 -- bash -lc "tail -30 /root/.pm2/logs/KonomiTV-out.log"'
ssh root@10.77.0.10 'pct exec 119 -- bash -lc "grep \"Live: \" /root/.pm2/logs/KonomiTV-error.log | tail -10"'
```

- ライブ: `[Live: gr011-1080p] [Status: ONAir]` まで進めば健全。
  `Client Disconnected (Timeout)` はクライアントが読めていない（多くはエミュレータの
  ソフトデコード限界。実機ハードウェアデコードでは出ない）。
- 録画: `keep-alive` が約 5 秒ごとに `204` で続くこと。`422 Session does not exist` が
  出たら セッション切れ（アプリ側の `startKeepAlive` が 5 秒間隔 + 422 で再作成する実装か確認）。

## 5. スモークテスト（定型検証の自動実行）

```bash
# エミュレータを起動しておく
python3 tools/smoke-test.py --server-ssh root@10.77.0.10
```

ライブ（540p に自動で落として再生確認）/ 録画（90 秒耐久 + position 前進）/
チャンネル自動ロードを順に検証し、スクリーンショットを `/tmp/miteru-smoke/` に残す。
`--fresh` で初期セットアップから、`--apk <path>` でインストールから実行。

## 6. エミュレータの注意

- メモリ 1536MB（Compose は 1024 だと ANR ループ）: `-memory 1536 -cores 2`
- **`assembleRelease` と同時に動かさない**（4GB ホストでは OOM でどちらかが死ぬ）
- 1080p はソフトデコードでリアルタイムに追いつかない → テストは 540p で
- 座標タップは 1080x2340 前提。ボトムナビは y≈2131（y≈2210 はシステムナビに落ちる）
