import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:islamic_launcher/providers/app_state.dart';
import 'package:islamic_launcher/screens/home/permission_blocked_overlay.dart';
import 'package:islamic_launcher/widgets/accessibility_disclosure_dialog.dart';
import 'package:provider/provider.dart';
import 'package:islamic_launcher/screens/onboarding/mode_selection_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    const channelBlock = MethodChannel('com.islamiclauncher/block');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channelBlock, (call) async => true);

    const channelApps = MethodChannel('com.islamiclauncher/apps');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channelApps, (call) async {
      if (call.method == 'getDeviceInfo') {
        return {
          'manufacturer': 'Samsung',
          'model': 'Galaxy S24',
          'sdkInt': 34,
        };
      }
      if (call.method == 'getAppStoragePath') {
        return '/mock/storage';
      }
      return true;
    });
  });

  group('2-Mode System (Standard & Passive) & Compliance Tests', () {
    test('Default mode state defaults to Standard Mode', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final state = AppState(prefs);

      expect(state.launcherMode, equals('standard'));
      expect(state.isPassiveMode, isFalse);
      expect(state.isAccessibilityRequired, isTrue);
    });

    test('Legacy strict mode preference is automatically migrated to Standard Mode', () async {
      SharedPreferences.setMockInitialValues({
        'is_strict_mode': true,
        'strict_mode_days': 30,
        'strict_mode_until_ms': DateTime.now().millisecondsSinceEpoch + 100000,
        'launcher_mode': 'strict',
      });
      final prefs = await SharedPreferences.getInstance();
      final state = AppState(prefs);
      await Future.delayed(const Duration(milliseconds: 50));

      expect(state.launcherMode, equals('standard'));
      expect(prefs.getBool('is_strict_mode'), isFalse);
      expect(prefs.getString('launcher_mode'), equals('standard'));
    });

    test('enableStandardMode sets launcherMode to standard', () async {
      SharedPreferences.setMockInitialValues({'launcher_mode': 'passive'});
      final prefs = await SharedPreferences.getInstance();
      final state = AppState(prefs);

      expect(state.isPassiveMode, isTrue);

      await state.enableStandardMode();

      expect(state.isPassiveMode, isFalse);
      expect(state.launcherMode, equals('standard'));
      expect(prefs.getString('launcher_mode'), equals('standard'));
    });

    test('enablePassiveMode sets launcherMode to passive and isAccessibilityRequired to false', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final state = AppState(prefs);

      await state.enablePassiveMode();
      expect(state.isPassiveMode, isTrue);
      expect(state.launcherMode, 'passive');
      expect(state.isAccessibilityRequired, isFalse);
      expect(state.hasSelectedMode, isTrue);
      expect(prefs.getString('launcher_mode'), 'passive');
    });

    testWidgets('PermissionBlockedOverlay bypasses blocking completely in Passive Mode', (tester) async {
      SharedPreferences.setMockInitialValues({
        'launcher_mode': 'passive',
        'has_completed_onboarding': true,
      });
      final prefs = await SharedPreferences.getInstance();
      final appState = AppState(prefs);

      await tester.pumpWidget(
        MaterialApp(
          home: PermissionBlockedOverlay(
            appState: appState,
            child: const Text('Normal HomeScreen Content'),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Normal HomeScreen Content'), findsOneWidget);
      expect(find.text('Tindakan Diperlukan'), findsNothing);
      expect(find.byType(ElevatedButton), findsNothing);
    });

    testWidgets('ModeSelectionScreen renders only 2 modes (Mode Standar & Mode Pasif)', (tester) async {
      SharedPreferences.setMockInitialValues({
        'languageCode': 'id',
      });
      final prefs = await SharedPreferences.getInstance();
      final appState = AppState(prefs);

      await tester.pumpWidget(
        ChangeNotifierProvider<AppState>.value(
          value: appState,
          child: const MaterialApp(
            home: ModeSelectionScreen(isOnboarding: true),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Mode Standar is present & recommended
      expect(find.text('Mode Standar'), findsOneWidget);
      expect(find.text('DIREKOMENDASIKAN'), findsOneWidget);

      // Mode Ketat is removed
      expect(find.text('Mode Ketat'), findsNothing);

      // Mode Pasif is present without "SANGAT TIDAK DIREKOMENDASIKAN" badge
      expect(find.text('SANGAT TIDAK DIREKOMENDASIKAN'), findsNothing);
      final passiveTitle = find.text('Mode Pasif');
      await tester.scrollUntilVisible(passiveTitle, 200);
      await tester.pumpAndSettle();
      expect(passiveTitle, findsOneWidget);
      expect(
        find.textContaining('tidak mengizinkan layanan Aksesibilitas aktif sama sekali'),
        findsOneWidget,
      );
      expect(
        find.textContaining('TIDAK AKAN BERFUNGSI'),
        findsOneWidget,
      );
    });

    testWidgets('PermissionBlockedOverlay shows 2 options (Re-enable Accessibility & Switch to Passive Mode with red trade-off) when Accessibility is disabled', (tester) async {
      const channelBlock = MethodChannel('com.islamiclauncher/block');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channelBlock, (call) async {
        if (call.method == 'isAccessibilityServiceEnabled') return false;
        return true;
      });

      SharedPreferences.setMockInitialValues({
        'languageCode': 'id',
        'hasCompletedOnboarding': true,
        'launcher_mode': 'standard',
      });
      final prefs = await SharedPreferences.getInstance();
      final appState = AppState(prefs);
      appState.refreshStatus();
      await tester.pump();

      await tester.pumpWidget(
        ChangeNotifierProvider<AppState>.value(
          value: appState,
          child: Consumer<AppState>(
            builder: (context, state, _) => MaterialApp(
              home: PermissionBlockedOverlay(
                appState: state,
                child: const Scaffold(body: Text('Home Content')),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Verify both choices and red trade-off explanation are visible
      expect(find.text('Mode Standar'), findsOneWidget);
      expect(find.text('DIREKOMENDASIKAN'), findsOneWidget);
      expect(find.text('AKTIFKAN KEMBALI AKSESIBILITAS'), findsOneWidget);
      expect(find.text('Konsekuensi Mode Pasif (Tanpa Aksesibilitas):'), findsOneWidget);
      expect(find.textContaining('TIDAK AKAN BERFUNGSI'), findsOneWidget);

      final switchBtn = find.text('PINDAH KE MODE PASIF');
      await tester.scrollUntilVisible(switchBtn, 150);
      await tester.pumpAndSettle();
      expect(switchBtn, findsOneWidget);

      // Tapping Switch to Passive Mode switches mode and dismisses the overlay
      await tester.tap(switchBtn);
      await tester.pumpAndSettle();

      expect(appState.isPassiveMode, isTrue);
      expect(find.text('PINDAH KE MODE PASIF'), findsNothing);
    });

    testWidgets('AccessibilityDisclosureDialog displays Google Play mandatory disclosure and returns user choice', (tester) async {
      bool? result;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: ElevatedButton(
                onPressed: () async {
                  result = await AccessibilityDisclosureDialog.show(
                    context,
                    lang: 'id',
                  );
                },
                child: const Text('Open Disclosure'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('Open Disclosure'));
      await tester.pumpAndSettle();

      expect(find.textContaining('AccessibilityService API'), findsWidgets);
      expect(find.text('Setuju & Lanjutkan'), findsOneWidget);
      expect(find.text('Tolak'), findsOneWidget);

      await tester.tap(find.text('Setuju & Lanjutkan'));
      await tester.pumpAndSettle();

      expect(result, isTrue);
    });

    test('Passive Mode completely suppresses hasActiveOverlay', () async {
      SharedPreferences.setMockInitialValues({
        'launcher_mode': 'passive',
      });
      final prefs = await SharedPreferences.getInstance();
      final state = AppState(prefs);

      expect(state.isPassiveMode, isTrue);
      expect(state.hasActiveOverlay, isFalse);
    });
  });
}
