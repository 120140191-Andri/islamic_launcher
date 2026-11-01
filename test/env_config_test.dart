import 'package:flutter_test/flutter_test.dart';
import 'package:islamic_launcher/config/env_config.dart';
import 'package:islamic_launcher/services/ad_service.dart';

void main() {
  group('EnvConfig & AdMob IDs Tests (Khusus Android)', () {
    test('EnvConfig provides valid default AdMob App ID', () {
      expect(EnvConfig.admobAppId, isNotEmpty);
      expect(EnvConfig.admobAppId, contains('~'));
    });

    test('EnvConfig provides valid default Rewarded Ad Unit ID for Android', () {
      expect(EnvConfig.admobRewardedAdUnitId, isNotEmpty);
      expect(EnvConfig.admobRewardedAdUnitId, contains('/'));
    });

    test('EnvConfig provides valid default Banner Ad Unit ID for Android', () {
      expect(EnvConfig.admobBannerAdUnitId, isNotEmpty);
      expect(EnvConfig.admobBannerAdUnitId, contains('/'));
    });

    test('AdService correctly delegates to EnvConfig values', () {
      expect(AdService.rewardedAdUnitId, equals(EnvConfig.rewardedAdUnitId));
      expect(AdService.bannerAdUnitId, equals(EnvConfig.bannerAdUnitId));
    });
  });
}
