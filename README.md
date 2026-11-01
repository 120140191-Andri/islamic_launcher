# Islamic Launcher 🕌

[English](#english) | [Bahasa Indonesia](#bahasa-indonesia) | [Privacy Policy](#privacy-policy-)

---

## English

An innovative custom Android launcher designed specifically to reduce smartphone addiction in a meaningful way. It transforms screen-wasting habits (mindless scrolling) into consistent daily habits of reading the Holy Quran, practicing Dhikr, and reflecting on Authentic Hadiths.

### Core Concept
Instead of simply locking apps with rigid timers, Islamic Launcher utilizes an **"Ibadah Points"** system. Distracting applications (games, social media, entertainment) are automatically locked in **Standard Mode** (or you can choose **Passive Mode** if you only want the Islamic home launcher without app blocking).

To unlock and access locked applications, you need a certain amount of points (e.g., 50 Points) to unlock an app for 60 minutes. Points are earned through multiple worship channels:

1. 📖 **Read Holy Quran (Primary — +10 to +25 Points):** Read interactive verses with Uthmani script, Tajweed color coding, and audio/focus validation.
2. 📿 **Mindful Dhikr (Face Presence — +10 Points per round):** Digital tasbih (33x) powered by smart front-camera face presence detection (valid even with closed eyes for serene contemplation), calming haptic feedback, rhythm pacing, and Arabic/Latin transliterations. Specially designed for everyday remembrance as well as when you are unable to read the Holy Quran (e.g., during menstruation/haid, or in a state of ritual impurity).
3. 📜 **Authentic Hadith Reflection (+3 to +8 Points):** Daily curated collection of authentic Prophetic Hadiths with focus-aware reading time and dynamic point calculations. Ideal for learning prophetic wisdom and as a convenient alternative during excused periods (e.g., menstruation/haid) or on the go.

### Key Features
* 🌟 **Diverse Worship Point System:** Earn points by reading Quran, doing Dhikr (tasbih), or reflecting on Hadiths.
* 🛡️ **2 Flexible Launcher Modes:**
  * **Standard Mode (Recommended):** Active app blocker for non-productive apps, Ghadhul Bashar reminders, and adult/gambling content shield with mindful reflection prompts.
  * **Passive Mode:** Clean Islamic home screen with Quran, Dhikr, and Hadith access without background app blocking.
* 📿 **Smart Face-Presence Dhikr:** Interactive digital tasbih with face presence detection, gentle haptic feedback on each count, anti-rush rhythm pacing, virtues (fadhilah), and automatic history logging.
* 📜 **Daily Authentic Hadith Collection:** Random daily shuffle of 50 verified Hadiths with theme filtering and multi-language translations.
* 🚫 **Permanent Blocker for Gambling & Adult Content Apps:** In compliance with Islamic law, apps categorized under gambling/betting (slots, casino, poker, domino island chips, sports betting, lotteries) as well as explicit adult content apps and bypass/anti-censorship browsers (which facilitate access to adult content) are **permanently blocked**. They cannot be unlocked with points under any circumstances.
* 👁️ **Ghadhul Bashar Lower-the-Gaze Reminders:** Dedicated Quranic reminder cards before opening browsers or high-risk social media platforms to maintain spiritual mindfulness.
* 🔒 **Temporary Unlock System:** Once 50 points are spent, non-productive apps become accessible normally for 60 minutes. When the duration expires, apps are locked again.
* 🌐 **Full Multilingual Support:** Comprehensive localized interface in 6 languages: English, Bahasa Indonesia, Bahasa Melayu, العربية, Afrikaans, and Kiswahili.

### Data Sources 📖
* **Holy Quran Data:** Sourced from the **[Tanzil Project](https://tanzil.net)** and bundled locally (`assets/quran.json`) for 100% offline availability (Uthmani Script, Tajweed, Saheeh International English translation, Indonesian Kemenag translation, and Latin transliteration).
* **Hadith Data:** Curated from classical authentic Hadith collections (*Kutub as-Sittah* — Sahih al-Bukhari, Sahih Muslim, Jami` at-Tirmidhi, Sunan Abi Dawud, etc.) bundled locally (`assets/hadiths.json`) with thematic categorizations and multi-language translations.

### Privacy Policy 🔒
Islamic Launcher is committed to protecting your privacy and complying with Google Play Developer Program Policies:
* **AccessibilityService API Disclosure:** Islamic Launcher uses the Android `AccessibilityService` API solely to detect the foreground application package name and browser/in-app webview URLs in order to: (1) display the Worship Points lock screen when a locked non-productive app is opened, (2) display the Ghadhul Bashar reminder, and (3) block explicit adult/pornographic and online gambling websites. All `AccessibilityService` evaluations are performed **100% locally on your device in real time**. We do **not** collect, store, or transmit any personal data, typing inputs, or screen content to any external server.
* **Installed Applications (`QUERY_ALL_PACKAGES` & `REQUEST_DELETE_PACKAGES`):** As a default Home Launcher replacement, Islamic Launcher queries the list of installed launchable apps on your device to display them in your App Drawer and categorize productive vs. non-productive apps locally. Your installed app list is never uploaded or shared externally. `REQUEST_DELETE_PACKAGES` is used only when you explicitly initiate an app uninstallation from the launcher.
* **Camera & Microphone (`CAMERA` & `RECORD_AUDIO`):** Front-camera frames (used for eye-focus and face-presence validation during Quran/Dhikr sessions) and microphone audio levels (used for voice recitation activity detection) are processed **100% locally in volatile device memory (RAM)**. No photos, videos, or audio recordings are ever saved to storage or transmitted over any network.
* **Advertising (`Google AdMob SDK`):** To support ongoing development, this app integrates the **Google Mobile Ads (AdMob) SDK** to display clearly labeled Native Ads and optional Rewarded Ads. The Google AdMob SDK may automatically collect and process certain device information—such as your Android Advertising ID, coarse IP-based location, app interaction/ad performance metrics, and crash diagnostics—for advertising, fraud prevention, and analytics purposes, governed by Google's User Messaging Platform (UMP) consent flow and [Google's Privacy & Terms](https://policies.google.com/technologies/partner-sites). Users can reset or delete their Advertising ID at any time in Android Settings (`Settings > Google > Ads`).
* **Open Source Transparency:** The entire source code of this application is open source in this repository for complete community auditability.

### Open Source & Amal Jariyah 🤲
This codebase is completely open and free to be developed, modified, and distributed by anyone. The creator's primary mission is to spread this application, its code, or at least the core concept and idea behind it as widely as possible in the hope of gaining *amal jariyah* (continuous reward / ongoing charity) inshaAllah.

### Support / Request Features ☕
* [Support / Request Features via Ko-fi](https://ko-fi.com/andrisetiawan84153)

---

## Bahasa Indonesia

Sebuah custom launcher Android inovatif yang dirancang khusus untuk mengurangi kecanduan smartphone dengan cara yang berkah dan bermanfaat. Aplikasi ini mengubah kebiasaan membuang waktu (*mindless scrolling*) menjadi kebiasaan rutin membaca Al-Quran, berdzikir, dan mentadabburi Hadits Shahih.

### Konsep Utama
Alih-alih sekadar memblokir aplikasi dengan pengatur waktu seperti launcher pada umumnya, Islamic Launcher menggunakan sistem **"Poin Ibadah"**. Aplikasi-aplikasi non-produktif (game, media sosial, video pendek) akan dikunci secara otomatis pada **Mode Standar** (atau Anda dapat memilih **Mode Pasif** jika hanya ingin tampilan launcher Islami tanpa pemblokiran aplikasi).

Untuk membuka dan mengakses aplikasi yang dikunci selama 60 menit, Anda membutuhkan sejumlah poin (misalnya: 50 Poin). Poin dapat diperoleh melalui berbagai jalur ibadah:

1. 📖 **Membaca Al-Quran (Jalur Utama — +10 s.d. +25 Poin):** Membaca ayat Al-Quran ber-Tajweed lengkap dengan teks Arab, Latin, arti, serta validasi suara/fokus mata.
2. 📿 **Dzikir Khusyu' (Deteksi Wajah — +10 Poin per putaran 33x):** Tasbih digital interaktif dengan deteksi kehadiran wajah melalui kamera depan (mata terpejam saat dzikir tetap sah), getaran haptic lembut, pembatas ritme dzikir agar tidak terburu-buru, teks Arab, Latin, arti, fadhilah, dan pencatatan riwayat.
3. 📜 **Tadabbur Hadits Shahih (+3 s.d. +8 Poin):** Membaca dan merenungi koleksi Hadits Shahih harian dengan kalkulasi poin dinamis berbasis panjang matan dan waktu fokus.

### Fitur Unggulan
* 🌟 **Sistem Poin Ibadah Fleksibel:** Kumpulkan poin melalui tilawah Al-Quran, putaran Dzikir, maupun tadabbur Hadits.
* 🛡️ **2 Pilihan Mode Launcher:**
  * **Mode Standar (Direkomendasikan):** Pemblokir aktif untuk aplikasi non-produktif, pengingat Ghadhul Bashar, serta perisai konten dewasa/judi dengan fitur renungan bijak.
  * **Mode Pasif:** Launcher beranda Islami (Al-Quran, Dzikir, Hadits) tanpa pemblokiran aplikasi latar belakang.
* 📿 **Mode Dzikir Khusyu' Cerdas:** Tasbih digital dengan deteksi kehadiran wajah (*Face Presence*), getaran lembut di setiap hitungan dzikir, ritme jeda agar tidak terburu-buru, dan riwayat dzikir otomatis.
* 📜 **Koleksi 50 Hadits Shahih Harian:** Diacak setiap hari dengan filter tema serta terjemahan multi-bahasa.
* 🚫 **Pemblokiran Total & Permanen untuk Aplikasi Judi & Konten Dewasa:** Sesuai syariat Islam, seluruh aplikasi kategori perjudian/taruhan serta aplikasi konten dewasa eksplisit dan peramban anti-blokir/bypass **diblokir secara permanen dan total**.
* 👁️ **Peringatan Ghadhul Bashar (Menundukkan Pandangan):** Menampilkan ayat Al-Qur'an untuk menundukkan pandangan sebelum membuka peramban umum serta media sosial berisiko tinggi.
* 🔒 **Sistem "Unlock" 60 Menit:** Setelah poin ditukar, aplikasi non-produktif dapat diakses normal selama 60 menit sebelum terkunci kembali secara otomatis.
* 🌐 **Dukungan Multi-Bahasa Lengkap:** Mendukung 6 bahasa (Bahasa Indonesia, English, Bahasa Melayu, العربية, Afrikaans, dan Kiswahili).

### Sumber Data 📖
* **Data Al-Quran:** Bersumber dari **[Tanzil Project](https://tanzil.net)** yang dibundel secara lokal (`assets/quran.json`) agar dapat diakses 100% offline.
* **Data Hadits:** Dikompilasi dari kitab-kitab hadits mu'tabar (*Kutubus Sittah* — Shahih Bukhari, Shahih Muslim, Sunan Tirmidzi, Sunan Abu Dawud, dll.) yang dibundel secara lokal (`assets/hadiths.json`).

### Kebijakan Privasi (Privacy Policy) 🔒
Islamic Launcher berkomitmen penuh melindungi privasi Anda dan mematuhi kebijakan Google Play Store serta Google AdMob:
* **Pengungkapan Layanan Aksesibilitas (`AccessibilityService API`):** Islamic Launcher menggunakan `AccessibilityService` API Android semata-mata untuk mendeteksi nama paket aplikasi yang sedang dibuka di layar utama serta URL/teks pada peramban guna: (1) menampilkan layar kunci Poin Ibadah saat aplikasi non-produktif dibuka, (2) menampilkan pengingat Ghadhul Bashar, dan (3) memblokir situs konten dewasa/pornografi serta judi online. Seluruh pemeriksaan diproses **100% secara lokal di dalam perangkat Anda secara real-time**. Kami **tidak pernah** mengumpulkan, menyimpan, atau mengirimkan data pribadi, ketikan pengguna, maupun isi layar ke server eksternal mana pun.
* **Daftar Aplikasi Terinstal (`QUERY_ALL_PACKAGES` & `REQUEST_DELETE_PACKAGES`):** Sebagai aplikasi peluncur beranda (*Home Launcher*), Islamic Launcher membaca daftar aplikasi yang terinstal di perangkat Anda untuk ditampilkan di Laci Aplikasi (*App Drawer*) dan mengelompokkan aplikasi produktif vs. non-produktif secara lokal. Daftar aplikasi Anda tidak pernah dikirim keluar dari perangkat. Izin `REQUEST_DELETE_PACKAGES` hanya digunakan saat Anda secara eksplisit menekan tombol hapus/uninstall aplikasi.
* **Kamera & Mikrofon (`CAMERA` & `RECORD_AUDIO`):** Stream kamera depan (untuk validasi fokus mata dan kehadiran wajah saat membaca Al-Quran / Dzikir) serta sensor mikrofon (untuk mendeteksi aktivitas suara bacaan tilawah) diproses **100% secara lokal di memori sementara (RAM)**. Tidak ada foto, video, atau rekaman suara yang pernah disimpan ke penyimpanan maupun dikirim ke internet.
* **Layanan Periklanan (`Google AdMob SDK`):** Untuk mendukung keberlanjutan pengembangan aplikasi, Islamic Launcher mengintegrasikan **Google Mobile Ads (AdMob) SDK** untuk menayangkan Iklan Native dan Iklan Rewarded opsional. SDK Google AdMob dapat mengumpulkan dan memproses pengenal perangkat (seperti *Android Advertising ID*), perkiraan lokasi kasar berbasis IP, metrik interaksi iklan, serta diagnostik aplikasi sesuai dengan persetujuan pengguna (Google UMP) dan [Kebijakan Privasi & Persyaratan Google](https://policies.google.com/technologies/partner-sites). Pengguna dapat mengatur ulang atau menghapus *Advertising ID* kapan saja melalui Pengaturan Android (`Pengaturan > Google > Iklan`).
* **Transparansi Open Source:** Seluruh kode sumber aplikasi ini bersifat sumber terbuka (*open source*) di repositori ini sehingga dapat diaudit secara transparan.

### Pengembangan & Amal Jariyah 🤲
Kode sumber ini sepenuhnya bebas dan boleh dikembangkan, dimodifikasi, serta disebarluaskan oleh siapa pun dengan harapan menjadi **amal jariyah** yang terus mengalir insyaAllah.

### Dukung / Usulkan Fitur ☕
* [Dukung / Usulkan Fitur via Trakteer](https://trakteer.id/andri_setiawan108/tip)

---
*Dibuat untuk memotivasi Anda memperbanyak interaksi dengan Al-Quran, Dzikir, dan Hadits serta mengubah kebiasaan screen-time menjadi pahala yang berlimpah.*
