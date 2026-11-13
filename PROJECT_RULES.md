# SRMusic Project Rules & Operating Standards

Dokumen ini berisi aturan operasional ketat (*operating rules*) yang wajib dipatuhi oleh AI Coding Engine atau pengembang sebelum menganalisis, merencanakan, memodifikasi, atau membangun kode pada proyek **SRMusic**.

---

## 1. Wajib Baca Aturan Pertama Kali (Read Rules First)
Sebelum menganalisis, merencanakan, atau mengubah file apa pun di setiap sesi / pergantian instruksi:
- Pengembang / Agen **WAJIB** membaca `/INSTRUCTIONS.md` dan `/PROJECT_RULES.md`.

---

## 2. Mandatory Pre-Execution Plan & Approval Gate
Sebelum menulis, mengedit, membuat, atau menghapus file kode (`.kt`, `.kts`, `.xml`, `.cpp`, `.h`) berdasarkan instruksi bahasa alami:
1. **Lakukan Analisis Mendalam**: Teliti codebase terkait, alur data, dan penyebab masalah (*root cause*).
2. **Sajikan Rencana Terstruktur**: Tampilkan ringkasan akar masalah, daftar file eksak yang akan diubah, serta preview diff perubahan.
3. **STOP Segera & Minta Konfirmasi**: Agen **DILARANG KERAS** menyentuh kode sebelum pengguna memberikan konfirmasi eksplisit atau kata **"approve"**.

*Pengecualian: Executable Spec (prompt berupa kode lengkap atau lampiran file kode siap pakai) dapat langsung dieksekusi tanpa approval gate.*

---

## 3. Strict Scope Adherence (No Unsolicited Features)
- **HANYA** kerjakan apa yang secara eksplisit diminta oleh pengguna.
- Dilarang membuat asumsi, menambahkan fitur sampingan, atau "mempercantik" fitur di luar brief pengguna.
- Dilarang mengubah file atau merefaktor modul yang tidak relevan dengan tugas.

---

## 4. Zero Hallucination (Jangan Mengarang Nama/Simbol)
- Dilarang mengarang nama file, class, fungsi, variabel, ataupun resource ID (`R.drawable`, `R.string`, `R.color`).
- Hanya gunakan simbol yang benar-benar ada di codebase atau yang secara eksplisit didefinisikan dalam spesifikasi.

---

## 5. Konfigurasi Tipe Build (`app/build.gradle.kts`)
Konfigurasi build ditentukan berdasarkan pilihan mode:

### A. APK Non-Debug
Digunakan untuk rilis produksi dan kompresi maksimal:
```kotlin
debug {
    isDebuggable = false
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    signingConfig = signingConfigs.getByName("debugConfig")
}
```
- `packaging.jniLibs.useLegacyPackaging = true` (Kompresi DEFLATE aktif untuk native libraries).

### B. APK Debug
Digunakan untuk pengujian cepat dan logging:
```kotlin
debug {
    isDebuggable = true
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    signingConfig = signingConfigs.getByName("debugConfig")
}
```
- `packaging.jniLibs.useLegacyPackaging = false`

### C. APK Full Debug
Digunakan untuk debugging penuh tanpa optimasi R8:
```kotlin
debug {
    isDebuggable = true
    isMinifyEnabled = false
    isShrinkResources = false
    signingConfig = signingConfigs.getByName("debugConfig")
}
```
- `packaging.jniLibs.useLegacyPackaging = false`

---

## 6. Proteksi File Keystore & Signing Config
- **DILARANG MENGUBAH / MENGHAPUS** konfigurasi `signingConfigs` (`release` dan `debugConfig`), file `debug.keystore`, atau `debug.keystore.base64`.

---

## 7. Larangan Merusak 3 Logika Kritis Performa (Preserve Core Performance Logic)
Setiap pengembang atau AI Agent **DILARANG MENGUBAH / MERUSAK** 3 implementasi logika kritis berikut:
1. **Penghapusan Overscroll**: Tetap mempertahankan `LocalOverscrollFactory provides null` dan `LocalOverscrollConfiguration provides null` di `Theme.kt`.
2. **Prioritas URI Artwork**: Di `ArtworkCard.kt`, wajib selalu memprioritaskan `contentUri` (MediaStore) dibanding `parsedArtworkUri` untuk loadThumbnail native Android 10+.
3. **Navigasi Bottom Bar Non-Mampir**: Di `AppNavigation.kt`, fungsi `selectTab` wajib mempertahankan pengecekan `pageDiff <= 1` (animate) vs `pageDiff > 1` (`scrollToPage` langsung tanpa animasi perantara yang me-render SongScreen), serta menjaga kehalusan animasi pill background di `MainTabNavigation.kt`.

---

## 8. Larangan Menulis Komentar (Strict No Comments Rule)
- AI Coding Engine dan pengembang **WAJIB** tidak menambahkan komentar dalam bentuk apa pun (`//`, `/* */`, `/** */`, `<!-- -->`, `#`) ke dalam file kode atau resource (`.kt`, `.kts`, `.xml`, `.cpp`, `.h`).
- Seluruh kode yang dihasilkan harus bersih, ringkas, idiomatis, dan self-documenting tanpa boilerplate komentar penjelasan.

---

## 9. Aturan Format Kode Rapi & Spasi Standar (Anti-Double-Spacing Rule)
- DILARANG KERAS menyisipkan baris kosong (empty line) di antara setiap baris kode (*double-spacing*).
- Import statements wajib dikelompokkan rapat tanpa baris kosong di antara setiap baris import.
- Parameter Composable, rantai modifier (`Modifier.xxx`), argumen fungsi, dan blok kode harus diformat padat, rapi, dan sesuai standar resmi Kotlin & Jetpack Compose Style Guide.
- Baris kosong HANYA diperbolehkan satu baris sebagai pemisah antar fungsi (`fun`), kelas (`class`), atau blok deklarasi utama.

---
