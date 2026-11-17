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
import com.example.data.model.formatBdt
import com.example.ui.components.OrderStatusChip
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onVerifyFarmer: (farmerId: String, approved: Boolean) -> Unit,
    onSendNotification: (targetUserId: String, targetRole: String, titleBn: String, messageBn: String) -> Unit,
    onAddCategory: (nameBn: String, nameEn: String, emoji: String) -> Unit,
    onAddLocation: (division: String, district: String, upazila: String, union: String, village: String) -> Unit,
    onResolveComplaint: (complaintId: String) -> Unit,
    onBuildCsvReport: () -> String
) {
    val context = LocalContext.current
    val stats = uiState.adminStats
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("সারসংক্ষেপ", "কৃষক যাচাই", "নোটিফিকেশন পাঠান", "ক্যাটাগরি", "এলাকা", "অর্ডার ও অভিযোগ", "অডিট লগ")

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
                1 -> AdminVerificationTab(uiState = uiState, onVerifyFarmer = onVerifyFarmer)
                2 -> AdminNotificationsTab(uiState = uiState, onSendNotification = onSendNotification)
                3 -> AdminCategoriesTab(uiState = uiState, onAddCategory = onAddCategory)
                4 -> AdminLocationsTab(uiState = uiState, onAddLocation = onAddLocation)
                5 -> AdminOrdersComplaintsTab(uiState = uiState, onResolveComplaint = onResolveComplaint)
                6 -> AdminAuditLogTab(uiState = uiState)
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
                AdminKpiCard("সক্রিয় ফসল", "${stats.activeProducts}", Color(0xFFB45309), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("আজকের অর্ডার", "${stats.todaysOrders}", PrimaryGreen, Modifier.weight(1f))
                AdminKpiCard("সম্পন্ন অর্ডার", "${stats.completedOrders}", DarkGreen, Modifier.weight(1f))
            }
        }
        item {
            AdminKpiCard("মোট পণ্য বিক্রয় (৳)", stats.totalSalesAmount.formatBdt(), PrimaryGreen, Modifier.fillMaxWidth())
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
    onVerifyFarmer: (String, Boolean) -> Unit
) {
    val sortedFarmers = uiState.farmers.sortedBy { if (it.verificationStatus == "PENDING") 0 else 1 }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sortedFarmers, key = { it.id }) { farmer ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(farmer.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Surface(
                            color = if (farmer.verificationStatus == "VERIFIED") LightGreen else HarvestGoldLight,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = farmer.verificationStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text("${farmer.upazila}, ${farmer.district} • ${farmer.phone}", style = MaterialTheme.typography.bodySmall)
                    Text("মাস্কড এনআইডি: ${farmer.nidMasked}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (farmer.verificationStatus != "VERIFIED") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onVerifyFarmer(farmer.id, true) },
                                modifier = Modifier.weight(1f).testTag("verify_approve_${farmer.id}")
                            ) { Text("অনুমোদন দিন") }
                            OutlinedButton(
                                onClick = { onVerifyFarmer(farmer.id, false) },
                                modifier = Modifier.weight(1f)
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
