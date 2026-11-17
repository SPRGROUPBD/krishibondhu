package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.OrderItemEntity
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.ProductUnit
import com.example.data.model.formatBdt
import com.example.data.model.formatQty
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.formatDateBn
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.MediaCaptureHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: String,
    uiState: MarketplaceUiState,
    orderItemsFlow: Flow<List<OrderItemEntity>>,
    onBack: () -> Unit,
    onUpdateStatus: (OrderStatus) -> Unit,
    onSubmitOrderPayment: (
        paymentMethod: PaymentMethod,
        senderNumber: String,
        transactionReference: String,
        receiptUri: String
    ) -> Unit = { _, _, _, _ -> },
    onFarmerConfirmPaymentReceived: () -> Unit = {},
    onSubmitReview: (productId: String, rating: Int, comment: String) -> Unit,
    onSubmitComplaint: (subject: String, description: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
        val isClosed = currentStatus == OrderStatus.REJECTED || currentStatus == OrderStatus.CANCELLED
        val paymentSubmitted = order.paymentStatus == "SUBMITTED" ||
            order.paymentStatus == "VERIFIED" ||
            order.paymentStatus == "PAID" ||
            order.transactionReference.isNotBlank() ||
            order.paymentReceiptUri.isNotBlank()
        val dualConfirmed = order.farmerPaymentConfirmed && order.adminPaymentVerified

        var selectedPayMethod by remember(order.paymentMethod) {
            mutableStateOf(
                runCatching { PaymentMethod.valueOf(order.paymentMethod) }.getOrDefault(PaymentMethod.BKASH)
            )
        }
        var senderNumber by remember(order.paymentSenderNumber, activeUser?.phone) {
            mutableStateOf(order.paymentSenderNumber.ifBlank { activeUser?.phone.orEmpty() })
        }
        var txRef by remember(order.transactionReference) { mutableStateOf(order.transactionReference) }
        var receiptUri by remember(order.paymentReceiptUri) { mutableStateOf(order.paymentReceiptUri) }
        var processingImage by remember { mutableStateOf(false) }
        var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

        val handleSelectedImage: (Uri) -> Unit = { uri ->
            processingImage = true
            scope.launch {
                val encoded = MediaCaptureHelper.encodeUriToCompactDataUrl(context, uri)
                receiptUri = encoded ?: uri.toString()
                processingImage = false
            }
        }
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri -> if (uri != null) handleSelectedImage(uri) }
        )
        val fileDocumentLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri -> if (uri != null) handleSelectedImage(uri) }
        )
        val cameraTakePictureLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            val captured = pendingCameraUri
            if (success && captured != null) handleSelectedImage(captured)
        }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val uri = MediaCaptureHelper.createCameraImageUri(context)
                pendingCameraUri = uri
                cameraTakePictureLauncher.launch(uri)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 5-Step Order & Payment Security Timeline
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
                            Text("৫-ধাপের অর্ডার ও পেমেন্ট নিরাপত্তা", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OrderStatusChip(order.status, uiState.language)
                        }

                        val steps = listOf(
                            ("১. ক্রেতা অর্ডার করেছেন (মণ হিসেবে)") to (!isClosed),
                            ("২. কৃষক অর্ডার গ্রহণ করেছেন") to (currentStatus != OrderStatus.PENDING && !isClosed),
                            ("৩. ক্রেতা পেমেন্ট ও রসিদ/TxID জমা দিয়েছেন") to paymentSubmitted,
                            ("৪. কৃষক 'টাকা বুঝে পেয়েছি' নিশ্চিত করেছেন") to order.farmerPaymentConfirmed,
                            ("৫. অ্যাডমিন যাচাই ও চূড়ান্ত নিশ্চিতকরণ (Confirmed)") to dualConfirmed
                        )
                        steps.forEach { (label, reached) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (reached) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
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

                        Surface(
                            color = HarvestGoldLight,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "নিরাপত্তা নিয়ম: শুধুমাত্র ক্রেতার পেমেন্ট স্ক্রিনশট দিয়ে অর্ডার Confirm হবে না। কৃষকের 'টাকা বুঝে পেয়েছি' এবং অ্যাডমিন/গেটওয়ে যাচাই উভয়ই আবশ্যক।",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF78350F),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 2. Items & Payment Details
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("পণ্যের তালিকা ও পেমেন্ট (১ মণ = ৪০ কেজি)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        KeyValRow("প্ল্যাটফর্ম কমিশন", "৳ ০")
                        KeyValRow("সর্বমোট", order.grandTotal.formatBdt(), bold = true)
                        HorizontalDivider()
                        KeyValRow("পেমেন্ট পদ্ধতি", order.paymentMethod)
                        KeyValRow("পেমেন্ট অবস্থা", order.paymentStatus)
                        if (order.paymentSenderNumber.isNotBlank()) {
                            KeyValRow("প্রেরকের মোবাইল", order.paymentSenderNumber)
                        }
                        if (order.transactionReference.isNotBlank()) {
                            KeyValRow("ট্রানজেকশন রেফারেন্স", order.transactionReference)
                        }
                        KeyValRow(
                            "কৃষক টাকা নিশ্চিতকরণ",
                            if (order.farmerPaymentConfirmed) "✓ নিশ্চিত (${formatDateBn(order.farmerPaymentConfirmedAt)})" else "অপেক্ষমান"
                        )
                        KeyValRow(
                            "অ্যাডমিন যাচাই",
                            if (order.adminPaymentVerified) "✓ যাচাইকৃত (${formatDateBn(order.adminPaymentVerifiedAt)})" else "অপেক্ষমান"
                        )
                        KeyValRow("ডেলিভারি ঠিকানা", order.deliveryAddress)

                        val savedReceiptModel = MediaCaptureHelper.imageModelFromUri(order.paymentReceiptUri)
                        if (savedReceiptModel != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("জমাকৃত পেমেন্ট রসিদ:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            AsyncImage(
                                model = savedReceiptModel,
                                contentDescription = "পেমেন্ট রসিদ",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }

            // 3. Buyer Payment & Receipt Submission Card (Step 3)
            if (isBuyerOwner && !isClosed && !order.adminPaymentVerified &&
                currentStatus in setOf(OrderStatus.ACCEPTED, OrderStatus.PROCESSING, OrderStatus.READY_FOR_PICKUP, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.PENDING)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("ধাপ ৩: ক্রেতার পেমেন্ট তথ্য ও রসিদ জমা দিন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = InfoBlue)
                            Text(
                                text = "কৃষক অর্ডার গ্রহণ করার পর পেমেন্ট করে Transaction ID ও রসিদের ছবি জমা দিন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(PaymentMethod.entries) { m ->
                                    FilterChip(
                                        selected = selectedPayMethod == m,
                                        onClick = { selectedPayMethod = m },
                                        label = { Text(m.labelBn) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = senderNumber,
                                onValueChange = { senderNumber = it },
                                label = { Text("প্রেরকের মোবাইল নম্বর") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("order_payment_sender_input")
                            )
                            OutlinedTextField(
                                value = txRef,
                                onValueChange = { txRef = it.uppercase() },
                                label = { Text("ট্রানজেকশন আইডি / রেফারেন্স") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("order_payment_txid_input")
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            val uri = MediaCaptureHelper.createCameraImageUri(context)
                                            pendingCameraUri = uri
                                            cameraTakePictureLauncher.launch(uri)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("ক্যামেরা", maxLines = 1) }
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("গ্যালারি", maxLines = 1) }
                                OutlinedButton(
                                    onClick = { fileDocumentLauncher.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("ফাইল", maxLines = 1) }
                            }
                            if (processingImage) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            val previewModel = MediaCaptureHelper.imageModelFromUri(receiptUri)
                            if (previewModel != null) {
                                AsyncImage(
                                    model = previewModel,
                                    contentDescription = "নির্বাচিত রসিদ",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Button(
                                onClick = {
                                    onSubmitOrderPayment(selectedPayMethod, senderNumber.trim(), txRef.trim(), receiptUri)
                                },
                                enabled = txRef.isNotBlank() || receiptUri.isNotBlank() || !selectedPayMethod.isDigital,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_order_payment_button")
                            ) {
                                Text("পেমেন্ট তথ্য ও রসিদ জমা দিন", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 4. Counterparty Direct Contact
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

            // 5. Farmer Payment Received Confirmation + Status Controls (Step 2 & Step 4)
            if (isFarmerOwner) {
                if (!isClosed && currentStatus != OrderStatus.PENDING && !order.farmerPaymentConfirmed) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = LightGreen),
                            border = BorderStroke(1.5.dp, PrimaryGreen)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "ধাপ ৪: কৃষকের টাকা প্রাপ্তি নিশ্চিতকরণ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkGreen
                                )
                                Text(
                                    text = "সতর্কতা: শুধু ক্রেতার স্ক্রিনশট দেখে নয় — প্রকৃতপক্ষে টাকা বুঝে পাওয়ার পরই নিচের বাটনে ট্যাপ করুন।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkGreen
                                )
                                Button(
                                    onClick = onFarmerConfirmPaymentReceived,
                                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("farmer_confirm_payment_button")
                                ) {
                                    Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("টাকা বুঝে পেয়েছি (Confirm Payment Received)", fontWeight = FontWeight.Bold)
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

            // 6. Buyer Completion, Review & Complaint Controls
            if (isBuyerOwner) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (currentStatus in setOf(OrderStatus.DELIVERED, OrderStatus.READY_FOR_PICKUP, OrderStatus.SHIPPED, OrderStatus.CONFIRMED)) {
                            Button(
                                onClick = { onUpdateStatus(OrderStatus.COMPLETED) },
                                enabled = dualConfirmed,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("complete_order_button")
                            ) {
                                Text(
                                    if (dualConfirmed) "পণ্য বুঝে পেয়েছি — সম্পন্ন চিহ্নিত করুন"
                                    else "কৃষক ও অ্যাডমিন পেমেন্ট নিশ্চিত হলে সম্পন্ন করা যাবে"
                                )
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
