package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
    val showSponsor = adConfig.sponsorEnabled && adConfig.sponsorTitle.isNotBlank()
    val showAdMob = adConfig.androidAdsEnabled
    if (!showSponsor && !showAdMob) return

    val context = LocalContext.current
    val resolvedUnitId = adConfig.androidBannerId.trim()
        .ifBlank { stringResource(id = R.string.admob_banner_unit_id) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showSponsor) {
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

                    if (adConfig.sponsorImageUrl.isNotBlank()) {
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

                    val targetUrl = adConfig.sponsorTargetUrl.trim()
                    if (targetUrl.startsWith("https://") || targetUrl.startsWith("http://")) {
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

        if (showAdMob && resolvedUnitId.isNotBlank()) {
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
                            text = "Sponsored Ad • AdMob",
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
                        key(resolvedUnitId) {
                            AndroidView(
                                factory = { ctx ->
                                    AdView(ctx).apply {
                                        setAdSize(AdSize.BANNER)
                                        adUnitId = resolvedUnitId
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
