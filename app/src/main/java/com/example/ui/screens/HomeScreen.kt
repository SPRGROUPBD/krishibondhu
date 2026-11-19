package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.HeroSliderEntity
import com.example.data.local.ProductEntity
import com.example.data.local.UserEntity
import com.example.data.model.AppLanguage
import com.example.data.model.OrderStatus
import com.example.data.model.UserRole
import com.example.data.model.formatBdt
import com.example.data.model.formatQty
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.MediaCaptureHelper

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
    onStartSellingTap: () -> Unit,
    onNavigateToOrders: () -> Unit = {},
    onOrderClick: (String) -> Unit = {},
    onOpenCart: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {},
    onOpenRegisterFarmer: () -> Unit = {},
    onOpenActivation: () -> Unit = {},
    onRefreshSync: () -> Unit = {}
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val activeUser = uiState.activeUser
    if (uiState.activeRole == UserRole.FARMER && activeUser != null) {
        FarmerHomeWorkspace(
            farmer = activeUser,
            uiState = uiState,
            onAddOrManageCrops = onStartSellingTap,
            onViewOrders = onNavigateToOrders,
            onBrowseMarket = onNavigateToBrowse,
            onOrderClick = onOrderClick,
            onProductClick = onProductClick,
            onRefreshSync = onRefreshSync
        )
        return
    }

    MarketplaceHomeContent(
        uiState = uiState,
        onNavigateToBrowse = onNavigateToBrowse,
        onCategoryClick = onCategoryClick,
        onProductClick = onProductClick,
        onFarmerClick = onFarmerClick,
        onQuickAddToCart = onQuickAddToCart,
        onSearchTap = onSearchTap,
        onFindFarmersTap = onFindFarmersTap,
        onStartSellingTap = onStartSellingTap,
        onNavigateToOrders = onNavigateToOrders,
        onOpenCart = onOpenCart,
        onOpenAdminDashboard = onOpenAdminDashboard,
        onOpenRegisterFarmer = onOpenRegisterFarmer,
        onOpenActivation = onOpenActivation,
        onRefreshSync = onRefreshSync
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FarmerHomeWorkspace(
    farmer: UserEntity,
    uiState: MarketplaceUiState,
    onAddOrManageCrops: () -> Unit,
    onViewOrders: () -> Unit,
    onBrowseMarket: () -> Unit,
    onOrderClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    onRefreshSync: () -> Unit
) {
    val lang = uiState.language
    val categoryEmojiMap = uiState.categories.associate { it.id to it.emoji }
    val myProducts = uiState.products.filter { it.farmerId == farmer.id }
    val activeMyProducts = myProducts.filter { it.status == "ACTIVE" }
    val incomingOrders = uiState.orders.filter { it.farmerId == farmer.id }
    val pendingIncoming = incomingOrders.filter { it.status == OrderStatus.PENDING.name }
    val completedIncoming = incomingOrders.filter { it.status == OrderStatus.COMPLETED.name }
    val totalEarnings = incomingOrders
        .filter { it.status != OrderStatus.CANCELLED.name && it.status != OrderStatus.REJECTED.name }
        .sumOf { it.productTotal }

    PullToRefreshBox(
        isRefreshing = uiState.isSyncing,
        onRefresh = onRefreshSync,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("farmer_home_workspace"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Farmer Workspace Hero + KPIs
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(DarkGreen, PrimaryGreen, Color(0xFF1F9D55))))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                        text = if (lang == AppLanguage.BENGALI) "👨‍🌾 কৃষকের খামার ড্যাশবোর্ড" else "👨‍🌾 Farmer Studio",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HarvestGoldLight,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = farmer.fullName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (farmer.verificationStatus == "VERIFIED") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Verified,
                                            contentDescription = "যাচাইকৃত",
                                            tint = Color(0xFF4ADE80),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${farmer.upazila}, ${farmer.district} • ০% কমিশন সরাসরি বিক্রি",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LightGreen
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                color = if (farmer.verificationStatus == "VERIFIED") Color(0xFF16A34A) else HarvestGold,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = when (farmer.verificationStatus) {
                                        "VERIFIED" -> "✓ যাচাইকৃত"
                                        "REJECTED" -> "প্রত্যাখ্যাত"
                                        else -> "যাচাই চলমান"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (farmer.verificationStatus == "VERIFIED") Color.White else TextCharcoal,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WorkspaceStatTile(
                                label = if (lang == AppLanguage.BENGALI) "সক্রিয় ফসল" else "Active Crops",
                                value = "${activeMyProducts.size}",
                                modifier = Modifier.weight(1f)
                            )
                            WorkspaceStatTile(
                                label = if (lang == AppLanguage.BENGALI) "নতুন অর্ডার" else "Pending",
                                value = "${pendingIncoming.size}",
                                modifier = Modifier.weight(1f)
                            )
                            WorkspaceStatTile(
                                label = if (lang == AppLanguage.BENGALI) "সম্পন্ন" else "Completed",
                                value = "${completedIncoming.size}",
                                modifier = Modifier.weight(1f)
                            )
                            WorkspaceStatTile(
                                label = if (lang == AppLanguage.BENGALI) "মোট বিক্রি" else "Revenue",
                                value = totalEarnings.formatBdt(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Farmer Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAddOrManageCrops,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("farmer_home_add_crop_btn")
                    ) {
                        Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == AppLanguage.BENGALI) "ফসল যোগ ও দাম" else "Add / Manage",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = onViewOrders,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("farmer_home_orders_btn")
                    ) {
                        Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == AppLanguage.BENGALI) "অর্ডার (${pendingIncoming.size})" else "Orders (${pendingIncoming.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                AdBannerSection(
                    adConfig = uiState.adConfig,
                    placementTag = "farmer_home_top"
                )
            }

            // 3. Pending Orders Requiring Farmer Action
            item {
                SectionHeader(
                    title = if (lang == AppLanguage.BENGALI)
                        "ক্রেতার অপেক্ষমাণ অর্ডার (${pendingIncoming.size})"
                    else
                        "Pending Buyer Orders (${pendingIncoming.size})",
                    actionLabel = if (lang == AppLanguage.BENGALI) "সব অর্ডার" else "All Orders",
                    onActionClick = onViewOrders,
                    modifier = Modifier.padding(horizontal = 0.dp)
                )
            }

            if (pendingIncoming.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📦", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "কোনো অপেক্ষমাণ অর্ডার নেই",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ক্রেতারা আপনার ফসল অর্ডার করলে তাৎক্ষণিক এখানে দেখাবে।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(pendingIncoming.take(4), key = { "fpend_${it.id}" }) { order ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOrderClick(order.id) }
                            .testTag("farmer_pending_order_${order.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = HarvestGoldLight.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, HarvestGold)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(order.orderCode, fontWeight = FontWeight.Bold, color = DarkGreen)
                                OrderStatusChip(order.status, lang)
                            }
                            Text(order.itemsSummary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ক্রেতা: ${order.buyerName} (${order.upazila})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = order.grandTotal.formatBdt(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreen
                                )
                            }
                        }
                    }
                }
            }

            item {
                AdBannerSection(
                    adConfig = uiState.adConfig,
                    placementTag = "farmer_home_mid"
                )
            }

            // 4. Farmer's Listed Crops Snapshot
            item {
                SectionHeader(
                    title = if (lang == AppLanguage.BENGALI)
                        "আমার তালিকাভুক্ত ফসল (${myProducts.size})"
                    else
                        "My Listed Crops (${myProducts.size})",
                    actionLabel = if (lang == AppLanguage.BENGALI) "সম্পাদনা ও যোগ" else "Manage",
                    onActionClick = onAddOrManageCrops,
                    modifier = Modifier.padding(horizontal = 0.dp)
                )
            }

            if (myProducts.isEmpty()) {
                item {
                    EmptyStateView(
                        emoji = "🌱",
                        title = "আপনার কোনো ফসল তালিকাভুক্ত নেই",
                        subtitle = "সরাসরি ক্রেতাদের কাছে ০% কমিশনে বিক্রি করতে প্রথম ফসলটি যুক্ত করুন।",
                        actionText = "+ নতুন ফসল যোগ করুন",
                        onAction = onAddOrManageCrops
                    )
                }
            } else {
                val previewCrops = myProducts.take(6)
                itemsIndexed(previewCrops, key = { _, prod -> "fprod_${prod.id}" }) { idx, prod ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProductClick(prod.id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LightGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(categoryEmojiMap[prod.categoryId] ?: "🌾", fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.nameBn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "মজুত: ${prod.availableQuantity.formatQty()} ${prod.unit} • ${prod.status}",
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
                        if ((idx + 1) % 2 == 0 && idx < previewCrops.lastIndex) {
                            AdBannerSection(
                                adConfig = uiState.adConfig,
                                placementTag = "farmer_crops_row_${idx + 1}"
                            )
                        }
                    }
                }
            }

            // 5. Market Price Reference Link for Farmers
            item {
                OutlinedCard(
                    onClick = onBrowseMarket,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "সারা দেশের বর্তমান বাজার দর দেখুন",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "অন্যান্য জেলায় কৃষকরা কী দামে ফসল বিক্রি করছেন তা দেখে আপনার দাম ঠিক করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                RewardedMarketTipCard(
                    adConfig = uiState.adConfig,
                    dailyTips = uiState.dailyTips
                )
            }

            item {
                AdBannerSection(
                    adConfig = uiState.adConfig,
                    placementTag = "farmer_home"
                )
            }
        }
    }
}

@Composable
private fun WorkspaceStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LightGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketplaceHomeContent(
    uiState: MarketplaceUiState,
    onNavigateToBrowse: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    onFarmerClick: (String) -> Unit,
    onQuickAddToCart: (String) -> Unit,
    onSearchTap: () -> Unit,
    onFindFarmersTap: () -> Unit,
    onStartSellingTap: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onOpenCart: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenRegisterFarmer: () -> Unit,
    onOpenActivation: () -> Unit,
    onRefreshSync: () -> Unit
) {
    val context = LocalContext.current
    val lang = uiState.language
    val activeUser = uiState.activeUser
    val isBuyer = uiState.activeRole == UserRole.BUYER
    val isAdmin = uiState.isSuperAdmin
    val categoryEmojiMap = uiState.categories.associate { it.id to it.emoji }
    val farmerPhoneMap = uiState.farmers.associate { it.id to it.phone }
    val activeProducts = uiState.products.filter { it.status == "ACTIVE" }
    val popularProducts = activeProducts.filter { it.isPopular }.take(6)
    val wholesaleProducts = activeProducts.take(6)
    val recentProducts = activeProducts.sortedByDescending { it.createdAt }.take(8)
    val verifiedFarmers = uiState.farmers.filter { it.verificationStatus == "VERIFIED" }.take(6)

    val activeSlides = uiState.heroSliders
        .filter { it.isActive }
        .sortedWith(compareBy<HeroSliderEntity> { it.sortOrder }.thenByDescending { it.updatedAt })
    val slides = activeSlides.ifEmpty {
        listOf(
            HeroSliderEntity(
                id = "default_flagship",
                badgeBn = if (lang == AppLanguage.BENGALI) "১০০% দালালমুক্ত বাজার" else "100% Middleman-Free",
                titleBn = if (lang == AppLanguage.BENGALI) "সরাসরি কৃষকের কাছ থেকে" else "Direct from Farmers",
                subtitleBn = if (lang == AppLanguage.BENGALI) "তাজা ফসল কিনুন" else "Buy Fresh Harvest",
                bullet1Bn = if (lang == AppLanguage.BENGALI) "কৃষকের ন্যায্য দাম" else "Fair farmer prices",
                bullet2Bn = if (lang == AppLanguage.BENGALI) "ক্রেতার ন্যায্য দামে ক্রয়" else "Direct buyer purchase",
                bullet3Bn = if (lang == AppLanguage.BENGALI) "সরাসরি যোগাযোগ" else "Direct communication",
                ctaTextBn = if (lang == AppLanguage.BENGALI) "এখনই অর্ডার করুন" else "Order Now",
                targetRoute = "browse"
            )
        )
    }

    fun dispatchSlideRoute(route: String) {
        when (route.trim().lowercase()) {
            "farmers" -> onFindFarmersTap()
            "sell" -> onStartSellingTap()
            "activation" -> onOpenActivation()
            "register_farmer" -> onOpenRegisterFarmer()
            else -> onNavigateToBrowse()
        }
    }

    // Real DB-derived Trust Metrics (no hardcoded fake counts)
    val verifiedFarmerCount = uiState.farmers.count { it.verificationStatus == "VERIFIED" }
    val buyerCount = uiState.buyers.size
    val completedOrderCount = uiState.orders.count { it.status == OrderStatus.COMPLETED.name }
    val satisfactionPct = if (uiState.reviews.isNotEmpty()) {
        ((uiState.reviews.map { it.rating }.average() / 5.0) * 100.0).toInt().coerceIn(0, 100)
    } else null
    val trustMetrics = buildList {
        if (verifiedFarmerCount > 0) {
            add(Triple(Icons.Filled.Eco, if (lang == AppLanguage.BENGALI) "যাচাইকৃত কৃষক" else "Verified Farmers", "$verifiedFarmerCount"))
        }
        if (buyerCount > 0) {
            add(Triple(Icons.Filled.ShoppingCart, if (lang == AppLanguage.BENGALI) "নিবন্ধিত ক্রেতা" else "Buyers", "$buyerCount"))
        }
        if (completedOrderCount > 0) {
            add(Triple(Icons.Filled.CheckCircle, if (lang == AppLanguage.BENGALI) "সম্পন্ন অর্ডার" else "Completed", "$completedOrderCount"))
        }
        if (satisfactionPct != null) {
            add(Triple(Icons.Filled.ThumbUp, if (lang == AppLanguage.BENGALI) "সন্তুষ্টির হার" else "Satisfaction", "$satisfactionPct%"))
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isSyncing,
        onRefresh = onRefreshSync,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag(if (isBuyer) "buyer_home_marketplace" else "guest_home_marketplace"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Seamless Green Gradient Search & Filter Strip (aligned cleanly beneath TopBar)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .background(Brush.horizontalGradient(listOf(DarkGreen, PrimaryGreen)))
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)
                ) {
                    Surface(
                        onClick = onSearchTap,
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("home_search_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "খুঁজুন",
                                tint = PrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lang == AppLanguage.BENGALI)
                                    "কোন ফসল খুঁজছেন?..."
                                else
                                    "Search crops, district...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = onSearchTap,
                                shape = RoundedCornerShape(22.dp),
                                color = PrimaryGreen
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (lang == AppLanguage.BENGALI) "ফিল্টার" else "Filter",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Admin quick shortcut banner
            if (isAdmin) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = onOpenAdminDashboard)
                            .testTag("home_admin_banner"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = HarvestGoldLight),
                        border = BorderStroke(1.dp, HarvestGold)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = DarkGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("সুপার অ্যাডমিন প্যানেল সক্রিয়", fontWeight = FontWeight.Bold, color = TextCharcoal)
                                Text("হিরো স্লাইডার, কৃষক-ক্রেতা যাচাই, বিজ্ঞাপন ও অর্ডার তদারকি করুন", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DarkGreen)
                        }
                    }
                }
            }

            // Personalized Buyer Welcome Strip
            if (isBuyer && activeUser != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = LightGreen),
                        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (lang == AppLanguage.BENGALI)
                                            "স্বাগতম, ${activeUser.fullName}"
                                        else
                                            "Welcome, ${activeUser.fullName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkGreen
                                    )
                                    if (activeUser.verificationStatus == "VERIFIED") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.Verified,
                                            contentDescription = "যাচাইকৃত ক্রেতা",
                                            tint = PrimaryGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "🛍️ ক্রেতা মার্কেটপ্লেস • ${activeUser.upazila}, ${activeUser.district}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkGreen
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalIconButton(
                                    onClick = onOpenCart,
                                    modifier = Modifier.testTag("buyer_home_cart_btn")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (uiState.cartBadgeCount > 0) {
                                                Badge { Text(uiState.cartBadgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.ShoppingCart, contentDescription = "কার্ট")
                                    }
                                }
                                FilledTonalIconButton(
                                    onClick = onNavigateToOrders,
                                    modifier = Modifier.testTag("buyer_home_orders_btn")
                                ) {
                                    Icon(Icons.Filled.ShoppingBag, contentDescription = "আমার অর্ডার")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Dynamic Hero Carousel (Admin-managed via HeroSliderEntity + Showcase backdrop)
            item {
                val pagerState = rememberPagerState(pageCount = { slides.size })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 12.dp
                    ) { pageIdx ->
                        val slide = slides[pageIdx]
                        HeroCarouselCard(
                            slide = slide,
                            lang = lang,
                            isFirstSlide = pageIdx == 0,
                            onPrimaryClick = { dispatchSlideRoute(slide.targetRoute) },
                            onFindFarmersClick = onFindFarmersTap
                        )
                    }
                    if (slides.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(slides.size) { index ->
                                val selected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(7.dp)
                                        .width(if (selected) 20.dp else 7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) PrimaryGreen else PrimaryGreen.copy(alpha = 0.25f)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // 3. Circular Crop Categories (matching reference circular bordered showcase)
            item {
                val activeCats = uiState.categories.filter { it.isActive }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = if (lang == AppLanguage.BENGALI) "ফসলের ক্যাটাগরি" else "Crop Categories",
                        actionLabel = if (lang == AppLanguage.BENGALI) "সব দেখুন" else "See all",
                        onActionClick = onNavigateToBrowse
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(activeCats, key = { it.id }) { cat ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onCategoryClick(cat.id) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(58.dp),
                                    shape = CircleShape,
                                    color = Color.White,
                                    border = BorderStroke(1.5.dp, PrimaryGreen.copy(alpha = 0.28f)),
                                    shadowElevation = 2.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(LightGreen.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = cat.emoji, fontSize = 26.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (lang == AppLanguage.BENGALI) cat.nameBn else cat.nameEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextCharcoal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            item {
                AdBannerSection(
                    adConfig = uiState.adConfig,
                    placementTag = "home_top",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 4. Empty / Syncing / "আজকের নতুন ফসল" Product Cards
            if (activeProducts.isEmpty()) {
                item {
                    if (uiState.isSyncing) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (lang == AppLanguage.BENGALI)
                                        "সার্ভার থেকে ফসল ও কৃষকের তথ্য আনা হচ্ছে..."
                                    else
                                        "Loading live crops and farmers...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (uiState.syncError != null || !uiState.isOnline) {
                        EmptyStateView(
                            emoji = "📡",
                            title = if (lang == AppLanguage.BENGALI) "সার্ভার থেকে তথ্য লোড হয়নি" else "Could not load server data",
                            subtitle = uiState.syncError ?: if (lang == AppLanguage.BENGALI)
                                "ইন্টারনেট সংযোগ চালু করে পুনরায় লোড করুন।"
                            else
                                "Turn on internet and tap Reload.",
                            actionText = if (lang == AppLanguage.BENGALI) "পুনরায় লোড করুন" else "Reload Data",
                            onAction = onRefreshSync
                        )
                    } else if (isBuyer) {
                        EmptyStateView(
                            emoji = "🌾",
                            title = if (lang == AppLanguage.BENGALI) "এখনো কোনো ফসল প্রকাশ করা হয়নি" else "No crops listed yet",
                            subtitle = if (lang == AppLanguage.BENGALI)
                                "কৃষকরা ফসল যুক্ত করলে এখানে সাথে সাথে দেখাবে। আপনি যাচাইকৃত কৃষক তালিকা দেখতে পারেন বা রিফ্রেশ করুন।"
                            else
                                "Fresh crops from farmers will appear here shortly.",
                            actionText = if (lang == AppLanguage.BENGALI) "রিফ্রেশ করুন" else "Refresh",
                            onAction = onRefreshSync
                        )
                    } else {
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
                }
            } else {
                // "আজকের নতুন ফসল" Showcase Row with "তাজা" pill & Direct Dial "কল করুন"
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Eco,
                                    contentDescription = null,
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = if (lang == AppLanguage.BENGALI) "আজকের নতুন ফসল" else "Today's Fresh Crops",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DarkGreen
                                )
                            }
                            Surface(
                                onClick = onNavigateToBrowse,
                                shape = RoundedCornerShape(20.dp),
                                color = LightGreen,
                                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lang == AppLanguage.BENGALI) "সব দেখুন" else "See All",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = DarkGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = DarkGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentProducts, key = { "fresh_${it.id}" }) { prod ->
                                val phone = farmerPhoneMap[prod.farmerId].orEmpty()
                                FreshCropShowcaseCard(
                                    product = prod,
                                    categoryEmoji = categoryEmojiMap[prod.categoryId] ?: "🌾",
                                    language = lang,
                                    onCardClick = { onProductClick(prod.id) },
                                    onQuickAddToCart = { onQuickAddToCart(prod.id) },
                                    onCallFarmerClick = {
                                        val digits = phone.filter { it.isDigit() || it == '+' }
                                        if (digits.isNotBlank()) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL, "tel:$digits".toUri())
                                                )
                                            }.onFailure {
                                                onProductClick(prod.id)
                                            }
                                        } else {
                                            onProductClick(prod.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Real-Data Farmer Trust & Marketplace Stats Strip
                if (trustMetrics.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = LightGreen),
                            border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.22f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                trustMetrics.forEachIndexed { idx, (icon, label, valueText) ->
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = PrimaryGreen.copy(alpha = 0.14f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = DarkGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = valueText,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = DarkGreen,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (idx < trustMetrics.lastIndex) {
                                        VerticalDivider(
                                            modifier = Modifier.height(28.dp),
                                            color = PrimaryGreen.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Promotional Dark-Green Farmer Registration Banner
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(DarkGreen, PrimaryGreen)))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.krishi_bondhu_showcase),
                                contentDescription = "কৃষি বন্ধু খামার",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 86.dp, height = 66.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == AppLanguage.BENGALI) "কৃষি বন্ধুর সাথে থাকুন" else "Stay with Krishi Bondhu",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (lang == AppLanguage.BENGALI) "কৃষি হোক আরও সমৃদ্ধ" else "Empowering Farmers Direct",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = HarvestGoldLight,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    if (activeUser == null) onOpenRegisterFarmer() else onStartSellingTap()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HarvestGold,
                                    contentColor = TextCharcoal
                                ),
                                shape = RoundedCornerShape(22.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = if (lang == AppLanguage.BENGALI) "এখনই যোগ দিন" else "Join Now",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    AdBannerSection(
                        adConfig = uiState.adConfig,
                        placementTag = "home_crops_mid",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
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
                    val recentPreview = recentProducts.take(6)
                    SectionHeader(
                        title = if (lang == AppLanguage.BENGALI) "সম্প্রতি যুক্ত পাইকারি ফসল" else "Recently Listed Wholesale Crops",
                        actionLabel = if (lang == AppLanguage.BENGALI) "সব ফসল" else "All Crops",
                        onActionClick = onNavigateToBrowse
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        recentPreview.forEachIndexed { idx, prod ->
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
                            if ((idx + 1) % 2 == 0 && idx < recentPreview.lastIndex) {
                                AdBannerSection(
                                    adConfig = uiState.adConfig,
                                    placementTag = "home_recent_crops_row_${idx + 1}"
                                )
                            }
                        }
                    }
                }
            }

            item {
                AdBannerSection(
                    adConfig = uiState.adConfig,
                    placementTag = "home_products_farmers_mid",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (verifiedFarmers.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = if (lang == AppLanguage.BENGALI) "যাচাইকৃত কৃষক" else "Verified Farmers",
                        actionLabel = if (lang == AppLanguage.BENGALI) "সব কৃষক" else "All Farmers",
                        onActionClick = onFindFarmersTap
                    )
                }

                val farmerChunks = verifiedFarmers.take(6).chunked(2)
                farmerChunks.forEachIndexed { chunkIdx, pair ->
                    item(key = "home_farmer_chunk_$chunkIdx") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { farmer ->
                                val count = uiState.products.count { it.farmerId == farmer.id && it.status == "ACTIVE" }
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onFarmerClick(farmer.id) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            UserAvatar(
                                                imageUri = farmer.profileImageUri,
                                                role = farmer.role,
                                                size = 40.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
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
                                        Spacer(modifier = Modifier.height(6.dp))
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
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (chunkIdx < farmerChunks.lastIndex) {
                        item(key = "home_farmer_ad_$chunkIdx") {
                            AdBannerSection(
                                adConfig = uiState.adConfig,
                                placementTag = "home_farmers_mid_${chunkIdx + 1}",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            item {
                RewardedMarketTipCard(
                    adConfig = uiState.adConfig,
                    dailyTips = uiState.dailyTips,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                AdBannerSection(
                    adConfig = uiState.adConfig,
                    placementTag = if (isBuyer) "buyer_home" else "home",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroCarouselCard(
    slide: HeroSliderEntity,
    lang: AppLanguage,
    isFirstSlide: Boolean,
    onPrimaryClick: () -> Unit,
    onFindFarmersClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 216.dp),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val imgModel = MediaCaptureHelper.imageModelFromUri(slide.imageUri)
            if (imgModel != null) {
                AsyncImage(
                    model = imgModel,
                    contentDescription = slide.titleBn,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.krishi_bondhu_showcase),
                    contentDescription = slide.titleBn,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            // Deep dark-green left scrim preserves Bengali readability over showcase/custom photos
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0xEC053A1E),
                                0.62f to Color(0xCE075B32),
                                1.0f to Color(0x55075B32)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (slide.badgeBn.isNotBlank()) {
                    Surface(
                        color = Color(0xFF168447).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        Text(
                            text = slide.badgeBn,
                            style = MaterialTheme.typography.labelSmall,
                            color = HarvestGoldLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = slide.titleBn,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (slide.subtitleBn.isNotBlank()) {
                        Text(
                            text = slide.subtitleBn,
                            style = MaterialTheme.typography.titleMedium,
                            color = HarvestGoldLight,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val bullets = listOf(slide.bullet1Bn, slide.bullet2Bn, slide.bullet3Bn)
                    .filter { it.isNotBlank() }
                if (bullets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        bullets.forEach { bullet ->
                            HeroPill(bullet)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPrimaryClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.2.dp, Color.White),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = if (isFirstSlide) {
                            Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hero_browse_button")
                        } else {
                            Modifier.minimumInteractiveComponentSize()
                        }
                    ) {
                        Text(
                            text = slide.ctaTextBn.ifBlank {
                                if (lang == AppLanguage.BENGALI) "এখনই অর্ডার করুন" else "Order Now"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = onFindFarmersClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.25f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = if (isFirstSlide) {
                            Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hero_farmers_button")
                        } else {
                            Modifier.minimumInteractiveComponentSize()
                        }
                    ) {
                        Text(
                            text = if (lang == AppLanguage.BENGALI) "কৃষক খুঁজুন" else "Find Farmers",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreshCropShowcaseCard(
    product: ProductEntity,
    categoryEmoji: String,
    language: AppLanguage,
    onCardClick: () -> Unit,
    onQuickAddToCart: () -> Unit,
    onCallFarmerClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(172.dp)
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFD9EBDD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
                    .background(LightGreen),
                contentAlignment = Alignment.Center
            ) {
                val imgModel = MediaCaptureHelper.imageModelFromUri(product.imageUri)
                if (imgModel != null) {
                    AsyncImage(
                        model = imgModel,
                        contentDescription = product.nameBn,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = categoryEmoji, fontSize = 40.sp)
                }

                // "তাজা" green pill top-right
                Surface(
                    color = PrimaryGreen,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                ) {
                    Text(
                        text = if (language == AppLanguage.BENGALI) "তাজা" else "Fresh",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (product.farmerVerified) {
                    Surface(
                        color = Color.White.copy(alpha = 0.94f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "যাচাইকৃত",
                            tint = PrimaryGreen,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(14.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (language == AppLanguage.BENGALI) product.nameBn else product.nameEn.ifBlank { product.nameBn },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextCharcoal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.pricePerUnit.formatBdt()}/${product.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryGreen,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = DarkGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${product.upazila}, ${product.district}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "মজুত: ${product.availableQuantity.formatQty()} ${product.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkGreen,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCallFarmerClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) "কল করুন" else "Call",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onQuickAddToCart,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = "কার্টে যোগ করুন",
                            tint = DarkGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4ADE80),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
