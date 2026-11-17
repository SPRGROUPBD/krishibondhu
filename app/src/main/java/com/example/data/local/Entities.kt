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
    val verificationStatus: String = "PENDING", // PENDING, VERIFIED, REJECTED
    val isMobileVerified: Boolean = false,
    val rating: Double = 4.8,
    val reviewCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isDemoRecord: Boolean = false
)

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
    val unit: String, // কেজি, মণ, বস্তা, টন, পিস, লিটার
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
    val paymentStatus: String = "PENDING", // PENDING, AWAITING_GATEWAY, PAID, FAILED
    val itemsSummary: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDemoRecord: Boolean = false
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
