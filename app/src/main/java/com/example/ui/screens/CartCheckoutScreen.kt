package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.example.data.local.CartItemEntity
import com.example.data.model.*
import com.example.ui.components.EmptyStateView
import com.example.ui.components.InteractiveBangladeshMapDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.LocationHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartCheckoutScreen(
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onBrowseClick: () -> Unit,
    onUpdateQty: (CartItemEntity, Double) -> Unit,
    onRemoveItem: (String) -> Unit,
    onPlaceOrder: (DeliveryOption, String, PaymentMethod, String) -> Unit,
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
    var selectedPayment by remember { mutableStateOf(PaymentMethod.CASH_ON_PICKUP) }
    var transactionRef by remember { mutableStateOf("") }
    var showMapDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (lang == AppLanguage.BENGALI) "শপিং কার্ট ও চেকআউট" else "Cart & Checkout") },
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
                    title = "আপনার কার্ট খালি",
                    subtitle = "সরাসরি কৃষকদের কাছ থেকে তাজা ফসল কার্টে যোগ করুন।",
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

            // Split-order rule notice
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
                            text = "নিয়ম: প্রতিটি অর্ডার একজন কৃষকের জন্য (১ মণ = ৪০ কেজি)। আপনার কার্টে ${grouped.size} জন কৃষকের পণ্য থাকায় ${grouped.size}টি আলাদা অর্ডার তৈরি হবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = InfoBlue
                        )
                    }
                }
            }

            // Grouped farmer sections
            grouped.forEach { (farmerId, items) ->
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
                                text = "কৃষক: ${items.first().farmerName}",
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

            // Payment Method Selection
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("পেমেন্ট পদ্ধতি", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        PaymentMethod.entries.forEach { method ->
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
                        if (selectedPayment.isDigital) {
                            Surface(color = HarvestGoldLight, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    text = "নিরাপদ পেমেন্ট নীতি: শুধু ট্রানজেকশন/স্ক্রিনশট দিয়ে অর্ডার কনফার্ম হবে না। কৃষকের টাকা প্রাপ্তি নিশ্চিতকরণ এবং অ্যাডমিন যাচাইয়ের পর অর্ডার Confirmed হবে।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78350F),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            OutlinedTextField(
                                value = transactionRef,
                                onValueChange = { transactionRef = it },
                                label = { Text("${selectedPayment.labelBn} ট্রানজেকশন রেফারেন্স *") },
                                placeholder = { Text("যেমন: TXN-8A94K2") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("checkout_txn_ref_input")
                            )
                        }
                    }
                }
            }

            // Cost Breakdown (Transparent, Zero Commission)
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
                        SummaryRow("পণ্যের মোট মূল্য", productTotal.formatBdt())
                        SummaryRow("ডেলিভারি চার্জ", deliveryTotal.formatBdt())
                        SummaryRow("প্ল্যাটফর্ম/দালাল কমিশন", "৳ ০ (সম্পূর্ণ ফ্রি)")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        SummaryRow("সর্বমোট (Grand Total)", grandTotal.formatBdt(), bold = true)
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (isUserInactive) {
                            onOpenActivation()
                        } else {
                            onPlaceOrder(selectedDelivery, deliveryAddress, selectedPayment, transactionRef)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("place_order_button")
                ) {
                    Text(
                        text = if (isUserInactive) "প্রথমে অ্যাকাউন্ট সক্রিয় করুন" else "অর্ডার পাঠান (${grandTotal.formatBdt()})",
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
