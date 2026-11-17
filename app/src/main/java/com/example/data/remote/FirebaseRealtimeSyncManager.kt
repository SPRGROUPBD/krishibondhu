package com.example.data.remote

import android.util.Log
import com.example.data.local.*
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Shared Firebase Realtime Database synchronization for Android & Website
 * pointing to `https://screen-playbd-default-rtdb.europe-west1.firebasedatabase.app`.
 * Safely degrades to local Room persistence whenever Firebase is offline or unavailable
 * (e.g., unit tests or offline Superadmin access).
 */
class FirebaseRealtimeSyncManager {

    companion object {
        const val RTDB_URL = "https://screen-playbd-default-rtdb.europe-west1.firebasedatabase.app"
        private const val TAG = "KrishiRTDBSync"
    }

    private var listenersAttached = false

    private fun rootRefOrNull(): DatabaseReference? = runCatching {
        if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isEmpty()) {
            null
        } else {
            FirebaseDatabase.getInstance(RTDB_URL).reference
        }
    }.getOrElse {
        runCatching { FirebaseDatabase.getInstance(RTDB_URL).reference }.getOrNull()
    }

    fun startRealtimeListeners(
        localDb: KrishiBondhuDatabase,
        scope: CoroutineScope,
        onIncomingNotification: (NotificationEntity) -> Unit
    ) {
        if (listenersAttached) return
        val root = rootRefOrNull() ?: return
        listenersAttached = true

        // 1. Users listener
        root.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { snap -> snapToUser(snap) }
                if (users.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { localDb.userDao().insertAll(users) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "users listener cancelled: ${error.message}")
            }
        })

        // 2. Categories listener (custom admin additions)
        root.child("categories").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val categories = snapshot.children.mapNotNull { snap -> snapToCategory(snap) }
                if (categories.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { localDb.categoryDao().insertAll(categories) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "categories listener cancelled: ${error.message}")
            }
        })

        // 3. Locations listener
        root.child("locations").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = snapshot.children.mapNotNull { snap -> snapToLocation(snap) }
                if (locations.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { localDb.locationDao().insertAll(locations) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "locations listener cancelled: ${error.message}")
            }
        })

        // 4. Products listener
        root.child("products").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val products = snapshot.children.mapNotNull { snap -> snapToProduct(snap) }
                if (products.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { localDb.productDao().insertAll(products) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "products listener cancelled: ${error.message}")
            }
        })

        // 5. Orders + OrderItems listener
        root.child("orders").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = mutableListOf<OrderEntity>()
                val allItems = mutableListOf<OrderItemEntity>()
                snapshot.children.forEach { snap ->
                    snapToOrder(snap)?.let { ord ->
                        orders.add(ord)
                        snap.child("items").children.mapNotNull { itemSnap ->
                            snapToOrderItem(itemSnap, ord.id)
                        }.forEach { allItems.add(it) }
                    }
                }
                if (orders.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            localDb.orderDao().insertOrders(orders)
                            if (allItems.isNotEmpty()) {
                                localDb.orderDao().insertOrderItems(allItems)
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "orders listener cancelled: ${error.message}")
            }
        })

        // 6. Notifications real-time ChildEventListener (triggers local alert on incoming events)
        root.child("notifications").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val notif = snapToNotification(snapshot) ?: return
                scope.launch(Dispatchers.IO) {
                    runCatching { localDb.notificationAuditDao().insertNotification(notif) }
                    if (!notif.isRead) {
                        onIncomingNotification(notif)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val notif = snapToNotification(snapshot) ?: return
                scope.launch(Dispatchers.IO) {
                    runCatching { localDb.notificationAuditDao().insertNotification(notif) }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "notifications listener cancelled: ${error.message}")
            }
        })

        // 7. Reviews & Complaints listeners
        root.child("reviews").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reviews = snapshot.children.mapNotNull { snapToReview(it) }
                if (reviews.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { localDb.reviewComplaintDao().insertReviews(reviews) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        root.child("complaints").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val complaints = snapshot.children.mapNotNull { snapToComplaint(it) }
                if (complaints.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { localDb.reviewComplaintDao().insertComplaints(complaints) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        root.child("auditLogs").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val logs = snapshot.children.mapNotNull { snapToAuditLog(it) }
                if (logs.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { localDb.notificationAuditDao().insertAuditLogs(logs) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    suspend fun pushUser(user: UserEntity) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("users").child(user.id).setValue(userToMap(user)).await()
        }.onFailure { Log.w(TAG, "pushUser skipped: ${it.message}") }
    }

    suspend fun fetchUserByPhone(phone: String): UserEntity? {
        return runCatching {
            val root = rootRefOrNull() ?: return null
            val snap = root.child("users")
                .orderByChild("phone")
                .equalTo(phone)
                .limitToFirst(1)
                .get()
                .await()
            val first = snap.children.firstOrNull() ?: return null
            snapToUser(first)
        }.onFailure { Log.w(TAG, "fetchUserByPhone skipped: ${it.message}") }.getOrNull()
    }

    suspend fun pushProduct(product: ProductEntity) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("products").child(product.id).setValue(productToMap(product)).await()
        }.onFailure { Log.w(TAG, "pushProduct skipped: ${it.message}") }
    }

    suspend fun pushOrder(order: OrderEntity, items: List<OrderItemEntity>) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("orders").child(order.id).setValue(orderToMap(order, items)).await()
        }.onFailure { Log.w(TAG, "pushOrder skipped: ${it.message}") }
    }

    suspend fun pushCategory(category: CategoryEntity) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("categories").child(category.id).setValue(categoryToMap(category)).await()
        }.onFailure { Log.w(TAG, "pushCategory skipped: ${it.message}") }
    }

    suspend fun pushLocation(location: LocationEntity) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("locations").child(location.id).setValue(locationToMap(location)).await()
        }.onFailure { Log.w(TAG, "pushLocation skipped: ${it.message}") }
    }

    suspend fun pushNotification(notification: NotificationEntity) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("notifications").child(notification.id).setValue(notificationToMap(notification)).await()
        }.onFailure { Log.w(TAG, "pushNotification skipped: ${it.message}") }
    }

    suspend fun pushReview(review: ReviewEntity) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("reviews").child(review.id).setValue(reviewToMap(review)).await()
        }.onFailure { Log.w(TAG, "pushReview skipped: ${it.message}") }
    }

    suspend fun pushComplaint(complaint: ComplaintEntity) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("complaints").child(complaint.id).setValue(complaintToMap(complaint)).await()
        }.onFailure { Log.w(TAG, "pushComplaint skipped: ${it.message}") }
    }

    suspend fun pushAuditLog(log: AuditLogEntity) {
        runCatching {
            val root = rootRefOrNull() ?: return
            root.child("auditLogs").child(log.id).setValue(auditLogToMap(log)).await()
        }.onFailure { Log.w(TAG, "pushAuditLog skipped: ${it.message}") }
    }

    // Explicit Map conversions (avoids Kotlin boolean `is*` getter naming mismatch with JS/Web)
    private fun userToMap(u: UserEntity): Map<String, Any?> = mapOf(
        "id" to u.id,
        "fullName" to u.fullName,
        "phone" to u.phone,
        "passwordHash" to u.passwordHash,
        "role" to u.role,
        "division" to u.division,
        "district" to u.district,
        "upazila" to u.upazila,
        "unionName" to u.unionName,
        "village" to u.village,
        "farmBio" to u.farmBio,
        "nidMasked" to u.nidMasked,
        "verificationStatus" to u.verificationStatus,
        "isMobileVerified" to u.isMobileVerified,
        "rating" to u.rating,
        "reviewCount" to u.reviewCount,
        "createdAt" to u.createdAt
    )

    private fun snapToUser(s: DataSnapshot): UserEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val phone = s.str("phone") ?: return null
        return UserEntity(
            id = id,
            fullName = s.str("fullName") ?: "",
            phone = phone,
            passwordHash = s.str("passwordHash") ?: "",
            role = s.str("role") ?: "BUYER",
            division = s.str("division") ?: "ঢাকা",
            district = s.str("district") ?: "ঢাকা",
            upazila = s.str("upazila") ?: "",
            unionName = s.str("unionName") ?: "",
            village = s.str("village") ?: "",
            farmBio = s.str("farmBio") ?: "",
            nidMasked = s.str("nidMasked") ?: "",
            verificationStatus = s.str("verificationStatus") ?: "PENDING",
            isMobileVerified = s.bool("isMobileVerified") ?: s.bool("mobileVerified") ?: false,
            rating = s.dbl("rating") ?: 5.0,
            reviewCount = (s.lng("reviewCount") ?: 0L).toInt(),
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis(),
            isDemoRecord = false
        )
    }

    private fun categoryToMap(c: CategoryEntity): Map<String, Any?> = mapOf(
        "id" to c.id,
        "nameBn" to c.nameBn,
        "nameEn" to c.nameEn,
        "groupKey" to c.groupKey,
        "emoji" to c.emoji,
        "sortOrder" to c.sortOrder,
        "isActive" to c.isActive
    )

    private fun snapToCategory(s: DataSnapshot): CategoryEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val nameBn = s.str("nameBn") ?: return null
        return CategoryEntity(
            id = id,
            nameBn = nameBn,
            nameEn = s.str("nameEn") ?: nameBn,
            groupKey = s.str("groupKey") ?: "OTHER",
            emoji = s.str("emoji") ?: "🌱",
            sortOrder = (s.lng("sortOrder") ?: 99L).toInt(),
            isActive = s.bool("isActive") ?: s.bool("active") ?: true
        )
    }

    private fun locationToMap(l: LocationEntity): Map<String, Any?> = mapOf(
        "id" to l.id,
        "divisionBn" to l.divisionBn,
        "districtBn" to l.districtBn,
        "upazilaBn" to l.upazilaBn,
        "unionBn" to l.unionBn,
        "villageBn" to l.villageBn,
        "isActive" to l.isActive
    )

    private fun snapToLocation(s: DataSnapshot): LocationEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val districtBn = s.str("districtBn") ?: return null
        val upazilaBn = s.str("upazilaBn") ?: return null
        return LocationEntity(
            id = id,
            divisionBn = s.str("divisionBn") ?: "ঢাকা",
            districtBn = districtBn,
            upazilaBn = upazilaBn,
            unionBn = s.str("unionBn") ?: "",
            villageBn = s.str("villageBn") ?: "",
            isActive = s.bool("isActive") ?: s.bool("active") ?: true
        )
    }

    private fun productToMap(p: ProductEntity): Map<String, Any?> = mapOf(
        "id" to p.id,
        "farmerId" to p.farmerId,
        "farmerName" to p.farmerName,
        "farmerVerified" to p.farmerVerified,
        "categoryId" to p.categoryId,
        "categoryNameBn" to p.categoryNameBn,
        "nameBn" to p.nameBn,
        "nameEn" to p.nameEn,
        "descriptionBn" to p.descriptionBn,
        "imageUri" to p.imageUri,
        "availableQuantity" to p.availableQuantity,
        "unit" to p.unit,
        "pricePerUnit" to p.pricePerUnit,
        "harvestDate" to p.harvestDate,
        "availabilityDate" to p.availabilityDate,
        "division" to p.division,
        "district" to p.district,
        "upazila" to p.upazila,
        "unionName" to p.unionName,
        "village" to p.village,
        "pickupAddress" to p.pickupAddress,
        "supportsFarmPickup" to p.supportsFarmPickup,
        "supportsDirectDelivery" to p.supportsDirectDelivery,
        "deliveryChargeEstimate" to p.deliveryChargeEstimate,
        "isWholesale" to p.isWholesale,
        "isPopular" to p.isPopular,
        "status" to p.status,
        "createdAt" to p.createdAt,
        "updatedAt" to p.updatedAt
    )

    private fun snapToProduct(s: DataSnapshot): ProductEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val nameBn = s.str("nameBn") ?: return null
        val farmerId = s.str("farmerId") ?: return null
        return ProductEntity(
            id = id,
            farmerId = farmerId,
            farmerName = s.str("farmerName") ?: "",
            farmerVerified = s.bool("farmerVerified") ?: false,
            categoryId = s.str("categoryId") ?: "cat_other",
            categoryNameBn = s.str("categoryNameBn") ?: "",
            nameBn = nameBn,
            nameEn = s.str("nameEn") ?: nameBn,
            descriptionBn = s.str("descriptionBn") ?: "",
            imageUri = s.str("imageUri") ?: "",
            availableQuantity = s.dbl("availableQuantity") ?: 0.0,
            unit = s.str("unit") ?: "কেজি",
            pricePerUnit = s.dbl("pricePerUnit") ?: 0.0,
            harvestDate = s.str("harvestDate") ?: "",
            availabilityDate = s.str("availabilityDate") ?: "",
            division = s.str("division") ?: "ঢাকা",
            district = s.str("district") ?: "ঢাকা",
            upazila = s.str("upazila") ?: "",
            unionName = s.str("unionName") ?: "",
            village = s.str("village") ?: "",
            pickupAddress = s.str("pickupAddress") ?: "",
            supportsFarmPickup = s.bool("supportsFarmPickup") ?: true,
            supportsDirectDelivery = s.bool("supportsDirectDelivery") ?: true,
            deliveryChargeEstimate = s.dbl("deliveryChargeEstimate") ?: 0.0,
            isWholesale = s.bool("isWholesale") ?: s.bool("wholesale") ?: false,
            isPopular = s.bool("isPopular") ?: s.bool("popular") ?: false,
            status = s.str("status") ?: "ACTIVE",
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis(),
            updatedAt = s.lng("updatedAt") ?: System.currentTimeMillis(),
            isDemoRecord = false
        )
    }

    private fun orderToMap(o: OrderEntity, items: List<OrderItemEntity>): Map<String, Any?> = mapOf(
        "id" to o.id,
        "orderCode" to o.orderCode,
        "buyerId" to o.buyerId,
        "buyerName" to o.buyerName,
        "buyerPhone" to o.buyerPhone,
        "farmerId" to o.farmerId,
        "farmerName" to o.farmerName,
        "farmerPhone" to o.farmerPhone,
        "status" to o.status,
        "deliveryOption" to o.deliveryOption,
        "deliveryAddress" to o.deliveryAddress,
        "division" to o.division,
        "district" to o.district,
        "upazila" to o.upazila,
        "productTotal" to o.productTotal,
        "deliveryCharge" to o.deliveryCharge,
        "grandTotal" to o.grandTotal,
        "paymentMethod" to o.paymentMethod,
        "transactionReference" to o.transactionReference,
        "paymentStatus" to o.paymentStatus,
        "itemsSummary" to o.itemsSummary,
        "createdAt" to o.createdAt,
        "updatedAt" to o.updatedAt,
        "items" to items.associate { it.id to orderItemToMap(it) }
    )

    private fun orderItemToMap(i: OrderItemEntity): Map<String, Any?> = mapOf(
        "id" to i.id,
        "orderId" to i.orderId,
        "productId" to i.productId,
        "productNameBn" to i.productNameBn,
        "unit" to i.unit,
        "quantity" to i.quantity,
        "pricePerUnit" to i.pricePerUnit,
        "lineTotal" to i.lineTotal
    )

    private fun snapToOrder(s: DataSnapshot): OrderEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val orderCode = s.str("orderCode") ?: return null
        return OrderEntity(
            id = id,
            orderCode = orderCode,
            buyerId = s.str("buyerId") ?: "",
            buyerName = s.str("buyerName") ?: "",
            buyerPhone = s.str("buyerPhone") ?: "",
            farmerId = s.str("farmerId") ?: "",
            farmerName = s.str("farmerName") ?: "",
            farmerPhone = s.str("farmerPhone") ?: "",
            status = s.str("status") ?: "PENDING",
            deliveryOption = s.str("deliveryOption") ?: "FARM_PICKUP",
            deliveryAddress = s.str("deliveryAddress") ?: "",
            division = s.str("division") ?: "ঢাকা",
            district = s.str("district") ?: "ঢাকা",
            upazila = s.str("upazila") ?: "",
            productTotal = s.dbl("productTotal") ?: 0.0,
            deliveryCharge = s.dbl("deliveryCharge") ?: 0.0,
            grandTotal = s.dbl("grandTotal") ?: 0.0,
            paymentMethod = s.str("paymentMethod") ?: "CASH_ON_PICKUP",
            transactionReference = s.str("transactionReference") ?: "",
            paymentStatus = s.str("paymentStatus") ?: "PENDING",
            itemsSummary = s.str("itemsSummary") ?: "",
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis(),
            updatedAt = s.lng("updatedAt") ?: System.currentTimeMillis(),
            isDemoRecord = false
        )
    }

    private fun snapToOrderItem(s: DataSnapshot, orderId: String): OrderItemEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val productId = s.str("productId") ?: return null
        return OrderItemEntity(
            id = id,
            orderId = s.str("orderId") ?: orderId,
            productId = productId,
            productNameBn = s.str("productNameBn") ?: "",
            unit = s.str("unit") ?: "কেজি",
            quantity = s.dbl("quantity") ?: 0.0,
            pricePerUnit = s.dbl("pricePerUnit") ?: 0.0,
            lineTotal = s.dbl("lineTotal") ?: 0.0
        )
    }

    private fun notificationToMap(n: NotificationEntity): Map<String, Any?> = mapOf(
        "id" to n.id,
        "userId" to n.userId,
        "targetRole" to n.targetRole,
        "senderRole" to n.senderRole,
        "orderId" to n.orderId,
        "type" to n.type,
        "titleBn" to n.titleBn,
        "messageBn" to n.messageBn,
        "isRead" to n.isRead,
        "createdAt" to n.createdAt
    )

    private fun snapToNotification(s: DataSnapshot): NotificationEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val titleBn = s.str("titleBn") ?: return null
        return NotificationEntity(
            id = id,
            userId = s.str("userId") ?: "ALL",
            targetRole = s.str("targetRole") ?: "",
            senderRole = s.str("senderRole") ?: "SYSTEM",
            orderId = s.str("orderId") ?: "",
            type = s.str("type") ?: "SYSTEM",
            titleBn = titleBn,
            messageBn = s.str("messageBn") ?: "",
            isRead = s.bool("isRead") ?: s.bool("read") ?: false,
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis()
        )
    }

    private fun reviewToMap(r: ReviewEntity): Map<String, Any?> = mapOf(
        "id" to r.id,
        "orderId" to r.orderId,
        "productId" to r.productId,
        "farmerId" to r.farmerId,
        "buyerId" to r.buyerId,
        "buyerName" to r.buyerName,
        "rating" to r.rating,
        "commentBn" to r.commentBn,
        "createdAt" to r.createdAt
    )

    private fun snapToReview(s: DataSnapshot): ReviewEntity? {
        val id = s.str("id") ?: s.key ?: return null
        return ReviewEntity(
            id = id,
            orderId = s.str("orderId") ?: "",
            productId = s.str("productId") ?: "",
            farmerId = s.str("farmerId") ?: "",
            buyerId = s.str("buyerId") ?: "",
            buyerName = s.str("buyerName") ?: "",
            rating = (s.lng("rating") ?: 5L).toInt(),
            commentBn = s.str("commentBn") ?: "",
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis()
        )
    }

    private fun complaintToMap(c: ComplaintEntity): Map<String, Any?> = mapOf(
        "id" to c.id,
        "orderId" to c.orderId,
        "reporterId" to c.reporterId,
        "reporterName" to c.reporterName,
        "subjectBn" to c.subjectBn,
        "descriptionBn" to c.descriptionBn,
        "status" to c.status,
        "createdAt" to c.createdAt
    )

    private fun snapToComplaint(s: DataSnapshot): ComplaintEntity? {
        val id = s.str("id") ?: s.key ?: return null
        return ComplaintEntity(
            id = id,
            orderId = s.str("orderId") ?: "",
            reporterId = s.str("reporterId") ?: "",
            reporterName = s.str("reporterName") ?: "",
            subjectBn = s.str("subjectBn") ?: "",
            descriptionBn = s.str("descriptionBn") ?: "",
            status = s.str("status") ?: "OPEN",
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis()
        )
    }

    private fun auditLogToMap(a: AuditLogEntity): Map<String, Any?> = mapOf(
        "id" to a.id,
        "actorId" to a.actorId,
        "actorRole" to a.actorRole,
        "actionBn" to a.actionBn,
        "targetEntity" to a.targetEntity,
        "targetId" to a.targetId,
        "detailsBn" to a.detailsBn,
        "createdAt" to a.createdAt
    )

    private fun snapToAuditLog(s: DataSnapshot): AuditLogEntity? {
        val id = s.str("id") ?: s.key ?: return null
        return AuditLogEntity(
            id = id,
            actorId = s.str("actorId") ?: "",
            actorRole = s.str("actorRole") ?: "",
            actionBn = s.str("actionBn") ?: "",
            targetEntity = s.str("targetEntity") ?: "",
            targetId = s.str("targetId") ?: "",
            detailsBn = s.str("detailsBn") ?: "",
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis()
        )
    }

    private fun DataSnapshot.str(key: String): String? = child(key).getValue(String::class.java)
    private fun DataSnapshot.bool(key: String): Boolean? = child(key).getValue(Boolean::class.java)
    private fun DataSnapshot.dbl(key: String): Double? = (child(key).value as? Number)?.toDouble()
    private fun DataSnapshot.lng(key: String): Long? = (child(key).value as? Number)?.toLong()
}
