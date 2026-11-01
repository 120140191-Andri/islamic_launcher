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
    test('1. Buka non-produktif < 2x tidak mewajibkan iklan (Buka ke-1 & 2)', () {
      appState.setDailyUnlocksForTesting(0);
      expect(appState.dailyUnlocksCount, 0);
      expect(appState.shouldShowAdBeforeUnlock, false);

      appState.setDailyUnlocksForTesting(1);
      expect(appState.dailyUnlocksCount, 1);
      expect(appState.shouldShowAdBeforeUnlock, false);
    });

    test('2. Buka non-produktif >= 2x mewajibkan iklan jika belum ada tiket santai aktif (Buka ke-3 dst)', () {
      appState.setDailyUnlocksForTesting(2);
      expect(appState.dailyUnlocksCount, 2);
      expect(appState.isAdGracePeriodActive, false);
      expect(appState.shouldShowAdBeforeUnlock, true);

      appState.setDailyUnlocksForTesting(3);
      expect(appState.shouldShowAdBeforeUnlock, true);

      appState.setDailyUnlocksForTesting(5);
      expect(appState.shouldShowAdBeforeUnlock, true);
    });

    test('3. Menonton iklan mengaktifkan masa bebas iklan 3 menit (Global Pass)', () async {
      appState.setDailyUnlocksForTesting(3);
      expect(appState.shouldShowAdBeforeUnlock, true);

      // Simulasi user menonton video dan mengaktifkan masa bebas iklan 3 menit
      await appState.activateAdGracePeriod(durationMinutes: 3);

      expect(appState.isAdGracePeriodActive, true);
      expect(appState.remainingAdGracePeriodSeconds, greaterThan(150));
      expect(appState.remainingAdGracePeriodMinutes, 3);

      // Selama masa aktif, tidak perlu nonton iklan lagi walaupun count >= 3
      expect(appState.shouldShowAdBeforeUnlock, false);
    });

    test('4. Ketika masa bebas iklan 3 menit kadaluarsa, wajib nonton iklan lagi', () {
      appState.setDailyUnlocksForTesting(4);

      // Set masa aktif yang sudah lewat 1 detik lalu
      final pastTime = DateTime.now().subtract(const Duration(seconds: 1));
      appState.setAdGracePeriodForTesting(pastTime);

      expect(appState.isAdGracePeriodActive, false);
      expect(appState.remainingAdGracePeriodSeconds, 0);
      expect(appState.shouldShowAdBeforeUnlock, true);
    });

    test('5. Reset harian (00:00) mengembalikan hitungan ke 0 dan membebaskan iklan', () {
      appState.setDailyUnlocksForTesting(4, date: '2026-10-30'); // Kemarin sudah 4x

      // Tanggal hari ini berbeda, maka count reset ke 0
      expect(appState.dailyUnlocksCount, 0);
      expect(appState.shouldShowAdBeforeUnlock, false);
    });

    test('6. Ketika iklan gagal tayang (offline / no fill), masa bebas iklan TIDAK diaktifkan', () {
      appState.setDailyUnlocksForTesting(2);
      expect(appState.shouldShowAdBeforeUnlock, true);

      // Simulasi bypass offline: activateAdGracePeriod TIDAK dipanggil
      expect(appState.isAdGracePeriodActive, false);
      expect(appState.remainingAdGracePeriodSeconds, 0);

      // Karena tiket tidak aktif, pembukaan selanjutnya tetap berstatus wajib iklan
      expect(appState.shouldShowAdBeforeUnlock, true);
    });
  });
}
