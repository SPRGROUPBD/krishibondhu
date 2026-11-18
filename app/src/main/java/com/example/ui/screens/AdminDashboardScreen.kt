package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ads.*
import com.example.data.local.ActivationPaymentEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.PaymentSettingsEntity
import com.example.data.local.ProductEntity
import com.example.data.model.*
import com.example.ui.components.AccountStatusBadge
import com.example.ui.components.AdBannerSection
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatDateBn
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.MediaCaptureHelper
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onVerifyUser: (userId: String, approved: Boolean) -> Unit,
    onApproveActivationPayment: (paymentId: String, notes: String) -> Unit = { _, _ -> },
    onRejectActivationPayment: (paymentId: String, reason: String, requestResubmission: Boolean) -> Unit = { _, _, _ -> },
    onSetAccountStatus: (userId: String, status: AccountStatus, reason: String) -> Unit = { _, _, _ -> },
    onVerifyOrderPayment: (orderId: String, approved: Boolean, notes: String) -> Unit = { _, _, _ -> },
    onSavePaymentSettings: (PaymentSettingsEntity) -> Unit = {},
    onSendNotification: (targetUserId: String, targetRole: String, titleBn: String, messageBn: String) -> Unit,
    onAddCategory: (nameBn: String, nameEn: String, emoji: String) -> Unit,
    onAddLocation: (division: String, district: String, upazila: String, union: String, village: String) -> Unit,
    onResolveComplaint: (complaintId: String) -> Unit,
    onVerifyProduct: (productId: String, approved: Boolean, reason: String) -> Unit = { _, _, _ -> },
    onUpdateProduct: (productId: String, nameBn: String, category: CategoryEntity, pricePerUnit: Double, availableQuantity: Double, descriptionBn: String, status: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteProduct: (productId: String) -> Unit = {},
    onSaveAdConfig: (AdConfig) -> Unit = {},
    onBuildCsvReport: () -> String
) {
    val context = LocalContext.current
    val stats = uiState.adminStats
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "সারসংক্ষেপ",
        "পেমেন্ট যাচাই (${stats.pendingActivationPayments})",
        "কৃষক ও ক্রেতা",
        "ফসল যাচাই (${stats.pendingProductsCount})",
        "অর্ডার ও অভিযোগ (${uiState.orders.size})",
        "পেমেন্ট সেটিংস",
        "নোটিফিকেশন পাঠান",
        "ক্যাটাগরি",
        "এলাকা",
        "অডিট লগ",
        "বিজ্ঞাপন ও সব নেটওয়ার্ক"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("সুপার অ্যাডমিন প্যানেল") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        runCatching {
                            val csv = onBuildCsvReport()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "Krishi Bondhu Bangladesh Sales Report")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "CSV রিপোর্ট শেয়ার করুন"))
                        }
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "CSV রিপোর্ট এক্সপোর্ট")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                color = LightGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f))
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "নীতিমালা: অ্যাডমিন অ্যাকাউন্ট, পেমেন্ট এবং কৃষকের ফসল যাচাই, অনুমোদন, সম্পাদনা ও বাতিল করতে পারেন।",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkGreen
                    )
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs.size) { index ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(tabs[index]) },
                        modifier = Modifier.testTag("admin_tab_$index")
                    )
                }
            }

            when (selectedTab) {
                0 -> AdminOverviewTab(stats = stats, adConfig = uiState.adConfig)
                1 -> AdminActivationPaymentsTab(
                    uiState = uiState,
                    onApprove = onApproveActivationPayment,
                    onReject = onRejectActivationPayment
                )
                2 -> AdminVerificationTab(
                    uiState = uiState,
                    onVerifyUser = onVerifyUser,
                    onSetAccountStatus = onSetAccountStatus
                )
                3 -> AdminProductsTab(
                    uiState = uiState,
                    onVerifyProduct = onVerifyProduct,
                    onUpdateProduct = onUpdateProduct,
                    onDeleteProduct = onDeleteProduct
                )
                4 -> AdminOrdersComplaintsTab(
                    uiState = uiState,
                    onVerifyOrderPayment = onVerifyOrderPayment,
                    onResolveComplaint = onResolveComplaint
                )
                5 -> AdminPaymentSettingsTab(
                    settings = uiState.paymentSettings,
                    onSave = onSavePaymentSettings
                )
                6 -> AdminNotificationsTab(uiState = uiState, onSendNotification = onSendNotification)
                7 -> AdminCategoriesTab(uiState = uiState, onAddCategory = onAddCategory)
                8 -> AdminLocationsTab(uiState = uiState, onAddLocation = onAddLocation)
                9 -> AdminAuditLogTab(uiState = uiState)
                10 -> AdminAdMonetizationTab(adConfig = uiState.adConfig, onSaveAdConfig = onSaveAdConfig)
            }
        }
    }
}

@Composable
private fun AdminOverviewTab(stats: AdminDashboardStats, adConfig: AdConfig) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("অপেক্ষমান অ্যাক্টিভেশন", "${stats.pendingActivationPayments}", Color(0xFFB45309), Modifier.weight(1f))
                AdminKpiCard("অনুমোদিত পেমেন্ট", "${stats.approvedActivationPayments}", PrimaryGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("সক্রিয় অ্যাকাউন্ট", "${stats.activeAccounts}", DarkGreen, Modifier.weight(1f))
                AdminKpiCard("মেয়াদোত্তীর্ণ / স্থগিত", "${stats.expiredAccounts} / ${stats.suspendedAccounts}", AlertRed, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("মোট কৃষক", "${stats.totalFarmers}", PrimaryGreen, Modifier.weight(1f))
                AdminKpiCard("মোট ক্রেতা", "${stats.totalBuyers}", InfoBlue, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("সক্রিয় / যাচাই বাকি ফসল", "${stats.activeProducts} / ${stats.pendingProductsCount}", Color(0xFFB45309), Modifier.weight(1f))
                AdminKpiCard("কৃষক পেমেন্ট অনুমোদন বাকি", "${stats.pendingOrderPaymentVerifications}", InfoBlue, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("সম্পন্ন অর্ডার", "${stats.completedOrders}", DarkGreen, Modifier.weight(1f))
                AdminKpiCard("মোট বিক্রয় (৳)", stats.totalSalesAmount.formatBdt(), PrimaryGreen, Modifier.weight(1f))
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "জেলাভিত্তিক বিক্রয় সারসংক্ষেপ (সারা বাংলাদেশ)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val entries = stats.districtSales.entries.toList()
                    if (entries.isEmpty()) {
                        Text("এখনো বিক্রয় তথ্য নেই", style = MaterialTheme.typography.bodySmall)
                    } else {
                        val maxVal = entries.maxOf { it.value }.coerceAtLeast(1.0)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            val barSpace = size.width / entries.size
                            val barWidth = barSpace * 0.5f
                            entries.forEachIndexed { idx, entry ->
                                val ratio = (entry.value / maxVal).toFloat()
                                val barHeight = (size.height - 16f) * ratio
                                val x = idx * barSpace + (barSpace - barWidth) / 2f
                                val y = size.height - barHeight
                                drawRoundRect(
                                    color = PrimaryGreen,
                                    topLeft = Offset(x, y),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(10f, 10f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        entries.forEach { (district, total) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(district, style = MaterialTheme.typography.bodySmall)
                                Text(total.formatBdt(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            AdBannerSection(
                adConfig = adConfig,
                placementTag = "admin_overview"
            )
        }
    }
}

@Composable
private fun AdminKpiCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun AdminActivationPaymentsTab(
    uiState: MarketplaceUiState,
    onApprove: (paymentId: String, notes: String) -> Unit,
    onReject: (paymentId: String, reason: String, requestResubmission: Boolean) -> Unit
) {
    var statusFilter by remember { mutableStateOf("PENDING") }
    var search by remember { mutableStateOf("") }
    var receiptPreviewUri by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<Pair<ActivationPaymentEntity, String>?>(null) } // entity + "REJECT"/"RESUBMIT"/"APPROVE"

    val txCounts = remember(uiState.activationPayments) {
        uiState.activationPayments
            .groupBy { it.transactionId.trim().uppercase() }
            .mapValues { it.value.size }
    }

    val filteredPayments = remember(uiState.activationPayments, statusFilter, search) {
        uiState.activationPayments.filter { p ->
            val matchesStatus = when (statusFilter) {
                "PENDING" -> p.status == ActivationPaymentStatus.PENDING.name
                "APPROVED" -> p.status == ActivationPaymentStatus.APPROVED.name
                "REJECTED" -> p.status == ActivationPaymentStatus.REJECTED.name || p.status == ActivationPaymentStatus.RESUBMISSION_REQUESTED.name
                else -> true
            }
            val matchesSearch = search.isBlank() ||
                p.userName.contains(search, ignoreCase = true) ||
                p.userPhone.contains(search, ignoreCase = true) ||
                p.transactionId.contains(search, ignoreCase = true) ||
                p.senderMobile.contains(search, ignoreCase = true)
            matchesStatus && matchesSearch
        }.sortedByDescending { it.createdAt }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "অ্যাকাউন্ট অ্যাক্টিভেশন ও রিনিউ পেমেন্ট যাচাই",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("নাম, মোবাইল বা TxID দিয়ে খুঁজুন") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_payments_search")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = statusFilter == "PENDING",
                            onClick = { statusFilter = "PENDING" },
                            label = { Text("অপেক্ষমান (${uiState.activationPayments.count { it.status == "PENDING" }})") },
                            modifier = Modifier.testTag("admin_pay_filter_pending")
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == "APPROVED",
                            onClick = { statusFilter = "APPROVED" },
                            label = { Text("অনুমোদিত (${uiState.activationPayments.count { it.status == "APPROVED" }})") },
                            modifier = Modifier.testTag("admin_pay_filter_approved")
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == "REJECTED",
                            onClick = { statusFilter = "REJECTED" },
                            label = { Text("প্রত্যাখ্যাত") },
                            modifier = Modifier.testTag("admin_pay_filter_rejected")
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == "ALL",
                            onClick = { statusFilter = "ALL" },
                            label = { Text("সব (${uiState.activationPayments.size})") },
                            modifier = Modifier.testTag("admin_pay_filter_all")
                        )
                    }
                }
            }
        }

        if (filteredPayments.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        text = "এই তালিকায় কোনো অ্যাক্টিভেশন পেমেন্ট পাওয়া যায়নি।",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(filteredPayments, key = { it.id }) { pay ->
            val duplicateCount = txCounts[pay.transactionId.trim().uppercase()] ?: 1
            val payStatus = ActivationPaymentStatus.fromName(pay.status)
            val (bg, fg) = when (payStatus) {
                ActivationPaymentStatus.APPROVED -> LightGreen to DarkGreen
                ActivationPaymentStatus.REJECTED, ActivationPaymentStatus.RESUBMISSION_REQUESTED -> AlertRedLight to AlertRed
                ActivationPaymentStatus.PENDING -> HarvestGoldLight to Color(0xFF92400E)
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.testTag("admin_activation_card_${pay.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pay.userName} (${if (pay.accountRole == "FARMER") "কৃষক" else "ক্রেতা"})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("${pay.userPhone} • ${pay.paymentType}", style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = payStatus.display(uiState.language),
                                style = MaterialTheme.typography.labelSmall,
                                color = fg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (duplicateCount > 1) {
                        Surface(color = AlertRedLight, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "⚠ সতর্কতা: ডুপ্লিকেট TxID পাওয়া গেছে (${pay.transactionId})",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlertRed,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "${pay.paymentMethod} -> ${pay.receiverNumber} (${pay.receiverAccountType}) • ${pay.amount.formatBdt()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryGreen
                    )
                    Text("প্রেরক: ${pay.senderMobile} • TxID: ${pay.transactionId}", style = MaterialTheme.typography.bodySmall)
                    Text("তারিখ: ${formatDateBn(pay.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (pay.receiptUri.isNotBlank()) {
                        OutlinedButton(onClick = { receiptPreviewUri = pay.receiptUri }) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পেমেন্ট রসিদ দেখুন")
                        }
                    }

                    if (pay.adminNotes.isNotBlank()) {
                        Text("মন্তব্য: ${pay.adminNotes}", style = MaterialTheme.typography.labelSmall, color = fg)
                    }

                    if (pay.status == ActivationPaymentStatus.PENDING.name) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { actionTarget = pay to "APPROVE" },
                                modifier = Modifier.weight(1f).testTag("admin_approve_pay_${pay.id}")
                            ) { Text("✓ অনুমোদন (২ বছর)") }
                            OutlinedButton(
                                onClick = { actionTarget = pay to "RESUBMIT" },
                                modifier = Modifier.weight(1f)
                            ) { Text("পুনরায় জমা") }
                            OutlinedButton(
                                onClick = { actionTarget = pay to "REJECT" },
                                modifier = Modifier.weight(0.9f).testTag("admin_reject_pay_${pay.id}")
                            ) { Text("প্রত্যাখ্যান") }
                        }
                    }
                }
            }
        }
    }

    receiptPreviewUri?.let { uri ->
        val model = MediaCaptureHelper.imageModelFromUri(uri)
        AlertDialog(
            onDismissRequest = { receiptPreviewUri = null },
            title = { Text("পেমেন্ট রসিদ") },
            text = {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = "রসিদ",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text("রসিদের ছবি পাওয়া যায়নি")
                }
            },
            confirmButton = {
                TextButton(onClick = { receiptPreviewUri = null }) { Text("বন্ধ করুন") }
            }
        )
    }

    actionTarget?.let { (pay, action) ->
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = {
                Text(
                    when (action) {
                        "APPROVE" -> "পেমেন্ট অনুমোদন ও ২ বছর সক্রিয় (${pay.userName})"
                        "RESUBMIT" -> "পুনরায় পেমেন্ট জমার অনুরোধ"
                        else -> "পেমেন্ট প্রত্যাখ্যান"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TxID: ${pay.transactionId} • পরিমাণ: ${pay.amount.formatBdt()}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(if (action == "APPROVE") "অ্যাডমিন নোট (ঐচ্ছিক)" else "কারণ লিখুন *") },
                        modifier = Modifier.fillMaxWidth().testTag("admin_pay_notes_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            "APPROVE" -> onApprove(pay.id, notes)
                            "RESUBMIT" -> onReject(pay.id, notes, true)
                            else -> onReject(pay.id, notes, false)
                        }
                        actionTarget = null
                    },
                    modifier = Modifier.testTag("admin_pay_confirm_action")
                ) {
                    Text("নিশ্চিত করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { actionTarget = null }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun AdminVerificationTab(
    uiState: MarketplaceUiState,
    onVerifyUser: (String, Boolean) -> Unit,
    onSetAccountStatus: (String, AccountStatus, String) -> Unit
) {
    var filterMode by remember { mutableStateOf("ALL") } // ALL, FARMER, BUYER, ACTIVE, EXPIRED, SUSPENDED
    val pendingFarmers = uiState.farmers.count { it.verificationStatus == "PENDING" }
    val pendingBuyers = uiState.buyers.count { it.verificationStatus == "PENDING" }

    val allNonAdmins = remember(uiState.farmers, uiState.buyers) { uiState.farmers + uiState.buyers }
    val visibleUsers = remember(allNonAdmins, filterMode) {
        val pool = when (filterMode) {
            "FARMER" -> allNonAdmins.filter { it.role == "FARMER" }
            "BUYER" -> allNonAdmins.filter { it.role == "BUYER" }
            "ACTIVE" -> allNonAdmins.filter { it.isMarketplaceActive() }
            "EXPIRED" -> allNonAdmins.filter { it.accountStatus == AccountStatus.EXPIRED.name }
            "SUSPENDED" -> allNonAdmins.filter { it.accountStatus == AccountStatus.SUSPENDED.name }
            else -> allNonAdmins
        }
        pool.sortedByDescending { it.createdAt }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "কৃষক ও ক্রেতা অ্যাকাউন্ট লাইফসাইকেল",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
                Text(
                    text = "অপেক্ষমাণ কৃষক: $pendingFarmers জন • অপেক্ষমাণ ক্রেতা: $pendingBuyers জন",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filterMode == "ALL",
                            onClick = { filterMode = "ALL" },
                            label = { Text("সবাই (${allNonAdmins.size})") },
                            modifier = Modifier.testTag("admin_users_filter_all")
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "FARMER",
                            onClick = { filterMode = "FARMER" },
                            label = { Text("কৃষক (${uiState.farmers.size})") },
                            modifier = Modifier.testTag("admin_users_filter_farmers")
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "BUYER",
                            onClick = { filterMode = "BUYER" },
                            label = { Text("ক্রেতা (${uiState.buyers.size})") },
                            modifier = Modifier.testTag("admin_users_filter_buyers")
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "ACTIVE",
                            onClick = { filterMode = "ACTIVE" },
                            label = { Text("সক্রিয়") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "EXPIRED",
                            onClick = { filterMode = "EXPIRED" },
                            label = { Text("মেয়াদোত্তীর্ণ") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterMode == "SUSPENDED",
                            onClick = { filterMode = "SUSPENDED" },
                            label = { Text("স্থগিত") }
                        )
                    }
                }
            }
        }

        if (visibleUsers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "এই তালিকায় কোনো ব্যবহারকারী নেই।",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(visibleUsers, key = { it.id }) { person ->
            val isFarmer = person.role == "FARMER"
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.testTag("admin_user_card_${person.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(imageUri = person.profileImageUri, role = person.role, size = 42.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = person.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isFarmer) "কৃষক (Farmer)" else "ক্রেতা (Buyer)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFarmer) PrimaryGreen else InfoBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        AccountStatusBadge(person.accountStatus, person.expiryDate, uiState.language)
                    }
                    val locLine = listOf(person.unionName, person.upazila, person.district, person.division)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    Text("$locLine • ${person.phone}", style = MaterialTheme.typography.bodySmall)
                    if (person.activationDate > 0L) {
                        Text(
                            text = "সক্রিয়: ${formatDateBn(person.activationDate)} • মেয়াদ শেষ: ${formatDateBn(person.expiryDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryGreen
                        )
                    }
                    if (person.nidMasked.isNotBlank()) {
                        Text("মাস্কড এনআইডি: ${person.nidMasked}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (person.verificationStatus != "VERIFIED") {
                            Button(
                                onClick = { onVerifyUser(person.id, true) },
                                modifier = Modifier.weight(1f).testTag("verify_approve_${person.id}")
                            ) { Text("✓ পরিচয় যাচাই") }
                        }
                        if (person.accountStatus != AccountStatus.SUSPENDED.name) {
                            OutlinedButton(
                                onClick = { onSetAccountStatus(person.id, AccountStatus.SUSPENDED, "অ্যাডমিন কর্তৃক স্থগিত") },
                                modifier = Modifier.weight(1f).testTag("suspend_user_${person.id}")
                            ) { Text("স্থগিত করুন") }
                        } else {
                            Button(
                                onClick = { onSetAccountStatus(person.id, AccountStatus.ACTIVE, "অ্যাডমিন কর্তৃক পুনঃসক্রিয়") },
                                modifier = Modifier.weight(1f).testTag("reactivate_user_${person.id}")
                            ) { Text("পুনঃসক্রিয় করুন") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOrdersComplaintsTab(
    uiState: MarketplaceUiState,
    onVerifyOrderPayment: (orderId: String, approved: Boolean, notes: String) -> Unit,
    onResolveComplaint: (String) -> Unit
) {
    var receiptUriPreview by remember { mutableStateOf<String?>(null) }
    val trackedOrders = remember(uiState.orders) {
        uiState.orders.sortedByDescending { it.createdAt }
    }
    val paymentHistory = remember(uiState.orderPayments) {
        uiState.orderPayments.sortedByDescending { it.createdAt }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = LightGreen),
                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "পাইকারি অর্ডার ও সরাসরি অগ্রিম পেমেন্ট নীতি",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkGreen
                    )
                    Text(
                        text = "সব পণ্য শুধুমাত্র পাইকারি দামে বিক্রি হয়। ক্রেতা সরাসরি কৃষকের বিকাশ/নগদ/রকেট নম্বরে অগ্রিম পাঠান — প্ল্যাটফর্ম বা অ্যাডমিন কোনো টাকা গ্রহণ বা অনুমোদন করে না; শুধুমাত্র কৃষক নিজের অ্যাকাউন্ট যাচাই করে পেমেন্ট নিশ্চিত করেন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkGreen
                    )
                }
            }
        }

        item {
            Text(
                text = "পাইকারি অর্ডার ও অগ্রিম হিসাব (${trackedOrders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkGreen
            )
        }

        if (trackedOrders.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Text(
                        text = "এখনো কোনো অর্ডার নেই।",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(trackedOrders, key = { "opay_${it.id}" }) { ord ->
            val effectiveDue = (ord.grandTotal - ord.verifiedAdvanceTotal).coerceAtLeast(0.0)
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (ord.farmerPaymentConfirmed) PrimaryGreen.copy(alpha = 0.45f) else InfoBlue.copy(alpha = 0.35f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${ord.orderCode} • মোট পাইকারি: ${ord.grandTotal.formatBdt()}", fontWeight = FontWeight.Bold)
                        OrderStatusChip(ord.status, uiState.language)
                    }
                    Text("ক্রেতা: ${ord.buyerName} -> কৃষক: ${ord.farmerName}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "যাচাইকৃত অগ্রিম: ${ord.verifiedAdvanceTotal.formatBdt()} • বাকি বকেয়া: ${effectiveDue.formatBdt()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkGreen
                    )
                    Text(
                        text = "পদ্ধতি: ${ord.paymentMethod} • TxID: ${ord.transactionReference.ifBlank { "—" }} • প্রেরক: ${ord.paymentSenderNumber.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (ord.farmerPaymentConfirmed) "✓ কৃষক অগ্রিম/পেমেন্ট যাচাই করে অর্ডার নিশ্চিত করেছেন" else "⏳ কৃষকের পেমেন্ট যাচাই অপেক্ষমাণ (অ্যাডমিন থেকে পরিবর্তনযোগ্য নয়)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ord.farmerPaymentConfirmed) PrimaryGreen else Color(0xFFB45309),
                        fontWeight = FontWeight.Bold
                    )
                    if (ord.paymentReceiptUri.isNotBlank()) {
                        OutlinedButton(onClick = { receiptUriPreview = ord.paymentReceiptUri }) {
                            Text("রসিদ দেখুন")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "অগ্রিম ও আংশিক পেমেন্ট অডিট লগ (${paymentHistory.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkGreen
            )
        }
        if (paymentHistory.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Text("কোনো অগ্রিম পেমেন্ট লগ এখনো নেই।", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            items(paymentHistory.take(40), key = { "op_audit_${it.id}" }) { pay ->
                val statusColor = when (pay.status) {
                    OrderPaymentStatus.CONFIRMED.name -> PrimaryGreen
                    OrderPaymentStatus.REJECTED.name -> AlertRed
                    else -> Color(0xFFB45309)
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${pay.orderCode} • ${pay.amount.formatBdt()} (${pay.paymentMethod})", fontWeight = FontWeight.Bold)
                            Text(pay.status, color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${pay.buyerName} -> ${pay.farmerName} • TxID: ${pay.transactionId}", style = MaterialTheme.typography.bodySmall)
                        if (pay.receiverNumber.isNotBlank() || pay.senderNumber.isNotBlank()) {
                            Text("প্রাপক (কৃষক): ${pay.receiverNumber.ifBlank { "—" }} • প্রেরক: ${pay.senderNumber.ifBlank { "—" }}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (pay.rejectionReason.isNotBlank()) {
                            Text("বাতিলের কারণ: ${pay.rejectionReason}", style = MaterialTheme.typography.labelSmall, color = AlertRed)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("অভিযোগসমূহ (${uiState.complaints.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(uiState.complaints, key = { it.id }) { cmp ->
            Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cmp.subjectBn, fontWeight = FontWeight.Bold)
                        Text(cmp.status, color = if (cmp.status == "RESOLVED") PrimaryGreen else AlertRed, fontWeight = FontWeight.Bold)
                    }
                    Text("${cmp.reporterName}: ${cmp.descriptionBn}", style = MaterialTheme.typography.bodySmall)
                    if (cmp.status != "RESOLVED") {
                        OutlinedButton(onClick = { onResolveComplaint(cmp.id) }) {
                            Text("সমাধান হিসেবে চিহ্নিত করুন")
                        }
                    }
                }
            }
        }
    }

    receiptUriPreview?.let { uri ->
        val model = MediaCaptureHelper.imageModelFromUri(uri)
        AlertDialog(
            onDismissRequest = { receiptUriPreview = null },
            title = { Text("অর্ডার পেমেন্ট রসিদ") },
            text = {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = "রসিদ",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text("রসিদ পাওয়া যায়নি")
                }
            },
            confirmButton = {
                TextButton(onClick = { receiptUriPreview = null }) { Text("বন্ধ করুন") }
            }
        )
    }
}

@Composable
private fun AdminPaymentSettingsTab(
    settings: PaymentSettingsEntity,
    onSave: (PaymentSettingsEntity) -> Unit
) {
    var bKashNumber by remember(settings) { mutableStateOf(settings.bKashNumber) }
    var bKashType by remember(settings) { mutableStateOf(settings.bKashAccountType) }
    var nagadNumber by remember(settings) { mutableStateOf(settings.nagadNumber) }
    var nagadType by remember(settings) { mutableStateOf(settings.nagadAccountType) }
    var farmerFee by remember(settings) { mutableStateOf(settings.farmerFee.toInt().toString()) }
    var buyerFee by remember(settings) { mutableStateOf(settings.buyerFee.toInt().toString()) }
    var renewalFee by remember(settings) { mutableStateOf(settings.renewalFee.toInt().toString()) }
    var validityYears by remember(settings) { mutableStateOf(settings.validityYears.toString()) }
    var instructions by remember(settings) { mutableStateOf(settings.paymentInstructions) }
    var notify30Days by remember(settings) { mutableStateOf(settings.notify30Days) }
    var notify7Days by remember(settings) { mutableStateOf(settings.notify7Days) }
    var notifyOnExpiry by remember(settings) { mutableStateOf(settings.notifyOnExpiry) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("অ্যাকাউন্ট অ্যাক্টিভেশন পেমেন্ট গেটওয়ে ও নম্বর", fontWeight = FontWeight.Bold, color = DarkGreen)

                    OutlinedTextField(
                        value = bKashNumber,
                        onValueChange = { bKashNumber = it },
                        label = { Text("বিকাশ (bKash) নম্বর *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_bkash_number_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentAccountType.entries.forEach { t ->
                            FilterChip(
                                selected = bKashType.equals(t.name, ignoreCase = true),
                                onClick = { bKashType = t.name },
                                label = { Text("bKash ${t.labelEn}") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = nagadNumber,
                        onValueChange = { nagadNumber = it },
                        label = { Text("নগদ (Nagad) নম্বর *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_nagad_number_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentAccountType.entries.forEach { t ->
                            FilterChip(
                                selected = nagadType.equals(t.name, ignoreCase = true),
                                onClick = { nagadType = t.name },
                                label = { Text("Nagad ${t.labelEn}") }
                            )
                        }
                    }

                    HorizontalDivider()
                    Text("ফি ও ২ বছরের মেয়াদ নীতি", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = farmerFee,
                            onValueChange = { farmerFee = it },
                            label = { Text("কৃষক ফি (৳)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = buyerFee,
                            onValueChange = { buyerFee = it },
                            label = { Text("ক্রেতা ফি (৳)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = renewalFee,
                            onValueChange = { renewalFee = it },
                            label = { Text("রিনিউ ফি (৳)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = validityYears,
                            onValueChange = { validityYears = it },
                            label = { Text("মেয়াদ (বছর)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("ব্যবহারকারীদের জন্য পেমেন্ট নির্দেশনা") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()
                    Text("মেয়াদ শেষের রিমাইন্ডার নোটিফিকেশন", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("৩০ দিন আগে রিমাইন্ডার", modifier = Modifier.weight(1f))
                        Switch(checked = notify30Days, onCheckedChange = { notify30Days = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("৭ দিন আগে রিমাইন্ডার", modifier = Modifier.weight(1f))
                        Switch(checked = notify7Days, onCheckedChange = { notify7Days = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("মেয়াদ শেষ হওয়ার দিনে নোটিফিকেশন", modifier = Modifier.weight(1f))
                        Switch(checked = notifyOnExpiry, onCheckedChange = { notifyOnExpiry = it })
                    }

                    Button(
                        onClick = {
                            onSave(
                                settings.copy(
                                    bKashNumber = bKashNumber.trim(),
                                    bKashAccountType = bKashType,
                                    nagadNumber = nagadNumber.trim(),
                                    nagadAccountType = nagadType,
                                    farmerFee = farmerFee.toDoubleOrNull() ?: 300.0,
                                    buyerFee = buyerFee.toDoubleOrNull() ?: 300.0,
                                    renewalFee = renewalFee.toDoubleOrNull() ?: 300.0,
                                    validityYears = validityYears.toIntOrNull() ?: 2,
                                    paymentInstructions = instructions.trim(),
                                    notify30Days = notify30Days,
                                    notify7Days = notify7Days,
                                    notifyOnExpiry = notifyOnExpiry
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_save_payment_settings_button")
                    ) {
                        Text("পেমেন্ট সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminNotificationsTab(
    uiState: MarketplaceUiState,
    onSendNotification: (targetUserId: String, targetRole: String, titleBn: String, messageBn: String) -> Unit
) {
    var targetMode by remember { mutableStateOf("ALL") }
    val allTargets = remember(uiState.farmers, uiState.buyers) { uiState.farmers + uiState.buyers }
    var selectedUserId by remember(allTargets) { mutableStateOf(allTargets.firstOrNull()?.id ?: "") }
    var titleBn by remember { mutableStateOf("") }
    var messageBn by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ব্যবহারকারীদের নোটিফিকেশন পাঠান",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = targetMode == "ALL", onClick = { targetMode = "ALL" }, label = { Text("সবাই (All)") })
                        }
                        item {
                            FilterChip(selected = targetMode == "FARMER", onClick = { targetMode = "FARMER" }, label = { Text("সকল কৃষক") })
                        }
                        item {
                            FilterChip(selected = targetMode == "BUYER", onClick = { targetMode = "BUYER" }, label = { Text("সকল ক্রেতা") })
                        }
                        if (allTargets.isNotEmpty()) {
                            item {
                                FilterChip(selected = targetMode == "SPECIFIC", onClick = { targetMode = "SPECIFIC" }, label = { Text("নির্দিষ্ট ব্যবহারকারী") })
                            }
                        }
                    }

                    if (targetMode == "SPECIFIC" && allTargets.isNotEmpty()) {
                        Text("প্রাপক নির্বাচন করুন:", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allTargets, key = { it.id }) { u ->
                                FilterChip(
                                    selected = selectedUserId == u.id,
                                    onClick = { selectedUserId = u.id },
                                    label = { Text("${u.fullName} (${if (u.role == "FARMER") "কৃষক" else "ক্রেতা"})") }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = titleBn,
                        onValueChange = { titleBn = it },
                        label = { Text("নোটিফিকেশন শিরোনাম *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_notif_title_input")
                    )

                    OutlinedTextField(
                        value = messageBn,
                        onValueChange = { messageBn = it },
                        label = { Text("বার্তা লিখুন *") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("admin_notif_message_input")
                    )

                    val resolvedUserId = if (targetMode == "SPECIFIC") selectedUserId else "ALL"
                    val resolvedRole = if (targetMode == "SPECIFIC") {
                        allTargets.firstOrNull { it.id == selectedUserId }?.role ?: "ALL"
                    } else {
                        targetMode
                    }

                    Button(
                        onClick = {
                            onSendNotification(resolvedUserId, resolvedRole, titleBn, messageBn)
                            titleBn = ""
                            messageBn = ""
                        },
                        enabled = titleBn.isNotBlank() && messageBn.isNotBlank() && (targetMode != "SPECIFIC" || selectedUserId.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_send_notif_button")
                    ) {
                        Text("নোটিফিকেশন পাঠান", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "প্রেরিত ও সিস্টেম নোটিফিকেশন (${uiState.notifications.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(uiState.notifications, key = { it.id }) { n ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(n.titleBn, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(n.type, style = MaterialTheme.typography.labelSmall, color = PrimaryGreen)
                    }
                    Text(n.messageBn, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "প্রাপক: ${if (n.userId == "ALL") n.targetRole else n.userId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminCategoriesTab(
    uiState: MarketplaceUiState,
    onAddCategory: (String, String, String) -> Unit
) {
    var nameBn by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🌿") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("নতুন কৃষি ক্যাটাগরি যোগ করুন", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("ইমোজি") }, modifier = Modifier.width(80.dp))
                        OutlinedTextField(value = nameBn, onValueChange = { nameBn = it }, label = { Text("বাংলা নাম *") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text("English Name") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (nameBn.isNotBlank()) {
                                onAddCategory(nameBn, nameEn, emoji)
                                nameBn = ""
                                nameEn = ""
                            }
                        },
                        enabled = nameBn.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ক্যাটাগরি যোগ করুন")
                    }
                }
            }
        }
        items(uiState.categories, key = { it.id }) { cat ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${cat.emoji} ${cat.nameBn} (${cat.nameEn})", fontWeight = FontWeight.SemiBold)
                    Text(if (cat.isActive) "সক্রিয়" else "নিষ্ক্রিয়", color = PrimaryGreen)
                }
            }
        }
    }
}

@Composable
private fun AdminLocationsTab(
    uiState: MarketplaceUiState,
    onAddLocation: (String, String, String, String, String) -> Unit
) {
    var division by remember { mutableStateOf("খুলনা") }
    var district by remember { mutableStateOf("কুষ্টিয়া") }
    var upazila by remember { mutableStateOf("") }
    var union by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("নতুন জেলা/উপজেলা/ইউনিয়ন যুক্ত করুন", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = division, onValueChange = { division = it }, label = { Text("বিভাগ") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("জেলা *") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = upazila, onValueChange = { upazila = it }, label = { Text("উপজেলা *") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = union, onValueChange = { union = it }, label = { Text("ইউনিয়ন") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = village, onValueChange = { village = it }, label = { Text("গ্রাম") }, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (district.isNotBlank() && upazila.isNotBlank()) {
                                onAddLocation(division, district, upazila, union, village)
                                upazila = ""
                                union = ""
                                village = ""
                            }
                        },
                        enabled = district.isNotBlank() && upazila.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("এলাকা সংরক্ষণ করুন") }
                }
            }
        }
        items(uiState.locations, key = { it.id }) { loc ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${loc.divisionBn} > ${loc.districtBn} > ${loc.upazilaBn}", fontWeight = FontWeight.Bold)
                    Text("ইউনিয়ন: ${loc.unionBn} • গ্রাম: ${loc.villageBn}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AdminAuditLogTab(uiState: MarketplaceUiState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(uiState.auditLogs, key = { it.id }) { log ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("${log.actionBn} (${log.actorRole})", fontWeight = FontWeight.Bold, color = DarkGreen)
                    Text(log.detailsBn, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AdminProductsTab(
    uiState: MarketplaceUiState,
    onVerifyProduct: (String, Boolean, String) -> Unit,
    onUpdateProduct: (String, String, CategoryEntity, Double, Double, String, String) -> Unit,
    onDeleteProduct: (String) -> Unit
) {
    var statusFilter by remember { mutableStateOf("PENDING_APPROVAL") }
    var searchQuery by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var rejectingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val activeCategories = remember(uiState.categories) {
        uiState.categories.filter { it.isActive }
    }

    val filteredProducts = remember(uiState.products, statusFilter, searchQuery) {
        val q = searchQuery.normalizeBn()
        uiState.products
            .filter { p ->
                val matchesStatus = statusFilter == "ALL" || p.status.equals(statusFilter, ignoreCase = true)
                val matchesSearch = q.isBlank() ||
                    p.nameBn.containsBn(q) ||
                    p.farmerName.containsBn(q) ||
                    p.district.containsBn(q) ||
                    p.categoryNameBn.containsBn(q)
                matchesStatus && matchesSearch
            }
            .sortedWith(
                compareBy<ProductEntity> {
                    when (it.status.uppercase()) {
                        "PENDING_APPROVAL" -> 0
                        "ACTIVE" -> 1
                        "REJECTED" -> 2
                        else -> 3
                    }
                }.thenByDescending { it.updatedAt }
            )
    }

    val pendingCount = uiState.products.count { it.status == "PENDING_APPROVAL" }
    val activeCount = uiState.products.count { it.status == "ACTIVE" }
    val rejectedCount = uiState.products.count { it.status == "REJECTED" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("ফসল, কৃষক বা জেলা খুঁজুন...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_products_search")
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = statusFilter == "PENDING_APPROVAL",
                        onClick = { statusFilter = "PENDING_APPROVAL" },
                        label = { Text("যাচাই বাকি ($pendingCount)") },
                        modifier = Modifier.testTag("admin_products_filter_pending")
                    )
                }
                item {
                    FilterChip(
                        selected = statusFilter == "ACTIVE",
                        onClick = { statusFilter = "ACTIVE" },
                        label = { Text("অনুমোদিত ($activeCount)") },
                        modifier = Modifier.testTag("admin_products_filter_active")
                    )
                }
                item {
                    FilterChip(
                        selected = statusFilter == "REJECTED",
                        onClick = { statusFilter = "REJECTED" },
                        label = { Text("প্রত্যাখ্যাত ($rejectedCount)") },
                        modifier = Modifier.testTag("admin_products_filter_rejected")
                    )
                }
                item {
                    FilterChip(
                        selected = statusFilter == "ALL",
                        onClick = { statusFilter = "ALL" },
                        label = { Text("সব (${uiState.products.size})") },
                        modifier = Modifier.testTag("admin_products_filter_all")
                    )
                }
            }
        }

        if (filteredProducts.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🌾", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("এই বিভাগে কোনো ফসল নেই", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            items(filteredProducts, key = { it.id }) { prod ->
                val (badgeLabel, badgeBg, badgeFg) = when (prod.status.uppercase()) {
                    "PENDING_APPROVAL" -> Triple("যাচাই অপেক্ষমাণ", HarvestGoldLight, Color(0xFF92400E))
                    "ACTIVE" -> Triple("অনুমোদিত (ACTIVE)", LightGreen, DarkGreen)
                    "REJECTED" -> Triple("প্রত্যাখ্যাত (REJECTED)", Color(0xFFFEE2E2), Color(0xFFB91C1C))
                    "OUT_OF_STOCK" -> Triple("মজুত শেষ", Color(0xFFF3F4F6), Color(0xFF374151))
                    else -> Triple(prod.status, Color(0xFFF3F4F6), Color(0xFF374151))
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.nameBn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${prod.categoryNameBn} • ${prod.pricePerUnit.formatBdt()}/মণ • মজুত: ${prod.availableQuantity.formatQty()} মণ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = badgeLabel,
                                    color = badgeFg,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "কৃষক: ${prod.farmerName} • ${prod.upazila}, ${prod.district}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (prod.descriptionBn.isNotBlank()) {
                            Text(prod.descriptionBn, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (prod.status != "ACTIVE") {
                                Button(
                                    onClick = { onVerifyProduct(prod.id, true, "") },
                                    modifier = Modifier.weight(1f).testTag("admin_approve_product_${prod.id}")
                                ) {
                                    Text("অনুমোদন", maxLines = 1)
                                }
                            }
                            if (prod.status != "REJECTED") {
                                OutlinedButton(
                                    onClick = { rejectingProduct = prod },
                                    modifier = Modifier.weight(1f).testTag("admin_reject_product_${prod.id}")
                                ) {
                                    Text("প্রত্যাখ্যান", maxLines = 1)
                                }
                            }
                            FilledTonalButton(
                                onClick = { editingProduct = prod },
                                modifier = Modifier.testTag("admin_edit_product_${prod.id}")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "সম্পাদনা", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("এডিট")
                            }
                            FilledTonalIconButton(
                                onClick = { deletingProduct = prod },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFFFEE2E2),
                                    contentColor = Color(0xFFB91C1C)
                                ),
                                modifier = Modifier.testTag("admin_delete_product_${prod.id}")
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "ফসল মুছুন")
                            }
                        }
                    }
                }
            }
        }
    }

    editingProduct?.let { prod ->
        var nameBn by remember(prod.id) { mutableStateOf(prod.nameBn) }
        var priceText by remember(prod.id) { mutableStateOf(prod.pricePerUnit.toString()) }
        var qtyText by remember(prod.id) { mutableStateOf(prod.availableQuantity.toString()) }
        var descBn by remember(prod.id) { mutableStateOf(prod.descriptionBn) }
        var statusVal by remember(prod.id) { mutableStateOf(prod.status) }
        var selectedCat by remember(prod.id, activeCategories) {
            mutableStateOf(
                activeCategories.firstOrNull { it.id == prod.categoryId }
                    ?: uiState.categories.firstOrNull { it.id == prod.categoryId }
                    ?: activeCategories.firstOrNull()
                    ?: CategoryEntity(id = prod.categoryId, nameBn = prod.categoryNameBn, nameEn = prod.categoryNameBn, groupKey = "OTHER", emoji = "🌾", sortOrder = 1)
            )
        }

        AlertDialog(
            onDismissRequest = { editingProduct = null },
            title = { Text("ফসল সম্পাদনা (${prod.nameBn})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameBn,
                        onValueChange = { nameBn = it },
                        label = { Text("ফসলের নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (activeCategories.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(activeCategories, key = { it.id }) { cat ->
                                FilterChip(
                                    selected = selectedCat.id == cat.id,
                                    onClick = { selectedCat = cat },
                                    label = { Text("${cat.emoji} ${cat.nameBn}") }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("দাম (৳/মণ)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it },
                            label = { Text("মজুত (মণ)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = descBn,
                        onValueChange = { descBn = it },
                        label = { Text("বিবরণ") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                "ACTIVE" to "অনুমোদিত",
                                "PENDING_APPROVAL" to "যাচাই বাকি",
                                "REJECTED" to "প্রত্যাখ্যাত",
                                "OUT_OF_STOCK" to "মজুত শেষ"
                            )
                        ) { (code, label) ->
                            FilterChip(
                                selected = statusVal == code,
                                onClick = { statusVal = code },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = priceText.toDoubleOrNull() ?: prod.pricePerUnit
                        val q = qtyText.toDoubleOrNull() ?: prod.availableQuantity
                        onUpdateProduct(prod.id, nameBn, selectedCat, p, q, descBn, statusVal)
                        editingProduct = null
                    }
                ) { Text("সংরক্ষণ") }
            },
            dismissButton = {
                TextButton(onClick = { editingProduct = null }) { Text("বাতিল") }
            }
        )
    }

    rejectingProduct?.let { prod ->
        var reason by remember(prod.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectingProduct = null },
            title = { Text("ফসল প্রত্যাখ্যান (${prod.nameBn})") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("প্রত্যাখ্যানের কারণ (ঐচ্ছিক)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onVerifyProduct(prod.id, false, reason)
                        rejectingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) { Text("প্রত্যাখ্যান করুন") }
            },
            dismissButton = {
                TextButton(onClick = { rejectingProduct = null }) { Text("বাতিল") }
            }
        )
    }

    deletingProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("ফসল মুছে ফেলুন?") },
            text = { Text("অ্যাডমিন হিসেবে আপনি কি '${prod.nameBn}' সম্পূর্ণরূপে মুছে ফেলতে চান?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(prod.id)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) { Text("মুছে ফেলুন") }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun AdminAdMonetizationTab(
    adConfig: AdConfig,
    onSaveAdConfig: (AdConfig) -> Unit
) {
    val context = LocalContext.current
    val telemetry by AdMonetizationManager.telemetry.collectAsStateWithLifecycle()

    var adsEnabled by remember(adConfig) { mutableStateOf(adConfig.adsEnabled) }
    var showAdsLabel by remember(adConfig) { mutableStateOf(adConfig.showAdsLabel) }
    var androidAdsEnabled by remember(adConfig) { mutableStateOf(adConfig.androidAdsEnabled) }
    var mediationAppKey by remember(adConfig) { mutableStateOf(adConfig.mediationAppKey) }
    var adTestMode by remember(adConfig) { mutableStateOf(adConfig.adTestMode) }

    // 5 Format Toggles + Unit/Placement IDs
    var bannerEnabled by remember(adConfig) { mutableStateOf(adConfig.bannerEnabled) }
    var androidBannerId by remember(adConfig) { mutableStateOf(adConfig.androidBannerId) }
    var nativeEnabled by remember(adConfig) { mutableStateOf(adConfig.nativeEnabled) }
    var androidNativePlacement by remember(adConfig) { mutableStateOf(adConfig.androidNativePlacement) }
    var interstitialEnabled by remember(adConfig) { mutableStateOf(adConfig.interstitialEnabled) }
    var androidInterstitialId by remember(adConfig) { mutableStateOf(adConfig.androidInterstitialId) }
    var rewardedEnabled by remember(adConfig) { mutableStateOf(adConfig.rewardedEnabled) }
    var androidRewardedId by remember(adConfig) { mutableStateOf(adConfig.androidRewardedId) }
    var appOpenEnabled by remember(adConfig) { mutableStateOf(adConfig.appOpenEnabled) }
    var androidAppOpenId by remember(adConfig) { mutableStateOf(adConfig.androidAppOpenId) }

    // 8 Mediation Networks (Unity LevelPlay 9.2.0, Zero AdMob)
    var unityAdsEnabled by remember(adConfig) { mutableStateOf(adConfig.unityAdsEnabled) }
    var unityGameId by remember(adConfig) { mutableStateOf(adConfig.unityGameId) }
    var appLovinEnabled by remember(adConfig) { mutableStateOf(adConfig.appLovinEnabled) }
    var appLovinSdkKey by remember(adConfig) { mutableStateOf(adConfig.appLovinSdkKey) }
    var metaAudienceEnabled by remember(adConfig) { mutableStateOf(adConfig.metaAudienceEnabled) }
    var metaPlacementId by remember(adConfig) { mutableStateOf(adConfig.metaPlacementId) }
    var mintegralEnabled by remember(adConfig) { mutableStateOf(adConfig.mintegralEnabled) }
    var mintegralAppId by remember(adConfig) { mutableStateOf(adConfig.mintegralAppId) }
    var pangleEnabled by remember(adConfig) { mutableStateOf(adConfig.pangleEnabled) }
    var pangleAppId by remember(adConfig) { mutableStateOf(adConfig.pangleAppId) }
    var inMobiEnabled by remember(adConfig) { mutableStateOf(adConfig.inMobiEnabled) }
    var inMobiAccountId by remember(adConfig) { mutableStateOf(adConfig.inMobiAccountId) }
    var liftoffEnabled by remember(adConfig) { mutableStateOf(adConfig.liftoffEnabled) }
    var liftoffAppId by remember(adConfig) { mutableStateOf(adConfig.liftoffAppId) }
    var dtExchangeEnabled by remember(adConfig) { mutableStateOf(adConfig.dtExchangeEnabled) }
    var dtExchangeAppId by remember(adConfig) { mutableStateOf(adConfig.dtExchangeAppId) }
    var chartboostEnabled by remember(adConfig) { mutableStateOf(adConfig.chartboostEnabled) }
    var chartboostAppId by remember(adConfig) { mutableStateOf(adConfig.chartboostAppId) }
    var startIoEnabled by remember(adConfig) { mutableStateOf(adConfig.startIoEnabled) }
    var startIoAppId by remember(adConfig) { mutableStateOf(adConfig.startIoAppId) }
    var consentGranted by remember(adConfig) { mutableStateOf(adConfig.consentGranted) }

    // Website & Hybrid Web Fallback
    var mobileWebAdsEnabled by remember(adConfig) { mutableStateOf(adConfig.mobileWebAdsEnabled) }
    var mobileAdNetworkPriority by remember(adConfig) { mutableStateOf(adConfig.mobileAdNetworkPriority) }
    var mobileCustomAdHtml by remember(adConfig) { mutableStateOf(adConfig.mobileCustomAdHtml) }
    var mobileDirectLinkUrl by remember(adConfig) { mutableStateOf(adConfig.mobileDirectLinkUrl) }

    var sponsorEnabled by remember(adConfig) { mutableStateOf(adConfig.sponsorEnabled) }
    var sponsorTitle by remember(adConfig) { mutableStateOf(adConfig.sponsorTitle) }
    var sponsorSubtitle by remember(adConfig) { mutableStateOf(adConfig.sponsorSubtitle) }
    var sponsorImageUrl by remember(adConfig) { mutableStateOf(adConfig.sponsorImageUrl) }
    var sponsorTargetUrl by remember(adConfig) { mutableStateOf(adConfig.sponsorTargetUrl) }
    var sponsorCtaText by remember(adConfig) { mutableStateOf(adConfig.sponsorCtaText) }

    var adsenseEnabled by remember(adConfig) { mutableStateOf(adConfig.adsenseEnabled) }
    var adsenseClient by remember(adConfig) { mutableStateOf(adConfig.adsenseClient) }
    var adsenseTopSlot by remember(adConfig) { mutableStateOf(adConfig.adsenseTopSlot) }
    var adsenseFeedSlot by remember(adConfig) { mutableStateOf(adConfig.adsenseFeedSlot) }
    var adsenseFooterSlot by remember(adConfig) { mutableStateOf(adConfig.adsenseFooterSlot) }

    var adsterraEnabled by remember(adConfig) { mutableStateOf(adConfig.adsterraEnabled) }
    var adsterraBannerKey by remember(adConfig) { mutableStateOf(adConfig.adsterraBannerKey) }
    var adsterraScriptUrl by remember(adConfig) { mutableStateOf(adConfig.adsterraScriptUrl) }
    var adsterraSocialBarUrl by remember(adConfig) { mutableStateOf(adConfig.adsterraSocialBarUrl) }

    var monetagEnabled by remember(adConfig) { mutableStateOf(adConfig.monetagEnabled) }
    var monetagZoneId by remember(adConfig) { mutableStateOf(adConfig.monetagZoneId) }
    var monetagScriptUrl by remember(adConfig) { mutableStateOf(adConfig.monetagScriptUrl) }

    var customAdEnabled by remember(adConfig) { mutableStateOf(adConfig.customAdEnabled) }
    var customAdName by remember(adConfig) { mutableStateOf(adConfig.customAdName) }
    var customTopHtml by remember(adConfig) { mutableStateOf(adConfig.customTopHtml) }

    fun buildDraftConfig(): AdConfig {
        val resolvedMode = if (unityAdsEnabled && mediationAppKey.trim().isBlank() && unityGameId.trim().isNotBlank()) {
            "UNITY_DIRECT"
        } else {
            "UNITY_LEVELPLAY"
        }
        return adConfig.copy(
            adsEnabled = adsEnabled,
            showAdsLabel = showAdsLabel,
            androidAdsEnabled = androidAdsEnabled,
            mediationPlatform = resolvedMode,
            mediationAppKey = mediationAppKey.trim(),
            adTestMode = adTestMode,
        bannerEnabled = bannerEnabled,
        androidBannerId = androidBannerId.trim(),
        nativeEnabled = nativeEnabled,
        androidNativePlacement = androidNativePlacement.trim(),
        interstitialEnabled = interstitialEnabled,
        androidInterstitialId = androidInterstitialId.trim(),
        rewardedEnabled = rewardedEnabled,
        androidRewardedId = androidRewardedId.trim(),
        appOpenEnabled = appOpenEnabled,
        androidAppOpenId = androidAppOpenId.trim(),
        unityAdsEnabled = unityAdsEnabled,
        unityGameId = unityGameId.trim(),
        appLovinEnabled = appLovinEnabled,
        appLovinSdkKey = appLovinSdkKey.trim(),
        metaAudienceEnabled = metaAudienceEnabled,
        metaPlacementId = metaPlacementId.trim(),
        mintegralEnabled = mintegralEnabled,
        mintegralAppId = mintegralAppId.trim(),
        pangleEnabled = pangleEnabled,
        pangleAppId = pangleAppId.trim(),
        inMobiEnabled = inMobiEnabled,
        inMobiAccountId = inMobiAccountId.trim(),
        liftoffEnabled = liftoffEnabled,
        liftoffAppId = liftoffAppId.trim(),
        dtExchangeEnabled = dtExchangeEnabled,
        dtExchangeAppId = dtExchangeAppId.trim(),
        chartboostEnabled = chartboostEnabled,
        chartboostAppId = chartboostAppId.trim(),
        startIoEnabled = startIoEnabled,
        startIoAppId = startIoAppId.trim(),
        consentGranted = consentGranted,
        mobileWebAdsEnabled = mobileWebAdsEnabled,
        mobileAdNetworkPriority = mobileAdNetworkPriority,
        mobileCustomAdHtml = mobileCustomAdHtml,
        mobileDirectLinkUrl = mobileDirectLinkUrl.trim(),
        sponsorEnabled = sponsorEnabled,
        sponsorTitle = sponsorTitle.trim(),
        sponsorSubtitle = sponsorSubtitle.trim(),
        sponsorImageUrl = sponsorImageUrl.trim(),
        sponsorTargetUrl = sponsorTargetUrl.trim(),
        sponsorCtaText = sponsorCtaText.trim(),
        adsenseEnabled = adsenseEnabled,
        adsenseClient = adsenseClient.trim(),
        adsenseTopSlot = adsenseTopSlot.trim(),
        adsenseFeedSlot = adsenseFeedSlot.trim(),
        adsenseFooterSlot = adsenseFooterSlot.trim(),
        adsterraEnabled = adsterraEnabled,
        adsterraBannerKey = adsterraBannerKey.trim(),
        adsterraScriptUrl = adsterraScriptUrl.trim(),
        adsterraSocialBarUrl = adsterraSocialBarUrl.trim(),
        monetagEnabled = monetagEnabled,
        monetagZoneId = monetagZoneId.trim(),
        monetagScriptUrl = monetagScriptUrl.trim(),
        customAdEnabled = customAdEnabled,
        customAdName = customAdName.trim(),
        customTopHtml = customTopHtml
    )
    }

    val priorityOptions = listOf(
        "ALL" to "সব নেটওয়ার্ক (All)",
        "LEVELPLAY" to "Unity LevelPlay Mediation",
        "UNITY" to "Unity Ads",
        "STARTIO" to "Start.io",
        "APPLOVIN" to "AppLovin MAX",
        "ADSTERRA" to "Adsterra",
        "MONETAG" to "Monetag",
        "ADSENSE" to "Website AdSense",
        "CUSTOM" to "Custom HTML / Direct",
        "SPONSOR" to "শুধু স্পন্সর"
    )

    val sdkColor = when (telemetry.sdkInitStatus) {
        SdkInitStatus.SUCCESS -> PrimaryGreen
        SdkInitStatus.INITIALIZING -> InfoBlue
        SdkInitStatus.FAILED -> AlertRed
        SdkInitStatus.IDLE -> Color(0xFFB45309)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live SDK Status, Real Impression Revenue & Diagnostic Telemetry
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, sdkColor.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Direct Unity Ads (6227313) & LevelPlay টেলিমেট্রি", fontWeight = FontWeight.Bold, color = DarkGreen, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(999.dp), color = sdkColor.copy(alpha = 0.14f)) {
                            Text(
                                text = telemetry.sdkInitStatus.labelBn,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sdkColor
                            )
                        }
                    }
                    Surface(
                        color = LightGreen,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val draft = buildDraftConfig()
                        Text(
                            text = if (AdMonetizationManager.usesDirectUnityAds(draft)) {
                                "Active Mode: Direct Unity Ads SDK 4.16.6 (Game ID: ${draft.unityGameId})"
                            } else {
                                "Active Mode: Unity LevelPlay Mediation (App Key: ${telemetry.activeAppKeyMasked.ifBlank { "(not set)" }})"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "AdMob সম্পূর্ণ অপসারিত (Zero AdMob SDK) • প্ল্যাটফর্ম: ${telemetry.mediationPlatform} • Test Mode: ${if (telemetry.testModeEnabled) "ON" else "OFF"} • Consent: ${if (telemetry.consentGranted) "GRANTED" else "DENIED"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (telemetry.lastInitError.isNotBlank()) {
                        Text(
                            text = "SDK Init ত্রুটি: ${telemetry.lastInitError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("দৈনিক আয় (Real)", style = MaterialTheme.typography.labelSmall, color = DarkGreen)
                                Text(
                                    text = "$" + String.format(Locale.ENGLISH, "%.5f", telemetry.dailyRevenueUsd),
                                    fontWeight = FontWeight.Bold,
                                    color = DarkGreen
                                )
                            }
                        }
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("মাসিক আয় (Real)", style = MaterialTheme.typography.labelSmall, color = DarkGreen)
                                Text(
                                    text = "$" + String.format(Locale.ENGLISH, "%.5f", telemetry.monthlyRevenueUsd),
                                    fontWeight = FontWeight.Bold,
                                    color = DarkGreen
                                )
                            }
                        }
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("ইম্প্রেশন", style = MaterialTheme.typography.labelSmall, color = DarkGreen)
                                Text("${telemetry.impressionCount}", fontWeight = FontWeight.Bold, color = DarkGreen)
                            }
                        }
                    }
                    if (telemetry.networkRevenueUsd.isNotEmpty()) {
                        Text("নেটওয়ার্কভিত্তিক আয় (ILRD):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        telemetry.networkRevenueUsd.forEach { (net, rev) ->
                            Text(
                                text = "• $net: $${String.format(Locale.ENGLISH, "%.5f", rev)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        Text(
                            text = "রিয়েল বিজ্ঞাপন প্রদর্শনের পরই এখানে দৈনিক/মাসিক/নেটওয়ার্কভিত্তিক আয় যুক্ত হবে (ভুয়া সংখ্যা দেখানো হয় না)।",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { AdMonetizationManager.initialize(context, buildDraftConfig()) },
                            modifier = Modifier.weight(1f).testTag("admin_test_sdk_init_button")
                        ) {
                            Text("SDK টেস্ট ও ইনিশিয়ালাইজ")
                        }
                        OutlinedButton(
                            onClick = { AdMonetizationManager.clearDebugLogs() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("লগ পরিষ্কার করুন")
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            adsEnabled = true
                            androidAdsEnabled = true
                            unityAdsEnabled = true
                            unityGameId = AdConfig.DEFAULT_UNITY_GAME_ID
                            bannerEnabled = true
                            androidBannerId = AdConfig.DEFAULT_UNITY_BANNER_ID
                            interstitialEnabled = true
                            androidInterstitialId = AdConfig.DEFAULT_UNITY_INTERSTITIAL_ID
                            rewardedEnabled = true
                            androidRewardedId = AdConfig.DEFAULT_UNITY_REWARDED_ID
                            mediationAppKey = ""
                            AdMonetizationManager.initialize(context, buildDraftConfig())
                        },
                        modifier = Modifier.fillMaxWidth().testTag("admin_apply_unity_preset_button")
                    ) {
                        Text("Unity Ads 6227313 ডিফল্ট সেট ও টেস্ট করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Master Configuration, App Key & 5 Format Controls
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("মাস্টার কন্ট্রোল ও ৫টি বিজ্ঞাপন ফরম্যাট (LevelPlay)", fontWeight = FontWeight.Bold, color = DarkGreen)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("মাস্টার মনিটাইজেশন চালু (Master ON/OFF)", modifier = Modifier.weight(1f))
                        Switch(checked = adsEnabled, onCheckedChange = { adsEnabled = it }, modifier = Modifier.testTag("admin_master_ads_switch"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Android LevelPlay Mediation সক্রিয়", modifier = Modifier.weight(1f))
                        Switch(checked = androidAdsEnabled, onCheckedChange = { androidAdsEnabled = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("টেস্ট মোড / Test Suite সক্রিয়", modifier = Modifier.weight(1f))
                        Switch(checked = adTestMode, onCheckedChange = { adTestMode = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("বিজ্ঞাপন লেবেল (Sponsored) দেখান", modifier = Modifier.weight(1f))
                        Switch(checked = showAdsLabel, onCheckedChange = { showAdsLabel = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("গোপনীয়তা সম্মতি (GDPR/Consent Granted)", modifier = Modifier.weight(1f))
                        Switch(checked = consentGranted, onCheckedChange = { consentGranted = it })
                    }
                    OutlinedTextField(
                        value = mediationAppKey,
                        onValueChange = { mediationAppKey = it },
                        label = { Text("Unity LevelPlay Android App Key (অথবা LEVELPLAY_APP_KEY Secret)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_levelplay_app_key_input")
                    )

                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Adaptive Banner Ad", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = bannerEnabled, onCheckedChange = { bannerEnabled = it })
                    }
                    OutlinedTextField(
                        value = androidBannerId,
                        onValueChange = { androidBannerId = it },
                        label = { Text("LevelPlay Banner Ad Unit ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_levelplay_banner_input")
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Native Ad", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = nativeEnabled, onCheckedChange = { nativeEnabled = it })
                    }
                    OutlinedTextField(
                        value = androidNativePlacement,
                        onValueChange = { androidNativePlacement = it },
                        label = { Text("LevelPlay Native Placement ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Interstitial Ad", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = interstitialEnabled, onCheckedChange = { interstitialEnabled = it })
                    }
                    OutlinedTextField(
                        value = androidInterstitialId,
                        onValueChange = { androidInterstitialId = it },
                        label = { Text("LevelPlay Interstitial Ad Unit ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_levelplay_interstitial_input")
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rewarded Video Ad", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = rewardedEnabled, onCheckedChange = { rewardedEnabled = it })
                    }
                    OutlinedTextField(
                        value = androidRewardedId,
                        onValueChange = { androidRewardedId = it },
                        label = { Text("LevelPlay Rewarded Ad Unit ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("App Open Ad", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = appOpenEnabled, onCheckedChange = { appOpenEnabled = it })
                    }
                    OutlinedTextField(
                        value = androidAppOpenId,
                        onValueChange = { androidAppOpenId = it },
                        label = { Text("LevelPlay App Open Ad Unit ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. 10 Official Multi-Network Integrations + App Keys (Zero AdMob)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("১০টি অফিসিয়াল অ্যাড নেটওয়ার্ক কনফিগারেশন", fontWeight = FontWeight.Bold, color = DarkGreen)
                    Text(
                        text = "Unity Ads বাদে বাকি ৯টি নেটওয়ার্কের জন্য সংশ্লিষ্ট পাবলিশার অ্যাকাউন্টে পৃথক অনুমোদন ও App ID প্রয়োজন।",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    @Composable
                    fun networkInput(
                        title: String,
                        approvalRequired: Boolean,
                        checked: Boolean,
                        onCheckedChange: (Boolean) -> Unit,
                        idLabel: String,
                        idValue: String,
                        onIdChange: (String) -> Unit
                    ) {
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (approvalRequired) "পৃথক পাবলিশার অ্যাকাউন্ট ও অনুমোদন প্রয়োজন" else "সরাসরি SDK / LevelPlay-তে প্রস্তুত (Game ID দিন)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (approvalRequired) Color(0xFFB45309) else PrimaryGreen
                                )
                            }
                            Switch(checked = checked, onCheckedChange = onCheckedChange)
                        }
                        OutlinedTextField(
                            value = idValue,
                            onValueChange = onIdChange,
                            label = { Text(idLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    networkInput("1. Unity Ads", false, unityAdsEnabled, { unityAdsEnabled = it }, "Unity Game ID", unityGameId) { unityGameId = it }
                    networkInput("2. AppLovin MAX", true, appLovinEnabled, { appLovinEnabled = it }, "AppLovin SDK Key / App ID", appLovinSdkKey) { appLovinSdkKey = it }
                    networkInput("3. Meta Audience Network", true, metaAudienceEnabled, { metaAudienceEnabled = it }, "Meta Placement / App ID", metaPlacementId) { metaPlacementId = it }
                    networkInput("4. Mintegral", true, mintegralEnabled, { mintegralEnabled = it }, "Mintegral App ID", mintegralAppId) { mintegralAppId = it }
                    networkInput("5. Pangle", true, pangleEnabled, { pangleEnabled = it }, "Pangle App ID", pangleAppId) { pangleAppId = it }
                    networkInput("6. InMobi", true, inMobiEnabled, { inMobiEnabled = it }, "InMobi Account / App ID", inMobiAccountId) { inMobiAccountId = it }
                    networkInput("7. Liftoff Monetize (Vungle)", true, liftoffEnabled, { liftoffEnabled = it }, "Liftoff App ID", liftoffAppId) { liftoffAppId = it }
                    networkInput("8. DT Exchange (Fyber)", true, dtExchangeEnabled, { dtExchangeEnabled = it }, "DT Exchange App ID", dtExchangeAppId) { dtExchangeAppId = it }
                    networkInput("9. Chartboost (LevelPlay)", true, chartboostEnabled, { chartboostEnabled = it }, "Chartboost App ID", chartboostAppId) { chartboostAppId = it }
                    networkInput("10. Start.io (Standalone SDK)", true, startIoEnabled, { startIoEnabled = it }, "Start.io App ID", startIoAppId) { startIoAppId = it }
                }
            }
        }

        // 4. Adapter & Format Monitoring Tables
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("লাইভ অ্যাডাপ্টার স্ট্যাটাস (${telemetry.adapterStatuses.size})", fontWeight = FontWeight.Bold, color = DarkGreen)
                    telemetry.adapterStatuses.forEach { st ->
                        val readyColor = if (st.runtimeReady) PrimaryGreen else Color(0xFFB45309)
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${st.displayName} (v${st.adapterVersion})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (st.runtimeReady) "ACTIVE" else if (st.sdkPackaged) "CONFIG/WAIT" else "MISSING",
                                    color = readyColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                text = "SDK Packaged: ${st.sdkPackaged} • App ID: ${if (st.appKeyConfigured) "Set" else "Missing"} • ${st.statusNote}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ফরম্যাট মনিটরিং (Fill Rate / Impressions / Clicks / Retries)", fontWeight = FontWeight.Bold, color = DarkGreen)
                    AdFormatType.entries.forEach { fmt ->
                        val fm = telemetry.formatMetrics[fmt] ?: AdFormatTelemetry(fmt)
                        val fillPct = String.format(Locale.ENGLISH, "%.0f%%", fm.fillRatePercent)
                        val ctrPct = String.format(Locale.ENGLISH, "%.1f%%", fm.ctrPercent)
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${fmt.labelEn} (${if (fm.enabled) "ON" else "OFF"})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text("${fm.requestStatus} / ${fm.loadedStatus}", style = MaterialTheme.typography.labelSmall, color = DarkGreen)
                            }
                            Text(
                                text = "Req: ${fm.requestCount} • Load: ${fm.loadedCount} ($fillPct) • Show: ${fm.displayedCount} • Click: ${fm.clickedCount} ($ctrPct) • Retry: ${fm.retryCount} • Fail: ${fm.failedCount}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (fm.lastError.isNotBlank()) {
                                Text("Last Error: ${fm.lastError}", style = MaterialTheme.typography.labelSmall, color = AlertRed)
                            }
                        }
                    }
                }
            }
        }

        // 5. Debug Event Logs
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("বিজ্ঞাপন ডিবাগ লগ (${telemetry.debugLogs.size})", fontWeight = FontWeight.Bold, color = DarkGreen)
                    if (telemetry.debugLogs.isEmpty()) {
                        Text("এখনো কোনো ডিবাগ ইভেন্ট লগ রেকর্ড হয়নি।", style = MaterialTheme.typography.bodySmall)
                    } else {
                        telemetry.debugLogs.take(25).forEach { entry ->
                            Text(
                                text = "[${AdMonetizationManager.formatTimestamp(entry.timestamp)}] ${entry.code} (${entry.network}${entry.format?.let { "/${it.labelEn}" } ?: ""}): ${entry.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.code.contains("FAIL") || entry.code.contains("INVALID") || entry.code == AdDebugEventCode.AD_NO_FILL) AlertRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 6. Hybrid WebView Fallback + Priority (for Web & Mobile Web Ads)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("হাইব্রিড ওয়েব ফলব্যাক ও স্পন্সর কনফিগারেশন", fontWeight = FontWeight.Bold, color = DarkGreen)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Multi-Network Web Ads ফলব্যাক চালু", modifier = Modifier.weight(1f))
                        Switch(checked = mobileWebAdsEnabled, onCheckedChange = { mobileWebAdsEnabled = it })
                    }
                    Text("মোবাইল অগ্রাধিকার:", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(priorityOptions) { (key, label) ->
                            FilterChip(
                                selected = mobileAdNetworkPriority.equals(key, ignoreCase = true),
                                onClick = { mobileAdNetworkPriority = key },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = mobileDirectLinkUrl,
                        onValueChange = { mobileDirectLinkUrl = it },
                        label = { Text("Direct Link / Smartlink URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mobileCustomAdHtml,
                        onValueChange = { mobileCustomAdHtml = it },
                        label = { Text("Custom Ad HTML / JS Tag") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 7. Direct Sponsor Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ডাইরেক্ট স্পন্সর ব্যানার", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = sponsorEnabled, onCheckedChange = { sponsorEnabled = it })
                    }
                    OutlinedTextField(value = sponsorTitle, onValueChange = { sponsorTitle = it }, label = { Text("স্পন্সর শিরোনাম") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sponsorSubtitle, onValueChange = { sponsorSubtitle = it }, label = { Text("বিবরণ") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sponsorImageUrl, onValueChange = { sponsorImageUrl = it }, label = { Text("ব্যানার ছবির URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sponsorTargetUrl, onValueChange = { sponsorTargetUrl = it }, label = { Text("লিঙ্ক URL") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = sponsorCtaText, onValueChange = { sponsorCtaText = it }, label = { Text("বাটন টেক্সট") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 8. Website Ad Networks (AdSense / Adsterra / Monetag / Custom HTML)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ওয়েবসাইট অ্যাড নেটওয়ার্কসমূহ", fontWeight = FontWeight.Bold, color = DarkGreen)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Google AdSense (Website Only) চালু", modifier = Modifier.weight(1f))
                        Switch(checked = adsenseEnabled, onCheckedChange = { adsenseEnabled = it })
                    }
                    OutlinedTextField(value = adsenseClient, onValueChange = { adsenseClient = it }, label = { Text("AdSense Publisher ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = adsenseTopSlot, onValueChange = { adsenseTopSlot = it }, label = { Text("Top Slot") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = adsenseFeedSlot, onValueChange = { adsenseFeedSlot = it }, label = { Text("Feed Slot") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = adsenseFooterSlot, onValueChange = { adsenseFooterSlot = it }, label = { Text("Footer Slot") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Adsterra চালু", modifier = Modifier.weight(1f))
                        Switch(checked = adsterraEnabled, onCheckedChange = { adsterraEnabled = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = adsterraBannerKey, onValueChange = { adsterraBannerKey = it }, label = { Text("Banner Key") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = adsterraScriptUrl, onValueChange = { adsterraScriptUrl = it }, label = { Text("invoke.js URL") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = adsterraSocialBarUrl, onValueChange = { adsterraSocialBarUrl = it }, label = { Text("Social Bar URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Monetag চালু", modifier = Modifier.weight(1f))
                        Switch(checked = monetagEnabled, onCheckedChange = { monetagEnabled = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = monetagZoneId, onValueChange = { monetagZoneId = it }, label = { Text("Zone ID") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = monetagScriptUrl, onValueChange = { monetagScriptUrl = it }, label = { Text("Script URL") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Custom HTML বিজ্ঞাপন", modifier = Modifier.weight(1f))
                        Switch(checked = customAdEnabled, onCheckedChange = { customAdEnabled = it })
                    }
                    OutlinedTextField(value = customAdName, onValueChange = { customAdName = it }, label = { Text("নেটওয়ার্ক নাম") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customTopHtml, onValueChange = { customTopHtml = it }, label = { Text("Custom HTML / Tag") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Button(
                onClick = { onSaveAdConfig(buildDraftConfig()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_save_ad_config_button")
            ) {
                Text("বিজ্ঞাপন সেটিংস সেভ করুন (অ্যাপ + ওয়েবসাইট লাইভ)", fontWeight = FontWeight.Bold)
            }
        }
    }
}
