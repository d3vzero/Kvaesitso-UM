# HANDOFF – Unified Messaging di Kvaesitso-UM

Untuk siapa pun (termasuk asisten AI) yang melanjutkan proyek ini.

## Riwayat singkat
1. Awalnya aplikasi terpisah `id.kvplugin.unifiedmsg` (v0.1–0.3.3): plugin pencarian + penyedia Feed.
2. Sekarang digabung ke fork **Kvaesitso-UM** (basis tag `v1.41.0`, branch `feed`) sebagai modul
   library `:unifiedmessaging` → **satu APK**.

## Status

| Hal | Status |
|---|---|
| Basis | Kvaesitso v1.41.0, build `fdroidDebug` (paket `de.mm20.launcher2.debug`) |
| Feed (usap kanan) | ✅ teruji sebagai aplikasi terpisah; ⚠️ belum diuji setelah digabung |
| Balas langsung WA/TG berkali-kali (juga setelah notifikasi hilang) | ✅ |
| Foto dari notifikasi | ⚠️ diimplementasi, belum dikonfirmasi |
| Hapus percakapan, Info balasan | ⚠️ belum diuji |
| Plugin pencarian di dalam Kvaesitso sendiri | ❓ belum tahu apakah Kvaesitso mendaftarkan plugin dari paketnya sendiri |
| Stiker | ⏸ ditunda (aksi Balas WA/TG kemungkinan teks saja – cek via "Reply info") |
| Telegram penuh via TDLib | 📋 direncanakan (login + daftar chat dulu) |

## Patch di luar modul (di kode Kvaesitso)
| File | Perubahan | Alasan |
|---|---|---|
| `settings.gradle.kts` | `include(":unifiedmessaging")` | daftarkan modul |
| `app/app/build.gradle.kts` | `implementation(project(":unifiedmessaging"))` | masukkan ke APK |
| `services/feed/.../FeedConnection.kt` | flag `BIND_ALLOW_ACTIVITY_STARTS` (API 34+) | penyedia Feed pihak ketiga boleh membuka activity; setelah digabung tidak wajib lagi, tetap aman |
| `app/ui/.../scaffold/components/FeedComponent.kt` | `override val drawBackground = false` | tanpa lapisan abu-abu di belakang Feed |

Feed sendiri hanya aktif di build non-release (`FeatureFlags.feed = BUILD_TYPE != "release"`).
Untuk build release: ubah flag itu ke `true`.

## Struktur modul `unifiedmessaging/`
```
build.gradle.kts            library + Compose, ikut version catalog Kvaesitso, resourcePrefix "um_"
src/main/AndroidManifest.xml  provider, listener, OverlayService, activities (nama kelas lengkap)
src/main/res/                 semua resource berawalan um_ (hindari bentrok dengan resource Kvaesitso)
src/main/java/id/kvplugin/unifiedmsg/
  MessagingApp.kt           daftar aplikasi & paket
  MessageListenerService.kt listener notifikasi → simpan pesan/foto, catat aksi
  NotificationParser.kt     judul chat, pesan MessagingStyle, URI foto, aksi Balas/Tandai dibaca
  ChatStore.kt              SQLite um_chats.db (skema v3), ChatEvents (StateFlow)
  ActiveChats.kt            aksi notifikasi di memori (live/stale) + PendingIntentCache
  ChatActions.kt            reply (RemoteInput), markRead, openChat, deleteChat, replyStatus
  MediaCache.kt             salinan foto di files/um_images
  UnifiedMessagingPlugin.kt + ContactFactory.kt   plugin pencarian (SDK project :plugins:sdk)
  OpenChatActivity.kt       trampoline kvmsg://
  Prefs.kt                  prefs um_settings, normalisasi nomor, lookup kontak
  SetupActivity.kt          layar Settings (Compose)
  overlay/OverlayService.kt  ILauncherOverlay.Stub (AIDL dari :services:feed)
  overlay/OverlayPanel.kt    jendela panel, animasi geser, gestur tutup, lifecycle Compose
  ui/ConversationsUi.kt      seluruh UI panel
  ui/ConversationsActivity.kt host activity
```

## Keputusan desain penting
1. **Lewat notifikasi**, bukan API: WhatsApp tidak punya API pribadi; library tak resmi berisiko ban.
2. **Kunci chat = judul dinormalisasi** (tidak ada ID chat stabil di notifikasi).
   Notifikasi berjudul "You"/"Anda" (hanya balasan kita) dipetakan ke chat lewat `sbn.key`.
3. **Aksi balas tetap disimpan setelah notifikasi hilang** (`live=false`) – Telegram selalu
   menghapus notifikasi setelah dibalas. `CanceledException` → `forgetReply()`.
4. **Foto disalin saat notifikasi masuk** (izin URI berakhir bersama notifikasi).
5. **Menu & konfirmasi berupa sheet di dalam panel**, bukan Dialog/Popup (tidak ada token activity di overlay).
6. **Back** ditangani `SwipePanelLayout.dispatchKeyEvent` (overlay) & `OnBackPressedCallback` (activity).
7. **Overlay sekarang in-process:** Kvaesitso bind ke `OverlayService` di paketnya sendiri;
   `ILauncherOverlay.Stub.asInterface` mengembalikan objek lokal (panggilan langsung, bukan binder).
   `OverlayService` hanya menerima pemanggil dari uid sendiri atau paket `de.mm20.launcher2*`.
8. **Nama penyimpanan berawalan `um_`** agar tidak bentrok dengan data Kvaesitso.

## Lingkungan
- PC Artix Linux, build lewat CLI, JDK 17 (`JAVA_HOME`), SDK di `~/Android/Sdk`.
- `./gradlew assembleFdroidDebug` → `app/app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk`.
- Update upstream: `git fetch upstream --tags`, branch baru dari tag, cherry-pick/merge branch `feed`.

## Ide berikutnya
- [ ] Uji ulang semua fitur setelah digabung.
- [ ] Telegram via TDLib: login (nomor → kode → 2FA), daftar chat, riwayat, kirim, + New conversation, media.
- [ ] Indikator "bisa dibalas langsung" di daftar chat.
- [ ] Bubble & filter bergaya widget (solid) agar seragam.
- [ ] Foto profil dari notifikasi.
