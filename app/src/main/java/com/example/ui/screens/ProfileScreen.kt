package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.AppLanguage
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.LocationHelper

@Composable
fun ProfileScreen(
    uiState: MarketplaceUiState,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onLogout: () -> Unit,
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
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (uiState.activeRole) {
                                UserRole.FARMER -> "🧑‍🌾"
                                UserRole.ADMIN -> "🛡️"
                                UserRole.BUYER -> "🛍️"
                                null -> "👤"
                            },
                            fontSize = 28.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.fullName ?: "অতিথি ব্যবহারকারী",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        val roleLabel = uiState.activeRole?.titleBn ?: "অতিথি"
                        val locationLabel = if (user != null) "${user.upazila}, ${user.district}" else "সারা বাংলাদেশ (৬৪ জেলা)"
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
                        Text(
                            text = "সক্রিয় অ্যাকাউন্ট (${uiState.activeRole?.titleBn ?: ""})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "যাচাই অবস্থা: ${user.verificationStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                "কৃষক যাচাই, ২৪ ক্যাটাগরি, এলাকা, রিপোর্ট, অভিযোগ ও অডিট লগ",
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
                        headlineContent = { Text("ক্যামেরা (ফসলের ছবি তোলা)") },
                        supportingContent = { Text("ফসল যুক্ত করতে সরাসরি ছবি তোলার অনুমতি") },
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
                            Text(if (hasNotifPermission) "অর্ডার ও অ্যাডমিন ঘোষণার জন্য সক্রিয়" else "অনুমতি প্রয়োজন")
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
    }
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
