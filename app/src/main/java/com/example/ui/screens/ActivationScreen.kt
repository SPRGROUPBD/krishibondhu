package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.components.AccountStatusBadge
import com.example.ui.components.formatDateBn
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.MediaCaptureHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onSubmitPayment: (
        method: PaymentMethod,
        senderMobile: String,
        transactionId: String,
        amount: Double,
        receiptUri: String,
        isRenewal: Boolean
    ) -> Unit,
    onRequireLogin: () -> Unit,
    onCopyToast: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = uiState.activeUser
    val settings = uiState.paymentSettings
    val lang = uiState.language

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("অ্যাকাউন্ট সক্রিয়করণ ও নবায়ন") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("activation_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        }
    ) { padding ->
        if (user == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("অ্যাকাউন্ট সক্রিয় করতে লগইন করুন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = onRequireLogin) {
                        Text("লগইন করুন")
                    }
                }
            }
            return@Scaffold
        }

        val myPayments = remember(uiState.activationPayments, user.id) {
            uiState.activationPayments.filter { it.userId == user.id }.sortedByDescending { it.createdAt }
        }
        val hasPendingPayment = myPayments.any { it.status == ActivationPaymentStatus.PENDING.name }
        val isActive = user.isMarketplaceActive()
        val isExpired = user.accountStatus == AccountStatus.EXPIRED.name || (user.expiryDate > 0L && user.expiryDate <= System.currentTimeMillis())
        val fee = if (isActive || isExpired) settings.renewalFee else if (user.role == UserRole.FARMER.name) settings.farmerFee else settings.buyerFee
        var showRenewalForm by remember(user.accountStatus) { mutableStateOf(!isActive) }

        var selectedMethod by remember { mutableStateOf(PaymentMethod.BKASH) }
        var senderMobile by remember(user.phone) { mutableStateOf(user.phone) }
        var transactionId by remember { mutableStateOf("") }
        var receiptUri by remember { mutableStateOf("") }
        var processingImage by remember { mutableStateOf(false) }
        var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
        var mediaHint by remember { mutableStateOf<String?>(null) }

        val handleSelectedImage: (Uri) -> Unit = { uri ->
            processingImage = true
            scope.launch {
                val encoded = MediaCaptureHelper.encodeUriToCompactDataUrl(context, uri)
                receiptUri = encoded ?: uri.toString()
                mediaHint = "রসিদের ছবি সংযুক্ত হয়েছে ✓"
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
            } else {
                mediaHint = "ক্যামেরা অনুমতি নেই — গ্যালারি বা ফাইল থেকে রসিদ নিন"
            }
        }

        val copyToClipboard: (String, String) -> Unit = { label, value ->
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cb?.setPrimaryClip(ClipData.newPlainText(label, value))
            onCopyToast("$label নম্বর কপি হয়েছে: $value")
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Account Status & Validity Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isActive) LightGreen else MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (isActive) PrimaryGreen else MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${if (user.role == UserRole.FARMER.name) "কৃষক (Farmer)" else "ক্রেতা (Buyer)"} • ${user.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AccountStatusBadge(user.accountStatus, user.expiryDate, lang)
                        }

                        HorizontalDivider()

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("অ্যাক্টিভেশন ফি (২ বছর):", style = MaterialTheme.typography.bodySmall)
                            Text("৳ ${fee.toInt()} (এককালীন)", fontWeight = FontWeight.Bold, color = DarkGreen)
                        }
                        if (user.activationDate > 0L) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("সক্রিয় হওয়ার তারিখ:", style = MaterialTheme.typography.bodySmall)
                                Text(formatDateBn(user.activationDate), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (user.expiryDate > 0L) {
                            val daysLeft = user.daysUntilExpiry() ?: 0
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("মেয়াদ শেষের তারিখ:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${formatDateBn(user.expiryDate)} (${daysLeft} দিন বাকি)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (daysLeft <= 30) AlertRed else DarkGreen
                                )
                            }
                        }

                        if (isActive && !hasPendingPayment) {
                            OutlinedButton(
                                onClick = { showRenewalForm = !showRenewalForm },
                                modifier = Modifier.fillMaxWidth().testTag("toggle_renewal_form_button")
                            ) {
                                Text(if (showRenewalForm) "রিনিউ ফর্ম লুকান" else "আগেই রিনিউ করুন (+২ বছর মেয়াদ বাড়ান • ৳${fee.toInt()})")
                            }
                        }
                    }
                }
            }

            // 2. Payment Channels from Admin Settings
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("পেমেন্ট পাঠানোর নম্বরসমূহ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        PaymentChannelCard(
                            name = "বিকাশ (bKash)",
                            number = settings.bKashNumber,
                            accountType = settings.bKashAccountType,
                            accent = Color(0xFFE2136E),
                            onCopy = { copyToClipboard("bKash", settings.bKashNumber) }
                        )
                        PaymentChannelCard(
                            name = "নগদ (Nagad)",
                            number = settings.nagadNumber,
                            accountType = settings.nagadAccountType,
                            accent = Color(0xFFF37021),
                            onCopy = { copyToClipboard("Nagad", settings.nagadNumber) }
                        )
                        Surface(color = HarvestGoldLight, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                text = settings.paymentInstructions,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 3. Pending Notice OR Submission Form
            if (hasPendingPayment) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = InfoBlueLight),
                        border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("activation_pending_banner")
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.HourglassTop, contentDescription = null, tint = InfoBlue)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("আপনার পেমেন্ট যাচাইাধীন রয়েছে", fontWeight = FontWeight.Bold, color = InfoBlue)
                                Text(
                                    "সুপার অ্যাডমিন আপনার ট্রানজেকশন আইডি ও রসিদ যাচাই করে অনুমোদন দেওয়ার পর ২ বছরের জন্য অ্যাকাউন্ট সক্রিয় হবে।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InfoBlue
                                )
                            }
                        }
                    }
                }
            } else if (showRenewalForm) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isActive || isExpired) "অ্যাকাউন্ট রিনিউ পেমেন্ট জমা দিন (+২ বছর)" else "অ্যাক্টিভেশন পেমেন্ট জমা দিন (২ বছর)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("নির্ধারিত ফি: ৳ ${fee.toInt()} (${settings.validityYears} বছর মেয়াদ)", fontWeight = FontWeight.SemiBold, color = PrimaryGreen)

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(
                                    selected = selectedMethod == PaymentMethod.BKASH,
                                    onClick = { selectedMethod = PaymentMethod.BKASH },
                                    label = { Text("bKash (${PaymentAccountType.fromName(settings.bKashAccountType).labelEn})") },
                                    modifier = Modifier.weight(1f).testTag("activation_method_bkash")
                                )
                                FilterChip(
                                    selected = selectedMethod == PaymentMethod.NAGAD,
                                    onClick = { selectedMethod = PaymentMethod.NAGAD },
                                    label = { Text("Nagad (${PaymentAccountType.fromName(settings.nagadAccountType).labelEn})") },
                                    modifier = Modifier.weight(1f).testTag("activation_method_nagad")
                                )
                            }

                            OutlinedTextField(
                                value = senderMobile,
                                onValueChange = { senderMobile = it },
                                label = { Text("যে নম্বর থেকে টাকা পাঠিয়েছেন (Sender Mobile) *") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("activation_sender_input")
                            )

                            OutlinedTextField(
                                value = transactionId,
                                onValueChange = { transactionId = it.uppercase() },
                                label = { Text("ট্রানজেকশন আইডি (Transaction ID / TrxID) *") },
                                placeholder = { Text("যেমন: BKA8X92Q71") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("activation_txid_input")
                            )

                            Text("পেমেন্ট স্ক্রিনশট / রসিদ (ঐচ্ছিক তবে সুপারিশকৃত)", style = MaterialTheme.typography.labelLarge)
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
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ক্যামেরা", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("গ্যালারি", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { fileDocumentLauncher.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ফাইল", maxLines = 1)
                                }
                            }

                            if (processingImage) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            mediaHint?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = PrimaryGreen)
                            }
                            val previewModel = MediaCaptureHelper.imageModelFromUri(receiptUri)
                            if (previewModel != null) {
                                AsyncImage(
                                    model = previewModel,
                                    contentDescription = "পেমেন্ট রসিদ",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp))
                                )
                            }

                            val canSubmit = senderMobile.length >= 10 && transactionId.trim().length >= 4
                            Button(
                                onClick = {
                                    onSubmitPayment(
                                        selectedMethod,
                                        senderMobile.trim(),
                                        transactionId.trim(),
                                        fee,
                                        receiptUri,
                                        isActive || isExpired
                                    )
                                    transactionId = ""
                                    receiptUri = ""
                                    mediaHint = null
                                },
                                enabled = canSubmit,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("activation_submit_button")
                            ) {
                                Text("পেমেন্ট যাচাইয়ের জন্য জমা দিন (৳ ${fee.toInt()})", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 4. Payment History
            if (myPayments.isNotEmpty()) {
                item {
                    Text(
                        text = "আপনার পূর্ববর্তী পেমেন্ট আবেদন (${myPayments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(myPayments, key = { it.id }) { pay ->
                    val payStatus = ActivationPaymentStatus.fromName(pay.status)
                    val (bg, fg) = when (payStatus) {
                        ActivationPaymentStatus.APPROVED -> LightGreen to DarkGreen
                        ActivationPaymentStatus.REJECTED, ActivationPaymentStatus.RESUBMISSION_REQUESTED -> AlertRedLight to AlertRed
                        ActivationPaymentStatus.PENDING -> HarvestGoldLight to Color(0xFF92400E)
                    }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${pay.paymentMethod} • ${pay.amount.formatBdt()}", fontWeight = FontWeight.Bold)
                                Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = payStatus.display(lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fg,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text("TxID: ${pay.transactionId} • প্রেরক: ${pay.senderMobile}", style = MaterialTheme.typography.bodySmall)
                            Text("তারিখ: ${formatDateBn(pay.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (pay.adminNotes.isNotBlank()) {
                                Text("অ্যাডমিন মন্তব্য: ${pay.adminNotes}", style = MaterialTheme.typography.bodySmall, color = fg, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentChannelCard(
    name: String,
    number: String,
    accountType: String,
    accent: Color,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$name (${PaymentAccountType.fromName(accountType).display(AppLanguage.BENGALI)})", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Text(number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "কপি", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("কপি")
            }
        }
    }
}
