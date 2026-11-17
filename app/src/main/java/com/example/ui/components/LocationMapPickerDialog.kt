package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.LightGreen
import com.example.ui.theme.PrimaryGreen
import com.example.util.AutoDetectedLocation
import com.example.util.DistrictCentroid
import com.example.util.LocationHelper
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Interactive Bangladesh 64-District Coordinate Map Picker + Live GPS Auto-Detect Dialog.
 */
@Composable
fun InteractiveBangladeshMapDialog(
    initialDistrictBn: String,
    onDismiss: () -> Unit,
    onLocationConfirmed: (AutoDetectedLocation) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCentroid by remember(initialDistrictBn) {
        mutableStateOf(
            LocationHelper.districtCentroids.firstOrNull { it.districtBn == initialDistrictBn }
                ?: LocationHelper.districtCentroids.first { it.districtBn == "কুষ্টিয়া" }
        )
    }
    var customDetected by remember { mutableStateOf<AutoDetectedLocation?>(null) }
    var detectingGps by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("ম্যাপের যেকোনো জেলা পিনে ট্যাপ করুন অথবা GPS অটো-ডিটেক্ট চাপুন") }

    val triggerGpsDetect: () -> Unit = {
        detectingGps = true
        statusText = "GPS / নেটওয়ার্ক থেকে অবস্থান খোঁজা হচ্ছে..."
        scope.launch {
            LocationHelper.detectCurrentLocation(context)
                .onSuccess { detected ->
                    customDetected = detected
                    selectedCentroid = LocationHelper.districtCentroids.firstOrNull {
                        it.districtBn == detected.districtBn
                    } ?: selectedCentroid
                    statusText = "সনাক্তকৃত অবস্থান: ${detected.upazilaBn}, ${detected.districtBn}"
                }
                .onFailure {
                    statusText = it.message ?: "GPS পাওয়া যায়নি; ম্যাপ থেকে জেলা নির্বাচন করুন"
                }
            detectingGps = false
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            triggerGpsDetect()
        } else {
            statusText = "লোকেশন অনুমতি দেওয়া হয়নি — ম্যাপ থেকে আপনার জেলা পিনে ট্যাপ করুন"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = PrimaryGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("বাংলাদেশ লোকেশন ম্যাপ (৬৪ জেলা)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Interactive coordinate map canvas of Bangladesh (lat 20.6..26.65, lng 88.0..92.7)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LightGreen,
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    val minLat = 20.6
                    val maxLat = 26.7
                    val minLng = 88.0
                    val maxLng = 92.7

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { tapOffset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    var best: DistrictCentroid? = null
                                    var bestDist = Float.MAX_VALUE
                                    LocationHelper.districtCentroids.forEach { c ->
                                        val px = (((c.lng - minLng) / (maxLng - minLng)) * w).toFloat()
                                        val py = ((1.0 - (c.lat - minLat) / (maxLat - minLat)) * h).toFloat()
                                        val d = hypot(tapOffset.x - px, tapOffset.y - py)
                                        if (d < bestDist) {
                                            bestDist = d
                                            best = c
                                        }
                                    }
                                    best?.let {
                                        selectedCentroid = it
                                        customDetected = null
                                        statusText = "ম্যাপ নির্বাচিত: ${it.districtBn} (${it.divisionBn} বিভাগ)"
                                    }
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height

                        // Grid lines
                        for (i in 1..3) {
                            val gx = w * i / 4f
                            val gy = h * i / 4f
                            drawLine(PrimaryGreen.copy(alpha = 0.14f), Offset(gx, 0f), Offset(gx, h), 1.5f)
                            drawLine(PrimaryGreen.copy(alpha = 0.14f), Offset(0f, gy), Offset(w, gy), 1.5f)
                        }

                        // Plot all 64 district nodes
                        LocationHelper.districtCentroids.forEach { c ->
                            val px = (((c.lng - minLng) / (maxLng - minLng)) * w).toFloat()
                            val py = ((1.0 - (c.lat - minLat) / (maxLat - minLat)) * h).toFloat()
                            val isSelected = c.districtBn == selectedCentroid.districtBn
                            drawCircle(
                                color = if (isSelected) Color(0xFFD97706) else PrimaryGreen.copy(alpha = 0.55f),
                                radius = if (isSelected) 13f else 6f,
                                center = Offset(px, py)
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = Color.White,
                                    radius = 5f,
                                    center = Offset(px, py)
                                )
                            }
                        }
                    }
                }

                val activeResult = customDetected ?: LocationHelper.locationForDistrictCentroid(selectedCentroid.districtBn)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "নির্বাচিত: ${activeResult.upazilaBn}, ${activeResult.districtBn} (${activeResult.divisionBn})",
                            style = MaterialTheme.typography.labelLarge,
                            color = DarkGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "স্থানাঙ্ক: ${"%.4f".format(activeResult.latitude)}, ${"%.4f".format(activeResult.longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (LocationHelper.hasLocationPermission(context)) {
                                triggerGpsDetect()
                            } else {
                                locationPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = !detectingGps,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (detectingGps) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GPS অটো")
                    }

                    OutlinedButton(
                        onClick = {
                            val res = customDetected ?: LocationHelper.locationForDistrictCentroid(selectedCentroid.districtBn)
                            runCatching {
                                val geoUri = Uri.parse("geo:${res.latitude},${res.longitude}?q=${res.latitude},${res.longitude}(${Uri.encode(res.districtBn)})")
                                context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ম্যাপ খুলুন")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = customDetected ?: LocationHelper.locationForDistrictCentroid(selectedCentroid.districtBn)
                    onLocationConfirmed(result)
                }
            ) {
                Text("লোকেশন নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
