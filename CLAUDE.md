# CLAUDE.md — Kvaesitso-UM

Fork [Kvaesitso](https://github.com/MM2-0/Kvaesitso) (launcher Android, Kotlin + Compose) dengan
modul **Unified Messaging (UM)**: panel *Conversations* untuk WhatsApp + Telegram di Feed (usap kanan).
Latar belakang, keputusan desain, dan status fitur UM: `unifiedmessaging/HANDOFF.md`.

## Struktur

- Kode UM ada di branch **`feed`** (belum di `main`). Buat branch kerja dari `origin/feed`, PR ke `feed`.
- `unifiedmessaging/` — modul UM (`:unifiedmessaging`, paket `id.kvplugin.unifiedmsg`). Semua
  resource dan nama penyimpanan berawalan `um_` (`resourcePrefix`).
  - `MessageListenerService`, `NotificationParser` — baca notifikasi WA/TG.
    Belum ada integrasi Beeper; sumber pesan saat ini hanya notifikasi aplikasi di `MessagingApp.kt`.
  - `ChatStore` (SQLite `um_chats.db`), `ActiveChats`, `ChatActions` (balas lewat RemoteInput), `MediaCache`.
  - `overlay/` — panel Feed (`ILauncherOverlay` dari `:services:feed`); `ui/ConversationsUi.kt` — seluruh UI panel (~900 baris).
  - `UnifiedMessagingPlugin`, `ContactFactory` — plugin pencarian (`:plugins:sdk`).
- Modul Kvaesitso asli (lihat `settings.gradle.kts`):
  - `app/app` (APK, flavor `default`/`fdroid`), `app/ui` (semua UI launcher).
  - `core/*` (dasar, preferensi, i18n), `data/*` (sumber data per jenis), `services/*`
    (search, feed, plugins, ...), `libs/*`, `plugins/sdk`.
- Patch UM di luar modul (daftar lengkap di HANDOFF.md): `settings.gradle.kts`,
  `app/app/build.gradle.kts`, `services/feed/.../FeedConnection.kt`,
  `app/ui/.../scaffold/components/FeedComponent.kt`.

## Perintah

Diukur di mesin ini (12 core, 30 GB RAM, JDK 21), dengan cache Gradle hangat:

| Tujuan | Perintah | Lama |
|---|---|---|
| Cek kompilasi UM saja (paling cepat) | `./gradlew :unifiedmessaging:compileDebugKotlin -q` | ~3 dtk inkremental |
| Build APK default debug | `./gradlew :app:app:assembleDefaultDebug -q` | ~1,5 menit setelah ganti branch; detik kalau sedikit berubah; build benar-benar dingin jauh lebih lama |
| Unit test | `./gradlew :unifiedmessaging:testDebugUnitTest :core:base:testDebugUnitTest :data:locations:testDebugUnitTest` | ~5 dtk (30 test) |
| Lint UM | `./gradlew :unifiedmessaging:lintDebug` | ~45 dtk; saat ini 0 error, 16 warning |

- APK: `app/app/build/outputs/apk/default/debug/app-default-debug.apk` (paket `de.mm20.launcher2.debug`).
- Laporan lint: `unifiedmessaging/build/reports/lint-results-debug.txt`. `abortOnError = false`,
  jadi lint tidak menggagalkan build; baca laporannya.
- UM **belum punya test**. Untuk test pertama, tambahkan `testImplementation(libs.junit)` di
  `unifiedmessaging/build.gradle.kts` (dari version catalog, sama seperti `data/locations`) dan tulis
  test JVM murni di `unifiedmessaging/src/test/java/...`. Kode yang memakai API Android
  (Notification, Context) tidak bisa dites di JVM; pisahkan logika murni (parsing, normalisasi) untuk dites.
- Tidak ada ktlint/detekt.

## Aturan

- Usahakan semua perubahan di `unifiedmessaging/`. Ubah kode Kvaesitso asli hanya kalau perlu,
  dan jelaskan di deskripsi PR (file apa, kenapa); tambahkan juga ke tabel patch di HANDOFF.md.
- Tanpa diminta, jangan ubah: versi dependensi (`gradle/libs.versions.toml`, juga versi yang
  ditulis langsung di `build.gradle.kts`), file Gradle wrapper (`gradlew*`, `gradle/wrapper/`),
  atau konfigurasi signing (`signingConfigs` di `app/app/build.gradle.kts`).
- Jangan commit `local.properties`, `build/`, APK, atau laporan lint (sudah di `.gitignore`;
  jangan `git add -f`).
- Jangan merge PR, jangan push ke `main`. Repo ini punya remote `upstream` (MM2-0/Kvaesitso);
  `gh` bisa default ke sana, jadi pakai `gh pr create --repo d3vzero/Kvaesitso-UM --base feed`.

## Catatan untuk `qwen-task`

- Model lokal punya konteks 64K dan build Gradle lama: **satu tugas = satu file atau satu kelas**.
- Sebutkan path file lengkap dan nama fungsi yang diubah. Jangan minta model membaca
  `ui/ConversationsUi.kt` utuh; tunjuk fungsi/composable tertentu.
- Kriteria selesai pakai perintah cepat: `./gradlew :unifiedmessaging:compileDebugKotlin -q` dan/atau
  `./gradlew :unifiedmessaging:testDebugUnitTest`. Jangan suruh model menjalankan `assemble`, `lint`,
  atau `./gradlew build` (lama, menghabiskan batas waktu); itu dijalankan pemeriksa.
- Ingatkan aturan prefix `um_` untuk resource dan nama penyimpanan baru.
