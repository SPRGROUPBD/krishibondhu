package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CartItemEntity
import com.example.data.model.*
import com.example.ui.components.EmptyStateView
import com.example.ui.components.InteractiveBangladeshMapDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.LocationHelper
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartCheckoutScreen(
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onBrowseClick: () -> Unit,
    onUpdateQty: (CartItemEntity, Double) -> Unit,
    onRemoveItem: (String) -> Unit,
    onPlaceOrder: (
        deliveryOption: DeliveryOption,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        transactionRef: String,
        advanceByFarmer: Map<String, Double>,
        senderNumber: String,
        referenceNote: String
    ) -> Unit,
    onOpenActivation: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = uiState.language
    val activeUser = uiState.activeUser
    val isUserInactive = activeUser != null && !activeUser.isMarketplaceActive()
    val grouped = remember(uiState.cartItems) { uiState.cartItems.groupBy { it.farmerId } }
    var selectedDelivery by remember { mutableStateOf(DeliveryOption.FARM_PICKUP) }
    var deliveryAddress by remember(uiState.activeUser) {
        mutableStateOf(
            listOfNotNull(
                uiState.activeUser?.village?.takeIf { it.isNotBlank() },
                uiState.activeUser?.upazila?.takeIf { it.isNotBlank() },
                uiState.activeUser?.district?.takeIf { it.isNotBlank() }
            ).joinToString(", ")
        )
    }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.BKASH) }
    var senderNumber by remember(uiState.activeUser) { mutableStateOf(uiState.activeUser?.phone.orEmpty()) }
    var transactionRef by remember { mutableStateOf("") }
    var referenceNote by remember { mutableStateOf("") }
    var showMapDialog by remember { mutableStateOf(false) }

    val advanceInputByFarmer = remember { mutableStateMapOf<String, String>() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            scope.launch {
                LocationHelper.detectCurrentLocation(context)
                    .onSuccess { deliveryAddress = it.addressLine }
                    .onFailure { showMapDialog = true }
            }
        } else {
            showMapDialog = true
        }
    }

    val productTotal = uiState.cartItems.sumOf { it.pricePerUnit * it.quantity }
    val deliveryTotal = if (selectedDelivery == DeliveryOption.FARM_PICKUP) 0.0
    else grouped.values.sumOf { farmerList -> farmerList.maxOfOrNull { it.deliveryChargeEstimate } ?: 0.0 }
    val grandTotal = productTotal + deliveryTotal

    val parsedAdvanceByFarmer = grouped.keys.associateWith { fid ->
        val farmerItems = grouped[fid].orEmpty()
        val fProd = farmerItems.sumOf { it.pricePerUnit * it.quantity }
        val fDeliv = if (selectedDelivery == DeliveryOption.FARM_PICKUP) 0.0 else (farmerItems.maxOfOrNull { it.deliveryChargeEstimate } ?: 0.0)
        val fTotal = fProd + fDeliv
        val raw = advanceInputByFarmer[fid]?.toDoubleOrNull() ?: 0.0
        raw.coerceIn(0.0, fTotal)
    }
    val totalRequestedAdvance = parsedAdvanceByFarmer.values.sum()
    val totalRemainingDue = max(0.0, grandTotal - totalRequestedAdvance)
    val needsDigitalTxnProof = totalRequestedAdvance > 0.0 && selectedPayment.isDigital
    val hasInvalidAdvance = grouped.any { (fid, items) ->
        val fProd = items.sumOf { it.pricePerUnit * it.quantity }
        val fDeliv = if (selectedDelivery == DeliveryOption.FARM_PICKUP) 0.0 else (items.maxOfOrNull { it.deliveryChargeEstimate } ?: 0.0)
        val fTotal = fProd + fDeliv
        val rawStr = advanceInputByFarmer[fid]
        if (rawStr.isNullOrBlank()) false
        else {
            val v = rawStr.toDoubleOrNull()
            v == null || v < 0.0 || v > fTotal
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (lang == AppLanguage.BENGALI) "পাইকারি কার্ট ও চেকআউট" else "Wholesale Cart & Checkout") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("cart_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.cartItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    emoji = "🛒",
                    title = "আপনার পাইকারি কার্ট খালি",
                    subtitle = "সরাসরি কৃষকদের কাছ থেকে পাইকারি ফসল কার্টে যোগ করুন।",
                    actionText = "ফসল দেখুন",
                    onAction = onBrowseClick
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isUserInactive) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = HarvestGoldLight),
                        border = BorderStroke(1.dp, HarvestGold),
                        modifier = Modifier.testTag("cart_activation_gate")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "অর্ডার করতে অ্যাকাউন্ট সক্রিয় থাকা আবশ্যক",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F)
                            )
                            Text(
                                text = "৩০০ টাকা অ্যাক্টিভেশন ফি জমা দিয়ে ২ বছরের জন্য অ্যাকাউন্ট সক্রিয়/রিনিউ করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F)
                            )
                            Button(onClick = onOpenActivation, modifier = Modifier.fillMaxWidth()) {
                                Text("অ্যাকাউন্ট সক্রিয় / রিনিউ করুন (৩০০৳)")
                            }
                        }
                    }
                }
            }

            // Wholesale & Direct Farmer Payment Notice
            item {
                Surface(
                    color = InfoBlueLight,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = InfoBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "শুধুমাত্র পাইকারি বিক্রয় (১ মণ = ৪০ কেজি)। আপনি যেকোনো পরিমাণ অগ্রিম (০ থেকে মোট মূল্য পর্যন্ত) সরাসরি কৃষকের নম্বরে পাঠাতে পারবেন। প্ল্যাটফর্ম কখনোই অগ্রিম টাকা গ্রহণ বা জমা রাখে না।",
                            style = MaterialTheme.typography.bodySmall,
                            color = InfoBlue
                        )
                    }
                }
            }

            // Grouped farmer sections with isolated payment channels & editable Advance Payment Amount
            grouped.forEach { (farmerId, items) ->
                val fProd = items.sumOf { it.pricePerUnit * it.quantity }
                val fDeliv = if (selectedDelivery == DeliveryOption.FARM_PICKUP) 0.0 else (items.maxOfOrNull { it.deliveryChargeEstimate } ?: 0.0)
                val fTotal = fProd + fDeliv
                // Strictly ONLY look up THIS farmer's registered payment settings — never expose other farmers' numbers
                val farmerSettings = uiState.farmerPaymentSettings.firstOrNull { it.farmerId == farmerId }
                val advanceRaw = advanceInputByFarmer[farmerId] ?: "0"
                val advanceParsed = advanceRaw.toDoubleOrNull()
                val advanceInvalid = advanceRaw.isNotBlank() && (advanceParsed == null || advanceParsed < 0.0 || advanceParsed > fTotal)
                val clampedAdvance = (advanceParsed ?: 0.0).coerceIn(0.0, fTotal)
                val farmerRemainingDue = max(0.0, fTotal - clampedAdvance)

                item(key = "group_$farmerId") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "কৃষক: ${items.first().farmerName} (পাইকারি মোট: ${fTotal.formatBdt()})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen
                            )
                            HorizontalDivider()
                            items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.productNameBn, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "${item.pricePerUnit.formatBdt()}/মণ (~${(item.quantity * ProductUnit.MON_TO_KG).formatQty()} কেজি)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { onUpdateQty(item, item.quantity - 1.0) }) {
                                            Icon(Icons.Filled.Remove, contentDescription = "কমান")
                                        }
                                        Text("${item.quantity.formatQty()} মণ", fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { onUpdateQty(item, item.quantity + 1.0) }) {
                                            Icon(Icons.Filled.Add, contentDescription = "বাড়ান")
                                        }
                                        IconButton(onClick = { onRemoveItem(item.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "মুছুন", tint = AlertRed)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()
                            // Isolated Farmer Payment Numbers (only for this farmer)
                            Surface(
                                color = LightGreen,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "কৃষকের নিবন্ধিত সরাসরি পেমেন্ট নম্বর",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkGreen
                                        )
                                    }
                                    if (farmerSettings == null || !farmerSettings.hasAnyNumber()) {
                                        Text(
                                            text = "কৃষক এখনো ডিজিটাল পেমেন্ট নম্বর যুক্ত করেননি — শূন্য (৳০) অগ্রিম দিয়ে অর্ডার পাঠাতে পারেন।",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF92400E)
                                        )
                                    } else {
                                        if (farmerSettings.bKashNumber.isNotBlank()) {
                                            Text("bKash: ${farmerSettings.bKashNumber}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = DarkGreen)
                                        }
                                        if (farmerSettings.nagadNumber.isNotBlank()) {
                                            Text("Nagad: ${farmerSettings.nagadNumber}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = DarkGreen)
                                        }
                                        if (farmerSettings.rocketNumber.isNotBlank()) {
                                            Text("Rocket: ${farmerSettings.rocketNumber}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = DarkGreen)
                                        }
                                        if (farmerSettings.bankAccountInfo.isNotBlank()) {
                                            Text("Bank: ${farmerSettings.bankAccountInfo}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = DarkGreen)
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = advanceRaw,
                                onValueChange = { advanceInputByFarmer[farmerId] = it },
                                label = { Text("Advance Payment Amount / অগ্রিম পেমেন্ট (৳) — ০ থেকে ${fTotal.formatBdt()}") },
                                singleLine = true,
                                isError = advanceInvalid,
                                supportingText = {
                                    if (advanceInvalid) {
                                        Text("অগ্রিম ০ থেকে ${fTotal.formatBdt()} এর মধ্যে হতে হবে", color = AlertRed)
                                    } else {
                                        Text("অবশিষ্ট বকেয়া (Remaining Due): ${farmerRemainingDue.formatBdt()} • অগ্রিম ৳০ দিলে PAYMENT_PENDING হিসেবে অর্ডার যাবে")
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("checkout_advance_input_$farmerId")
                            )
                        }
                    }
                }
            }

            // Delivery Options A & B
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ডেলিভারি পদ্ধতি নির্বাচন করুন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        DeliveryOption.entries.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDelivery == option,
                                    onClick = { selectedDelivery = option }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(option.labelBn, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (option == DeliveryOption.FARM_PICKUP) "ডেলিভারি চার্জ: ৳ ০" else "আলাদা ডেলিভারি চার্জ প্রযোজ্য",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (selectedDelivery == DeliveryOption.DIRECT_DELIVERY) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = deliveryAddress,
                                    onValueChange = { deliveryAddress = it },
                                    label = { Text("ডেলিভারি ঠিকানা *") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("checkout_address_input")
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        if (LocationHelper.hasLocationPermission(context)) {
                                            scope.launch {
                                                LocationHelper.detectCurrentLocation(context)
                                                    .onSuccess { deliveryAddress = it.addressLine }
                                                    .onFailure { showMapDialog = true }
                                            }
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("checkout_auto_location_button")
                                ) {
                                    Icon(Icons.Filled.MyLocation, contentDescription = "ম্যাপ থেকে ঠিকানা নিন")
                                }
                            }
                        }
                    }
                }
            }

            // Direct Payment Method & Advance Proof Selection
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("সরাসরি কৃষককে অগ্রিম পেমেন্ট পদ্ধতি", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf(
                            PaymentMethod.BKASH,
                            PaymentMethod.NAGAD,
                            PaymentMethod.ROCKET,
                            PaymentMethod.BANK_TRANSFER,
                            PaymentMethod.CASH_ON_PICKUP
                        ).forEach { method ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPayment == method,
                                    onClick = { selectedPayment = method }
                                )
                                Text(method.display(lang), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (totalRequestedAdvance > 0.0) {
                            Surface(color = HarvestGoldLight, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    text = "নিরাপদ পেমেন্ট নীতি: শুধু ট্রানজেকশন/রেফারেন্স দিলে অটো-কনফার্ম হবে না। কৃষক নিজের অ্যাকাউন্টে টাকা যাচাই করে অনুমোদন করলে অগ্রিম ব্যালেন্স যুক্ত হবে ও অর্ডার Confirmed হবে।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78350F),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            OutlinedTextField(
                                value = senderNumber,
                                onValueChange = { senderNumber = it },
                                label = { Text("প্রেরকের মোবাইল / অ্যাকাউন্ট নম্বর") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("checkout_sender_number_input")
                            )
                            OutlinedTextField(
                                value = transactionRef,
                                onValueChange = { transactionRef = it.uppercase() },
                                label = { Text("${selectedPayment.labelBn} Transaction ID${if (needsDigitalTxnProof) " *" else ""}") },
                                placeholder = { Text("যেমন: TXN-8A94K2") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("checkout_txn_ref_input")
                            )
                            OutlinedTextField(
                                value = referenceNote,
                                onValueChange = { referenceNote = it },
                                label = { Text("পেমেন্ট রেফারেন্স নোট (ঐচ্ছিক)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("checkout_reference_note_input")
                            )
                        }
                    }
                }
            }

            // Wholesale Cost & Remaining Due Breakdown
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LightGreen),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SummaryRow("পাইকারি পণ্যের মোট মূল্য", productTotal.formatBdt())
                        SummaryRow("ডেলিভারি চার্জ", deliveryTotal.formatBdt())
                        SummaryRow("প্ল্যাটফর্ম/দালাল কমিশন", "৳ ০ (সরাসরি কৃষককে পেমেন্ট)")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        SummaryRow("মোট পাইকারি অর্ডার মূল্য", grandTotal.formatBdt(), bold = true)
                        SummaryRow("জমাকৃত অগ্রিম (যাচাই অপেক্ষমাণ)", totalRequestedAdvance.formatBdt())
                        SummaryRow("সম্ভাব্য অবশিষ্ট বকেয়া", totalRemainingDue.formatBdt(), bold = true)
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (isUserInactive) {
                            onOpenActivation()
                        } else {
                            onPlaceOrder(
                                selectedDelivery,
                                deliveryAddress,
                                selectedPayment,
                                transactionRef.trim(),
                                parsedAdvanceByFarmer,
                                senderNumber.trim(),
                                referenceNote.trim()
                            )
                        }
                    },
                    enabled = !hasInvalidAdvance && (!needsDigitalTxnProof || transactionRef.isNotBlank()),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("place_order_button")
                ) {
                    Text(
                        text = if (isUserInactive) {
                            "প্রথমে অ্যাকাউন্ট সক্রিয় করুন"
                        } else if (totalRequestedAdvance > 0.0) {
                            "অর্ডার ও অগ্রিম তথ্য পাঠান (${totalRequestedAdvance.formatBdt()} অগ্রিম)"
                        } else {
                            "পাইকারি অর্ডার পাঠান (${grandTotal.formatBdt()})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showMapDialog) {
        InteractiveBangladeshMapDialog(
            initialDistrictBn = uiState.activeUser?.district ?: "কুষ্টিয়া",
            onDismiss = { showMapDialog = false },
            onLocationConfirmed = { detected ->
                deliveryAddress = detected.addressLine
                showMapDialog = false
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = DarkGreen
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = DarkGreen
        )
    }
}
