package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["phone"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phone: String,
    val passwordHash: String,
    val role: String, // FARMER, BUYER, ADMIN
    val division: String = "খুলনা",
    val district: String = "কুষ্টিয়া",
    val upazila: String,
    val unionName: String = "",
    val village: String = "",
    val farmBio: String = "",
    val nidMasked: String = "", // Never expose raw NID publicly
    val profileImageUri: String = "", // Compact Base64 data URL synced with RTDB
    val verificationStatus: String = "PENDING", // PENDING, VERIFIED, REJECTED
    val isMobileVerified: Boolean = false,
    val rating: Double = 4.8,
    val reviewCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isDemoRecord: Boolean = false,
    val accountStatus: String = "PAYMENT_PENDING", // PENDING_REGISTRATION, PAYMENT_PENDING, PAYMENT_UNDER_REVIEW, ACTIVE, PAYMENT_REJECTED, EXPIRED, SUSPENDED
    val activationDate: Long = 0L,
    val expiryDate: Long = 0L,
    val validityYears: Int = 2,
    val lastPaymentId: String = "",
    val adsDisabled: Boolean = false
) {
    fun isMarketplaceActive(now: Long = System.currentTimeMillis()): Boolean {
        if (role == "ADMIN") return true
        if (accountStatus != "ACTIVE") return false
        return expiryDate <= 0L || expiryDate > now
    }

    fun daysUntilExpiry(now: Long = System.currentTimeMillis()): Int? {
        if (expiryDate <= 0L) return null
        val diff = expiryDate - now
        return if (diff <= 0L) 0 else kotlin.math.ceil(diff / 86_400_000.0).toInt()
    }
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val nameBn: String,
    val nameEn: String,
    val groupKey: String, // GRAIN, VEGETABLE, FRUIT, PULSE, OILSEED, PROTEIN, OTHER
    val emoji: String,
    val sortOrder: Int,
    val isActive: Boolean = true
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val divisionBn: String,
    val districtBn: String,
    val upazilaBn: String,
    val unionBn: String,
    val villageBn: String,
    val isActive: Boolean = true
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["farmerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("farmerId"), Index("categoryId"), Index("upazila")]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val farmerId: String,
    val farmerName: String,
    val farmerVerified: Boolean,
    val categoryId: String,
    val categoryNameBn: String,
    val nameBn: String,
    val nameEn: String,
    val descriptionBn: String,
    val imageUri: String = "", // Content URI from Photo Picker or empty for curated artwork
    val availableQuantity: Double,
    val unit: String = "মণ", // শুধুমাত্র মণ (১ মণ = ৪০ কেজি)
    val pricePerUnit: Double,
    val harvestDate: String,
    val availabilityDate: String,
    val division: String = "ঢাকা",
    val district: String,
    val upazila: String,
    val unionName: String,
    val village: String,
    val pickupAddress: String,
    val supportsFarmPickup: Boolean = true,
    val supportsDirectDelivery: Boolean = true,
    val deliveryChargeEstimate: Double = 60.0,
    val isWholesale: Boolean = false,
    val isPopular: Boolean = false,
    val status: String = "ACTIVE", // ACTIVE, PAUSED, OUT_OF_STOCK
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDemoRecord: Boolean = false
)

@Entity(
    tableName = "cart_items",
    indices = [Index("productId"), Index("farmerId")]
)
data class CartItemEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val farmerId: String,
    val farmerName: String,
    val productNameBn: String,
    val unit: String,
    val pricePerUnit: Double,
    val quantity: Double,
    val maxAvailableQuantity: Double,
    val supportsFarmPickup: Boolean,
    val supportsDirectDelivery: Boolean,
    val deliveryChargeEstimate: Double
)

@Entity(
    tableName = "orders",
    indices = [Index("buyerId"), Index("farmerId"), Index("status")]
)
data class OrderEntity(
    @PrimaryKey val id: String,
    val orderCode: String,
    val buyerId: String,
    val buyerName: String,
    val buyerPhone: String,
    val farmerId: String,
    val farmerName: String,
    val farmerPhone: String,
    val status: String, // OrderStatus.name
    val deliveryOption: String, // DeliveryOption.name
    val deliveryAddress: String,
    val division: String = "ঢাকা",
    val district: String,
    val upazila: String,
    val productTotal: Double,
    val deliveryCharge: Double,
    val grandTotal: Double,
    val paymentMethod: String, // PaymentMethod.name
    val transactionReference: String = "",
    val paymentStatus: String = "PENDING", // PENDING, AWAITING_GATEWAY, SUBMITTED, VERIFIED, PAID, FAILED, REJECTED
    val itemsSummary: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDemoRecord: Boolean = false,
    val paymentSenderNumber: String = "",
    val paymentReceiptUri: String = "",
    val farmerPaymentConfirmed: Boolean = false,
    val farmerPaymentConfirmedAt: Long = 0L,
    val adminPaymentVerified: Boolean = false,
    val adminPaymentVerifiedBy: String = "",
    val adminPaymentVerifiedAt: Long = 0L,
    val advanceAmountRequested: Double = 0.0,
    val verifiedAdvanceTotal: Double = 0.0,
    val remainingDue: Double = 0.0
)

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId")]
)
data class OrderItemEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val productId: String,
    val productNameBn: String,
    val unit: String,
    val quantity: Double,
    val pricePerUnit: Double,
    val lineTotal: Double
)

@Entity(tableName = "reviews", indices = [Index("farmerId"), Index("productId")])
data class ReviewEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val productId: String,
    val farmerId: String,
    val buyerId: String,
    val buyerName: String,
    val rating: Int,
    val commentBn: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "complaints")
data class ComplaintEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val reporterId: String,
    val reporterName: String,
    val subjectBn: String,
    val descriptionBn: String,
    val status: String = "OPEN", // OPEN, IN_REVIEW, RESOLVED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val targetRole: String = "",
    val senderRole: String = "SYSTEM",
    val orderId: String = "",
    val type: String = "SYSTEM",
    val titleBn: String,
    val messageBn: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val actorId: String,
    val actorRole: String,
    val actionBn: String,
    val targetEntity: String,
    val targetId: String,
    val detailsBn: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "activation_payments",
    indices = [Index("userId"), Index("status"), Index("transactionId")]
)
data class ActivationPaymentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userName: String,
    val userPhone: String,
    val accountRole: String, // FARMER or BUYER
    val paymentType: String = "ACTIVATION", // ACTIVATION or RENEWAL
    val paymentMethod: String, // BKASH or NAGAD
    val receiverNumber: String = "01756144900",
    val receiverAccountType: String = "PERSONAL", // PERSONAL or MERCHANT
    val senderMobile: String,
    val transactionId: String,
    val amount: Double = 300.0,
    val receiptUri: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, RESUBMISSION_REQUESTED
    val adminNotes: String = "",
    val reviewedBy: String = "",
    val reviewedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "payment_settings")
data class PaymentSettingsEntity(
    @PrimaryKey val id: String = "default",
    val bKashNumber: String = "01756144900",
    val bKashAccountType: String = "PERSONAL", // PERSONAL or MERCHANT
    val nagadNumber: String = "01756144900",
    val nagadAccountType: String = "PERSONAL", // PERSONAL or MERCHANT
    val farmerFee: Double = 300.0,
    val buyerFee: Double = 300.0,
    val renewalFee: Double = 300.0,
    val adRemovalFee: Double = 200.0,
    val validityYears: Int = 2,
    val paymentInstructions: String = "বিকাশ বা নগদ নম্বরে ৩০০ টাকা পরিশোধ করে প্রেরকের মোবাইল নম্বর, ট্রানজেকশন আইডি (TxID) ও রসিদের ছবি জমা দিন। অ্যাডমিন যাচাইয়ের পর ২ বছরের জন্য অ্যাকাউন্ট সক্রিয় হবে।",
    val notify30Days: Boolean = true,
    val notify7Days: Boolean = true,
    val notifyOnExpiry: Boolean = true,
    val updatedAt: Long = 0L
)

@Entity(tableName = "farmer_payment_settings")
data class FarmerPaymentSettingsEntity(
    @PrimaryKey val farmerId: String,
    val bKashNumber: String = "",
    val nagadNumber: String = "",
    val rocketNumber: String = "",
    val bankAccountInfo: String = "",
    val updatedAt: Long = 0L
) {
    fun hasAnyNumber(): Boolean =
        bKashNumber.isNotBlank() || nagadNumber.isNotBlank() || rocketNumber.isNotBlank() || bankAccountInfo.isNotBlank()
}

@Entity(
    tableName = "order_payments",
    indices = [
        Index("orderId"),
        Index("farmerId"),
        Index("buyerId"),
        Index("transactionIdNormalized")
    ]
)
data class OrderPaymentEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val orderCode: String,
    val buyerId: String,
    val buyerName: String,
    val farmerId: String,
    val farmerName: String,
    val amount: Double,
    val paymentMethod: String,
    val receiverNumber: String = "",
    val senderNumber: String = "",
    val transactionId: String,
    val transactionIdNormalized: String,
    val referenceNote: String = "",
    val status: String = "PENDING_VERIFICATION", // PENDING_VERIFICATION, CONFIRMED, REJECTED
    val rejectionReason: String = "",
    val verifiedBy: String = "",
    val verifiedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "hero_sliders")
data class HeroSliderEntity(
    @PrimaryKey val id: String,
    val badgeBn: String = "১০০% দালালমুক্ত বাজার",
    val titleBn: String,
    val subtitleBn: String = "",
    val bullet1Bn: String = "কৃষকের ন্যায্য দাম",
    val bullet2Bn: String = "ক্রেতার ন্যায্য দামে ক্রয়",
    val bullet3Bn: String = "সরাসরি যোগাযোগ",
    val ctaTextBn: String = "এখনই অর্ডার করুন",
    val targetRoute: String = "browse",
    val imageUri: String = "",
    val sortOrder: Int = 1,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

