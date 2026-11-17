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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
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
import com.example.data.local.UserEntity
import com.example.data.model.AppLanguage
import com.example.data.model.OrderStatus
import com.example.data.model.UserRole
import com.example.data.model.formatBdt
import com.example.data.model.formatQty
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
    onStartSellingTap: () -> Unit,
    onNavigateToOrders: () -> Unit = {},
    onOrderClick: (String) -> Unit = {},
    onOpenCart: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {},
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
            onProductClick = onProductClick
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
        onRefreshSync = onRefreshSync
    )
}

@Composable
private fun FarmerHomeWorkspace(
    farmer: UserEntity,
    uiState: MarketplaceUiState,
    onAddOrManageCrops: () -> Unit,
    onViewOrders: () -> Unit,
    onBrowseMarket: () -> Unit,
    onOrderClick: (String) -> Unit,
    onProductClick: (String) -> Unit
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
            items(myProducts.take(5), key = { "fprod_${it.id}" }) { prod ->
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
            AdBannerSection(
                adConfig = uiState.adConfig,
                placementTag = "farmer_home"
            )
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
    onRefreshSync: () -> Unit
) {
    val lang = uiState.language
    val activeUser = uiState.activeUser
    val isBuyer = uiState.activeRole == UserRole.BUYER
    val isAdmin = uiState.isSuperAdmin
    val categoryEmojiMap = uiState.categories.associate { it.id to it.emoji }
    val activeProducts = uiState.products.filter { it.status == "ACTIVE" }
    val popularProducts = activeProducts.filter { it.isPopular }.take(6)
    val wholesaleProducts = activeProducts.filter { it.isWholesale }.take(6)
    val retailProducts = activeProducts.filter { !it.isWholesale }.take(6)
    val recentProducts = activeProducts.sortedByDescending { it.createdAt }.take(6)
    val verifiedFarmers = uiState.farmers.filter { it.verificationStatus == "VERIFIED" }.take(6)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(if (isBuyer) "buyer_home_marketplace" else "guest_home_marketplace"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Admin quick shortcut banner
        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
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
                            Text("কৃষক ও ক্রেতা যাচাই, বিজ্ঞাপন ও অর্ডার তদারকি করুন", style = MaterialTheme.typography.bodySmall)
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
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
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

        // 1. Search Bar
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
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
                                        text = when {
                                            isBuyer -> if (lang == AppLanguage.BENGALI) "সরাসরি খামার থেকে কিনুন" else "Direct Farm Purchase"
                                            else -> if (lang == AppLanguage.BENGALI) "১০০% দালালমুক্ত বাজার" else "100% Middleman-Free"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HarvestGoldLight,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isBuyer) {
                                        if (lang == AppLanguage.BENGALI)
                                            "টাটকা ফসল কিনুন সরাসরি যাচাইকৃত কৃষকের কাছ থেকে।"
                                        else
                                            "Buy fresh harvest directly from verified farmers."
                                    } else {
                                        if (lang == AppLanguage.BENGALI)
                                            "কৃষকের ফসল, কৃষকের ন্যায্য দাম — সরাসরি ক্রেতার হাতে।"
                                        else
                                            "Farmer's harvest, farmer's fair price — directly to buyers."
                                    },
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
                                    text = if (lang == AppLanguage.BENGALI) "ফসল দেখুন" else "Browse Crops",
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

        // Empty / syncing state when no products have been loaded yet
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
            // 4. Nearby Products
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
                placementTag = if (isBuyer) "buyer_home" else "home",
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
