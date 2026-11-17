package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.formatBdt
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState

@Composable
fun HomeScreen(
    uiState: MarketplaceUiState,
    onNavigateToBrowse: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    onFarmerClick: (String) -> Unit,
    onQuickAddToCart: (String) -> Unit,
    onSearchTap: () -> Unit,
    onFindFarmersTap: () -> Unit,
    onStartSellingTap: () -> Unit
) {
    val lang = uiState.language
    val categoryEmojiMap = uiState.categories.associate { it.id to it.emoji }
    val activeProducts = uiState.products.filter { it.status == "ACTIVE" }
    val popularProducts = activeProducts.filter { it.isPopular }.take(6)
    val wholesaleProducts = activeProducts.filter { it.isWholesale }.take(6)
    val retailProducts = activeProducts.filter { !it.isWholesale }.take(6)
    val recentProducts = activeProducts.sortedByDescending { it.createdAt }.take(6)
    val verifiedFarmers = uiState.farmers.filter { it.verificationStatus == "VERIFIED" }.take(6)

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Search Bar
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("home_search_bar")
                    .clickable(onClick = onSearchTap),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "খুঁজুন",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (lang == AppLanguage.BENGALI)
                            "ফসল, জেলা বা কৃষকের নাম খুঁজুন..."
                        else
                            "Search crops, district or farmers...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Hero Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(DarkGreen, PrimaryGreen, Color(0xFF22A058))))
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (lang == AppLanguage.BENGALI) "১০০% দালালমুক্ত বাজার" else "100% Middleman-Free",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HarvestGoldLight,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (lang == AppLanguage.BENGALI)
                                        "কৃষকের ফসল, কৃষকের ন্যায্য দাম — সরাসরি ক্রেতার হাতে।"
                                    else
                                        "Farmer's harvest, farmer's fair price — directly to buyers.",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Image(
                                painter = painterResource(id = R.drawable.krishi_bondhu_logo),
                                contentDescription = "কৃষি বন্ধু ব্যানার",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(82.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroPill("দালালমুক্ত")
                            HeroPill("০% কমিশন")
                            HeroPill("সারা বাংলাদেশ (৬৪ জেলা)")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onNavigateToBrowse,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HarvestGold,
                                    contentColor = TextCharcoal
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("hero_browse_button")
                            ) {
                                Text(
                                    text = if (lang == AppLanguage.BENGALI) "এখনই দেখুন" else "Browse Crops",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = onFindFarmersTap,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("hero_farmers_button")
                            ) {
                                Text(if (lang == AppLanguage.BENGALI) "কৃষক খুঁজুন" else "Find Farmers")
                            }
                        }
                    }
                }
            }
        }

        // 3. Product Categories (24 Dynamic Categories)
        item {
            SectionHeader(
                title = if (lang == AppLanguage.BENGALI) "ক্যাটাগরি সমূহ" else "Categories",
                actionLabel = if (lang == AppLanguage.BENGALI) "সব দেখুন" else "See all",
                onActionClick = onNavigateToBrowse
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.categories.filter { it.isActive }, key = { it.id }) { cat ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(74.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onCategoryClick(cat.id) }
                            .padding(vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = cat.emoji, fontSize = 26.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (lang == AppLanguage.BENGALI) cat.nameBn else cat.nameEn,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Empty state when no products have been published yet
        if (activeProducts.isEmpty()) {
            item {
                EmptyStateView(
                    emoji = "🌾",
                    title = if (lang == AppLanguage.BENGALI) "এখনো কোনো ফসল প্রকাশ করা হয়নি" else "No crops listed yet",
                    subtitle = if (lang == AppLanguage.BENGALI)
                        "কৃষক হিসেবে নিবন্ধন করে আপনার উৎপাদিত প্রথম ফসলটি সরাসরি ক্রেতাদের জন্য প্রকাশ করুন।"
                    else
                        "Register as a farmer to list fresh crops directly for buyers.",
                    actionText = if (lang == AppLanguage.BENGALI) "ফসল বিক্রি শুরু করুন" else "Start Selling",
                    onAction = onStartSellingTap
                )
            }
        } else {
            // 4. Nearby Kushtia Products
            item {
                SectionHeader(
                    title = if (lang == AppLanguage.BENGALI) "কাছাকাছি কৃষকের পণ্য" else "Nearby Produce",
                    actionLabel = if (lang == AppLanguage.BENGALI) "আরও দেখুন" else "More",
                    onActionClick = onNavigateToBrowse
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeProducts.take(8), key = { it.id }) { prod ->
                        ProductCard(
                            product = prod,
                            categoryEmoji = categoryEmojiMap[prod.categoryId] ?: "🌾",
                            language = lang,
                            onClick = { onProductClick(prod.id) },
                            onQuickAddToCart = { onQuickAddToCart(prod.id) },
                            modifier = Modifier.width(205.dp)
                        )
                    }
                }
            }

            if (popularProducts.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = if (lang == AppLanguage.BENGALI) "জনপ্রিয় কৃষিপণ্য" else "Popular Crops",
                        actionLabel = if (lang == AppLanguage.BENGALI) "সব দেখুন" else "See all",
                        onActionClick = onNavigateToBrowse
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(popularProducts, key = { "pop_${it.id}" }) { prod ->
                            ProductCard(
                                product = prod,
                                categoryEmoji = categoryEmojiMap[prod.categoryId] ?: "🌾",
                                language = lang,
                                onClick = { onProductClick(prod.id) },
                                onQuickAddToCart = { onQuickAddToCart(prod.id) },
                                modifier = Modifier.width(205.dp)
                            )
                        }
                    }
                }
            }

            if (wholesaleProducts.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = if (lang == AppLanguage.BENGALI) "পাইকারি ফসল" else "Wholesale Crops",
                        actionLabel = if (lang == AppLanguage.BENGALI) "আরও দেখুন" else "More",
                        onActionClick = onNavigateToBrowse
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(wholesaleProducts, key = { "wh_${it.id}" }) { prod ->
                            ProductCard(
                                product = prod,
                                categoryEmoji = categoryEmojiMap[prod.categoryId] ?: "🌾",
                                language = lang,
                                onClick = { onProductClick(prod.id) },
                                onQuickAddToCart = { onQuickAddToCart(prod.id) },
                                modifier = Modifier.width(205.dp)
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = if (lang == AppLanguage.BENGALI) "খুচরা ও সম্প্রতি যুক্ত ফসল" else "Retail & Recently Listed",
                    actionLabel = if (lang == AppLanguage.BENGALI) "সব ফসল" else "All Crops",
                    onActionClick = onNavigateToBrowse
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    (retailProducts + recentProducts).distinctBy { it.id }.take(5).forEach { prod ->
                        Surface(
                            onClick = { onProductClick(prod.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LightGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(categoryEmojiMap[prod.categoryId] ?: "🌾", fontSize = 26.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == AppLanguage.BENGALI) prod.nameBn else prod.nameEn,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${prod.farmerName} • ${prod.upazila}, ${prod.district}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${prod.pricePerUnit.formatBdt()}/${prod.unit}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (verifiedFarmers.isNotEmpty()) {
            item {
                SectionHeader(
                    title = if (lang == AppLanguage.BENGALI) "যাচাইকৃত কৃষক" else "Verified Farmers",
                    actionLabel = if (lang == AppLanguage.BENGALI) "সব কৃষক" else "All Farmers",
                    onActionClick = onFindFarmersTap
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(verifiedFarmers, key = { it.id }) { farmer ->
                        val count = uiState.products.count { it.farmerId == farmer.id }
                        Card(
                            modifier = Modifier
                                .width(220.dp)
                                .clickable { onFarmerClick(farmer.id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(LightGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🧑‍🌾", fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = farmer.fullName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Filled.Verified,
                                                contentDescription = "যাচাইকৃত",
                                                tint = PrimaryGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "${farmer.upazila}, ${farmer.district}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = farmer.farmBio,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "★ ${farmer.rating} • $count টি ফসল",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            AdBannerSection(
                adConfig = uiState.adConfig,
                placementTag = "home",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = HarvestGold,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
