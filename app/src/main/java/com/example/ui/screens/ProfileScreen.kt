package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.local.UserEntity
import com.example.data.model.AccountStatus
import com.example.data.model.AppLanguage
import com.example.data.model.BangladeshLocations
import com.example.data.model.UserRole
import com.example.ui.components.AccountStatusBadge
import com.example.ui.components.AdBannerSection
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatDateBn
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.LocationHelper
import com.example.util.MediaCaptureHelper
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    uiState: MarketplaceUiState,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onLogout: () -> Unit,
    onOpenActivation: () -> Unit = {},
    onOpenAdRemovalPayment: () -> Unit = {},
    onUpdateProfile: (
        fullName: String,
        division: String,
        district: String,
        upazila: String,
        unionName: String,
        village: String,
        farmBio: String,
        nidLastFour: String,
        profileImageUri: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onOpenAdminDashboard: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenInfo: (String) -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onPermissionMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val lang = uiState.language
    val user = uiState.activeUser
    var showEditDialog by remember { mutableStateOf(false) }

    var hasNotifPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember { mutableStateOf(LocationHelper.hasLocationPermission(context)) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPermission = granted
            onPermissionMessage(
                if (granted) "নোটিফিকেশন অনুমতি চালু হয়েছে"
                else "নোটিফিকেশন অনুমতি দেওয়া হয়নি — সেটিংস থেকে চালু করতে পারবেন"
            )
        }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = granted
        onPermissionMessage(
            if (granted) "লোকেশন (GPS / মানচিত্র) অনুমতি চালু হয়েছে ✓"
            else "লোকেশন অনুমতি দেওয়া হয়নি"
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        onPermissionMessage(
            if (granted) "ক্যামেরা অনুমতি চালু হয়েছে ✓"
            else "ক্যামেরা অনুমতি দেওয়া হয়নি"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Profile Header Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        imageUri = user?.profileImageUri.orEmpty(),
                        role = user?.role.orEmpty(),
                        size = 62.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.fullName ?: "অতিথি ব্যবহারকারী",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        val roleLabel = uiState.activeRole?.titleBn ?: "অতিথি"
                        val locationLabel = if (user != null) {
                            listOf(user.unionName, user.upazila, user.district)
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                        } else "সারা বাংলাদেশ (৬৪ জেলা)"
                        Text(
                            text = "$roleLabel • $locationLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = LightGreen
                        )
                        if (user != null && user.phone.isNotBlank()) {
                            Text(
                                text = user.phone,
                                style = MaterialTheme.typography.labelSmall,
                                color = HarvestGoldLight
                            )
                        }
                    }
                }
            }
        }

        item {
            AdBannerSection(
                adConfig = uiState.adConfig,
                placementTag = "profile_top"
            )
        }

        // Account Authentication / Session Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (user == null) {
                        Text(
                            text = "অ্যাকাউন্টে প্রবেশ করুন",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ফসল বিক্রি, অর্ডার ট্র্যাকিং ও সরাসরি কেনাকাটার জন্য লগইন অথবা নতুন নিবন্ধন করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onOpenLogin,
                                modifier = Modifier.weight(1f).testTag("profile_login_button")
                            ) {
                                Text("লগইন")
                            }
                            Button(
                                onClick = onOpenRegister,
                                modifier = Modifier.weight(1f).testTag("profile_register_button")
                            ) {
                                Text("নতুন নিবন্ধন")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "সক্রিয় অ্যাকাউন্ট (${uiState.activeRole?.titleBn ?: ""})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (user.role != UserRole.ADMIN.name) {
                                AccountStatusBadge(user.accountStatus, user.expiryDate, lang)
                            }
                        }
                        if (user.farmBio.isNotBlank()) {
                            Text(
                                text = user.farmBio,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val verificationLabel = when (user.verificationStatus) {
                            "VERIFIED" -> "যাচাইকৃত ✓ (Verified)"
                            "REJECTED" -> "যাচাই প্রত্যাখ্যাত (Rejected)"
                            else -> "অ্যাডমিন যাচাই অপেক্ষমাণ (Pending)"
                        }
                        Text(
                            text = "পরিচয় অবস্থা: $verificationLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (user.verificationStatus == "VERIFIED") PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (user.role != UserRole.ADMIN.name) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.weight(1f).testTag("profile_edit_button")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("প্রোফাইল এডিট")
                                }
                                OutlinedButton(
                                    onClick = onLogout,
                                    modifier = Modifier.weight(1f).testTag("profile_logout_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("লগআউট")
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onLogout,
                                modifier = Modifier.fillMaxWidth().testTag("profile_logout_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("লগআউট করুন")
                            }
                        }
                    }
                }
            }
        }

        // Account Activation & 2-Year Validity Card (Farmers & Buyers)
        if (user != null && user.role != UserRole.ADMIN.name) {
            item {
                val isActive = user.isMarketplaceActive()
                val daysLeft = user.daysUntilExpiry()
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isActive) LightGreen else HarvestGoldLight),
                    border = BorderStroke(1.dp, if (isActive) PrimaryGreen else HarvestGold),
                    modifier = Modifier.testTag("profile_activation_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "অ্যাকাউন্ট সক্রিয়করণ ও ২ বছরের মেয়াদ",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen
                            )
                            AccountStatusBadge(user.accountStatus, user.expiryDate, lang)
                        }
                        if (user.activationDate > 0L) {
                            Text(
                                text = "সক্রিয় তারিখ: ${formatDateBn(user.activationDate)} • মেয়াদ শেষ: ${formatDateBn(user.expiryDate)}" +
                                    if (daysLeft != null) " (${daysLeft} দিন বাকি)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkGreen
                            )
                        } else {
                            Text(
                                text = "এককালীন ৩০০ টাকা অ্যাক্টিভেশন ফি জমা দিয়ে ২ বছরের জন্য মার্কেটপ্লেসে সম্পূর্ণ অ্যাক্সেস চালু করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F)
                            )
                        }
                        Button(
                            onClick = onOpenActivation,
                            modifier = Modifier.fillMaxWidth().testTag("profile_activation_button")
                        ) {
                            Text(
                                text = when {
                                    user.accountStatus == AccountStatus.PAYMENT_UNDER_REVIEW.name -> "পেমেন্ট অবস্থা দেখুন (যাচাইাধীন)"
                                    isActive -> "মেয়াদ ও রিনিউ ব্যবস্থাপনা (৳ ৩০০ / ২ বছর)"
                                    else -> "অ্যাকাউন্ট সক্রিয় / রিনিউ করুন (৩০০৳)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                val adFee = uiState.paymentSettings.adRemovalFee.takeIf { it > 0 }?.toInt() ?: 200
                val adFreePending = uiState.activationPayments.any {
                    it.userId == user.id && it.paymentType == "AD_FREE" && it.status == "PENDING"
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (user.adsDisabled) LightGreen else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (user.adsDisabled) PrimaryGreen else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.testTag("profile_ad_free_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "বিজ্ঞাপন মুক্ত অভিজ্ঞতা (Ad-Free)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen
                            )
                            Surface(
                                color = if (user.adsDisabled) PrimaryGreen else if (adFreePending) InfoBlueLight else HarvestGoldLight,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = when {
                                        user.adsDisabled -> "✓ Ad-Free সক্রিয়"
                                        adFreePending -> "যাচাইাধীন"
                                        else -> "৳ $adFee"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        user.adsDisabled -> Color.White
                                        adFreePending -> InfoBlue
                                        else -> DarkGreen
                                    },
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (user.adsDisabled) {
                            Text(
                                text = "✓ আপনার সব বিজ্ঞাপন বন্ধ রয়েছে (Ad-Free সক্রিয়)। অ্যাপ ও ওয়েবসাইটে কোনো বিজ্ঞাপন প্রদর্শিত হবে না।",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkGreen
                            )
                        } else if (adFreePending) {
                            Text(
                                text = "আপনার Ad-Free পেমেন্ট আবেদন অ্যাডমিন যাচাই করছেন। অনুমোদিত হলেই সব বিজ্ঞাপন বন্ধ হবে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = InfoBlue
                            )
                        } else {
                            Text(
                                text = "এককালীন মাত্র ৳ $adFee পেমেন্ট করে অ্যাপ ও ওয়েবসাইট থেকে সব বিজ্ঞাপন চিরতরে বন্ধ করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onOpenAdRemovalPayment,
                                modifier = Modifier.fillMaxWidth().testTag("profile_remove_ads_button")
                            ) {
                                Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("সব বিজ্ঞাপন বন্ধ করুন (৳ $adFee)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Super Admin Panel Entry — visible ONLY when logged in as Super Admin
        if (uiState.isSuperAdmin) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_admin_dashboard_card")
                        .clickable(onClick = onOpenAdminDashboard),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LightGreen),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("সুপার অ্যাডমিন ড্যাশবোর্ড", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DarkGreen)
                            Text(
                                "পেমেন্ট যাচাই, কৃষক ও ক্রেতা ম্যানেজমেন্ট, ২৪ ক্যাটাগরি ও অডিট লগ",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkGreen
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DarkGreen)
                    }
                }
            }
        }

        // App Preferences
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("অ্যাপের ভাষা (${lang.labelBn})") },
                        supportingContent = { Text("বাংলা / English পরিবর্তন করুন") },
                        leadingContent = { Icon(Icons.Filled.Language, contentDescription = null) },
                        trailingContent = {
                            TextButton(onClick = onToggleLanguage) {
                                Text(if (lang == AppLanguage.BENGALI) "English" else "বাংলা")
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("ডার্ক মোড (Dark Mode)") },
                        leadingContent = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                        trailingContent = {
                            Switch(checked = uiState.darkMode, onCheckedChange = onToggleDarkMode)
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("ইন্টারনেট ও রিয়েল-টাইম ডাটা সিঙ্ক") },
                        supportingContent = {
                            Text(if (uiState.isOnline) "অনলাইন • Firebase রিয়েল-টাইম সংযুক্ত" else "অফলাইন • ইন্টারনেট পেলে স্বয়ংক্রিয় সিঙ্ক হবে")
                        },
                        leadingContent = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                        trailingContent = {
                            Text(
                                text = if (uiState.isOnline) "অনলাইন ✓" else "অফলাইন",
                                color = if (uiState.isOnline) PrimaryGreen else HarvestGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("লোকেশন (GPS ও মানচিত্র)") },
                        supportingContent = { Text("ম্যাপ থেকে জেলা ও ঠিকানা অটো-শনাক্তকরণ") },
                        leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        trailingContent = {
                            if (!hasLocationPermission) {
                                OutlinedButton(onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }) { Text("চালু করুন") }
                            } else {
                                Text("সক্রিয় ✓", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("ক্যামেরা (ফসল ও প্রোফাইল ছবি)") },
                        supportingContent = { Text("সরাসরি ছবি তোলার অনুমতি") },
                        leadingContent = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        trailingContent = {
                            if (!hasCameraPermission) {
                                OutlinedButton(onClick = {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }) { Text("চালু করুন") }
                            } else {
                                Text("সক্রিয় ✓", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("ফাইল ও মিডিয়া (Photo Picker & Files)") },
                        supportingContent = { Text("গোপনীয়তা-সুরক্ষিত জিরো-পারমিশন ফাইল/ছবি নির্বাচন") },
                        leadingContent = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                        trailingContent = {
                            Text("নিরাপদ ✓", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("পুশ নোটিফিকেশন অনুমতি") },
                        supportingContent = {
                            Text(if (hasNotifPermission) "অর্ডার, মেয়াদ ও অ্যাডমিন ঘোষণার জন্য সক্রিয়" else "অনুমতি প্রয়োজন")
                        },
                        leadingContent = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
                        trailingContent = {
                            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OutlinedButton(onClick = {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }) { Text("চালু করুন") }
                            } else {
                                Text("সক্রিয় ✓", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }

        // Platform Pages (About, Contact, Privacy, Terms, Architecture/API)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    ProfileNavItem(Icons.Filled.Notifications, "নোটিফিকেশন ইনবক্স (${uiState.unreadNotificationCount})") {
                        onOpenNotifications()
                    }
                    HorizontalDivider()
                    ProfileNavItem(Icons.Filled.Info, "আমাদের সম্পর্কে (About Us)") { onOpenInfo("about") }
                    HorizontalDivider()
                    ProfileNavItem(Icons.Filled.CloudSync, "ব্যাকএন্ড ও এপিআই ইন্টিগ্রেশন গাইড") { onOpenInfo("integration") }
                    HorizontalDivider()
                    ProfileNavItem(Icons.Filled.SupportAgent, "যোগাযোগ ও সহায়তা (Contact Us)") { onOpenInfo("contact") }
                    HorizontalDivider()
                    ProfileNavItem(Icons.Filled.PrivacyTip, "গোপনীয়তা নীতি (Privacy Policy)") { onOpenInfo("privacy") }
                    HorizontalDivider()
                    ProfileNavItem(Icons.Filled.Gavel, "শর্তাবলি (Terms & Conditions)") { onOpenInfo("terms") }
                }
            }
        }

        item {
            AdBannerSection(
                adConfig = uiState.adConfig,
                placementTag = "profile"
            )
        }

        item {
            Text(
                text = "Krishi Bondhu • Version ${BuildConfig.VERSION_NAME} • Build ${BuildConfig.BUILD_VERSION}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("profile_version_footer")
            )
        }
    }

    if (showEditDialog && user != null) {
        EditProfileDialog(
            user = user,
            onDismiss = { showEditDialog = false },
            onSave = { name, div, dist, upz, union, village, bio, nid4, photoUri ->
                onUpdateProfile(name, div, dist, upz, union, village, bio, nid4, photoUri)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    user: UserEntity,
    onDismiss: () -> Unit,
    onSave: (
        fullName: String,
        division: String,
        district: String,
        upazila: String,
        unionName: String,
        village: String,
        farmBio: String,
        nidLastFour: String,
        profileImageUri: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fullName by remember(user) { mutableStateOf(user.fullName) }
    var division by remember(user) { mutableStateOf(user.division.ifBlank { BangladeshLocations.divisions.first() }) }
    var district by remember(user) { mutableStateOf(user.district.ifBlank { "কুষ্টিয়া" }) }
    var upazila by remember(user) { mutableStateOf(user.upazila) }
    var unionName by remember(user) { mutableStateOf(user.unionName) }
    var village by remember(user) { mutableStateOf(user.village) }
    var farmBio by remember(user) { mutableStateOf(user.farmBio) }
    var nidLastFour by remember(user) { mutableStateOf(user.nidMasked.takeLast(4).filter { it.isDigit() }) }
    var profileImageUri by remember(user) { mutableStateOf(user.profileImageUri) }
    var processingImage by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var divExpanded by remember { mutableStateOf(false) }
    var distExpanded by remember { mutableStateOf(false) }
    var upzExpanded by remember { mutableStateOf(false) }

    val districts = remember(division) {
        BangladeshLocations.districtsForDivision(division).map { it.districtBn }
    }
    val upazilas = remember(district) {
        BangladeshLocations.upazilasForDistrict(district)
    }

    val handleImage: (Uri) -> Unit = { uri ->
        processingImage = true
        scope.launch {
            val encoded = MediaCaptureHelper.encodeUriToCompactDataUrl(context, uri)
            profileImageUri = encoded ?: uri.toString()
            processingImage = false
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) handleImage(uri) }
    )
    val fileDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) handleImage(uri) }
    )
    val cameraTakePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) handleImage(captured)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("প্রোফাইল সম্পাদনা করুন") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserAvatar(imageUri = profileImageUri, role = user.role, size = 60.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("প্রোফাইল ছবি আপলোড", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text("ক্যামেরা", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("edit_profile_gallery_button")
                            ) { Text("গ্যালারি", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { fileDocumentLauncher.launch(arrayOf("image/*")) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text("ফাইল", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                if (processingImage) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("পূর্ণ নাম *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_name_input")
                )

                ExposedDropdownMenuBox(
                    expanded = divExpanded,
                    onExpandedChange = { divExpanded = it }
                ) {
                    OutlinedTextField(
                        value = division,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("বিভাগ *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = divExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("edit_profile_division_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = divExpanded,
                        onDismissRequest = { divExpanded = false }
                    ) {
                        BangladeshLocations.divisions.forEach { div ->
                            DropdownMenuItem(
                                text = { Text(div) },
                                onClick = {
                                    division = div
                                    val firstDist = BangladeshLocations.districtsForDivision(div).firstOrNull()?.districtBn ?: "ঢাকা"
                                    district = firstDist
                                    upazila = BangladeshLocations.upazilasForDistrict(firstDist).firstOrNull() ?: firstDist
                                    divExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = distExpanded,
                    onExpandedChange = { distExpanded = it }
                ) {
                    OutlinedTextField(
                        value = district,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("জেলা *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = distExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("edit_profile_district_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = distExpanded,
                        onDismissRequest = { distExpanded = false }
                    ) {
                        districts.forEach { d ->
                            DropdownMenuItem(
                                text = { Text(d) },
                                onClick = {
                                    district = d
                                    upazila = BangladeshLocations.upazilasForDistrict(d).firstOrNull() ?: d
                                    distExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = upzExpanded,
                    onExpandedChange = { upzExpanded = it }
                ) {
                    OutlinedTextField(
                        value = upazila,
                        onValueChange = { upazila = it },
                        readOnly = upazilas.isNotEmpty(),
                        label = { Text("উপজেলা / থানা *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = upzExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("edit_profile_upazila_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = upzExpanded,
                        onDismissRequest = { upzExpanded = false }
                    ) {
                        upazilas.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = {
                                    upazila = u
                                    upzExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unionName,
                        onValueChange = { unionName = it },
                        label = { Text("ইউনিয়ন") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit_profile_union_input")
                    )
                    OutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("গ্রাম") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = nidLastFour,
                    onValueChange = { if (it.length <= 4) nidLastFour = it },
                    label = { Text("এনআইডি শেষ ৪ অঙ্ক (ঐচ্ছিক)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = farmBio,
                    onValueChange = { farmBio = it },
                    label = { Text(if (user.role == UserRole.FARMER.name) "খামার পরিচিতি" else "ঠিকানা / পরিচিতি") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isNotBlank()) {
                        onSave(fullName, division, district, upazila, unionName, village, farmBio, nidLastFour, profileImageUri)
                    }
                },
                enabled = fullName.isNotBlank() && !processingImage,
                modifier = Modifier.testTag("edit_profile_save_button")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
private fun ProfileNavItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
