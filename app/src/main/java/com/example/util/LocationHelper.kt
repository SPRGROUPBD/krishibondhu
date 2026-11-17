package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.BangladeshLocations
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.*

data class AutoDetectedLocation(
    val latitude: Double,
    val longitude: Double,
    val divisionBn: String,
    val districtBn: String,
    val upazilaBn: String,
    val addressLine: String,
    val sourceLabel: String = "GPS / মানচিত্র"
)

data class DistrictCentroid(
    val divisionBn: String,
    val districtBn: String,
    val lat: Double,
    val lng: Double
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    // Approximate geographic centroids (lat, lng) for all 64 districts of Bangladesh
    val districtCentroids: List<DistrictCentroid> = listOf(
        // ঢাকা
        DistrictCentroid("ঢাকা", "ঢাকা", 23.8103, 90.4125),
        DistrictCentroid("ঢাকা", "গাজীপুর", 24.0023, 90.4264),
        DistrictCentroid("ঢাকা", "নারায়ণগঞ্জ", 23.6238, 90.5000),
        DistrictCentroid("ঢাকা", "নরসিংদী", 23.9229, 90.7177),
        DistrictCentroid("ঢাকা", "মানিকগঞ্জ", 23.8644, 90.0047),
        DistrictCentroid("ঢাকা", "মুন্সীগঞ্জ", 23.5422, 90.5305),
        DistrictCentroid("ঢাকা", "টাঙ্গাইল", 24.2513, 89.9167),
        DistrictCentroid("ঢাকা", "কিশোরগঞ্জ", 24.4449, 90.7766),
        DistrictCentroid("ঢাকা", "ফরিদপুর", 23.6071, 89.8429),
        DistrictCentroid("ঢাকা", "মাদারীপুর", 23.1641, 90.1897),
        DistrictCentroid("ঢাকা", "শরীয়তপুর", 23.2423, 90.4348),
        DistrictCentroid("ঢাকা", "গোপালগঞ্জ", 23.0051, 89.8266),
        DistrictCentroid("ঢাকা", "রাজবাড়ী", 23.7574, 89.6445),
        // চট্টগ্রাম
        DistrictCentroid("চট্টগ্রাম", "চট্টগ্রাম", 22.3569, 91.7832),
        DistrictCentroid("চট্টগ্রাম", "কক্সবাজার", 21.4272, 92.0058),
        DistrictCentroid("চট্টগ্রাম", "কুমিল্লা", 23.4607, 91.1809),
        DistrictCentroid("চট্টগ্রাম", "ব্রাহ্মণবাড়িয়া", 23.9571, 91.1119),
        DistrictCentroid("চট্টগ্রাম", "চাঁদপুর", 23.2333, 90.6713),
        DistrictCentroid("চট্টগ্রাম", "নোয়াখালী", 22.8696, 91.0994),
        DistrictCentroid("চট্টগ্রাম", "ফেনী", 23.0159, 91.3976),
        DistrictCentroid("চট্টগ্রাম", "লক্ষ্মীপুর", 22.9425, 90.8412),
        DistrictCentroid("চট্টগ্রাম", "রাঙ্গামাটি", 22.7324, 92.2985),
        DistrictCentroid("চট্টগ্রাম", "খাগড়াছড়ি", 23.1193, 91.9847),
        DistrictCentroid("চট্টগ্রাম", "বান্দরবান", 22.1953, 92.2184),
        // রাজশাহী
        DistrictCentroid("রাজশাহী", "রাজশাহী", 24.3745, 88.6042),
        DistrictCentroid("রাজশাহী", "বগুড়া", 24.8465, 89.3773),
        DistrictCentroid("রাজশাহী", "পাবনা", 24.0064, 89.2372),
        DistrictCentroid("রাজশাহী", "সিরাজগঞ্জ", 24.4534, 89.7007),
        DistrictCentroid("রাজশাহী", "নাটোর", 24.4206, 89.0003),
        DistrictCentroid("রাজশাহী", "নওগাঁ", 24.7936, 88.9318),
        DistrictCentroid("রাজশাহী", "চাঁপাইনবাবগঞ্জ", 24.5965, 88.2775),
        DistrictCentroid("রাজশাহী", "জয়পুরহাট", 25.0968, 89.0227),
        // খুলনা
        DistrictCentroid("খুলনা", "কুষ্টিয়া", 23.9013, 89.1205),
        DistrictCentroid("খুলনা", "খুলনা", 22.8456, 89.5403),
        DistrictCentroid("খুলনা", "যশোর", 23.1634, 89.2182),
        DistrictCentroid("খুলনা", "ঝিনাইদহ", 23.5448, 89.1539),
        DistrictCentroid("খুলনা", "চুয়াডাঙ্গা", 23.6402, 88.8418),
        DistrictCentroid("খুলনা", "মেহেরপুর", 23.7622, 88.6318),
        DistrictCentroid("খুলনা", "মাগুরা", 23.4873, 89.4199),
        DistrictCentroid("খুলনা", "নড়াইল", 23.1725, 89.5127),
        DistrictCentroid("খুলনা", "সাতক্ষীরা", 22.7185, 89.0705),
        DistrictCentroid("খুলনা", "বাগেরহাট", 22.6516, 89.7859),
        // বরিশাল
        DistrictCentroid("বরিশাল", "বরিশাল", 22.7010, 90.3535),
        DistrictCentroid("বরিশাল", "পটুয়াখালী", 22.3596, 90.3299),
        DistrictCentroid("বরিশাল", "ভোলা", 22.6859, 90.6482),
        DistrictCentroid("বরিশাল", "পিরোজপুর", 22.5841, 89.9720),
        DistrictCentroid("বরিশাল", "বরগুনা", 22.1592, 90.1256),
        DistrictCentroid("বরিশাল", "ঝালকাঠি", 22.6406, 90.1987),
        // সিলেট
        DistrictCentroid("সিলেট", "সিলেট", 24.8949, 91.8687),
        DistrictCentroid("সিলেট", "সুনামগঞ্জ", 25.0658, 91.3950),
        DistrictCentroid("সিলেট", "হবিগঞ্জ", 24.3749, 91.4155),
        DistrictCentroid("সিলেট", "মৌলভীবাজার", 24.4829, 91.7774),
        // রংপুর
        DistrictCentroid("রংপুর", "রংপুর", 25.7439, 89.2752),
        DistrictCentroid("রংপুর", "দিনাজপুর", 25.6217, 88.6355),
        DistrictCentroid("রংপুর", "কুড়িগ্রাম", 25.8054, 89.6362),
        DistrictCentroid("রংপুর", "গাইবান্ধা", 25.3288, 89.5281),
        DistrictCentroid("রংপুর", "নীলফামারী", 25.9318, 88.8560),
        DistrictCentroid("রংপুর", "লালমনিরহাট", 25.9923, 89.2847),
        DistrictCentroid("রংপুর", "ঠাকুরগাঁও", 26.0337, 88.4617),
        DistrictCentroid("রংপুর", "পঞ্চগড়", 26.3411, 88.5542),
        // ময়মনসিংহ
        DistrictCentroid("ময়মনসিংহ", "ময়মনসিংহ", 24.7471, 90.4203),
        DistrictCentroid("ময়মনসিংহ", "জামালপুর", 24.9375, 89.9378),
        DistrictCentroid("ময়মনসিংহ", "নেত্রকোনা", 24.8709, 90.7279),
        DistrictCentroid("ময়মনসিংহ", "শেরপুর", 25.0205, 90.0153)
    )

    private val englishAliasToDistrictBn = mapOf(
        "dhaka" to "ঢাকা", "dakka" to "ঢাকা", "savar" to "ঢাকা",
        "gazipur" to "গাজীপুর", "narayanganj" to "নারায়ণগঞ্জ", "narsingdi" to "নরসিংদী",
        "manikganj" to "মানিকগঞ্জ", "munshiganj" to "মুন্সীগঞ্জ", "tangail" to "টাঙ্গাইল",
        "kishoreganj" to "কিশোরগঞ্জ", "faridpur" to "ফরিদপুর", "madaripur" to "মাদারীপুর",
        "shariatpur" to "শরীয়তপুর", "gopalganj" to "গোপালগঞ্জ", "rajbari" to "রাজবাড়ী",
        "chattogram" to "চট্টগ্রাম", "chittagong" to "চট্টগ্রাম", "cox's bazar" to "কক্সবাজার",
        "coxs bazar" to "কক্সবাজার", "cumilla" to "কুমিল্লা", "comilla" to "কুমিল্লা",
        "brahmanbaria" to "ব্রাহ্মণবাড়িয়া", "chandpur" to "চাঁদপুর", "noakhali" to "নোয়াখালী",
        "feni" to "ফেনী", "lakshmipur" to "লক্ষ্মীপুর", "rangamati" to "রাঙ্গামাটি",
        "khagrachari" to "খাগড়াছড়ি", "bandarban" to "বান্দরবান",
        "rajshahi" to "রাজশাহী", "bogura" to "বগুড়া", "bogra" to "বগুড়া",
        "pabna" to "পাবনা", "sirajganj" to "সিরাজগঞ্জ", "natore" to "নাটোর",
        "naogaon" to "নওগাঁ", "chapai" to "চাঁপাইনবাবগঞ্জ", "nawabganj" to "চাঁপাইনবাবগঞ্জ",
        "joypurhat" to "জয়পুরহাট",
        "kushtia" to "কুষ্টিয়া", "khulna" to "খুলনা", "jashore" to "যশোর", "jessore" to "যশোর",
        "jhenaidah" to "ঝিনাইদহ", "chuadanga" to "চুয়াডাঙ্গা", "meherpur" to "মেহেরপুর",
        "magura" to "মাগুরা", "narail" to "নড়াইল", "satkhira" to "সাতক্ষীরা", "bagerhat" to "বাগেরহাট",
        "barishal" to "বরিশাল", "barisal" to "বরিশাল", "patuakhali" to "পটুয়াখালী",
        "bhola" to "ভোলা", "pirojpur" to "পিরোজপুর", "barguna" to "বরগুনা", "jhalokati" to "ঝালকাঠি",
        "sylhet" to "সিলেট", "sunamganj" to "সুনামগঞ্জ", "habiganj" to "হবিগঞ্জ", "moulvibazar" to "মৌলভীবাজার",
        "rangpur" to "রংপুর", "dinajpur" to "দিনাজপুর", "kurigram" to "কুড়িগ্রাম",
        "gaibandha" to "গাইবান্ধা", "nilphamari" to "নীলফামারী", "lalmonirhat" to "লালমনিরহাট",
        "thakurgaon" to "ঠাকুরগাঁও", "panchagarh" to "পঞ্চগড়",
        "mymensingh" to "ময়মনসিংহ", "jamalpur" to "জামালপুর", "netrokona" to "নেত্রকোনা", "sherpur" to "শেরপুর"
    )

    @SuppressLint("MissingPermission")
    suspend fun detectCurrentLocation(context: Context): Result<AutoDetectedLocation> = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) {
            return@withContext Result.failure(SecurityException("লোকেশন অনুমতি প্রয়োজন"))
        }

        val loc = fetchBestDeviceLocation(context)
            ?: return@withContext Result.failure(IllegalStateException("GPS সিগন্যাল পাওয়া যায়নি। ম্যাপ থেকে আপনার জেলা নির্বাচন করুন।"))

        val resolved = resolveFromCoordinates(context, loc.latitude, loc.longitude, "GPS অটো-ডিটেক্ট")
        Result.success(resolved)
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchBestDeviceLocation(context: Context): Location? {
        val fused = runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val tokenSource = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token).await()
                ?: client.lastLocation.await()
        }.getOrNull()
        if (fused != null) return fused

        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val passive = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            listOfNotNull(gps, net, passive).maxByOrNull { it.time }
        }.getOrNull()
    }

    suspend fun resolveFromCoordinates(
        context: Context,
        lat: Double,
        lng: Double,
        sourceLabel: String = "মানচিত্র"
    ): AutoDetectedLocation = withContext(Dispatchers.IO) {
        val nearest = nearestDistrict(lat, lng)
        val address = reverseGeocodeAddress(context, lat, lng)

        val rawText = listOfNotNull(
            address?.subAdminArea,
            address?.locality,
            address?.adminArea,
            address?.getAddressLine(0)
        ).joinToString(" ")

        val matchedDistrict = matchDistrictFromText(rawText) ?: nearest.districtBn
        val matchedDivision = BangladeshLocations.divisionForDistrict(matchedDistrict)
        val upazilas = BangladeshLocations.upazilasForDistrict(matchedDistrict)
        val matchedUpazila = upazilas.firstOrNull { upz ->
            rawText.contains(upz, ignoreCase = true)
        } ?: address?.subLocality?.takeIf { it.isNotBlank() }
        ?: upazilas.firstOrNull()
        ?: matchedDistrict

        val formattedLine = address?.getAddressLine(0)?.takeIf { it.isNotBlank() }
            ?: "$matchedUpazila, $matchedDistrict, $matchedDivision"

        AutoDetectedLocation(
            latitude = lat,
            longitude = lng,
            divisionBn = matchedDivision,
            districtBn = matchedDistrict,
            upazilaBn = matchedUpazila,
            addressLine = formattedLine,
            sourceLabel = sourceLabel
        )
    }

    fun locationForDistrictCentroid(districtBn: String): AutoDetectedLocation {
        val c = districtCentroids.firstOrNull { it.districtBn == districtBn }
            ?: districtCentroids.first()
        val upazila = BangladeshLocations.upazilasForDistrict(c.districtBn).firstOrNull() ?: c.districtBn
        return AutoDetectedLocation(
            latitude = c.lat,
            longitude = c.lng,
            divisionBn = c.divisionBn,
            districtBn = c.districtBn,
            upazilaBn = upazila,
            addressLine = "$upazila, ${c.districtBn}, ${c.divisionBn}",
            sourceLabel = "মানচিত্র পিন"
        )
    }

    private fun matchDistrictFromText(text: String): String? {
        if (text.isBlank()) return null
        val directBn = BangladeshLocations.allDistricts.firstOrNull { text.contains(it.districtBn) }?.districtBn
        if (directBn != null) return directBn
        val lower = text.lowercase(Locale.ENGLISH)
        return englishAliasToDistrictBn.entries.firstOrNull { lower.contains(it.key) }?.value
    }

    private fun nearestDistrict(lat: Double, lng: Double): DistrictCentroid {
        return districtCentroids.minByOrNull { c ->
            haversineKm(lat, lng, c.lat, c.lng)
        } ?: districtCentroids.first()
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 2 * r * asin(sqrt(a))
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeAddress(context: Context, lat: Double, lng: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("bn", "BD"))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
        }
    }
}
