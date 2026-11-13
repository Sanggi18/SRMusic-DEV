# SRMusic Android Architecture & UI/UX Instructions

Dokumen ini adalah referensi arsitektur dan standar implementasi resmi untuk proyek **SRMusic Android App**. Setiap pengembang atau AI Agent **WAJIB** membaca dan mematuhi spesifikasi di bawah ini sebelum merancang atau memodifikasi kode.

---

## 1. Pemisahan Layer Arsitektur (Separation of Concerns)

Proyek mengikuti pola arsitektur **Clean Architecture / MVVM-MVI** yang terstruktur sebagai berikut:

### A. Core Layer (`core/`)
- Berisi seluruh logika domain, pemrosesan data, playback engine, dan utilities bersama.
  - `core/model/`: Domain data models (misal `Song`, `ArtworkRef`, `EqualizerConfig`, `PlaybackState`, `AlbumUiModel`, `ArtistUiModel`, `FolderUiModel`).
  - `core/data/`: Data persistence (Room DAOs, database migrations, SharedPreferences/DataStore).
  - `core/playback/`: Media3 ExoPlayer, FFmpeg renderers, audio sink pipeline, MusicService lifecycle.
  - `core/audio/`: Oboe, audio output detectors, terminal logger, DAC volume & sample rate modes.
  - `core/filter/`: Algoritma filtering, sorting, pencarian multi-script, dan parser pohon folder (`AlbumFilter`, `ArtistFilter`, `SongFilter`, `SortHelper`, `MultiScriptSearchMatcher`, `FolderTreeParser`).
  - `core/util/`: Shared utilities murni (`TimeUtils`, `FileSizeUtils`, `ArtistHelper`, `ArtworkPaletteExtractor`).
- **ATURAN MUTLAK**: Layer `core/` **TIDAK BOLEH** mengimpor atau bergantung pada layer `ui/`.

### B. Feature Layer (`ui/<feature>/`)
- Setiap fitur mengisolasi state management dan komponen visualnya sendiri:
  - `ui/<feature>/core/`: ViewModel, UiState, dan Actions spesifik untuk fitur tersebut. State management tetap berada di dalam folder fiturnya, BUKAN di `core/`.
  - `ui/<feature>/components/`: Komponen UI modular, widget kecil, dialog, dan cards milik fitur tersebut.
  - `ui/<feature>/<Feature>Screen.kt`: Composable screen utama.

### C. Shared UI Layer (`ui/common/`)
- Menyediakan komponen visual dan dispatcher aksi yang digunakan lintas fitur (`UiEnums.kt`, `LocalMiniPlayerVisible.kt`, `SongActionDispatcher.kt`):
  - `ui/common/theme/`: Sistem tema terpusat (`Theme.kt`, `Preset.kt`, `Color.kt`, `Type.kt`, `Spacing.kt`, `ExpressiveMotion.kt`).
  - `ui/common/components/`: Komponen bersama (`ArtworkCard`, `AlbumArtwork`, `PlayShuffleButtonRow`, `SRMusicGlyphIcon`, `DynamicArtworkHero`, `DonateDialog`, `SongInfoBottomSheet`).

### D. Screen Composables sebagai Pure Presenters
- Layar composable (`*Screen.kt`) hanya bertindak sebagai **Pure Presenter**:
  - Mengambil state dari ViewModel (`collectAsStateWithLifecycle`).
  - Merender UI dan mendelegasikan aksi pengguna ke atas (*upward action callbacks*).
  - **DILARANG KERAS** meletakkan business logic, kalkulasi data, format angka/waktu, atau efek samping langsung di dalam Screen Composable.

---

## 2. Standar UI/UX Jetpack Compose

### A. Aturan No Grey Box Ripple (MANDATORY)
- Setiap komponen yang dapat diklik (`.clickable`) dengan sudut membulat atau custom shape **WAJIB** menerapkan:
  1. `.clip(shape)` **sebelum** `.clickable`, atau
  2. `indication = null` dengan `MutableInteractionSource` (misal menggunakan efek tekan pegas `springPress`), atau
  3. `ripple(bounded = true)` yang dibatasi oleh bentuk shape.
- **DILARANG** mengaplikasikan `.clickable` langsung pada container yang belum di-clip, karena Jetpack Compose akan menampilkan ripple kotak abu-abu (*Grey Box Ripple*) di luar batas sudut lengkung.

### B. Pratinjau Home Terkunci (Locked Home Previews)
- **For You**: Pratinjau di layar beranda wajib berupa kolase artwork grid 2x2 terkunci (maksimal 4 lagu). Format carousel hanya diperuntukkan bagi layar anak (*child screens*).
- **Genre Today**: Pratinjau di layar beranda wajib berupa kartu bertumpuk statis (`GenreTodayCard`) dengan judul genre dan subtitle warna primer.

### C. Carousel Artwork Persegi (1:1 Aspect Ratio)
- Pada seluruh child screen (misal For You Remix Screen, Genre Today Screen), carousel album art wajib menggunakan item dengan rasio aspek persegi 1:1 (`aspectRatio(1f)`), mencegah distorsi vertikal atau peregangan gambar.

### D. Accessibility & Touch Targets
- Setiap elemen interaktif wajib memiliki touch target minimum **48dp x 48dp**.
- Setiap icon dan gambar non-dekoratif wajib menyertakan `contentDescription` yang jelas.
- Tambahkan `Modifier.testTag("snake_case_name")` pada tombol, input, dan kartu aksi utama.

---

## 3. Standar Audio & Hardware DAC Playback

1. **Bit-Perfect Mode**:
   - Seluruh pipeline bit-perfect menggunakan **C++ Direct USB Exclusive Mode** (`libusb` / UAC2 kernel bypass).
   - Audio diteruskan langsung ke hardware DAC tanpa melalui resampling atau mixer Android `AudioFlinger`.
2. **Standard Android Audio**:
   - Saat Bit-Perfect nonaktif, audio dialihkan ke `AudioTrack` atau `Oboe` dengan dukungan 32-bit float output dan equalizer software internal.

---

## 4. Aturan Kritis Performa UI & Navigasi (DILARANG MENGUBAH LOGIKA INI)

Ketiga logika berikut merupakan optimasi performa inti SRMusic yang terbukti stabil dan **TIDAK BOLEH DIUBAH** dalam refaktor atau update apa pun:

### A. Penghapusan Efek Bouncy / Overscroll Total (Android 12+)
- `LocalOverscrollFactory provides null` dan `LocalOverscrollConfiguration provides null` **WAJIB** terpasang di `Theme.kt`.
- Hal ini memastikan overscroll glow/stretch bawaan Android 12+ tidak aktif di Home, Songs, Artist, dan Albums saat ditarik ke atas maupun ke bawah.

### B. Prioritas Load Artwork (Gambar Lagu & MediaStore)
- Di `ArtworkCard.kt`, prioritas URI **WAJIB** mengutamakan `contentUri` (MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) terlebih dahulu, baru kemudian fallback ke `parsedArtworkUri`:
  ```kotlin
  val primaryUri = contentUri?.takeIf { it != Uri.EMPTY } ?: (artworkUri as? Uri)?.takeIf { it != Uri.EMPTY }
  ```
- **Rasional**: Di Android 10+ (API 29+), `contentUri` memungkinkan eksekusi langsung `loadThumbnail(contentUri, Size(px, px), null)` secara native dan instan tanpa fallback decoding manual yang berat.

### C. Navigasi Bottom Bar & Swipe Pager (Smooth Slide Sync & Anti-Stutter) — [LOCKED / JANGAN DISENTUH]
- **STATUS TERKUNCI MUTLAK**: Logika Bottom Bar dan Pager di `AppNavigation.kt` serta `MainTabNavigation.kt` sudah final, sangat ringan, dan **DILARANG KERAS DISENTUH ATAU DIUBAH LAGI**.
- Di `AppNavigation.kt`, fungsi `selectTab(tab)` menggunakan `if (animate && pageDiff <= 1)` untuk menganimasikan perpindahan tab bersebelahan (`animateScrollToPage`), dan menggunakan `scrollToPage(targetIndex)` untuk lompatan lintas tab (`pageDiff > 1`) agar tidak melewati komposisi tab perantara yang menyebabkan patah-patah.
- Di `MainTabContent` (`MainTabNavigation.kt`), `HorizontalPager` menggunakan `snapPositionalThreshold = 0.22f` dan `spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)` agar swipe geser kanan-kiri terasa cepat dan responsif.
- Di `MainBottomNavigationBar` (`MainTabNavigation.kt`):
  - Saat berpindah ke tab bersebelahan atau digeser manual (swipe), posisi background pill dibaca langsung pada fase layout `.offset { ... }` dari `(pagerState.currentPage + pagerState.currentPageOffsetFraction)` sehingga menempel 1:1 (0ms delay) dengan pergerakan halaman.
  - Saat melompat lebih dari 1 tab melalui tap Bottom Bar (`diff > 1`), `pillAnimatable` menganimasikan pergeseran background pill secara mulus menggunakan `tween(220, easing = FastOutSlowInEasing)`.
