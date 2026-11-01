import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../providers/app_state.dart';
import '../../utils/page_transitions.dart';
import '../../utils/translations.dart';
import 'setup_hub_screen.dart';
import 'setup_launcher_screen.dart';

class ModeSelectionScreen extends StatefulWidget {
  final bool isOnboarding;
  const ModeSelectionScreen({super.key, this.isOnboarding = false});

  @override
  State<ModeSelectionScreen> createState() => _ModeSelectionScreenState();
}

class _ModeSelectionScreenState extends State<ModeSelectionScreen> {
  String _selectedMode = 'standard'; // 'standard', 'passive'
  bool _isProcessing = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final appState = Provider.of<AppState>(context, listen: false);
      if (appState.isPassiveMode) {
        setState(() {
          _selectedMode = 'passive';
        });
      } else {
        setState(() {
          _selectedMode = 'standard';
        });
      }
    });
  }

  Future<void> _handleConfirm() async {
    final appState = Provider.of<AppState>(context, listen: false);

    if (_selectedMode == 'standard') {
      setState(() => _isProcessing = true);
      await appState.enableStandardMode();

      if (!mounted) return;
      if (widget.isOnboarding) {
        appState.navigatorKey.currentState?.pushReplacement(
          AppPageRoute(child: const SetupHubScreen(isOnboarding: true)),
        );
      } else {
        Navigator.maybePop(context);
      }
    } else {
      // Mode Pasif
      setState(() => _isProcessing = true);
      await appState.enablePassiveMode();

      if (!mounted) return;
      if (widget.isOnboarding) {
        appState.navigatorKey.currentState?.pushReplacement(
          AppPageRoute(child: const SetupLauncherScreen(isSingleStep: true)),
        );
      } else {
        Navigator.maybePop(context);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final appState = Provider.of<AppState>(context);
    final lang = appState.languageCode;
    final isEn = lang == 'en';

    return Scaffold(
      backgroundColor: const Color(0xFFF7FAF8),
      body: Column(
        children: [
          // ── Top Header ──────────────────────────────────────────────────
          Container(
            width: double.infinity,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFF0F5E3B), Color(0xFF083C25)],
              ),
              borderRadius: const BorderRadius.only(
                bottomLeft: Radius.circular(30),
                bottomRight: Radius.circular(30),
              ),
              boxShadow: [
                BoxShadow(
                  color: const Color(0xFF0D5C3A).withValues(alpha: 0.25),
                  blurRadius: 18,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
            child: SafeArea(
              bottom: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 26),
                child: Column(
                  children: [
                    Row(
                      children: [
                        if (!widget.isOnboarding)
                          IconButton(
                            icon: const Icon(
                              Icons.arrow_back_ios_new_rounded,
                              color: Colors.white,
                              size: 20,
                            ),
                            onPressed: () => Navigator.maybePop(context),
                          )
                        else
                          const SizedBox(width: 4),
                        Expanded(
                          child: Text(
                            Translations.get(lang, 'mode_selection_title'),
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                              fontSize: 18,
                              letterSpacing: -0.3,
                            ),
                          ),
                        ),
                        if (!widget.isOnboarding)
                          const SizedBox(width: 48)
                        else
                          const SizedBox(width: 4),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Text(
                      Translations.get(lang, 'mode_selection_subtitle'),
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.85),
                        fontSize: 13,
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),

          // ── Mode Cards List (2 Modes: Standard & Passive) ───────────────
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(18, 20, 18, 20),
              children: [
                // 1. MODE STANDAR CARD (RECOMMENDED DEFAULT)
                _buildModeCard(
                  isSelected: _selectedMode == 'standard',
                  title: Translations.get(lang, 'standard_mode_title'),
                  badgeText: Translations.get(lang, 'standard_mode_badge'),
                  isRecommended: true,
                  icon: Icons.shield_rounded,
                  onTap: () => setState(() => _selectedMode = 'standard'),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        Translations.get(lang, 'standard_mode_desc'),
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.grey.shade700,
                          height: 1.45,
                        ),
                      ),
                      const SizedBox(height: 14),
                      _buildCheckItem(
                        icon: Icons.lock_clock_rounded,
                        text: isEn
                            ? 'Locks non-productive apps (unlocked with Worship Points)'
                            : 'Kunci aplikasi non-produktif (buka dengan Poin Ibadah)',
                      ),
                      const SizedBox(height: 8),
                      _buildCheckItem(
                        icon: Icons.visibility_off_rounded,
                        text: isEn
                            ? 'Ghadhul Bashar reminder & adult/gambling content shield'
                            : 'Pengingat Ghadhul Bashar & perisai konten dewasa/judi',
                      ),
                      const SizedBox(height: 8),
                      _buildCheckItem(
                        icon: Icons.self_improvement_rounded,
                        text: isEn
                            ? 'Mindful reflection prompt with full user control'
                            : 'Renungan bijak dengan kendali penuh di tangan Anda',
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 16),

                // 2. MODE PASIF CARD
                _buildModeCard(
                  isSelected: _selectedMode == 'passive',
                  title: Translations.get(lang, 'passive_mode_title'),
                  badgeText: '',
                  isRecommended: false,
                  isDanger: true,
                  icon: Icons.gpp_maybe_rounded,
                  onTap: () => setState(() => _selectedMode = 'passive'),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        Translations.get(lang, 'passive_mode_desc'),
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.grey.shade700,
                          height: 1.45,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 9,
                        ),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFFFBEB),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: Colors.amber.shade300),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(
                              Icons.warning_amber_rounded,
                              size: 18,
                              color: Colors.amber.shade900,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                Translations.get(lang, 'passive_mode_tradeoff'),
                                style: const TextStyle(
                                  fontSize: 11.5,
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFF78350F),
                                  height: 1.35,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // ── Bottom Confirmation Button ──────────────────────────────────
          Container(
            padding: EdgeInsets.fromLTRB(
              20,
              14,
              20,
              14 +
                  math.max(
                    MediaQuery.of(context).padding.bottom,
                    MediaQuery.of(context).viewPadding.bottom,
                  ),
            ),
            decoration: BoxDecoration(
              color: Colors.white,
              border: Border(
                top: BorderSide(
                  color: Colors.black.withValues(alpha: 0.05),
                  width: 1,
                ),
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.04),
                  blurRadius: 10,
                  offset: const Offset(0, -4),
                ),
              ],
            ),
            child: SizedBox(
              width: double.infinity,
              height: 54,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(16),
                  gradient: _selectedMode == 'passive'
                      ? const LinearGradient(
                          colors: [Color(0xFFD97706), Color(0xFFB45309)],
                        )
                      : const LinearGradient(
                          colors: [Color(0xFF0F5E3B), Color(0xFF094027)],
                        ),
                  boxShadow: [
                    BoxShadow(
                      color: _selectedMode == 'passive'
                          ? const Color(0xFFB45309).withValues(alpha: 0.28)
                          : const Color(0xFF0D5C3A).withValues(alpha: 0.28),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: ElevatedButton(
                  onPressed: _isProcessing ? null : _handleConfirm,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.transparent,
                    foregroundColor: Colors.white,
                    shadowColor: Colors.transparent,
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        _selectedMode == 'standard'
                            ? Translations.get(lang, 'start_standard_mode')
                            : Translations.get(lang, 'start_passive_mode'),
                        style: const TextStyle(
                          fontSize: 13.5,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 0.6,
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Icon(Icons.arrow_forward_rounded, size: 20),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildModeCard({
    required bool isSelected,
    required String title,
    required String badgeText,
    required bool isRecommended,
    required IconData icon,
    required VoidCallback onTap,
    required Widget child,
    bool isDanger = false,
  }) {
    final activeColor = isDanger
        ? const Color(0xFFD97706)
        : const Color(0xFF0D5C3A);

    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeInOut,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: isSelected
              ? activeColor
              : (isDanger
                    ? Colors.amber.shade300
                    : Colors.black.withValues(alpha: 0.08)),
          width: isSelected ? 2.2 : 1,
        ),
        boxShadow: [
          BoxShadow(
            color: isSelected
                ? activeColor.withValues(alpha: 0.12)
                : Colors.black.withValues(alpha: 0.03),
            blurRadius: 14,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(22),
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: isSelected
                            ? activeColor
                            : (isDanger
                                  ? Colors.amber.shade50
                                  : Colors.grey.shade100),
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        icon,
                        color: isSelected
                            ? Colors.white
                            : (isDanger
                                  ? Colors.amber.shade800
                                  : Colors.grey.shade600),
                        size: 20,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Text(
                                title,
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                  color: isSelected
                                      ? activeColor
                                      : const Color(0xFF1E293B),
                                ),
                              ),
                              if (badgeText.isNotEmpty) ...[
                                const SizedBox(width: 8),
                                Flexible(
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: isDanger
                                          ? Colors.amber.shade50
                                          : (isRecommended
                                                ? const Color(
                                                    0xFF0D5C3A,
                                                  ).withValues(alpha: 0.1)
                                                : Colors.grey.shade200),
                                      borderRadius: BorderRadius.circular(8),
                                      border: isDanger
                                          ? Border.all(
                                              color: Colors.amber.shade300,
                                              width: 0.8,
                                            )
                                          : null,
                                    ),
                                    child: Text(
                                      badgeText,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        fontSize: 9.5,
                                        fontWeight: FontWeight.bold,
                                        color: isDanger
                                            ? const Color(0xFF78350F)
                                            : (isRecommended
                                                  ? const Color(0xFF0D5C3A)
                                                  : Colors.grey.shade700),
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ],
                      ),
                    ),
                    Container(
                      width: 22,
                      height: 22,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: isSelected
                              ? activeColor
                              : (isDanger
                                    ? Colors.amber.shade400
                                    : Colors.grey.shade400),
                          width: 2,
                        ),
                      ),
                      child: isSelected
                          ? Center(
                              child: Container(
                                width: 12,
                                height: 12,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: activeColor,
                                ),
                              ),
                            )
                          : null,
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                child,
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildCheckItem({required IconData icon, required String text}) {
    return Row(
      children: [
        Icon(icon, color: const Color(0xFF0D5C3A), size: 16),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              fontSize: 12.5,
              fontWeight: FontWeight.w600,
              color: Color(0xFF334155),
            ),
          ),
        ),
      ],
    );
  }
}
