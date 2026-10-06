# Unified Messaging (modul Kvaesitso-UM)

Panel **Conversations** ala Ratio Launcher, dibangun langsung ke dalam fork Kvaesitso ini:
baca dan balas chat **WhatsApp** dan **Telegram** dari home screen tanpa membuka aplikasinya.

## Fitur
- **Feed (usap ke kanan):** panel Conversations bergeser masuk di atas wallpaper.
  - Kartu chat 2 baris bergaya widget Kvaesitso, filter *Unread · Personal · Group · WhatsApp · Telegram*,
    bar *Search* seperti di home.
  - Ketuk chat → riwayat + papan ketik langsung terbuka untuk **balas langsung**.
  - Foto dari notifikasi tampil di chat; tekan lama / ⋮ → tandai dibaca, buka di aplikasi,
    info balasan, hapus percakapan.
- **Aplikasi "Conversations"** di app drawer sebagai cadangan.
- **Plugin pencarian** (opsional): chat, nomor telepon, `@username` Telegram di kotak cari.

## Cara kerja
Membaca notifikasi WhatsApp/Telegram (`NotificationListenerService`), menyimpan pesan secara lokal,
dan membalas lewat aksi "Balas" (RemoteInput) dari notifikasi. Panel ditampilkan lewat protokol
*launcher overlay* yang dipakai fitur Feed Kvaesitso.

## Setup di HP
1. Pasang build Kvaesitso-UM (`./gradlew assembleFdroidDebug`).
2. Buka **Conversations** (app drawer) → ⋮ → Settings → aktifkan **Notification access**
   untuk *Unified Messaging – chat indexer*. Android 13+: kalau abu-abu, Info Aplikasi Kvaesitso →
   ⋮ → *Allow restricted settings*.
3. Aktifkan **Unrestricted battery**.
4. Kvaesitso → Settings → **Integrations → Feed** → pilih *Conversations (Unified Messaging)*,
   lalu **Gestures → Swipe right → Feed**.

## Keterbatasan
- Hanya chat yang pernah muncul di notifikasi; chat yang di-mute tidak masuk.
- Balas langsung butuh aksi "Balas" dari notifikasi; aksi terakhir disimpan di memori (hilang saat
  reboot/update aplikasi, pulih saat ada pesan baru). Teks saja – stiker belum bisa.
- Membalas menandai chat sebagai dibaca (WhatsApp mengirim centang biru).
- Video/stiker/dokumen: ketuk untuk membuka di aplikasinya.

## Privasi
Semua data (nama chat, pesan, foto) hanya di HP, di storage privat Kvaesitso
(`um_chats.db`, `files/um_images`), maks. 30 hari. Tidak ada server.

Detail teknis: [HANDOFF.md](HANDOFF.md).
