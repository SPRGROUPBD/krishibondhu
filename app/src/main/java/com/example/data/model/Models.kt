package com.example.data.model

enum class AppLanguage(val labelBn: String, val labelEn: String) {
    BENGALI("বাংলা", "Bengali"),
    ENGLISH("English", "English")
}

enum class UserRole(val titleBn: String, val titleEn: String) {
    BUYER("ক্রেতা", "Buyer"),
    FARMER("কৃষক", "Farmer"),
    ADMIN("সুপার অ্যাডমিন", "Super Admin")
}

enum class ProductUnit(val labelBn: String, val labelEn: String) {
    MON("মণ", "Maund");

    companion object {
        const val MON_TO_KG = 40
        val allLabelsBn: List<String> = entries.map { it.labelBn }
        fun isAllowedUnit(unit: String?): Boolean {
            val clean = unit?.trim().orEmpty()
            return clean == "মণ" || clean == "মন" || clean.equals("MON", ignoreCase = true) || clean.equals("Maund", ignoreCase = true)
        }
    }
}

enum class AccountStatus(val labelBn: String, val labelEn: String) {
    PENDING_REGISTRATION("নিবন্ধন অসম্পূর্ণ", "Pending Registration"),
    PAYMENT_PENDING("পেমেন্ট অপেক্ষমান", "Payment Pending"),
    PAYMENT_UNDER_REVIEW("পেমেন্ট যাচাইাধীন", "Payment Under Review"),
    ACTIVE("সক্রিয় (Active)", "Active"),
    PAYMENT_REJECTED("পেমেন্ট প্রত্যাখ্যাত", "Payment Rejected"),
    EXPIRED("মেয়াদোত্তীর্ণ (Expired)", "Expired"),
    SUSPENDED("স্থগিত (Suspended)", "Suspended");

    fun display(lang: AppLanguage): String = if (lang == AppLanguage.BENGALI) labelBn else labelEn

    companion object {
        fun fromName(raw: String?): AccountStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PAYMENT_PENDING
    }
}

enum class ActivationPaymentStatus(val labelBn: String, val labelEn: String) {
    PENDING("অপেক্ষমান (Pending)", "Pending"),
    APPROVED("অনুমোদিত (Approved)", "Approved"),
    REJECTED("প্রত্যাখ্যাত (Rejected)", "Rejected"),
    RESUBMISSION_REQUESTED("পুনরায় জমা প্রয়োজন", "Resubmission Requested");

    fun display(lang: AppLanguage): String = if (lang == AppLanguage.BENGALI) labelBn else labelEn

    companion object {
        fun fromName(raw: String?): ActivationPaymentStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
    }
}

enum class PaymentAccountType(val labelBn: String, val labelEn: String) {
    PERSONAL("পার্সোনাল (Personal)", "Personal"),
    MERCHANT("মার্চেন্ট (Merchant)", "Merchant");

    fun display(lang: AppLanguage): String = if (lang == AppLanguage.BENGALI) labelBn else labelEn

    companion object {
        fun fromName(raw: String?): PaymentAccountType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PERSONAL
    }
}

enum class OrderStatus(val labelBn: String, val labelEn: String) {
    PENDING("Pending", "Pending"),
    ACCEPTED("Accepted", "Accepted"),
    REJECTED("Rejected", "Rejected"),
    PROCESSING("Processing", "Processing"),
    READY_FOR_PICKUP("Ready for Pickup", "Ready for Pickup"),
    SHIPPED("Shipped", "Shipped"),
    DELIVERED("Delivered", "Delivered"),
    CONFIRMED("Confirmed", "Confirmed"),
    COMPLETED("Completed", "Completed"),
    CANCELLED("Cancelled", "Cancelled"),
    DISPUTED("Disputed", "Disputed");

    fun display(lang: AppLanguage): String = when (this) {
        PENDING -> if (lang == AppLanguage.BENGALI) "অপেক্ষমান (Pending)" else "Pending"
        ACCEPTED -> if (lang == AppLanguage.BENGALI) "গৃহীত (Accepted)" else "Accepted"
        REJECTED -> if (lang == AppLanguage.BENGALI) "প্রত্যাখ্যাত (Rejected)" else "Rejected"
        PROCESSING -> if (lang == AppLanguage.BENGALI) "প্রস্তুত হচ্ছে (Processing)" else "Processing"
        READY_FOR_PICKUP -> if (lang == AppLanguage.BENGALI) "সংগ্রহের জন্য প্রস্তুত" else "Ready for Pickup"
        SHIPPED -> if (lang == AppLanguage.BENGALI) "পথে আছে (Shipped)" else "Shipped"
        DELIVERED -> if (lang == AppLanguage.BENGALI) "পৌঁছেছে (Delivered)" else "Delivered"
        CONFIRMED -> if (lang == AppLanguage.BENGALI) "নিশ্চিতকৃত (Confirmed)" else "Confirmed"
        COMPLETED -> if (lang == AppLanguage.BENGALI) "সম্পন্ন (Completed)" else "Completed"
        CANCELLED -> if (lang == AppLanguage.BENGALI) "বাতিল (Cancelled)" else "Cancelled"
        DISPUTED -> if (lang == AppLanguage.BENGALI) "বিরোধপূর্ণ (Disputed)" else "Disputed"
    }
}

enum class DeliveryOption(val labelBn: String, val labelEn: String) {
    FARM_PICKUP(
        "ক্রেতা সরাসরি কৃষকের কাছ থেকে পণ্য সংগ্রহ করবেন",
        "Buyer collects directly from farmer"
    ),
    DIRECT_DELIVERY(
        "কৃষক ও ক্রেতা সরাসরি ডেলিভারি ব্যবস্থা করবেন",
        "Farmer & buyer arrange delivery directly"
    )
}

enum class PaymentMethod(val labelBn: String, val labelEn: String, val isDigital: Boolean) {
    CASH_ON_PICKUP("Cash on Pickup", "Cash on Pickup", false),
    DIRECT_CASH("Direct Cash Payment", "Direct Cash Payment", false),
    BANK_TRANSFER("Direct Bank Transfer", "Direct Bank Transfer", true),
    BKASH("bKash", "bKash", true),
    NAGAD("Nagad", "Nagad", true);

    fun display(lang: AppLanguage): String = when (this) {
        CASH_ON_PICKUP -> if (lang == AppLanguage.BENGALI) "সংগ্রহের সময় নগদ (Cash on Pickup)" else "Cash on Pickup"
        DIRECT_CASH -> if (lang == AppLanguage.BENGALI) "সরাসরি নগদ প্রদান (Direct Cash)" else "Direct Cash Payment"
        BANK_TRANSFER -> if (lang == AppLanguage.BENGALI) "সরাসরি ব্যাংক ট্রান্সফার" else "Direct Bank Transfer"
        BKASH -> if (lang == AppLanguage.BENGALI) "বিকাশ (bKash - গেটওয়ে)" else "bKash (Gateway)"
        NAGAD -> if (lang == AppLanguage.BENGALI) "নগদ (Nagad - গেটওয়ে)" else "Nagad (Gateway)"
    }
}

enum class PriceSort {
    NEWEST,
    PRICE_LOW_HIGH,
    PRICE_HIGH_LOW
}

data class AdminDashboardStats(
    val totalFarmers: Int = 0,
    val verifiedFarmers: Int = 0,
    val totalBuyers: Int = 0,
    val verifiedBuyers: Int = 0,
    val activeProducts: Int = 0,
    val todaysOrders: Int = 0,
    val completedOrders: Int = 0,
    val totalSalesAmount: Double = 0.0,
    val districtSales: Map<String, Double> = emptyMap(),
    val pendingActivationPayments: Int = 0,
    val approvedActivationPayments: Int = 0,
    val rejectedActivationPayments: Int = 0,
    val activeAccounts: Int = 0,
    val expiredAccounts: Int = 0,
    val suspendedAccounts: Int = 0,
    val pendingOrderPaymentVerifications: Int = 0
)

data class AdConfig(
    val adsEnabled: Boolean = true,
    val showAdsLabel: Boolean = true,
    val androidAdsEnabled: Boolean = true,
    val androidBannerId: String = "",
    val androidInterstitialId: String = "",
    val mobileWebAdsEnabled: Boolean = true,
    val mobileAdNetworkPriority: String = "ALL",
    val mobileCustomAdHtml: String = "",
    val mobileDirectLinkUrl: String = "",
    val adsenseEnabled: Boolean = false,
    val adsenseClient: String = "",
    val adsenseTopSlot: String = "",
    val adsenseFeedSlot: String = "",
    val adsenseFooterSlot: String = "",
    val adsterraEnabled: Boolean = false,
    val adsterraBannerKey: String = "",
    val adsterraScriptUrl: String = "",
    val adsterraSocialBarUrl: String = "",
    val monetagEnabled: Boolean = false,
    val monetagZoneId: String = "",
    val monetagScriptUrl: String = "",
    val customAdEnabled: Boolean = false,
    val customAdName: String = "Ezoic / Media.net / Custom",
    val customTopHtml: String = "",
    val customFeedHtml: String = "",
    val customFooterHtml: String = "",
    val sponsorEnabled: Boolean = true,
    val sponsorTitle: String = "সরাসরি কৃষকের বীজ, সার ও আধুনিক কৃষি পরামর্শ",
    val sponsorSubtitle: String = "কৃষি বন্ধু স্পন্সর স্লট — অ্যাডমিন প্যানেল থেকে কাস্টম বিজ্ঞাপন বা ব্যানার যুক্ত করুন।",
    val sponsorImageUrl: String = "",
    val sponsorTargetUrl: String = "",
    val sponsorCtaText: String = "বিস্তারিত দেখুন",
    val updatedAt: Long = 0L
)

fun Double.formatBdt(): String {
    val rounded = kotlin.math.round(this * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
        "৳ %,d".format(rounded.toLong())
    } else {
        "৳ %,.2f".format(rounded)
    }
}

fun Double.formatQty(): String {
    return if (this % 1.0 == 0.0) {
        this.toLong().toString()
    } else {
        "%.1f".format(this)
    }
}

fun String.normalizeBn(): String {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""
    return java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFC)
}

fun String.normalizeBnQuery(): String =
    normalizeBn().lowercase(java.util.Locale.ENGLISH)

fun String.equalsBn(other: String?): Boolean {
    if (other == null) return false
    return this.normalizeBn().equals(other.normalizeBn(), ignoreCase = true)
}

fun String.containsBn(query: String?): Boolean {
    if (query.isNullOrBlank()) return true
    return this.normalizeBnQuery().contains(query.normalizeBnQuery())
}
