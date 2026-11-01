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
    test('Default mode state defaults to Standard Mode (isStrictMode = false)', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final state = AppState(prefs);

      expect(state.isStrictMode, isFalse);
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

      expect(state.isStrictMode, isFalse);
      expect(state.isStrictActiveNow, isFalse);
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

      expect(state.isStrictMode, isFalse);
      expect(state.isPassiveMode, isFalse);
      expect(state.launcherMode, equals('standard'));
      expect(prefs.getString('launcher_mode'), equals('standard'));
    });

    test('Standard Mode reflection state defaults to false and reacts to dismiss and clear', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final state = AppState(prefs);

      expect(state.isStandardReflectionActive, isFalse);
      expect(state.hasActiveOverlay, isFalse);

      // Dismiss and skip
      await state.dismissStandardReflectionAndSkip(durationMillis: 60000);
      expect(state.isStandardReflectionActive, isFalse);

      state.clearStandardReflection();
      expect(state.isStandardReflectionActive, isFalse);
    });

    test('enablePassiveMode sets launcherMode to passive and isAccessibilityRequired to false', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final state = AppState(prefs);

      await state.enablePassiveMode();
      expect(state.isStrictMode, isFalse);
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

      // Mode Pasif is present
      final badge = find.text('SANGAT TIDAK DIREKOMENDASIKAN');
      await tester.scrollUntilVisible(badge, 200);
      await tester.pumpAndSettle();
      expect(badge, findsOneWidget);
      expect(find.text('Mode Pasif'), findsOneWidget);
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
