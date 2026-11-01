package com.kraftech.islamic_launcher

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.os.Build
import android.content.IntentFilter
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

class AppBlockService : AccessibilityService() {
    companion object {
        private var instance: AppBlockService? = null
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private val backgroundExecutor = java.util.concurrent.Executors.newFixedThreadPool(4)
        private var blockedPackages = Collections.synchronizedSet(mutableSetOf<String>())
        private var temporaryAllowedPackages = ConcurrentHashMap<String, Long>()
        
        // Countdown vibration alert tracking (3m -> 3x, 2m -> 2x, final kick -> 1x long ending at exit)
        private val notified3Min = Collections.synchronizedSet(mutableSetOf<String>())
        private val notified2Min = Collections.synchronizedSet(mutableSetOf<String>())
        private val notified1Min = Collections.synchronizedSet(mutableSetOf<String>())
        private const val FINAL_VIBRATION_MS = 1200L
        
        // Ghadhul Bashar state tracking
        private var ghadhulBasharPackages = Collections.synchronizedSet(mutableSetOf<String>())
        private var activeGhadhulSessions = Collections.synchronizedSet(mutableSetOf<String>())
        private var lastActiveGhadhulTimes = ConcurrentHashMap<String, Long>()

        // Prohibited bypass browsers (Permanently blocked in restricted regions)
        private var prohibitedPackages = Collections.synchronizedSet(mutableSetOf<String>())
        @Volatile
        private var lastProhibitedTriggerTime: Long = 0L
        @Volatile
        private var lastProhibitedBringToFrontTime: Long = 0L

        // Transition Shield: Prevent loop during the first 10 seconds of unlock
        private var lastBypassPackage: String? = null
        private var lastBypassTime: Long = 0
        private var currentForegroundPackage: String? = null

        // One-time bypass for opening Developer Support (Trakteer / Ko-fi) links
        @Volatile
        private var bypassSupportDeveloperUntil: Long = 0L

        @Volatile
        var hasCompletedOnboarding: Boolean = false

        fun setOnboardingCompleted(context: Context, completed: Boolean) {
            hasCompletedOnboarding = completed
            val prefs = context.getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("hasCompletedOnboarding", completed).apply()
            Log.d("AppBlockService", "ONBOARDING STATUS UPDATED: completed=$completed")
        }

        fun isOnboardingCompleted(context: Context): Boolean {
            if (hasCompletedOnboarding) return true
            try {
                val blockPrefs = context.getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
                if (blockPrefs.getBoolean("hasCompletedOnboarding", false)) {
                    hasCompletedOnboarding = true
                    return true
                }
            } catch (_: Exception) {}
            try {
                val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                if (flutterPrefs.getBoolean("flutter.hasCompletedOnboarding", false)) {
                    hasCompletedOnboarding = true
                    return true
                }
            } catch (_: Exception) {}
            return false
        }

        fun prepareSupportDeveloperBypass() {
            bypassSupportDeveloperUntil = System.currentTimeMillis() + 10000L
            Log.d("AppBlockService", "SUPPORT DEVELOPER BYPASS ARMED (10s window)")
        }

        // Debounce to prevent multiple back-to-back startActivity calls on rapid accessibility events
        private var lastTriggeredPackage: String? = null
        private var lastTriggeredTime: Long = 0

        private val watchdogRunnable = object : Runnable {
            override fun run() {
                if (temporaryAllowedPackages.isNotEmpty()) {
                    checkCountdownVibrations()
                    checkAllExpiredUnlocks()
                    if (temporaryAllowedPackages.isNotEmpty()) {
                        handler.postDelayed(this, 1000L)
                    }
                }
            }
        }

        fun startWatchdog() {
            handler.removeCallbacks(watchdogRunnable)
            if (temporaryAllowedPackages.isNotEmpty()) {
                handler.post(watchdogRunnable)
            }
        }

        fun checkAndKickExpiredApp(packageName: String) {
            val s = instance ?: return
            val cleanPkg = packageName.trim().lowercase()
            val now = System.currentTimeMillis()
            val expiry = temporaryAllowedPackages[cleanPkg]
            if (expiry != null && now >= (expiry - 300L)) {
                temporaryAllowedPackages.remove(cleanPkg)
                notified3Min.remove(cleanPkg)
                notified2Min.remove(cleanPkg)
                notified1Min.remove(cleanPkg)
                
                // Remove from SharedPreferences
                try {
                    val prefs = s.getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
                    val allowedMap = (prefs.getStringSet("allowed_temp_packages", emptySet()) ?: emptySet()).toMutableSet()
                    val filtered = allowedMap.filterNot { it.startsWith("$cleanPkg|") }.toSet()
                    prefs.edit().putStringSet("allowed_temp_packages", filtered).commit()
                } catch (e: Exception) {}

                // Reset shields & debounces so kick is immediate and cannot be bypassed
                if (lastBypassPackage == cleanPkg) {
                    lastBypassPackage = null
                    lastBypassTime = 0L
                }
                if (lastTriggeredPackage == cleanPkg) {
                    lastTriggeredPackage = null
                    lastTriggeredTime = 0L
                }

                if (blockedPackages.contains(cleanPkg)) {
                    val isForeground = currentForegroundPackage?.equals(cleanPkg, ignoreCase = true) == true
                    if (isForeground) {
                        Log.d("AppBlockService", "EXPIRED: Kicking $cleanPkg (foreground=$currentForegroundPackage)")
                        MainActivity.notifyAppBlocked(cleanPkg)
                        s.bringLauncherToFront("blockedPackageName", cleanPkg, "triggerBlockScreen")
                    } else {
                        Log.d("AppBlockService", "EXPIRED: Silently relocked $cleanPkg (not in foreground, foreground=$currentForegroundPackage)")
                    }
                }
            }
        }

        /**
         * Scans all temporary unlocks and kicks any that have expired.
         */
        fun checkAllExpiredUnlocks() {
            val s = instance ?: return
            val now = System.currentTimeMillis()
            val expiredPackages = mutableListOf<String>()
            for ((pkg, expiry) in temporaryAllowedPackages) {
                if (now >= (expiry - 300L)) {
                    expiredPackages.add(pkg)
                }
            }
            for (pkg in expiredPackages) {
                checkAndKickExpiredApp(pkg)
            }
        }

        /**
         * Checks remaining time for temporarily allowed apps and triggers countdown vibrations:
         * 3 minutes remaining -> 3 vibrations (300ms each)
         * 2 minutes remaining -> 2 vibrations (600ms each)
         * Final countdown     -> 1 long vibration (1200ms) that finishes EXACTLY when the app is kicked out!
         * Only triggers if the app is currently in the foreground.
         */
        fun checkCountdownVibrations() {
            val s = instance ?: return
            val now = System.currentTimeMillis()
            for ((pkg, expiry) in temporaryAllowedPackages) {
                // Only alert if the user is currently using this unlocked app
                val isForeground = currentForegroundPackage?.equals(pkg, ignoreCase = true) == true
                if (!isForeground) continue

                val remaining = expiry - now
                if (remaining in 120_001..180_000) {
                    if (!notified3Min.contains(pkg)) {
                        notified3Min.add(pkg)
                        vibrateAlert(s, 3)
                        Log.d("AppBlockService", "COUNTDOWN VIBRATION: 3m remaining for $pkg (3x)")
                    }
                } else if (remaining in 60_001..120_000) {
                    if (!notified2Min.contains(pkg)) {
                        notified2Min.add(pkg)
                        vibrateAlert(s, 2)
                        Log.d("AppBlockService", "COUNTDOWN VIBRATION: 2m remaining for $pkg (2x)")
                    }
                } else if (remaining in 1..FINAL_VIBRATION_MS) {
                    if (!notified1Min.contains(pkg)) {
                        notified1Min.add(pkg)
                        val exactDuration = remaining.coerceIn(200L, FINAL_VIBRATION_MS)
                        vibrateSinglePulse(s, exactDuration)
                        Log.d("AppBlockService", "COUNTDOWN VIBRATION: Final $exactDuration ms for $pkg (finishes at kick)")
                    }
                }
            }
        }

        fun triggerFinalKickVibration(packageName: String) {
            val s = instance ?: return
            val cleanPkg = packageName.trim().lowercase()
            val expiry = temporaryAllowedPackages[cleanPkg] ?: return
            val now = System.currentTimeMillis()
            val remaining = expiry - now

            if (remaining > 0 && !notified1Min.contains(cleanPkg)) {
                val isForeground = currentForegroundPackage?.equals(cleanPkg, ignoreCase = true) == true
                if (isForeground) {
                    notified1Min.add(cleanPkg)
                    val exactDuration = remaining.coerceIn(200L, FINAL_VIBRATION_MS)
                    vibrateSinglePulse(s, exactDuration)
                    Log.d("AppBlockService", "FINAL KICK VIBRATION: $cleanPkg for ${exactDuration}ms (finishes exactly at kick)")
                }
            }
        }

        fun vibrateSinglePulse(context: Context, durationMs: Long) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                } ?: return

                if (!vibrator.hasVibrator()) return

                val timings = longArrayOf(0L, durationMs)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = if (vibrator.hasAmplitudeControl()) {
                        val amplitudes = intArrayOf(0, 255)
                        VibrationEffect.createWaveform(timings, amplitudes, -1)
                    } else {
                        VibrationEffect.createWaveform(timings, -1)
                    }
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            } catch (e: Exception) {
                Log.e("AppBlockService", "vibrateSinglePulse failed: ${e.message}")
            }
        }

        fun vibrateAlert(context: Context, count: Int) {
            try {
                if (count == 1) {
                    vibrateSinglePulse(context, FINAL_VIBRATION_MS)
                    return
                }

                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                } ?: return

                if (!vibrator.hasVibrator()) return

                // Semakin sedikit jumlahnya, semakin panjang durasi getarnya:
                // 3x getar (sisa 3m) -> 300ms getar, 150ms jeda (3 ketukan tegas)
                // 2x getar (sisa 2m) -> 600ms getar, 200ms jeda (2 getaran mantap & lebih panjang)
                val pulseMs = when (count) {
                    3 -> 300L
                    2 -> 600L
                    else -> 400L
                }
                val sleepMs = when (count) {
                    3 -> 150L
                    2 -> 200L
                    else -> 200L
                }

                val timings = LongArray(count * 2)
                timings[0] = 0L
                for (i in 0 until count) {
                    timings[i * 2 + 1] = pulseMs
                    if (i < count - 1) {
                        timings[(i + 1) * 2] = sleepMs
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = if (vibrator.hasAmplitudeControl()) {
                        // Kekuatan getar maksimal (255)
                        val amplitudes = IntArray(timings.size) { index ->
                            if (index % 2 == 1) 255 else 0
                        }
                        VibrationEffect.createWaveform(timings, amplitudes, -1)
                    } else {
                        VibrationEffect.createWaveform(timings, -1)
                    }
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, -1)
                }
            } catch (e: Exception) {
                Log.e("AppBlockService", "vibrateAlert failed: ${e.message}")
            }
        }

        fun updateBlockedPackages(context: Context, packages: List<String>) {
            Log.d("AppBlockService", "Flutter updateBlockedPackages: ${packages.size} apps")
            blockedPackages.clear()
            for (p in packages) {
                val clean = p.trim().lowercase()
                if (clean.isNotEmpty()) {
                    blockedPackages.add(clean)
                }
            }
            
            // Persist to SharedPreferences
            val prefs = context.getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("blocked_packages", blockedPackages.toSet()).commit()
        }

        fun allowTemporarily(context: Context, packageName: String, durationMillis: Long) {
            val pkg = packageName.trim().lowercase()
            val now = System.currentTimeMillis()
            val expiry = now + durationMillis
            
            temporaryAllowedPackages[pkg] = expiry
            
            // Activate Shield & Debounce
            lastBypassPackage = pkg
            lastBypassTime = now
            lastTriggeredPackage = pkg
            lastTriggeredTime = now
            
            // Reset countdown notification state
            notified3Min.remove(pkg)
            notified2Min.remove(pkg)
            notified1Min.remove(pkg)
            if (durationMillis <= 180_000L) notified3Min.add(pkg)
            if (durationMillis <= 120_000L) notified2Min.add(pkg)
            if (durationMillis <= FINAL_VIBRATION_MS) notified1Min.add(pkg)
            
            // Persist to SharedPreferences to prevent loss on service restart
            val prefs = context.getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
            val allowedMap = (prefs.getStringSet("allowed_temp_packages", emptySet()) ?: emptySet()).toMutableSet()
            allowedMap.add("$pkg|$expiry")
            prefs.edit().putStringSet("allowed_temp_packages", allowedMap).commit()
            
            // Schedule final vibration right before kick so it finishes exactly when app is kicked
            val delayForFinalVibe = durationMillis - FINAL_VIBRATION_MS
            if (delayForFinalVibe > 0) {
                handler.postDelayed({
                    triggerFinalKickVibration(pkg)
                }, delayForFinalVibe)
            }

            // Schedule instant kick when duration expires & activate continuous watchdog
            startWatchdog()
            handler.postDelayed({
                checkAndKickExpiredApp(pkg)
            }, durationMillis)
            
            Log.d("AppBlockService", "ALLOW_TEMP: $pkg until $expiry (Shield ON, Scheduler Active)")
        }

        fun updateGhadhulBasharPackages(context: Context, packages: List<String>) {
            Log.d("AppBlockService", "Flutter updateGhadhulBasharPackages: ${packages.size} apps")
            ghadhulBasharPackages.clear()
            for (p in packages) {
                ghadhulBasharPackages.add(p.trim().lowercase())
            }
            val prefs = context.getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("ghadhul_bashar_packages", ghadhulBasharPackages.toSet()).commit()
        }

        fun updateProhibitedPackages(context: Context, packages: List<String>) {
            Log.d("AppBlockService", "Flutter updateProhibitedPackages: ${packages.size} apps")
            prohibitedPackages.clear()
            for (p in packages) {
                prohibitedPackages.add(p.trim().lowercase())
            }
            val prefs = context.getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("prohibited_packages", prohibitedPackages.toSet()).commit()
        }

        private fun getTopmostNode(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            var curr = node ?: return null
            try {
                while (curr.parent != null) {
                    curr = curr.parent
                }
            } catch (_: Exception) {}
            return curr
        }

        private var facebookBrowserSessionActive = ConcurrentHashMap<String, Boolean>()
        private var isFbBrowserActive = ConcurrentHashMap<String, Boolean>()
        private var pendingGhadhulRunnable: Runnable? = null
        private var pendingGhadhulPackage: String? = null

        fun cancelPendingGhadhulVerification() {
            pendingGhadhulRunnable?.let { handler.removeCallbacks(it) }
            pendingGhadhulRunnable = null
            pendingGhadhulPackage = null
        }

        fun allowGhadhulBasharSession(packageName: String) {
            val pkg = packageName.trim().lowercase()
            cancelPendingGhadhulVerification()
            activeGhadhulSessions.add(pkg)
            facebookBrowserSessionActive[pkg] = true
            val now = System.currentTimeMillis()
            lastActiveGhadhulTimes[pkg] = now
            lastBypassPackage = pkg
            lastBypassTime = now
            lastTriggeredPackage = pkg
            lastTriggeredTime = now
            Log.d("AppBlockService", "GHADHUL SESSION ALLOWED: $pkg (Shield & Debounce Active, fbSessionActive=true)")
        }

        fun resetGhadhulBasharSession(packageName: String) {
            val pkg = packageName.trim().lowercase()
            cancelPendingGhadhulVerification()
            activeGhadhulSessions.remove(pkg)
            lastActiveGhadhulTimes.remove(pkg)
            facebookBrowserSessionActive.remove(pkg)
            isFbBrowserActive.remove(pkg)
            Log.d("AppBlockService", "GHADHUL SESSION RESET: $pkg")
        }

        fun isKeyboardOrTransientOverlay(pkg: String?): Boolean {
            if (pkg == null) return true
            val clean = pkg.trim().lowercase()
            return clean.contains("inputmethod") ||
                   clean.contains(".ime") ||
                   clean.contains("keyboard") ||
                   clean.contains("honeyboard") ||
                   clean.contains("swiftkey") ||
                   clean.contains("fleksy") ||
                   clean == "com.google.android.gms" ||
                   clean == "com.android.intentresolver"
        }

        fun isKnownBrowser(pkg: String): Boolean {
            val clean = pkg.trim().lowercase()
            return clean.contains("browser") ||
                   clean.contains("chrome") ||
                   clean.contains("firefox") ||
                   clean.contains("opera") ||
                   clean.contains("duckduckgo") ||
                   clean.contains("vivaldi") ||
                   clean == "com.microsoft.emmx" ||
                   clean == "org.torproject.torbrowser" ||
                   clean == "com.cloudmosa.puffinfree"
        }

        fun isSocialMediaPackage(pkg: String?): Boolean {
            if (pkg == null) return false
            val clean = pkg.trim().lowercase()
            return clean == "com.facebook.katana" ||
                   clean == "com.facebook.lite" ||
                   clean == "com.facebook.orca" ||
                   clean == "com.facebook.mlite" ||
                   clean == "com.instagram.android" ||
                   clean == "com.instagram.lite" ||
                   clean == "com.instagram.threadsapp" ||
                   clean == "com.twitter.android" ||
                   clean == "com.twitter.android.lite" ||
                   clean == "com.zhiliaoapp.musically" ||
                   clean == "com.zhiliaoapp.musically.go" ||
                   clean == "com.ss.android.ugc.trill" ||
                   clean == "com.ss.android.ugc.aweme"
        }

        fun isFacebookPackage(pkg: String?): Boolean = isSocialMediaPackage(pkg)

        fun isSocialInAppBrowser(packageName: String, className: String): Boolean {
            if (!isSocialMediaPackage(packageName)) return false

            val lowerClass = className.lowercase()
            return lowerClass.contains("webview") ||
                   lowerClass.contains("webkit") ||
                   lowerClass.contains("browserlite") ||
                   lowerClass.contains("browser.lite") ||
                   lowerClass.contains("fbchromeactivity") ||
                   lowerClass.contains("inappbrowser") ||
                   lowerClass.contains("browseractivity") ||
                   lowerClass.contains("browserproxy") ||
                   lowerClass.contains("customtab") ||
                   lowerClass.contains("customtabs") ||
                   lowerClass.contains("crossplatform") ||
                   lowerClass.contains("bullet") ||
                   lowerClass.contains("adbrowser") ||
                   lowerClass.contains("webactivity") ||
                   lowerClass.contains("webviewactivity") ||
                   lowerClass.contains("urlhandleractivity") ||
                   (lowerClass.contains("browser") && (lowerClass.contains("activity") || lowerClass.contains("chrome") || lowerClass.contains("view")))
        }

        fun isFacebookInAppBrowser(packageName: String, className: String): Boolean = isSocialInAppBrowser(packageName, className)

        fun isSocialMainFeedActivity(className: String): Boolean {
            val lower = className.lowercase()
            if (lower.startsWith("android.widget.") ||
                lower.startsWith("android.view.") ||
                lower.startsWith("androidx.") ||
                lower.contains("dialog") ||
                lower.contains("popup") ||
                lower.contains("menu") ||
                lower.contains("browser") ||
                lower.contains("customtab") ||
                lower.contains("crossplatform") ||
                lower.contains("bullet") ||
                lower.contains("webactivity") ||
                lower.contains("webview")) {
                return false
            }
            return lower.contains("maintabactivity") ||
                   lower.contains("feedactivity") ||
                   lower.contains("loginactivity") ||
                   lower.endsWith(".mainactivity") ||
                   lower.contains("newsfeed") ||
                   lower.contains("tabactivity") ||
                   lower.contains("storyvieweractivity") ||
                   lower.contains("videohomeactivity") ||
                   lower.contains("hometimeline") ||
                   lower.contains("timelineactivity") ||
                   lower.contains("mainactivity") ||
                   lower.contains("splashactivity") ||
                   lower.contains("detailactivity") ||
                   lower.contains("launcheractivity")
        }

        fun hasWebViewInWindow(root: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (root == null) return false
            val queue = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited < 80) {
                val current = queue.poll() ?: break
                visited++
                val cls = current.className?.toString()?.lowercase() ?: ""
                if (cls.contains("webview") || cls.contains("webcontent") || cls.contains("webkit")) {
                    return true
                }
                for (i in 0 until current.childCount) {
                    try {
                        val child = current.getChild(i) ?: continue
                        queue.add(child)
                    } catch (_: Exception) {}
                }
            }
            return false
        }

        fun isFacebookMainFeedActivity(className: String): Boolean = isSocialMainFeedActivity(className)

        enum class BrowserViolation {
            NONE,
            GAMBLING,
            ADULT
        }

        data class FacebookScanResult(
            val violation: BrowserViolation,
            val targetInfo: String,
            val hasContent: Boolean,
            val isSocialGroupLink: Boolean = false
        )

        private val TRUSTED_DOMAINS = listOf(
            // Search engines & portals
            "google.com",
            "google.co.id",
            "gstatic.com",
            "googleusercontent.com",
            "googleapis.com",
            "bing.com",
            "yahoo.com",
            "duckduckgo.com",
            "yandex.com",
            "ecosia.org",
            "startpage.com",
            // Video & Streaming
            "youtube.com",
            "youtu.be",
            "vidio.com",
            "vimeo.com",
            "dailymotion.com",
            "twitch.tv",
            "tiktok.com",
            "netflix.com",
            "disneyplus.com",
            "primevideo.com",
            "hulu.com",
            "spotify.com",
            // Knowledge & Reference
            "wikipedia.org",
            "wikimedia.org",
            "medium.com",
            "kompasiana.com",
            "wordpress.com",
            "blogspot.com",
            // Tech & Tools
            "microsoft.com",
            "apple.com",
            "nvidia.com",
            "github.com",
            "gitlab.com",
            "play.google.com",
            // Indonesian & Global News
            "kompas.com",
            "detik.com",
            "tribunnews.com",
            "tempo.co",
            "liputan6.com",
            "cnnindonesia.com",
            "cnbcindonesia.com",
            "kumparan.com",
            "idntimes.com",
            "sindonews.com",
            "suara.com",
            "merdeka.com",
            "jawapos.com",
            "antaranews.com",
            "republika.co.id",
            "viva.co.id",
            "inews.id",
            "bisnis.com",
            "kontan.co.id",
            "tirto.id",
            "okezone.com",
            "katadata.co.id",
            "pikiran-rakyat.com",
            "medcom.id",
            "tvonenews.com",
            "beritasatu.com",
            "bbc.com",
            "reuters.com",
            "aljazeera.com",
            "nytimes.com",
            "theguardian.com",
            // Social Media
            "facebook.com",
            "fb.com",
            "instagram.com",
            "whatsapp.com",
            "twitter.com",
            "x.com",
            "threads.net",
            "telegram.org",
            "t.me",
            "linkedin.com",
            "pinterest.com",
            "reddit.com",
            // E-Commerce & Payments
            "tokopedia.com",
            "shopee.co.id",
            "shopee.com",
            "lazada.co.id",
            "blibli.com",
            "bukalapak.com",
            "trakteer.id",
            "ko-fi.com",
            "saweria.co"
        )

        fun isTrustedSafeDomain(host: String): Boolean {
            val cleanHost = host.trim().lowercase().removePrefix("www.").removePrefix("m.")
            for (trusted in TRUSTED_DOMAINS) {
                if (cleanHost == trusted || cleanHost.endsWith(".$trusted")) {
                    return true
                }
            }
            return false
        }

        fun isTrustedDestinationDomain(text: String): Boolean {
            val lower = text.lowercase().trim()
            for (domain in TRUSTED_DOMAINS) {
                if (containsDomainWithBoundaries(lower, domain)) {
                    return true
                }
            }
            if (lower.contains(".go.id") || lower.contains(".gov") ||
                lower.contains(".ac.id") || lower.contains(".edu") || lower.contains(".sch.id")) {
                return true
            }
            return false
        }

        fun isSuspiciousDisguisedDomain(token: String): Boolean {
            var clean = token.trim().lowercase()
            if (clean.contains("://")) {
                clean = clean.substringAfter("://")
            }
            clean = clean
                .removePrefix("//")
                .removePrefix("www.")
                .removePrefix("m.")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringBefore(":")
                .trim()

            while (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith(")") || clean.endsWith("\"") || clean.endsWith("'") || clean.endsWith(";") || clean.endsWith(">") || clean.endsWith("]")) {
                clean = clean.dropLast(1)
            }
            while (clean.startsWith("(") || clean.startsWith("\"") || clean.startsWith("'") || clean.startsWith("<") || clean.startsWith("[")) {
                clean = clean.drop(1)
            }

            if (clean.length < 4 || clean.length > 100) return false

            // Must look like a domain or host name (has at least one dot, no spaces)
            val hasDot = clean.contains(".")
            if (!hasDot || clean.contains(" ")) return false

            // If it is an explicitly trusted mainstream domain, allow it immediately
            if (isTrustedSafeDomain(clean) || isTrustedDestinationDomain(clean)) {
                return false
            }

            // Exclude trusted official / governmental / academic TLDs
            if (clean.endsWith(".go.id") || clean.endsWith(".gov") || clean.endsWith(".mil") ||
                clean.endsWith(".edu") || clean.endsWith(".ac.id") || clean.endsWith(".sch.id")) {
                return false
            }

            // Explicit underground leak host names & streaming mirrors
            val hasExplicitLeakName = clean.contains("viadey") ||
                    clean.contains("viodey") ||
                    clean.contains("vioranow") ||
                    clean.contains("slicedrivenow") ||
                    clean.contains("slicedrive") ||
                    clean.contains("slikdrive") ||
                    clean.contains("doodstream") ||
                    clean.contains("streamtape") ||
                    clean.contains("mixdrop") ||
                    clean.contains("lulustream") ||
                    clean.contains("filelions") ||
                    clean.contains("streamsb") ||
                    clean.contains("anhai") ||
                    clean.contains("vidoy") ||
                    clean.contains("vidply") ||
                    clean.contains("vidhide") ||
                    clean.contains("vidcloud") ||
                    clean.contains("vidstream") ||
                    clean.contains("vidlox") ||
                    clean.contains("vidshare") ||
                    clean.contains("vidplay") ||
                    clean.contains("vldey") ||
                    clean.contains("vldeyco") ||
                    clean.contains("1024tera") ||
                    clean.contains("terabox") ||
                    clean.contains("nephobox") ||
                    clean.contains("mirrobox") ||
                    clean.contains("4funbox") ||
                    clean.contains("tibibox")

            if (hasExplicitLeakName) return true

            // Pattern recognition on domain labels (e.g. vid*, *vid, vld*, vdy*, vdk*, viadey*, etc.)
            val labels = clean.split(".")
            val domainLabels = labels.dropLast(1) // everything before the TLD

            for (label in domainLabels) {
                if (label.isEmpty()) continue

                // Check for safe dictionary / proper words containing 'vid' (e.g. david, davidtool, covid, provider, evidence, etc.)
                val isSafeVidWord = label.contains("david") ||
                        label.contains("covid") ||
                        label.contains("provid") ||
                        label.contains("evid") ||
                        label.contains("divid") ||
                        label.contains("divisi") ||
                        label.contains("vivid") ||
                        label.contains("gravid") ||
                        label.startsWith("video") ||
                        label.endsWith("video") ||
                        label == "video"

                // If this label is a genuine legitimate word (like david, davidtool, videomaker), skip vid-pattern detection for it
                if (!isSafeVidWord) {
                    val isLeakVid = label == "vidoy" || label == "vidply" || label == "vidhide" ||
                            label == "vidcloud" || label == "vidstream" || label == "vidlox" ||
                            label == "vidshare" || label == "vidplay" || label.startsWith("streamvid") ||
                            label.startsWith("playvid")
                    if (isLeakVid) {
                        return true
                    }
                }

                // 2. Obfuscated leak site prefixes: vldey, vldplay, vdyplay, vdkplay, etc.
                if (label.startsWith("vldey") || label.startsWith("vldplay") || label == "vldey" ||
                    label.startsWith("vdyplay") || label == "vdy" ||
                    label.startsWith("vdkplay") || label == "vdk" ||
                    label == "vld" || label.contains("vld") || label.contains("vdy") || label.contains("vdk")) {
                    return true
                }

                // 3. 'viadey' / 'viodey' variations
                if (label.contains("viade") || label.contains("viode")) {
                    return true
                }
            }

            return false
        }

        private val ADULT_KEYWORDS = listOf(
            "porn", "porno", "xxx", "bokep", "hentai", "doujin", "nekopoi",
            "javhd", "javsub", "jav censored", "jav uncensored", "dmm.co.jp", "dmm.com",
            "xnxx", "xvideos", "xhamster", "redtube", "youporn", "spankbang", "brazzers", "beeg", "eporner", "tube8",
            "onlyfans", "fansly", "fancentro", "stripchat", "chaturbate", "bongacams", "cam4", "livejasmin",
            "sange", "lendir", "colmek", "crot", "pemersatubangsa", ".xxx", ".porn", ".adult",
            "cd.slikdrive.com", "slikdrive.com", "slikdrive",
            "viadey", "viodey", "vldeyco.id", "vldey", "vioranow", "slicedrivenow", "slicedrive",
            "anhai", "doodstream", "vidoy", "vidply",
            "1024tera.com", "1024tera", "1024terabox.com", "terabox.com", "terabox", "aceimg.com", "uc-share.com",
            "vld", "vdy", "vdk",
            "open bo", "open vcs", "vcs real", "vcs barbar"
        )

        // Keywords that must ONLY be checked against domain names, NEVER against page body/content/query strings
        private val DOMAIN_ONLY_KEYWORDS = setOf("vld", "vdy", "vdk")

        fun extractDomainName(raw: String): String? {
            var clean = raw.trim().lowercase()
            if (clean.isEmpty()) return null
            if (clean.contains("://")) {
                clean = clean.substringAfter("://")
            }
            clean = clean
                .removePrefix("//")
                .removePrefix("www.")
                .removePrefix("m.")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringBefore(":")
                .trim()

            while (clean.isNotEmpty() && (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith(")") || clean.endsWith("\"") || clean.endsWith("'") || clean.endsWith(";") || clean.endsWith(">") || clean.endsWith("]"))) {
                clean = clean.dropLast(1)
            }
            while (clean.isNotEmpty() && (clean.startsWith("(") || clean.startsWith("\"") || clean.startsWith("'") || clean.startsWith("<") || clean.startsWith("["))) {
                clean = clean.drop(1)
            }

            if (clean.length < 3 || clean.length > 100) return null

            // Must contain at least one dot, no spaces, no @
            if (!clean.contains(".") || clean.contains(" ") || clean.contains("@")) return null

            // Ensure valid domain format (labels separated by dots)
            val parts = clean.split(".")
            if (parts.any { it.isEmpty() }) return null
            val tld = parts.last()
            if (tld.length < 2 || !tld.all { it.isLetterOrDigit() }) return null

            return clean
        }

        private val SHORTENER_DOMAINS = listOf(
            "s.id", "bit.ly", "tinyurl.com", "cutt.ly", "rb.gy", "is.gd", "v.gd", "t.co",
            "shorturl.at", "linktr.ee", "linktree.com", "heylink.me", "heylink.id", "sl.al",
            "snip.ly", "rebrand.ly", "bl.ink", "bitly.com", "ow.ly",
            "buff.ly", "soo.gd", "adf.ly", "bc.vc", "sh.st", "hyperurl.co", "rotf.lol",
            "taplink.cc", "lnk.bio", "campsite.bio", "bio.link", "beacons.ai", "mezink.com"
        )

        private val LINK_IN_BIO_DOMAINS = listOf(
            "heylink.me", "heylink.id", "linktr.ee", "linktree.com",
            "taplink.cc", "lnk.bio", "campsite.bio", "bio.link",
            "beacons.ai", "mezink.com"
        )

        fun isLinkInBioDomain(url: String): Boolean {
            val lower = url.lowercase().trim()
            return LINK_IN_BIO_DOMAINS.any { bio ->
                lower.contains(bio)
            }
        }

        fun isShortenerDomain(text: String): Boolean {
            val lower = text.lowercase().trim()
            return SHORTENER_DOMAINS.any { shortener ->
                lower == shortener || lower.endsWith(".$shortener") || lower.contains("/$shortener/") || lower.contains("$shortener/")
            }
        }

        fun isShortenerUrl(url: String): Boolean {
            val lower = url.lowercase().trim()
            return SHORTENER_DOMAINS.any { shortener ->
                lower.contains(shortener)
            }
        }

        fun extractCandidateUrls(text: String): List<String> {
            val urls = mutableListOf<String>()
            if (text.isEmpty()) return urls

            // 1. If text is a Facebook/Instagram redirect wrapper (l.facebook.com/l.php?u=...)
            if (text.contains("facebook.com/l.php") || text.contains("instagram.com/l.php")) {
                try {
                    val uParam = text.substringAfter("u=", "").substringBefore("&")
                    if (uParam.isNotEmpty()) {
                        val decoded = safelyDecodeUrl(uParam)
                        if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                            urls.add(decoded)
                        }
                    }
                } catch (_: Exception) {}
            }

            // Also check for redirect query params in any URL (e.g. ?url=..., ?target=..., ?link=..., ?dest=..., ?redirect=...)
            if (text.contains("?url=") || text.contains("&url=") ||
                text.contains("?link=") || text.contains("&link=") ||
                text.contains("?target=") || text.contains("&target=") ||
                text.contains("?redirect=") || text.contains("&redirect=")) {
                try {
                    val inner = text.substringAfter("url=", "")
                        .ifEmpty { text.substringAfter("link=", "") }
                        .ifEmpty { text.substringAfter("target=", "") }
                        .ifEmpty { text.substringAfter("redirect=", "") }
                        .substringBefore("&")
                    if (inner.isNotEmpty()) {
                        val decoded = safelyDecodeUrl(inner)
                        if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                            urls.add(decoded)
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Extract standard URLs using regex
            val urlRegex = Regex("""(?i)\b(?:https?://|www\.)[a-z0-9\-\._~:/?#\[\]@!$&'()*+,;=%]+""")
            val matches = urlRegex.findAll(text)
            for (m in matches) {
                val u = m.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '>')
                if (u.isNotEmpty() && !urls.contains(u)) {
                    urls.add(u)
                }
            }

            // 3. Match naked shortener domains (e.g. "s.id/xyz", "bit.ly/123", "heylink.me/abc")
            for (shortener in SHORTENER_DOMAINS) {
                val shortRegex = Regex("""(?i)\b$shortener/[a-z0-9\-_./?=&%]+""")
                for (m in shortRegex.findAll(text)) {
                    val raw = m.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '>')
                    val full = "https://$raw"
                    if (!urls.contains(full) && !urls.contains(raw)) {
                        urls.add(full)
                    }
                }
            }

            return urls
        }

        private val redirectCache = ConcurrentHashMap<String, String>()

        fun resolveShortenerRedirectAsync(rawUrl: String, onResolved: ((String) -> Unit)? = null) {
            val cleanUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                "https://$rawUrl"
            } else rawUrl

            val cached = redirectCache[cleanUrl]
            if (cached != null) {
                onResolved?.invoke(cached)
                return
            }

            backgroundExecutor.execute {
                try {
                    var currentUrl = cleanUrl
                    var redirects = 0
                    var violatedLabel: String? = null
                    var violatedInfo: String? = null

                    while (redirects < 4) {
                        val urlObj = java.net.URL(currentUrl)
                        val conn = urlObj.openConnection() as java.net.HttpURLConnection
                        conn.instanceFollowRedirects = false
                        conn.connectTimeout = 2500
                        conn.readTimeout = 2500
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        val code = conn.responseCode
                        if (code in 300..399) {
                            val loc = conn.getHeaderField("Location") ?: break
                            currentUrl = if (loc.startsWith("http")) loc else java.net.URL(urlObj, loc).toString()
                            redirects++
                            conn.disconnect()
                        } else if (code == 200) {
                            // Check link-in-bio pages (e.g. heylink.me, linktr.ee) by inspecting HTML snippet
                            if (isLinkInBioDomain(currentUrl)) {
                                try {
                                    val stream = conn.inputStream
                                    val reader = java.io.BufferedReader(java.io.InputStreamReader(stream))
                                    val sb = StringBuilder()
                                    var line: String? = null
                                    var totalRead = 0
                                    while (reader.readLine().also { line = it } != null && totalRead < 4096) {
                                        sb.append(line).append(" ")
                                        totalRead += (line?.length ?: 0)
                                    }
                                    conn.disconnect()
                                    val htmlSnippet = sb.toString().lowercase()
                                    for (kw in GAMBLING_KEYWORDS) {
                                        if (textContainsKeyword(htmlSnippet, kw)) {
                                            violatedLabel = "Judi Online"
                                            violatedInfo = "$currentUrl [$kw]"
                                            break
                                        }
                                    }
                                    if (violatedLabel == null) {
                                        for (kw in ADULT_KEYWORDS) {
                                            if (DOMAIN_ONLY_KEYWORDS.contains(kw)) continue
                                            if (textContainsKeyword(htmlSnippet, kw)) {
                                                violatedLabel = "Konten Dewasa"
                                                violatedInfo = "$currentUrl [$kw]"
                                                break
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            } else {
                                conn.disconnect()
                            }
                            break
                        } else {
                            conn.disconnect()
                            break
                        }
                    }

                    redirectCache[cleanUrl] = currentUrl
                    if (currentUrl != cleanUrl) {
                        Log.d("FbBlock", "[SHORTENER-RESOLVED] $cleanUrl -> $currentUrl")
                        onResolved?.invoke(currentUrl)
                    }

                    // Check destination URL for gambling/adult keywords
                    val lowerDest = currentUrl.lowercase()
                    if (violatedLabel == null) {
                        for (kw in GAMBLING_KEYWORDS) {
                            if (textContainsKeyword(lowerDest, kw)) {
                                violatedLabel = "Judi Online"
                                violatedInfo = currentUrl
                                break
                            }
                        }
                    }
                    if (violatedLabel == null) {
                        val destDomain = extractDomainName(lowerDest)
                        for (kw in ADULT_KEYWORDS) {
                            if (DOMAIN_ONLY_KEYWORDS.contains(kw)) {
                                if (destDomain != null && !isTrustedSafeDomain(destDomain) && !isTrustedDestinationDomain(destDomain)) {
                                    val labels = destDomain.split(".").dropLast(1)
                                    if (labels.any { it.contains(kw) }) {
                                        violatedLabel = "Konten Dewasa"
                                        violatedInfo = "$currentUrl [$kw]"
                                        break
                                    }
                                }
                            } else {
                                if (textContainsKeyword(lowerDest, kw)) {
                                    violatedLabel = "Konten Dewasa"
                                    violatedInfo = currentUrl
                                    break
                                }
                            }
                        }
                    }

                    if (violatedLabel != null) {
                        val label = violatedLabel
                        val info = violatedInfo ?: currentUrl
                        handler.post {
                            instance?.triggerProhibitedSiteBlock(label, info)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FbBlock", "[SHORTENER-FAIL] Could not resolve $cleanUrl: ${e.message}")
                }
            }
        }

        private val GAMBLING_KEYWORDS = listOf(
            "slot88", "slot777", "slotgacor", "slot gacor", "slot online", "slotonline", "judi slot",
            "situs slot", "situsslot", "agen slot", "agenslot", "daftar slot", "daftarslot",
            "link slot", "linkslot", "bocoran slot", "bocoranslot", "pola slot", "pola-slot",
            "gacor", "maxwin", "scatter hitam", "scatter gacor",
            "kakek zeus", "kakekzeus", "gates of olympus", "gatesofolympus",
            "mahjong ways", "mahjongways", "sweet bonanza", "sweetbonanza",
            "pragmatic play", "pragmaticplay", "pg soft", "pgsoft", "habanero slot", "spadegaming", "joker123",
            "judol", "judi online", "judi bola", "taruhan bola", "taruhan online", "agenjudi", "agen judi",
            "bandar judi", "bandar slot", "bandar togel", "bandar bola", "bandar casino", "bandar online",
            "link gacor", "linkgacor", "rtp slot", "rtpslot", "rtp live", "rtp tertinggi", "bocoran rtp", "infomaxwin",
            "togel", "totomacau", "toto macau", "totogel", "togel online", "togelonline", "singaporepools", "hongkongpools",
            "casino online", "live casino", "baccarat online", "roulette online", "sicbo online",
            "poker88", "idnpoker", "idn poker", "domino99", "dominoqq", "qiuqiu online", "ceme online", "capsa susun",
            "sbobet", "bet365", "1xbet", "parimatch", "m88", "w88", "fun88", "dafabet",
            "sabung ayam", "sabungayam", "sv388", "s128", "cockfight betting",
            "jackpot slot", "freebet", "freespin", "free spin",
            "starlight princess", "starlightprincess", "nexusengine",
            "mpoplay", "mposlot", "pay4d", "idnplay", "idnsport",
            "depo pulsa", "depo qris", "minimal depo", "slot depo", "depo 10k", "depo 20k", "depo 25k", "depo 50k",
            "pulsa tanpa potongan"
        )

        fun textContainsKeyword(text: String, keyword: String): Boolean {
            if (keyword.length <= 4 && !keyword.contains(".") && !keyword.contains("-")) {
                val regex = Regex("\\b" + Regex.escape(keyword) + "\\b")
                return regex.containsMatchIn(text)
            }
            return text.contains(keyword)
        }

        fun safelyDecodeUrl(input: String): String {
            if (!input.contains("%")) return input
            return try {
                var decoded = Uri.decode(input)
                if (decoded.contains("%")) {
                    decoded = Uri.decode(decoded)
                }
                decoded
            } catch (_: Exception) {
                input
            }
        }

        fun containsDomainWithBoundaries(text: String, domain: String): Boolean {
            var idx = 0
            while (idx < text.length) {
                val found = text.indexOf(domain, idx)
                if (found == -1) break

                val charBefore = if (found > 0) text[found - 1] else null
                val charAfter = if (found + domain.length < text.length) text[found + domain.length] else null

                // Before domain: must NOT be an alphanumeric character or dot (e.g. 'u' in about.me, 'r' in start.me)
                val validBefore = charBefore == null || (!charBefore.isLetterOrDigit() && charBefore != '.')
                // After domain: must be a path separator '/', query '?', hash '#', port ':', whitespace, or end of string
                val validAfter = charAfter == null || charAfter == '/' || charAfter == '?' || charAfter == '#' || charAfter == ':' || charAfter.isWhitespace()

                if (validBefore && validAfter) {
                    return true
                }
                idx = found + 1
            }
            return false
        }

        fun hasSocialGroupTarget(text: String): Boolean {
            val lower = text.lowercase()
            if (lower.contains("chat.whatsapp.com") ||
                lower.contains("api.whatsapp.com") ||
                lower.contains("whatsapp.com") ||
                lower.contains("wa.wizard.id") ||
                lower.contains("telegram.me") ||
                lower.contains("telegram.dog")) {
                return true
            }
            val shortDomains = listOf("t.me", "wa.me", "wb.me", "wa.link")
            for (domain in shortDomains) {
                if (containsDomainWithBoundaries(lower, domain)) {
                    return true
                }
            }
            return false
        }

        fun detectFacebookBrowserViolation(
            event: AccessibilityEvent?,
            rootNode: android.view.accessibility.AccessibilityNodeInfo?
        ): FacebookScanResult {
            val gatheredTexts = mutableListOf<String>()

            // 1. Gather text from the accessibility event (if available)
            if (event != null) {
                for (charSeq in event.text) {
                    val str = charSeq?.toString()?.trim()?.lowercase() ?: continue
                    if (str.isNotEmpty() && str.length <= 400 && !gatheredTexts.contains(str)) {
                        gatheredTexts.add(str)
                    }
                }
                event.contentDescription?.toString()?.trim()?.lowercase()?.let {
                    if (it.isNotEmpty() && it.length <= 400 && !gatheredTexts.contains(it)) {
                        gatheredTexts.add(it)
                    }
                }
            }

            fun extractNodeText(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                node.text?.toString()?.trim()?.lowercase()?.let {
                    if (it.isNotEmpty() && it.length <= 400 && !gatheredTexts.contains(it)) {
                        gatheredTexts.add(it)
                    }
                }
                node.contentDescription?.toString()?.trim()?.lowercase()?.let {
                    if (it.isNotEmpty() && it.length <= 400 && !gatheredTexts.contains(it)) {
                        gatheredTexts.add(it)
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.trim()?.lowercase()?.let {
                        if (it.isNotEmpty() && it.length <= 400 && !gatheredTexts.contains(it)) {
                            gatheredTexts.add(it)
                        }
                    }
                }
            }

            // 2. Perform BFS traversal on window tree from root (max 120 nodes)
            // BFS discovers toolbar (address/title) and WebView at levels 1-3 without getting stuck in deep feeds!
            val root = rootNode ?: (if (event != null) getTopmostNode(event.source) ?: event.source else null)
            val webViewNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()

            if (root != null) {
                val queue = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
                queue.add(root)
                var visited = 0
                while (queue.isNotEmpty() && visited < 120) {
                    val current = queue.poll() ?: break
                    visited++
                    extractNodeText(current)

                    val cls = current.className?.toString()?.lowercase() ?: ""
                    if (cls.contains("webview") || cls.contains("webcontent") || cls.contains("webkit")) {
                        webViewNodes.add(current)
                        continue
                    }

                    for (i in 0 until current.childCount) {
                        try {
                            val child = current.getChild(i) ?: continue
                            queue.add(child)
                        } catch (_: Exception) {}
                    }
                }
            }

            // Also include event.source if it is a WebView or inside a WebView
            val evSource = event?.source
            if (evSource != null) {
                val srcCls = evSource.className?.toString()?.lowercase() ?: ""
                if (srcCls.contains("webview") || srcCls.contains("webcontent") || srcCls.contains("webkit")) {
                    if (!webViewNodes.contains(evSource)) {
                        webViewNodes.add(evSource)
                    }
                } else {
                    extractNodeText(evSource)
                }
            }

            // 3. Deep-scan WebView nodes to capture HTML document text, buttons, and links (max depth 15, max 100 nodes per webview)
            for (wv in webViewNodes) {
                var wvCount = 0
                fun scanWebViewChildren(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int) {
                    if (node == null || depth > 15 || wvCount >= 100) return
                    wvCount++
                    extractNodeText(node)
                    for (i in 0 until node.childCount) {
                        try {
                            val child = node.getChild(i) ?: continue
                            scanWebViewChildren(child, depth + 1)
                        } catch (_: Exception) {}
                    }
                }
                scanWebViewChildren(wv, 0)
            }

            // 4. Window title scanning: Android exposes WebView document.title as window title
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val windowTitle = root?.window?.title?.toString()?.trim()?.lowercase()
                    if (!windowTitle.isNullOrEmpty() && !gatheredTexts.contains(windowTitle)) {
                        gatheredTexts.add(windowTitle)
                        Log.d("FbBlock", "[LOG4-TITLE] Window title captured: $windowTitle")
                    }
                } catch (_: Exception) {}
            }

            if (gatheredTexts.isEmpty()) {
                Log.d("FbBlock", "[LOG4-SCAN] gatheredTexts EMPTY → NONE (no accessibility text found)")
                return FacebookScanResult(BrowserViolation.NONE, "", false)
            }

            // 5. Robust decoding of URL wrappers & candidate URL extraction
            val allTexts = mutableListOf<String>()
            allTexts.addAll(gatheredTexts)
            for (text in gatheredTexts) {
                if (text.contains("%")) {
                    val decoded = safelyDecodeUrl(text)
                    if (decoded.isNotEmpty() && !allTexts.contains(decoded)) {
                        allTexts.add(decoded)
                    }
                }
                val candidateUrls = extractCandidateUrls(text)
                for (u in candidateUrls) {
                    if (!allTexts.contains(u)) {
                        allTexts.add(u)
                    }
                    val cached = redirectCache[u] ?: redirectCache["https://$u"]
                    if (cached != null && !allTexts.contains(cached)) {
                        allTexts.add(cached)
                    }
                }
            }

            val combined = allTexts.joinToString(" ")
            Log.d("FbBlock", "[LOG4-SCAN] texts(${allTexts.size}): ${allTexts.take(5).map { it.take(80) }}")

            // 5.5 Check if the active page is a trusted destination (search engines, news, edu, gov, e-commerce, wiki, tech, etc.)
            // Legitimate sites (Google search results, news reporting on events, Wikipedia, etc.)
            // must NEVER be falsely blocked as gambling/adult!
            val isTrustedDestination = allTexts.any { isTrustedDestinationDomain(it) }
            if (isTrustedDestination) {
                Log.d("FbBlock", "[LOG5-TRUSTED] Trusted destination domain detected -> bypassed from adult/gambling blocking")
                return FacebookScanResult(BrowserViolation.NONE, "", true, isSocialGroupLink = false)
            }

            // 6. Check Gambling (Maysir - Haram Mutlak, Highest Priority)
            for (kw in GAMBLING_KEYWORDS) {
                if (textContainsKeyword(combined, kw)) {
                    val matchedSnippet = allTexts.find { textContainsKeyword(it, kw) } ?: kw
                    return FacebookScanResult(BrowserViolation.GAMBLING, matchedSnippet, true)
                }
            }

            // 7. Check Core Adult Keywords
            for (kw in ADULT_KEYWORDS) {
                // Domain-only keywords (vld, vdy, vdk) must ONLY match the domain name, never page text or query params!
                if (DOMAIN_ONLY_KEYWORDS.contains(kw)) continue

                if (textContainsKeyword(combined, kw)) {
                    val matchedSnippet = allTexts.find { textContainsKeyword(it, kw) } ?: kw
                    return FacebookScanResult(BrowserViolation.ADULT, matchedSnippet, true)
                }
            }

            // 7.5 Check Domain-Only Keywords strictly against extracted domain names
            val candidateDomains = mutableSetOf<String>()
            for (text in allTexts) {
                val d = extractDomainName(text)
                if (d != null) candidateDomains.add(d)
                for (u in extractCandidateUrls(text)) {
                    val du = extractDomainName(u)
                    if (du != null) candidateDomains.add(du)
                }
                val tokens = text.split(" ", "\t", "\n", ",", "|", ";", "\"", "'", "(", ")", "[", "]", "<", ">")
                for (token in tokens) {
                    val dt = extractDomainName(token)
                    if (dt != null) candidateDomains.add(dt)
                }
            }

            for (domain in candidateDomains) {
                if (isTrustedSafeDomain(domain) || isTrustedDestinationDomain(domain)) continue

                for (kw in DOMAIN_ONLY_KEYWORDS) {
                    if (domain.contains(kw)) {
                        Log.d("FbBlock", "[ADULT-DOMAIN] Prohibited domain matched keyword '$kw': $domain")
                        return FacebookScanResult(BrowserViolation.ADULT, domain, true)
                    }
                }
            }

            // 8. Pattern Matching for Disguised Adult Leak / Streaming Domains (vid, vld, vdy, vdk, viadey, viodey, etc.)
            for (domain in candidateDomains) {
                if (isSuspiciousDisguisedDomain(domain)) {
                    Log.d("FbBlock", "[ADULT-DISGUISED] Suspicious disguised domain detected: $domain")
                    return FacebookScanResult(BrowserViolation.ADULT, domain, true)
                }
            }
            for (text in allTexts) {
                val tokens = text.split(" ", "\t", "\n", ",", "|", ";", "\"", "'", "(", ")", "[", "]", "<", ">")
                for (token in tokens) {
                    val d = extractDomainName(token) ?: continue
                    if (isSuspiciousDisguisedDomain(d)) {
                        return FacebookScanResult(BrowserViolation.ADULT, d, true)
                    }
                }
            }

            // 9. Check for Social Group Links with strict word boundaries
            val hasSocialGroupPattern = allTexts.any { hasSocialGroupTarget(it) }

            // 10. Meaningful content check (exclude placeholders, intermediate shortener domains, and loading states)
            val hasMeaningfulContent = allTexts.any { text ->
                val trimmed = text.trim()
                val isPlaceholder = trimmed == "about:blank" ||
                        trimmed.startsWith("about:") ||
                        trimmed.startsWith("chrome://") ||
                        trimmed.startsWith("data:") ||
                        trimmed == "loading..." ||
                        trimmed == "memuat..." ||
                        isShortenerDomain(trimmed)
                !isPlaceholder && trimmed.length >= 4 && (trimmed.contains(".") || trimmed.contains("http") || trimmed.contains("://"))
            }

            // 11. Asynchronously resolve any shortener URLs found in texts
            for (text in allTexts) {
                val urls = extractCandidateUrls(text)
                for (u in urls) {
                    if (isShortenerUrl(u)) {
                        resolveShortenerRedirectAsync(u)
                    }
                }
                if (isShortenerUrl(text)) {
                    resolveShortenerRedirectAsync(text)
                }
            }

            return FacebookScanResult(BrowserViolation.NONE, "", hasMeaningfulContent, isSocialGroupLink = hasSocialGroupPattern)
        }
    }

    private fun loadTemporaryAllowed() {
        val prefs = getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("allowed_temp_packages", null)
        if (saved != null) {
            val now = System.currentTimeMillis()
            val stillvalid = mutableSetOf<String>()
            
            for (entry in saved) {
                val parts = entry.split("|")
                if (parts.size == 2) {
                    val pkg = parts[0].trim().lowercase()
                    val expiry = parts[1].toLongOrNull() ?: 0L
                    if (now < expiry) {
                        temporaryAllowedPackages[pkg] = expiry
                        stillvalid.add("$pkg|$expiry")
                        
                        val remaining = expiry - now
                        if (remaining <= 180_000L) notified3Min.add(pkg)
                        if (remaining <= 120_000L) notified2Min.add(pkg)
                        if (remaining <= FINAL_VIBRATION_MS) notified1Min.add(pkg)

                        val delayForFinalVibe = remaining - FINAL_VIBRATION_MS
                        if (delayForFinalVibe > 0) {
                            handler.postDelayed({
                                triggerFinalKickVibration(pkg)
                            }, delayForFinalVibe)
                        }

                        handler.postDelayed({
                            checkAndKickExpiredApp(pkg)
                        }, remaining)
                    }
                }
            }
            
            // Cleanup expired ones from Prefs
            if (stillvalid.size != saved.size) {
                prefs.edit().putStringSet("allowed_temp_packages", stillvalid).commit()
            }
            if (temporaryAllowedPackages.isNotEmpty()) {
                startWatchdog()
            }
        }
    }

    private fun loadBlockedPackages() {
        val prefs = getSharedPreferences("app_block_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("blocked_packages", null)
        if (saved != null) {
            blockedPackages.clear()
            for (pkg in saved) {
                blockedPackages.add(pkg.trim().lowercase())
            }
        }
        val savedGhadhul = prefs.getStringSet("ghadhul_bashar_packages", null)
        if (savedGhadhul != null) {
            ghadhulBasharPackages.clear()
            for (pkg in savedGhadhul) {
                ghadhulBasharPackages.add(pkg.trim().lowercase())
            }
        }
        val savedProhibited = prefs.getStringSet("prohibited_packages", null)
        if (savedProhibited != null) {
            prohibitedPackages.clear()
            for (pkg in savedProhibited) {
                prohibitedPackages.add(pkg.trim().lowercase())
            }
        }
        hasCompletedOnboarding = isOnboardingCompleted(this)
        loadTemporaryAllowed()
    }

    private val allowReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                "com.islamiclauncher.ALLOW_PACKAGE" -> {
                    val pkg = intent.getStringExtra("packageName")?.trim()?.lowercase()
                    val duration = intent.getLongExtra("durationMillis", 3600000L)
                    if (pkg != null) {
                        val now = System.currentTimeMillis()
                        val expiry = now + duration
                        temporaryAllowedPackages[pkg] = expiry
                        lastBypassPackage = pkg
                        lastBypassTime = now
                        notified3Min.remove(pkg)
                        notified2Min.remove(pkg)
                        notified1Min.remove(pkg)
                        if (duration <= 180_000L) notified3Min.add(pkg)
                        if (duration <= 120_000L) notified2Min.add(pkg)
                        if (duration <= FINAL_VIBRATION_MS) notified1Min.add(pkg)

                        val delayForFinalVibe = duration - FINAL_VIBRATION_MS
                        if (delayForFinalVibe > 0) {
                            handler.postDelayed({
                                triggerFinalKickVibration(pkg)
                            }, delayForFinalVibe)
                        }

                        startWatchdog()
                        Log.d("AppBlockService", "BROADCAST RECEIVED: Allowed $pkg (Shield ON)")
                    }
                }
                "com.islamiclauncher.ALLOW_GHADHUL_BASHAR" -> {
                    val pkg = intent.getStringExtra("packageName")?.trim()?.lowercase()
                    if (pkg != null) {
                        allowGhadhulBasharSession(pkg)
                    }
                }
                "com.islamiclauncher.RESET_GHADHUL_BASHAR" -> {
                    val pkg = intent.getStringExtra("packageName")?.trim()?.lowercase()
                    if (pkg != null) {
                        resetGhadhulBasharSession(pkg)
                    }
                }
                "com.islamiclauncher.BYPASS_SUPPORT_DEV" -> {
                    prepareSupportDeveloperBypass()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val type = event.eventType
            val isWindowState = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            val isWindowContent = type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            val isViewClicked = type == AccessibilityEvent.TYPE_VIEW_CLICKED

            if (!isWindowState && !isWindowContent && !isViewClicked) return

            val packageName = event.packageName?.toString()?.trim()?.lowercase() ?: return
            val className = event.className?.toString()?.lowercase() ?: ""
            val isWebViewCls = className.contains("webview") || className.contains("webcontent") || className.contains("webkit")

            val now = System.currentTimeMillis()
            // If prohibited screen or site block was triggered recently (< 3500ms),
            // DROP all events immediately to prevent animation events from creating an infinite loop / ANR!
            if ((now - lastProhibitedTriggerTime) < 3500L) {
                return
            }

            // Proactively handle clicks (e.g. pre-resolving URL shorteners in Facebook)
            if (isViewClicked) {
                if (isFacebookPackage(packageName)) {
                    val clickedTexts = mutableListOf<String>()
                    for (cs in event.text) {
                        cs?.toString()?.trim()?.let { clickedTexts.add(it) }
                    }
                    event.contentDescription?.toString()?.trim()?.let { clickedTexts.add(it) }
                    for (txt in clickedTexts) {
                        val urls = extractCandidateUrls(txt)
                        for (u in urls) {
                            if (isShortenerUrl(u)) {
                                resolveShortenerRedirectAsync(u) { resolved ->
                                    Log.d("FbBlock", "[CLICK-RESOLVED] Pre-resolved shortener: $u -> $resolved")
                                }
                            }
                        }
                        if (isShortenerUrl(txt)) {
                            resolveShortenerRedirectAsync(txt) { resolved ->
                                Log.d("FbBlock", "[CLICK-RESOLVED] Pre-resolved shortener: $txt -> $resolved")
                            }
                        }
                    }
                }
                return
            }

            // FAST EARLY-EXIT: If it's a content change (scrolling/typing/layout updates),
            // ONLY process if it's Facebook in-app browser or Custom Tab!
            // For 99% of normal app actions (typing WhatsApp, scrolling TikTok/IG), bail out in < 1 microsecond!
            if (isWindowContent) {
                val isFb = isFacebookPackage(packageName)
                val isCustomTab = className.contains("customtab")
                if (!isFb && !isCustomTab) {
                    return
                }
                if (isFb) {
                    if (isWebViewCls && ghadhulBasharPackages.contains(packageName)) {
                        if (isFbBrowserActive[packageName] != true) {
                            Log.d("FbBlock", "[FIX2-AUTO] Auto-set isFbBrowserActive=true from WebView content change: pkg=$packageName")
                        }
                        isFbBrowserActive[packageName] = true
                    }
                    if (isFbBrowserActive[packageName] != true && !isWebViewCls && !isFacebookInAppBrowser(packageName, className)) {
                        val active = rootInActiveWindow ?: getTopmostNode(event.source)
                        if (hasWebViewInWindow(active)) {
                            Log.d("FbBlock", "[FIX3-AUTO] Auto-set isFbBrowserActive=true from hasWebViewInWindow: pkg=$packageName")
                            isFbBrowserActive[packageName] = true
                        } else {
                            Log.d("FbBlock", "[LOG1-DROP] CONTENT_CHANGED dropped: pkg=$packageName cls=$className isFbBrowserActive=${isFbBrowserActive[packageName]} isFbBrowserDetected=${isFacebookInAppBrowser(packageName, className)}")
                            return
                        }
                    }
                    Log.d("FbBlock", "[LOG2-PASS] CONTENT_CHANGED passed: pkg=$packageName cls=$className isFbBrowserActive=${isFbBrowserActive[packageName]}")
                }
            }

            // Track Facebook in-app browser activity state (only on window state changes)
            if (isFacebookPackage(packageName) && isWindowState) {
                val isBrowserCls = isWebViewCls || isFacebookInAppBrowser(packageName, className)
                val isFeedCls = isFacebookMainFeedActivity(className)
                Log.d("FbBlock", "[LOG3-STATE] WINDOW_STATE: pkg=$packageName cls=$className isBrowserCls=$isBrowserCls isFeedCls=$isFeedCls prevIsFbBrowserActive=${isFbBrowserActive[packageName]}")
                if (isBrowserCls) {
                    isFbBrowserActive[packageName] = true
                } else if (isFeedCls) {
                    val active = rootInActiveWindow ?: getTopmostNode(event.source)
                    if (!hasWebViewInWindow(active)) {
                        isFbBrowserActive[packageName] = false
                        facebookBrowserSessionActive[packageName] = false
                    }
                }
            }

            // Skip transient overlays, keyboards (IMEs), and system dialogs so they don't corrupt foreground state
            if (isKeyboardOrTransientOverlay(packageName)) {
                return
            }

            // Skip our own app
            if (packageName == this.packageName) {
                currentForegroundPackage = packageName
                return
            }

            val previousPackage = currentForegroundPackage
            currentForegroundPackage = packageName

            // If user left Facebook for another app, clean up the Facebook in-app browser session
            if (previousPackage != null && isFacebookPackage(previousPackage) && previousPackage != packageName) {
                isFbBrowserActive.remove(previousPackage)
                facebookBrowserSessionActive.remove(previousPackage)
            }

            if (packageName == lastTriggeredPackage && (now - lastTriggeredTime) < 1200L) {
                return
            }

            // Scan for any expired temporary unlocks and countdown alerts on every accessibility event
            // This is a backup for postDelayed timers that may be deferred by Doze mode
            if (temporaryAllowedPackages.isNotEmpty()) {
                checkCountdownVibrations()
                checkAllExpiredUnlocks()
            }

            // Lazy resolution of activeNode: only query window tree via IPC if and when an inspector actually needs it!
            var cachedActiveNode: android.view.accessibility.AccessibilityNodeInfo? = null
            var hasFetchedActiveNode = false
            fun getActiveNode(): android.view.accessibility.AccessibilityNodeInfo? {
                if (!hasFetchedActiveNode) {
                    hasFetchedActiveNode = true
                    cachedActiveNode = rootInActiveWindow ?: getTopmostNode(event.source) ?: event.source
                }
                return cachedActiveNode
            }

            // 0. PRIORITAS 0: PROHIBITED BROWSER CHECK (Dilarang Total - Tanpa Poin/Waktu)
            if (prohibitedPackages.contains(packageName)) {
                Log.d("AppBlockService", "PROHIBITED BROWSER DETECTED: $packageName")
                lastTriggeredPackage = packageName
                lastTriggeredTime = now
                MainActivity.notifyAppProhibited(packageName)
                bringLauncherToFront("prohibitedPackageName", packageName, "triggerProhibitedScreen")
                return // DILARANG TOTAL SAMA SEKALI!
            }

            // 0.1 PRIORITAS 0.1: PROHIBITED FACEBOOK IN-APP BROWSER & CUSTOM TABS URL CHECK (Haram Mutlak - Tanpa Bypass)
            val isFb = isFacebookPackage(packageName)
            var isFbBrowser = isFb && (isWebViewCls || isFacebookInAppBrowser(packageName, className) || (isFbBrowserActive[packageName] == true))
            val isCustomTab = className.contains("customtab")
            if (isFb && !isFbBrowser) {
                val active = getActiveNode()
                if (hasWebViewInWindow(active)) {
                    isFbBrowserActive[packageName] = true
                    isFbBrowser = true
                }
            }
            Log.d("FbBlock", "[LOG5-P01] pkg=$packageName isFb=$isFb isFbBrowser=$isFbBrowser isCustomTab=$isCustomTab isFbBrowserActive=${isFbBrowserActive[packageName]} cls=$className eventType=${if (isWindowState) "STATE" else "CONTENT"}")
            if ((now - lastProhibitedTriggerTime) < 3500L) {
                return
            }
            if (isFbBrowser || isCustomTab) {
                val (violation, targetInfo) = detectFacebookBrowserViolation(event, getActiveNode())
                Log.d("FbBlock", "[LOG6-P01-RESULT] violation=$violation targetInfo=${targetInfo.take(80)}")
                if (violation == BrowserViolation.GAMBLING || violation == BrowserViolation.ADULT) {
                    val targetLabel = if (violation == BrowserViolation.GAMBLING) "Judi Online" else "Konten Dewasa"
                    Log.d("AppBlockService", "BROWSER VIOLATION ($targetLabel): $targetInfo in $packageName")
                    forceCloseAndBlockProhibitedApp(packageName, targetLabel)
                    return // DILARANG TOTAL SAMA SEKALI!
                }
            }

            // Khusus: Bypass Ghadhul Bashar jika dibuka via tombol "Dukung Developer" (Trakteer / Ko-fi)
            val isSupportDevBypass = (now < bypassSupportDeveloperUntil) &&
                    (isKnownBrowser(packageName) || ghadhulBasharPackages.contains(packageName))
            if (isSupportDevBypass) {
                Log.d("AppBlockService", "GHADHUL BASHAR: Bypassed specifically for Dukung Developer ($packageName)")
                bypassSupportDeveloperUntil = 0L // Segera reset agar hanya berlaku 1 kali (one-time)
                lastBypassPackage = packageName
                lastBypassTime = now
                return
            }

            // 1. TRANSITION SHIELD (Highest Priority)
            // Immunity period for recently unlocked or allowed apps (15 seconds)
            // Memastikan transisi awal ke aplikasi yang baru dibuka kunci / diizinkan tidak memantul
            val isWithinBypassShield = (packageName == lastBypassPackage && (now - lastBypassTime) < 15000L)
            if (isWithinBypassShield) {
                if (ghadhulBasharPackages.contains(packageName)) {
                    lastActiveGhadhulTimes[packageName] = now
                }
                return 
            }

            // 2. PRIORITAS 1: NON-PRODUCTIVE BLOCK CHECK (Kunci Utama)
            if (blockedPackages.contains(packageName)) {
                val expiry = temporaryAllowedPackages[packageName]
                val isAllowed = expiry != null && now < expiry
                if (!isAllowed) {
                    Log.d("AppBlockService", "BLOCK (Non-Productive): Detect $packageName")
                    lastTriggeredPackage = packageName
                    lastTriggeredTime = now
                    MainActivity.notifyAppBlocked(packageName)
                    bringLauncherToFront("blockedPackageName", packageName, "triggerBlockScreen")
                    return // KUNCI NON-PRODUKTIF ADALAH YANG UTAMA!
                }
            }

            // 3. PRIORITAS 2: GHADHUL BASHAR CHECK (Pengingat Murni)
            // Hanya dieksekusi jika aplikasi bukan aplikasi non-produktif terkunci (atau sudah dibuka kuncinya)
            if (ghadhulBasharPackages.contains(packageName)) {
                // If a prohibited app or site was recently triggered, DO NOT trigger Ghadhul Bashar!
                if ((now - lastProhibitedTriggerTime) < 4000L) {
                    return
                }

                val lastActive = lastActiveGhadhulTimes[packageName]
                val isIdleExpired = lastActive != null && (now - lastActive) > (30 * 60 * 1000)
                
                if (isIdleExpired) {
                    activeGhadhulSessions.remove(packageName)
                    facebookBrowserSessionActive.remove(packageName)
                    isFbBrowserActive.remove(packageName)
                }

                val hasActiveSession = activeGhadhulSessions.contains(packageName)
                val isBrowser = isKnownBrowser(packageName)
                var isFbBrowserCurrent = isWebViewCls || isFacebookInAppBrowser(packageName, className) || (isFbBrowserActive[packageName] == true)
                if (isFacebookPackage(packageName) && !isFbBrowserCurrent) {
                    val active = getActiveNode()
                    if (hasWebViewInWindow(active)) {
                        isFbBrowserActive[packageName] = true
                        isFbBrowserCurrent = true
                    }
                }

                Log.d("FbBlock", "[LOG7-P3] pkg=$packageName hasActiveSession=$hasActiveSession isBrowser=$isBrowser isFbBrowserCurrent=$isFbBrowserCurrent fbSessionActive=${facebookBrowserSessionActive[packageName]} isFbBrowserActive=${isFbBrowserActive[packageName]} cls=$className")

                // Track status sesi Facebook in-app browser
                if (isFacebookPackage(packageName) && isFacebookMainFeedActivity(className)) {
                    val active = rootInActiveWindow ?: getTopmostNode(event.source)
                    if (!hasWebViewInWindow(active)) {
                        facebookBrowserSessionActive[packageName] = false
                        isFbBrowserActive[packageName] = false
                    }
                }

                if (hasActiveSession) {
                    if (isFbBrowserCurrent) {
                        // Khusus Facebook & Facebook Lite:
                        // 1. CEK PERTAMA (Langsung saat domain/link muncul):
                        // Sesi aktif TIDAK PERNAH mem-bypass deteksi konten terlarang!
                        val scanRes = detectFacebookBrowserViolation(event, getActiveNode())
                        if (scanRes.violation == BrowserViolation.GAMBLING || scanRes.violation == BrowserViolation.ADULT) {
                            val targetLabel = if (scanRes.violation == BrowserViolation.GAMBLING) "Judi Online" else "Konten Dewasa"
                            Log.d("AppBlockService", "FACEBOOK BROWSER VIOLATION ($targetLabel): ${scanRes.targetInfo} in $packageName")
                            forceCloseAndBlockProhibitedApp(packageName, targetLabel)
                            return
                        }

                        // 2. Hanya jika konten terbukti BERSIH, periksa apakah pengguna sudah berada di sesi browser
                        val isAlreadyInFbBrowser = facebookBrowserSessionActive[packageName] == true
                        if (isAlreadyInFbBrowser || isWithinBypassShield) {
                            facebookBrowserSessionActive[packageName] = true
                            lastActiveGhadhulTimes[packageName] = now
                            return
                        }

                        // 3. Link baru dibuka di Facebook -> VERIFIKASI 2 TAHAP:
                        // Tahap 1: Lolos cek awal (bukan situs terlarang).
                        // Tunggu 1000ms (1 detik) agar redirect shortener dan halaman selesai dimuat sebelum memutuskan.
                        // Tahap 2: Scan ulang setelah 1000ms:
                        //   - Jika berubah jadi situs terlarang (hasil redirect) -> Tampilkan OVERLAY MERAH!
                        //   - Jika tetap bersih dan konten tujuan sudah termuat -> Tampilkan GHADHUL BASHAR!
                        //   - Jika masih loading / shortener belum selesai -> Tunggu event berikutnya, jangan terburu-buru panggil Ghadhul Bashar!
                        if (pendingGhadhulPackage == packageName) {
                            // Timer tahap 2 sedang berjalan, biarkan menyelesaikan pemindaian
                            return
                        }

                        val capturedPkg = packageName
                        pendingGhadhulPackage = capturedPkg
                        val runnable = Runnable {
                            pendingGhadhulRunnable = null
                            pendingGhadhulPackage = null
                            try {
                                val curFg = currentForegroundPackage
                                if (curFg == null || !isFacebookPackage(curFg)) {
                                    Log.d("FbBlock", "[2-STEP] User exited Facebook before verification completed ($curFg)")
                                    return@Runnable
                                }
                                val active = rootInActiveWindow ?: getTopmostNode(null)
                                if (active == null) {
                                    Log.d("FbBlock", "[2-STEP] active node null after 1000ms for $capturedPkg")
                                    return@Runnable
                                }

                                val secondScan = detectFacebookBrowserViolation(null, active)
                                Log.d("FbBlock", "[2-STEP-RESULT] secondScan violation=${secondScan.violation} hasContent=${secondScan.hasContent} targetInfo=${secondScan.targetInfo.take(80)}")

                                if (secondScan.violation == BrowserViolation.GAMBLING || secondScan.violation == BrowserViolation.ADULT) {
                                    val targetLabel = if (secondScan.violation == BrowserViolation.GAMBLING) "Judi Online" else "Konten Dewasa"
                                    Log.d("AppBlockService", "2-STEP CHECK DETECTED VIOLATION ($targetLabel): ${secondScan.targetInfo} in $capturedPkg")
                                    forceCloseAndBlockProhibitedApp(capturedPkg, targetLabel)
                                } else if (secondScan.hasContent) {
                                    // Tahap 2: Link terbukti tetap BERSIH dan konten tujuan sudah termuat -> Tampilkan Ghadhul Bashar!
                                    Log.d("AppBlockService", "2-STEP CHECK CONFIRMED CLEAN LINK -> Showing Ghadhul Bashar for $capturedPkg")
                                    facebookBrowserSessionActive[capturedPkg] = true
                                    lastTriggeredPackage = capturedPkg
                                    lastTriggeredTime = System.currentTimeMillis()
                                    val extraTag = if (secondScan.isSocialGroupLink) "social_group_link" else ""
                                    MainActivity.notifyGhadhulBashar(capturedPkg, extraTag)
                                    instance?.bringLauncherToFront("ghadhulBasharPackageName", capturedPkg, "triggerGhadhulBasharScreen", extraTag)
                                } else {
                                    // Masih berupa domain shortener atau masih proses loading di WebView -> biarkan event berikutnya yang memverifikasi
                                    Log.d("AppBlockService", "2-STEP CHECK: Page still loading or on shortener domain at 1s mark for $capturedPkg, waiting for content change.")
                                }
                            } catch (e: Exception) {
                                Log.e("FbBlock", "Error in 2-step verification runnable: ${e.message}")
                            }
                        }
                        pendingGhadhulRunnable = runnable
                        handler.postDelayed(runnable, 1000L)
                        return
                    } else if (!isBrowser) {
                        // Aplikasi non-browser (seperti TikTok, Instagram, Twitter/X, atau feed Facebook):
                        lastActiveGhadhulTimes[packageName] = now
                        return
                    } else {
                        // Aplikasi Browser (Chrome, Firefox, dll):
                        val isLauncherOrSystem = previousPackage == null ||
                            previousPackage == this.packageName ||
                            previousPackage == "com.android.systemui" ||
                            previousPackage == packageName ||
                            previousPackage == "android" ||
                            previousPackage.endsWith(".launcher") ||
                            previousPackage.contains("launcher") ||
                            previousPackage.contains("home") ||
                            isKeyboardOrTransientOverlay(previousPackage)

                        val isCustomTabFromExternal = className.contains("customtab") && previousPackage != packageName
                        val isLinkFromExternalApp = (!isLauncherOrSystem) || isCustomTabFromExternal

                        val scanRes = detectFacebookBrowserViolation(event, getActiveNode())
                        if (scanRes.violation == BrowserViolation.GAMBLING || scanRes.violation == BrowserViolation.ADULT) {
                            val targetLabel = if (scanRes.violation == BrowserViolation.GAMBLING) "Judi Online" else "Konten Dewasa"
                            Log.d("AppBlockService", "EXTERNAL BROWSER / CUSTOM TAB VIOLATION ($targetLabel): ${scanRes.targetInfo} in $packageName")
                            forceCloseAndBlockProhibitedApp(packageName, targetLabel)
                            return
                        }

                        if (!isLinkFromExternalApp || isWithinBypassShield) {
                            lastActiveGhadhulTimes[packageName] = now
                            return
                        }
                    }
                }

                // Tampilkan overlay jika:
                // 1. Peluncuran baru / idle > 30 menit (!hasActiveSession), ATAU
                // 2. Browser dibuka dari klik link aplikasi eksternal / in-app browser Facebook
                Log.d("AppBlockService", "GHADHUL BASHAR: Triggered for $packageName (isBrowser=$isBrowser, activeSession=$hasActiveSession)")
                lastTriggeredPackage = packageName
                lastTriggeredTime = now
                val isSocialTarget = (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b" ||
                        packageName == "org.telegram.messenger" || packageName == "org.telegram.plus" ||
                        packageName == "org.thunderdog.challegram")
                val isSocialFromFb = isSocialTarget && (previousPackage != null && isFacebookPackage(previousPackage))
                var extraTag = if (isSocialFromFb) "social_group_link" else ""
                if (extraTag.isEmpty() && isBrowser) {
                    val browserScan = detectFacebookBrowserViolation(event, getActiveNode())
                    if (browserScan.isSocialGroupLink) {
                        extraTag = "social_group_link"
                    }
                }
                MainActivity.notifyGhadhulBashar(packageName, extraTag)
                bringLauncherToFront("ghadhulBasharPackageName", packageName, "triggerGhadhulBasharScreen", extraTag)
                return
            }
        } catch (e: Exception) {
            Log.e("AppBlockService", "Crash in onAccessibilityEvent: ${e.message}")
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = info.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            serviceInfo = info
        } catch (e: Exception) {
            Log.w("AppBlockService", "Failed to update serviceInfo: ${e.message}")
        }

        loadBlockedPackages()
        
        // Register receiver for instant unlock & session signals
        val filter = IntentFilter().apply {
            addAction("com.islamiclauncher.ALLOW_PACKAGE")
            addAction("com.islamiclauncher.ALLOW_GHADHUL_BASHAR")
            addAction("com.islamiclauncher.RESET_GHADHUL_BASHAR")
            addAction("com.islamiclauncher.BYPASS_SUPPORT_DEV")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(allowReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(allowReceiver, filter)
        }
        
        Log.d("AppBlockService", "Accessibility Service Connected with Receiver")
    }

    override fun onDestroy() {
        try {
            if (instance == this) instance = null
            handler.removeCallbacksAndMessages(null)
            unregisterReceiver(allowReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    fun forceCloseAndBlockProhibitedApp(targetPackage: String, targetLabel: String) {
        val now = System.currentTimeMillis()
        val cleanPkg = targetPackage.trim().lowercase()

        cancelPendingGhadhulVerification()
        isFbBrowserActive[cleanPkg] = false
        facebookBrowserSessionActive[cleanPkg] = false
        lastTriggeredPackage = cleanPkg
        lastTriggeredTime = now
        lastProhibitedTriggerTime = now

        // Langsung bawa ke HOME (Islamic Launcher) dan tampilkan layar blokir merah
        MainActivity.notifyAppProhibited(targetLabel)
        bringLauncherToFront("prohibitedPackageName", targetLabel, "triggerProhibitedScreen")
    }

    fun triggerProhibitedSiteBlock(targetLabel: String, targetInfo: String) {
        val now = System.currentTimeMillis()
        if ((now - lastProhibitedTriggerTime) < 3000L) {
            return
        }
        val fg = currentForegroundPackage ?: "com.facebook.katana"
        Log.d("AppBlockService", "TRIGGER PROHIBITED SITE BLOCK ($targetLabel): $targetInfo in $fg")
        forceCloseAndBlockProhibitedApp(fg, targetLabel)
    }

    /**
     * Brings the launcher to foreground using a multi-layered approach for maximum reliability:
     * 1. performGlobalAction(HOME) — Works on ALL Android versions (API 16+), bypasses
     *    Android 10+ background activity launch restrictions. Simulates the HOME button press.
     * 2. Explicit Intent to MainActivity with custom action to prevent HOME categorization.
     * 3. PendingIntent with MODE_BACKGROUND_ACTIVITY_START_ALLOWED (bypasses Android 14+ BAL restrictions).
     * 4. Direct startActivity fallback if PendingIntent failed.
     */
    private fun bringLauncherToFront(packageNameKey: String, packageNameValue: String, triggerKey: String, sourceExtra: String? = null) {
        val now = System.currentTimeMillis()
        if (triggerKey == "triggerProhibitedScreen" && (now - lastProhibitedBringToFrontTime) < 3000L) {
            return
        }
        if (triggerKey == "triggerProhibitedScreen") {
            lastProhibitedBringToFrontTime = now
        }

        val cleanPkg = packageNameValue.trim().lowercase()

        // LAYER 1: performGlobalAction(HOME) — Immediately minimizes the foreground app smoothly
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.w("AppBlockService", "performGlobalAction(HOME) failed: ${e.message}")
        }

        // LAYER 2: Explicit Intent to MainActivity with custom action to prevent HOME categorization
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.islamiclauncher.SHOW_BLOCK_OVERLAY"
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(packageNameKey, cleanPkg)
            putExtra(triggerKey, true)
            if (sourceExtra != null) {
                putExtra("ghadhulBasharExtraInfo", sourceExtra)
            }
        }

        // LAYER 3: PendingIntent with MODE_BACKGROUND_ACTIVITY_START_ALLOWED (bypasses Android 14+ BAL restrictions)
        var startedViaPendingIntent = false
        try {
            val pendingIntent = PendingIntent.getActivity(
                this,
                (System.currentTimeMillis() % 10000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                pendingIntent.send(this, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }
            startedViaPendingIntent = true
        } catch (e: Exception) {
            Log.w("AppBlockService", "PendingIntent.send failed: ${e.message}")
        }

        // LAYER 4: Direct startActivity fallback if PendingIntent failed
        if (!startedViaPendingIntent) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.w("AppBlockService", "startActivity failed: ${e.message}")
            }
        }

        // LAYER 5: Retry with 400ms delay for non-prohibited screens
        if (triggerKey != "triggerProhibitedScreen") {
            handler.postDelayed({
                try {
                    startActivity(intent)
                } catch (retryEx: Exception) {
                    Log.e("AppBlockService", "Retry startActivity failed: ${retryEx.message}")
                }
            }, 400L)
        }
    }
}
