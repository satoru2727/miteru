<div align="center"><a href="https://github.com/Safouene1/support-palestine-banner/blob/master/Markdown-pages/Support.md"><img src="https://raw.githubusercontent.com/Safouene1/support-palestine-banner/master/banner-support.svg" alt="Support Palestine" style="width: 100%;"></a></div>

[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue?style=for-the-badge)](LICENSE)
[![Anti-Copyright](https://img.shields.io/badge/anti--copyright-black?style=for-the-badge)](https://unlicense.org/)
[![Free Palestine](https://img.shields.io/badge/%F0%9F%87%B5%F0%9F%87%B8_FREE_PALESTINE-techforpalestine.org-000?labelColor=grey&color=D83838&style=for-the-badge)](https://techforpalestine.org/learn-more)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#動作環境)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](#)
[![F-Droid](https://img.shields.io/badge/F--Droid-submission%20in%20progress-1976D2?style=for-the-badge&logo=fdroid&logoColor=white)](#f-droid)

# Miteru

**KonomiTV のための非公式 Android クライアント。** 自宅の KonomiTV サーバーを、スマートフォンから「見る」ことに集中して使えるように作りました。ライブ、番組表、録画、そして片手で扱えるプレイヤーが一つになっています。

> KonomiTV は [tsukumijima](https://github.com/tsukumijima/KonomiTV) 氏のプロジェクトです。Miteru は**非公式**のサードパーティクライアントであり、本家とは一切関係ありません。

## できること

### ライブ
- 地上波 / BS / CS / その他のチャンネル一覧（ロゴ・現在の番組・次の番組・視聴者数）
- 放送の進み具合をカードの進捗バーで表示
- チャンネル名・番組名での検索、種別フィルタ
- 通信できないときは最後に取得した一覧をキャッシュから表示

### 番組表
- 24 時間の横スクロール番組表。チャンネル列は固定
- 今日 / 明日 / 明後日の切り替え、表示倍率の変更（1 時間 140〜360dp）
- 「現在」ボタンで現在時刻へジャンプ、赤い現在時刻ライン
- 番組詳細（ジャンル・詳細情報・予約状態）、放送中の番組はそのまま視聴

### 録画
- サムネイル付きの録画一覧、タイトル検索、MPEG-TS / MP4 フィルタ、新しい順 / 古い順
- 無限スクロール、視聴位置の進捗表示、「続きから」再生
- 解像度・コンテナ・ファイルサイズのメタ情報

### プレイヤー
- Media3 (ExoPlayer) によるライブ MPEG-TS / 録画 HLS 再生
- 画質の切り替え（1080p60 / 1080p / 810p / 720p / 540p / 480p / 360p / 240p）
- 主音声 / 副音声の切り替え
- ピクチャーインピクチャー、バックグラウンド再生（メディア通知）
- ダブルタップで ±10 秒シーク、ライブ時の前後チャンネル移動
- ミニプレイヤー（下部ナビの上に常駐）

### 設定
- システム / ダーク / ライト、ダイナミックカラー（Android 12+）
- 既定の画質、モバイル通信時の確認、続きから再生のオン / オフ
- サーバー URL の変更、キャッシュ削除

## 動作環境

- Android 10 (API 29) 以上
- KonomiTV サーバー（API を公開しているもの。0.14 系の API スキーマを対象に実装）
- バックエンドは Mirakurun / EDCB のどちらでも可（録画予約の作成は EDCB 限定のため、本アプリでは Web UI を案内）

## インストール

F-Droid への掲載を準備中です。それまではソースからビルドしてください。

```bash
export JAVA_HOME="$HOME/.local/share/mise/installs/java/temurin-21.0.13+9.0.LTS"
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` に `sdk.dir` を設定し、Android SDK 36 を用意してください。

## リリース（GitHub / Obtainium）

署名済み APK を GitHub Releases で配布します。ファイル名は `miteru-<version>-universal.apk` です。

- 署名鍵: リポジトリ外の `~/keys/miteru-release.jks`（4096-bit RSA、10,000 日）
- CI: `.github/workflows/release.yml` が Secrets（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`）で署名
- ローカル: リポジトリ直下に `key.properties` がある場合だけ release に署名設定が入る
- **F-Droid 版とは署名が異なります。** F-Droid 版に乗り換えるときは一度アンインストールが必要です（アプリデータは引き継げません）

## F-Droid

- `fastlane/metadata/android/{en-US,ja-JP}/` にストア用メタデータ（説明・アイコン・スクリーンショット）を同梱
- F-Droid は `key.properties` のない状態でビルドし、F-Droid の鍵で署名します
- `versionCode` / `versionName` は `app/build.gradle.kts`（CI からは Gradle プロパティで上書き）
- 提出状況: **fdroiddata に MR 提出済み**（レビュー待ち）

## 制限事項

| 項目 | 状況 | 理由 |
| --- | --- | --- |
| ARIB 字幕 | 未対応 | libaribcaption / aribb24 相当のデコーダ実装が必要 |
| データ放送 | 未対応 | web-bml を WebView に載せる必要がある |
| original（MPEG-2）画質 | 非対応 | Android 標準に MPEG-2 デコーダがない |
| 録画予約の作成 | 未対応 | 予約は KonomiTV の Web UI から |
| ニコニコ実況コメント | 未対応 | WebSocket クライアントの実装が必要 |
| オフライン保存 | 未対応 | KonomiTV 独自の `KTVODLP` 形式の解析が必要 |

## 設計メモ

| 論点 | 決定 | 理由 |
| --- | --- | --- |
| アプリ名 | Miteru | 公式クライアントと誤認されない固有ブランドにするため |
| applicationId | `io.github.satoru2727.miteru` | 公開後に変更できないため最初に固定 |
| minSdk | 29 | Media3 / Compose の現実的な下限。HTTP の LAN 接続も扱いやすい |
| 画質 | ネイティブで再生できる 8 段階のみ | MPEG-2 original は Android で再生できない |
| 認証 | なし（サーバー URL のみ） | KonomiTV のユーザー機能は視聴に必須ではない |
| HTTP | 許可 | 家庭内サーバーは `http://192.168.x.x:7000` が一般的 |

## プライバシー

- 広告・解析・トラッキングなし
- 通信先はユーザーが設定した KonomiTV サーバーのみ
- 権限: `INTERNET` / `ACCESS_NETWORK_STATE` / `WAKE_LOCK` / `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` / `POST_NOTIFICATIONS`
- `allowBackup="false"`（設定や視聴位置を OS のバックアップに送らない）

## ライセンス

[Unlicense](LICENSE)（パブリックドメイン）。許可もクレジットも不要です。
