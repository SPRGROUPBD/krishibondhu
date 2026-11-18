package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Build
import com.example.data.model.AdConfig
import com.example.util.NetworkMonitor
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    const val AD_DISPLAY_FAILED = "AD_DISPLAY_FAILED"
    const val AD_CLOSED = "AD_CLOSED"
    const val AD_NO_FILL = "AD_NO_FILL"
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
    val noFillCount: Int = 0,
    val failedCount: Int = 0,
    val lastError: String = ""
)

data class AdRuntimeTelemetry(
    val sdkInitStatus: SdkInitStatus = SdkInitStatus.IDLE,
    val mediationPlatform: String = "Unity LevelPlay 9.2.0",
    val activeAppKeyMasked: String = "",
    val testModeEnabled: Boolean = false,
    val lastInitError: String = "",
    val adapterStatuses: List<AdNetworkAdapterStatus> = defaultAdapterStatuses(AdConfig(), false),
    val formatMetrics: Map<AdFormatType, AdFormatTelemetry> = AdFormatType.entries.associateWith {
        AdFormatTelemetry(format = it)
    },
    val dailyRevenueUsd: Double = 0.0,
    val monthlyRevenueUsd: Double = 0.0,
    val networkRevenueUsd: Map<String, Double> = emptyMap(),
    val impressionCount: Int = 0,
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
        adapterVersion = "5.13.0",
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
    )
)

private fun isAdapterClassPresent(className: String): Boolean =
    runCatching { Class.forName(className) }.isSuccess

private fun defaultAdapterStatuses(
    config: AdConfig,
    sdkInitialized: Boolean
): List<AdNetworkAdapterStatus> =
    SUPPORTED_NETWORK_DESCRIPTORS.map { desc ->
        val packaged = isAdapterClassPresent(desc.adapterClass)
        val enabled = desc.enabledSelector(config)
        val keyPresent = desc.appIdSelector(config).trim().isNotBlank()
        val ready = sdkInitialized && packaged && enabled && keyPresent
        val note = when {
            !packaged -> "Adapter class missing from build"
            !enabled -> "Disabled in Admin Panel"
            !keyPresent -> if (desc.requiresSeparateApproval) {
                "Account approval & Network App ID required"
            } else {
                "Network App/Game ID missing in Admin Panel"
            }
            !sdkInitialized -> "Waiting for LevelPlay SDK initialization"
            else -> "Configured & active via LevelPlay Mediation"
        }
        AdNetworkAdapterStatus(
            key = desc.key,
            displayName = desc.displayName,
            adapterClass = desc.adapterClass,
            adapterVersion = desc.adapterVersion,
            requiresSeparateApproval = desc.requiresSeparateApproval,
            sdkPackaged = packaged,
            enabledInConfig = enabled,
            appKeyConfigured = keyPresent,
            runtimeReady = ready,
            statusNote = note
        )
    }

object AdMonetizationManager {
    private const val INTERSTITIAL_COOLDOWN_MS = 180_000L
    private const val APP_OPEN_COOLDOWN_MS = 240_000L
    private const val MAX_LOG_ENTRIES = 80

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _telemetry = MutableStateFlow(AdRuntimeTelemetry())
    val telemetry: StateFlow<AdRuntimeTelemetry> = _telemetry.asStateFlow()

    @Volatile
    private var latestConfig: AdConfig = AdConfig()
    @Volatile
    private var initializedAppKey: String = ""
    @Volatile
    private var sdkInitialized = false
    @Volatile
    private var sdkInitializing = false
    @Volatile
    private var impressionListenerRegistered = false
    @Volatile
    private var currentRoute: String = "splash"
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    private var interstitialAd: LevelPlayInterstitialAd? = null
    private var loadedInterstitialUnitId: String = ""
    private var isLoadingInterstitial = false
    private var lastInterstitialShownAt = 0L

    private var rewardedAd: LevelPlayRewardedAd? = null
    private var loadedRewardedUnitId: String = ""
    private var isLoadingRewarded = false
    private var pendingRewardCallback: ((Boolean, String) -> Unit)? = null

    private var appOpenAd: LevelPlayInterstitialAd? = null
    private var loadedAppOpenUnitId: String = ""
    private var isLoadingAppOpen = false
    private var lastAppOpenShownAt = 0L

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

    fun setCurrentActivity(activity: Activity?) {
        currentActivityRef = activity?.let { WeakReference(it) }
    }

    fun clearActivityIfMatches(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }

    fun setCurrentRoute(route: String) {
        currentRoute = route
    }

    fun isAdBlockedRoute(route: String): Boolean =
        route == "splash" ||
            route == "cart" ||
            route == "activation" ||
            route.startsWith("order/") ||
            route == "login" ||
            route == "register"

    fun syncConfig(context: Context, config: AdConfig) {
        latestConfig = config
        refreshAdapterAndFormatConfig(config)
        if (!config.adsEnabled || !config.androidAdsEnabled) {
            return
        }
        val appKey = config.mediationAppKey.trim()
        if (appKey.isEmpty()) {
            return
        }
        if (!isTestEnvironment()) {
            initialize(context, config) {
                preloadAllFormats(context, config)
            }
        }
    }

    fun initialize(
        context: Context,
        config: AdConfig = latestConfig,
        userId: String? = null,
        onReady: (() -> Unit)? = null
    ) {
        latestConfig = config
        refreshAdapterAndFormatConfig(config)

        if (!config.adsEnabled || !config.androidAdsEnabled) {
            appendLog(
                code = AdDebugEventCode.NETWORK_UNAVAILABLE,
                message = "Android monetization disabled in Admin configuration"
            )
            return
        }

        val appKey = config.mediationAppKey.trim()
        if (appKey.isEmpty()) {
            _telemetry.update {
                it.copy(
                    sdkInitStatus = SdkInitStatus.FAILED,
                    lastInitError = "LevelPlay App Key missing in Admin Monetization settings",
                    activeAppKeyMasked = "(empty)",
                    adapterStatuses = defaultAdapterStatuses(config, false)
                )
            }
            appendLog(
                code = AdDebugEventCode.INVALID_APP_KEY,
                message = "LevelPlay initialization skipped: App Key is empty"
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

        if (sdkInitialized && initializedAppKey == appKey) {
            onReady?.invoke()
            return
        }
        if (sdkInitializing) return

        sdkInitializing = true
        val masked = maskKey(appKey)
        _telemetry.update {
            it.copy(
                sdkInitStatus = SdkInitStatus.INITIALIZING,
                activeAppKeyMasked = masked,
                testModeEnabled = config.adTestMode,
                lastInitError = ""
            )
        }
        appendLog(
            code = AdDebugEventCode.SDK_INITIALIZATION_STARTED,
            message = "Starting Unity LevelPlay initialization (appKey=$masked, testMode=${config.adTestMode})"
        )

        runCatching {
            if (!impressionListenerRegistered) {
                LevelPlay.addImpressionDataListener(impressionDataListener)
                impressionListenerRegistered = true
            }
            if (config.adTestMode) {
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
                    initializedAppKey = appKey
                    _telemetry.update {
                        it.copy(
                            sdkInitStatus = SdkInitStatus.SUCCESS,
                            lastInitError = "",
                            activeAppKeyMasked = masked,
                            testModeEnabled = config.adTestMode,
                            adapterStatuses = defaultAdapterStatuses(latestConfig, true)
                        )
                    }
                    appendLog(
                        code = AdDebugEventCode.SDK_INITIALIZATION_SUCCESS,
                        message = "LevelPlay SDK initialized successfully"
                    )
                    onReady?.let { cb ->
                        scope.launch { cb() }
                    }
                }

                override fun onInitFailed(error: LevelPlayInitError) {
                    sdkInitializing = false
                    sdkInitialized = false
                    val msg = "Code ${error.errorCode}: ${error.errorMessage}"
                    _telemetry.update {
                        it.copy(
                            sdkInitStatus = SdkInitStatus.FAILED,
                            lastInitError = msg,
                            adapterStatuses = defaultAdapterStatuses(latestConfig, false)
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
                    adapterStatuses = defaultAdapterStatuses(latestConfig, false)
                )
            }
            appendLog(
                code = AdDebugEventCode.SDK_INITIALIZATION_FAILED,
                message = msg
            )
        }
    }

    fun preloadAllFormats(context: Context, config: AdConfig = latestConfig) {
        if (isTestEnvironment()) return
        if (!config.adsEnabled || !config.androidAdsEnabled) return
        if (config.interstitialEnabled) {
            preloadInterstitial(context, config.resolvedInterstitialUnitId(), true)
        }
        if (config.rewardedEnabled) {
            preloadRewarded(context, config.resolvedRewardedUnitId(), true)
        }
        if (config.appOpenEnabled) {
            preloadAppOpen(context, config.resolvedAppOpenUnitId(), true)
        }
    }

    fun preloadInterstitial(context: Context, unitId: String, enabled: Boolean) {
        val cleanId = unitId.trim()
        if (!enabled || !latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
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
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                    updateFormatMetric(AdFormatType.INTERSTITIAL) {
                        it.copy(displayedCount = it.displayedCount + 1)
                    }
                    appendLog(
                        code = AdDebugEventCode.AD_DISPLAYED,
                        format = AdFormatType.INTERSTITIAL,
                        network = adInfo.adNetwork,
                        message = "Interstitial shown (${adInfo.adNetwork})"
                    )
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
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

                override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    interstitialAd = null
                    appendLog(
                        code = AdDebugEventCode.AD_CLOSED,
                        format = AdFormatType.INTERSTITIAL,
                        network = adInfo.adNetwork,
                        message = "Interstitial closed"
                    )
                    preloadInterstitial(context, cleanId, latestConfig.interstitialEnabled)
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
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        if (isTestEnvironment() || !enabled || !latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
        if (isAdBlockedRoute(currentRoute)) return

        val cleanId = overrideUnitId.trim()
        if (cleanId.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) return

        val ad = interstitialAd
        if (sdkInitialized && ad != null && loadedInterstitialUnitId == cleanId && ad.isAdReady()) {
            lastInterstitialShownAt = now
            runCatching { ad.showAd(activity, currentRoute) }.onFailure { err ->
                interstitialAd = null
                appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.INTERSTITIAL, message = err.message ?: "showAd failed")
            }
        } else {
            preloadInterstitial(activity, cleanId, enabled)
        }
    }

    fun preloadRewarded(context: Context, unitId: String, enabled: Boolean) {
        val cleanId = unitId.trim()
        if (!enabled || !latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
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
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                    updateFormatMetric(AdFormatType.REWARDED) {
                        it.copy(displayedCount = it.displayedCount + 1)
                    }
                    appendLog(AdDebugEventCode.AD_DISPLAYED, AdFormatType.REWARDED, adInfo.adNetwork, "Rewarded displayed")
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    rewardedAd = null
                    pendingRewardCallback?.invoke(false, error.errorMessage)
                    pendingRewardCallback = null
                    appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.REWARDED, adInfo.adNetwork, error.errorMessage)
                }

                override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

                override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                    pendingRewardCallback?.invoke(true, "${reward.amount} ${reward.name}".trim())
                    pendingRewardCallback = null
                    appendLog(AdDebugEventCode.AD_DISPLAYED, AdFormatType.REWARDED, adInfo.adNetwork, "Reward granted: ${reward.amount} ${reward.name}")
                }

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    rewardedAd = null
                    pendingRewardCallback = null
                    appendLog(AdDebugEventCode.AD_CLOSED, AdFormatType.REWARDED, adInfo.adNetwork, "Rewarded closed")
                    preloadRewarded(context, cleanId, latestConfig.rewardedEnabled)
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
        if (isAdBlockedRoute(currentRoute) || !cfg.adsEnabled || !cfg.androidAdsEnabled || !cfg.rewardedEnabled) {
            onResult(false, "Rewarded ads are currently disabled")
            return
        }
        val cleanId = cfg.resolvedRewardedUnitId()
        if (cleanId.isEmpty()) {
            markInvalidPlacement(AdFormatType.REWARDED, "Rewarded Ad Unit ID missing")
            onResult(false, "Rewarded Ad Unit ID missing")
            return
        }
        if (isTestEnvironment()) {
            onResult(false, "Test environment")
            return
        }
        val ad = rewardedAd
        if (sdkInitialized && ad != null && loadedRewardedUnitId == cleanId && ad.isAdReady()) {
            pendingRewardCallback = onResult
            runCatching { ad.showAd(activity, placementName) }.onFailure { err ->
                pendingRewardCallback = null
                onResult(false, err.message ?: "Failed to show rewarded ad")
            }
        } else {
            preloadRewarded(activity, cleanId, true)
            onResult(false, "Rewarded video loading — please try again shortly")
        }
    }

    fun preloadAppOpen(context: Context, unitId: String, enabled: Boolean) {
        val cleanId = unitId.trim()
        if (!enabled || !latestConfig.adsEnabled || !latestConfig.androidAdsEnabled) return
        if (cleanId.isEmpty()) {
            markInvalidPlacement(AdFormatType.APP_OPEN, "App Open Ad Unit ID is blank")
            return
        }
        if (isTestEnvironment()) return
        if (!NetworkMonitor.isCurrentlyOnline(context)) {
            appendLog(AdDebugEventCode.NETWORK_UNAVAILABLE, AdFormatType.APP_OPEN, message = "Offline: skipped app open preload")
            return
        }
        if (!sdkInitialized) {
            initialize(context, latestConfig) {
                preloadAppOpen(context, cleanId, enabled)
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
                    updateFormatMetric(AdFormatType.APP_OPEN) {
                        it.copy(
                            requestStatus = "Completed",
                            loadedStatus = "Loaded",
                            loadedCount = it.loadedCount + 1,
                            lastError = ""
                        )
                    }
                    appendLog(AdDebugEventCode.AD_LOADED, AdFormatType.APP_OPEN, adInfo.adNetwork, "App Open ready")
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    isLoadingAppOpen = false
                    appOpenAd = null
                    handleAdLoadFailure(AdFormatType.APP_OPEN, error)
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                    updateFormatMetric(AdFormatType.APP_OPEN) {
                        it.copy(displayedCount = it.displayedCount + 1)
                    }
                    appendLog(AdDebugEventCode.AD_DISPLAYED, AdFormatType.APP_OPEN, adInfo.adNetwork, "App Open displayed")
                }

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    appOpenAd = null
                    appendLog(AdDebugEventCode.AD_DISPLAY_FAILED, AdFormatType.APP_OPEN, adInfo.adNetwork, error.errorMessage)
                }

                override fun onAdClicked(adInfo: LevelPlayAdInfo) = Unit

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    appOpenAd = null
                    appendLog(AdDebugEventCode.AD_CLOSED, AdFormatType.APP_OPEN, adInfo.adNetwork, "App Open closed")
                    preloadAppOpen(context, cleanId, latestConfig.appOpenEnabled)
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

    fun maybeShowAppOpenOnForeground(activity: Activity? = currentActivityRef?.get()) {
        val cfg = latestConfig
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        if (isTestEnvironment() || !cfg.adsEnabled || !cfg.androidAdsEnabled || !cfg.appOpenEnabled) return
        if (isAdBlockedRoute(currentRoute)) return
        val cleanId = cfg.resolvedAppOpenUnitId()
        if (cleanId.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastAppOpenShownAt < APP_OPEN_COOLDOWN_MS) return

        val ad = appOpenAd
        if (sdkInitialized && ad != null && loadedAppOpenUnitId == cleanId && ad.isAdReady()) {
            lastAppOpenShownAt = now
            runCatching { ad.showAd(activity, "app_open") }
        } else {
            preloadAppOpen(activity, cleanId, true)
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
        appendLog(
            code = AdDebugEventCode.AD_DISPLAYED,
            format = mapFormatName(adFormat),
            network = cleanNetwork,
            message = "Real impression recorded (+$${"%.5f".format(Locale.ENGLISH, cleanRevenue)})"
        )
    }

    fun isSdkReady(): Boolean = sdkInitialized

    fun canRequestSdkAd(context: Context, config: AdConfig, unitOrPlacementId: String, format: AdFormatType): Boolean {
        if (!config.adsEnabled || !config.androidAdsEnabled) return false
        if (config.mediationAppKey.trim().isEmpty()) {
            appendLog(AdDebugEventCode.INVALID_APP_KEY, format, message = "Mediation App Key is missing")
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

    fun resetForTest() {
        latestConfig = AdConfig()
        initializedAppKey = ""
        sdkInitialized = false
        sdkInitializing = false
        interstitialAd = null
        loadedInterstitialUnitId = ""
        isLoadingInterstitial = false
        lastInterstitialShownAt = 0L
        rewardedAd = null
        loadedRewardedUnitId = ""
        isLoadingRewarded = false
        pendingRewardCallback = null
        appOpenAd = null
        loadedAppOpenUnitId = ""
        isLoadingAppOpen = false
        lastAppOpenShownAt = 0L
        _telemetry.value = AdRuntimeTelemetry()
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
                .copy(enabled = config.appOpenEnabled, unitOrPlacementId = config.resolvedAppOpenUnitId())

            state.copy(
                activeAppKeyMasked = maskKey(config.mediationAppKey.trim()),
                testModeEnabled = config.adTestMode,
                adapterStatuses = defaultAdapterStatuses(config, sdkInitialized),
                formatMetrics = updatedFormats
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
