# HadirKu - Sistem Presensi Siswa Digital

HadirKu adalah aplikasi presensi akademik digital berbasis Android yang dirancang untuk menjamin validitas kehadiran siswa secara akurat dan transparan. Aplikasi ini menerapkan sistem verifikasi tiga lapis yang menggabungkan validasi geofence koordinat ruang kelas, sensor biometrik sidik jari, dan pengenalan wajah cerdas dengan deteksi kecerahan cahaya.

> **Informasi Status Pengembangan**: Proyek ini saat ini masih dalam tahap pengembangan aktif (*work in progress*) dan belum sepenuhnya selesai. Implementasi saat ini diprioritaskan pada alur dan antarmuka peran siswa, sedangkan penyesuaian lanjutan untuk peran guru dan administrator sedang dalam proses pengerjaan.

---

## Fitur Utama

### 1. Verifikasi Presensi 3 Lapis
* **Geofence Koordinat Ruang Kelas**: Presensi hanya dapat dilakukan apabila perangkat siswa terdeteksi berada di dalam radius toleransi koordinat ruang kelas yang dijadwalkan (misalnya toleransi 50 meter ke titik lokasi ruang kelas).
* **Autentikasi Biometrik Perangkat**: Integrasi Android BiometricPrompt untuk memvalidasi sidik jari siswa secara lokal pada perangkat.
* **Pengenalan Wajah & Analisis Pencahayaan**: Pemanfaatan inferensi model MobileFaceNet (TensorFlow Lite) dan CameraX untuk mencocokkan kemiripan wajah siswa dengan profil terdaftar, disertai validasi intensitas cahaya guna mencegah kecurangan.

### 2. Registrasi Mandiri dengan Kartu Pelajar
* **Pemindaian Kartu**: Siswa memindai kartu pelajar menggunakan kamera atau memasukkan NIS/NISN secara manual.
* **Kustomisasi Username**: Siswa dapat menentukan username pribadi unik yang digunakan sebagai kredensial utama saat login ke aplikasi.
* **Penguncian Profil**: Informasi NIS, kelas, dan data biometrik secara otomatis terhubung ke akun yang didaftarkan.

### 3. Antarmuka Siswa Modern
* **Beranda**: Menampilkan ringkasan status kehadiran (persentase hadir, izin/sakit, alpa), kartu sesi presensi aktif dengan akses cepat, serta filter jadwal harian.
* **Jadwal Pelajaran**: Tampilan jadwal mata pelajaran per hari yang terorganisasi, dilengkapi penanda kode ruangan dan waktu sesi.
* **Riwayat Presensi**: Log catatan riwayat presensi bulanan dengan filter kategori status kehadiran (Hadir, Izin, Sakit, Alpa).
* **Profil Akun**: Informasi kartu identitas siswa, rincian akun, status aktif verifikasi tiga lapis, dan fitur pembaruan kata sandi.

### 4. Manajemen Terpadu untuk Guru dan Administrator
* **Role Guru**: Pembuatan dan pembukaan sesi presensi mata pelajaran, penyesuaian jam mulai/selesai kelas, dan ekspor laporan rekapitulasi kehadiran.
* **Role Administrator**: Pemantauan presensi sekolah secara menyeluruh dan manajemen data master (siswa, guru, jadwal, serta koordinat kelas).

---

## Tumpukan Teknologi (Technology Stack)

| Komponen | Teknologi |
| :--- | :--- |
| **Bahasa Pemrograman** | Kotlin |
| **Pola Arsitektur** | Model-View-ViewModel (MVVM) / Repository Pattern |
| **Komponen UI** | Android Jetpack, Material Design 3, ViewBinding |
| **Pemrosesan Citra & Kamera** | AndroidX CameraX, Google ML Kit |
| **Model Pembelajaran Mesin** | TensorFlow Lite (MobileFaceNet Embedding) |
| **Sensor & Lokasi** | Android LocationManager, AndroidX Biometric API |
| **Basis Data & Backend** | Supabase (PostgreSQL, Remote Auth & Storage) |
| **Penyimpanan Lokal** | SharedPreferences / Encrypted Data Storage |

---

## Struktur Basis Data

Aplikasi menggunakan skema PostgreSQL pada Supabase dengan relasi antar-entitas berikut:

| Nama Tabel | Deskripsi |
| :--- | :--- |
| `public.classes` | Menyimpan master data kelas, penamaan ruang, koordinat latitude & longitude, serta batas radius presensi (meter). |
| `public.users` | Menyimpan data akun pengguna, peran akses (STUDENT, TEACHER, ADMIN), kata sandi, dan status biometrik. |
| `public.student_cards`| Menyimpan data profil siswa terverifikasi (NIS, nama lengkap, kelas, alamat). |
| `public.schedules` | Menyimpan jadwal mata pelajaran, alokasi jam pembelajaran, kode ruang kelas, dan status pembukaan sesi presensi. |
| `public.attendance` | Menyimpan rekam presensi siswa, jarak presensi dari koordinat kelas, verifikasi biometrik, metode validasi, dan waktu pencatatan. |

Skema lengkap dan kebijakan Row Level Security (RLS) tersedia pada berkas [supabase_schema.sql](supabase_schema.sql).

---

## Panduan Pemasangan dan Kompilasi

### Prasyarat Lingkungan Pengembangan
* Android Studio Ladybug / Koala atau versi yang lebih baru
* JDK 17 atau JDK 21
* Android SDK (API Level minimal 24, Target SDK 34)
* Perangkat fisik Android dengan sensor kamera dan pemindai biometrik (disarankan untuk pengujian penuh)

### Langkah Pemasangan

1. Klon repositori ini ke direktori lokal:
   ```bash
   git clone https://github.com/derrick0930/Absensi-Kelas.git
   cd Absensi-Kelas
   ```

2. Buka proyek menggunakan Android Studio.

3. Konfigurasi kredensial Supabase pada berkas `SupabaseConfig.kt` atau `local.properties`:
   ```kotlin
   const val SUPABASE_URL = "https://<PROJECT-ID>.supabase.co"
   const val SUPABASE_ANON_KEY = "<YOUR-SUPABASE-ANON-KEY>"
   ```

4. Jalankan skrip skema basis data pada SQL Editor di dasbor Supabase melalui berkas `supabase_schema.sql`.

5. Lakukan sinkronisasi dependensi Gradle dan jalankan build APK:
   ```bash
   # Kompilasi versi Debug
   ./gradlew assembleDebug

   # Kompilasi versi Release
   ./gradlew assembleRelease
   ```
