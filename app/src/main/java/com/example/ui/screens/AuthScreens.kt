package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.BangladeshLocations
import com.example.data.model.UserRole
import com.example.ui.components.InteractiveBangladeshMapDialog
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.LightGreen
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.LocationHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLogin: (identifier: String, password: String) -> Unit,
    onResetPassword: (phone: String, newPassword: String, onDone: () -> Unit) -> Unit = { _, _, onDone -> onDone() },
    onGoToRegister: () -> Unit
) {
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("লগইন করুন") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "আপনার নিবন্ধিত মোবাইল নম্বর বা ইউজার আইডি দিয়ে প্রবেশ করুন।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = loginId,
                onValueChange = { loginId = it },
                label = { Text("মোবাইল নম্বর বা ইউজার আইডি *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().testTag("login_phone_input")
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("পাসওয়ার্ড *") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.testTag("login_password_toggle")
                    ) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "পাসওয়ার্ড লুকান" else "পাসওয়ার্ড দেখুন"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("login_password_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showForgotDialog = true },
                    modifier = Modifier.testTag("forgot_password_button")
                ) {
                    Text("পাসওয়ার্ড ভুলে গেছেন?")
                }
            }

            Button(
                onClick = { onLogin(loginId, password) },
                enabled = loginId.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_submit_button")
            ) {
                Text("প্রবেশ করুন", fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onGoToRegister, modifier = Modifier.fillMaxWidth()) {
                Text("অ্যাকাউন্ট নেই? নতুন নিবন্ধন করুন")
            }
        }
    }

    if (showForgotDialog) {
        var resetPhone by remember { mutableStateOf(loginId) }
        var newPass by remember { mutableStateOf("") }
        var confirmNewPass by remember { mutableStateOf("") }
        var showNewPass by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("পাসওয়ার্ড রিসেট করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "আপনার নিবন্ধিত মোবাইল নম্বর ও নতুন পাসওয়ার্ড দিন (কোনো OTP প্রয়োজন নেই)।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = resetPhone,
                        onValueChange = { resetPhone = it },
                        label = { Text("নিবন্ধিত মোবাইল নম্বর *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("forgot_phone_input")
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("নতুন পাসওয়ার্ড (সর্বনিম্ন ৬ অক্ষর) *") },
                        singleLine = true,
                        visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showNewPass = !showNewPass }) {
                                Icon(
                                    imageVector = if (showNewPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showNewPass) "লুকান" else "দেখুন"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("forgot_new_password_input")
                    )
                    OutlinedTextField(
                        value = confirmNewPass,
                        onValueChange = { confirmNewPass = it },
                        label = { Text("নতুন পাসওয়ার্ড নিশ্চিত করুন *") },
                        singleLine = true,
                        isError = confirmNewPass.isNotEmpty() && newPass != confirmNewPass,
                        visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("forgot_confirm_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetPassword(resetPhone.trim(), newPass) {
                            loginId = resetPhone.trim()
                            password = ""
                            showForgotDialog = false
                        }
                    },
                    enabled = resetPhone.trim().length >= 10 && newPass.length >= 6 && newPass == confirmNewPass,
                    modifier = Modifier.testTag("forgot_submit_button")
                ) {
                    Text("পাসওয়ার্ড পরিবর্তন করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("বাতিল") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    uiState: MarketplaceUiState,
    initialRole: UserRole = UserRole.BUYER,
    onBack: () -> Unit,
    onRegister: (
        fullName: String,
        phone: String,
        password: String,
        confirmPassword: String,
        role: UserRole,
        division: String,
        district: String,
        upazila: String,
        unionName: String,
        village: String,
        nidLastFour: String,
        farmBio: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var role by remember { mutableStateOf(initialRole) }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var divDropdownExpanded by remember { mutableStateOf(false) }
    var distDropdownExpanded by remember { mutableStateOf(false) }
    var upzDropdownExpanded by remember { mutableStateOf(false) }

    var selectedDivision by remember { mutableStateOf(BangladeshLocations.divisions.first()) }
    var selectedDistrict by remember {
        mutableStateOf(BangladeshLocations.districtsForDivision(BangladeshLocations.divisions.first()).firstOrNull()?.districtBn ?: "ঢাকা")
    }
    var selectedUpazila by remember {
        mutableStateOf(BangladeshLocations.upazilasForDistrict(selectedDistrict).firstOrNull() ?: selectedDistrict)
    }

    val districtsForDivision = remember(selectedDivision) {
        BangladeshLocations.districtsForDivision(selectedDivision).map { it.districtBn }
    }
    val upazilasForDistrict = remember(selectedDistrict, uiState.locations) {
        (BangladeshLocations.upazilasForDistrict(selectedDistrict) +
            uiState.locations.filter { it.districtBn == selectedDistrict }.map { it.upazilaBn }).distinct()
    }
    var unionName by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var nidLastFour by remember { mutableStateOf("") }
    var farmBio by remember { mutableStateOf("") }

    var showMapDialog by remember { mutableStateOf(false) }
    var detectingLocation by remember { mutableStateOf(false) }
    var autoLocationStatus by remember { mutableStateOf<String?>(null) }

    val runAutoGps: () -> Unit = {
        detectingLocation = true
        autoLocationStatus = "ম্যাপ ও GPS থেকে লোকেশন শনাক্ত করা হচ্ছে..."
        scope.launch {
            LocationHelper.detectCurrentLocation(context)
                .onSuccess { detected ->
                    selectedDivision = detected.divisionBn
                    selectedDistrict = detected.districtBn
                    selectedUpazila = detected.upazilaBn
                    autoLocationStatus = "অটো লোকেশন: ${detected.upazilaBn}, ${detected.districtBn} (${detected.divisionBn})"
                }
                .onFailure {
                    autoLocationStatus = "GPS পাওয়া যায়নি — ম্যাপ ডায়ালগ থেকে জেলা নির্বাচন করুন"
                    showMapDialog = true
                }
            detectingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            runAutoGps()
        } else {
            autoLocationStatus = "লোকেশন অনুমতি দেওয়া হয়নি — ম্যাপ থেকে জেলা নির্বাচন করুন"
            showMapDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("নতুন নিবন্ধন (Registration)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LightGreen),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "অ্যাকাউন্ট সক্রিয়করণ নীতি (২ বছর মেয়াদ — ৩০০৳)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen
                        )
                        Text(
                            text = "নিবন্ধনের পর কৃষক ও ক্রেতা উভয়ের জন্য এককালীন ৩০০ টাকা অ্যাক্টিভেশন ফি (২ বছরের মেয়াদ) জমা দিয়ে অ্যাডমিন যাচাইয়ের পর অ্যাকাউন্ট সক্রিয় হবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkGreen
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = role == UserRole.BUYER,
                        onClick = { role = UserRole.BUYER },
                        label = { Text("ক্রেতা (Buyer)") },
                        modifier = Modifier.weight(1f).testTag("register_role_buyer")
                    )
                    FilterChip(
                        selected = role == UserRole.FARMER,
                        onClick = { role = UserRole.FARMER },
                        label = { Text("কৃষক (Farmer)") },
                        modifier = Modifier.weight(1f).testTag("register_role_farmer")
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("পূর্ণ নাম *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("register_name_input")
                )
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নম্বর *") },
                    supportingText = { Text("সঠিক ১১ অঙ্কের বাংলাদেশী মোবাইল নম্বর দিন") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("register_phone_input")
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LightGreen),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ম্যাপ / GPS থেকে অটো লোকেশন",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        runAutoGps()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            )
                                        )
                                    }
                                },
                                enabled = !detectingLocation,
                                modifier = Modifier.weight(1f).testTag("register_auto_gps_button")
                            ) {
                                if (detectingLocation) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("অটো GPS")
                            }
                            OutlinedButton(
                                onClick = { showMapDialog = true },
                                modifier = Modifier.weight(1f).testTag("register_open_map_button")
                            ) {
                                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ম্যাপ খুলুন")
                            }
                        }
                        autoLocationStatus?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = DarkGreen)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = divDropdownExpanded,
                    onExpandedChange = { divDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedDivision,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("বিভাগ নির্বাচন করুন *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = divDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("register_division_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = divDropdownExpanded,
                        onDismissRequest = { divDropdownExpanded = false }
                    ) {
                        BangladeshLocations.divisions.forEach { div ->
                            DropdownMenuItem(
                                text = { Text(div) },
                                onClick = {
                                    selectedDivision = div
                                    val firstDist = BangladeshLocations.districtsForDivision(div).firstOrNull()?.districtBn ?: "ঢাকা"
                                    selectedDistrict = firstDist
                                    selectedUpazila = BangladeshLocations.upazilasForDistrict(firstDist).firstOrNull() ?: firstDist
                                    divDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = distDropdownExpanded,
                    onExpandedChange = { distDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedDistrict,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("জেলা নির্বাচন করুন (${districtsForDivision.size}টি) *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = distDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("register_district_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = distDropdownExpanded,
                        onDismissRequest = { distDropdownExpanded = false }
                    ) {
                        districtsForDivision.forEach { dist ->
                            DropdownMenuItem(
                                text = { Text(dist) },
                                onClick = {
                                    selectedDistrict = dist
                                    selectedUpazila = BangladeshLocations.upazilasForDistrict(dist).firstOrNull() ?: dist
                                    distDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = upzDropdownExpanded,
                    onExpandedChange = { upzDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedUpazila,
                        onValueChange = { selectedUpazila = it },
                        readOnly = true,
                        label = { Text("উপজেলা / থানা নির্বাচন করুন *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = upzDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("register_upazila_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = upzDropdownExpanded,
                        onDismissRequest = { upzDropdownExpanded = false }
                    ) {
                        upazilasForDistrict.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = {
                                    selectedUpazila = u
                                    upzDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unionName,
                        onValueChange = { unionName = it },
                        label = { Text("ইউনিয়ন") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("register_union_input")
                    )
                    OutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("গ্রাম") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("register_village_input")
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = nidLastFour,
                    onValueChange = { if (it.length <= 4) nidLastFour = it },
                    label = { Text("এনআইডি শেষ ৪ অঙ্ক (অ্যাডমিন যাচাইয়ের জন্য — গোপন থাকবে)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("register_nid_input")
                )
                if (role == UserRole.FARMER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = farmBio,
                        onValueChange = { farmBio = it },
                        label = { Text("আপনার খামার ও ফসলের বিবরণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                val passwordMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("পাসওয়ার্ড (সর্বনিম্ন ৬ অক্ষর) *") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.testTag("register_password_toggle")
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "পাসওয়ার্ড লুকান" else "পাসওয়ার্ড দেখুন"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("register_password_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("পাসওয়ার্ড নিশ্চিত করুন (Confirm Password) *") },
                    singleLine = true,
                    isError = passwordMismatch,
                    supportingText = if (passwordMismatch) {
                        { Text("দুটি পাসওয়ার্ড মেলেনি", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showConfirmPassword = !showConfirmPassword },
                            modifier = Modifier.testTag("register_confirm_password_toggle")
                        ) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showConfirmPassword) "পাসওয়ার্ড লুকান" else "পাসওয়ার্ড দেখুন"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("register_confirm_password_input")
                )
            }

            item {
                val canSubmit = fullName.isNotBlank() &&
                    phone.length >= 10 &&
                    password.length >= 6 &&
                    confirmPassword == password
                Button(
                    onClick = {
                        onRegister(
                            fullName, phone, password, confirmPassword, role,
                            selectedDivision, selectedDistrict, selectedUpazila, unionName, village, nidLastFour, farmBio
                        )
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("register_submit_button")
                ) {
                    Text("নিবন্ধন ও সক্রিয়করণে যান", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showMapDialog) {
        InteractiveBangladeshMapDialog(
            initialDistrictBn = selectedDistrict,
            onDismiss = { showMapDialog = false },
            onLocationConfirmed = { detected ->
                selectedDivision = detected.divisionBn
                selectedDistrict = detected.districtBn
                selectedUpazila = detected.upazilaBn
                autoLocationStatus = "নির্বাচিত (${detected.sourceLabel}): ${detected.upazilaBn}, ${detected.districtBn}"
                showMapDialog = false
            }
        )
    }
}
