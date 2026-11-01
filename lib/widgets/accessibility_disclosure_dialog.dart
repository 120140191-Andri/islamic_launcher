import 'package:flutter/material.dart';

class AccessibilityDisclosureDialog extends StatelessWidget {
  final String lang;

  const AccessibilityDisclosureDialog({
    super.key,
    required this.lang,
  });

  /// Shows the Google Play mandatory Prominent Disclosure dialog for AccessibilityService API.
  /// Returns `true` if the user explicitly agrees, `false` otherwise.
  static Future<bool> show(BuildContext context, {required String lang}) async {
    final accepted = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AccessibilityDisclosureDialog(lang: lang),
    );
    return accepted == true;
  }

  @override
  Widget build(BuildContext context) {
    final isEn = lang != 'id' && lang != 'ms';

    final title = isEn
        ? 'AccessibilityService API Disclosure'
        : 'Pengungkapan Layanan Aksesibilitas (AccessibilityService API)';

    final intro = isEn
        ? 'Islamic Launcher uses the Android AccessibilityService API to provide its core digital wellbeing and focus features:'
        : 'Islamic Launcher menggunakan Android AccessibilityService API untuk menjalankan fitur utama fokus ibadah & kendali layar Anda:';

    final bullet1 = isEn
        ? 'Detecting when a locked non-productive app is opened so it can display the Worship Points lock screen.'
        : 'Mendeteksi saat aplikasi non-produktif yang dikunci dibuka untuk menampilkan layar kunci Poin Ibadah.';

    final bullet2 = isEn
        ? 'Displaying the Ghadhul Bashar (lowering the gaze) reminder and blocking explicit adult or gambling content on browsers.'
        : 'Menampilkan pengingat Ghadhul Bashar (menjaga pandangan) serta memblokir situs konten dewasa & judi online di peramban.';

    final privacyNote = isEn
        ? 'Privacy Guarantee: All detection is processed 100% locally on your device in real time. Islamic Launcher DOES NOT collect, store, or share any personal data, screen content, or browsing history with any external servers or third parties.'
        : 'Jaminan Privasi: Seluruh deteksi diproses 100% secara lokal di dalam perangkat Anda. Islamic Launcher TIDAK mengumpulkan, menyimpan, atau membagikan data pribadi, isi layar, maupun riwayat penelusuran Anda ke server mana pun atau pihak ketiga.';

    final declineText = isEn ? 'Decline' : 'Tolak';
    final agreeText = isEn ? 'Agree & Continue' : 'Setuju & Lanjutkan';

    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
      backgroundColor: Colors.white,
      titlePadding: const EdgeInsets.fromLTRB(22, 22, 22, 10),
      contentPadding: const EdgeInsets.fromLTRB(22, 6, 22, 16),
      actionsPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      title: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFF0D5C3A).withValues(alpha: 0.1),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.privacy_tip_rounded,
              color: Color(0xFF0D5C3A),
              size: 24,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                fontSize: 16.5,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
                height: 1.3,
              ),
            ),
          ),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              intro,
              style: const TextStyle(
                fontSize: 13,
                color: Color(0xFF334155),
                height: 1.45,
              ),
            ),
            const SizedBox(height: 12),
            _buildBullet(Icons.lock_clock_rounded, bullet1),
            const SizedBox(height: 8),
            _buildBullet(Icons.shield_rounded, bullet2),
            const SizedBox(height: 14),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFF0FDF4),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: const Color(0xFF86EFAC),
                ),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(
                    Icons.verified_user_rounded,
                    size: 18,
                    color: Color(0xFF0D5C3A),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      privacyNote,
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF14532D),
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      actions: [
        Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: () => Navigator.of(context).pop(false),
                style: OutlinedButton.styleFrom(
                  foregroundColor: Colors.grey.shade700,
                  side: BorderSide(color: Colors.grey.shade300),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: Text(
                  declineText,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              flex: 2,
              child: ElevatedButton(
                onPressed: () => Navigator.of(context).pop(true),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF0D5C3A),
                  foregroundColor: Colors.white,
                  elevation: 0,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: Text(
                  agreeText,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                  ),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildBullet(IconData icon, String text) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 2),
          child: Icon(icon, size: 16, color: const Color(0xFF0D5C3A)),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              fontSize: 12.5,
              color: Color(0xFF334155),
              height: 1.4,
            ),
          ),
        ),
      ],
    );
  }
}

