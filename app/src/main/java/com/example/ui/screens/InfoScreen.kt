package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.LightGreen
import com.example.ui.viewmodel.MarketplaceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("নোটিফিকেশন") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    TextButton(onClick = onMarkAllRead) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সব পঠিত")
                    }
                }
            )
        }
    ) { padding ->
        val visibleNotifs = uiState.visibleNotifications
        if (visibleNotifs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyStateView(
                    emoji = "🔔",
                    title = "কোনো নোটিফিকেশন নেই",
                    subtitle = "অর্ডার ও অ্যাডমিন বার্তা এখানে দেখা যাবে।"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleNotifs, key = { it.id }) { notif ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface else LightGreen
                        )
                    ) {
                        Row(modifier = Modifier.padding(14.dp)) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = DarkGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(notif.titleBn, fontWeight = FontWeight.Bold)
                                Text(notif.messageBn, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    topic: String,
    onBack: () -> Unit
) {
    val (title, body) = when (topic) {
        "about" -> "আমাদের সম্পর্কে (Krishi Bondhu)" to """
            কৃষি বন্ধু (Krishi Bondhu) সারা বাংলাদেশের কৃষকদের জন্য তৈরি একটি ১০০% দালালমুক্ত ও সরাসরি কৃষক-থেকে-ক্রেতা ডিজিটাল কৃষি বাজার।
            
            মূল নীতি:
            "কৃষক ও ক্রেতার মাঝে কোনো দালাল থাকবে না। কৃষক নিজেই বিক্রি করবেন, ক্রেতা সরাসরি কিনবেন।"
            
            কভারেজ: বাংলাদেশের ৮টি বিভাগ ও ৬৪টি জেলার সকল উপজেলা ও ইউনিয়নের কৃষক ও ক্রেতাদের জন্য উন্মুক্ত।
            
            • কৃষক নিজেই তার ফসলের ন্যায্য দাম নির্ধারণ করেন।
            • প্ল্যাটফর্ম কোনো বাধ্যতামূলক কমিশন কাটে না।
            • অ্যাডমিন শুধুমাত্র কৃষকের পরিচয় যাচাই ও শৃঙ্খলা তদারকি করেন।
        """.trimIndent()

        "integration" -> "ব্যাকএন্ড, ওয়েবসাইট ও এপিআই ইন্টিগ্রেশন" to """
            এই অ্যান্ড্রয়েড অ্যাপ (com.krishibondhu.bd) এবং এর ওয়েবসাইট ভার্সন একই Firebase Realtime Database-এর সাথে রিয়েল-টাইম যুক্ত।
            
            সিস্টেম আর্কিটেকচার:
            ১. Firebase Realtime Database (`screen-playbd`): রিয়েল-টাইম ফসল, ব্যবহারকারী, অর্ডার এবং ক্রেতা-থেকে-কৃষক ও অ্যাডমিন নোটিফিকেশন সিঙ্ক হয়।
            ২. অফলাইন ক্যাশ (Room SQLite): ইন্টারনেট না থাকলেও ক্যাশকৃত তথ্য দেখা যায় এবং সুপার অ্যাডমিন অফলাইনেও প্রবেশ করতে পারেন।
            ৩. REST API কন্ট্রাক্ট: `KrishiBondhuApiService` (Retrofit + Moshi) এ `/api/auth`, `/api/products`, `/api/orders`, `/api/categories`, `/api/locations` এবং `/api/admin` সংজ্ঞায়িত রয়েছে।
        """.trimIndent()

        "contact" -> "যোগাযোগ ও সহায়তা" to """
            কৃষি বন্ধু কেন্দ্রীয় সহায়তা কেন্দ্র (সারা বাংলাদেশ)
            ঠিকানা: ঢাকা ও কুষ্টিয়া আঞ্চলিক সমন্বয় কেন্দ্র, বাংলাদেশ।
            হেল্পলাইন: +৮৮০ ১৭০০-০০০০০০
            ইমেইল: support@krishibondhu.bd
            সময়: প্রতিদিন সকাল ৮টা থেকে রাত ৮টা।
        """.trimIndent()

        "privacy" -> "গোপনীয়তা নীতি (Privacy Policy)" to """
            ১. কৃষকের জাতীয় পরিচয়পত্র (NID) নম্বর ও ব্যক্তিগত ঠিকানা কখনোই জনসমক্ষে বা সাধারণ ক্রেতার কাছে প্রকাশ করা হয় না।
            ২. কেবলমাত্র যাচাইকৃত পরিচয় ব্যাজ প্রদর্শিত হয়।
            ৩. পাসওয়ার্ড সর্বদা SHA-256 সল্টেড হ্যাশ হিসেবে সংরক্ষিত থাকে।
            ৪. ছবি নির্বাচনের জন্য Android Photo Picker ব্যবহৃত হয় — কোনো অতিরিক্ত স্টোরেজ পারমিশন নেওয়া হয় না।
            ৫. বিজ্ঞাপন ও মনিটাইজেশন: অ্যাপটিতে Google AdMob এবং ওয়েবসাইটে Google AdSense / অনুমোদিত বিজ্ঞাপন নেটওয়ার্ক ব্যবহার হতে পারে, যা প্রাসঙ্গিক বিজ্ঞাপন দেখাতে ডিভাইস Advertising ID বা কুকি ব্যবহার করতে পারে (ব্যক্তিগত এনআইডি বা পাসওয়ার্ড কখনোই বিজ্ঞাপন নেটওয়ার্কের সাথে শেয়ার করা হয় না)।
        """.trimIndent()

        else -> "শর্তাবলি (Terms & Conditions)" to """
            ১. কৃষি বন্ধু কোনো দালাল বা মধ্যস্বত্বভোগী প্রতিষ্ঠান নয়।
            ২. কৃষকের প্রকাশিত পণ্যের মূল্য, ওজন ও গুণগত মানের দায়িত্ব সরাসরি বিক্রেতা কৃষকের।
            ৩. ক্রেতা ও কৃষক খামার থেকে সংগ্রহ অথবা সরাসরি ডেলিভারি পদ্ধতিতে লেনদেন সম্পন্ন করবেন।
            ৪. যেকোনো অর্ডার বিরোধ বা অভিযোগ অ্যাডমিন প্যানেলের মাধ্যমে যাচাই ও সমাধান করা হবে।
            ৫. অ্যাপ ও ওয়েবসাইটের স্বাভাবিক পরিচালনার জন্য নির্ধারিত স্থানে বিজ্ঞাপন প্রদর্শিত হতে পারে।
        """.trimIndent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (topic == "about" || topic == "integration") {
                item {
                    Image(
                        painter = painterResource(id = R.drawable.krishi_bondhu_showcase),
                        contentDescription = "কৃষি বন্ধু প্ল্যাটফর্ম ওভারভিউ",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
