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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
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
import com.example.data.local.ProductEntity
import com.example.data.model.*
import com.example.ui.components.EmptyStateView
import com.example.ui.components.InteractiveBangladeshMapDialog
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.LightGreen
import com.example.ui.theme.PrimaryGreen
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.LocationHelper
import com.example.util.MediaCaptureHelper
import kotlinx.coroutines.launch

@Composable
fun SellScreen(
    uiState: MarketplaceUiState,
    onLoginFarmer: () -> Unit,
    onRegisterFarmer: () -> Unit,
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
    onUpdatePriceAndStock: (productId: String, newPrice: Double, newStock: Double) -> Unit
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
                            text = "কৃষি বন্ধুতে পণ্যের দাম কৃষক নিজেই নির্ধারণ করেন। কোনো বাধ্যতামূলক কমিশন নেই। ফসল বিক্রি করতে কৃষক হিসেবে লগইন করুন অথবা নতুন নিবন্ধন করুন।",
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

    val myProducts = uiState.products.filter { it.farmerId == activeUser.id }
    val myOrders = uiState.orders.filter { it.farmerId == activeUser.id }
    val totalSales = myOrders.filter { it.status == OrderStatus.COMPLETED.name }.sumOf { it.productTotal }

    var showAddForm by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }

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
                    Text(
                        text = "${activeUser.upazila}, ${activeUser.district} • মোবাইল: ${activeUser.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightGreen
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FarmerStatBox("আমার পণ্য", "${myProducts.size}", Modifier.weight(1f))
                        FarmerStatBox("অর্ডার", "${myOrders.size}", Modifier.weight(1f))
                        FarmerStatBox("মোট বিক্রি", totalSales.formatBdt(), Modifier.weight(1f))
                    }
                }
            }
        }

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
                Text(if (showAddForm) "ফর্ম বন্ধ করুন" else "নতুন ফসল যুক্ত করুন (Add Product)")
            }
        }

        if (showAddForm) {
            item {
                AddProductFormCard(
                    categories = uiState.categories.filter { it.isActive },
                    defaultAddress = "${activeUser.village}, ${activeUser.upazila}, ${activeUser.district}",
                    defaultDistrict = activeUser.district.ifBlank { "কুষ্টিয়া" },
                    onSubmit = { cat, name, desc, uri, qty, unit, price, harvest, avail, addr, pickup, delivery, fee, wholesale ->
                        onAddProduct(cat, name, desc, uri, qty, unit, price, harvest, avail, addr, pickup, delivery, fee, wholesale)
                        showAddForm = false
                    }
                )
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
                    subtitle = "উপরের বাটনে ট্যাপ করে প্রথম ফসল যোগ করুন।"
                )
            }
        } else {
            items(myProducts, key = { it.id }) { prod ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.nameBn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${prod.pricePerUnit.formatBdt()}/${prod.unit} • মজুত: ${prod.availableQuantity.formatQty()} ${prod.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "অবস্থা: ${prod.status} • ${prod.categoryNameBn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { editingProduct = prod },
                            modifier = Modifier.testTag("edit_product_${prod.id}")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "দাম ও মজুত সম্পাদনা")
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
    var qtyText by remember { mutableStateOf("20") }
    var selectedUnit by remember { mutableStateOf(ProductUnit.KG.labelBn) }
    var priceText by remember { mutableStateOf("") }
    var harvestDate by remember { mutableStateOf("আজই সংগ্রহ") }
    var availDate by remember { mutableStateOf("আজই পাওয়া যাবে") }
    var pickupAddress by remember { mutableStateOf(defaultAddress) }
    var supportsPickup by remember { mutableStateOf(true) }
    var supportsDelivery by remember { mutableStateOf(true) }
    var deliveryFeeText by remember { mutableStateOf("60") }
    var isWholesale by remember { mutableStateOf(false) }

    val handleSelectedImageUri: (Uri) -> Unit = { uri ->
        processingImage = true
        scope.launch {
            val encoded = MediaCaptureHelper.encodeUriToCompactDataUrl(context, uri)
            imageUri = encoded ?: uri.toString()
            mediaHint = if (encoded != null) "ছবি প্রস্তুত ও সিঙ্ক-যোগ্য ✓" else "ছবি নির্বাচিত হয়েছে ✓"
            processingImage = false
        }
    }

    // 1. Play Policy compliant zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) handleSelectedImageUri(uri) }
    )

    // 2. Play Policy compliant zero-permission Files / Storage Access Framework picker
    val fileDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) handleSelectedImageUri(uri) }
    )

    // 3. Camera Capture via FileProvider + runtime CAMERA permission
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
            Text("নতুন ফসলের তথ্য দিন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Camera + Photo Picker + Files Row
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
                placeholder = { Text("যেমন: খাঁটি মিনিকেট চাল") },
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
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("দাম (৳/${selectedUnit}) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("add_product_price_input")
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("মজুত পরিমাণ *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("add_product_qty_input")
                )
            }

            Text("একক (Unit)", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProductUnit.allLabelsBn) { u ->
                    FilterChip(
                        selected = selectedUnit == u,
                        onClick = { selectedUnit = u },
                        label = { Text(u) }
                    )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isWholesale, onCheckedChange = { isWholesale = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isWholesale) "পাইকারি বিক্রি (Wholesale)" else "খুচরা বিক্রি (Retail)")
            }

            val priceVal = priceText.toDoubleOrNull()
            val qtyVal = qtyText.toDoubleOrNull()
            val canSubmit = selectedCategory != null && nameBn.isNotBlank() && priceVal != null && priceVal > 0 && qtyVal != null && qtyVal > 0

            Button(
                onClick = {
                    val cat = selectedCategory ?: return@Button
                    onSubmit(
                        cat, nameBn, descBn.ifBlank { "$nameBn — সরাসরি কৃষকের ক্ষেত থেকে।" },
                        imageUri, qtyVal ?: 1.0, selectedUnit, priceVal ?: 1.0,
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
                Text("ফসল প্রকাশ করুন", fontWeight = FontWeight.Bold)
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
                Text("কৃষক হিসেবে আপনি যেকোনো সময় আপনার পণ্যের দাম ও মজুত পরিবর্তন করতে পারবেন।", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("নতুন দাম (৳/${product.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it },
                    label = { Text("বর্তমান মজুত (${product.unit})") },
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
