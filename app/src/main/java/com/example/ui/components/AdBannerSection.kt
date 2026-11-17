package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.R
import com.example.ads.AdMonetizationManager
import com.example.data.model.AdConfig
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.HarvestGoldLight
import com.example.ui.theme.LightGreen
import com.example.ui.theme.PrimaryGreen
import com.example.util.NetworkMonitor
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdBannerSection(
    adConfig: AdConfig,
    modifier: Modifier = Modifier,
    placementTag: String = "banner"
) {
    if (!adConfig.adsEnabled) return

    val context = LocalContext.current
    val priority = adConfig.mobileAdNetworkPriority.trim().ifBlank { "ALL" }
    fun allows(networkKey: String): Boolean =
        priority.equals("ALL", ignoreCase = true) || priority.equals(networkKey, ignoreCase = true)

    val resolvedAdMobUnit = adConfig.androidBannerId.trim()
        .ifBlank { stringResource(id = R.string.admob_banner_unit_id) }

    val webNetworkAd = remember(adConfig, placementTag, priority) {
        buildMobileNetworkHtmlPayload(adConfig, placementTag, priority)
    }
    val isOnline = remember(context) {
        AdMonetizationManager.isTestEnvironment() || NetworkMonitor.isCurrentlyOnline(context)
    }
    val showAdMob = adConfig.androidAdsEnabled &&
        resolvedAdMobUnit.isNotBlank() &&
        allows("ADMOB") &&
        (AdMonetizationManager.isTestEnvironment() || (isOnline && AdMonetizationManager.isPlayServicesAvailable(context)))
    val showMobileWebAd = adConfig.mobileWebAdsEnabled && webNetworkAd != null &&
        (AdMonetizationManager.isTestEnvironment() || isOnline)
    val showSponsor = adConfig.sponsorEnabled &&
        adConfig.sponsorTitle.isNotBlank() &&
        (allows("SPONSOR") || (!showAdMob && !showMobileWebAd))

    val directLink = adConfig.mobileDirectLinkUrl.trim()
        .takeIf { it.startsWith("https://") || it.startsWith("http://") }

    if (!showSponsor && !showAdMob && !showMobileWebAd && directLink == null) return

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

        // 2. Multi-Network Mobile WebView Ad Bridge (collapses cleanly if ad network fails to load)
        if (showMobileWebAd && webNetworkAd != null) {
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

        // 3. Google AdMob Native Banner (+ AdMob Mediation Networks: Meta, Unity, AppLovin, Start.io)
        if (showAdMob) {
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
                            text = "Sponsored Ad • AdMob & Mediation",
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
                                    text = "AdMob Banner ($placementTag)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DarkGreen
                                )
                            }
                        }
                    } else {
                        key(resolvedAdMobUnit) {
                            AndroidView(
                                factory = { ctx ->
                                    AdView(ctx).apply {
                                        setAdSize(AdSize.BANNER)
                                        adUnitId = resolvedAdMobUnit
                                        loadAd(AdRequest.Builder().build())
                                    }
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
    onAdFailed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
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
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            }
                            return true
                        }
                        return true
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            onAdFailed()
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        if (request?.isForMainFrame == true) {
                            onAdFailed()
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.cancel()
                        onAdFailed()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.postDelayed({
                            view.evaluateJavascript(
                                "(function(){var b=document.body;if(!b)return '0';var h=b.querySelector('img,iframe,ins,a,object,embed');return (h||(b.innerText||'').trim().length>0)?'1':'0';})()"
                            ) { res ->
                                if (res == "\"0\"" || res == "0") {
                                    onAdFailed()
                                }
                            }
                        }, 3500L)
                    }
                }
                loadDataWithBaseURL("https://krishibondhu.bd", htmlContent, "text/html", "utf-8", null)
            }
        }
    )
}
