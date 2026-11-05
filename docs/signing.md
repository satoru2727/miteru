# 署名とリリース

## 署名鍵

| 項目 | 値 |
| --- | --- |
| Keystore | `~/keys/miteru-release.jks`（リポジトリ外・600） |
| エイリアス | `miteru` |
| アルゴリズム | RSA 4096-bit |
| 有効期限 | 10,000 日 |
| 証明書 SHA-256 | `6DF9EEB064BB1ABF61CD07469BBFA5E47C3C430F9D589A48F79AA1C9A2579384` |
| パスワード | `~/keys/miteru.properties`（600） |

`key.properties` はリポジトリ直下に置くが、`.gitignore` 済み。存在するときだけ
`release` ビルドに署名設定が入る。存在しない場合（F-Droid のビルドサーバー）は
未署名の `app-release-unsigned.apk` が生成され、F-Droid が自前の鍵で署名する。

## GitHub Actions

`.github/workflows/release.yml` は次の Secrets を使う。

- `KEYSTORE_BASE64`: `base64 -w0 ~/keys/miteru-release.jks`
- `KEYSTORE_PASSWORD`: `storePassword`
- `KEY_ALIAS`: `miteru`
- `KEY_PASSWORD`: `keyPassword`

タグ `v*` を push するか、Actions から `workflow_dispatch` で versionName /
versionCode を指定すると、署名済み APK を GitHub Release に添付する。

## チャネル間の署名

- GitHub Releases / Obtainium: 上記の鍵で署名
- F-Droid: F-Droid の鍵で再署名

**両者は別のアプリ識別子になる。** F-Droid 版へ乗り換えるときは一度アンインストールが
必要（アプリデータは引き継げない）。F-Droid の再現可能ビルド（`Binaries` +
`AllowedAPKSigningKeys`）を設定すれば同一鍵にできるが、現時点では未検証。
