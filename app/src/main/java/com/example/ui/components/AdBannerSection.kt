package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.ads.AdFormatType
import com.example.ads.AdMonetizationManager
import com.example.data.model.AdConfig
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.HarvestGoldLight
import com.example.ui.theme.LightGreen
import com.example.ui.theme.PrimaryGreen
import com.example.util.NetworkMonitor
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAd
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAdListener
import com.ironsource.mediationsdk.ads.nativead.NativeAdLayout
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo
import com.ironsource.mediationsdk.logger.IronSourceError
import com.startapp.sdk.ads.banner.Banner as StartIoBanner
import com.startapp.sdk.ads.banner.BannerListener as StartIoBannerListener
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.banner.LevelPlayBannerAdView
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

@Composable
fun AdBannerSection(
    adConfig: AdConfig,
    modifier: Modifier = Modifier,
    placementTag: String = "banner"
) {
    if (!adConfig.adsEnabled || AdMonetizationManager.isCurrentRouteAdBlocked()) return

    val context = LocalContext.current
    val telemetry by AdMonetizationManager.telemetry.collectAsState()
    val priority = adConfig.mobileAdNetworkPriority.trim().ifBlank { "ALL" }
    fun allows(networkKey: String): Boolean =
        priority.equals("ALL", ignoreCase = true) || priority.equals(networkKey, ignoreCase = true)

    val bannerUnitId = adConfig.resolvedBannerUnitId()
    val nativePlacement = adConfig.resolvedNativePlacement()
    val mediationAppKey = AdMonetizationManager.resolveMediationAppKey(adConfig)
    val startIoAppId = AdMonetizationManager.resolveStartIoAppId(adConfig)
    val usesDirectUnity = AdMonetizationManager.usesDirectUnityAds(adConfig)

    val webNetworkAd = remember(adConfig, placementTag, priority) {
        buildMobileNetworkHtmlPayload(adConfig, placementTag, priority)
    }
    val isOnline = remember(context) {
        AdMonetizationManager.isTestEnvironment() || NetworkMonitor.isCurrentlyOnline(context)
    }

    val sdkReadyOrTest = AdMonetizationManager.isTestEnvironment() ||
        telemetry.sdkInitStatus == com.example.ads.SdkInitStatus.SUCCESS

    val showDirectUnityBanner = usesDirectUnity &&
        adConfig.bannerEnabled &&
        bannerUnitId.isNotBlank() &&
        (allows("UNITY") || allows("LEVELPLAY") || allows("UNITY_LEVELPLAY")) &&
        (AdMonetizationManager.isTestEnvironment() || (isOnline && sdkReadyOrTest))

    val showMediatedBanner = adConfig.androidAdsEnabled &&
        adConfig.bannerEnabled &&
        mediationAppKey.isNotBlank() &&
        bannerUnitId.isNotBlank() &&
        (allows("LEVELPLAY") || allows("UNITY_LEVELPLAY")) &&
        (AdMonetizationManager.isTestEnvironment() || (isOnline && sdkReadyOrTest))

    val showStartIoBanner = adConfig.androidAdsEnabled &&
        adConfig.bannerEnabled &&
        adConfig.startIoEnabled &&
        startIoAppId.isNotBlank() &&
        allows("STARTIO") &&
        (AdMonetizationManager.isTestEnvironment() || (isOnline && telemetry.startIoReady))

    val showMediatedNative = adConfig.androidAdsEnabled &&
        adConfig.nativeEnabled &&
        mediationAppKey.isNotBlank() &&
        nativePlacement.isNotBlank() &&
        (placementTag == "browse_feed" || placementTag == "browse_crops_row_3") &&
        (AdMonetizationManager.isTestEnvironment() || (isOnline && sdkReadyOrTest))

    val showMobileWebAd = adConfig.mobileWebAdsEnabled && webNetworkAd != null &&
        (AdMonetizationManager.isTestEnvironment() || isOnline)
    val showSponsor = adConfig.sponsorEnabled &&
        adConfig.sponsorTitle.isNotBlank() &&
        (allows("SPONSOR") || (!showMediatedBanner && !showDirectUnityBanner && !showStartIoBanner && !showMobileWebAd))

    val directLink = adConfig.mobileDirectLinkUrl.trim()
        .takeIf { it.startsWith("https://") || it.startsWith("http://") }

    if (!showSponsor && !showDirectUnityBanner && !showMediatedBanner && !showStartIoBanner && !showMediatedNative && !showMobileWebAd && directLink == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Direct Agricultural Sponsor & Smart Direct Link Card
        if (showSponsor || directLink != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sponsor_banner_$placementTag")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Campaign,
                                contentDescription = null,
                                tint = PrimaryGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "কৃষি সহায়তা ও স্পন্সর",
                                style = MaterialTheme.typography.labelMedium,
                                color = DarkGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (adConfig.showAdsLabel) {
                            Surface(
                                color = HarvestGoldLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "বিজ্ঞাপন",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DarkGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (showSponsor && adConfig.sponsorImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = adConfig.sponsorImageUrl,
                            contentDescription = adConfig.sponsorTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }

                    if (showSponsor) {
                        Text(
                            text = adConfig.sponsorTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (adConfig.sponsorSubtitle.isNotBlank()) {
                            Text(
                                text = adConfig.sponsorSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val targetUrl = adConfig.sponsorTargetUrl.trim()
                        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?: directLink
                    if (targetUrl != null) {
                        OutlinedButton(
                            onClick = {
                                AdMonetizationManager.trackFormatClicked(AdFormatType.BANNER, "Sponsor")
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, targetUrl.toUri()))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(adConfig.sponsorCtaText.ifBlank { "বিস্তারিত দেখুন" })
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Multi-Network Mobile WebView Ad Bridge (collapses cleanly on failure)
        if (webNetworkAd != null && adConfig.mobileWebAdsEnabled && (AdMonetizationManager.isTestEnvironment() || isOnline)) {
            var webAdFailed by remember(webNetworkAd.html, placementTag) { mutableStateOf(false) }
            if (!webAdFailed) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (adConfig.showAdsLabel) {
                            Text(
                                text = "Sponsored Ad • ${webNetworkAd.networkLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (AdMonetizationManager.isTestEnvironment()) {
                            Surface(
                                color = LightGreen,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .testTag("mobile_multi_ad_$placementTag")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${webNetworkAd.networkLabel} ($placementTag)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkGreen
                                    )
                                }
                            }
                        } else {
                            key(webNetworkAd.html) {
                                SandboxedAdWebView(
                                    htmlContent = webNetworkAd.html,
                                    onAdClicked = {
                                        AdMonetizationManager.trackFormatClicked(
                                            AdFormatType.BANNER,
                                            webNetworkAd.networkLabel
                                        )
                                    },
                                    onAdFailed = { webAdFailed = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .testTag("mobile_multi_ad_$placementTag")
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2b. Direct Unity Ads Banner (Unity Game ID + Banner_Android — Zero AdMob)
        if (showDirectUnityBanner) {
            DirectUnityBannerAdCard(
                placementId = bannerUnitId,
                placementTag = placementTag,
                showAdsLabel = adConfig.showAdsLabel
            )
        }

        // 3. Unity LevelPlay Adaptive Banner (Unity Ads, AppLovin, Meta, Mintegral, Pangle, InMobi, Liftoff, DT Exchange — Zero AdMob)
        if (showMediatedBanner) {
            var bannerFailed by remember(bannerUnitId, placementTag) { mutableStateOf(false) }
            if (!bannerFailed) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (adConfig.showAdsLabel) {
                            Text(
                                text = "Sponsored Ad • Multi-Network Mediation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (AdMonetizationManager.isTestEnvironment()) {
                            Surface(
                                color = LightGreen,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("ad_banner_$placementTag")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "LevelPlay Adaptive Banner ($placementTag)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkGreen
                                    )
                                }
                            }
                        } else {
                            key(bannerUnitId, placementTag) {
                                var bannerViewHolder by remember { mutableStateOf<LevelPlayBannerAdView?>(null) }
                                DisposableEffect(bannerUnitId, placementTag) {
                                    onDispose {
                                        bannerViewHolder?.destroy()
                                        bannerViewHolder = null
                                    }
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        val adSize = LevelPlayAdSize.createAdaptiveAdSize(ctx)
                                        val configBuilder = LevelPlayBannerAdView.Config.Builder()
                                        if (adSize != null) {
                                            configBuilder.setAdSize(adSize)
                                        }
                                        configBuilder.setPlacementName(placementTag)
                                        LevelPlayBannerAdView(ctx, bannerUnitId, configBuilder.build()).apply {
                                            setBannerListener(object : LevelPlayBannerAdViewListener {
                                                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                                                    bannerFailed = false
                                                    AdMonetizationManager.trackFormatLoaded(
                                                        AdFormatType.BANNER,
                                                        adInfo.adNetwork
                                                    )
                                                }

                                                override fun onAdLoadFailed(error: LevelPlayAdError) {
                                                    bannerFailed = true
                                                    AdMonetizationManager.trackFormatFailed(
                                                        AdFormatType.BANNER,
                                                        error.errorCode,
                                                        error.errorMessage
                                                    )
                                                }

                                                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                                                    AdMonetizationManager.trackFormatDisplayed(
                                                        AdFormatType.BANNER,
                                                        adInfo.adNetwork
                                                    )
                                                }

                                                override fun onAdDisplayFailed(adInfo: LevelPlayAdInfo, error: LevelPlayAdError) {
                                                    AdMonetizationManager.trackFormatFailed(
                                                        AdFormatType.BANNER,
                                                        error.errorCode,
                                                        error.errorMessage
                                                    )
                                                }

                                                override fun onAdClicked(adInfo: LevelPlayAdInfo) {
                                                    AdMonetizationManager.trackFormatClicked(
                                                        AdFormatType.BANNER,
                                                        adInfo.adNetwork
                                                    )
                                                }
                                                override fun onAdLeftApplication(adInfo: LevelPlayAdInfo) = Unit
                                                override fun onAdExpanded(adInfo: LevelPlayAdInfo) = Unit
                                                override fun onAdCollapsed(adInfo: LevelPlayAdInfo) = Unit
                                            })
                                            AdMonetizationManager.trackFormatRequested(AdFormatType.BANNER, bannerUnitId)
                                            loadAd()
                                        }.also { bannerViewHolder = it }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 50.dp)
                                        .testTag("ad_banner_$placementTag")
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3b. Start.io Standalone Banner (Multi-Network Supplement)
        if (showStartIoBanner) {
            StartIoBannerAdCard(
                placementTag = placementTag,
                showAdsLabel = adConfig.showAdsLabel
            )
        }

        // 4. LevelPlay Native Ad Slot (for feed cards)
        if (showMediatedNative) {
            LevelPlayNativeAdCard(
                placementName = nativePlacement,
                showAdsLabel = adConfig.showAdsLabel
            )
        }
    }
}

@Composable
private fun DirectUnityBannerAdCard(
    placementId: String,
    placementTag: String,
    showAdsLabel: Boolean
) {
    var bannerFailed by remember(placementId, placementTag) { mutableStateOf(false) }
    if (bannerFailed) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showAdsLabel) {
                Text(
                    text = "Sponsored Ad • Unity Ads",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (AdMonetizationManager.isTestEnvironment()) {
                Surface(
                    color = LightGreen,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("unity_banner_$placementTag")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Unity Banner ($placementId / $placementTag)",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkGreen
                        )
                    }
                }
            } else {
                key(placementId, placementTag) {
                    var bannerViewHolder by remember { mutableStateOf<BannerView?>(null) }
                    DisposableEffect(placementId, placementTag) {
                        onDispose {
                            runCatching { bannerViewHolder?.destroy() }
                            bannerViewHolder = null
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            val host = ctx.findActivity() ?: ctx
                            runCatching {
                                val size = UnityBannerSize.getDynamicSize(host)
                                BannerView(host, placementId, size).apply {
                                    listener = object : BannerView.IListener {
                                        override fun onBannerLoaded(bannerAdView: BannerView?) {
                                            bannerFailed = false
                                            AdMonetizationManager.trackFormatLoaded(AdFormatType.BANNER, "Unity Ads")
                                        }

                                        override fun onBannerShown(bannerAdView: BannerView?) {
                                            AdMonetizationManager.trackFormatDisplayed(AdFormatType.BANNER, "Unity Ads")
                                        }

                                        override fun onBannerClick(bannerAdView: BannerView?) {
                                            AdMonetizationManager.trackFormatClicked(AdFormatType.BANNER, "Unity Ads")
                                        }

                                        override fun onBannerFailedToLoad(
                                            bannerAdView: BannerView?,
                                            errorInfo: BannerErrorInfo?
                                        ) {
                                            bannerFailed = true
                                            AdMonetizationManager.trackFormatFailed(
                                                AdFormatType.BANNER,
                                                errorInfo?.errorCode?.ordinal ?: -1,
                                                errorInfo?.errorMessage ?: "Unity banner failed to load"
                                            )
                                        }

                                        override fun onBannerLeftApplication(bannerAdView: BannerView?) = Unit
                                    }
                                    AdMonetizationManager.trackFormatRequested(AdFormatType.BANNER, placementId)
                                    load()
                                }.also { bannerViewHolder = it }
                            }.getOrElse { err ->
                                bannerFailed = true
                                AdMonetizationManager.trackFormatFailed(
                                    AdFormatType.BANNER,
                                    -1,
                                    err.message ?: "Unity BannerView creation error"
                                )
                                FrameLayout(ctx)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .testTag("unity_banner_$placementTag")
                    )
                }
            }
        }
    }
}

@Composable
private fun StartIoBannerAdCard(
    placementTag: String,
    showAdsLabel: Boolean
) {
    var bannerFailed by remember(placementTag) { mutableStateOf(false) }
    if (bannerFailed) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showAdsLabel) {
                Text(
                    text = "Sponsored Ad • Start.io Network",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (AdMonetizationManager.isTestEnvironment()) {
                Surface(
                    color = LightGreen,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("startio_banner_$placementTag")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Start.io Banner ($placementTag)",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkGreen
                        )
                    }
                }
            } else {
                key(placementTag) {
                    var bannerViewHolder by remember { mutableStateOf<StartIoBanner?>(null) }
                    DisposableEffect(placementTag) {
                        onDispose {
                            runCatching { bannerViewHolder?.hideBanner() }
                            bannerViewHolder = null
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            val host = ctx.findActivity() ?: ctx
                            runCatching {
                                StartIoBanner(host, object : StartIoBannerListener {
                                    override fun onReceiveAd(banner: View?) {
                                        bannerFailed = false
                                        AdMonetizationManager.trackFormatLoaded(AdFormatType.BANNER, "Start.io")
                                    }

                                    override fun onFailedToReceiveAd(banner: View?) {
                                        bannerFailed = true
                                        AdMonetizationManager.trackFormatFailed(
                                            AdFormatType.BANNER,
                                            -1,
                                            "Start.io banner failed to load"
                                        )
                                    }

                                    override fun onImpression(banner: View?) {
                                        AdMonetizationManager.trackFormatDisplayed(AdFormatType.BANNER, "Start.io")
                                    }

                                    override fun onClick(banner: View?) {
                                        AdMonetizationManager.trackFormatClicked(AdFormatType.BANNER, "Start.io")
                                    }
                                }).apply {
                                    AdMonetizationManager.trackFormatRequested(AdFormatType.BANNER, "startio_$placementTag")
                                    loadAd()
                                }.also { bannerViewHolder = it }
                            }.getOrElse { err ->
                                bannerFailed = true
                                AdMonetizationManager.trackFormatFailed(
                                    AdFormatType.BANNER,
                                    -1,
                                    err.message ?: "Start.io banner error"
                                )
                                FrameLayout(ctx)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .testTag("startio_banner_$placementTag")
                    )
                }
            }
        }
    }
}

@Composable
fun RewardedMarketTipCard(
    adConfig: AdConfig,
    modifier: Modifier = Modifier
) {
    if (!adConfig.adsEnabled || !adConfig.androidAdsEnabled || !adConfig.rewardedEnabled) return
    if (AdMonetizationManager.isCurrentRouteAdBlocked()) return
    val rewardedId = adConfig.resolvedRewardedUnitId()
    val startIoReady = adConfig.startIoEnabled && AdMonetizationManager.resolveStartIoAppId(adConfig).isNotBlank()
    if (rewardedId.isBlank() && !startIoReady) return
    val hasAdConfig = AdMonetizationManager.usesDirectUnityAds(adConfig) ||
        AdMonetizationManager.resolveMediationAppKey(adConfig).isNotBlank() ||
        startIoReady
    if (!hasAdConfig) return

    val context = LocalContext.current
    var unlockedTip by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val tips = remember {
        listOf(
            "🌾 পাইকারি পরামর্শ: মণ প্রতি আর্দ্রতা ১৪% এর নিচে থাকলে ধান ও ভুট্টা সংরক্ষণে ভালো পাইকারি দাম পাওয়া যায়।",
            "🧅 সংরক্ষণ টিপস: পেঁয়াজ ও আলু ছায়াযুক্ত ও বাতাস চলাচল করে এমন মাচায় রাখলে পচন ৩০% পর্যন্ত কমে।",
            "📦 পরিবহন টিপস: সরাসরি খামার থেকে পাইকারি পিকআপে মধ্যস্বত্বভোগীর ৫–১২% খরচ সাশ্রয় হয়।"
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HarvestGoldLight),
        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("rewarded_market_tip_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = DarkGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "দৈনিক পাইকারি কৃষি টিপস (Rewarded Video)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DarkGreen
                    )
                }
                if (adConfig.showAdsLabel) {
                    Surface(color = LightGreen, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "Ad",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (unlockedTip != null) {
                Text(
                    text = unlockedTip!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkGreen,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "একটি ছোট ভিডিও বিজ্ঞাপন দেখে আজকের ফসল সংরক্ষণ ও পাইকারি বাজারের বিশেষ টিপস দেখুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkGreen
                )
            }

            Button(
                onClick = {
                    statusMessage = "বিজ্ঞাপন লোড হচ্ছে..."
                    AdMonetizationManager.showRewardedAd(
                        activity = context.findActivity(),
                        placementName = rewardedId
                    ) { rewarded, detail ->
                        if (rewarded) {
                            unlockedTip = tips.random()
                            statusMessage = "✓ ধন্যবাদ! টিপস আনলক হয়েছে।"
                        } else {
                            statusMessage = detail
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("watch_rewarded_ad_button")
            ) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (unlockedTip == null) "ভিডিও দেখে টিপস আনলক করুন" else "আরেকটি টিপস দেখুন")
            }
        }
    }
}

@Composable
private fun LevelPlayNativeAdCard(
    placementName: String,
    showAdsLabel: Boolean
) {
    if (AdMonetizationManager.isTestEnvironment()) {
        Surface(
            color = LightGreen,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("native_ad_$placementName")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "LevelPlay Native Ad ($placementName)",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkGreen
                )
            }
        }
        return
    }

    var nativeLoaded by remember(placementName) { mutableStateOf(false) }
    var nativeFailed by remember(placementName) { mutableStateOf(false) }
    if (nativeFailed) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (nativeLoaded) Modifier else Modifier.height(1.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (showAdsLabel && nativeLoaded) {
                Text(
                    text = "Sponsored • Native Mediation",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            var nativeAdHolder by remember(placementName) { mutableStateOf<LevelPlayNativeAd?>(null) }
            DisposableEffect(placementName) {
                onDispose {
                    nativeAdHolder?.destroyAd()
                    nativeAdHolder = null
                }
            }
            AndroidView(
                factory = { ctx ->
                    val container = NativeAdLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val titleView = TextView(ctx).apply { textSize = 15f }
                    val bodyView = TextView(ctx).apply { textSize = 13f }
                    val ctaView = TextView(ctx).apply { textSize = 13f }
                    val inner = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(titleView)
                        addView(bodyView)
                        addView(ctaView)
                    }
                    container.addView(inner)

                    val nativeAd = LevelPlayNativeAd.Builder()
                        .withPlacementName(placementName)
                        .withListener(object : LevelPlayNativeAdListener {
                            override fun onAdLoaded(ad: LevelPlayNativeAd?, adInfo: AdInfo?) {
                                nativeLoaded = true
                                nativeFailed = false
                                ad?.let { loaded ->
                                    titleView.text = loaded.title ?: ""
                                    bodyView.text = loaded.body ?: ""
                                    ctaView.text = loaded.callToAction ?: ""
                                    container.registerNativeAdViews(loaded)
                                }
                                AdMonetizationManager.trackFormatLoaded(
                                    AdFormatType.NATIVE,
                                    adInfo?.adNetwork ?: "LevelPlay"
                                )
                            }

                            override fun onAdLoadFailed(ad: LevelPlayNativeAd?, error: IronSourceError?) {
                                nativeFailed = true
                                nativeLoaded = false
                                AdMonetizationManager.trackFormatFailed(
                                    AdFormatType.NATIVE,
                                    error?.errorCode ?: -1,
                                    error?.errorMessage ?: "Native ad load failed"
                                )
                            }

                            override fun onAdImpression(ad: LevelPlayNativeAd?, adInfo: AdInfo?) {
                                AdMonetizationManager.trackFormatDisplayed(
                                    AdFormatType.NATIVE,
                                    adInfo?.adNetwork ?: "LevelPlay"
                                )
                            }

                            override fun onAdClicked(ad: LevelPlayNativeAd?, adInfo: AdInfo?) {
                                AdMonetizationManager.trackFormatClicked(
                                    AdFormatType.NATIVE,
                                    adInfo?.adNetwork ?: "LevelPlay"
                                )
                            }
                        })
                        .build()

                    container.setTitleView(titleView)
                    container.setBodyView(bodyView)
                    container.setCallToActionView(ctaView)
                    nativeAdHolder = nativeAd
                    AdMonetizationManager.trackFormatRequested(AdFormatType.NATIVE, placementName)
                    nativeAd.loadAd()
                    container
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class MobileWebAdPayload(
    val networkLabel: String,
    val html: String
)

private fun isSafeHttpsOrHttp(url: String): Boolean {
    val t = url.trim()
    return t.startsWith("https://") || t.startsWith("http://")
}

private fun wrapHtmlDocument(bodyHtml: String): String = """
    <!DOCTYPE html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
        <style>
          html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: transparent; overflow: hidden; display: flex; align-items: center; justify-content: center; }
          * { max-width: 100%; box-sizing: border-box; }
        </style>
      </head>
      <body>$bodyHtml</body>
    </html>
""".trimIndent()

private fun buildMobileNetworkHtmlPayload(
    cfg: AdConfig,
    placementTag: String,
    priority: String
): MobileWebAdPayload? {
    fun allows(networkKey: String): Boolean =
        priority.equals("ALL", ignoreCase = true) || priority.equals(networkKey, ignoreCase = true)

    val customMobile = cfg.mobileCustomAdHtml.trim()
    if (customMobile.isNotEmpty() && (allows("CUSTOM") || allows("ALL"))) {
        return MobileWebAdPayload(
            networkLabel = cfg.customAdName.ifBlank { "Mobile Ad Network" },
            html = wrapHtmlDocument(customMobile)
        )
    }

    val adsterraUrl = cfg.adsterraScriptUrl.trim()
    val adsterraKey = cfg.adsterraBannerKey.trim()
    if (cfg.adsterraEnabled && allows("ADSTERRA") && isSafeHttpsOrHttp(adsterraUrl) && adsterraKey.isNotEmpty()) {
        val escapedKey = adsterraKey.replace("\"", "").replace("'", "")
        val snippet = """
            <script type="text/javascript">
              atOptions = { 'key' : '$escapedKey', 'format' : 'iframe', 'height' : 50, 'width' : 320, 'params' : {} };
            </script>
            <script type="text/javascript" src="$adsterraUrl"></script>
        """.trimIndent()
        return MobileWebAdPayload("Adsterra Network", wrapHtmlDocument(snippet))
    }

    val monetagUrl = cfg.monetagScriptUrl.trim()
    val monetagZone = cfg.monetagZoneId.trim()
    if (cfg.monetagEnabled && allows("MONETAG") && (isSafeHttpsOrHttp(monetagUrl) || monetagZone.isNotEmpty())) {
        val src = if (isSafeHttpsOrHttp(monetagUrl)) monetagUrl else "https://alwingulla.com/88/tag.min.js"
        val escapedZone = monetagZone.replace("\"", "")
        val snippet = """<script src="$src" data-zone="$escapedZone" async data-cfasync="false"></script>"""
        return MobileWebAdPayload("Monetag / PropellerAds", wrapHtmlDocument(snippet))
    }

    val adsenseClient = cfg.adsenseClient.trim()
    val adsenseSlot = when (placementTag) {
        "home_banner" -> cfg.adsenseTopSlot.trim().ifBlank { cfg.adsenseFeedSlot.trim() }
        "browse_feed" -> cfg.adsenseFeedSlot.trim().ifBlank { cfg.adsenseTopSlot.trim() }
        else -> cfg.adsenseFooterSlot.trim().ifBlank { cfg.adsenseFeedSlot.trim() }
    }
    if (cfg.adsenseEnabled && allows("ADSENSE") && adsenseClient.startsWith("ca-pub-") && adsenseSlot.isNotEmpty()) {
        val snippet = """
            <script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=$adsenseClient" crossorigin="anonymous"></script>
            <ins class="adsbygoogle" style="display:inline-block;width:320px;height:50px" data-ad-client="$adsenseClient" data-ad-slot="$adsenseSlot"></ins>
            <script>(adsbygoogle = window.adsbygoogle || []).push({});</script>
        """.trimIndent()
        return MobileWebAdPayload("Google AdSense", wrapHtmlDocument(snippet))
    }

    val sharedCustom = when (placementTag) {
        "home_banner" -> cfg.customTopHtml.trim()
        "browse_feed" -> cfg.customFeedHtml.trim().ifBlank { cfg.customTopHtml.trim() }
        else -> cfg.customFooterHtml.trim().ifBlank { cfg.customTopHtml.trim() }
    }
    if (cfg.customAdEnabled && allows("CUSTOM") && sharedCustom.isNotEmpty()) {
        return MobileWebAdPayload(
            networkLabel = cfg.customAdName.ifBlank { "Custom Ad Network" },
            html = wrapHtmlDocument(sharedCustom)
        )
    }

    return null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedAdWebView(
    htmlContent: String,
    onAdClicked: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnAdClicked by rememberUpdatedState(onAdClicked)
    val latestOnAdFailed by rememberUpdatedState(onAdFailed)
    var webViewHolder by remember(htmlContent) { mutableStateOf<WebView?>(null) }
    var verifyRunnableHolder by remember(htmlContent) { mutableStateOf<Runnable?>(null) }
    var isDisposed by remember(htmlContent) { mutableStateOf(false) }

    DisposableEffect(htmlContent) {
        onDispose {
            isDisposed = true
            verifyRunnableHolder?.let { runnable ->
                runCatching { webViewHolder?.removeCallbacks(runnable) }
            }
            verifyRunnableHolder = null
            webViewHolder?.let { wv ->
                runCatching {
                    wv.stopLoading()
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                }
            }
            webViewHolder = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            runCatching {
                WebView(ctx).apply {
                    // Use software layer for small Compose-hosted ad WebViews to avoid GPU/Vulkan renderer crashes (code -1)
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (isSafeHttpsOrHttp(url)) {
                                latestOnAdClicked()
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                                return true
                            }
                            return true
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            // Gracefully absorb Chromium renderer crash without terminating host app process
                            verifyRunnableHolder?.let { runnable ->
                                runCatching { view?.removeCallbacks(runnable) }
                            }
                            verifyRunnableHolder = null
                            view?.let { crashed ->
                                runCatching {
                                    crashed.stopLoading()
                                    (crashed.parent as? ViewGroup)?.removeView(crashed)
                                    crashed.destroy()
                                }
                            }
                            if (webViewHolder === view) {
                                webViewHolder = null
                            }
                            if (!isDisposed) {
                                latestOnAdFailed()
                            }
                            return true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true && !isDisposed) {
                                latestOnAdFailed()
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame == true && !isDisposed) {
                                latestOnAdFailed()
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            handler?.cancel()
                            if (!isDisposed) {
                                latestOnAdFailed()
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            val wv = view ?: return
                            verifyRunnableHolder?.let { old -> runCatching { wv.removeCallbacks(old) } }
                            val checkRunnable = Runnable {
                                if (isDisposed || webViewHolder == null) return@Runnable
                                runCatching {
                                    wv.evaluateJavascript(
                                        "(function(){var b=document.body;if(!b)return '0';var h=b.querySelector('img,iframe,ins,a,object,embed');return (h||(b.innerText||'').trim().length>0)?'1':'0';})()"
                                    ) { res ->
                                        if (!isDisposed && (res == "\"0\"" || res == "0")) {
                                            latestOnAdFailed()
                                        }
                                    }
                                }
                            }
                            verifyRunnableHolder = checkRunnable
                            wv.postDelayed(checkRunnable, 3500L)
                        }
                    }
                    webViewHolder = this
                    loadDataWithBaseURL("https://krishibondhu.bd", htmlContent, "text/html", "utf-8", null)
                }
            }.getOrElse {
                latestOnAdFailed()
                FrameLayout(ctx)
            }
        }
    )
}
