package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.FarmerPaymentSettingsEntity
import com.example.data.local.OrderItemEntity
import com.example.data.local.OrderPaymentEntity
import com.example.data.model.OrderPaymentStatus
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.ProductUnit
import com.example.data.model.formatBdt
import com.example.data.model.formatQty
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.formatDateBn
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: String,
    uiState: MarketplaceUiState,
    orderItemsFlow: Flow<List<OrderItemEntity>>,
    orderPaymentsFlow: Flow<List<OrderPaymentEntity>> = flowOf(emptyList()),
    farmerPaymentSettingsFlow: Flow<FarmerPaymentSettingsEntity?> = flowOf(null),
    onBack: () -> Unit,
    onUpdateStatus: (OrderStatus) -> Unit,
    onSubmitOrderAdvancePayment: (
        amount: Double,
        paymentMethod: PaymentMethod,
        senderNumber: String,
        transactionId: String,
        referenceNote: String
    ) -> Unit = { _, _, _, _, _ -> },
    onFarmerVerifyOrderPayment: (paymentId: String, approved: Boolean, reason: String) -> Unit = { _, _, _ -> },
    onApproveZeroAdvanceOrder: () -> Unit = {},
    onSubmitReview: (productId: String, rating: Int, comment: String) -> Unit,
    onSubmitComplaint: (subject: String, description: String) -> Unit
) {
    val context = LocalContext.current
    val order = uiState.orders.firstOrNull { it.id == orderId }
    val items by orderItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val orderPayments by orderPaymentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val farmerSettings by farmerPaymentSettingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val activeUser = uiState.activeUser

    var showReviewDialog by remember { mutableStateOf(false) }
    var showComplaintDialog by remember { mutableStateOf(false) }
    var rejectTargetPayment by remember { mutableStateOf<OrderPaymentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(order?.orderCode ?: "অর্ডার ও পেমেন্ট ট্র্যাকিং") },
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
        val isClosed = currentStatus == OrderStatus.REJECTED || currentStatus == OrderStatus.CANCELLED
        val verifiedAdvance = order.verifiedAdvanceTotal
        val remainingDue = max(0.0, order.grandTotal - verifiedAdvance)
        val pendingPayments = orderPayments.filter { it.status == OrderPaymentStatus.PENDING_VERIFICATION.name }
        val resolvedFarmerSettings = farmerSettings
            ?: uiState.farmerPaymentSettings.firstOrNull { it.farmerId == order.farmerId }

        var advanceAmountText by remember(remainingDue) {
            mutableStateOf(if (remainingDue > 0) remainingDue.toLong().toString() else "0")
        }
        var selectedPayMethod by remember(order.paymentMethod) {
            mutableStateOf(
                listOf(PaymentMethod.BKASH, PaymentMethod.NAGAD, PaymentMethod.ROCKET, PaymentMethod.BANK_TRANSFER)
                    .firstOrNull { it.name == order.paymentMethod }
                    ?: PaymentMethod.BKASH
            )
        }
        var senderNumber by remember(activeUser?.phone) {
            mutableStateOf(order.paymentSenderNumber.ifBlank { activeUser?.phone.orEmpty() })
        }
        var txIdInput by remember { mutableStateOf("") }
        var refNoteInput by remember { mutableStateOf("") }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Wholesale Financial & Payment Security Summary
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("পাইকারি অর্ডার ও সরাসরি পেমেন্ট হিসাব", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OrderStatusChip(order.status, uiState.language)
                        }

                        KeyValRow("মোট পাইকারি অর্ডার মূল্য", order.grandTotal.formatBdt(), bold = true)
                        KeyValRow("নিশ্চিতকৃত অগ্রিম (Verified Advance)", verifiedAdvance.formatBdt(), bold = true)
                        KeyValRow("অবশিষ্ট বকেয়া (Remaining Due)", remainingDue.formatBdt(), bold = true)
                        KeyValRow("চূড়ান্ত নিশ্চিতকরণ নীতি", "সরাসরি ক্রেতা-থেকে-কৃষক পেমেন্ট (কৃষকের যাচাই আবশ্যক)")

                        Surface(
                            color = HarvestGoldLight,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "প্ল্যাটফর্ম কখনো অগ্রিম টাকা গ্রহণ বা কেটে রাখে না। শুধুমাত্র কৃষক নিজের বকেয়া/ব্যালেন্স যাচাই করে নিশ্চিত করলে অগ্রিম ব্যালেন্স যুক্ত হবে ও অর্ডার Confirmed হবে।",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF78350F),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 2. Farmer's Direct Payment Details (Strictly ONLY this order's farmer)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LightGreen),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "কৃষকের নিবন্ধিত পেমেন্ট নম্বর (${order.farmerName})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen
                        )
                        if (resolvedFarmerSettings == null || !resolvedFarmerSettings.hasAnyNumber()) {
                            Text(
                                text = "কৃষক এখনো ডিজিটাল পেমেন্ট নম্বর যুক্ত করেননি।",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkGreen
                            )
                        } else {
                            if (resolvedFarmerSettings.bKashNumber.isNotBlank()) {
                                KeyValRow("বিকাশ (bKash)", resolvedFarmerSettings.bKashNumber)
                            }
                            if (resolvedFarmerSettings.nagadNumber.isNotBlank()) {
                                KeyValRow("নগদ (Nagad)", resolvedFarmerSettings.nagadNumber)
                            }
                            if (resolvedFarmerSettings.rocketNumber.isNotBlank()) {
                                KeyValRow("রকেট (Rocket)", resolvedFarmerSettings.rocketNumber)
                            }
                            if (resolvedFarmerSettings.bankAccountInfo.isNotBlank()) {
                                KeyValRow("ব্যাংক একাউন্ট", resolvedFarmerSettings.bankAccountInfo)
                            }
                        }
                    }
                }
            }

            // 3. Wholesale Items & Order Metadata
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("পাইকারি পণ্যের তালিকা (১ মণ = ৪০ কেজি)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${item.productNameBn} (${item.quantity.formatQty()} মণ • ~${(item.quantity * ProductUnit.MON_TO_KG).formatQty()} কেজি)")
                                Text(item.lineTotal.formatBdt(), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider()
                        KeyValRow("পণ্য মূল্য", order.productTotal.formatBdt())
                        KeyValRow("ডেলিভারি চার্জ", order.deliveryCharge.formatBdt())
                        KeyValRow("ডেলিভারি ঠিকানা", order.deliveryAddress)
                    }
                }
            }

            // 4. Payment History & Verification Audit Section (Supports multiple verified partial payments)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "পেমেন্ট ইতিহাস ও যাচাই লগ (${orderPayments.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen
                        )
                        if (orderPayments.isEmpty()) {
                            Text(
                                text = "এখনো কোনো অগ্রিম পেমেন্ট জমা দেওয়া হয়নি।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            orderPayments.forEach { pay ->
                                val payStatus = OrderPaymentStatus.fromName(pay.status)
                                val (chipBg, chipFg) = when (payStatus) {
                                    OrderPaymentStatus.CONFIRMED -> LightGreen to DarkGreen
                                    OrderPaymentStatus.REJECTED -> AlertRedLight to AlertRed
                                    OrderPaymentStatus.PENDING_VERIFICATION -> HarvestGoldLight to Color(0xFF92400E)
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${pay.amount.formatBdt()} • ${pay.paymentMethod}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(color = chipBg, shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    text = payStatus.display(uiState.language),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = chipFg,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text("TxID: ${pay.transactionId} • প্রেরক: ${pay.senderNumber.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                                        if (pay.receiverNumber.isNotBlank()) {
                                            Text("কৃষকের নম্বর: ${pay.receiverNumber}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (pay.referenceNote.isNotBlank()) {
                                            Text("রেফারেন্স: ${pay.referenceNote}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text("জমা: ${formatDateBn(pay.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (pay.rejectionReason.isNotBlank()) {
                                            Text("প্রত্যাখ্যানের কারণ: ${pay.rejectionReason}", style = MaterialTheme.typography.labelSmall, color = AlertRed)
                                        }
                                        if (isFarmerOwner && payStatus == OrderPaymentStatus.PENDING_VERIFICATION) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { onFarmerVerifyOrderPayment(pay.id, true, "") },
                                                    modifier = Modifier.weight(1f).testTag("farmer_confirm_payment_${pay.id}")
                                                ) {
                                                    Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("টাকা পেয়েছি (নিশ্চিত)")
                                                }
                                                OutlinedButton(
                                                    onClick = { rejectTargetPayment = pay },
                                                    modifier = Modifier.weight(0.85f).testTag("farmer_reject_payment_${pay.id}")
                                                ) {
                                                    Text("প্রত্যাখ্যান")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Buyer Partial / Advance Payment Submission Form (Direct Buyer-to-Farmer)
            if (isBuyerOwner && !isClosed && remainingDue > 0.0) {
                val parsedAmt = advanceAmountText.toDoubleOrNull() ?: 0.0
                val amountValid = parsedAmt > 0.0 && parsedAmt <= remainingDue + 0.01
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "নতুন অগ্রিম / আংশিক পেমেন্ট জমা দিন",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = InfoBlue
                            )
                            Text(
                                text = "সরাসরি উপরের কৃষকের নম্বরে টাকা পাঠিয়ে পরিমাণ, Transaction ID ও রেফারেন্স দিন (অবশিষ্ট বকেয়া: ${remainingDue.formatBdt()})।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(PaymentMethod.BKASH, PaymentMethod.NAGAD, PaymentMethod.ROCKET, PaymentMethod.BANK_TRANSFER)) { m ->
                                    FilterChip(
                                        selected = selectedPayMethod == m,
                                        onClick = { selectedPayMethod = m },
                                        label = { Text(m.labelBn) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = advanceAmountText,
                                onValueChange = { advanceAmountText = it },
                                label = { Text("Advance Payment Amount (৳) — সর্বোচ্চ ${remainingDue.formatBdt()}") },
                                singleLine = true,
                                isError = advanceAmountText.isNotBlank() && !amountValid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().testTag("order_advance_amount_input")
                            )
                            OutlinedTextField(
                                value = senderNumber,
                                onValueChange = { senderNumber = it },
                                label = { Text("প্রেরকের মোবাইল নম্বর") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("order_payment_sender_input")
                            )
                            OutlinedTextField(
                                value = txIdInput,
                                onValueChange = { txIdInput = it.uppercase() },
                                label = { Text("Transaction ID *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("order_payment_txid_input")
                            )
                            OutlinedTextField(
                                value = refNoteInput,
                                onValueChange = { refNoteInput = it },
                                label = { Text("পেমেন্ট রেফারেন্স নোট (ঐচ্ছিক)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("order_payment_ref_note_input")
                            )
                            Button(
                                onClick = {
                                    onSubmitOrderAdvancePayment(
                                        parsedAmt,
                                        selectedPayMethod,
                                        senderNumber.trim(),
                                        txIdInput.trim(),
                                        refNoteInput.trim()
                                    )
                                    txIdInput = ""
                                    refNoteInput = ""
                                },
                                enabled = amountValid && txIdInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_order_payment_button")
                            ) {
                                Text("অগ্রিম পেমেন্ট জমা দিন", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 6. Counterparty Direct Contact
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
                                context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$targetPhone".toUri()))
                            }
                        }) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সরাসরি কল")
                        }
                    }
                }
            }

            // 7. Farmer Zero-Advance Approval + Order Fulfillment Controls
            if (isFarmerOwner) {
                if ((currentStatus == OrderStatus.PAYMENT_PENDING || currentStatus == OrderStatus.PENDING) &&
                    pendingPayments.isEmpty() && verifiedAdvance <= 0.0
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = HarvestGoldLight),
                            border = BorderStroke(1.dp, HarvestGold)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "শূন্য-অগ্রিম (৳০) পাইকারি অর্ডার",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF78350F)
                                )
                                Text(
                                    text = "ক্রেতা কোনো অগ্রিম ছাড়াই অর্ডার করেছেন। আপনি চাইলে শূন্য অগ্রিমে সরাসরি অনুমোদন (Confirmed) করতে পারেন অথবা অগ্রিম পেমেন্টের জন্য অপেক্ষা করতে পারেন।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78350F)
                                )
                                Button(
                                    onClick = onApproveZeroAdvanceOrder,
                                    modifier = Modifier.fillMaxWidth().testTag("farmer_approve_zero_advance_button")
                                ) {
                                    Text("শূন্য-অগ্রিম অর্ডার অনুমোদন করুন (Confirm)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("কৃষকের অর্ডার অ্যাকশন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            when (currentStatus) {
                                OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING -> {
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
                                OrderStatus.CONFIRMED, OrderStatus.ACCEPTED -> {
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

            // 8. Buyer Completion, Review & Complaint Controls
            if (isBuyerOwner) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (currentStatus in setOf(OrderStatus.DELIVERED, OrderStatus.READY_FOR_PICKUP, OrderStatus.SHIPPED, OrderStatus.CONFIRMED)) {
                            val canComplete = order.farmerPaymentConfirmed || order.paymentStatus == "ZERO_ADVANCE_APPROVED"
                            Button(
                                onClick = { onUpdateStatus(OrderStatus.COMPLETED) },
                                enabled = canComplete,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("complete_order_button")
                            ) {
                                Text(
                                    if (canComplete) "পণ্য বুঝে পেয়েছি — সম্পন্ন চিহ্নিত করুন"
                                    else "কৃষক অর্ডার/অগ্রিম অনুমোদন করলে সম্পন্ন করা যাবে"
                                )
                            }
                        }
                        if (currentStatus == OrderStatus.PENDING || currentStatus == OrderStatus.PAYMENT_PENDING) {
                            OutlinedButton(
                                onClick = { onUpdateStatus(OrderStatus.CANCELLED) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("অর্ডার বাতিল করুন")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CONFIRMED) {
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

    rejectTargetPayment?.let { pay ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTargetPayment = null },
            title = { Text("পেমেন্ট প্রত্যাখ্যান করুন (${pay.amount.formatBdt()})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TxID: ${pay.transactionId}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("প্রত্যাখ্যানের কারণ *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reject_payment_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onFarmerVerifyOrderPayment(pay.id, false, reason.ifBlank { "অ্যাকাউন্টে টাকা পাওয়া যায়নি" })
                        rejectTargetPayment = null
                    }
                ) { Text("প্রত্যাখ্যান নিশ্চিত করুন") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTargetPayment = null }) { Text("বাতিল") }
            }
        )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (bold) PrimaryGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f)
        )
    }
}
