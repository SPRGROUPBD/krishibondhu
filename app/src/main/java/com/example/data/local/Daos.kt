package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'FARMER' ORDER BY verificationStatus DESC, rating DESC")
    fun observeFarmers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'BUYER' ORDER BY createdAt DESC")
    fun observeBuyers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsersOnce(): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Query("DELETE FROM users WHERE isDemoRecord = 1 OR phone LIKE 'farmer_%'")
    suspend fun deleteDemoUsers()

    @Query("DELETE FROM users WHERE id != 'admin_super'")
    suspend fun clearNonAdminUsers()

    @Query("DELETE FROM users WHERE id != 'admin_super' AND id NOT IN (:ids)")
    suspend fun deleteNonAdminUsersNotIn(ids: List<String>)

    @Query("DELETE FROM users WHERE id = :id AND id != 'admin_super'")
    suspend fun deleteNonAdminUserById(id: String)

    @Upsert
    suspend fun insertAll(users: List<UserEntity>)

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("UPDATE users SET verificationStatus = :status WHERE id = :userId")
    suspend fun updateVerificationStatus(userId: String, status: String)

    @Query("UPDATE users SET accountStatus = :accountStatus WHERE id = :userId")
    suspend fun updateAccountStatus(userId: String, accountStatus: String)

    @Query("UPDATE users SET accountStatus = :accountStatus, verificationStatus = :verificationStatus, activationDate = :activationDate, expiryDate = :expiryDate, validityYears = :validityYears, lastPaymentId = :lastPaymentId WHERE id = :userId")
    suspend fun updateAccountLifecycle(
        userId: String,
        accountStatus: String,
        verificationStatus: String,
        activationDate: Long,
        expiryDate: Long,
        validityYears: Int,
        lastPaymentId: String
    )

    @Query("UPDATE users SET passwordHash = :passwordHash WHERE id = :userId")
    suspend fun updatePasswordHash(userId: String, passwordHash: String)

    @Query("UPDATE users SET rating = :rating, reviewCount = :reviewCount WHERE id = :userId")
    suspend fun updateRatingAndReviewCount(userId: String, rating: Double, reviewCount: Int)

    @Query("UPDATE users SET adsDisabled = :adsDisabled WHERE id = :userId")
    suspend fun updateAdsDisabled(userId: String, adsDisabled: Boolean)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countCategories(): Int

    @Upsert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY districtBn ASC, upazilaBn ASC")
    fun observeLocations(): Flow<List<LocationEntity>>

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun countLocations(): Int

    @Upsert
    suspend fun insertAll(locations: List<LocationEntity>)

    @Upsert
    suspend fun upsertLocation(location: LocationEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY createdAt DESC")
    fun observeAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getAllProductsOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): ProductEntity?

    @Query("DELETE FROM products WHERE isDemoRecord = 1")
    suspend fun deleteDemoProducts()

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()

    @Query("DELETE FROM products WHERE id NOT IN (:ids)")
    suspend fun deleteProductsNotIn(ids: List<String>)

    @Upsert
    suspend fun insertAll(products: List<ProductEntity>)

    @Upsert
    suspend fun upsertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET availableQuantity = availableQuantity - :qty, status = CASE WHEN availableQuantity - :qty <= 0 THEN 'OUT_OF_STOCK' ELSE status END, updatedAt = :now WHERE id = :productId AND availableQuantity >= :qty")
    suspend fun reserveStock(productId: String, qty: Double, now: Long): Int

    @Query("UPDATE products SET availableQuantity = availableQuantity + :qty, status = 'ACTIVE', updatedAt = :now WHERE id = :productId")
    suspend fun restoreStock(productId: String, qty: Double, now: Long)

    @Query("UPDATE products SET pricePerUnit = :price, availableQuantity = :stock, status = CASE WHEN status IN ('PENDING_APPROVAL', 'REJECTED') THEN status WHEN :stock > 0 THEN 'ACTIVE' ELSE 'OUT_OF_STOCK' END, updatedAt = :now WHERE id = :productId AND farmerId = :farmerId")
    suspend fun updateFarmerPriceAndStock(productId: String, farmerId: String, price: Double, stock: Double, now: Long): Int

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProductById(productId: String)

    @Query("SELECT * FROM products WHERE farmerId = :farmerId")
    suspend fun getProductsForFarmer(farmerId: String): List<ProductEntity>

    @Query("UPDATE products SET farmerVerified = :verified WHERE farmerId = :farmerId")
    suspend fun updateFarmerVerifiedOnProducts(farmerId: String, verified: Boolean)

    @Query("UPDATE products SET farmerName = :farmerName, division = :division, district = :district, upazila = :upazila, unionName = :unionName, village = :village, updatedAt = :now WHERE farmerId = :farmerId")
    suspend fun updateFarmerMetadataOnProducts(
        farmerId: String,
        farmerName: String,
        division: String,
        district: String,
        upazila: String,
        unionName: String,
        village: String,
        now: Long
    )
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items")
    fun observeCartItems(): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items")
    suspend fun getCartItemsOnce(): List<CartItemEntity>

    @Query("SELECT * FROM cart_items WHERE productId = :productId LIMIT 1")
    suspend fun getCartItemByProductId(productId: String): CartItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCartItem(item: CartItemEntity)

    @Query("UPDATE cart_items SET quantity = :qty WHERE id = :id")
    suspend fun updateQuantity(id: String, qty: Double)

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun deleteCartItem(id: String)

    @Query("DELETE FROM cart_items WHERE productId = :productId")
    suspend fun deleteCartItemsForProduct(productId: String)

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    fun observeOrderItems(orderId: String): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItemsOnce(orderId: String): List<OrderItemEntity>

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: String): OrderEntity?

    @Query("DELETE FROM orders WHERE isDemoRecord = 1")
    suspend fun deleteDemoOrders()

    @Query("DELETE FROM order_items")
    suspend fun clearAllOrderItems()

    @Query("DELETE FROM orders")
    suspend fun clearAllOrders()

    @Query("DELETE FROM order_items WHERE orderId NOT IN (:orderIds)")
    suspend fun deleteOrderItemsForMissingOrders(orderIds: List<String>)

    @Query("DELETE FROM orders WHERE id NOT IN (:ids)")
    suspend fun deleteOrdersNotIn(ids: List<String>)

    @Upsert
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Upsert
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("UPDATE orders SET status = :status, updatedAt = :now WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String, now: Long)

    @Query("UPDATE orders SET paymentStatus = :paymentStatus, transactionReference = :ref, updatedAt = :now WHERE id = :orderId")
    suspend fun updatePaymentDetails(orderId: String, paymentStatus: String, ref: String, now: Long)

    @Query("UPDATE orders SET paymentMethod = :paymentMethod, paymentSenderNumber = :senderNumber, transactionReference = :txId, paymentReceiptUri = :receiptUri, paymentStatus = :paymentStatus, updatedAt = :now WHERE id = :orderId")
    suspend fun updateBuyerPaymentSubmission(
        orderId: String,
        paymentMethod: String,
        senderNumber: String,
        txId: String,
        receiptUri: String,
        paymentStatus: String,
        now: Long
    )

    @Query("UPDATE orders SET farmerPaymentConfirmed = :confirmed, farmerPaymentConfirmedAt = :confirmedAt, updatedAt = :now WHERE id = :orderId")
    suspend fun updateFarmerPaymentConfirmed(
        orderId: String,
        confirmed: Boolean,
        confirmedAt: Long,
        now: Long
    )

    @Query("UPDATE orders SET adminPaymentVerified = :verified, adminPaymentVerifiedBy = :verifiedBy, adminPaymentVerifiedAt = :verifiedAt, paymentStatus = :paymentStatus, updatedAt = :now WHERE id = :orderId")
    suspend fun updateAdminPaymentVerified(
        orderId: String,
        verified: Boolean,
        verifiedBy: String,
        verifiedAt: Long,
        paymentStatus: String,
        now: Long
    )

    @Query("UPDATE orders SET verifiedAdvanceTotal = :verifiedAdvanceTotal, remainingDue = :remainingDue, farmerPaymentConfirmed = :farmerConfirmed, farmerPaymentConfirmedAt = :farmerConfirmedAt, paymentStatus = :paymentStatus, status = :status, updatedAt = :now WHERE id = :orderId")
    suspend fun updateOrderAdvanceAndStatus(
        orderId: String,
        verifiedAdvanceTotal: Double,
        remainingDue: Double,
        farmerConfirmed: Boolean,
        farmerConfirmedAt: Long,
        paymentStatus: String,
        status: String,
        now: Long
    )
}

@Dao
interface ReviewComplaintDao {
    @Query("SELECT * FROM reviews ORDER BY createdAt DESC")
    fun observeReviews(): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE farmerId = :farmerId")
    suspend fun getReviewsForFarmer(farmerId: String): List<ReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<ReviewEntity>)

    @Query("SELECT * FROM complaints ORDER BY createdAt DESC")
    fun observeComplaints(): Flow<List<ComplaintEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaint(complaint: ComplaintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaints(complaints: List<ComplaintEntity>)

    @Query("UPDATE complaints SET status = :status WHERE id = :id")
    suspend fun updateComplaintStatus(id: String, status: String)

    @Query("DELETE FROM reviews")
    suspend fun clearAllReviews()

    @Query("DELETE FROM reviews WHERE id NOT IN (:ids)")
    suspend fun deleteReviewsNotIn(ids: List<String>)

    @Query("DELETE FROM complaints")
    suspend fun clearAllComplaints()

    @Query("DELETE FROM complaints WHERE id NOT IN (:ids)")
    suspend fun deleteComplaintsNotIn(ids: List<String>)
}

@Dao
interface NotificationAuditDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: String): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE isRead = 0")
    suspend fun getUnreadNotifications(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isRead = 0 AND (userId = :userId OR userId = 'ALL' OR targetRole = :role)")
    suspend fun getUnreadNotificationsForUser(userId: String, role: String): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId OR userId = 'ALL' OR targetRole = :role")
    suspend fun markNotificationsReadForUser(userId: String, role: String)

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(logs: List<AuditLogEntity>)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()

    @Query("DELETE FROM notifications WHERE id NOT IN (:ids)")
    suspend fun deleteNotificationsNotIn(ids: List<String>)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllAuditLogs()

    @Query("DELETE FROM audit_logs WHERE id NOT IN (:ids)")
    suspend fun deleteAuditLogsNotIn(ids: List<String>)
}

@Dao
interface ActivationPaymentDao {
    @Query("SELECT * FROM activation_payments ORDER BY createdAt DESC")
    fun observeAllPayments(): Flow<List<ActivationPaymentEntity>>

    @Query("SELECT * FROM activation_payments WHERE userId = :userId ORDER BY createdAt DESC")
    fun observePaymentsForUser(userId: String): Flow<List<ActivationPaymentEntity>>

    @Query("SELECT * FROM activation_payments ORDER BY createdAt DESC")
    suspend fun getAllPaymentsOnce(): List<ActivationPaymentEntity>

    @Query("SELECT * FROM activation_payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: String): ActivationPaymentEntity?

    @Query("SELECT * FROM activation_payments WHERE userId = :userId AND status = 'PENDING' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getPendingPaymentForUser(userId: String): ActivationPaymentEntity?

    @Upsert
    suspend fun upsertPayment(payment: ActivationPaymentEntity)

    @Upsert
    suspend fun insertAll(payments: List<ActivationPaymentEntity>)

    @Query("DELETE FROM activation_payments")
    suspend fun clearAllPayments()

    @Query("DELETE FROM activation_payments WHERE id NOT IN (:ids)")
    suspend fun deletePaymentsNotIn(ids: List<String>)
}

@Dao
interface PaymentSettingsDao {
    @Query("SELECT * FROM payment_settings WHERE id = 'default' LIMIT 1")
    fun observeSettings(): Flow<PaymentSettingsEntity?>

    @Query("SELECT * FROM payment_settings WHERE id = 'default' LIMIT 1")
    suspend fun getSettingsOnce(): PaymentSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: PaymentSettingsEntity)

    @Query("DELETE FROM payment_settings")
    suspend fun clearSettings()
}

@Dao
interface FarmerPaymentSettingsDao {
    @Query("SELECT * FROM farmer_payment_settings")
    fun observeAll(): Flow<List<FarmerPaymentSettingsEntity>>

    @Query("SELECT * FROM farmer_payment_settings WHERE farmerId = :farmerId LIMIT 1")
    fun observeForFarmer(farmerId: String): Flow<FarmerPaymentSettingsEntity?>

    @Query("SELECT * FROM farmer_payment_settings WHERE farmerId = :farmerId LIMIT 1")
    suspend fun getForFarmerOnce(farmerId: String): FarmerPaymentSettingsEntity?

    @Upsert
    suspend fun upsert(settings: FarmerPaymentSettingsEntity)

    @Upsert
    suspend fun insertAll(list: List<FarmerPaymentSettingsEntity>)

    @Query("DELETE FROM farmer_payment_settings WHERE farmerId NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM farmer_payment_settings")
    suspend fun clearAll()
}

@Dao
interface OrderPaymentDao {
    @Query("SELECT * FROM order_payments ORDER BY createdAt DESC")
    fun observeAllPayments(): Flow<List<OrderPaymentEntity>>

    @Query("SELECT * FROM order_payments WHERE orderId = :orderId ORDER BY createdAt DESC")
    fun observePaymentsForOrder(orderId: String): Flow<List<OrderPaymentEntity>>

    @Query("SELECT * FROM order_payments WHERE orderId = :orderId ORDER BY createdAt DESC")
    suspend fun getPaymentsForOrderOnce(orderId: String): List<OrderPaymentEntity>

    @Query("SELECT * FROM order_payments")
    suspend fun getAllPaymentsOnce(): List<OrderPaymentEntity>

    @Query("SELECT * FROM order_payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: String): OrderPaymentEntity?

    @Query("SELECT * FROM order_payments WHERE transactionIdNormalized = :txNorm LIMIT 1")
    suspend fun findByNormalizedTxId(txNorm: String): OrderPaymentEntity?

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM order_payments WHERE orderId = :orderId AND status = 'CONFIRMED'")
    suspend fun getConfirmedTotalForOrder(orderId: String): Double

    @Upsert
    suspend fun upsertPayment(payment: OrderPaymentEntity)

    @Upsert
    suspend fun insertAll(payments: List<OrderPaymentEntity>)

    @Query("DELETE FROM order_payments WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM order_payments")
    suspend fun clearAll()
}

@Dao
interface HeroSliderDao {
    @Query("SELECT * FROM hero_sliders ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeAllSliders(): Flow<List<HeroSliderEntity>>

    @Query("SELECT * FROM hero_sliders ORDER BY sortOrder ASC, updatedAt DESC")
    suspend fun getAllSlidersOnce(): List<HeroSliderEntity>

    @Query("SELECT * FROM hero_sliders WHERE id = :id LIMIT 1")
    suspend fun getSliderById(id: String): HeroSliderEntity?

    @Upsert
    suspend fun upsertSlider(slider: HeroSliderEntity)

    @Upsert
    suspend fun insertAll(sliders: List<HeroSliderEntity>)

    @Query("DELETE FROM hero_sliders WHERE id = :id")
    suspend fun deleteSliderById(id: String)

    @Query("DELETE FROM hero_sliders WHERE id NOT IN (:ids)")
    suspend fun deleteSlidersNotIn(ids: List<String>)

    @Query("DELETE FROM hero_sliders")
    suspend fun clearAllSliders()
}

