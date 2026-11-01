import 'dart:io';

/// Konfigurasi Environment Aplikasi (Khusus Android)
/// Nilai dibaca secara aman dari compile-time environment (--dart-define-from-file=.env).
/// Jika variabel tidak disediakan, otomatis menggunakan Official Google AdMob Test IDs.
class EnvConfig {
  EnvConfig._();

  // ── AdMob Application ID (Android) ─────────────────────────────────────────
  static const String admobAppId = String.fromEnvironment(
    'ADMOB_APP_ID',
    defaultValue: 'ca-app-pub-3940256099942544~3347511713',
  );

  // ── AdMob Rewarded Ad Unit ID (Android) ────────────────────────────────────
  static const String admobRewardedAdUnitId = String.fromEnvironment(
    'ADMOB_REWARDED_AD_UNIT_ID',
    defaultValue: 'ca-app-pub-3940256099942544/5224354917',
  );

  // ── AdMob Banner Ad Unit ID (Android) ──────────────────────────────────────
  static const String admobBannerAdUnitId = String.fromEnvironment(
    'ADMOB_BANNER_AD_UNIT_ID',
    defaultValue: 'ca-app-pub-3940256099942544/6300978111',
  );

  /// Mengambil Rewarded Ad Unit ID (Khusus Android)
  static String get rewardedAdUnitId {
    if (Platform.isAndroid) {
      return admobRewardedAdUnitId;
    }
    return '';
  }

  /// Mengambil Banner Ad Unit ID (Khusus Android)
  static String get bannerAdUnitId {
    if (Platform.isAndroid) {
      return admobBannerAdUnitId;
    }
    return '';
  }
}
