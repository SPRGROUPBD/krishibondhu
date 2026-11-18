package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.data.model.AppLanguage
import com.example.data.model.UserRole
import com.example.data.model.formatBdt
import com.example.data.model.formatQty
import com.example.ui.components.AdBannerSection
import com.example.ui.components.ProductVisualArtwork
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onFarmerClick: (String) -> Unit,
    onAddToCart: (Double) -> Unit,
    onGoToCart: () -> Unit,
    onOpenActivation: () -> Unit = {},
    onRequireActiveAccount: () -> Unit = {}
) {
    val context = LocalContext.current
    val lang = uiState.language
    val product = uiState.products.firstOrNull { it.id == productId }
    val farmer = uiState.users.firstOrNull { it.id == product?.farmerId }
    val category = uiState.categories.firstOrNull { it.id == product?.categoryId }
    val reviews = uiState.reviews.filter { it.productId == productId || it.farmerId == product?.farmerId }
    val activeUser = uiState.activeUser
    val isUserInactive = activeUser != null && !activeUser.isMarketplaceActive()

    var qty by remember(productId, product?.availableQuantity) {
        val avail = product?.availableQuantity ?: 1.0
        mutableDoubleStateOf(if (avail > 0.0 && avail < 1.0) avail else 1.0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product?.nameBn ?: "পণ্যের বিবরণ", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    if (uiState.activeRole != UserRole.FARMER || uiState.cartBadgeCount > 0) {
                        IconButton(onClick = onGoToCart) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = "কার্ট")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (product == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("পণ্যটি পাওয়া যায়নি")
            }
            return@Scaffold
        }

        val isOwnerOrAdmin = (activeUser != null && activeUser.id == product.farmerId) ||
            uiState.activeRole == UserRole.ADMIN
        val isBlockedForPublic = !isOwnerOrAdmin &&
            (product.status == "PENDING_APPROVAL" || product.status == "REJECTED")
        if (isBlockedForPublic) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("⏳", style = MaterialTheme.typography.displayMedium)
                    Text(
                        text = "এই ফসলটি এখনো অ্যাডমিন যাচাইয়ের অপেক্ষায় রয়েছে বা অনুমোদিত নয়।",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "অ্যাডমিন যাচাই ও অনুমোদনের পর সব ক্রেতার জন্য উন্মুক্ত হবে।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onBack) {
                        Text("ফিরে যান")
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                AdBannerSection(
                    adConfig = uiState.adConfig,
                    placementTag = "product_detail_top",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ProductVisualArtwork(
                    product = product,
                    categoryEmoji = category?.emoji ?: "🌾",
                    modifier = Modifier.height(220.dp)
                )
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = LightGreen,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${category?.emoji ?: ""} ${product.categoryNameBn}",
                                style = MaterialTheme.typography.labelMedium,
                                color = DarkGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "ID: ${product.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (lang == AppLanguage.BENGALI) product.nameBn else product.nameEn,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = product.pricePerUnit.formatBdt(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "/ মণ (১ মণ = ${com.example.data.model.ProductUnit.MON_TO_KG} কেজি)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = HarvestGoldLight,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "০% দালাল কমিশন — এই দাম সরাসরি কৃষক নিজেই নির্ধারণ করেছেন। (বাংলাদেশ মানদণ্ড: ১ মণ = ৪০ কেজি)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF78350F),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = product.descriptionBn,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetailRow(
                                "মজুত পরিমাণ",
                                "${product.availableQuantity.formatQty()} মণ (~${(product.availableQuantity * com.example.data.model.ProductUnit.MON_TO_KG).formatQty()} কেজি)"
                            )
                            DetailRow("সংগ্রহের তারিখ", product.harvestDate)
                            DetailRow("প্রাপ্যতার তারিখ", product.availabilityDate)
                            DetailRow(
                                "এলাকা",
                                listOf(product.village, product.unionName, product.upazila, product.district)
                                    .filter { it.isNotBlank() }
                                    .joinToString(", ")
                            )
                            DetailRow("পিকআপ ঠিকানা", product.pickupAddress)
                            DetailRow(
                                "ডেলিভারি সুবিধা",
                                buildString {
                                    if (product.supportsFarmPickup) append("খামার থেকে সংগ্রহ (০৳)")
                                    if (product.supportsFarmPickup && product.supportsDirectDelivery) append(" • ")
                                    if (product.supportsDirectDelivery) append("সরাসরি ডেলিভারি (~${product.deliveryChargeEstimate.formatBdt()})")
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    // Farmer card with direct call/SMS gated on active status
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFarmerClick(product.farmerId) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LightGreen),
                        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = DarkGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = product.farmerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                if (product.farmerVerified) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "যাচাইকৃত",
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${product.upazila}, ${product.district} • প্রোফাইল ও অন্যান্য ফসল দেখুন →",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkGreen
                            )
                            if (farmer != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val callerActive = activeUser?.isMarketplaceActive() == true
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!callerActive) {
                                                onRequireActiveAccount()
                                            } else {
                                                runCatching {
                                                    val intent = Intent(Intent.ACTION_DIAL, "tel:${farmer.phone}".toUri())
                                                    context.startActivity(intent)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("সরাসরি কল")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (!callerActive) {
                                                onRequireActiveAccount()
                                            } else {
                                                runCatching {
                                                    val intent = Intent(Intent.ACTION_SENDTO, "smsto:${farmer.phone}".toUri())
                                                    context.startActivity(intent)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("মেসেজ")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    val isOwnerFarmer = uiState.activeUser?.id == product.farmerId
                    val isFarmerViewer = uiState.activeRole == UserRole.FARMER
                    if (isOwnerFarmer) {
                        Surface(
                            color = LightGreen,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().testTag("owner_farmer_notice")
                        ) {
                            Text(
                                text = "✓ এটি আপনার নিজস্ব তালিকাভুক্ত ফসল। দাম বা মজুত পরিবর্তন করতে 'আমার ফসল' ট্যাবে যান।",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkGreen,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else if (isFarmerViewer) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("farmer_market_viewer_notice")
                        ) {
                            Text(
                                text = "📊 আপনি কৃষক অ্যাকাউন্ট থেকে বাজার দর পর্যবেক্ষণ করছেন।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else if (isUserInactive) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = HarvestGoldLight),
                            border = BorderStroke(1.dp, HarvestGold),
                            modifier = Modifier.fillMaxWidth().testTag("detail_activation_gate")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "অর্ডার ও কার্টে যোগ করতে অ্যাকাউন্ট সক্রিয় করুন (৩০০৳ / ২ বছর)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF78350F)
                                )
                                Button(onClick = onOpenActivation, modifier = Modifier.fillMaxWidth()) {
                                    Text("অ্যাকাউন্ট সক্রিয় / রিনিউ করুন")
                                }
                            }
                        }
                    } else {
                        // Quantity Picker (মণ) + Add to Cart for Active Buyers & Guests
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (qty > 1.0) qty -= 1.0 },
                                        modifier = Modifier.testTag("qty_minus")
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "কমান")
                                    }
                                    Text(
                                        text = "${qty.formatQty()} মণ",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { if (qty + 1.0 <= product.availableQuantity) qty += 1.0 },
                                        modifier = Modifier.testTag("qty_plus")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "বাড়ান")
                                    }
                                }
                            }

                            Button(
                                onClick = { onAddToCart(qty) },
                                enabled = product.availableQuantity >= qty && product.availableQuantity > 0,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("add_to_cart_button")
                            ) {
                                Icon(Icons.Filled.AddShoppingCart, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "কার্টে যোগ (${(product.pricePerUnit * qty).formatBdt()})",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (reviews.isNotEmpty()) {
                item {
                    Text(
                        text = "ক্রেতাদের রিভিউ (${reviews.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(reviews, key = { it.id }) { rev ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(rev.buyerName, fontWeight = FontWeight.Bold)
                                Text("★ ${rev.rating}/5", color = HarvestGold, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(rev.commentBn, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                AdBannerSection(
                    adConfig = uiState.adConfig,
                    placementTag = "product_detail",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f)
        )
    }
}
