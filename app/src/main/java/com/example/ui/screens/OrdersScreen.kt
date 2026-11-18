package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.OrderStatus
import com.example.data.model.UserRole
import com.example.data.model.formatBdt
import com.example.ui.components.AdBannerSection
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OrderStatusChip
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.HarvestGoldLight
import com.example.ui.theme.LightGreen
import com.example.ui.viewmodel.MarketplaceUiState

@Composable
fun OrdersScreen(
    uiState: MarketplaceUiState,
    onOrderClick: (String) -> Unit,
    onGoToCart: () -> Unit,
    onBrowseClick: () -> Unit
) {
    val lang = uiState.language
    val activeUser = uiState.activeUser
    val isFarmer = activeUser?.role == UserRole.FARMER.name
    val isBuyer = activeUser?.role == UserRole.BUYER.name
    val isAdmin = activeUser?.role == UserRole.ADMIN.name

    val farmerIncomingOrders = remember(uiState.orders, activeUser) {
        if (activeUser == null) emptyList() else uiState.orders.filter { it.farmerId == activeUser.id }
    }
    val buyerPurchaseOrders = remember(uiState.orders, activeUser) {
        if (activeUser == null) emptyList() else uiState.orders.filter { it.buyerId == activeUser.id }
    }

    var showIncomingForFarmer by remember(isFarmer) { mutableStateOf(isFarmer) }
    var farmerPendingOnly by remember { mutableStateOf(false) }

    val visibleOrders = remember(
        uiState.orders,
        activeUser,
        showIncomingForFarmer,
        farmerPendingOnly,
        farmerIncomingOrders,
        buyerPurchaseOrders
    ) {
        when {
            activeUser == null -> emptyList()
            isAdmin -> uiState.orders
            isFarmer && showIncomingForFarmer -> if (farmerPendingOnly) {
                farmerIncomingOrders.filter { it.status == OrderStatus.PENDING.name }
            } else {
                farmerIncomingOrders
            }
            else -> buyerPurchaseOrders
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(
                when {
                    isFarmer -> "orders_screen_farmer"
                    isBuyer -> "orders_screen_buyer"
                    else -> "orders_screen_guest"
                }
            )
    ) {
        // Role-specific Header Strip
        Surface(
            color = if (isFarmer) LightGreen else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = when {
                        isFarmer -> if (lang == AppLanguage.BENGALI)
                            "👨‍🌾 কৃষকের প্রাপ্ত অর্ডার ব্যবস্থাপনা (${farmerIncomingOrders.size})"
                        else
                            "👨‍🌾 Incoming Buyer Orders (${farmerIncomingOrders.size})"
                        isBuyer -> if (lang == AppLanguage.BENGALI)
                            "🛍️ আমার ক্রয় অর্ডার ও ট্র্যাকিং (${buyerPurchaseOrders.size})"
                        else
                            "🛍️ My Purchase Orders (${buyerPurchaseOrders.size})"
                        isAdmin -> "🛡️ সকল প্ল্যাটফর্ম অর্ডার (${uiState.orders.size})"
                        else -> "📦 অর্ডার ট্র্যাকিং"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
                Text(
                    text = when {
                        isFarmer -> "ক্রেতার পাঠানো নতুন অর্ডার গ্রহণ, প্রস্তুত এবং ডেলিভারি স্ট্যাটাস আপডেট করুন।"
                        isBuyer -> "আপনার ক্রয়কৃত ফসলের রিয়েল-টাইম ডেলিভারি ও পেমেন্ট অবস্থা দেখুন।"
                        isAdmin -> "সারা বাংলাদেশের সকল অর্ডার তদারকি করুন।"
                        else -> "আপনার অর্ডার দেখতে লগইন বা নিবন্ধন করুন।"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Cart Quick Entry Banner for Buyers & Guests (or Farmers only if they explicitly have cart items)
        if (uiState.cartItems.isNotEmpty() && (!isFarmer || uiState.cartItems.isNotEmpty())) {
            Surface(
                onClick = onGoToCart,
                color = HarvestGoldLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("orders_cart_banner"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "কার্টে ${uiState.cartItems.size}টি পণ্য রয়েছে — চেকআউট করুন",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("দেখুন →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isFarmer) {
            val pendingCount = farmerIncomingOrders.count { it.status == OrderStatus.PENDING.name }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showIncomingForFarmer && !farmerPendingOnly,
                    onClick = {
                        showIncomingForFarmer = true
                        farmerPendingOnly = false
                    },
                    label = { Text("সব প্রাপ্ত (${farmerIncomingOrders.size})") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = showIncomingForFarmer && farmerPendingOnly,
                    onClick = {
                        showIncomingForFarmer = true
                        farmerPendingOnly = true
                    },
                    label = { Text("অপেক্ষমাণ ($pendingCount)") },
                    modifier = Modifier.weight(1f)
                )
                if (buyerPurchaseOrders.isNotEmpty()) {
                    FilterChip(
                        selected = !showIncomingForFarmer,
                        onClick = { showIncomingForFarmer = false },
                        label = { Text("ক্রয় (${buyerPurchaseOrders.size})") },
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }
        }

        if (visibleOrders.isEmpty()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    EmptyStateView(
                        emoji = "📦",
                        title = if (lang == AppLanguage.BENGALI) "এখনো কোনো অর্ডার নেই" else "No orders yet",
                        subtitle = when {
                            isFarmer -> "ক্রেতারা আপনার ফসল অর্ডার করলে এখানে সাথে সাথে দেখাবে।"
                            isBuyer -> "সরাসরি যাচাইকৃত কৃষকের কাছ থেকে তাজা ফসল অর্ডার করুন।"
                            else -> "সরাসরি কৃষকের কাছ থেকে ফসল কিনুন বা ফসল বিক্রি করুন।"
                        },
                        actionText = if (isFarmer) null else if (lang == AppLanguage.BENGALI) "ফসল দেখুন" else "Browse Crops",
                        onAction = if (isFarmer) null else onBrowseClick
                    )
                }
                item {
                    AdBannerSection(
                        adConfig = uiState.adConfig,
                        placementTag = "orders"
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleOrders, key = { it.id }) { order ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("order_card_${order.id}")
                            .clickable { onOrderClick(order.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = order.orderCode,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OrderStatusChip(order.status, lang)
                            }
                            Text(
                                text = order.itemsSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isFarmer && showIncomingForFarmer)
                                    "ক্রেতা: ${order.buyerName} (${order.upazila})"
                                else
                                    "কৃষক: ${order.farmerName} (${order.upazila})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider()
                            val due = kotlin.math.max(0.0, order.grandTotal - order.verifiedAdvanceTotal)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "নিশ্চিত অগ্রিম: ${order.verifiedAdvanceTotal.formatBdt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "বকেয়া: ${due.formatBdt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = order.grandTotal.formatBdt(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (order.farmerPaymentConfirmed) LightGreen else HarvestGoldLight,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (order.farmerPaymentConfirmed) "✓ কৃষক কর্তৃক অনুমোদিত" else "যাচাই / অনুমোদন অপেক্ষমাণ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (order.farmerPaymentConfirmed) DarkGreen else androidx.compose.ui.graphics.Color(0xFF92400E),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "সরাসরি পেমেন্ট (${order.paymentMethod})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    AdBannerSection(
                        adConfig = uiState.adConfig,
                        placementTag = "orders"
                    )
                }
            }
        }
    }
}
