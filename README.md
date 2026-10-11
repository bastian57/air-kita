# AIR KITA — Admin Tagihan Air PAMSIMAS

Aplikasi Android native (Kotlin + Jetpack Compose Material3) untuk mencatat
tagihan air PAMSIMAS "AIR KITA". 100% offline, tanpa server, tanpa biaya.

- Package: `com.daftech.airkita` • v1.0.0 (versionCode 1)
- minSdk 26, target/compileSdk 34, Kotlin 2.0.21, AGP 8.5.2, Compose BOM 2025.01.00
- Bahasa UI: Indonesia • Watermark: "apk by DAFTECH"

## Fitur (8 layar + wizard)

| Layar | Fungsi |
|---|---|
| Setup Wizard | Migrasi cut-off dari buku: tarif → pelanggan+meter terakhir → tunggakan → saldo kas awal |
| Beranda | Ringkasan tagihan/terkumpul/tunggakan/pelanggan, aksi cepat, daftar penunggak |
| Catat Meter | 2 opsi: ketik manual / scan kamera (CameraX + ML Kit OCR on-device, offline) |
| Tagihan | Filter Semua/Belum lunas/Lunas, progress terkumpul |
| Struk | Struk monospace, Tandai Lunas, Cetak Thermal (Bluetooth ESC/POS 58mm), Bagikan WA |
| Kas | Saldo real-time, pemasukan otomatis dari pembayaran, catat pengeluaran manual |
| Laporan Bulanan | PDF A4 on-device (ringkasan, tabel pelanggan, penunggak, arus kas, tanda tangan), share ke grup WA |
| Pengaturan | Tarif bertingkat, printer Bluetooth, backup/restore, nomor WA, Tentang ("apk by DAFTECH") |

## Backup

`Backup Sekarang` mengunggah **PDF arsip + JSON data** ke Google Drive
(service account dari secret CI `GOOGLE_SERVICE_ACCOUNT_JSON`, folder dari
secret `DRIVE_FOLDER_ID`). Tanpa kredensial: fallback simpan ke Download +
share sheet manual. File `app/src/main/assets/service_account.json` di repo
hanyalah placeholder — file asli tidak boleh di-commit (sudah di `.gitignore`).

## Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/build-apk.yml`): build otomatis tiap push,
upload artifact + rolling release `latest`. Perlu secrets:
`GOOGLE_SERVICE_ACCOUNT_JSON` (opsional), `DRIVE_FOLDER_ID` (opsional).

## Struktur source

```
app/src/main/java/com/daftech/airkita/
├── MainActivity.kt, AirKitaApp.kt
├── data/          # Room: entities, DAO, AppDatabase, Repository, PrefKeys
├── engine/        # TariffEngine (pure function, unit-testable)
├── ui/theme/      # Warna "Biru Air", Typography, Theme
├── ui/nav/        # NavHost 9 rute
├── ui/components/ # Header gradien, bottom bar, kartu, badge
├── ui/screen/     # 9 layar Compose
├── print/         # EscPosPrinter (Bluetooth SPP, ESC/POS 58mm)
├── pdf/           # ReportPdf (PdfDocument A4 + simpan ke Download)
├── backup/        # DriveBackup (Drive REST API v3, service account)
└── util/          # Fmt (Rupiah, periode, tanggal)
```
