package com.example.data.local

import com.example.data.model.BangladeshLocations
import java.security.MessageDigest

object SecurityHashUtil {
    fun hashPassword(rawPassword: String, salt: String = "KB_BD_SALT_2026"): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$rawPassword".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Seeds ONLY structural reference master catalogs (24 Agricultural Categories &
 * All-Bangladesh 8 Divisions / 64 Districts hierarchy) and removes any legacy demo records.
 * Contains zero fake farmers, buyers, products, orders, reviews, or complaints.
 */
object MasterCatalogSeeder {

    suspend fun initialize(db: KrishiBondhuDatabase) {
        // Purge any previously seeded demo records from prior builds
        db.orderDao().deleteDemoOrders()
        db.productDao().deleteDemoProducts()
        db.userDao().deleteDemoUsers()

        // Seed 24 agricultural categories if empty
        if (db.categoryDao().countCategories() == 0) {
            val categories = listOf(
                CategoryEntity("cat_paddy", "ধান", "Paddy", "GRAIN", "🌾", 1),
                CategoryEntity("cat_rice", "চাল", "Rice", "GRAIN", "🍚", 2),
                CategoryEntity("cat_wheat", "গম", "Wheat", "GRAIN", "🌿", 3),
                CategoryEntity("cat_maize", "ভুট্টা", "Maize", "GRAIN", "🌽", 4),
                CategoryEntity("cat_potato", "আলু", "Potato", "VEGETABLE", "🥔", 5),
                CategoryEntity("cat_onion", "পেঁয়াজ", "Onion", "VEGETABLE", "🧅", 6),
                CategoryEntity("cat_garlic", "রসুন", "Garlic", "VEGETABLE", "🧄", 7),
                CategoryEntity("cat_chili", "মরিচ", "Chili", "VEGETABLE", "🌶️", 8),
                CategoryEntity("cat_tomato", "টমেটো", "Tomato", "VEGETABLE", "🍅", 9),
                CategoryEntity("cat_eggplant", "বেগুন", "Eggplant", "VEGETABLE", "🍆", 10),
                CategoryEntity("cat_leafy", "শাকসবজি", "Leafy Vegetables", "VEGETABLE", "🥬", 11),
                CategoryEntity("cat_mango", "আম", "Mango", "FRUIT", "🥭", 12),
                CategoryEntity("cat_jackfruit", "কাঁঠাল", "Jackfruit", "FRUIT", "🍈", 13),
                CategoryEntity("cat_banana", "কলা", "Banana", "FRUIT", "🍌", 14),
                CategoryEntity("cat_lychee", "লিচু", "Lychee", "FRUIT", "🍒", 15),
                CategoryEntity("cat_lentil", "মসুর ডাল", "Red Lentil", "PULSE", "🫘", 16),
                CategoryEntity("cat_mung", "মুগ ডাল", "Mung Bean", "PULSE", "🫛", 17),
                CategoryEntity("cat_mustard", "সরিষা", "Mustard", "OILSEED", "🌼", 18),
                CategoryEntity("cat_sesame", "তিল", "Sesame", "OILSEED", "🌱", 19),
                CategoryEntity("cat_sugarcane", "আখ", "Sugarcane", "OTHER", "🎋", 20),
                CategoryEntity("cat_fish", "মাছ", "Fish", "PROTEIN", "🐟", 21),
                CategoryEntity("cat_milk", "দুধ", "Milk", "PROTEIN", "🥛", 22),
                CategoryEntity("cat_egg", "ডিম", "Egg", "PROTEIN", "🥚", 23),
                CategoryEntity("cat_other", "অন্যান্য কৃষিপণ্য", "Other Produce", "OTHER", "🧺", 24)
            )
            db.categoryDao().insertAll(categories)
        }

        // Seed All-Bangladesh 64-district reference locations (64 districts + key upazilas)
        if (db.locationDao().countLocations() < 64) {
            val allLocations = mutableListOf<LocationEntity>()
            var idx = 1
            BangladeshLocations.allDistricts.forEach { info ->
                // Seed primary upazila for every district, and all 6 upazilas for Kushtia
                val upazilasToSeed = if (info.districtBn == "কুষ্টিয়া") info.upazilas else listOf(info.upazilas.first())
                upazilasToSeed.forEach { upz ->
                    allLocations.add(
                        LocationEntity(
                            id = "loc_${idx++}",
                            divisionBn = info.divisionBn,
                            districtBn = info.districtBn,
                            upazilaBn = upz,
                            unionBn = "সদর ইউনিয়ন",
                            villageBn = ""
                        )
                    )
                }
            }
            db.locationDao().insertAll(allLocations)
        }
    }
}
