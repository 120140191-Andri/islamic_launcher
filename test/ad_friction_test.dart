import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:islamic_launcher/providers/app_state.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late SharedPreferences prefs;
  late AppState appState;

  setUp(() async {
    SharedPreferences.setMockInitialValues({
      'points': 500,
      'isPassiveMode': true,
    });
    prefs = await SharedPreferences.getInstance();

    const channelBlock = MethodChannel('com.islamiclauncher/block');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channelBlock, (call) async => true);

    const channelApps = MethodChannel('com.islamiclauncher/apps');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channelApps, (call) async {
          if (call.method == 'getApps') {
            return [];
          }
          return true;
        });

    appState = AppState(prefs);
  });

  tearDown(() {
    const channelBlock = MethodChannel('com.islamiclauncher/block');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channelBlock, null);

    const channelApps = MethodChannel('com.islamiclauncher/apps');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channelApps, null);
  });

  group('Ad Friction & Grace Period Tests (Aturan Iklan Pembatas)', () {
    test(
      '1. Buka non-produktif < 2x tidak mewajibkan iklan (Buka ke-1 & 2)',
      () {
        appState.setDailyUnlocksForTesting(0);
        expect(appState.dailyUnlocksCount, 0);
        expect(appState.shouldShowAdBeforeUnlock, false);

        appState.setDailyUnlocksForTesting(1);
        expect(appState.dailyUnlocksCount, 1);
        expect(appState.shouldShowAdBeforeUnlock, false);
      },
    );

    test(
      '2. Buka non-produktif >= 2x mewajibkan iklan jika belum ada tiket santai aktif (Buka ke-3 dst)',
      () {
        appState.setDailyUnlocksForTesting(2);
        expect(appState.dailyUnlocksCount, 2);
        expect(appState.isAdGracePeriodActive, false);
        expect(appState.shouldShowAdBeforeUnlock, true);

        appState.setDailyUnlocksForTesting(3);
        expect(appState.shouldShowAdBeforeUnlock, true);

        appState.setDailyUnlocksForTesting(5);
        expect(appState.shouldShowAdBeforeUnlock, true);
      },
    );

    test(
      '3. Menonton iklan mengaktifkan masa bebas iklan 3 menit (Global Pass)',
      () async {
        appState.setDailyUnlocksForTesting(3);
        expect(appState.shouldShowAdBeforeUnlock, true);

        // Simulasi user menonton video dan mengaktifkan masa bebas iklan 3 menit
        await appState.activateAdGracePeriod(durationMinutes: 3);

        expect(appState.isAdGracePeriodActive, true);
        expect(appState.remainingAdGracePeriodSeconds, greaterThan(150));
        expect(appState.remainingAdGracePeriodMinutes, 3);

        // Selama masa aktif, tidak perlu nonton iklan lagi walaupun count >= 3
        expect(appState.shouldShowAdBeforeUnlock, false);
      },
    );

    test(
      '4. Ketika masa bebas iklan 3 menit kadaluarsa, wajib nonton iklan lagi',
      () {
        appState.setDailyUnlocksForTesting(4);

        // Set masa aktif yang sudah lewat 1 detik lalu
        final pastTime = DateTime.now().subtract(const Duration(seconds: 1));
        appState.setAdGracePeriodForTesting(pastTime);

        expect(appState.isAdGracePeriodActive, false);
        expect(appState.remainingAdGracePeriodSeconds, 0);
        expect(appState.shouldShowAdBeforeUnlock, true);
      },
    );

    test(
      '5. Reset harian (00:00) mengembalikan hitungan ke 0 dan membebaskan iklan',
      () {
        appState.setDailyUnlocksForTesting(
          4,
          date: '2026-10-30',
        ); // Kemarin sudah 4x

        // Tanggal hari ini berbeda, maka count reset ke 0
        expect(appState.dailyUnlocksCount, 0);
        expect(appState.shouldShowAdBeforeUnlock, false);
      },
    );

    test(
      '6. Ketika iklan gagal tayang (offline / no fill), masa bebas iklan TIDAK diaktifkan',
      () {
        appState.setDailyUnlocksForTesting(2);
        expect(appState.shouldShowAdBeforeUnlock, true);

        // Simulasi bypass offline: activateAdGracePeriod TIDAK dipanggil
        expect(appState.isAdGracePeriodActive, false);
        expect(appState.remainingAdGracePeriodSeconds, 0);

        // Karena tiket tidak aktif, pembukaan selanjutnya tetap berstatus wajib iklan
        expect(appState.shouldShowAdBeforeUnlock, true);
      },
    );

    test(
      '7. Menu Apps Native Ad Level scaling sesuai frekuensi buka app non-produktif',
      () {
        // 0x buka: level 0 (tanpa iklan native)
        appState.setDailyUnlocksForTesting(0);
        appState.setDailyNonProductiveOpensForTesting(0);
        expect(appState.nonProductiveAppOpenLevel, 0);

        // 1x buka: level 1 (1 kotak aplikasi)
        appState.setDailyNonProductiveOpensForTesting(1);
        expect(appState.nonProductiveAppOpenLevel, 1);

        // 2x buka: level 2 (4 kotak horizontal / 1 baris)
        appState.setDailyNonProductiveOpensForTesting(2);
        expect(appState.nonProductiveAppOpenLevel, 2);

        // 3x buka: level 3 (2 baris vertikal x 4 kotak horizontal)
        appState.setDailyNonProductiveOpensForTesting(3);
        expect(appState.nonProductiveAppOpenLevel, 3);

        // 4x buka: level 4 (3 baris vertikal x 4 kotak horizontal - maksimal)
        appState.setDailyNonProductiveOpensForTesting(4);
        expect(appState.nonProductiveAppOpenLevel, 4);

        // 10x buka: tetap maksimal level 4
        appState.setDailyNonProductiveOpensForTesting(10);
        expect(appState.nonProductiveAppOpenLevel, 4);
      },
    );

    test(
      '8. Reset harian (00:00) mengembalikan level iklan native menu apps ke 0 (bebas iklan)',
      () {
        appState.setDailyUnlocksForTesting(0);
        appState.setDailyNonProductiveOpensForTesting(
          5,
          date: '2026-10-30',
        ); // Hari kemarin
        expect(appState.dailyNonProductiveOpens, 0);
        expect(appState.nonProductiveAppOpenLevel, 0);
      },
    );

    test(
      '9. Development reset & manual override non-produktif harian',
      () async {
        // Set level 3
        await appState.setDailyNonProductiveOpens(3);
        expect(appState.dailyNonProductiveOpens, 3);
        expect(appState.nonProductiveAppOpenLevel, 3);

        // Jalankan reset development
        await appState.resetDailyNonProductiveState();
        expect(appState.dailyNonProductiveOpens, 0);
        expect(appState.dailyUnlocksCount, 0);
        expect(appState.isAdGracePeriodActive, false);
        expect(appState.nonProductiveAppOpenLevel, 0);
      },
    );

    test('9. Development reset & manual override non-produktif harian', () async {
      // Set level 3
      await appState.setDailyNonProductiveOpens(3);
      expect(appState.dailyNonProductiveOpens, 3);
      expect(appState.nonProductiveAppOpenLevel, 3);

      // Jalankan reset development
      await appState.resetDailyNonProductiveState();
      expect(appState.dailyNonProductiveOpens, 0);
      expect(appState.dailyUnlocksCount, 0);
      expect(appState.isAdGracePeriodActive, false);
      expect(appState.nonProductiveAppOpenLevel, 0);
    });
  });
}
