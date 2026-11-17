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

    @Query("DELETE FROM users WHERE isDemoRecord = 1")
    suspend fun deleteDemoUsers()

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

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): ProductEntity?

    @Query("DELETE FROM products WHERE isDemoRecord = 1")
    suspend fun deleteDemoProducts()

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

    @Query("UPDATE products SET pricePerUnit = :price, availableQuantity = :stock, status = CASE WHEN :stock > 0 THEN 'ACTIVE' ELSE 'OUT_OF_STOCK' END, updatedAt = :now WHERE id = :productId AND farmerId = :farmerId")
    suspend fun updateFarmerPriceAndStock(productId: String, farmerId: String, price: Double, stock: Double, now: Long): Int

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
}

@Dao
interface PaymentSettingsDao {
    @Query("SELECT * FROM payment_settings WHERE id = 'default' LIMIT 1")
    fun observeSettings(): Flow<PaymentSettingsEntity?>

    @Query("SELECT * FROM payment_settings WHERE id = 'default' LIMIT 1")
    suspend fun getSettingsOnce(): PaymentSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: PaymentSettingsEntity)
}
