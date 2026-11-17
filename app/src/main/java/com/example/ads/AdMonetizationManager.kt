package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Build
import com.example.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AdMonetizationManager {
    private const val INTERSTITIAL_COOLDOWN_MS = 180_000L
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false
    @Volatile
    private var initializing = false
    private var isLoadingInterstitial = false
    private var interstitialAd: InterstitialAd? = null
    private var loadedUnitId: String = ""
    private var lastShownAt = 0L

    fun isTestEnvironment(): Boolean =
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true) || Build.FINGERPRINT.isBlank()

    fun isPlayServicesAvailable(context: Context): Boolean =
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
        if (isTestEnvironment()) return
        if (initialized) {
            onReady?.let { cb ->
                initScope.launch(Dispatchers.Main) { cb() }
            }
            return
        }
        val appContext = context.applicationContext
        if (!isPlayServicesAvailable(appContext)) return
        if (initializing) return
        initializing = true
        initScope.launch {
            runCatching {
                MobileAds.initialize(appContext) {
                    initialized = true
                    initializing = false
                    onReady?.let { cb ->
                        initScope.launch(Dispatchers.Main) { cb() }
                    }
                }
            }.onFailure {
                initializing = false
            }
        }
    }

    fun resolveBannerUnitId(context: Context, overrideId: String): String =
        overrideId.trim().ifBlank { context.getString(R.string.admob_banner_unit_id) }

    fun resolveInterstitialUnitId(context: Context, overrideId: String): String =
        overrideId.trim().ifBlank { context.getString(R.string.admob_interstitial_unit_id) }

    fun preloadInterstitial(context: Context, overrideUnitId: String, enabled: Boolean) {
        if (isTestEnvironment() || !enabled || !isPlayServicesAvailable(context)) return
        val unitId = resolveInterstitialUnitId(context, overrideUnitId)
        if (unitId.isBlank()) return
        if ((interstitialAd != null && loadedUnitId == unitId) || isLoadingInterstitial) return

        val appContext = context.applicationContext
        if (!initialized) {
            initialize(appContext) {
                preloadInterstitial(appContext, overrideUnitId, enabled)
            }
            return
        }

        isLoadingInterstitial = true
        runCatching {
            InterstitialAd.load(
                appContext,
                unitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        loadedUnitId = unitId
                        isLoadingInterstitial = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isLoadingInterstitial = false
                    }
                }
            )
        }.onFailure {
            isLoadingInterstitial = false
        }
    }

    fun maybeShowInterstitial(activity: Activity?, overrideUnitId: String, enabled: Boolean) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        if (isTestEnvironment() || !enabled) return

        val now = System.currentTimeMillis()
        if (now - lastShownAt < INTERSTITIAL_COOLDOWN_MS) return

        val unitId = resolveInterstitialUnitId(activity, overrideUnitId)
        val ad = interstitialAd
        if (ad != null && loadedUnitId == unitId) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitial(activity, overrideUnitId, enabled)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    interstitialAd = null
                }
            }
            lastShownAt = now
            runCatching { ad.show(activity) }.onFailure { interstitialAd = null }
        } else {
            preloadInterstitial(activity, overrideUnitId, enabled)
        }
    }
}
