package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ProductCard
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.LightGreen
import com.example.ui.theme.PrimaryGreen
import com.example.ui.viewmodel.MarketplaceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerProfileScreen(
    farmerId: String,
    uiState: MarketplaceUiState,
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    onQuickAddToCart: (String) -> Unit
) {
    val context = LocalContext.current
    val farmer = uiState.farmers.firstOrNull { it.id == farmerId }
    val farmerProducts = uiState.products.filter { it.farmerId == farmerId && it.status == "ACTIVE" }
    val categoryEmojiMap = uiState.categories.associate { it.id to it.emoji }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(farmer?.fullName ?: "কৃষকের প্রোফাইল") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        }
    ) { padding ->
        if (farmer == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("কৃষকের তথ্য পাওয়া যায়নি")
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
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(LightGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🧑‍🌾", fontSize = 32.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = farmer.fullName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (farmer.verificationStatus == "VERIFIED") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Verified,
                                            contentDescription = "যাচাইকৃত কৃষক",
                                            tint = PrimaryGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${farmer.village}, ${farmer.unionName}, ${farmer.upazila}, ${farmer.district}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = if (farmer.verificationStatus == "VERIFIED") LightGreen else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (farmer.verificationStatus == "VERIFIED")
                                            "যাচাইকৃত কৃষক • ★ ${farmer.rating} (${farmer.reviewCount} রিভিউ)"
                                        else
                                            "পরিচয় যাচাই প্রক্রিয়াধীন",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (farmer.farmBio.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = farmer.farmBio,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${farmer.phone}".toUri()))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("সরাসরি কল")
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_SENDTO, "smsto:${farmer.phone}".toUri()))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("এসএমএস")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "নিরাপত্তা নীতি: কৃষকের এনআইডি ও ব্যক্তিগত গোপনীয় তথ্য প্রকাশ্যে প্রদর্শিত হয় না।",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "এই কৃষকের ফসলসমূহ (${farmerProducts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (farmerProducts.isEmpty()) {
                item {
                    EmptyStateView(
                        emoji = "🌾",
                        title = "বর্তমানে কোনো ফসল তালিকাভুক্ত নেই",
                        subtitle = "কৃষক নতুন ফসল যুক্ত করলে এখানে দেখা যাবে।"
                    )
                }
            } else {
                items(farmerProducts, key = { it.id }) { prod ->
                    ProductCard(
                        product = prod,
                        categoryEmoji = categoryEmojiMap[prod.categoryId] ?: "🌾",
                        language = uiState.language,
                        onClick = { onProductClick(prod.id) },
                        onQuickAddToCart = { onQuickAddToCart(prod.id) }
                    )
                }
            }
        }
    }
}
