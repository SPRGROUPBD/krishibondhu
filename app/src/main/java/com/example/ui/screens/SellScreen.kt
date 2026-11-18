package com.example.ui.screens

import android.Manifest
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Verified
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
import com.example.data.local.CategoryEntity
import com.example.data.local.FarmerPaymentSettingsEntity
import com.example.data.local.ProductEntity
import com.example.data.model.*
import com.example.ui.components.EmptyStateView
import com.example.ui.components.InteractiveBangladeshMapDialog
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.HarvestGoldLight
import com.example.ui.theme.LightGreen
import com.example.ui.theme.PrimaryGreen
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.LocationHelper
import com.example.util.MediaCaptureHelper
import kotlinx.coroutines.launch

@Composable
fun SellScreen(
    uiState: MarketplaceUiState,
    farmerPaymentSettings: FarmerPaymentSettingsEntity? = null,
    onLoginFarmer: () -> Unit,
    onRegisterFarmer: () -> Unit,
    onOpenActivation: () -> Unit = {},
    onSavePaymentSettings: (bKash: String, nagad: String, rocket: String, bank: String) -> Unit = { _, _, _, _ -> },
    onAddProduct: (
        category: CategoryEntity,
        nameBn: String,
        descriptionBn: String,
        imageUri: String,
        quantity: Double,
        unit: String,
        pricePerUnit: Double,
        harvestDate: String,
        availabilityDate: String,
        pickupAddress: String,
        supportsFarmPickup: Boolean,
        supportsDirectDelivery: Boolean,
        deliveryCharge: Double,
        isWholesale: Boolean
    ) -> Unit,
    onUpdatePriceAndStock: (productId: String, newPrice: Double, newStock: Double) -> Unit,
    onDeleteProduct: (productId: String) -> Unit = {}
) {
    val activeUser = uiState.activeUser
    val lang = uiState.language

    if (activeUser == null || activeUser.role != UserRole.FARMER.name) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🌾", style = MaterialTheme.typography.displaySmall)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (lang == AppLanguage.BENGALI)
                                "দালাল ছাড়া সরাসরি আপনার ফসল বিক্রি করুন"
                            else
                                "Sell your harvest directly with 0% commission",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "কৃষি বন্ধুতে ফসল বিক্রি হয় শুধুমাত্র মণ হিসেবে (১ মণ = ৪০ কেজি)। পণ্যের দাম কৃষক নিজেই নির্ধারণ করেন।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onLoginFarmer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_farmer_button")
                        ) {
                            Text("কৃষক হিসেবে লগইন করুন")
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onRegisterFarmer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("register_farmer_button")
                        ) {
                            Text("নতুন কৃষক হিসেবে নিবন্ধন করুন")
                        }
                    }
                }
            }
        }
        return
    }

    val isFarmerActive = activeUser.isMarketplaceActive()
    val myProducts = uiState.products.filter { it.farmerId == activeUser.id }
    val myOrders = uiState.orders.filter { it.farmerId == activeUser.id }
    val totalSales = myOrders.filter { it.status == OrderStatus.COMPLETED.name || it.status == OrderStatus.CONFIRMED.name }.sumOf { it.productTotal }
    val resolvedSettings = farmerPaymentSettings ?: uiState.farmerPaymentSettings.firstOrNull { it.farmerId == activeUser.id }
    val pendingPaymentVerifyCount = uiState.orderPayments.count {
        it.farmerId == activeUser.id && it.status == OrderPaymentStatus.PENDING_VERIFICATION.name
    }

    var showAddForm by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Farmer Dashboard KPIs
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkGreen)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "কৃষক ড্যাশবোর্ড: ${activeUser.fullName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (activeUser.verificationStatus == "VERIFIED") {
                            Icon(Icons.Filled.Verified, contentDescription = "যাচাইকৃত", tint = Color(0xFF4ADE80))
                        }
                    }
                    val locLine = listOf(activeUser.unionName, activeUser.upazila, activeUser.district)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    Text(
                        text = "$locLine • মোবাইল: ${activeUser.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    com.example.ui.components.AccountStatusBadge(activeUser.accountStatus, activeUser.expiryDate, lang)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FarmerStatBox("আমার পণ্য", "${myProducts.size}", Modifier.weight(1f))
                        FarmerStatBox("অর্ডার", "${myOrders.size}", Modifier.weight(1f))
                        FarmerStatBox("মোট বিক্রি", totalSales.formatBdt(), Modifier.weight(1f))
                    }
                    if (pendingPaymentVerifyCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = HarvestGoldLight,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HarvestGold)
                        ) {
                            Text(
                                text = "⚠ আপনার ${pendingPaymentVerifyCount}টি ক্রেতা অগ্রিম পেমেন্ট যাচাই অপেক্ষমাণ — অর্ডার ট্যাবে গিয়ে নিশ্চিত করুন।",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Farmer Direct Payment Settings Card (Isolated per farmer)
        item {
            FarmerPaymentSettingsCard(
                currentSettings = resolvedSettings,
                onSave = onSavePaymentSettings
            )
        }

        // Active Account Gate Banner
        if (!isFarmerActive) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HarvestGoldLight),
                    border = BorderStroke(1.dp, com.example.ui.theme.HarvestGold),
                    modifier = Modifier.testTag("sell_activation_gate_banner")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ফসল প্রকাশ ও সম্পাদনার জন্য অ্যাকাউন্ট সক্রিয় করুন",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78350F)
                        )
                        Text(
                            text = "আপনার অ্যাকাউন্টের বর্তমান অবস্থা: ${AccountStatus.fromName(activeUser.accountStatus).display(lang)}। ৩০০ টাকা দিয়ে ২ বছরের জন্য অ্যাকাউন্ট সক্রিয়/রিনিউ করলে ফসল প্রকাশ ও দাম আপডেট করতে পারবেন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF78350F)
                        )
                        Button(
                            onClick = onOpenActivation,
                            modifier = Modifier.fillMaxWidth().testTag("sell_go_activation_button")
                        ) {
                            Text("অ্যাকাউন্ট সক্রিয় / রিনিউ করুন (৩০০৳)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Toggle Add Product Form
            item {
                Button(
                    onClick = { showAddForm = !showAddForm },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("toggle_add_product_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showAddForm) "ফর্ম বন্ধ করুন" else "নতুন ফসল যুক্ত করুন (মণ হিসেবে)")
                }
            }

            if (showAddForm) {
                item {
                    AddProductFormCard(
                        categories = uiState.categories.filter { it.isActive },
                        defaultAddress = listOf(activeUser.village, activeUser.unionName, activeUser.upazila, activeUser.district)
                            .filter { it.isNotBlank() }
                            .joinToString(", "),
                        defaultDistrict = activeUser.district.ifBlank { "কুষ্টিয়া" },
                        onSubmit = { cat, name, desc, uri, qty, unit, price, harvest, avail, addr, pickup, delivery, fee, wholesale ->
                            onAddProduct(cat, name, desc, uri, qty, unit, price, harvest, avail, addr, pickup, delivery, fee, wholesale)
                            showAddForm = false
                        }
                    )
                }
            }
        }

        item {
            Text(
                text = "আমার তালিকাভুক্ত ফসল (${myProducts.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (myProducts.isEmpty()) {
            item {
                EmptyStateView(
                    emoji = "🌱",
                    title = "আপনার কোনো ফসল এখনো যুক্ত করা হয়নি",
                    subtitle = if (isFarmerActive) "উপরের বাটনে ট্যাপ করে প্রথম ফসল যোগ করুন।" else "অ্যাকাউন্ট সক্রিয় হলে এখান থেকে ফসল যোগ করতে পারবেন।"
                )
            }
        } else {
            items(myProducts, key = { it.id }) { prod ->
                val (statusLabelBn, statusBg, statusFg) = when (prod.status.uppercase()) {
                    "PENDING_APPROVAL" -> Triple("অ্যাডমিন যাচাই অপেক্ষমাণ", HarvestGoldLight, Color(0xFF92400E))
                    "ACTIVE" -> Triple("অনুমোদিত (সবার জন্য উন্মুক্ত)", LightGreen, DarkGreen)
                    "REJECTED" -> Triple("প্রত্যাখ্যাত", Color(0xFFFEE2E2), Color(0xFFB91C1C))
                    "OUT_OF_STOCK" -> Triple("মজুত শেষ", Color(0xFFF3F4F6), Color(0xFF374151))
                    else -> Triple(prod.status, Color(0xFFF3F4F6), Color(0xFF374151))
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(prod.nameBn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${prod.pricePerUnit.formatBdt()}/মণ • মজুত: ${prod.availableQuantity.formatQty()} মণ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = statusLabelBn,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = statusFg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = prod.categoryNameBn,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isFarmerActive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalIconButton(
                                    onClick = { editingProduct = prod },
                                    modifier = Modifier.testTag("edit_product_${prod.id}")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "দাম ও মজুত সম্পাদনা")
                                }
                                FilledTonalIconButton(
                                    onClick = { deletingProduct = prod },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color(0xFFFEE2E2),
                                        contentColor = Color(0xFFB91C1C)
                                    ),
                                    modifier = Modifier.testTag("delete_product_${prod.id}")
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "ফসল মুছুন")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingProduct?.let { prod ->
        EditPriceStockDialog(
            product = prod,
            onDismiss = { editingProduct = null },
            onSave = { newPrice, newStock ->
                onUpdatePriceAndStock(prod.id, newPrice, newStock)
                editingProduct = null
            }
        )
    }

    deletingProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("ফসল মুছে ফেলুন?") },
            text = { Text("আপনি কি নিশ্চিতভাবে '${prod.nameBn}' তালিকা থেকে মুছে ফেলতে চান?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(prod.id)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun FarmerStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = LightGreen)
        }
    }
}

@Composable
private fun AddProductFormCard(
    categories: List<CategoryEntity>,
    defaultAddress: String,
    defaultDistrict: String = "কুষ্টিয়া",
    onSubmit: (
        CategoryEntity, String, String, String, Double, String, Double,
        String, String, String, Boolean, Boolean, Double, Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember(categories) { mutableStateOf(categories.firstOrNull()) }
    var nameBn by remember { mutableStateOf("") }
    var descBn by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf("") }
    var processingImage by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showMapDialog by remember { mutableStateOf(false) }
    var mediaHint by remember { mutableStateOf<String?>(null) }
    var qtyText by remember { mutableStateOf("10") }
    val selectedUnit = ProductUnit.MON.labelBn // শুধুমাত্র মণ (১ মণ = ৪০ কেজি)
    var priceText by remember { mutableStateOf("") }
    var harvestDate by remember { mutableStateOf("আজই সংগ্রহ") }
    var availDate by remember { mutableStateOf("আজই পাওয়া যাবে") }
    var pickupAddress by remember { mutableStateOf(defaultAddress) }
    var supportsPickup by remember { mutableStateOf(true) }
    var supportsDelivery by remember { mutableStateOf(true) }
    var deliveryFeeText by remember { mutableStateOf("60") }
    val isWholesale = true // সব পণ্য শুধুমাত্র পাইকারি দামে বিক্রি হবে

    val handleSelectedImageUri: (Uri) -> Unit = { uri ->
        processingImage = true
        scope.launch {
            val encoded = MediaCaptureHelper.encodeUriToCompactDataUrl(context, uri)
            imageUri = encoded ?: uri.toString()
            mediaHint = if (encoded != null) "ছবি প্রস্তুত ও সিঙ্ক-যোগ্য ✓" else "ছবি নির্বাচিত হয়েছে ✓"
            processingImage = false
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) handleSelectedImageUri(uri) }
    )

    val fileDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) handleSelectedImageUri(uri) }
    )

    val cameraTakePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            handleSelectedImageUri(captured)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = MediaCaptureHelper.createCameraImageUri(context)
            pendingCameraUri = uri
            cameraTakePictureLauncher.launch(uri)
        } else {
            mediaHint = "ক্যামেরা অনুমতি দেওয়া হয়নি — গ্যালারি বা ফাইল থেকে ছবি নিন"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            scope.launch {
                LocationHelper.detectCurrentLocation(context)
                    .onSuccess { detected -> pickupAddress = detected.addressLine }
                    .onFailure { showMapDialog = true }
            }
        } else {
            showMapDialog = true
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("নতুন ফসলের তথ্য দিন (বিক্রয়ের একক: মণ)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val uri = MediaCaptureHelper.createCameraImageUri(context)
                            pendingCameraUri = uri
                            cameraTakePictureLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("add_product_camera_button")
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ক্যামেরা", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f).testTag("add_product_gallery_button")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("গ্যালারি", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        fileDocumentLauncher.launch(arrayOf("image/*"))
                    },
                    modifier = Modifier.weight(1f).testTag("add_product_files_button")
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

            val previewModel = MediaCaptureHelper.imageModelFromUri(imageUri)
            if (previewModel != null) {
                AsyncImage(
                    model = previewModel,
                    contentDescription = "নির্বাচিত ফসলের ছবি",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Text("ক্যাটাগরি নির্বাচন করুন", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = selectedCategory?.id == cat.id,
                        onClick = { selectedCategory = cat },
                        label = { Text("${cat.emoji} ${cat.nameBn}") }
                    )
                }
            }

            OutlinedTextField(
                value = nameBn,
                onValueChange = { nameBn = it },
                label = { Text("ফসলের নাম *") },
                placeholder = { Text("যেমন: ধান / আলু / পেঁয়াজ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("add_product_name_input")
            )

            OutlinedTextField(
                value = descBn,
                onValueChange = { descBn = it },
                label = { Text("বিবরণ") },
                placeholder = { Text("মান, জাত ও চাষের বিবরণ লিখুন") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("পরিমাণ (মণ) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("add_product_qty_input")
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("প্রতি মণের দাম (৳) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("add_product_price_input")
                )
            }

            val priceVal = priceText.toDoubleOrNull() ?: 0.0
            val qtyVal = qtyText.toDoubleOrNull() ?: 0.0
            Surface(
                color = LightGreen,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "বিক্রয়ের একক: মণ (বাংলাদেশ মানদণ্ড: ১ মণ = ${ProductUnit.MON_TO_KG} কেজি)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkGreen
                    )
                    if (qtyVal > 0 && priceVal > 0) {
                        Text(
                            text = "মোট বিক্রয় মূল্য: ${(qtyVal * priceVal).formatBdt()} (${qtyVal.formatQty()} মণ × ${priceVal.formatBdt()}/মণ)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen
                        )
                        Text(
                            text = "সমতুল্য মোট ওজন: ${(qtyVal * ProductUnit.MON_TO_KG).formatQty()} কেজি",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkGreen
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = harvestDate,
                    onValueChange = { harvestDate = it },
                    label = { Text("সংগ্রহের তারিখ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = availDate,
                    onValueChange = { availDate = it },
                    label = { Text("পাওয়ার তারিখ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pickupAddress,
                    onValueChange = { pickupAddress = it },
                    label = { Text("পিকআপ ঠিকানা") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = {
                        if (LocationHelper.hasLocationPermission(context)) {
                            scope.launch {
                                LocationHelper.detectCurrentLocation(context)
                                    .onSuccess { detected -> pickupAddress = detected.addressLine }
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
                    modifier = Modifier.testTag("sell_auto_location_button")
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "ম্যাপ থেকে পিকআপ লোকেশন")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = supportsPickup, onCheckedChange = { supportsPickup = it })
                Text("খামার থেকে সরাসরি সংগ্রহ", modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = supportsDelivery, onCheckedChange = { supportsDelivery = it })
                Text("সরাসরি ডেলিভারি সুবিধা", modifier = Modifier.weight(1f))
            }
            if (supportsDelivery) {
                OutlinedTextField(
                    value = deliveryFeeText,
                    onValueChange = { deliveryFeeText = it },
                    label = { Text("আনুমানিক ডেলিভারি চার্জ (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Surface(
                color = LightGreen,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "বিক্রয়ের ধরন: পাইকারি মাত্র (Wholesale Only • ১ মণ = ৪০ কেজি)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen,
                    modifier = Modifier.padding(10.dp)
                )
            }

            val canSubmit = selectedCategory != null && nameBn.isNotBlank() && priceVal > 0 && qtyVal > 0

            Button(
                onClick = {
                    val cat = selectedCategory ?: return@Button
                    onSubmit(
                        cat, nameBn, descBn.ifBlank { "$nameBn — সরাসরি কৃষকের ক্ষেত থেকে।" },
                        imageUri, qtyVal, selectedUnit, priceVal,
                        harvestDate, availDate, pickupAddress, supportsPickup, supportsDelivery,
                        deliveryFeeText.toDoubleOrNull() ?: 60.0, isWholesale
                    )
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_add_product_button")
            ) {
                Text("ফসল প্রকাশ করুন (মণ হিসেবে)", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showMapDialog) {
        InteractiveBangladeshMapDialog(
            initialDistrictBn = defaultDistrict,
            onDismiss = { showMapDialog = false },
            onLocationConfirmed = { detected ->
                pickupAddress = detected.addressLine
                showMapDialog = false
            }
        )
    }
}

@Composable
private fun EditPriceStockDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit
) {
    var priceText by remember { mutableStateOf(product.pricePerUnit.toString()) }
    var stockText by remember { mutableStateOf(product.availableQuantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("দাম ও মজুত আপডেট (${product.nameBn})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("কৃষক হিসেবে আপনি প্রতি মণ (১ মণ = ৪০ কেজি) দাম ও মজুত পরিবর্তন করতে পারবেন।", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("প্রতি মণের নতুন দাম (৳/মণ)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it },
                    label = { Text("বর্তমান মজুত (মণ)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = priceText.toDoubleOrNull()
                val s = stockText.toDoubleOrNull()
                if (p != null && s != null) onSave(p, s)
            }) {
                Text("সংরক্ষণ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
private fun FarmerPaymentSettingsCard(
    currentSettings: FarmerPaymentSettingsEntity?,
    onSave: (bKash: String, nagad: String, rocket: String, bank: String) -> Unit
) {
    var expanded by remember(currentSettings) { mutableStateOf(currentSettings?.hasAnyNumber() != true) }
    var bKash by remember(currentSettings) { mutableStateOf(currentSettings?.bKashNumber.orEmpty()) }
    var nagad by remember(currentSettings) { mutableStateOf(currentSettings?.nagadNumber.orEmpty()) }
    var rocket by remember(currentSettings) { mutableStateOf(currentSettings?.rocketNumber.orEmpty()) }
    var bankInfo by remember(currentSettings) { mutableStateOf(currentSettings?.bankAccountInfo.orEmpty()) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().testTag("farmer_payment_settings_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = PrimaryGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "কৃষকের সরাসরি পেমেন্ট সেটিংস",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen
                        )
                        Text(
                            text = "ক্রেতা অগ্রিম পেমেন্ট সরাসরি আপনার নম্বরে পাঠাবেন (প্ল্যাটফর্ম কোনো টাকা নেয় না)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("toggle_farmer_payment_settings")
                ) {
                    Text(if (expanded) "লুকান" else "সম্পাদনা")
                }
            }

            if (!expanded && currentSettings != null && currentSettings.hasAnyNumber()) {
                val summary = listOfNotNull(
                    currentSettings.bKashNumber.takeIf { it.isNotBlank() }?.let { "bKash: $it" },
                    currentSettings.nagadNumber.takeIf { it.isNotBlank() }?.let { "Nagad: $it" },
                    currentSettings.rocketNumber.takeIf { it.isNotBlank() }?.let { "Rocket: $it" },
                    currentSettings.bankAccountInfo.takeIf { it.isNotBlank() }?.let { "Bank: $it" }
                ).joinToString(" • ")
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (expanded) {
                OutlinedTextField(
                    value = bKash,
                    onValueChange = { bKash = it },
                    label = { Text("বিকাশ (bKash) নম্বর") },
                    placeholder = { Text("01XXXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("farmer_pay_bkash_input")
                )
                OutlinedTextField(
                    value = nagad,
                    onValueChange = { nagad = it },
                    label = { Text("নগদ (Nagad) নম্বর") },
                    placeholder = { Text("01XXXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("farmer_pay_nagad_input")
                )
                OutlinedTextField(
                    value = rocket,
                    onValueChange = { rocket = it },
                    label = { Text("রকেট (Rocket) নম্বর") },
                    placeholder = { Text("01XXXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("farmer_pay_rocket_input")
                )
                OutlinedTextField(
                    value = bankInfo,
                    onValueChange = { bankInfo = it },
                    label = { Text("ব্যাংক একাউন্ট তথ্য (ঐচ্ছিক)") },
                    placeholder = { Text("ব্যাংকের নাম, শাখা, অ্যাকাউন্ট নম্বর") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("farmer_pay_bank_input")
                )
                Button(
                    onClick = {
                        onSave(bKash.trim(), nagad.trim(), rocket.trim(), bankInfo.trim())
                        expanded = false
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("farmer_pay_save_button")
                ) {
                    Text("পেমেন্ট নম্বর সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
