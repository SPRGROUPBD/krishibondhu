package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.FirebaseRealtimeSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MarketplaceRepository(
    private val db: KrishiBondhuDatabase,
    private val prefs: UserPreferences,
    private val rtdbSync: FirebaseRealtimeSyncManager = FirebaseRealtimeSyncManager()
) {
    companion object {
        const val SUPERADMIN_LOGIN_ID = "admin"
        const val SUPERADMIN_PASSWORD = "bondhu"
        const val SUPERADMIN_USER_ID = "admin_super"
    }

    val sessionPrefs: Flow<UserSessionPrefs> = prefs.sessionPrefs
    val allUsers: Flow<List<UserEntity>> = db.userDao().observeAllUsers()
    val farmers: Flow<List<UserEntity>> = db.userDao().observeFarmers()
    val buyers: Flow<List<UserEntity>> = db.userDao().observeBuyers()
    val categories: Flow<List<CategoryEntity>> = db.categoryDao().observeCategories()
    val locations: Flow<List<LocationEntity>> = db.locationDao().observeLocations()
    val products: Flow<List<ProductEntity>> = db.productDao().observeAllProducts()
    val cartItems: Flow<List<CartItemEntity>> = db.cartDao().observeCartItems()
    val orders: Flow<List<OrderEntity>> = db.orderDao().observeAllOrders()
    val reviews: Flow<List<ReviewEntity>> = db.reviewComplaintDao().observeReviews()
    val complaints: Flow<List<ComplaintEntity>> = db.reviewComplaintDao().observeComplaints()
    val notifications: Flow<List<NotificationEntity>> = db.notificationAuditDao().observeNotifications()
    val auditLogs: Flow<List<AuditLogEntity>> = db.notificationAuditDao().observeAuditLogs()

    fun observeOrderItems(orderId: String): Flow<List<OrderItemEntity>> =
        db.orderDao().observeOrderItems(orderId)

    suspend fun initializeCatalogsAndSync(
        scope: CoroutineScope? = null,
        onIncomingNotification: (NotificationEntity) -> Unit = {}
    ) {
        MasterCatalogSeeder.initialize(db)
        if (scope != null) {
            rtdbSync.startRealtimeListeners(db, scope, onIncomingNotification)
        }
    }

    suspend fun setLanguage(language: AppLanguage) = prefs.setLanguage(language)
    suspend fun setDarkMode(enabled: Boolean) = prefs.setDarkMode(enabled)

    suspend fun logoutUser() {
        prefs.setActiveUserId("")
    }

    /**
     * Authenticates users:
     * 1. Superadmin (`id = admin`, `password = bondhu`) works offline/locally even if not in Firebase.
     * 2. Standard Farmers and Buyers check Room first, then Firebase Realtime Database.
     */
    suspend fun loginUser(identifier: String, rawPassword: String): Result<UserEntity> {
        val cleanId = identifier.trim()
        if (cleanId.equals(SUPERADMIN_LOGIN_ID, ignoreCase = true)) {
            if (rawPassword != SUPERADMIN_PASSWORD) {
                return Result.failure(IllegalArgumentException("সুপার অ্যাডমিন পাসওয়ার্ড সঠিক নয়।"))
            }
            val adminUser = UserEntity(
                id = SUPERADMIN_USER_ID,
                fullName = "সুপার অ্যাডমিন",
                phone = SUPERADMIN_LOGIN_ID,
                passwordHash = SecurityHashUtil.hashPassword(SUPERADMIN_PASSWORD),
                role = UserRole.ADMIN.name,
                division = "ঢাকা",
                district = "ঢাকা",
                upazila = "সাভার",
                unionName = "কেন্দ্রীয়",
                village = "সারা বাংলাদেশ তদারকি",
                farmBio = "প্ল্যাটফর্ম তদারকি ও যাচাইকরণ অ্যাডমিন",
                nidMasked = "SUPERADMIN",
                verificationStatus = "VERIFIED",
                isMobileVerified = true,
                rating = 5.0,
                reviewCount = 0,
                isDemoRecord = false
            )
            db.userDao().upsertUser(adminUser)
            prefs.setActiveUserId(adminUser.id)
            return Result.success(adminUser)
        }

        val expectedHash = SecurityHashUtil.hashPassword(rawPassword)
        var user = db.userDao().getUserByPhone(cleanId)
        if (user == null) {
            val remoteUser = rtdbSync.fetchUserByPhone(cleanId)
            if (remoteUser != null) {
                db.userDao().upsertUser(remoteUser)
                user = remoteUser
            }
        }
        if (user == null) {
            return Result.failure(IllegalArgumentException("এই মোবাইল নম্বরে কোনো অ্যাকাউন্ট পাওয়া যায়নি।"))
        }
        if (user.passwordHash != expectedHash) {
            return Result.failure(IllegalArgumentException("পাসওয়ার্ড সঠিক নয়।"))
        }
        prefs.setActiveUserId(user.id)
        return Result.success(user)
    }

    suspend fun registerUser(
        fullName: String,
        phone: String,
        rawPassword: String,
        role: UserRole,
        division: String = "",
        district: String,
        upazila: String,
        unionName: String,
        village: String,
        nidLastFour: String,
        farmBio: String
    ): Result<UserEntity> {
        val cleanPhone = phone.trim()
        if (fullName.isBlank() || cleanPhone.length < 10 || rawPassword.length < 6) {
            return Result.failure(IllegalArgumentException("নাম, সঠিক মোবাইল নম্বর ও কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন।"))
        }
        if (cleanPhone.equals(SUPERADMIN_LOGIN_ID, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("এই আইডি ব্যবহার করা যাবে না।"))
        }
        if (role == UserRole.ADMIN) {
            return Result.failure(IllegalArgumentException("অ্যাডমিন হিসেবে সরাসরি নিবন্ধন করা যাবে না।"))
        }
        if (db.userDao().getUserByPhone(cleanPhone) != null || rtdbSync.fetchUserByPhone(cleanPhone) != null) {
            return Result.failure(IllegalArgumentException("এই মোবাইল নম্বরটি ইতোমধ্যে নিবন্ধিত।"))
        }

        val resolvedDistrict = district.ifBlank { "ঢাকা" }
        val resolvedDivision = division.ifBlank { BangladeshLocations.divisionForDistrict(resolvedDistrict) }
        val resolvedUpazila = upazila.ifBlank {
            BangladeshLocations.upazilasForDistrict(resolvedDistrict).firstOrNull() ?: resolvedDistrict
        }
        val uid = "${role.name.lowercase()}_${UUID.randomUUID().toString().take(8)}"
        val maskedNid = if (nidLastFour.isNotBlank()) "NID-****-${nidLastFour.takeLast(4)}" else ""

        val user = UserEntity(
            id = uid,
            fullName = fullName.trim(),
            phone = cleanPhone,
            passwordHash = SecurityHashUtil.hashPassword(rawPassword),
            role = role.name,
            division = resolvedDivision,
            district = resolvedDistrict,
            upazila = resolvedUpazila,
            unionName = unionName.trim(),
            village = village.trim(),
            farmBio = farmBio.trim(),
            nidMasked = maskedNid,
            verificationStatus = if (role == UserRole.FARMER) "PENDING" else "VERIFIED",
            isMobileVerified = true,
            rating = 5.0,
            reviewCount = 0,
            isDemoRecord = false
        )
        db.userDao().upsertUser(user)
        rtdbSync.pushUser(user)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = uid,
            actorRole = role.name,
            actionBn = "নতুন অ্যাকাউন্ট নিবন্ধন",
            targetEntity = "User",
            targetId = uid,
            detailsBn = "${user.fullName} (${role.titleBn}) — ${user.upazila}, ${user.district}, ${user.division}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        prefs.setActiveUserId(uid)
        return Result.success(user)
    }

    suspend fun addToCart(product: ProductEntity, requestedQty: Double): Result<Unit> {
        if (requestedQty <= 0.0) {
            return Result.failure(IllegalArgumentException("সঠিক পরিমাণ নির্বাচন করুন।"))
        }
        if (product.status != "ACTIVE" || product.availableQuantity <= 0.0) {
            return Result.failure(IllegalArgumentException("দুঃখিত, এই ফসলটির মজুত শেষ।"))
        }
        val existing = db.cartDao().getCartItemByProductId(product.id)
        val totalQty = (existing?.quantity ?: 0.0) + requestedQty
        if (totalQty > product.availableQuantity) {
            return Result.failure(
                IllegalArgumentException("সর্বোচ্চ মজুত ${product.availableQuantity.formatQty()} ${product.unit} পর্যন্ত যোগ করা যাবে।")
            )
        }
        val item = CartItemEntity(
            id = existing?.id ?: "cart_${UUID.randomUUID().toString().take(8)}",
            productId = product.id,
            farmerId = product.farmerId,
            farmerName = product.farmerName,
            productNameBn = product.nameBn,
            unit = product.unit,
            pricePerUnit = product.pricePerUnit,
            quantity = totalQty,
            maxAvailableQuantity = product.availableQuantity,
            supportsFarmPickup = product.supportsFarmPickup,
            supportsDirectDelivery = product.supportsDirectDelivery,
            deliveryChargeEstimate = product.deliveryChargeEstimate
        )
        db.cartDao().upsertCartItem(item)
        return Result.success(Unit)
    }

    suspend fun updateCartQuantity(cartItemId: String, newQty: Double, maxAvailable: Double): Result<Unit> {
        if (newQty <= 0.0) {
            db.cartDao().deleteCartItem(cartItemId)
            return Result.success(Unit)
        }
        if (newQty > maxAvailable) {
            return Result.failure(IllegalArgumentException("পর্যাপ্ত মজুত নেই (সর্বোচ্চ ${maxAvailable.formatQty()})"))
        }
        db.cartDao().updateQuantity(cartItemId, newQty)
        return Result.success(Unit)
    }

    suspend fun removeFromCart(cartItemId: String) = db.cartDao().deleteCartItem(cartItemId)

    suspend fun placeOrdersFromCart(
        buyer: UserEntity,
        deliveryOption: DeliveryOption,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        transactionRef: String
    ): Result<List<OrderEntity>> {
        val items = db.cartDao().getCartItemsOnce()
        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("আপনার কার্ট খালি।"))
        }
        if (deliveryOption == DeliveryOption.DIRECT_DELIVERY && deliveryAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("ডেলিভারির ঠিকানা লিখুন।"))
        }
        if (paymentMethod.isDigital && transactionRef.isBlank()) {
            return Result.failure(
                IllegalArgumentException("${paymentMethod.labelBn} পেমেন্টের জন্য ট্রানজেকশন রেফারেন্স দিন।")
            )
        }

        val now = System.currentTimeMillis()
        val createdBundles = mutableListOf<Triple<OrderEntity, List<OrderItemEntity>, NotificationEntity>>()
        val updatedProducts = mutableListOf<ProductEntity>()

        return runCatching {
            db.withTransaction {
                val groupedByFarmer = items.groupBy { it.farmerId }
                groupedByFarmer.forEach { (farmerId, farmerItems) ->
                    val farmer = db.userDao().getUserById(farmerId)
                    var productTotal = 0.0
                    var maxDeliveryFee = 0.0
                    val orderId = "ord_${UUID.randomUUID().toString().take(8)}"
                    val orderCode = "KB-${(1000..9999).random()}-${(10..99).random()}"
                    val orderLineEntities = mutableListOf<OrderItemEntity>()

                    for (cartItem in farmerItems) {
                        val updatedRows = db.productDao().reserveStock(cartItem.productId, cartItem.quantity, now)
                        if (updatedRows == 0) {
                            throw IllegalStateException("${cartItem.productNameBn}-এর পর্যাপ্ত মজুত নেই।")
                        }
                        db.productDao().getProductById(cartItem.productId)?.let { updatedProducts.add(it) }
                        val lineTotal = cartItem.pricePerUnit * cartItem.quantity
                        productTotal += lineTotal
                        if (cartItem.deliveryChargeEstimate > maxDeliveryFee) {
                            maxDeliveryFee = cartItem.deliveryChargeEstimate
                        }
                        orderLineEntities.add(
                            OrderItemEntity(
                                id = "oi_${UUID.randomUUID().toString().take(8)}",
                                orderId = orderId,
                                productId = cartItem.productId,
                                productNameBn = cartItem.productNameBn,
                                unit = cartItem.unit,
                                quantity = cartItem.quantity,
                                pricePerUnit = cartItem.pricePerUnit,
                                lineTotal = lineTotal
                            )
                        )
                    }

                    val deliveryCharge = if (deliveryOption == DeliveryOption.FARM_PICKUP) 0.0 else maxDeliveryFee
                    val grandTotal = productTotal + deliveryCharge
                    val summary = farmerItems.joinToString(", ") {
                        "${it.productNameBn} × ${it.quantity.formatQty()} ${it.unit}"
                    }
                    val paymentStatus = if (paymentMethod.isDigital) "AWAITING_GATEWAY" else "PENDING"

                    val order = OrderEntity(
                        id = orderId,
                        orderCode = orderCode,
                        buyerId = buyer.id,
                        buyerName = buyer.fullName,
                        buyerPhone = buyer.phone,
                        farmerId = farmerId,
                        farmerName = farmer?.fullName ?: farmerItems.first().farmerName,
                        farmerPhone = farmer?.phone ?: "",
                        status = OrderStatus.PENDING.name,
                        deliveryOption = deliveryOption.name,
                        deliveryAddress = if (deliveryOption == DeliveryOption.FARM_PICKUP) {
                            "সরাসরি কৃষকের খামার থেকে সংগ্রহ"
                        } else {
                            deliveryAddress.trim()
                        },
                        division = farmer?.division ?: buyer.division,
                        district = farmer?.district ?: buyer.district,
                        upazila = farmer?.upazila ?: buyer.upazila,
                        productTotal = productTotal,
                        deliveryCharge = deliveryCharge,
                        grandTotal = grandTotal,
                        paymentMethod = paymentMethod.name,
                        transactionReference = transactionRef.trim(),
                        paymentStatus = paymentStatus,
                        itemsSummary = summary,
                        createdAt = now,
                        updatedAt = now,
                        isDemoRecord = false
                    )

                    val farmerNotification = NotificationEntity(
                        id = "not_${UUID.randomUUID().toString().take(8)}",
                        userId = farmerId,
                        targetRole = UserRole.FARMER.name,
                        senderRole = UserRole.BUYER.name,
                        orderId = orderId,
                        type = "ORDER_NEW",
                        titleBn = "নতুন অর্ডার এসেছে ($orderCode)",
                        messageBn = "ক্রেতা ${buyer.fullName} (${buyer.upazila}, ${buyer.district}) $summary অর্ডার করেছেন — মোট ${grandTotal.formatBdt()}।",
                        isRead = false,
                        createdAt = now
                    )

                    db.orderDao().insertOrders(listOf(order))
                    db.orderDao().insertOrderItems(orderLineEntities)
                    db.notificationAuditDao().insertNotification(farmerNotification)
                    createdBundles.add(Triple(order, orderLineEntities, farmerNotification))
                }
                db.cartDao().clearCart()
            }

            updatedProducts.forEach { rtdbSync.pushProduct(it) }
            createdBundles.forEach { (order, lineItems, notif) ->
                rtdbSync.pushOrder(order, lineItems)
                rtdbSync.pushNotification(notif)
            }
            createdBundles.map { it.first }
        }
    }

    suspend fun createFarmerProduct(
        farmer: UserEntity,
        category: CategoryEntity,
        nameBn: String,
        descriptionBn: String,
        imageUri: String,
        quantity: Double,
        unit: String,
        pricePerUnit: Double,
        harvestDate: String,
        availabilityDate: String,
        pickupAddress: String,
        supportsFarmPickup: Boolean,
        supportsDirectDelivery: Boolean,
        deliveryCharge: Double,
        isWholesale: Boolean
    ): Result<ProductEntity> {
        if (farmer.role != UserRole.FARMER.name) {
            return Result.failure(IllegalStateException("শুধুমাত্র কৃষক নিজস্ব ফসল তালিকাভুক্ত করতে পারেন।"))
        }
        if (nameBn.isBlank() || quantity <= 0.0 || pricePerUnit <= 0.0 || deliveryCharge < 0.0) {
            return Result.failure(IllegalArgumentException("ফসলের নাম, সঠিক পরিমাণ ও দাম দিন।"))
        }
        val now = System.currentTimeMillis()
        val product = ProductEntity(
            id = "prod_${UUID.randomUUID().toString().take(8)}",
            farmerId = farmer.id,
            farmerName = farmer.fullName,
            farmerVerified = farmer.verificationStatus == "VERIFIED",
            categoryId = category.id,
            categoryNameBn = category.nameBn,
            nameBn = nameBn.trim(),
            nameEn = nameBn.trim(),
            descriptionBn = descriptionBn.trim(),
            imageUri = imageUri,
            availableQuantity = quantity,
            unit = unit,
            pricePerUnit = pricePerUnit,
            harvestDate = harvestDate.ifBlank { "আজই সংগ্রহ" },
            availabilityDate = availabilityDate.ifBlank { "আজই পাওয়া যাবে" },
            division = farmer.division,
            district = farmer.district,
            upazila = farmer.upazila,
            unionName = farmer.unionName,
            village = farmer.village,
            pickupAddress = pickupAddress.ifBlank {
                listOf(farmer.village, farmer.upazila, farmer.district).filter { it.isNotBlank() }.joinToString(", ")
            },
            supportsFarmPickup = supportsFarmPickup,
            supportsDirectDelivery = supportsDirectDelivery,
            deliveryChargeEstimate = if (supportsDirectDelivery) deliveryCharge else 0.0,
            isWholesale = isWholesale,
            isPopular = false,
            status = "ACTIVE",
            createdAt = now,
            updatedAt = now,
            isDemoRecord = false
        )
        db.productDao().upsertProduct(product)
        rtdbSync.pushProduct(product)
        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = farmer.id,
            actorRole = farmer.role,
            actionBn = "নতুন ফসল যুক্ত",
            targetEntity = "Product",
            targetId = product.id,
            detailsBn = "${product.nameBn} (${product.district}) — ${product.pricePerUnit.formatBdt()}/${product.unit}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(product)
    }

    suspend fun updateFarmerPriceAndStock(
        productId: String,
        farmerId: String,
        newPrice: Double,
        newStock: Double
    ): Result<Unit> {
        if (newPrice <= 0.0 || newStock < 0.0) {
            return Result.failure(IllegalArgumentException("সঠিক মূল্য ও মজুতের পরিমাণ দিন।"))
        }
        val updated = db.productDao().updateFarmerPriceAndStock(
            productId = productId,
            farmerId = farmerId,
            price = newPrice,
            stock = newStock,
            now = System.currentTimeMillis()
        )
        if (updated == 0) {
            return Result.failure(IllegalAccessException("অন্য কৃষকের পণ্যের দাম পরিবর্তন করা অনুমোদিত নয়।"))
        }
        db.productDao().getProductById(productId)?.let { rtdbSync.pushProduct(it) }
        return Result.success(Unit)
    }

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        actor: UserEntity
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        val order = db.orderDao().getOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("অর্ডার পাওয়া যায়নি।"))

        val isFarmer = actor.id == order.farmerId
        val isBuyer = actor.id == order.buyerId
        val isAdmin = actor.role == UserRole.ADMIN.name

        if (!isFarmer && !isBuyer && !isAdmin) {
            return Result.failure(IllegalAccessException("এই অর্ডার পরিবর্তনের অনুমতি নেই।"))
        }
        if (isBuyer && !isAdmin && newStatus !in setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
            return Result.failure(IllegalAccessException("ক্রেতা শুধুমাত্র অর্ডার বাতিল বা সম্পন্ন করতে পারেন।"))
        }
        if (isBuyer && newStatus == OrderStatus.CANCELLED && order.status != OrderStatus.PENDING.name) {
            return Result.failure(IllegalStateException("অর্ডার প্রক্রিয়াধীন হলে বাতিল করা যাবে না।"))
        }

        val wasActive = order.status != OrderStatus.CANCELLED.name && order.status != OrderStatus.REJECTED.name
        val restoringStock = wasActive && (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.REJECTED)
        val restoredProducts = mutableListOf<ProductEntity>()

        db.withTransaction {
            db.orderDao().updateOrderStatus(orderId, newStatus.name, now)
            if (newStatus == OrderStatus.COMPLETED && order.paymentStatus == "PENDING") {
                db.orderDao().updatePaymentDetails(orderId, "PAID", order.transactionReference, now)
            }
            if (restoringStock) {
                val orderItems = db.orderDao().getOrderItemsOnce(orderId)
                orderItems.forEach { item ->
                    db.productDao().restoreStock(item.productId, item.quantity, now)
                    db.productDao().getProductById(item.productId)?.let { restoredProducts.add(it) }
                }
            }
        }

        val updatedOrder = db.orderDao().getOrderById(orderId)
        val lineItems = db.orderDao().getOrderItemsOnce(orderId)
        if (updatedOrder != null) {
            rtdbSync.pushOrder(updatedOrder, lineItems)
        }
        restoredProducts.forEach { rtdbSync.pushProduct(it) }

        val targetUserId = if (isFarmer) order.buyerId else order.farmerId
        val targetRole = if (isFarmer) UserRole.BUYER.name else UserRole.FARMER.name
        val notif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = targetUserId,
            targetRole = targetRole,
            senderRole = actor.role,
            orderId = orderId,
            type = "ORDER_STATUS",
            titleBn = "অর্ডার আপডেট (${order.orderCode})",
            messageBn = "অর্ডারের বর্তমান অবস্থা: ${newStatus.display(AppLanguage.BENGALI)}",
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(notif)
        rtdbSync.pushNotification(notif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = actor.id,
            actorRole = actor.role,
            actionBn = "অর্ডার স্ট্যাটাস পরিবর্তন",
            targetEntity = "Order",
            targetId = orderId,
            detailsBn = "${order.orderCode} -> ${newStatus.name}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(Unit)
    }

    suspend fun submitReview(
        order: OrderEntity,
        productId: String,
        buyer: UserEntity,
        rating: Int,
        commentBn: String
    ) {
        val review = ReviewEntity(
            id = "rev_${UUID.randomUUID().toString().take(8)}",
            orderId = order.id,
            productId = productId,
            farmerId = order.farmerId,
            buyerId = buyer.id,
            buyerName = buyer.fullName,
            rating = rating.coerceIn(1, 5),
            commentBn = commentBn.trim()
        )
        db.reviewComplaintDao().insertReview(review)
        rtdbSync.pushReview(review)

        // Recalculate farmer's average rating & review count
        val farmerReviews = db.reviewComplaintDao().getReviewsForFarmer(order.farmerId)
        if (farmerReviews.isNotEmpty()) {
            val avg = kotlin.math.round((farmerReviews.sumOf { it.rating }.toDouble() / farmerReviews.size) * 10.0) / 10.0
            db.userDao().updateRatingAndReviewCount(order.farmerId, avg, farmerReviews.size)
            db.userDao().getUserById(order.farmerId)?.let { rtdbSync.pushUser(it) }
        }
    }

    suspend fun submitComplaint(
        orderId: String,
        reporter: UserEntity,
        subjectBn: String,
        descriptionBn: String
    ) {
        val complaint = ComplaintEntity(
            id = "cmp_${UUID.randomUUID().toString().take(8)}",
            orderId = orderId,
            reporterId = reporter.id,
            reporterName = reporter.fullName,
            subjectBn = subjectBn.trim(),
            descriptionBn = descriptionBn.trim()
        )
        db.reviewComplaintDao().insertComplaint(complaint)
        rtdbSync.pushComplaint(complaint)
    }

    suspend fun adminVerifyFarmer(admin: UserEntity, farmerId: String, approved: Boolean) {
        if (admin.role != UserRole.ADMIN.name) return
        val status = if (approved) "VERIFIED" else "REJECTED"
        db.userDao().updateVerificationStatus(farmerId, status)
        db.productDao().updateFarmerVerifiedOnProducts(farmerId, approved)
        db.userDao().getUserById(farmerId)?.let { rtdbSync.pushUser(it) }

        val notif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = farmerId,
            targetRole = UserRole.FARMER.name,
            senderRole = UserRole.ADMIN.name,
            type = "VERIFICATION",
            titleBn = if (approved) "কৃষক পরিচয় যাচাই সম্পন্ন" else "পরিচয় যাচাই প্রত্যাখ্যাত",
            messageBn = if (approved) "আপনার প্রোফাইল যাচাইকৃত ব্যাজ পেয়েছে।" else "অনুগ্রহ করে সঠিক তথ্য দিয়ে পুনরায় আবেদন করুন।",
            isRead = false
        )
        db.notificationAuditDao().insertNotification(notif)
        rtdbSync.pushNotification(notif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "কৃষক যাচাইকরণ ($status)",
            targetEntity = "User",
            targetId = farmerId,
            detailsBn = "অ্যাডমিন দ্বারা কৃষকের পরিচয় $status করা হয়েছে"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
    }

    suspend fun adminSendNotification(
        admin: UserEntity,
        targetUserId: String,
        targetRole: String,
        titleBn: String,
        messageBn: String
    ): Result<NotificationEntity> {
        if (admin.role != UserRole.ADMIN.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সুপার অ্যাডমিন নোটিফিকেশন পাঠাতে পারেন।"))
        }
        if (titleBn.isBlank() || messageBn.isBlank()) {
            return Result.failure(IllegalArgumentException("নোটিফিকেশনের শিরোনাম ও বার্তা লিখুন।"))
        }
        val notif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = targetUserId.ifBlank { "ALL" },
            targetRole = targetRole.ifBlank { "ALL" },
            senderRole = UserRole.ADMIN.name,
            type = "ADMIN_BROADCAST",
            titleBn = titleBn.trim(),
            messageBn = messageBn.trim(),
            isRead = false,
            createdAt = System.currentTimeMillis()
        )
        db.notificationAuditDao().insertNotification(notif)
        rtdbSync.pushNotification(notif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "অ্যাডমিন নোটিফিকেশন প্রেরণ",
            targetEntity = "Notification",
            targetId = notif.id,
            detailsBn = "প্রাপক: ${notif.userId} (${notif.targetRole}) — ${notif.titleBn}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(notif)
    }

    suspend fun adminAddCategory(admin: UserEntity, nameBn: String, nameEn: String, emoji: String) {
        if (admin.role != UserRole.ADMIN.name) return
        val id = "cat_${UUID.randomUUID().toString().take(6)}"
        val category = CategoryEntity(
            id = id,
            nameBn = nameBn.trim(),
            nameEn = nameEn.trim().ifBlank { nameBn.trim() },
            groupKey = "OTHER",
            emoji = emoji.ifBlank { "🌱" },
            sortOrder = 99
        )
        db.categoryDao().upsertCategory(category)
        rtdbSync.pushCategory(category)
        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "নতুন ক্যাটাগরি যোগ",
            targetEntity = "Category",
            targetId = id,
            detailsBn = "$emoji $nameBn ($nameEn)"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
    }

    suspend fun adminAddLocation(
        admin: UserEntity,
        divisionBn: String,
        districtBn: String,
        upazilaBn: String,
        unionBn: String,
        villageBn: String
    ) {
        if (admin.role != UserRole.ADMIN.name) return
        val id = "loc_${UUID.randomUUID().toString().take(6)}"
        val location = LocationEntity(
            id = id,
            divisionBn = divisionBn.trim(),
            districtBn = districtBn.trim(),
            upazilaBn = upazilaBn.trim(),
            unionBn = unionBn.trim(),
            villageBn = villageBn.trim()
        )
        db.locationDao().upsertLocation(location)
        rtdbSync.pushLocation(location)
        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "নতুন এলাকা যুক্ত",
            targetEntity = "Location",
            targetId = id,
            detailsBn = "$divisionBn > $districtBn > $upazilaBn > $unionBn"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
    }

    suspend fun adminResolveComplaint(admin: UserEntity, complaintId: String) {
        if (admin.role != UserRole.ADMIN.name) return
        db.reviewComplaintDao().updateComplaintStatus(complaintId, "RESOLVED")
        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "অভিযোগ নিষ্পত্তি",
            targetEntity = "Complaint",
            targetId = complaintId,
            detailsBn = "অভিযোগটি সমাধান হিসেবে চিহ্নিত করা হয়েছে"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
    }

    suspend fun markNotificationsRead(activeUser: UserEntity?) {
        if (activeUser == null) return
        if (activeUser.role == UserRole.ADMIN.name) {
            db.notificationAuditDao().markAllNotificationsRead()
        } else {
            db.notificationAuditDao().markNotificationsReadForUser(activeUser.id, activeUser.role)
        }
    }
}
