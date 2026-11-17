package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.UserRole
import com.example.data.model.formatBdt
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OrderStatusChip
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
    val isAdmin = activeUser?.role == UserRole.ADMIN.name

    var showIncomingForFarmer by remember(isFarmer) { mutableStateOf(isFarmer) }

    val visibleOrders = remember(uiState.orders, activeUser, showIncomingForFarmer) {
        when {
            activeUser == null -> emptyList()
            isAdmin -> uiState.orders
            showIncomingForFarmer -> uiState.orders.filter { it.farmerId == activeUser.id }
            else -> uiState.orders.filter { it.buyerId == activeUser.id }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Cart Quick Entry Banner if items exist
        if (uiState.cartItems.isNotEmpty()) {
            Surface(
                onClick = onGoToCart,
                color = LightGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = showIncomingForFarmer,
                    onClick = { showIncomingForFarmer = true },
                    label = { Text("কৃষকের প্রাপ্ত অর্ডার") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !showIncomingForFarmer,
                    onClick = { showIncomingForFarmer = false },
                    label = { Text("আমার ক্রয় অর্ডার") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (visibleOrders.isEmpty()) {
            EmptyStateView(
                emoji = "📦",
                title = if (lang == AppLanguage.BENGALI) "এখনো কোনো অর্ডার নেই" else "No orders yet",
                subtitle = if (lang == AppLanguage.BENGALI)
                    "সরাসরি কৃষকের কাছ থেকে ফসল কিনুন বা ফসল বিক্রি করুন।"
                else
                    "Browse fresh Kushtia crops to place your first direct order.",
                actionText = if (lang == AppLanguage.BENGALI) "ফসল দেখুন" else "Browse Crops",
                onAction = onBrowseClick
            )
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
                                        Icons.Filled.ReceiptLong,
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
                                text = if (showIncomingForFarmer)
                                    "ক্রেতা: ${order.buyerName} (${order.upazila})"
                                else
                                    "কৃষক: ${order.farmerName} (${order.upazila})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "পেমেন্ট: ${order.paymentMethod} (${order.paymentStatus})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = order.grandTotal.formatBdt(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
