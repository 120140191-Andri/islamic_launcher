import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart' hide AppState;
import '../config/env_config.dart';

class AdService {
  AdService._();
  static final AdService instance = AdService._();

  bool _isInitialized = false;
  RewardedAd? _rewardedAd;
  bool _isLoadingRewarded = false;
  Completer<RewardedAd?>? _loadingCompleter;
  int _loadAttempts = 0;
  static const int _maxLoadAttempts = 3;
  String? _lastError;

  /// Status apakah iklan rewarded sedang ada di memori dan siap ditayangkan seketika
  bool get isRewardedAdReady => _rewardedAd != null;

  /// Pesan error terakhir saat memuat / menampilkan iklan
  String? get lastError => _lastError;

  /// Ad Unit ID Rewarded (dibaca dari .env atau fallback ke test ID)
  static String get rewardedAdUnitId => EnvConfig.rewardedAdUnitId;

  /// Ad Unit ID Banner (dibaca dari .env atau fallback ke test ID)
  static String get bannerAdUnitId => EnvConfig.bannerAdUnitId;

  /// Ad Unit ID Native (dibaca dari .env atau fallback ke test ID)
  static String get nativeAdUnitId => EnvConfig.nativeAdUnitId;

  /// Inisialisasi Mobile Ads SDK (Khusus Android)
  Future<void> initialize() async {
    if (_isInitialized) return;
    try {
      if (Platform.isAndroid) {
        await MobileAds.instance.initialize();
        _isInitialized = true;
        // Langsung preload 1 iklan reward di background agar saat dibutuhkan sudah siap
        preloadRewardedAd();
      }
    } catch (e) {
      debugPrint('[AdService] MobileAds init error: $e');
    }
  }

  /// Preload Rewarded Ad agar tidak perlu menunggu saat ditekan
  void preloadRewardedAd() {
    if (!_isInitialized || _isLoadingRewarded || _rewardedAd != null) return;
    if (!Platform.isAndroid) return;

    _isLoadingRewarded = true;
    _loadingCompleter ??= Completer<RewardedAd?>();

    RewardedAd.load(
      adUnitId: rewardedAdUnitId,
      request: const AdRequest(),
      rewardedAdLoadCallback: RewardedAdLoadCallback(
        onAdLoaded: (ad) {
          debugPrint('[AdService] Rewarded ad loaded successfully.');
          _rewardedAd = ad;
          _isLoadingRewarded = false;
          _loadAttempts = 0;
          if (_loadingCompleter != null && !_loadingCompleter!.isCompleted) {
            _loadingCompleter!.complete(ad);
          }
          _loadingCompleter = null;
        },
        onAdFailedToLoad: (error) {
          _lastError = '${error.message} (Code: ${error.code})';
          debugPrint('[AdService] Rewarded ad failed to load: $_lastError');
          _rewardedAd = null;
          _isLoadingRewarded = false;
          if (_loadingCompleter != null && !_loadingCompleter!.isCompleted) {
            _loadingCompleter!.complete(null);
          }
          _loadingCompleter = null;

          _loadAttempts++;
          if (_loadAttempts < _maxLoadAttempts) {
            final delay = Duration(seconds: 2 * _loadAttempts);
            debugPrint(
              '[AdService] Will retry loading rewarded ad in $delay (attempt $_loadAttempts/$_maxLoadAttempts)',
            );
            Future.delayed(delay, () {
              preloadRewardedAd();
            });
          }
        },
      ),
    );
  }

  /// Menampilkan Rewarded Ad jika ada.
  /// Return `true` jika iklan berhasil ditayangkan dan pengguna menyelesaikan tontonan (reward earned).
  /// Return `false` jika iklan tidak tersedia / gagal tayang / offline (sehingga caller bisa langsung bypass).
  Future<bool> showRewardedAd({
    Duration timeout = const Duration(seconds: 8),
    VoidCallback? onAdReadyToShow,
  }) async {
    if (!Platform.isAndroid) return false;

    // Pastikan Mobile Ads SDK sudah terinisialisasi
    if (!_isInitialized) {
      await initialize();
    }

    // Ambil iklan yang sudah siap, atau tunggu iklan yang sedang / baru dimuat
    RewardedAd? ad = _rewardedAd;
    _rewardedAd = null; // Consume

    if (ad == null) {
      if (!_isLoadingRewarded) {
        _loadAttempts = 0; // reset untuk permintaan baru dari user
        preloadRewardedAd();
      }

      final completer = _loadingCompleter;
      if (completer != null) {
        try {
          ad = await completer.future.timeout(
            timeout,
            onTimeout: () {
              _lastError = 'Timeout ($timeout) memuat video iklan';
              debugPrint('[AdService] Rewarded ad load timeout ($timeout).');
              return null;
            },
          );
        } catch (e) {
          _lastError = 'Error waiting for rewarded ad: $e';
          debugPrint('[AdService] Error waiting for rewarded ad: $e');
          ad = null;
        }
      }
    }

    if (ad == null) {
      debugPrint(
        '[AdService] No rewarded ad available to show. Reason: $_lastError',
      );
      return false;
    }

    // Panggil callback sebelum native full screen ad mengambil alih tampilan
    onAdReadyToShow?.call();

    final adCompleter = Completer<bool>();
    bool earnedReward = false;

    ad.fullScreenContentCallback = FullScreenContentCallback(
      onAdShowedFullScreenContent: (ad) {
        debugPrint('[AdService] Rewarded ad showed full screen.');
      },
      onAdDismissedFullScreenContent: (ad) {
        debugPrint('[AdService] Rewarded ad dismissed.');
        ad.dispose();
        preloadRewardedAd(); // Siapkan untuk sesi berikutnya
        if (!adCompleter.isCompleted) {
          adCompleter.complete(earnedReward);
        }
      },
      onAdFailedToShowFullScreenContent: (ad, error) {
        _lastError = 'Show failed: ${error.message} (Code: ${error.code})';
        debugPrint('[AdService] Rewarded ad failed to show: $_lastError');
        ad.dispose();
        preloadRewardedAd();
        if (!adCompleter.isCompleted) {
          adCompleter.complete(false);
        }
      },
    );

    try {
      await ad.show(
        onUserEarnedReward: (adWithoutView, reward) {
          debugPrint(
            '[AdService] User earned reward: ${reward.amount} ${reward.type}',
          );
          earnedReward = true;
        },
      );
    } catch (e) {
      _lastError = 'Exception showing rewarded ad: $e';
      debugPrint('[AdService] Error showing rewarded ad: $e');
      preloadRewardedAd();
      return false;
    }

    return await adCompleter.future;
  }
}
