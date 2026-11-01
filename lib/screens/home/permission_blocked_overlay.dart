import 'package:flutter/material.dart';
import '../../providers/app_state.dart';
import '../onboarding/setup_hub_screen.dart';
import '../../utils/page_transitions.dart';
import '../../utils/translations.dart';

class PermissionBlockedOverlay extends StatelessWidget {
  final AppState appState;
  final Widget child;

  const PermissionBlockedOverlay({
    super.key,
    required this.appState,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    // In passive mode, accessibility is not required/prompted
    if (appState.isPassiveMode) return child;

    // Only enforce strict checks AFTER onboarding is completed
    if (!appState.hasCompletedOnboarding) return child;
    if (appState.ignorePermissionGuard) return child;

    final isMissingDefault = !appState.isDefaultLauncher;
    final isMissingAccess = appState.isAccessibilityRequired && !appState.isAccessibilityEnabled;

    if (!isMissingDefault && !isMissingAccess) return child;

    final lang = appState.languageCode;

    return Stack(
      children: [
        child,
        // The Blocking Overlay
        Positioned.fill(
          child: Container(
            color: Colors.black.withValues(alpha: 0.85),
            child: Material(
              color: Colors.transparent,
              child: SafeArea(
                child: Center(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 32),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          padding: const EdgeInsets.all(24),
                          decoration: BoxDecoration(
                            color: Colors.amber.shade50,
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            Icons.security_rounded,
                            color: Colors.amber.shade800,
                            size: 64,
                          ),
                        ),
                        const SizedBox(height: 32),
                        Text(
                          Translations.get(lang, 'action_required'),
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 28,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          Translations.get(lang, 'permission_disabled_desc'),
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.8),
                            fontSize: 16,
                            height: 1.5,
                          ),
                        ),
                        const SizedBox(height: 24),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: const Color(0xFFECFDF5),
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: const Color(0xFF34D399),
                              width: 1.5,
                            ),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  const Icon(
                                    Icons.shield_rounded,
                                    color: Color(0xFF0D5C3A),
                                    size: 20,
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    Translations.get(lang, 'standard_mode_title'),
                                    style: const TextStyle(
                                      fontSize: 15,
                                      fontWeight: FontWeight.bold,
                                      color: Color(0xFF064E3B),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Flexible(
                                    child: Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 8,
                                        vertical: 3,
                                      ),
                                      decoration: BoxDecoration(
                                        color: const Color(0xFF0D5C3A),
                                        borderRadius: BorderRadius.circular(8),
                                      ),
                                      child: Text(
                                        Translations.get(
                                          lang,
                                          'standard_mode_badge',
                                        ),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(
                                          fontSize: 9.5,
                                          fontWeight: FontWeight.bold,
                                          color: Colors.white,
                                        ),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                Translations.get(lang, 'standard_mode_desc'),
                                style: const TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w500,
                                  color: Color(0xFF065F46),
                                  height: 1.4,
                                ),
                              ),
                              const SizedBox(height: 14),
                              _buildRepairButton(
                                icon: isMissingAccess
                                    ? Icons.accessibility_new_rounded
                                    : Icons.tune_rounded,
                                label: Translations.get(
                                  lang,
                                  isMissingAccess
                                      ? 'reactivate_accessibility_btn'
                                      : 'open_setup_hub',
                                ),
                                onTap: () {
                                  appState.setIgnorePermissionGuard(true);
                                  appState.navigatorKey.currentState
                                      ?.push(
                                        AppPageRoute(
                                          child: const SetupHubScreen(
                                            isOnboarding: false,
                                          ),
                                        ),
                                      )
                                      .then(
                                        (_) => appState
                                            .setIgnorePermissionGuard(false),
                                      );
                                },
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: const Color(0xFFFFFBEB),
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: Colors.amber.shade400,
                              width: 1.5,
                            ),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Icon(
                                    Icons.warning_amber_rounded,
                                    color: Colors.amber.shade900,
                                    size: 20,
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      Translations.get(
                                        lang,
                                        'passive_mode_tradeoff_title',
                                      ),
                                      style: const TextStyle(
                                        fontSize: 13,
                                        fontWeight: FontWeight.bold,
                                        color: Color(0xFF78350F),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                Translations.get(lang, 'passive_mode_tradeoff'),
                                style: const TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w600,
                                  color: Color(0xFF92400E),
                                  height: 1.4,
                                ),
                              ),
                              const SizedBox(height: 14),
                              SizedBox(
                                width: double.infinity,
                                height: 48,
                                child: ElevatedButton.icon(
                                  onPressed: () async {
                                    await appState.enablePassiveMode();
                                  },
                                  icon: const Icon(
                                    Icons.shield_outlined,
                                    size: 18,
                                  ),
                                  label: FittedBox(
                                    fit: BoxFit.scaleDown,
                                    child: Text(
                                      Translations.get(
                                        lang,
                                        'switch_to_passive_mode_btn',
                                      ).toUpperCase(),
                                      style: const TextStyle(
                                        fontWeight: FontWeight.bold,
                                        letterSpacing: 0.8,
                                      ),
                                    ),
                                  ),
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: const Color(0xFFD97706),
                                    foregroundColor: Colors.white,
                                    elevation: 0,
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildRepairButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: 50),
      child: ElevatedButton.icon(
        onPressed: onTap,
        icon: Icon(icon, size: 20),
        label: FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(
            label.toUpperCase(),
            style: const TextStyle(fontWeight: FontWeight.bold, letterSpacing: 0.8),
          ),
        ),
        style: ElevatedButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
          backgroundColor: const Color(0xFF0D5C3A),
          foregroundColor: Colors.white,
          elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
    );
  }
}
