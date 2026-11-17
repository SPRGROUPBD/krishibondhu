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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AdConfig
import com.example.data.model.formatBdt
import com.example.ui.components.OrderStatusChip
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onVerifyUser: (userId: String, approved: Boolean) -> Unit,
    onSendNotification: (targetUserId: String, targetRole: String, titleBn: String, messageBn: String) -> Unit,
    onAddCategory: (nameBn: String, nameEn: String, emoji: String) -> Unit,
    onAddLocation: (division: String, district: String, upazila: String, union: String, village: String) -> Unit,
    onResolveComplaint: (complaintId: String) -> Unit,
    onSaveAdConfig: (AdConfig) -> Unit = {},
    onBuildCsvReport: () -> String
) {
    val context = LocalContext.current
    val stats = uiState.adminStats
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "সারসংক্ষেপ",
        "কৃষক ও ক্রেতা",
        "নোটিফিকেশন পাঠান",
        "ক্যাটাগরি",
        "এলাকা",
        "অর্ডার ও অভিযোগ",
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
            // Admin Price-Protection Policy Banner
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
                        text = "নীতিমালা: অ্যাডমিন প্ল্যাটফর্ম যাচাই ও তদারকি করেন — কোনো কৃষকের পণ্যের দাম পরিবর্তন করতে পারেন না।",
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
                0 -> AdminOverviewTab(stats = stats)
                1 -> AdminVerificationTab(uiState = uiState, onVerifyUser = onVerifyUser)
                2 -> AdminNotificationsTab(uiState = uiState, onSendNotification = onSendNotification)
                3 -> AdminCategoriesTab(uiState = uiState, onAddCategory = onAddCategory)
                4 -> AdminLocationsTab(uiState = uiState, onAddLocation = onAddLocation)
                5 -> AdminOrdersComplaintsTab(uiState = uiState, onResolveComplaint = onResolveComplaint)
                6 -> AdminAuditLogTab(uiState = uiState)
                7 -> AdminAdMonetizationTab(adConfig = uiState.adConfig, onSaveAdConfig = onSaveAdConfig)
            }
        }
    }
}

@Composable
private fun AdminOverviewTab(stats: com.example.data.model.AdminDashboardStats) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("মোট কৃষক", "${stats.totalFarmers}", PrimaryGreen, Modifier.weight(1f))
                AdminKpiCard("যাচাইকৃত কৃষক", "${stats.verifiedFarmers}", DarkGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("মোট ক্রেতা", "${stats.totalBuyers}", InfoBlue, Modifier.weight(1f))
                AdminKpiCard("যাচাইকৃত ক্রেতা", "${stats.verifiedBuyers}", DarkGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("সক্রিয় ফসল", "${stats.activeProducts}", Color(0xFFB45309), Modifier.weight(1f))
                AdminKpiCard("আজকের অর্ডার", "${stats.todaysOrders}", PrimaryGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("সম্পন্ন অর্ডার", "${stats.completedOrders}", DarkGreen, Modifier.weight(1f))
                AdminKpiCard("মোট বিক্রয় (৳)", stats.totalSalesAmount.formatBdt(), PrimaryGreen, Modifier.weight(1f))
            }
        }

        // Upazila-wise Sales Bar Chart using Compose Canvas
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
                        entries.forEach { (upazila, total) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(upazila, style = MaterialTheme.typography.bodySmall)
                                Text(total.formatBdt(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
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
private fun AdminVerificationTab(
    uiState: MarketplaceUiState,
    onVerifyUser: (String, Boolean) -> Unit
) {
    var roleFilter by remember { mutableStateOf("ALL") } // ALL, FARMER, BUYER
    val pendingFarmers = uiState.farmers.count { it.verificationStatus == "PENDING" }
    val pendingBuyers = uiState.buyers.count { it.verificationStatus == "PENDING" }

    val visibleUsers = remember(uiState.farmers, uiState.buyers, roleFilter) {
        val pool = when (roleFilter) {
            "FARMER" -> uiState.farmers
            "BUYER" -> uiState.buyers
            else -> uiState.farmers + uiState.buyers
        }
        pool.sortedWith(
            compareBy<com.example.data.local.UserEntity> {
                when (it.verificationStatus) {
                    "PENDING" -> 0
                    "REJECTED" -> 1
                    else -> 2
                }
            }.thenByDescending { it.createdAt }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "কৃষক ও ক্রেতা যাচাইকরণ এবং তালিকা",
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
                            selected = roleFilter == "ALL",
                            onClick = { roleFilter = "ALL" },
                            label = { Text("সবাই (${uiState.farmers.size + uiState.buyers.size})") },
                            modifier = Modifier.testTag("admin_users_filter_all")
                        )
                    }
                    item {
                        FilterChip(
                            selected = roleFilter == "FARMER",
                            onClick = { roleFilter = "FARMER" },
                            label = { Text("কৃষক (${uiState.farmers.size})") },
                            modifier = Modifier.testTag("admin_users_filter_farmers")
                        )
                    }
                    item {
                        FilterChip(
                            selected = roleFilter == "BUYER",
                            onClick = { roleFilter = "BUYER" },
                            label = { Text("ক্রেতা (${uiState.buyers.size})") },
                            modifier = Modifier.testTag("admin_users_filter_buyers")
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
                        text = "এখনো এই তালিকায় কোনো নিবন্ধিত কৃষক বা ক্রেতা নেই।",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(visibleUsers, key = { it.id }) { person ->
            val isFarmer = person.role == "FARMER"
            val statusColor = when (person.verificationStatus) {
                "VERIFIED" -> LightGreen
                "REJECTED" -> Color(0xFFFEE2E2)
                else -> HarvestGoldLight
            }
            val statusTextColor = when (person.verificationStatus) {
                "VERIFIED" -> DarkGreen
                "REJECTED" -> AlertRed
                else -> Color(0xFF92400E)
            }
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (isFarmer) "🧑‍🌾" else "🛍️"} ${person.fullName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isFarmer) "ভূমিকা: কৃষক (Farmer)" else "ভূমিকা: ক্রেতা (Buyer)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFarmer) PrimaryGreen else InfoBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(color = statusColor, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = person.verificationStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusTextColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text("${person.upazila}, ${person.district}, ${person.division} • ${person.phone}", style = MaterialTheme.typography.bodySmall)
                    if (person.nidMasked.isNotBlank()) {
                        Text("মাস্কড এনআইডি: ${person.nidMasked}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isFarmer && person.farmBio.isNotBlank()) {
                        Text("খামার পরিচিতি: ${person.farmBio}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (person.verificationStatus != "VERIFIED") {
                            Button(
                                onClick = { onVerifyUser(person.id, true) },
                                modifier = Modifier.weight(1f).testTag("verify_approve_${person.id}")
                            ) { Text("✓ অনুমোদন দিন") }
                        }
                        if (person.verificationStatus != "REJECTED") {
                            OutlinedButton(
                                onClick = { onVerifyUser(person.id, false) },
                                modifier = Modifier.weight(1f).testTag("verify_reject_${person.id}")
                            ) { Text("প্রত্যাখ্যান") }
                        }
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
    var targetMode by remember { mutableStateOf("ALL") } // ALL, FARMER, BUYER, SPECIFIC
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
                    Text(
                        text = "অ্যাডমিনের পাঠানো বার্তা তাৎক্ষণিকভাবে Firebase Realtime Database-এর মাধ্যমে মোবাইল অ্যাপ ও ওয়েবসাইটে পৌঁছে যাবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = targetMode == "ALL",
                                onClick = { targetMode = "ALL" },
                                label = { Text("সবাই (All)") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = targetMode == "FARMER",
                                onClick = { targetMode = "FARMER" },
                                label = { Text("সকল কৃষক") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = targetMode == "BUYER",
                                onClick = { targetMode = "BUYER" },
                                label = { Text("সকল ক্রেতা") }
                            )
                        }
                        if (allTargets.isNotEmpty()) {
                            item {
                                FilterChip(
                                    selected = targetMode == "SPECIFIC",
                                    onClick = { targetMode = "SPECIFIC" },
                                    label = { Text("নির্দিষ্ট ব্যবহারকারী") }
                                )
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
                        OutlinedTextField(
                            value = emoji,
                            onValueChange = { emoji = it },
                            label = { Text("ইমোজি") },
                            modifier = Modifier.width(80.dp)
                        )
                        OutlinedTextField(
                            value = nameBn,
                            onValueChange = { nameBn = it },
                            label = { Text("বাংলা নাম *") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = { nameEn = it },
                        label = { Text("English Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
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
    var division by remember { mutableStateOf("ঢাকা") }
    var district by remember { mutableStateOf("ঢাকা") }
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
                    Text("নতুন জেলা/উপজেলা যুক্ত করুন (সারা বাংলাদেশ সম্প্রসারণ)", fontWeight = FontWeight.Bold)
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
private fun AdminOrdersComplaintsTab(
    uiState: MarketplaceUiState,
    onResolveComplaint: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
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
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("সর্বশেষ অর্ডারসমূহ (${uiState.orders.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(uiState.orders, key = { it.id }) { ord ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${ord.orderCode} • ${ord.farmerName}", fontWeight = FontWeight.Bold)
                        Text("${ord.buyerName} • ${ord.grandTotal.formatBdt()}", style = MaterialTheme.typography.bodySmall)
                    }
                    OrderStatusChip(ord.status, uiState.language)
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
private fun AdminAdMonetizationTab(
    adConfig: AdConfig,
    onSaveAdConfig: (AdConfig) -> Unit
) {
    var adsEnabled by remember(adConfig) { mutableStateOf(adConfig.adsEnabled) }
    var showAdsLabel by remember(adConfig) { mutableStateOf(adConfig.showAdsLabel) }
    var androidAdsEnabled by remember(adConfig) { mutableStateOf(adConfig.androidAdsEnabled) }
    var androidBannerId by remember(adConfig) { mutableStateOf(adConfig.androidBannerId) }
    var androidInterstitialId by remember(adConfig) { mutableStateOf(adConfig.androidInterstitialId) }
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

    val priorityOptions = listOf(
        "ALL" to "সব নেটওয়ার্ক (All)",
        "ADMOB" to "AdMob / Mediation",
        "ADSTERRA" to "Adsterra",
        "MONETAG" to "Monetag",
        "ADSENSE" to "AdSense",
        "CUSTOM" to "Unity/AppLovin/Start.io/Custom",
        "SPONSOR" to "শুধু স্পন্সর"
    )

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
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("মোবাইল অ্যাপ — সব অ্যাড নেটওয়ার্ক মনিটাইজেশন", fontWeight = FontWeight.Bold, color = DarkGreen)
                    Text(
                        "সাপোর্টেড নেটওয়ার্ক: Google AdMob (ও Mediation: Meta, Unity Ads, AppLovin, Start.io), Adsterra, Monetag / PropellerAds, Google AdSense, Custom HTML5 এবং ডাইরেক্ট স্পন্সর।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("সব বিজ্ঞাপন সক্রিয় রাখুন", modifier = Modifier.weight(1f))
                        Switch(checked = adsEnabled, onCheckedChange = { adsEnabled = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("বিজ্ঞাপন লেবেল (Sponsored) দেখান", modifier = Modifier.weight(1f))
                        Switch(checked = showAdsLabel, onCheckedChange = { showAdsLabel = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Android Google AdMob (SDK + Mediation)", modifier = Modifier.weight(1f))
                        Switch(checked = androidAdsEnabled, onCheckedChange = { androidAdsEnabled = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("মোবাইলে Multi-Network Web Ads (Adsterra/Monetag/AdSense/Custom)", modifier = Modifier.weight(1f))
                        Switch(checked = mobileWebAdsEnabled, onCheckedChange = { mobileWebAdsEnabled = it })
                    }
                    Text("মোবাইল অগ্রাধিকার নেটওয়ার্ক:", style = MaterialTheme.typography.labelLarge)
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
                        value = androidBannerId,
                        onValueChange = { androidBannerId = it },
                        label = { Text("AdMob Banner / Mediation Unit ID (ফাঁকা রাখলে Google Test ID)") },
                        placeholder = { Text("ca-app-pub-xxxxxxxxxxxxxxxx/xxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_admob_banner_input")
                    )
                    OutlinedTextField(
                        value = androidInterstitialId,
                        onValueChange = { androidInterstitialId = it },
                        label = { Text("AdMob Interstitial Unit ID (ফাঁকা রাখলে Google Test ID)") },
                        placeholder = { Text("ca-app-pub-xxxxxxxxxxxxxxxx/xxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_admob_interstitial_input")
                    )
                    OutlinedTextField(
                        value = mobileDirectLinkUrl,
                        onValueChange = { mobileDirectLinkUrl = it },
                        label = { Text("মোবাইল Direct Link / SmartLink URL (Adsterra/Monetag)") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mobileCustomAdHtml,
                        onValueChange = { mobileCustomAdHtml = it },
                        label = { Text("মোবাইল কাস্টম HTML5 / Unity / AppLovin / Start.io অ্যাড ট্যাগ") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("সরাসরি স্পন্সর ব্যানার (অ্যাপ + ওয়েবসাইট)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = sponsorEnabled, onCheckedChange = { sponsorEnabled = it })
                    }
                    OutlinedTextField(value = sponsorTitle, onValueChange = { sponsorTitle = it }, label = { Text("স্পন্সর শিরোনাম") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sponsorSubtitle, onValueChange = { sponsorSubtitle = it }, label = { Text("বিবরণ") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sponsorImageUrl, onValueChange = { sponsorImageUrl = it }, label = { Text("ব্যানার ছবির URL (ঐচ্ছিক)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sponsorTargetUrl, onValueChange = { sponsorTargetUrl = it }, label = { Text("লিঙ্ক URL") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = sponsorCtaText, onValueChange = { sponsorCtaText = it }, label = { Text("বাটন টেক্সট") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ক্রস-প্ল্যাটফর্ম অ্যাড নেটওয়ার্কসমূহ (অ্যাপ + ওয়েবসাইট)", fontWeight = FontWeight.Bold, color = DarkGreen)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Google AdSense চালু", modifier = Modifier.weight(1f))
                        Switch(checked = adsenseEnabled, onCheckedChange = { adsenseEnabled = it })
                    }
                    OutlinedTextField(value = adsenseClient, onValueChange = { adsenseClient = it }, label = { Text("AdSense Publisher ID (ca-pub-...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = adsenseTopSlot, onValueChange = { adsenseTopSlot = it }, label = { Text("Top Slot ID") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = adsenseFeedSlot, onValueChange = { adsenseFeedSlot = it }, label = { Text("Feed Slot ID") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = adsenseFooterSlot, onValueChange = { adsenseFooterSlot = it }, label = { Text("Footer Slot ID") }, singleLine = true, modifier = Modifier.weight(1f))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Adsterra Ad Network চালু", modifier = Modifier.weight(1f))
                        Switch(checked = adsterraEnabled, onCheckedChange = { adsterraEnabled = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = adsterraBannerKey, onValueChange = { adsterraBannerKey = it }, label = { Text("Adsterra Banner Key") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = adsterraScriptUrl, onValueChange = { adsterraScriptUrl = it }, label = { Text("invoke.js URL") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = adsterraSocialBarUrl, onValueChange = { adsterraSocialBarUrl = it }, label = { Text("Adsterra Social Bar / Pop Script URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Monetag / PropellerAds চালু", modifier = Modifier.weight(1f))
                        Switch(checked = monetagEnabled, onCheckedChange = { monetagEnabled = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = monetagZoneId, onValueChange = { monetagZoneId = it }, label = { Text("Monetag Zone ID") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = monetagScriptUrl, onValueChange = { monetagScriptUrl = it }, label = { Text("Monetag Script URL") }, singleLine = true, modifier = Modifier.weight(1f))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ezoic / Media.net / Custom HTML বিজ্ঞাপন", modifier = Modifier.weight(1f))
                        Switch(checked = customAdEnabled, onCheckedChange = { customAdEnabled = it })
                    }
                    OutlinedTextField(value = customAdName, onValueChange = { customAdName = it }, label = { Text("কাস্টম নেটওয়ার্ক নাম") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customTopHtml, onValueChange = { customTopHtml = it }, label = { Text("Custom Top Banner HTML / Tag") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSaveAdConfig(
                        adConfig.copy(
                            adsEnabled = adsEnabled,
                            showAdsLabel = showAdsLabel,
                            androidAdsEnabled = androidAdsEnabled,
                            androidBannerId = androidBannerId.trim(),
                            androidInterstitialId = androidInterstitialId.trim(),
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
                    )
                },
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
