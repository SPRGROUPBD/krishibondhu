package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.OrderItemEntity
import com.example.data.model.OrderStatus
import com.example.data.model.formatBdt
import com.example.data.model.formatQty
import com.example.ui.components.OrderStatusChip
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.LightGreen
import com.example.ui.theme.PrimaryGreen
import com.example.ui.viewmodel.MarketplaceUiState
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: String,
    uiState: MarketplaceUiState,
    orderItemsFlow: Flow<List<OrderItemEntity>>,
    onBack: () -> Unit,
    onUpdateStatus: (OrderStatus) -> Unit,
    onSubmitReview: (productId: String, rating: Int, comment: String) -> Unit,
    onSubmitComplaint: (subject: String, description: String) -> Unit
) {
    val context = LocalContext.current
    val order = uiState.orders.firstOrNull { it.id == orderId }
    val items by orderItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeUser = uiState.activeUser

    var showReviewDialog by remember { mutableStateOf(false) }
    var showComplaintDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(order?.orderCode ?: "অর্ডার ট্র্যাকিং") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("order_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        }
    ) { padding ->
        if (order == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("অর্ডার পাওয়া যায়নি")
            }
            return@Scaffold
        }

        val currentStatus = runCatching { OrderStatus.valueOf(order.status) }.getOrDefault(OrderStatus.PENDING)
        val isFarmerOwner = activeUser?.id == order.farmerId
        val isBuyerOwner = activeUser?.id == order.buyerId

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Order Status Timeline
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("অর্ডার ট্র্যাকিং", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OrderStatusChip(order.status, uiState.language)
                        }
                        val steps = listOf(
                            OrderStatus.PENDING to "১. অর্ডার পাঠানো হয়েছে",
                            OrderStatus.ACCEPTED to "২. কৃষক গ্রহণ করেছেন",
                            OrderStatus.PROCESSING to "৩. ফসল প্রস্তুত হচ্ছে",
                            OrderStatus.SHIPPED to "৪. ডেলিভারি / সংগ্রহের পথে",
                            OrderStatus.COMPLETED to "৫. সফলভাবে সম্পন্ন"
                        )
                        val activeIndex = when (currentStatus) {
                            OrderStatus.PENDING -> 0
                            OrderStatus.ACCEPTED -> 1
                            OrderStatus.PROCESSING, OrderStatus.READY_FOR_PICKUP -> 2
                            OrderStatus.SHIPPED, OrderStatus.DELIVERED -> 3
                            OrderStatus.COMPLETED -> 4
                            else -> -1
                        }
                        steps.forEachIndexed { idx, (_, label) ->
                            val reached = idx <= activeIndex
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (reached) PrimaryGreen else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (reached) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Items & Payment Transparency
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("পণ্যের তালিকা ও পেমেন্ট", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.productNameBn} (${item.quantity.formatQty()} ${item.unit})")
                                Text(item.lineTotal.formatBdt(), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider()
                        KeyValRow("পণ্য মূল্য", order.productTotal.formatBdt())
                        KeyValRow("ডেলিভারি চার্জ", order.deliveryCharge.formatBdt())
                        KeyValRow("প্ল্যাটফর্ম কমিশন", "৳ ০")
                        KeyValRow("সর্বমোট", order.grandTotal.formatBdt(), bold = true)
                        HorizontalDivider()
                        KeyValRow("পেমেন্ট পদ্ধতি", order.paymentMethod)
                        KeyValRow("পেমেন্ট অবস্থা", order.paymentStatus)
                        if (order.transactionReference.isNotBlank()) {
                            KeyValRow("ট্রানজেকশন রেফারেন্স", order.transactionReference)
                        }
                        KeyValRow("ডেলিভারি ঠিকানা", order.deliveryAddress)
                    }
                }
            }

            // Counterparty Direct Contact
            item {
                val targetName = if (isFarmerOwner) order.buyerName else order.farmerName
                val targetPhone = if (isFarmerOwner) order.buyerPhone else order.farmerPhone
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LightGreen)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isFarmerOwner) "ক্রেতা" else "কৃষক", style = MaterialTheme.typography.labelSmall, color = DarkGreen)
                            Text(targetName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DarkGreen)
                            Text(targetPhone, style = MaterialTheme.typography.bodySmall, color = DarkGreen)
                        }
                        Button(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$targetPhone")))
                            }
                        }) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সরাসরি কল")
                        }
                    }
                }
            }

            // Farmer Status Controls
            if (isFarmerOwner) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("কৃষকের অর্ডার অ্যাকশন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            when (currentStatus) {
                                OrderStatus.PENDING -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { onUpdateStatus(OrderStatus.ACCEPTED) },
                                            modifier = Modifier.weight(1f).testTag("accept_order_button")
                                        ) { Text("অর্ডার গ্রহণ") }
                                        OutlinedButton(
                                            onClick = { onUpdateStatus(OrderStatus.REJECTED) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("প্রত্যাখ্যান") }
                                    }
                                }
                                OrderStatus.ACCEPTED -> {
                                    Button(
                                        onClick = { onUpdateStatus(OrderStatus.PROCESSING) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("প্রস্তুত হচ্ছে চিহ্নিত করুন") }
                                }
                                OrderStatus.PROCESSING -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { onUpdateStatus(OrderStatus.READY_FOR_PICKUP) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("সংগ্রহের জন্য প্রস্তুত") }
                                        Button(
                                            onClick = { onUpdateStatus(OrderStatus.SHIPPED) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("পাঠানো হয়েছে") }
                                    }
                                }
                                OrderStatus.READY_FOR_PICKUP, OrderStatus.SHIPPED -> {
                                    Button(
                                        onClick = { onUpdateStatus(OrderStatus.DELIVERED) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("ডেলিভারি সম্পন্ন চিহ্নিত করুন") }
                                }
                                else -> {
                                    Text("বর্তমান অবস্থা: ${currentStatus.display(uiState.language)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Buyer Completion, Review & Complaint Controls
            if (isBuyerOwner) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (currentStatus == OrderStatus.DELIVERED || currentStatus == OrderStatus.READY_FOR_PICKUP || currentStatus == OrderStatus.SHIPPED) {
                            Button(
                                onClick = { onUpdateStatus(OrderStatus.COMPLETED) },
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("complete_order_button")
                            ) {
                                Text("পণ্য বুঝে পেয়েছি — সম্পন্ন চিহ্নিত করুন")
                            }
                        }
                        if (currentStatus == OrderStatus.PENDING) {
                            OutlinedButton(
                                onClick = { onUpdateStatus(OrderStatus.CANCELLED) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("অর্ডার বাতিল করুন")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (currentStatus == OrderStatus.COMPLETED) {
                                OutlinedButton(
                                    onClick = { showReviewDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("রিভিউ দিন")
                                }
                            }
                            OutlinedButton(
                                onClick = { showComplaintDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("অভিযোগ জানান")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReviewDialog) {
        var rating by remember { mutableIntStateOf(5) }
        var comment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            title = { Text("কৃষককে রিভিউ দিন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { star ->
                            FilterChip(
                                selected = rating == star,
                                onClick = { rating = star },
                                label = { Text("★ $star") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("আপনার অভিজ্ঞতা লিখুন") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val firstProd = items.firstOrNull()?.productId ?: ""
                    onSubmitReview(firstProd, rating, comment.ifBlank { "চমৎকার তাজা ফসল!" })
                    showReviewDialog = false
                }) { Text("জমা দিন") }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDialog = false }) { Text("বাতিল") }
            }
        )
    }

    if (showComplaintDialog) {
        var subject by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showComplaintDialog = false },
            title = { Text("অর্ডার সংক্রান্ত অভিযোগ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("বিষয় *") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("বিস্তারিত বিবরণ *") }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = subject.isNotBlank() && description.isNotBlank(),
                    onClick = {
                        onSubmitComplaint(subject, description)
                        showComplaintDialog = false
                    }
                ) { Text("পাঠান") }
            },
            dismissButton = {
                TextButton(onClick = { showComplaintDialog = false }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun KeyValRow(key: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (bold) PrimaryGreen else MaterialTheme.colorScheme.onSurface
        )
    }
}
