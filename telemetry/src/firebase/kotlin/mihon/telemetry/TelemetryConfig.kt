package mihon.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object TelemetryConfig {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context, isPreviewBuildType: Boolean, commitCount: String) {
        // KMK -->
        // Only project release and preview packages send this project's telemetry.
        if (!context.isKomiyomiProductionApp()) return
        // KMK <--

        analytics = FirebaseAnalytics.getInstance(context)
        FirebaseApp.initializeApp(context)
        crashlytics = FirebaseCrashlytics.getInstance()
        // KMK -->
        if (isPreviewBuildType) {
            analytics?.setUserProperty("preview_version", commitCount)
        }
        // KMK <--
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    // KMK -->
    private fun Context.isKomiyomiProductionApp(): Boolean = packageName in KOMIYOMI_PACKAGES
    // KMK <--
}

// KMK -->
private val KOMIYOMI_PACKAGES = hashSetOf(
    "com.wongpinter.komiyomi.ext",
    "com.wongpinter.komiyomi.ext.beta",
)
// KMK <--
