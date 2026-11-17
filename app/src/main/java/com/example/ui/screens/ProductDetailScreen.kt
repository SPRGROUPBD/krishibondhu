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
    onGoToCart: () -> Unit
) {
    val context = LocalContext.current
    val lang = uiState.language
    val product = uiState.products.firstOrNull { it.id == productId }
    val farmer = uiState.users.firstOrNull { it.id == product?.farmerId }
    val category = uiState.categories.firstOrNull { it.id == product?.categoryId }
    val reviews = uiState.reviews.filter { it.productId == productId || it.farmerId == product?.farmerId }

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
                    IconButton(onClick = onGoToCart) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = "কার্ট")
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
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
                            text = "/ ${product.unit}",
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
                            text = "০% দালাল কমিশন — এই দাম সরাসরি কৃষক নিজেই নির্ধারণ করেছেন।",
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
                            DetailRow("মজুত পরিমাণ", "${product.availableQuantity.formatQty()} ${product.unit}")
                            DetailRow("সংগ্রহের তারিখ", product.harvestDate)
                            DetailRow("প্রাপ্যতার তারিখ", product.availabilityDate)
                            DetailRow("এলাকা", "${product.village}, ${product.unionName}, ${product.upazila}, ${product.district}")
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
                    // Farmer card with direct call/SMS
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
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_DIAL, "tel:${farmer.phone}".toUri())
                                                context.startActivity(intent)
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
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_SENDTO, "smsto:${farmer.phone}".toUri())
                                                context.startActivity(intent)
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
                    // Quantity Picker + Add to Cart
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
                                    text = "${qty.formatQty()} ${product.unit}",
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
