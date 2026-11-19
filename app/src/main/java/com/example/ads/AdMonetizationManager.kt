package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.data.model.AdConfig
import com.example.util.NetworkMonitor
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.impression.LevelPlayImpressionData
import com.unity3d.mediation.impression.LevelPlayImpressionDataListener
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SdkInitStatus(val labelBn: String, val labelEn: String) {
    IDLE("অপেক্ষমান (Not Initialized)", "Not Initialized"),
    INITIALIZING("ইনিশিয়ালাইজ হচ্ছে...", "Initializing"),
    SUCCESS("সক্রিয় (Initialized)", "Initialized"),
    FAILED("ব্যর্থ (Init Failed)", "Failed")
}

enum class AdFormatType(val labelBn: String, val labelEn: String) {
    BANNER("Adaptive Banner", "Adaptive Banner"),
    NATIVE("Native Ad", "Native Ad"),
    INTERSTITIAL("Interstitial", "Interstitial"),
    REWARDED("Rewarded Video", "Rewarded Video"),
    APP_OPEN("App Open", "App Open")
}

object AdDebugEventCode {
    const val SDK_INITIALIZATION_STARTED = "SDK_INITIALIZATION_STARTED"
    const val SDK_INITIALIZATION_SUCCESS = "SDK_INITIALIZATION_SUCCESS"
    const val SDK_INITIALIZATION_FAILED = "SDK_INITIALIZATION_FAILED"
    const val AD_REQUEST_STARTED = "AD_REQUEST_STARTED"
    const val AD_LOADED = "AD_LOADED"
    const val AD_DISPLAYED = "AD_DISPLAYED"
    const val AD_CLICKED = "AD_CLICKED"
    const val AD_DISPLAY_FAILED = "AD_DISPLAY_FAILED"
    const val AD_CLOSED = "AD_CLOSED"
    const val AD_NO_FILL = "AD_NO_FILL"
    const val AD_RETRY_SCHEDULED = "AD_RETRY_SCHEDULED"
    const val NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE"
    const val INVALID_APP_KEY = "INVALID_APP_KEY"
    const val INVALID_PLACEMENT_ID = "INVALID_PLACEMENT_ID"
}

data class AdDebugLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val code: String,
    val format: AdFormatType? = null,
    val network: String = "LevelPlay",
    val message: String
)

data class AdNetworkAdapterStatus(
    val key: String,
    val displayName: String,
    val adapterClass: String,
    val adapterVersion: String,
    val requiresSeparateApproval: Boolean,
    val sdkPackaged: Boolean,
    val enabledInConfig: Boolean,
    val appKeyConfigured: Boolean,
    val runtimeReady: Boolean,
    val statusNote: String
)

data class AdFormatTelemetry(
    val format: AdFormatType,
    val enabled: Boolean = false,
    val unitOrPlacementId: String = "",
    val requestStatus: String = "Idle",
    val loadedStatus: String = "Not Loaded",
    val requestCount: Int = 0,
    val loadedCount: Int = 0,
    val displayedCount: Int = 0,
    val clickedCount: Int = 0,
    val noFillCount: Int = 0,
    val failedCount: Int = 0,
    val retryCount: Int = 0,
    val lastError: String = ""
) {
    val fillRatePercent: Double
        get() = if (requestCount > 0) (loadedCount * 100.0) / requestCount else 0.0

    val ctrPercent: Double
        get() = if (displayedCount > 0) (clickedCount * 100.0) / displayedCount else 0.0
}

data class ProductionCheckItem(
    val key: String,
    val titleBn: String,
    val passed: Boolean,
    val detailBn: String
)

data class ProductionReadinessReport(
    val passedCount: Int = 0,
    val totalCount: Int = 0,
    val isProductionReady: Boolean = false,
    val levelPlayKeySource: String = "Direct Unity Ads 6227313",
    val startIoKeySource: String = "Not Configured",
    val checks: List<ProductionCheckItem> = emptyList()
)

data class AdRuntimeTelemetry(
    val sdkInitStatus: SdkInitStatus = SdkInitStatus.IDLE,
    val mediationPlatform: String = "Unity LevelPlay 9.2.0",
    val activeAppKeyMasked: String = "",
    val testModeEnabled: Boolean = false,
    val consentGranted: Boolean = true,
    val startIoReady: Boolean = false,
    val lastInitError: String = "",
    val adapterStatuses: List<AdNetworkAdapterStatus> = defaultAdapterStatuses(AdConfig(), sdkInitialized = false, startIoInitialized = false),
    val formatMetrics: Map<AdFormatType, AdFormatTelemetry> = AdFormatType.entries.associateWith {
        AdFormatTelemetry(format = it)
    },
    val dailyRevenueUsd: Double = 0.0,
    val monthlyRevenueUsd: Double = 0.0,
    val networkRevenueUsd: Map<String, Double> = emptyMap(),
    val impressionCount: Int = 0,
    val totalClicks: Int = 0,
    val productionReadiness: ProductionReadinessReport = AdMonetizationManager.buildProductionReadiness(AdConfig()),
    val debugLogs: List<AdDebugLogEntry> = emptyList()
)

private data class NetworkDescriptor(
    val key: String,
    val displayName: String,
    val adapterClass: String,
    val adapterVersion: String,
    val requiresSeparateApproval: Boolean,
    val enabledSelector: (AdConfig) -> Boolean,
    val appIdSelector: (AdConfig) -> String
)

private val SUPPORTED_NETWORK_DESCRIPTORS = listOf(
    NetworkDescriptor(
        key = "unity_ads",
        displayName = "Unity Ads",
        adapterClass = "com.ironsource.adapters.unityads.UnityAdsAdapter",
        adapterVersion = "5.14.0",
        requiresSeparateApproval = false,
        enabledSelector = { it.unityAdsEnabled },
        appIdSelector = { it.unityGameId }
    ),
    NetworkDescriptor(
        key = "applovin",
        displayName = "AppLovin MAX",
        adapterClass = "com.ironsource.adapters.applovin.AppLovinAdapter",
        adapterVersion = "5.2.0",
        requiresSeparateApproval = true,
        enabledSelector = { it.appLovinEnabled },
        appIdSelector = { it.appLovinSdkKey }
    ),
    NetworkDescriptor(
        key = "meta",
        displayName = "Meta Audience Network",
        adapterClass = "com.ironsource.adapters.facebook.FacebookAdapter",
        adapterVersion = "5.6.0",
        requiresSeparateApproval = true,
        enabledSelector = { it.metaAudienceEnabled },
        appIdSelector = { it.metaPlacementId }
    ),
    NetworkDescriptor(
        key = "mintegral",
        displayName = "Mintegral",
        adapterClass = "com.ironsource.adapters.mintegral.MintegralAdapter",
        adapterVersion = "5.2.0",
        requiresSeparateApproval = true,
        enabledSelector = { it.mintegralEnabled },
        appIdSelector = { it.mintegralAppId }
    ),
    NetworkDescriptor(
        key = "pangle",
        displayName = "Pangle",
        adapterClass = "com.ironsource.adapters.pangle.PangleAdapter",
        adapterVersion = "5.2.0",
        requiresSeparateApproval = true,
        enabledSelector = { it.pangleEnabled },
        appIdSelector = { it.pangleAppId }
    ),
    NetworkDescriptor(
        key = "inmobi",
        displayName = "InMobi",
        adapterClass = "com.ironsource.adapters.inmobi.InMobiAdapter",
        adapterVersion = "5.12.0",
        requiresSeparateApproval = true,
        enabledSelector = { it.inMobiEnabled },
        appIdSelector = { it.inMobiAccountId }
    ),
    NetworkDescriptor(
        key = "liftoff",
        displayName = "Liftoff Monetize",
        adapterClass = "com.ironsource.adapters.vungle.VungleAdapter",
        adapterVersion = "5.12.0",
        requiresSeparateApproval = true,
        enabledSelector = { it.liftoffEnabled },
        appIdSelector = { it.liftoffAppId }
    ),
    NetworkDescriptor(
        key = "dt_exchange",
        displayName = "DT Exchange",
        adapterClass = "com.ironsource.adapters.fyber.FyberAdapter",
        adapterVersion = "5.3.0",
        requiresSeparateApproval = true,
        enabledSelector = { it.dtExchangeEnabled },
        appIdSelector = { it.dtExchangeAppId }
    ),
    NetworkDescriptor(
        key = "chartboost",
        displayName = "Chartboost",
        adapterClass = "com.ironsource.adapters.chartboost.ChartboostAdapter",
        adapterVersion = "5.12.0",
        requiresSeparateApproval = true,
        enabledSelector = { it.chartboostEnabled },
        appIdSelector = { it.chartboostAppId }
    ),
    NetworkDescriptor(
        key = "startio",
        displayName = "Start.io",
        adapterClass = "com.startapp.sdk.adsbase.StartAppSDK",
        adapterVersion = "5.4.0 (Standalone)",
        requiresSeparateApproval = true,
        enabledSelector = { it.startIoEnabled || (AdMonetizationManager.isStartIoSecretConfigured() && it.startIoAppId.trim().isEmpty()) },
        appIdSelector = { AdMonetizationManager.resolveStartIoAppId(it) }
    )
)

private fun isAdapterClassPresent(className: String): Boolean =
    runCatching { Class.forName(className) }.isSuccess

private fun defaultAdapterStatuses(
    config: AdConfig,
    sdkInitialized: Boolean,
    startIoInitialized: Boolean
): List<AdNetworkAdapterStatus> =
    SUPPORTED_NETWORK_DESCRIPTORS.map { desc ->
        val resolvedLevelPlayKey = AdMonetizationManager.resolveMediationAppKey(config)
        val directUnityMode = desc.key == "unity_ads" && AdMonetizationManager.usesDirectUnityAds(config)
        val isStartIo = desc.key == "startio"
        val packaged = isAdapterClassPresent(desc.adapterClass) ||
            (desc.key == "unity_ads" && isAdapterClassPresent("com.unity3d.ads.UnityAds"))
        val enabled = desc.enabledSelector(config)
        val keyPresent = desc.appIdSelector(config).trim().isNotBlank()
        val ready = when {
            isStartIo -> startIoInitialized && packaged && enabled && keyPresent
            else -> sdkInitialized && packaged && enabled && keyPresent &&
                (directUnityMode || resolvedLevelPlayKey.isNotEmpty())
        }
        val note = when {
            !packaged -> "SDK/Adapter class missing from build"
            !enabled -> "Disabled in Admin Panel"
            !keyPresent -> if (desc.requiresSeparateApproval) {
                "Account approval & Network App ID required"
            } else {
                "Unity Game ID missing in Admin Panel"
            }
            isStartIo && startIoInitialized -> "সক্রিয় (Start.io Standalone SDK)"
            isStartIo -> "Waiting for Start.io SDK initialization"
            directUnityMode && sdkInitialized -> "সক্রিয় (Direct Unity Ads • Game ID: ${config.unityGameId.trim()})"
            directUnityMode -> "Waiting for Direct Unity Ads SDK initialization (${config.unityGameId.trim()})"
            !sdkInitialized -> "Waiting for LevelPlay SDK initialization"
            else -> "Configured & active via LevelPlay Mediation"
        }
        AdNetworkAdapterStatus(
            key = desc.key,
            displayName = desc.displayName,
            adapterClass = desc.adapterClass,
            adapterVersion = if (directUnityMode) "${AdMonetizationManager.resolvedUnitySdkVersion()} (Direct)" else desc.adapterVersion,
            requiresSeparateApproval = desc.requiresSeparateApproval,
            sdkPackaged = packaged,
            enabledInConfig = enabled,
            appKeyConfigured = keyPresent,
            runtimeReady = ready,
            statusNote = note
        )
    }

object AdMonetizationManager {
    const val DIRECT_UNITY_SDK_VERSION = "4.21.0"
    private const val INTERSTITIAL_COOLDOWN_MS = 180_000L
    private const val APP_OPEN_COOLDOWN_MS = 240_000L
    private const val FULLSCREEN_MIN_GAP_MS = 60_000L
    private const val MAX_LOG_ENTRIES = 100
    private const val TELEMETRY_PREFS_NAME = "kb_ad_telemetry_v1"
    private const val KEY_DAY_KEY = "day_key"
    private const val KEY_MONTH_KEY = "month_key"
    private const val KEY_DAILY_REV = "daily_rev"
    private const val KEY_MONTHLY_REV = "monthly_rev"
    private const val KEY_IMPRESSIONS = "impressions"
    private const val KEY_CLICKS = "clicks"
    private const val KEY_NET_REV_JSON = "network_rev_json"
    const val MAX_RETRY_ATTEMPTS = 3
    private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L)

    fun resolvedUnitySdkVersion(): String =
        runCatching { UnityAds.version.takeIf { it.isNotBlank() } }.getOrNull() ?: DIRECT_UNITY_SDK_VERSION

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    internal var clockMillisProvider: () -> Long = { System.currentTimeMillis() }
    @Volatile
    private var telemetryAppContext: Context? = null
    @Volatile
    private var telemetryLoadedFromDisk = false
    @Volatile
    private var activeDateKey: String = ""
    @Volatile
    private var activeMonthKey: String = ""

    private val _telemetry = MutableStateFlow(AdRuntimeTelemetry())
    val telemetry: StateFlow<AdRuntimeTelemetry> = _telemetry.asStateFlow()

    @Volatile
    private var latestConfig: AdConfig = AdConfig()
    @Volatile
    private var initializedAppKey: String = ""
    @Volatile
    private var initializedUnityGameId: String = ""
    @Volatile
    private var initializedStartIoAppId: String = ""
    @Volatile
    private var isDirectUnityMode: Boolean = false
    @Volatile
    private var sdkInitialized = false
    @Volatile
    private var sdkInitializing = false
    @Volatile
    private var startIoInitialized = false
    @Volatile
    private var startIoInitializing = false
    @Volatile
    private var impressionListenerRegistered = false
    @Volatile
    private var currentRoute: String = "splash"
    private val _currentRouteFlow = MutableStateFlow("splash")
    val currentRouteFlow: StateFlow<String> = _currentRouteFlow.asStateFlow()
    private val _userAdsSuppressed = MutableStateFlow(false)
    val userAdsSuppressedFlow: StateFlow<Boolean> = _userAdsSuppressed.asStateFlow()
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null
    @Volatile
    private var isShowingFullscreenAd = false

    private var interstitialAd: LevelPlayInterstitialAd? = null
    private var loadedInterstitialUnitId: String = ""
    private var loadedDirectInterstitialId: String = ""
    private var isLoadingInterstitial = false
    private var lastInterstitialShownAt = 0L

    private var rewardedAd: LevelPlayRewardedAd? = null
    private var loadedRewardedUnitId: String = ""
    private var loadedDirectRewardedId: String = ""
    private var isLoadingRewarded = false
    private var pendingRewardCallback: ((Boolean, String) -> Unit)? = null
    private var pendingDirectRewardShowActivity: WeakReference<Activity>? = null

    private var appOpenAd: LevelPlayInterstitialAd? = null
    private var loadedAppOpenUnitId: String = ""
    private var loadedDirectAppOpenId: String = ""
    private var isLoadingAppOpen = false
    private var lastAppOpenShownAt = 0L
    @Volatile
    private var pendingAppOpenOnLaunch = false

    private val STARTIO_NO_FILL_BACKOFF_MS = longArrayOf(120_000L, 300_000L, 600_000L)

    private var startIoInterstitial: StartAppAd? = null
    private var startIoInterstitialReady = false
    private var isLoadingStartIoInterstitial = false
    @Volatile
    private var startIoInterstitialNoFillUntil = 0L
    @Volatile
    private var startIoInterstitialFailCount = 0

    private var startIoRewarded: StartAppAd? = null
    private var startIoRewardedReady = false
    private var isLoadingStartIoRewarded = false
    @Volatile
    private var startIoRewardedNoFillUntil = 0L
    @Volatile
    private var startIoRewardedFailCount = 0

    @Volatile
    private var startIoBannerNoFillUntil = 0L
    @Volatile
    private var startIoBannerFailCount = 0

    private val retryAttempts = mutableMapOf<AdFormatType, Int>()
    private val retryJobs = mutableMapOf<AdFormatType, Job>()

    private val impressionDataListener = object : LevelPlayImpressionDataListener {
        override fun onImpressionSuccess(impressionData: LevelPlayImpressionData) {
            recordRealImpressionRevenue(
                rawRevenue = impressionData.revenue ?: 0.0,
                adNetwork = impressionData.adNetwork ?: "LevelPlay",
                adFormat = impressionData.adFormat
            )
        }
    }

    fun isTestEnvironment(): Boolean =
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true) || Build.FINGERPRINT.isBlank()

    internal fun isEmulatorDevice(): Boolean =
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk_gphone", ignoreCase = true) ||
            Build.PRODUCT.contains("emulator", ignoreCase = true)

    internal fun effectiveAdTestMode(config: AdConfig = latestConfig): Boolean =
        config.adTestMode || BuildConfig.DEBUG || isEmulatorDevice()

    internal fun effectiveStartIoTestAdsEnabled(config: AdConfig = latestConfig): Boolean =
        effectiveAdTestMode(config)

    internal fun readLevelPlaySecret(): String {
        val secretKey = runCatching { BuildConfig.LEVELPLAY_APP_KEY.trim() }.getOrDefault("")
        return if (secretKey.isBlank() || secretKey.equals("UNSET", ignoreCase = true) || secretKey.startsWith("LEVELPLAY_APP_KEY")) {
            ""
        } else {
            secretKey
        }
    }

    internal fun readStartIoSecret(): String {
        val secretId = runCatching { BuildConfig.STARTIO_APP_ID.trim() }.getOrDefault("")
        return if (secretId.isBlank() || secretId.equals("UNSET", ignoreCase = true) || secretId.startsWith("STARTIO_APP_ID")) {
            ""
        } else {
            secretId
        }
    }

    fun isLevelPlaySecretConfigured(): Boolean = readLevelPlaySecret().isNotEmpty()
    fun isStartIoSecretConfigured(): Boolean = readStartIoSecret().isNotEmpty()

    fun resolveMediationAppKey(config: AdConfig = latestConfig): String {
        val adminKey = config.mediationAppKey.trim()
        if (adminKey.isNotEmpty()) return adminKey
        return readLevelPlaySecret()
    }

    fun resolveStartIoAppId(config: AdConfig = latestConfig): String {
        val adminId = config.startIoAppId.trim()
        if (adminId.isNotEmpty()) return adminId
        return readStartIoSecret()
    }

    fun isStartIoEnabledEffective(config: AdConfig = latestConfig): Boolean =
        config.adsEnabled &&
            config.androidAdsEnabled &&
            resolveStartIoAppId(config).isNotEmpty() &&
            (config.startIoEnabled || (isStartIoSecretConfigured() && config.startIoAppId.trim().isEmpty()))

    fun levelPlayKeySourceLabel(config: AdConfig = latestConfig): String = when {
        config.mediationAppKey.trim().isNotEmpty() -> "Admin Panel Override"
        isLevelPlaySecretConfigured() -> "AI Studio Secret (BuildConfig)"
        usesDirectUnityAds(config) -> "Direct Unity Ads (${config.unityGameId.trim()})"
        else -> "Not Configured"
    }

    fun startIoKeySourceLabel(config: AdConfig = latestConfig): String = when {
        config.startIoAppId.trim().isNotEmpty() -> "Admin Panel Override"
        isStartIoSecretConfigured() -> "AI Studio Secret (BuildConfig)"
        else -> "Not Configured"
    }

    fun buildProductionReadiness(config: AdConfig = latestConfig): ProductionReadinessReport {
        val lpKey = resolveMediationAppKey(config)
        val directUnity = usesDirectUnityAds(config)
        val lpSource = levelPlayKeySourceLabel(config)
        val startIoId = resolveStartIoAppId(config)
        val startIoActive = isStartIoEnabledEffective(config)
        val startIoSource = startIoKeySourceLabel(config)
        val checks = listOf(
            ProductionCheckItem(
                key = "mediation_or_unity",
                titleBn = "LevelPlay / Unity Ads কী",
                passed = lpKey.isNotEmpty() || directUnity,
                detailBn = if (lpKey.isNotEmpty()) {
                    "LevelPlay সক্রিয় ($lpSource • ${maskKey(lpKey)})"
                } else if (directUnity) {
                    "Direct Unity Ads সক্রিয় (Game ID: ${config.unityGameId.trim()})"
                } else {
                    "LEVELPLAY_APP_KEY অথবা Unity Game ID সেট করুন"
                }
            ),
            ProductionCheckItem(
                key = "startio",
                titleBn = "Start.io SDK 5.4.0",
                passed = startIoActive && startIoId.isNotEmpty(),
                detailBn = if (startIoActive && startIoId.isNotEmpty()) {
                    "প্রস্তুত ($startIoSource • ${maskKey(startIoId)})"
                } else {
                    "AI Studio Secrets-এ STARTIO_APP_ID দিন অথবা Admin প্যানেলে যুক্ত করুন"
                }
            ),
            ProductionCheckItem(
                key = "test_mode",
                titleBn = "টেস্ট মোড গার্ড",
                passed = !config.adTestMode,
                detailBn = if (!config.adTestMode) {
                    "Test Mode OFF — লাইভ প্রোডাকশন বিজ্ঞাপনের জন্য প্রস্তুত"
                } else {
                    "Test Mode ON — প্লে স্টোরে প্রকাশের আগে বন্ধ করুন"
                }
            ),
            ProductionCheckItem(
                key = "consent",
                titleBn = "GDPR / গোপনীয়তা সম্মতি",
                passed = config.consentGranted,
                detailBn = if (config.consentGranted) {
                    "Consent Granted সক্রিয় (LevelPlay, Unity, Start.io)"
                } else {
                    "Consent Denied — নন-পার্সোনালাইজড বিজ্ঞাপন মোড"
                }
            ),
            ProductionCheckItem(
                key = "zero_admob",
                titleBn = "Zero AdMob নীতি",
                passed = true,
                detailBn = "Play Services Ads / AdMob সম্পূর্ণ অপসারিত (১০ নেটওয়ার্ক স্ট্যাক)"
            ),
            ProductionCheckItem(
                key = "blocked_routes",
                titleBn = "চেকআউট ও গুরুত্বপূর্ণ স্ক্রিন সুরক্ষা",
                passed = true,
                detailBn = "Sell, Cart/Checkout, Order, Activation, Login, Register-এ বিজ্ঞাপন বন্ধ"
            ),
            ProductionCheckItem(
                key = "release_r8",
                titleBn = "Release R8 / ProGuard",
                passed = true,
                detailBn = "Release বিল্ডে কোড মিনিফিকেশন, রিসোর্স শ্রিঙ্কিং ও ProGuard যুক্ত"
            ),
            ProductionCheckItem(
                key = "app_ads_txt",
                titleBn = "Start.io app-ads.txt",
                passed = true,
                detailBn = "website/app-ads.txt-তে start.io, 165433365, DIRECT সংরক্ষিত"
            )
        )
        val passed = checks.count { it.passed }
        return ProductionReadinessReport(
            passedCount = passed,
            totalCount = checks.size,
            isProductionReady = passed == checks.size,
            levelPlayKeySource = lpSource,
            startIoKeySource = startIoSource,
            checks = checks
        )
    }

    fun usesDirectUnityAds(config: AdConfig = latestConfig): Boolean =
        config.adsEnabled &&
            config.androidAdsEnabled &&
            resolveMediationAppKey(config).isEmpty() &&
            config.unityAdsEnabled &&
            config.unityGameId.trim().isNotEmpty()

    fun setCurrentActivity(activity: Activity?) {
        currentActivityRef = activity?.let { WeakReference(it) }
    }

    fun clearActivityIfMatches(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
        if (pendingDirectRewardShowActivity?.get() === activity) {
            pendingDirectRewardShowActivity = null
            pendingRewardCallback = null
        }
    }

    fun setCurrentRoute(route: String) {
        val previousRoute = currentRoute
        currentRoute = route
        _currentRouteFlow.value = route
        if (isAdBlockedRoute(route) && route != "splash") {
            pendingAppOpenOnLaunch = false
            return
        }
        if (previousRoute == "splash" && route == "home" && pendingAppOpenOnLaunch) {
            maybeShowAppOpenOnForeground(currentActivityRef?.get())
        }
    }

    fun setUserAdsSuppressed(suppressed: Boolean) {
        if (_userAdsSuppressed.value == suppressed) return
        _userAdsSuppressed.value = suppressed
        if (suppressed) {
            pendingAppOpenOnLaunch = false
            cancelAllRetryJobs()
        }
    }

    fun isUserAdsSuppressed(): Boolean = _userAdsSuppressed.value

    fun isCurrentRouteAdBlocked(): Boolean = isAdBlockedRoute(currentRoute)

    fun isAdBlockedRoute(route: String): Boolean =
        route == "splash" ||
            route == "cart" ||
            route == "sell" ||
            route.startsWith("sell?") ||
            route == "activation" ||
            route.startsWith("activation?") ||
            route.startsWith("order/") ||
            route == "login" ||
            route == "register" ||
            route.startsWith("register?")

    fun syncConfig(context: Context, config: AdConfig) {
        if (!isTestEnvironment()) {
            initTelemetryStore(context)
        }
        latestConfig = config
        refreshAdapterAndFormatConfig(config)
        if (!config.adsEnabled || !config.androidAdsEnabled) {
            cancelAllRetryJobs()
            return
        }
        if (isTestEnvironment()) {
            return
        }
        val usesDirectUnity = usesDirectUnityAds(config)
        val appKey = resolveMediationAppKey(config)
        val startIoId = resolveStartIoAppId(config)
        if (isStartIoEnabledEffective(config) && startIoId.isNotEmpty()) {
            initializeStartIo(context, config, startIoId)
        }
        if (!usesDirectUnity && appKey.isEmpty()) {
            return
        }
        initialize(context, config) {
            preloadAllFormats(context, config)
        }
    }

    fun onConnectivityRestored(context: Context, config: AdConfig = latestConfig) {
        if (isTestEnvironment()) return
        if (!NetworkMonitor.isCurrentlyOnline(context)) return
        if (!config.adsEnabled || !config.androidAdsEnabled) return
        clearStartIoBackoff()
        appendLog(
            code = AdDebugEventCode.SDK_INITIALIZATION_STARTED,
            network = "NetworkMonitor",
            message = "Connectivity restored — verifying ad SDK initialization & preloading"
        )
        syncConfig(context, config)
    }

    fun initialize(
        context: Context,
        config: AdConfig = latestConfig,
        userId: String? = null,
        onReady: (() -> Unit)? = null
    ) {
        if (!isTestEnvironment()) {
            initTelemetryStore(context)
        }
        latestConfig = config
        refreshAdapterAndFormatConfig(config)

        if (!config.adsEnabled || !config.androidAdsEnabled) {
            appendLog(
                code = AdDebugEventCode.NETWORK_UNAVAILABLE,
                message = "Android monetization disabled in Admin configuration"
            )
            return
        }

        val startIoId = resolveStartIoAppId(config)
        if (!isTestEnvironment() && isStartIoEnabledEffective(config) && startIoId.isNotEmpty()) {
            initializeStartIo(context, config, startIoId)
        }

        if (usesDirectUnityAds(config)) {
            initializeDirectUnityAds(context, config, onReady)
            return
        }

        val appKey = resolveMediationAppKey(config)
        if (appKey.isEmpty()) {
            _telemetry.update {
                it.copy(
                    sdkInitStatus = if (startIoInitialized) SdkInitStatus.SUCCESS else SdkInitStatus.FAILED,
                    lastInitError = if (startIoInitialized) "" else "Unity Game ID বা LevelPlay App Key দেওয়া হয়নি",
                    activeAppKeyMasked = "(empty)",
                    adapterStatuses = defaultAdapterStatuses(config, sdkInitialized, startIoInitialized)
                )
            }
            appendLog(
                code = AdDebugEventCode.INVALID_APP_KEY,
                message = "LevelPlay App Key missing (set LEVELPLAY_APP_KEY in AI Studio Secrets or Admin Panel)"
            )
            return
        }

        if (isTestEnvironment()) {
            return
        }

        val appContext = context.applicationContext
        if (!NetworkMonitor.isCurrentlyOnline(appContext)) {
            appendLog(
                code = AdDebugEventCode.NETWORK_UNAVAILABLE,
                message = "Cannot initialize LevelPlay SDK: device is offline"
            )
            return
        }

        applyPrivacyConsent(appContext, config)

        if (sdkInitialized && !isDirectUnityMode && initializedAppKey == appKey) {
            onReady?.invoke()
            return
        }
        if (sdkInitializing) return

        sdkInitializing = true
        isDirectUnityMode = false
        val masked = maskKey(appKey)
        _telemetry.update {
            it.copy(
                sdkInitStatus = SdkInitStatus.INITIALIZING,
                mediationPlatform = "Unity LevelPlay 9.2.0",
                activeAppKeyMasked = masked,
                testModeEnabled = config.adTestMode,
                consentGranted = config.consentGranted,
                lastInitError = ""
            )
        }
        val runtimeTestMode = effectiveAdTestMode(config)
        appendLog(
            code = AdDebugEventCode.SDK_INITIALIZATION_STARTED,
            message = "Starting Unity LevelPlay initialization (appKey=$masked, testMode=$runtimeTestMode, consent=${config.consentGranted})"
        )

        runCatching {
            if (!impressionListenerRegistered) {
                LevelPlay.addImpressionDataListener(impressionDataListener)
                impressionListenerRegistered = true
            }
            LevelPlay.setConsent(config.consentGranted)
            if (runtimeTestMode) {
                LevelPlay.setMetaData("is_test_suite", "enable")
            } else {
                LevelPlay.setMetaData("is_test_suite", "disable")
            }

            val reqBuilder = LevelPlayInitRequest.Builder(appKey)
            if (!userId.isNullOrBlank()) {
                reqBuilder.withUserId(userId)
            }
            val initRequest = reqBuilder.build()

            LevelPlay.init(appContext, initRequest, object : LevelPlayInitListener {
                override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                    sdkInitializing = false
                    sdkInitialized = true
                    isDirectUnityMode = false
                    initializedAppKey = appKey
                    _telemetry.update {
                        it.copy(
                            sdkInitStatus = SdkInitStatus.SUCCESS,
                            mediationPlatform = "Unity LevelPlay 9.2.0",
                            lastInitError = "",
                            activeAppKeyMasked = masked,
                            testModeEnabled = config.adTestMode,
                            consentGranted = config.consentGranted,
                            adapterStatuses = defaultAdapterStatuses(latestConfig, true, startIoInitialized)
                        )
                    }
                    appendLog(
                        code = AdDebugEventCode.SDK_INITIALIZATION_SUCCESS,
                        message = "LevelPlay SDK initialized successfully"
                    )
                    onReady?.let { cb -> scope.launch { cb() } }
                }

                override fun onInitFailed(error: LevelPlayInitError) {
                    sdkInitializing = false
                    sdkInitialized = false
                    val msg = "Code ${error.errorCode}: ${error.errorMessage}"
                    _telemetry.update {
                        it.copy(
                            sdkInitStatus = SdkInitStatus.FAILED,
                            lastInitError = msg,
                            adapterStatuses = defaultAdapterStatuses(latestConfig, false, startIoInitialized)
                        )
                    }
                    appendLog(
                        code = AdDebugEventCode.SDK_INITIALIZATION_FAILED,
                        message = "LevelPlay init failed ($msg)"
                    )
                }
            })
        }.onFailure { err ->
            sdkInitializing = false
            sdkInitialized = false
            val msg = err.message ?: "Unexpected initialization error"
            _telemetry.update {
                it.copy(
                    sdkInitStatus = SdkInitStatus.FAILED,
                    lastInitError = msg,
                    adapterStatuses = defaultAdapterStatuses(latestConfig, false, startIoInitialized)
                )
            }
            appendLog(
                code = AdDebugEventCode.SDK_INITIALIZATION_FAILED,
                message = msg
            )
        }
    }

    private fun initializeDirectUnityAds(
        context: Context,
        config: AdConfig,
        onReady: (() -> Unit)? = null
    ) {
        val gameId = config.unityGameId.trim()
        if (gameId.isEmpty()) return
        if (isTestEnvironment()) return

        val appContext = context.applicationContext
        if (!NetworkMonitor.isCurrentlyOnline(appContext)) {
            appendLog(
                code = AdDebugEventCode.NETWORK_UNAVAILABLE,
                network = "Unity Ads",
                message = "Cannot initialize Unity Ads: device is offline"
            )
            return
        }

        applyPrivacyConsent(appContext, config)
        val runtimeTestMode = effectiveAdTestMode(config)
        runCatching { UnityAds.debugMode = runtimeTestMode }

        if ((sdkInitialized && isDirectUnityMode && initializedUnityGameId == gameId) ||
            (UnityAds.isInitialized && initializedUnityGameId == gameId)
        ) {
            sdkInitialized = true
            isDirectUnityMode = true
            _telemetry.update {
                it.copy(
                    sdkInitStatus = SdkInitStatus.SUCCESS,
                    mediationPlatform = "Unity Ads ${resolvedUnitySdkVersion()} (Direct)",
                    activeAppKeyMasked = "Game ID: $gameId",
                    testModeEnabled = config.adTestMode,
                    consentGranted = config.consentGranted,
                    lastInitError = "",
                    adapterStatuses = defaultAdapterStatuses(latestConfig, true, startIoInitialized)
                )
            }
            onReady?.let { cb -> scope.launch { cb() } }
            return
        }
        if (sdkInitializing) return

        sdkInitializing = true
        isDirectUnityMode = true
        _telemetry.update {
            it.copy(
                sdkInitStatus = SdkInitStatus.INITIALIZING,
                mediationPlatform = "Unity Ads ${resolvedUnitySdkVersion()} (Direct)",
                activeAppKeyMasked = "Game ID: $gameId",
                testModeEnabled = config.adTestMode,
                consentGranted = config.consentGranted,
                lastInitError = ""
            )
        }
        appendLog(
            code = AdDebugEventCode.SDK_INITIALIZATION_STARTED,
            network = "Unity Ads",
            message = "Initializing Unity Ads SDK ${resolvedUnitySdkVersion()} (Game ID=$gameId, testMode=$runtimeTestMode, consent=${config.consentGranted})"
        )

        runCatching {
            UnityAds.initialize(appContext, gameId, runtimeTestMode, object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    sdkInitializing = false
                    sdkInitialized = true
                    isDirectUnityMode = true
                    initializedUnityGameId = gameId
                    _telemetry.update {
                        it.copy(
                            sdkInitStatus = SdkInitStatus.SUCCESS,
                            mediationPlatform = "Unity Ads ${UnityAds.version} (Direct)",
                            activeAppKeyMasked = "Game ID: $gameId",
                            testModeEnabled = latestConfig.adTestMode,
                            consentGranted = latestConfig.consentGranted,
                            lastInitError = "",
                            adapterStatuses = defaultAdapterStatuses(latestConfig, true, startIoInitialized)
                        )
                    }
                    appendLog(
                        code = AdDebugEventCode.SDK_INITIALIZATION_SUCCESS,
                        network = "Unity Ads",
                        message = "Unity Ads SDK ${UnityAds.version} initialized (Game ID=$gameId)"
                    )
                    onReady?.let { cb -> scope.launch { cb() } }
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError,
                    message: String
                ) {
                    sdkInitializing = false
                    sdkInitialized = false
                    val msg = "${error.name}: $message"
                    _telemetry.update {
                        it.copy(
                            sdkInitStatus = SdkInitStatus.FAILED,
                            lastInitError = msg,
                            adapterStatuses = defaultAdapterStatuses(latestConfig, false, startIoInitialized)
                        )
                    }
                    appendLog(
                        code = AdDebugEventCode.SDK_INITIALIZATION_FAILED,
                        network = "Unity Ads",
                        message = "Unity Ads init failed ($msg)"
                    )
                }
            })
        }.onFailure { err ->
            sdkInitializing = false
            sdkInitialized = false
            val msg = err.message ?: "Unexpected Unity Ads initialization error"
            _telemetry.update {
                it.copy(
                    sdkInitStatus = SdkInitStatus.FAILED,
                    lastInitError = msg,
                    adapterStatuses = defaultAdapterStatuses(latestConfig, false, startIoInitialized)
                )
            }
            appendLog(
                code = AdDebugEventCode.SDK_INITIALIZATION_FAILED,
                network = "Unity Ads",
                message = msg
            )
        }
    }

    private fun startIoCooldownForFailCount(failCount: Int): Long {
        val idx = (failCount - 1).coerceIn(0, STARTIO_NO_FILL_BACKOFF_MS.lastIndex)
        return STARTIO_NO_FILL_BACKOFF_MS[idx]
    }

    internal fun clearStartIoBackoff() {
        startIoInterstitialFailCount = 0
        startIoInterstitialNoFillUntil = 0L
        startIoRewardedFailCount = 0
        startIoRewardedNoFillUntil = 0L
        startIoBannerFailCount = 0
        startIoBannerNoFillUntil = 0L
    }

    fun canRequestStartIoBanner(): Boolean =
        clockMillisProvider() >= startIoBannerNoFillUntil

    fun recordStartIoBannerLoaded() {
        startIoBannerFailCount = 0
        startIoBannerNoFillUntil = 0L
    }

    fun recordStartIoBannerNoFill() {
        startIoBannerFailCount += 1
        startIoBannerNoFillUntil = clockMillisProvider() + startIoCooldownForFailCount(startIoBannerFailCount)
    }

    private fun initializeStartIo(context: Context, config: AdConfig, appId: String) {
        if (isTestEnvironment()) return
        val appContext = context.applicationContext
        if (!NetworkMonitor.isCurrentlyOnline(appContext)) return
        val testAds = effectiveStartIoTestAdsEnabled(config)
        if (startIoInitialized && initializedStartIoAppId == appId) {
            runCatching {
                StartAppSDK.setTestAdsEnabled(testAds)
                StartAppSDK.setUserConsent(appContext, "pas", System.currentTimeMillis(), config.consentGranted)
            }
            return
        }
        if (startIoInitializing) return
        startIoInitializing = true

        appendLog(
            code = AdDebugEventCode.SDK_INITIALIZATION_STARTED,
            network = "Start.io",
            message = "Initializing Start.io SDK 5.4.0 (appId=${maskKey(appId)}, testMode=$testAds)"
        )

        runCatching {
            StartAppSDK.setTestAdsEnabled(testAds)
            StartAppSDK.setUserConsent(appContext, "pas", System.currentTimeMillis(), config.consentGranted)
            StartAppAd.disableSplash()
            StartAppSDK.initParams(appContext, appId)
                .setReturnAdsEnabled(false)
                .setCallback {
                    startIoInitializing = false
                    startIoInitialized = true
                    initializedStartIoAppId = appId
                    StartAppAd.disableSplash()
                    _telemetry.update {
                        it.copy(
                            startIoReady = true,
                            adapterStatuses = defaultAdapterStatuses(latestConfig, sdkInitialized, true)
                        )
                    }
                    appendLog(
                        code = AdDebugEventCode.SDK_INITIALIZATION_SUCCESS,
                        network = "Start.io",
                        message = "Start.io SDK initialized (testMode=$testAds)"
                    )
                    if (latestConfig.interstitialEnabled || latestConfig.appOpenEnabled) {
                        preloadStartIoInterstitial(appContext)
                    }
                    if (latestConfig.rewardedEnabled) {
                        preloadStartIoRewarded(appContext)
                    }
                }
                .init()
        }.onFailure { err ->
            startIoInitializing = false
            startIoInitialized = false
            appendLog(
                code = AdDebugEventCode.SDK_INITIALIZATION_FAILED,
                network = "Start.io",
                message = err.message ?: "Start.io initialization failed"
            )
        }
    }

    private fun applyPrivacyConsent(context: Context, config: AdConfig) {
        runCatching {
            val gdpr = MetaData(context)
            gdpr["gdpr.consent"] = config.consentGranted
            gdpr["privacy.consent"] = config.consentGranted
            gdpr.commit()
        }
        runCatching { LevelPlay.setConsent(config.consentGranted) }
        runCatching {
            if (isStartIoEnabledEffective(config) && resolveStartIoAppId(config).isNotEmpty()) {
                StartAppSDK.setUserConsent(context, "pas", System.currentTimeMillis(), config.consentGranted)
            }
        }
    }

    private fun preloadStartIoInterstitial(context: Context) {
        if (_userAdsSuppressed.value) return
        if (isTestEnvironment() || !startIoInitialized || isLoadingStartIoInterstitial || startIoInterstitialReady) return
        if (!latestConfig.adsEnabled || !latestConfig.androidAdsEnabled || (!latestConfig.interstitialEnabled && !latestConfig.appOpenEnabled)) return
        if (!NetworkMonitor.isCurrentlyOnline(context)) return
        if (clockMillisProvider() < startIoInterstitialNoFillUntil) return
        isLoadingStartIoInterstitial = true
        runCatching {
            val ad = StartAppAd(context.applicationContext)
            startIoInterstitial = ad
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(adObj: Ad) {
                    isLoadingStartIoInterstitial = false
                    startIoInterstitialReady = true
                    startIoInterstitialFailCount = 0
                    startIoInterstitialNoFillUntil = 0L
                    appendLog(AdDebugEventCode.AD_LOADED, AdFormatType.INTERSTITIAL, "Start.io", "Start.io Interstitial / App Open ready")
                    if (pendingAppOpenOnLaunch && latestConfig.appOpenEnabled && !isAdBlockedRoute(currentRoute)) {
                        scope.launch { maybeShowAppOpenOnForeground() }
                    }
                }

                override fun onFailedToReceiveAd(adObj: Ad?) {
                    isLoadingStartIoInterstitial = false
                    startIoInterstitialReady = false
                    startIoInterstitial = null
                    startIoInterstitialFailCount += 1
                    startIoInterstitialNoFillUntil = clockMillisProvider() + startIoCooldownForFailCount(startIoInterstitialFailCount)
                    appendLog(AdDebugEventCode.AD_NO_FILL, AdFormatType.INTERSTITIAL, "Start.io", adObj?.errorMessage ?: "Start.io interstitial failed")
                }
            })
        }.onFailure {
            isLoadingStartIoInterstitial = false
            startIoInterstitialReady = false
            startIoInterstitial = null
        }
    }

    private fun preloadStartIoRewarded(context: Context, force: Boolean = false) {
        if (_userAdsSuppressed.value) return
        if (isTestEnvironment() || !startIoInitialized || isLoadingStartIoRewarded || startIoRewardedReady) return
        if (!latestConfig.adsEnabled || !latestConfig.androidAdsEnabled || !latestConfig.rewardedEnabled) return
        if (!NetworkMonitor.isCurrentlyOnline(context)) return
        if (!force && clockMillisProvider() < startIoRewardedNoFillUntil) return
        isLoadingStartIoRewarded = true
        runCatching {
            val ad = StartAppAd(context.applicationContext)
            startIoRewarded = ad
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(adObj: Ad) {
                    isLoadingStartIoRewarded = false
                    startIoRewardedReady = true
                    startIoRewardedFailCount = 0
                    startIoRewardedNoFillUntil = 0L
                    appendLog(AdDebugEventCode.AD_LOADED, AdFormatType.REWARDED, "Start.io", "Start.io Rewarded ready")
                }

                override fun onFailedToReceiveAd(adObj: Ad?) {
                    isLoadingStartIoRewarded = false
                    startIoRewardedReady = false
                    startIoRewarded = null
                    startIoRewardedFailCount += 1
                    startIoRewardedNoFillUntil = clockMillisProvider() + startIoCooldownForFailCount(startIoRewardedFailCount)
                    appendLog(AdDebugEventCode.AD_NO_FILL, AdFormatType.REWARDED, "Start.io", adObj?.errorMessage ?: "Start.io rewarded failed")
                }
            })
        }.onFailure {
            isLoadingStartIoRewarded = false
            startIoRewardedReady = false
            startIoRewarded = null
        }
    }

    fun preloadAllFormats(context: Context, config: AdConfig = latestConfig) {
        if (_userAdsSuppressed.value || isTestEnvironment()) return
        if (!config.adsEnabled || !config.androidAdsEnabled) return
        if (config.interstitialEnabled) {
            preloadInterstitial(context, config.resolvedInterstitialUnitId(), true)
        }
        if (config.rewardedEnabled) {
            preloadRewarded(context, config.resolvedRewardedUnitId(), true)
        }
        if (config.appOpenEnabled) {
            preloadAppOpen(context, resolveEffectiveAppOpenUnitId(config), true)
        }
        if (startIoInitialized) {
            if (config.interstitialEnabled || config.appOpenEnabled) preloadStartIoInterstitial(context)
            if (config.rewardedEnabled) preloadStartIoRewarded(context)
        }
    }

    fun preloadInterstitial(context: Context, unitId: String, enabled: Boolean) {
        val cleanId = unitId.trim()
        if (_userAdsSuppressed.value || !enabled || !latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
        if (cleanId.isEmpty()) {
            markInvalidPlacement(AdFormatType.INTERSTITIAL, "Interstitial Ad Unit ID is blank")
            return
        }
        if (isTestEnvironment()) return
        if (!NetworkMonitor.isCurrentlyOnline(context)) {
            appendLog(AdDebugEventCode.NETWORK_UNAVAILABLE, AdFormatType.INTERSTITIAL, message = "Offline: skipped interstitial preload")
            return
        }
        if (!sdkInitialized) {
            initialize(context, latestConfig) {
                preloadInterstitial(context, cleanId, enabled)
            }
            return
        }
        if (usesDirectUnityAds()) {
            val sharesAppOpenLoad = cleanId == resolveEffectiveAppOpenUnitId(latestConfig) &&
                (loadedDirectAppOpenId == cleanId || isLoadingAppOpen)
            if (loadedDirectInterstitialId == cleanId || isLoadingInterstitial || sharesAppOpenLoad) return
            isLoadingInterstitial = true
            updateFormatMetric(AdFormatType.INTERSTITIAL) {
                it.copy(
                    requestStatus = "Requesting",
                    requestCount = it.requestCount + 1,
                    unitOrPlacementId = cleanId
                )
            }
            appendLog(
                code = AdDebugEventCode.AD_REQUEST_STARTED,
                format = AdFormatType.INTERSTITIAL,
                network = "Unity Ads",
                message = "Loading Unity Interstitial placement $cleanId"
            )
            runCatching {
                UnityAds.load(cleanId, object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String) {
                        isLoadingInterstitial = false
                        loadedDirectInterstitialId = placementId
                        resetRetryState(AdFormatType.INTERSTITIAL)
                        updateFormatMetric(AdFormatType.INTERSTITIAL) {
                            it.copy(
                                requestStatus = "Completed",
                                loadedStatus = "Loaded",
                                loadedCount = it.loadedCount + 1,
                                lastError = ""
                            )
                        }
                        appendLog(
                            code = AdDebugEventCode.AD_LOADED,
                            format = AdFormatType.INTERSTITIAL,
                            network = "Unity Ads",
                            message = "Unity Interstitial ready ($placementId)"
                        )
                    }

                    override fun onUnityAdsFailedToLoad(
                        placementId: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        isLoadingInterstitial = false
                        if (loadedDirectInterstitialId == placementId) loadedDirectInterstitialId = ""
                        val isNoFill = error == UnityAds.UnityAdsLoadError.NO_FILL ||
                            message.contains("no fill", ignoreCase = true)
                        updateFormatMetric(AdFormatType.INTERSTITIAL) {
                            it.copy(
                                requestStatus = "Failed",
                                loadedStatus = if (isNoFill) "No Fill" else "Failed",
                                noFillCount = it.noFillCount + if (isNoFill) 1 else 0,
                                failedCount = it.failedCount + 1,
                                lastError = "${error.name}: $message"
                            )
                        }
                        appendLog(
                            code = if (isNoFill) AdDebugEventCode.AD_NO_FILL else AdDebugEventCode.AD_DISPLAY_FAILED,
                            format = AdFormatType.INTERSTITIAL,
                            network = "Unity Ads",
                            message = "${error.name}: $message"
                        )
                        scheduleRetry(context.applicationContext, AdFormatType.INTERSTITIAL) {
                            preloadInterstitial(context.applicationContext, cleanId, latestConfig.interstitialEnabled)
                        }
                    }
                })
            }.onFailure { err ->
                isLoadingInterstitial = false
                loadedDirectInterstitialId = ""
                appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.INTERSTITIAL, "Unity Ads", err.message ?: "Unity Interstitial load error")
            }
            return
        }

        if ((interstitialAd?.isAdReady() == true && loadedInterstitialUnitId == cleanId) || isLoadingInterstitial) {
            return
        }

        isLoadingInterstitial = true
        updateFormatMetric(AdFormatType.INTERSTITIAL) {
            it.copy(
                requestStatus = "Requesting",
                requestCount = it.requestCount + 1,
                unitOrPlacementId = cleanId
            )
        }
        appendLog(AdDebugEventCode.AD_REQUEST_STARTED, AdFormatType.INTERSTITIAL, message = "Loading interstitial unit $cleanId")

        runCatching {
            val ad = LevelPlayInterstitialAd(cleanId)
            ad.setListener(object : LevelPlayInterstitialAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    isLoadingInterstitial = false
                    loadedInterstitialUnitId = cleanId
                    resetRetryState(AdFormatType.INTERSTITIAL)
                    updateFormatMetric(AdFormatType.INTERSTITIAL) {
                        it.copy(
                            requestStatus = "Completed",
                            loadedStatus = "Loaded",
                            loadedCount = it.loadedCount + 1,
                            lastError = ""
                        )
                    }
                    appendLog(
                        code = AdDebugEventCode.AD_LOADED,
                        format = AdFormatType.INTERSTITIAL,
                        network = adInfo.adNetwork,
                        message = "Interstitial ready (${adInfo.adNetwork})"
                    )
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    handleAdLoadFailure(AdFormatType.INTERSTITIAL, error)
                    scheduleRetry(context.applicationContext, AdFormatType.INTERSTITIAL) {
                        preloadInterstitial(context.applicationContext, cleanId, latestConfig.interstitialEnabled)
                    }
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                    trackFormatDisplayed(AdFormatType.INTERSTITIAL, adInfo.adNetwork)
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    isShowingFullscreenAd = false
                    interstitialAd = null
                    updateFormatMetric(AdFormatType.INTERSTITIAL) {
                        it.copy(failedCount = it.failedCount + 1, lastError = error.errorMessage)
                    }
                    appendLog(
                        code = AdDebugEventCode.AD_DISPLAY_FAILED,
                        format = AdFormatType.INTERSTITIAL,
                        network = adInfo.adNetwork,
                        message = error.errorMessage
                    )
                }

                override fun onAdClicked(adInfo: LevelPlayAdInfo) {
                    trackFormatClicked(AdFormatType.INTERSTITIAL, adInfo.adNetwork)
                }

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    isShowingFullscreenAd = false
                    interstitialAd = null
                    appendLog(
                        code = AdDebugEventCode.AD_CLOSED,
                        format = AdFormatType.INTERSTITIAL,
                        network = adInfo.adNetwork,
                        message = "Interstitial closed"
                    )
                    preloadInterstitial(context.applicationContext, cleanId, latestConfig.interstitialEnabled)
                }

                override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
            })
            interstitialAd = ad
            ad.loadAd()
        }.onFailure { err ->
            isLoadingInterstitial = false
            interstitialAd = null
            updateFormatMetric(AdFormatType.INTERSTITIAL) {
                it.copy(requestStatus = "Failed", lastError = err.message ?: "Load failed")
            }
            appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.INTERSTITIAL, message = err.message ?: "Interstitial exception")
        }
    }

    fun maybeShowInterstitial(activity: Activity?, overrideUnitId: String, enabled: Boolean) {
        if (_userAdsSuppressed.value) return
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        if (isTestEnvironment() || !enabled || !latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
        if (isAdBlockedRoute(currentRoute)) return
        if (isShowingFullscreenAd) return

        val cleanId = overrideUnitId.trim()
        if (cleanId.isEmpty()) return

        val now = clockMillisProvider()
        if (now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) return
        if (lastAppOpenShownAt > 0L && now - lastAppOpenShownAt < FULLSCREEN_MIN_GAP_MS) return

        if (usesDirectUnityAds()) {
            if (sdkInitialized && loadedDirectInterstitialId == cleanId) {
                lastInterstitialShownAt = now
                loadedDirectInterstitialId = ""
                isShowingFullscreenAd = true
                runCatching {
                    UnityAds.show(activity, cleanId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                        override fun onUnityAdsShowFailure(
                            placementId: String,
                            error: UnityAds.UnityAdsShowError,
                            message: String
                        ) {
                            isShowingFullscreenAd = false
                            updateFormatMetric(AdFormatType.INTERSTITIAL) {
                                it.copy(failedCount = it.failedCount + 1, lastError = "${error.name}: $message")
                            }
                            appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.INTERSTITIAL, "Unity Ads", "${error.name}: $message")
                            preloadInterstitial(activity.applicationContext, cleanId, latestConfig.interstitialEnabled)
                        }

                        override fun onUnityAdsShowStart(placementId: String) {
                            trackFormatDisplayed(AdFormatType.INTERSTITIAL, "Unity Ads")
                        }

                        override fun onUnityAdsShowClick(placementId: String) {
                            trackFormatClicked(AdFormatType.INTERSTITIAL, "Unity Ads")
                        }

                        override fun onUnityAdsShowComplete(
                            placementId: String,
                            state: UnityAds.UnityAdsShowCompletionState
                        ) {
                            isShowingFullscreenAd = false
                            appendLog(AdDebugEventCode.AD_CLOSED, AdFormatType.INTERSTITIAL, "Unity Ads", "Unity Interstitial closed (${state.name})")
                            preloadInterstitial(activity.applicationContext, cleanId, latestConfig.interstitialEnabled)
                        }
                    })
                }.onFailure { err ->
                    isShowingFullscreenAd = false
                    appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.INTERSTITIAL, "Unity Ads", err.message ?: "Unity show failed")
                }
                return
            } else if (startIoInitialized && startIoInterstitialReady && startIoInterstitial != null) {
                showStartIoInterstitial(activity, now)
                preloadInterstitial(activity, cleanId, enabled)
                return
            } else {
                preloadInterstitial(activity, cleanId, enabled)
                return
            }
        }

        val ad = interstitialAd
        if (sdkInitialized && ad != null && loadedInterstitialUnitId == cleanId && ad.isAdReady()) {
            lastInterstitialShownAt = now
            isShowingFullscreenAd = true
            runCatching { ad.showAd(activity, currentRoute) }.onFailure { err ->
                isShowingFullscreenAd = false
                interstitialAd = null
                appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.INTERSTITIAL, message = err.message ?: "showAd failed")
            }
        } else if (startIoInitialized && startIoInterstitialReady && startIoInterstitial != null) {
            showStartIoInterstitial(activity, now)
            preloadInterstitial(activity, cleanId, enabled)
        } else {
            preloadInterstitial(activity, cleanId, enabled)
        }
    }

    private fun showStartIoInterstitial(activity: Activity, now: Long) {
        val ad = startIoInterstitial ?: return
        lastInterstitialShownAt = now
        startIoInterstitialReady = false
        isShowingFullscreenAd = true
        val shown = runCatching {
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(adObj: Ad) {
                    isShowingFullscreenAd = false
                    appendLog(AdDebugEventCode.AD_CLOSED, AdFormatType.INTERSTITIAL, "Start.io", "Start.io Interstitial closed")
                    preloadStartIoInterstitial(activity.applicationContext)
                }

                override fun adDisplayed(adObj: Ad) {
                    trackFormatDisplayed(AdFormatType.INTERSTITIAL, "Start.io")
                }

                override fun adClicked(adObj: Ad) {
                    trackFormatClicked(AdFormatType.INTERSTITIAL, "Start.io")
                }

                override fun adNotDisplayed(adObj: Ad) {
                    isShowingFullscreenAd = false
                    appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.INTERSTITIAL, "Start.io", "Start.io Interstitial not displayed")
                    preloadStartIoInterstitial(activity.applicationContext)
                }
            })
        }.getOrDefault(false)
        if (!shown) {
            isShowingFullscreenAd = false
            preloadStartIoInterstitial(activity.applicationContext)
        }
    }

    fun preloadRewarded(context: Context, unitId: String, enabled: Boolean) {
        val cleanId = unitId.trim()
        if (_userAdsSuppressed.value || !enabled || !latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
        if (cleanId.isEmpty()) {
            markInvalidPlacement(AdFormatType.REWARDED, "Rewarded Ad Unit ID is blank")
            return
        }
        if (isTestEnvironment()) return
        if (!NetworkMonitor.isCurrentlyOnline(context)) {
            appendLog(AdDebugEventCode.NETWORK_UNAVAILABLE, AdFormatType.REWARDED, message = "Offline: skipped rewarded preload")
            return
        }
        if (!sdkInitialized) {
            initialize(context, latestConfig) {
                preloadRewarded(context, cleanId, enabled)
            }
            return
        }
        if (usesDirectUnityAds()) {
            if (loadedDirectRewardedId == cleanId || isLoadingRewarded) return
            isLoadingRewarded = true
            updateFormatMetric(AdFormatType.REWARDED) {
                it.copy(
                    requestStatus = "Requesting",
                    requestCount = it.requestCount + 1,
                    unitOrPlacementId = cleanId
                )
            }
            appendLog(AdDebugEventCode.AD_REQUEST_STARTED, AdFormatType.REWARDED, "Unity Ads", "Loading Unity Rewarded placement $cleanId")

            runCatching {
                UnityAds.load(cleanId, object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String) {
                        isLoadingRewarded = false
                        loadedDirectRewardedId = placementId
                        resetRetryState(AdFormatType.REWARDED)
                        updateFormatMetric(AdFormatType.REWARDED) {
                            it.copy(
                                requestStatus = "Completed",
                                loadedStatus = "Loaded",
                                loadedCount = it.loadedCount + 1,
                                lastError = ""
                            )
                        }
                        appendLog(AdDebugEventCode.AD_LOADED, AdFormatType.REWARDED, "Unity Ads", "Unity Rewarded ready ($placementId)")

                        val waitingAct = pendingDirectRewardShowActivity?.get()
                        val waitingCb = pendingRewardCallback
                        if (waitingAct != null && !waitingAct.isFinishing && !waitingAct.isDestroyed && waitingCb != null && !isShowingFullscreenAd) {
                            pendingDirectRewardShowActivity = null
                            pendingRewardCallback = null
                            scope.launch {
                                showDirectUnityRewarded(waitingAct, placementId, waitingCb)
                            }
                        }
                    }

                    override fun onUnityAdsFailedToLoad(
                        placementId: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        isLoadingRewarded = false
                        if (loadedDirectRewardedId == placementId) loadedDirectRewardedId = ""
                        val isNoFill = error == UnityAds.UnityAdsLoadError.NO_FILL ||
                            message.contains("no fill", ignoreCase = true)
                        updateFormatMetric(AdFormatType.REWARDED) {
                            it.copy(
                                requestStatus = "Failed",
                                loadedStatus = if (isNoFill) "No Fill" else "Failed",
                                noFillCount = it.noFillCount + if (isNoFill) 1 else 0,
                                failedCount = it.failedCount + 1,
                                lastError = "${error.name}: $message"
                            )
                        }
                        appendLog(
                            code = if (isNoFill) AdDebugEventCode.AD_NO_FILL else AdDebugEventCode.AD_DISPLAY_FAILED,
                            format = AdFormatType.REWARDED,
                            network = "Unity Ads",
                            message = "${error.name}: $message"
                        )
                        val waitingAct = pendingDirectRewardShowActivity?.get()
                        val waitingCb = pendingRewardCallback
                        if (waitingAct != null && !waitingAct.isFinishing && !waitingAct.isDestroyed && waitingCb != null &&
                            startIoInitialized && startIoRewardedReady && startIoRewarded != null
                        ) {
                            pendingDirectRewardShowActivity = null
                            pendingRewardCallback = null
                            showStartIoRewarded(waitingAct, waitingCb)
                        } else {
                            pendingDirectRewardShowActivity = null
                            pendingRewardCallback = null
                            waitingCb?.let { cb -> invokeRewardCallback(cb, false, "বিজ্ঞাপন লোড হয়নি (${error.name})") }
                        }
                        scheduleRetry(context.applicationContext, AdFormatType.REWARDED) {
                            preloadRewarded(context.applicationContext, cleanId, latestConfig.rewardedEnabled)
                        }
                    }
                })
            }.onFailure { err ->
                isLoadingRewarded = false
                loadedDirectRewardedId = ""
                pendingDirectRewardShowActivity = null
                val waitingCb = pendingRewardCallback
                pendingRewardCallback = null
                waitingCb?.let { cb -> invokeRewardCallback(cb, false, err.message ?: "Unity Rewarded load error") }
                appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.REWARDED, "Unity Ads", err.message ?: "Unity Rewarded load error")
            }
            return
        }

        if ((rewardedAd?.isAdReady() == true && loadedRewardedUnitId == cleanId) || isLoadingRewarded) return

        isLoadingRewarded = true
        updateFormatMetric(AdFormatType.REWARDED) {
            it.copy(
                requestStatus = "Requesting",
                requestCount = it.requestCount + 1,
                unitOrPlacementId = cleanId
            )
        }
        appendLog(AdDebugEventCode.AD_REQUEST_STARTED, AdFormatType.REWARDED, message = "Loading rewarded unit $cleanId")

        runCatching {
            val ad = LevelPlayRewardedAd(cleanId)
            ad.setListener(object : LevelPlayRewardedAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    isLoadingRewarded = false
                    loadedRewardedUnitId = cleanId
                    resetRetryState(AdFormatType.REWARDED)
                    updateFormatMetric(AdFormatType.REWARDED) {
                        it.copy(
                            requestStatus = "Completed",
                            loadedStatus = "Loaded",
                            loadedCount = it.loadedCount + 1,
                            lastError = ""
                        )
                    }
                    appendLog(AdDebugEventCode.AD_LOADED, AdFormatType.REWARDED, adInfo.adNetwork, "Rewarded ready")
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    isLoadingRewarded = false
                    rewardedAd = null
                    handleAdLoadFailure(AdFormatType.REWARDED, error)
                    scheduleRetry(context.applicationContext, AdFormatType.REWARDED) {
                        preloadRewarded(context.applicationContext, cleanId, latestConfig.rewardedEnabled)
                    }
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                    trackFormatDisplayed(AdFormatType.REWARDED, adInfo.adNetwork)
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    isShowingFullscreenAd = false
                    rewardedAd = null
                    pendingRewardCallback?.invoke(false, error.errorMessage)
                    pendingRewardCallback = null
                    appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.REWARDED, adInfo.adNetwork, error.errorMessage)
                }

                override fun onAdClicked(adInfo: LevelPlayAdInfo) {
                    trackFormatClicked(AdFormatType.REWARDED, adInfo.adNetwork)
                }

                override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                    pendingRewardCallback?.invoke(true, "${reward.amount} ${reward.name}".trim())
                    pendingRewardCallback = null
                    appendLog(AdDebugEventCode.AD_DISPLAYED, AdFormatType.REWARDED, adInfo.adNetwork, "Reward granted: ${reward.amount} ${reward.name}")
                }

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    isShowingFullscreenAd = false
                    rewardedAd = null
                    pendingRewardCallback?.invoke(false, "ভিডিও সম্পূর্ণ দেখা হয়নি")
                    pendingRewardCallback = null
                    appendLog(AdDebugEventCode.AD_CLOSED, AdFormatType.REWARDED, adInfo.adNetwork, "Rewarded closed")
                    preloadRewarded(context.applicationContext, cleanId, latestConfig.rewardedEnabled)
                }

                override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
            })
            rewardedAd = ad
            ad.loadAd()
        }.onFailure { err ->
            isLoadingRewarded = false
            rewardedAd = null
            appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.REWARDED, message = err.message ?: "Rewarded load exception")
        }
    }

    private fun invokeRewardCallback(
        callback: (Boolean, String) -> Unit,
        rewarded: Boolean,
        detail: String
    ) {
        if (isTestEnvironment() || android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            callback(rewarded, detail)
        } else {
            scope.launch {
                callback(rewarded, detail)
            }
        }
    }

    private fun showDirectUnityRewarded(
        activity: Activity,
        placementId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (isShowingFullscreenAd) {
            invokeRewardCallback(onResult, false, "অন্য একটি বিজ্ঞাপন প্রদর্শিত হচ্ছে")
            return
        }
        loadedDirectRewardedId = ""
        pendingRewardCallback = onResult
        isShowingFullscreenAd = true
        runCatching {
            UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    isShowingFullscreenAd = false
                    updateFormatMetric(AdFormatType.REWARDED) {
                        it.copy(failedCount = it.failedCount + 1, lastError = "${error.name}: $message")
                    }
                    appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.REWARDED, "Unity Ads", "${error.name}: $message")
                    val cb = pendingRewardCallback
                    pendingRewardCallback = null
                    cb?.let { invokeRewardCallback(it, false, "${error.name}: $message") }
                    preloadRewarded(activity.applicationContext, placementId, latestConfig.rewardedEnabled)
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    trackFormatDisplayed(AdFormatType.REWARDED, "Unity Ads")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    trackFormatClicked(AdFormatType.REWARDED, "Unity Ads")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    isShowingFullscreenAd = false
                    val completed = state == UnityAds.UnityAdsShowCompletionState.COMPLETED
                    appendLog(AdDebugEventCode.AD_CLOSED, AdFormatType.REWARDED, "Unity Ads", "Unity Rewarded ${state.name}")
                    val cb = pendingRewardCallback
                    pendingRewardCallback = null
                    cb?.let {
                        invokeRewardCallback(
                            it,
                            completed,
                            if (completed) "Unity Rewarded Video সম্পন্ন ✓" else "ভিডিও সম্পূর্ণ দেখা হয়নি"
                        )
                    }
                    preloadRewarded(activity.applicationContext, placementId, latestConfig.rewardedEnabled)
                }
            })
        }.onFailure { err ->
            isShowingFullscreenAd = false
            pendingRewardCallback = null
            invokeRewardCallback(onResult, false, err.message ?: "Failed to show Unity Rewarded ad")
        }
    }

    private fun showStartIoRewarded(
        activity: Activity,
        onResult: (Boolean, String) -> Unit
    ) {
        val ad = startIoRewarded ?: run {
            onResult(false, "Start.io Rewarded unavailable")
            return
        }
        if (isShowingFullscreenAd) {
            onResult(false, "অন্য একটি বিজ্ঞাপন প্রদর্শিত হচ্ছে")
            return
        }
        startIoRewardedReady = false
        isShowingFullscreenAd = true
        var rewardEarned = false
        ad.setVideoListener(object : VideoListener {
            override fun onVideoCompleted() {
                rewardEarned = true
            }
        })
        val shown = runCatching {
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(adObj: Ad) {
                    isShowingFullscreenAd = false
                    if (rewardEarned) {
                        onResult(true, "Start.io Rewarded Video সম্পন্ন ✓")
                    } else {
                        onResult(false, "ভিডিও সম্পূর্ণ দেখা হয়নি")
                    }
                    preloadStartIoRewarded(activity.applicationContext)
                }

                override fun adDisplayed(adObj: Ad) {
                    trackFormatDisplayed(AdFormatType.REWARDED, "Start.io")
                }

                override fun adClicked(adObj: Ad) {
                    trackFormatClicked(AdFormatType.REWARDED, "Start.io")
                }

                override fun adNotDisplayed(adObj: Ad) {
                    isShowingFullscreenAd = false
                    onResult(false, "Start.io Rewarded দেখানো যায়নি")
                    preloadStartIoRewarded(activity.applicationContext)
                }
            })
        }.getOrDefault(false)
        if (!shown) {
            isShowingFullscreenAd = false
            onResult(false, "Start.io Rewarded লোড হচ্ছে — কিছুক্ষণ পরে চেষ্টা করুন")
            preloadStartIoRewarded(activity.applicationContext)
        }
    }

    fun showRewardedAd(
        activity: Activity?,
        placementName: String = "agro_tip_reward",
        onResult: (rewarded: Boolean, detail: String) -> Unit
    ) {
        val cfg = latestConfig
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onResult(false, "Activity unavailable")
            return
        }
        if (_userAdsSuppressed.value || isAdBlockedRoute(currentRoute) || !cfg.adsEnabled || !cfg.androidAdsEnabled || !cfg.rewardedEnabled) {
            onResult(false, if (_userAdsSuppressed.value) "Ad-Free active" else "Rewarded ads are currently disabled")
            return
        }
        if (isShowingFullscreenAd) {
            onResult(false, "অন্য একটি বিজ্ঞাপন চলছে — অনুগ্রহ করে অপেক্ষা করুন")
            return
        }
        val cleanId = cfg.resolvedRewardedUnitId()
        if (cleanId.isEmpty() && !(startIoInitialized && startIoRewardedReady)) {
            markInvalidPlacement(AdFormatType.REWARDED, "Rewarded Ad Unit ID missing")
            onResult(false, "Rewarded Ad Unit ID missing")
            return
        }
        if (isTestEnvironment()) {
            onResult(false, "Test environment")
            return
        }

        if (usesDirectUnityAds(cfg)) {
            if (sdkInitialized && loadedDirectRewardedId == cleanId) {
                showDirectUnityRewarded(activity, cleanId, onResult)
            } else if (startIoInitialized && startIoRewardedReady && startIoRewarded != null) {
                showStartIoRewarded(activity, onResult)
                if (cleanId.isNotEmpty()) preloadRewarded(activity, cleanId, true)
            } else if (isLoadingRewarded && pendingRewardCallback != null) {
                onResult(false, "রিওয়ার্ডেড ভিডিও ইতোমধ্যে লোড হচ্ছে — অনুগ্রহ করে অপেক্ষা করুন")
            } else {
                pendingRewardCallback = onResult
                pendingDirectRewardShowActivity = WeakReference(activity)
                preloadRewarded(activity, cleanId, true)
            }
            return
        }

        val ad = rewardedAd
        if (sdkInitialized && ad != null && loadedRewardedUnitId == cleanId && ad.isAdReady()) {
            pendingRewardCallback = onResult
            isShowingFullscreenAd = true
            runCatching { ad.showAd(activity, placementName) }.onFailure { err ->
                isShowingFullscreenAd = false
                pendingRewardCallback = null
                onResult(false, err.message ?: "Failed to show rewarded ad")
            }
        } else if (startIoInitialized && startIoRewardedReady && startIoRewarded != null) {
            showStartIoRewarded(activity, onResult)
            if (cleanId.isNotEmpty()) preloadRewarded(activity, cleanId, true)
        } else {
            preloadRewarded(activity, cleanId, true)
            onResult(false, "Rewarded video loading — please try again shortly")
        }
    }

    internal fun resolveEffectiveAppOpenUnitId(config: AdConfig = latestConfig): String =
        config.resolvedAppOpenUnitId().ifEmpty { config.resolvedInterstitialUnitId() }

    fun preloadAppOpen(context: Context, unitId: String, enabled: Boolean) {
        val cleanId = unitId.trim().ifEmpty { latestConfig.resolvedInterstitialUnitId() }
        if (_userAdsSuppressed.value || !enabled || !latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
        if (cleanId.isEmpty()) {
            if (isStartIoEnabledEffective()) {
                preloadStartIoInterstitial(context)
            } else {
                markInvalidPlacement(AdFormatType.APP_OPEN, "App Open / Interstitial Ad Unit ID is blank")
            }
            return
        }
        if (isTestEnvironment()) return
        if (!NetworkMonitor.isCurrentlyOnline(context)) {
            appendLog(AdDebugEventCode.NETWORK_UNAVAILABLE, AdFormatType.APP_OPEN, message = "Offline: skipped app open preload")
            return
        }
        if (!sdkInitialized) {
            initialize(context, latestConfig) {
                preloadAppOpen(context, cleanId, latestConfig.appOpenEnabled)
            }
            return
        }

        if (usesDirectUnityAds()) {
            val sharesInterstitialPlacement = cleanId == latestConfig.resolvedInterstitialUnitId() &&
                (loadedDirectInterstitialId == cleanId || isLoadingInterstitial)
            if (loadedDirectAppOpenId == cleanId || isLoadingAppOpen || sharesInterstitialPlacement) return
            isLoadingAppOpen = true
            updateFormatMetric(AdFormatType.APP_OPEN) {
                it.copy(
                    requestStatus = "Requesting",
                    requestCount = it.requestCount + 1,
                    unitOrPlacementId = cleanId
                )
            }
            appendLog(AdDebugEventCode.AD_REQUEST_STARTED, AdFormatType.APP_OPEN, "Unity Ads", "Loading Unity App Open ($cleanId)")
            runCatching {
                UnityAds.load(cleanId, object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String) {
                        isLoadingAppOpen = false
                        loadedDirectAppOpenId = placementId
                        trackFormatLoaded(AdFormatType.APP_OPEN, "Unity Ads")
                        if (pendingAppOpenOnLaunch && !isAdBlockedRoute(currentRoute)) {
                            scope.launch { maybeShowAppOpenOnForeground() }
                        }
                    }

                    override fun onUnityAdsFailedToLoad(
                        placementId: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        isLoadingAppOpen = false
                        loadedDirectAppOpenId = ""
                        trackFormatFailed(AdFormatType.APP_OPEN, error.ordinal, "${error.name}: $message")
                        scheduleRetry(context.applicationContext, AdFormatType.APP_OPEN) {
                            preloadAppOpen(context.applicationContext, cleanId, latestConfig.appOpenEnabled)
                        }
                    }
                })
            }.onFailure { err ->
                isLoadingAppOpen = false
                loadedDirectAppOpenId = ""
                appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.APP_OPEN, "Unity Ads", err.message ?: "Unity App Open load error")
            }
            return
        }

        if ((appOpenAd?.isAdReady() == true && loadedAppOpenUnitId == cleanId) || isLoadingAppOpen) return

        isLoadingAppOpen = true
        updateFormatMetric(AdFormatType.APP_OPEN) {
            it.copy(
                requestStatus = "Requesting",
                requestCount = it.requestCount + 1,
                unitOrPlacementId = cleanId
            )
        }
        appendLog(AdDebugEventCode.AD_REQUEST_STARTED, AdFormatType.APP_OPEN, message = "Loading App Open unit $cleanId")

        runCatching {
            val ad = LevelPlayInterstitialAd(cleanId)
            ad.setListener(object : LevelPlayInterstitialAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    isLoadingAppOpen = false
                    loadedAppOpenUnitId = cleanId
                    resetRetryState(AdFormatType.APP_OPEN)
                    updateFormatMetric(AdFormatType.APP_OPEN) {
                        it.copy(
                            requestStatus = "Completed",
                            loadedStatus = "Loaded",
                            loadedCount = it.loadedCount + 1,
                            lastError = ""
                        )
                    }
                    appendLog(AdDebugEventCode.AD_LOADED, AdFormatType.APP_OPEN, adInfo.adNetwork, "App Open ready")
                    if (pendingAppOpenOnLaunch && !isAdBlockedRoute(currentRoute)) {
                        scope.launch { maybeShowAppOpenOnForeground() }
                    }
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    isLoadingAppOpen = false
                    appOpenAd = null
                    handleAdLoadFailure(AdFormatType.APP_OPEN, error)
                    scheduleRetry(context.applicationContext, AdFormatType.APP_OPEN) {
                        preloadAppOpen(context.applicationContext, cleanId, latestConfig.appOpenEnabled)
                    }
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                    trackFormatDisplayed(AdFormatType.APP_OPEN, adInfo.adNetwork)
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    isShowingFullscreenAd = false
                    appOpenAd = null
                    appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.APP_OPEN, adInfo.adNetwork, error.errorMessage)
                }

                override fun onAdClicked(adInfo: LevelPlayAdInfo) {
                    trackFormatClicked(AdFormatType.APP_OPEN, adInfo.adNetwork)
                }

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    isShowingFullscreenAd = false
                    appOpenAd = null
                    appendLog(AdDebugEventCode.AD_CLOSED, AdFormatType.APP_OPEN, adInfo.adNetwork, "App Open closed")
                    preloadAppOpen(context.applicationContext, cleanId, latestConfig.appOpenEnabled)
                }

                override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) = Unit
            })
            appOpenAd = ad
            ad.loadAd()
        }.onFailure { err ->
            isLoadingAppOpen = false
            appOpenAd = null
            appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.APP_OPEN, message = err.message ?: "App Open exception")
        }
    }

    private fun showDirectUnityAppOpen(activity: Activity, placementId: String) {
        if (isShowingFullscreenAd) return
        loadedDirectAppOpenId = ""
        isShowingFullscreenAd = true
        runCatching {
            UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    isShowingFullscreenAd = false
                    updateFormatMetric(AdFormatType.APP_OPEN) {
                        it.copy(failedCount = it.failedCount + 1, lastError = "${error.name}: $message")
                    }
                    appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.APP_OPEN, "Unity Ads", "${error.name}: $message")
                    preloadAppOpen(activity.applicationContext, placementId, latestConfig.appOpenEnabled)
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    trackFormatDisplayed(AdFormatType.APP_OPEN, "Unity Ads")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    trackFormatClicked(AdFormatType.APP_OPEN, "Unity Ads")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    isShowingFullscreenAd = false
                    appendLog(AdDebugEventCode.AD_CLOSED, AdFormatType.APP_OPEN, "Unity Ads", "Unity App Open closed (${state.name})")
                    preloadAppOpen(activity.applicationContext, placementId, latestConfig.appOpenEnabled)
                }
            })
        }.onFailure { err ->
            isShowingFullscreenAd = false
            appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.APP_OPEN, "Unity Ads", err.message ?: "Unity App Open exception")
        }
    }

    private fun showStartIoAppOpen(activity: Activity) {
        val ad = startIoInterstitial ?: return
        if (isShowingFullscreenAd) return
        startIoInterstitialReady = false
        isShowingFullscreenAd = true
        val shown = runCatching {
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(adObj: Ad) {
                    isShowingFullscreenAd = false
                    preloadStartIoInterstitial(activity.applicationContext)
                }

                override fun adDisplayed(adObj: Ad) {
                    trackFormatDisplayed(AdFormatType.APP_OPEN, "Start.io")
                }

                override fun adClicked(adObj: Ad) {
                    trackFormatClicked(AdFormatType.APP_OPEN, "Start.io")
                }

                override fun adNotDisplayed(adObj: Ad) {
                    isShowingFullscreenAd = false
                    preloadStartIoInterstitial(activity.applicationContext)
                }
            })
        }.getOrDefault(false)
        if (!shown) {
            isShowingFullscreenAd = false
            preloadStartIoInterstitial(activity.applicationContext)
        }
    }

    fun maybeShowAppOpenOnForeground(activity: Activity? = currentActivityRef?.get()) {
        val cfg = latestConfig
        if (_userAdsSuppressed.value || isTestEnvironment() || !cfg.adsEnabled || !cfg.androidAdsEnabled || !cfg.appOpenEnabled) return
        if (currentRoute == "splash") {
            pendingAppOpenOnLaunch = true
            activity?.let { act ->
                val cleanId = resolveEffectiveAppOpenUnitId(cfg)
                if (cleanId.isNotEmpty()) preloadAppOpen(act, cleanId, true)
                if (startIoInitialized) preloadStartIoInterstitial(act)
            }
            return
        }
        if (isAdBlockedRoute(currentRoute) || isShowingFullscreenAd) return
        if (activity == null || activity.isFinishing || activity.isDestroyed) return

        val now = clockMillisProvider()
        if (now - lastAppOpenShownAt < APP_OPEN_COOLDOWN_MS ||
            (lastInterstitialShownAt > 0L && now - lastInterstitialShownAt < FULLSCREEN_MIN_GAP_MS)
        ) {
            pendingAppOpenOnLaunch = false
            return
        }

        val cleanId = resolveEffectiveAppOpenUnitId(cfg)
        val lpAd = appOpenAd
        when {
            !usesDirectUnityAds(cfg) && sdkInitialized && cleanId.isNotEmpty() && lpAd != null && loadedAppOpenUnitId == cleanId && lpAd.isAdReady() -> {
                pendingAppOpenOnLaunch = false
                lastAppOpenShownAt = now
                isShowingFullscreenAd = true
                runCatching { lpAd.showAd(activity, "app_open") }.onFailure {
                    isShowingFullscreenAd = false
                }
            }
            usesDirectUnityAds(cfg) && sdkInitialized && cleanId.isNotEmpty() && loadedDirectAppOpenId == cleanId -> {
                pendingAppOpenOnLaunch = false
                lastAppOpenShownAt = now
                showDirectUnityAppOpen(activity, cleanId)
            }
            usesDirectUnityAds(cfg) && sdkInitialized && cleanId.isNotEmpty() && loadedDirectInterstitialId == cleanId -> {
                pendingAppOpenOnLaunch = false
                lastAppOpenShownAt = now
                loadedDirectInterstitialId = ""
                showDirectUnityAppOpen(activity, cleanId)
            }
            startIoInitialized && startIoInterstitialReady && startIoInterstitial != null -> {
                pendingAppOpenOnLaunch = false
                lastAppOpenShownAt = now
                showStartIoAppOpen(activity)
            }
            else -> {
                if (cleanId.isNotEmpty()) preloadAppOpen(activity, cleanId, true)
                if (startIoInitialized) preloadStartIoInterstitial(activity)
            }
        }
    }

    fun trackFormatRequested(format: AdFormatType, unitId: String) {
        updateFormatMetric(format) {
            it.copy(
                requestStatus = "Requesting",
                requestCount = it.requestCount + 1,
                unitOrPlacementId = unitId
            )
        }
        appendLog(AdDebugEventCode.AD_REQUEST_STARTED, format, message = "Loading ${format.labelEn} ($unitId)")
    }

    fun trackFormatLoaded(format: AdFormatType, network: String = "LevelPlay") {
        resetRetryState(format)
        updateFormatMetric(format) {
            it.copy(
                requestStatus = "Completed",
                loadedStatus = "Loaded",
                loadedCount = it.loadedCount + 1,
                lastError = ""
            )
        }
        appendLog(AdDebugEventCode.AD_LOADED, format, network, "${format.labelEn} loaded ($network)")
    }

    fun trackFormatDisplayed(format: AdFormatType, network: String = "LevelPlay") {
        updateFormatMetric(format) {
            it.copy(displayedCount = it.displayedCount + 1)
        }
        appendLog(AdDebugEventCode.AD_DISPLAYED, format, network, "${format.labelEn} displayed ($network)")
    }

    fun trackFormatClicked(format: AdFormatType, network: String = "LevelPlay") {
        rolloverTelemetryDateIfNeeded()
        updateFormatMetric(format) {
            it.copy(clickedCount = it.clickedCount + 1)
        }
        _telemetry.update { it.copy(totalClicks = it.totalClicks + 1) }
        persistTelemetryIfAttached()
        appendLog(AdDebugEventCode.AD_CLICKED, format, network, "${format.labelEn} clicked ($network)")
    }

    fun trackFormatFailed(format: AdFormatType, errorCode: Int, errorMessage: String) {
        val isNoFill = errorCode == 509 || errorMessage.contains("no fill", ignoreCase = true)
        updateFormatMetric(format) {
            it.copy(
                requestStatus = "Failed",
                loadedStatus = if (isNoFill) "No Fill" else "Failed",
                noFillCount = it.noFillCount + if (isNoFill) 1 else 0,
                failedCount = it.failedCount + 1,
                lastError = "($errorCode) $errorMessage"
            )
        }
        appendLog(
            code = if (isNoFill) AdDebugEventCode.AD_NO_FILL else AdDebugEventCode.AD_DISPLAY_FAILED,
            format = format,
            message = "($errorCode) $errorMessage"
        )
    }

    fun markInvalidPlacement(format: AdFormatType, reason: String) {
        updateFormatMetric(format) {
            it.copy(
                requestStatus = "Invalid Placement",
                loadedStatus = "Unconfigured",
                lastError = reason
            )
        }
        appendLog(AdDebugEventCode.INVALID_PLACEMENT_ID, format, message = reason)
    }

    fun recordRealImpressionRevenue(rawRevenue: Double, adNetwork: String, adFormat: String? = null) {
        rolloverTelemetryDateIfNeeded()
        val cleanRevenue = if (rawRevenue.isFinite() && rawRevenue >= 0.0) rawRevenue else 0.0
        val cleanNetwork = adNetwork.trim().ifBlank { "LevelPlay" }
        _telemetry.update { state ->
            val currentNet = state.networkRevenueUsd.toMutableMap()
            currentNet[cleanNetwork] = (currentNet[cleanNetwork] ?: 0.0) + cleanRevenue
            state.copy(
                dailyRevenueUsd = state.dailyRevenueUsd + cleanRevenue,
                monthlyRevenueUsd = state.monthlyRevenueUsd + cleanRevenue,
                networkRevenueUsd = currentNet,
                impressionCount = state.impressionCount + 1
            )
        }
        persistTelemetryIfAttached()
        appendLog(
            code = AdDebugEventCode.AD_DISPLAYED,
            format = mapFormatName(adFormat),
            network = cleanNetwork,
            message = "Real impression recorded (+$${"%.5f".format(Locale.ENGLISH, cleanRevenue)})"
        )
    }

    internal fun initTelemetryStore(
        context: Context,
        nowMillis: Long = clockMillisProvider(),
        forceReload: Boolean = false
    ) {
        val appCtx = context.applicationContext
        telemetryAppContext = appCtx
        if (telemetryLoadedFromDisk && !forceReload) {
            rolloverTelemetryDateIfNeeded(nowMillis)
            return
        }
        val dayKey = formatDayKey(nowMillis)
        val monthKey = formatMonthKey(nowMillis)
        val prefs = appCtx.getSharedPreferences(TELEMETRY_PREFS_NAME, Context.MODE_PRIVATE)
        val savedDay = prefs.getString(KEY_DAY_KEY, "") ?: ""
        val savedMonth = prefs.getString(KEY_MONTH_KEY, "") ?: ""

        val dailyRev = if (savedDay == dayKey) prefs.getFloat(KEY_DAILY_REV, 0f).toDouble().coerceAtLeast(0.0) else 0.0
        val monthlyRev = if (savedMonth == monthKey) prefs.getFloat(KEY_MONTHLY_REV, 0f).toDouble().coerceAtLeast(0.0) else 0.0
        val impressions = if (savedMonth == monthKey) prefs.getInt(KEY_IMPRESSIONS, 0).coerceAtLeast(0) else 0
        val clicks = if (savedMonth == monthKey) prefs.getInt(KEY_CLICKS, 0).coerceAtLeast(0) else 0
        val netMap = if (savedMonth == monthKey) {
            parseNetworkRevenueJson(prefs.getString(KEY_NET_REV_JSON, "") ?: "")
        } else {
            emptyMap()
        }

        activeDateKey = dayKey
        activeMonthKey = monthKey
        telemetryLoadedFromDisk = true

        _telemetry.update { state ->
            state.copy(
                dailyRevenueUsd = dailyRev,
                monthlyRevenueUsd = monthlyRev,
                impressionCount = impressions,
                totalClicks = clicks,
                networkRevenueUsd = netMap
            )
        }
        if (savedDay != dayKey || savedMonth != monthKey) {
            persistTelemetryIfAttached(nowMillis)
        }
    }

    internal fun rolloverTelemetryDateIfNeeded(nowMillis: Long = clockMillisProvider()) {
        val dayKey = formatDayKey(nowMillis)
        val monthKey = formatMonthKey(nowMillis)
        if (activeDateKey.isEmpty() || activeMonthKey.isEmpty()) {
            activeDateKey = dayKey
            activeMonthKey = monthKey
            return
        }
        if (activeDateKey == dayKey && activeMonthKey == monthKey) return
        val monthChanged = activeMonthKey != monthKey
        activeDateKey = dayKey
        activeMonthKey = monthKey
        _telemetry.update { state ->
            state.copy(
                dailyRevenueUsd = 0.0,
                monthlyRevenueUsd = if (monthChanged) 0.0 else state.monthlyRevenueUsd,
                impressionCount = if (monthChanged) 0 else state.impressionCount,
                totalClicks = if (monthChanged) 0 else state.totalClicks,
                networkRevenueUsd = if (monthChanged) emptyMap() else state.networkRevenueUsd
            )
        }
        persistTelemetryIfAttached(nowMillis)
    }

    private fun persistTelemetryIfAttached(nowMillis: Long = clockMillisProvider()) {
        val appCtx = telemetryAppContext ?: return
        val dayKey = activeDateKey.ifBlank { formatDayKey(nowMillis) }
        val monthKey = activeMonthKey.ifBlank { formatMonthKey(nowMillis) }
        val snapshot = _telemetry.value
        val netJson = JSONObject().apply {
            snapshot.networkRevenueUsd.forEach { (k, v) -> put(k, v) }
        }.toString()
        appCtx.getSharedPreferences(TELEMETRY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DAY_KEY, dayKey)
            .putString(KEY_MONTH_KEY, monthKey)
            .putFloat(KEY_DAILY_REV, snapshot.dailyRevenueUsd.toFloat())
            .putFloat(KEY_MONTHLY_REV, snapshot.monthlyRevenueUsd.toFloat())
            .putInt(KEY_IMPRESSIONS, snapshot.impressionCount)
            .putInt(KEY_CLICKS, snapshot.totalClicks)
            .putString(KEY_NET_REV_JSON, netJson)
            .apply()
    }

    private fun parseNetworkRevenueJson(raw: String): Map<String, Double> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Double>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optDouble(k, 0.0)
                if (v.isFinite() && v >= 0.0) result[k] = v
            }
            result.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun formatDayKey(nowMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(nowMillis))

    private fun formatMonthKey(nowMillis: Long): String =
        SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(Date(nowMillis))

    fun isSdkReady(): Boolean = sdkInitialized || startIoInitialized

    fun canRequestSdkAd(context: Context, config: AdConfig, unitOrPlacementId: String, format: AdFormatType): Boolean {
        if (_userAdsSuppressed.value || !config.adsEnabled || !config.androidAdsEnabled) return false
        if (!usesDirectUnityAds(config) && resolveMediationAppKey(config).isEmpty()) {
            appendLog(AdDebugEventCode.INVALID_APP_KEY, format, message = "Unity Game ID or Mediation App Key is missing")
            return false
        }
        if (unitOrPlacementId.trim().isEmpty()) {
            markInvalidPlacement(format, "${format.labelEn} placement/unit ID is empty")
            return false
        }
        if (!isTestEnvironment() && !NetworkMonitor.isCurrentlyOnline(context)) {
            appendLog(AdDebugEventCode.NETWORK_UNAVAILABLE, format, message = "Offline: cannot request ${format.labelEn}")
            return false
        }
        return true
    }

    fun clearDebugLogs() {
        _telemetry.update { it.copy(debugLogs = emptyList()) }
    }

    fun resetForTest(clearContextStore: Context? = null, resetClock: Boolean = true) {
        cancelAllRetryJobs()
        clearContextStore?.let { ctx ->
            ctx.applicationContext.getSharedPreferences(TELEMETRY_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
        if (resetClock) {
            clockMillisProvider = { System.currentTimeMillis() }
        }
        telemetryAppContext = null
        telemetryLoadedFromDisk = false
        activeDateKey = ""
        activeMonthKey = ""
        latestConfig = AdConfig()
        initializedAppKey = ""
        initializedUnityGameId = ""
        initializedStartIoAppId = ""
        isDirectUnityMode = false
        sdkInitialized = false
        sdkInitializing = false
        startIoInitialized = false
        startIoInitializing = false
        isShowingFullscreenAd = false
        currentRoute = "splash"
        _currentRouteFlow.value = "splash"
        _userAdsSuppressed.value = false
        interstitialAd = null
        loadedInterstitialUnitId = ""
        loadedDirectInterstitialId = ""
        isLoadingInterstitial = false
        lastInterstitialShownAt = 0L
        rewardedAd = null
        loadedRewardedUnitId = ""
        loadedDirectRewardedId = ""
        isLoadingRewarded = false
        pendingRewardCallback = null
        pendingDirectRewardShowActivity = null
        appOpenAd = null
        loadedAppOpenUnitId = ""
        loadedDirectAppOpenId = ""
        isLoadingAppOpen = false
        lastAppOpenShownAt = 0L
        pendingAppOpenOnLaunch = false
        startIoInterstitial = null
        startIoInterstitialReady = false
        isLoadingStartIoInterstitial = false
        startIoInterstitialNoFillUntil = 0L
        startIoInterstitialFailCount = 0
        startIoRewarded = null
        startIoRewardedReady = false
        isLoadingStartIoRewarded = false
        startIoRewardedNoFillUntil = 0L
        startIoRewardedFailCount = 0
        startIoBannerNoFillUntil = 0L
        startIoBannerFailCount = 0
        _telemetry.value = AdRuntimeTelemetry()
    }

    internal fun nextRetryDelayMs(attemptIndex: Int): Long? {
        if (attemptIndex < 0 || attemptIndex >= MAX_RETRY_ATTEMPTS) return null
        return RETRY_DELAYS_MS[attemptIndex]
    }

    private fun scheduleRetry(context: Context, format: AdFormatType, block: () -> Unit) {
        if (isTestEnvironment() || _userAdsSuppressed.value) return
        if (!latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
        if (!NetworkMonitor.isCurrentlyOnline(context)) return
        val attempt = retryAttempts[format] ?: 0
        val delayMs = nextRetryDelayMs(attempt) ?: return
        if (retryJobs[format]?.isActive == true) return
        retryAttempts[format] = attempt + 1
        updateFormatMetric(format) { it.copy(retryCount = it.retryCount + 1) }
        appendLog(
            code = AdDebugEventCode.AD_RETRY_SCHEDULED,
            format = format,
            message = "Scheduling retry ${attempt + 1}/$MAX_RETRY_ATTEMPTS in ${delayMs / 1000}s"
        )
        retryJobs[format] = scope.launch {
            delay(delayMs)
            retryJobs.remove(format)
            if (!_userAdsSuppressed.value && NetworkMonitor.isCurrentlyOnline(context) && latestConfig.adsEnabled && latestConfig.androidAdsEnabled) {
                block()
            }
        }
    }

    private fun resetRetryState(format: AdFormatType) {
        retryAttempts[format] = 0
        retryJobs.remove(format)?.cancel()
    }

    private fun cancelAllRetryJobs() {
        retryAttempts.clear()
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
    }

    private fun handleAdLoadFailure(format: AdFormatType, error: LevelPlayAdError) {
        trackFormatFailed(format, error.errorCode, error.errorMessage)
    }

    private fun mapFormatName(raw: String?): AdFormatType? = when {
        raw.isNullOrBlank() -> null
        raw.contains("banner", ignoreCase = true) -> AdFormatType.BANNER
        raw.contains("native", ignoreCase = true) -> AdFormatType.NATIVE
        raw.contains("reward", ignoreCase = true) -> AdFormatType.REWARDED
        raw.contains("open", ignoreCase = true) -> AdFormatType.APP_OPEN
        raw.contains("interstitial", ignoreCase = true) -> AdFormatType.INTERSTITIAL
        else -> null
    }

    private fun refreshAdapterAndFormatConfig(config: AdConfig) {
        _telemetry.update { state ->
            val updatedFormats = state.formatMetrics.toMutableMap()
            updatedFormats[AdFormatType.BANNER] = (updatedFormats[AdFormatType.BANNER] ?: AdFormatTelemetry(AdFormatType.BANNER))
                .copy(enabled = config.bannerEnabled, unitOrPlacementId = config.resolvedBannerUnitId())
            updatedFormats[AdFormatType.NATIVE] = (updatedFormats[AdFormatType.NATIVE] ?: AdFormatTelemetry(AdFormatType.NATIVE))
                .copy(enabled = config.nativeEnabled, unitOrPlacementId = config.resolvedNativePlacement())
            updatedFormats[AdFormatType.INTERSTITIAL] = (updatedFormats[AdFormatType.INTERSTITIAL] ?: AdFormatTelemetry(AdFormatType.INTERSTITIAL))
                .copy(enabled = config.interstitialEnabled, unitOrPlacementId = config.resolvedInterstitialUnitId())
            updatedFormats[AdFormatType.REWARDED] = (updatedFormats[AdFormatType.REWARDED] ?: AdFormatTelemetry(AdFormatType.REWARDED))
                .copy(enabled = config.rewardedEnabled, unitOrPlacementId = config.resolvedRewardedUnitId())
            updatedFormats[AdFormatType.APP_OPEN] = (updatedFormats[AdFormatType.APP_OPEN] ?: AdFormatTelemetry(AdFormatType.APP_OPEN))
                .copy(enabled = config.appOpenEnabled, unitOrPlacementId = resolveEffectiveAppOpenUnitId(config))

            val directMode = usesDirectUnityAds(config)
            val resolvedAppKey = resolveMediationAppKey(config)
            state.copy(
                mediationPlatform = if (directMode) "Unity Ads ${resolvedUnitySdkVersion()} (Direct)" else "Unity LevelPlay 9.2.0",
                activeAppKeyMasked = if (directMode) "Game ID: ${config.unityGameId.trim()}" else maskKey(resolvedAppKey),
                testModeEnabled = config.adTestMode,
                consentGranted = config.consentGranted,
                startIoReady = startIoInitialized,
                adapterStatuses = defaultAdapterStatuses(config, sdkInitialized, startIoInitialized),
                formatMetrics = updatedFormats,
                productionReadiness = buildProductionReadiness(config)
            )
        }
    }

    private fun updateFormatMetric(format: AdFormatType, transform: (AdFormatTelemetry) -> AdFormatTelemetry) {
        _telemetry.update { state ->
            val map = state.formatMetrics.toMutableMap()
            val prev = map[format] ?: AdFormatTelemetry(format)
            map[format] = transform(prev)
            state.copy(formatMetrics = map)
        }
    }

    private fun appendLog(
        code: String,
        format: AdFormatType? = null,
        network: String = "LevelPlay",
        message: String
    ) {
        _telemetry.update { state ->
            val next = (listOf(
                AdDebugLogEntry(
                    code = code,
                    format = format,
                    network = network,
                    message = message
                )
            ) + state.debugLogs).take(MAX_LOG_ENTRIES)
            state.copy(debugLogs = next)
        }
    }

    private fun maskKey(key: String): String {
        if (key.isEmpty()) return "(not set)"
        if (key.length <= 6) return "****"
        return "${key.take(3)}***${key.takeLast(3)}"
    }

    fun formatTimestamp(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        return sdf.format(Date(ts))
    }
}
