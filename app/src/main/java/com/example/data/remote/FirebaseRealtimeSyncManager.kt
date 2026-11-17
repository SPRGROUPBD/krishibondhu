package com.example.data.remote

import android.util.Log
import com.example.data.local.*
import com.example.data.model.AdConfig
import com.example.data.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shared Firebase Realtime Database synchronization for Android & Website
 * pointing to `https://screen-playbd-default-rtdb.europe-west1.firebasedatabase.app`.
 * Uses BOTH Firebase Android SDK real-time listeners AND OkHttp REST fallback/polling
 * so App <-> Website <-> Admin Panel always remain synchronized regardless of WebSocket state
 * or RTDB `.indexOn` server rules.
 */
class FirebaseRealtimeSyncManager(
    private val enableRemoteSync: Boolean = true
) {

    companion object {
        const val RTDB_URL = "https://screen-playbd-default-rtdb.europe-west1.firebasedatabase.app"
        private const val TAG = "KrishiRTDBSync"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private var listenersAttached = false
    private val launchTimestamp = System.currentTimeMillis()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private fun rootRefOrNull(): DatabaseReference? {
        if (!enableRemoteSync) return null
        return runCatching {
            if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isEmpty()) {
                null
            } else {
                FirebaseDatabase.getInstance(RTDB_URL).reference
            }
        }.getOrElse {
            runCatching { FirebaseDatabase.getInstance(RTDB_URL).reference }.getOrNull()
        }
    }

    fun startRealtimeListeners(
        localDb: KrishiBondhuDatabase,
        scope: CoroutineScope,
        onAdConfigChanged: (AdConfig) -> Unit = {},
        onIncomingNotification: (NotificationEntity) -> Unit
    ) {
        if (!enableRemoteSync || listenersAttached) return
        listenersAttached = true

        // Always launch REST immediate pull + periodic live mirror loop + offline push flush
        scope.launch(Dispatchers.IO) {
            pullAllFromRest(localDb, onAdConfigChanged, onIncomingNotification)
            pushMissingLocalRecordsToRest(localDb)
            while (isActive) {
                delay(10_000L)
                pullAllFromRest(localDb, onAdConfigChanged, onIncomingNotification)
            }
        }

        val root = rootRefOrNull() ?: return

        // 1. Users listener
        root.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { snap -> snapToUser(itOrSelf(snap)) }
                if (users.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        users.forEach { u -> runCatching { localDb.userDao().upsertUser(u) } }
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

        // 4. Products listener (ensures farmer user exists before inserting to satisfy FK cleanly)
        root.child("products").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val products = snapshot.children.mapNotNull { snap -> snapToProduct(snap) }
                if (products.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        upsertProductsEnsuringFarmers(localDb, products)
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

        // 6. Notifications real-time ChildEventListener
        root.child("notifications").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val notif = snapToNotification(snapshot) ?: return
                scope.launch(Dispatchers.IO) {
                    ingestRemoteNotification(localDb, notif, onIncomingNotification)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val notif = snapToNotification(snapshot) ?: return
                scope.launch(Dispatchers.IO) {
                    val dao = localDb.notificationAuditDao()
                    val existing = runCatching { dao.getNotificationById(notif.id) }.getOrNull()
                    val merged = if (existing != null && existing.isRead) notif.copy(isRead = true) else notif
                    runCatching { dao.insertNotification(merged) }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "notifications listener cancelled: ${error.message}")
            }
        })

        // 6b. Ad Monetization Configuration listener
        root.child("adConfig").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    snapToAdConfig(snapshot)?.let { onAdConfigChanged(it) }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "adConfig listener cancelled: ${error.message}")
            }
        })

        // 7. Reviews, Complaints & AuditLogs listeners
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

        // 8. Activation Payments listener
        root.child("activationPayments").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val payments = snapshot.children.mapNotNull { snapToActivationPayment(it) }
                if (payments.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { localDb.activationPaymentDao().insertAll(payments) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "activationPayments listener cancelled: ${error.message}")
            }
        })

        // 9. Payment Settings listener
        root.child("paymentSettings").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    snapToPaymentSettings(snapshot)?.let { settings ->
                        scope.launch(Dispatchers.IO) {
                            runCatching { localDb.paymentSettingsDao().upsertSettings(settings) }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "paymentSettings listener cancelled: ${error.message}")
            }
        })
    }

    private fun itOrSelf(snap: DataSnapshot): DataSnapshot = snap

    private suspend fun ingestRemoteNotification(
        localDb: KrishiBondhuDatabase,
        notif: NotificationEntity,
        onIncomingNotification: (NotificationEntity) -> Unit
    ) {
        val dao = localDb.notificationAuditDao()
        val existing = runCatching { dao.getNotificationById(notif.id) }.getOrNull()
        val merged = if (existing != null && existing.isRead) notif.copy(isRead = true) else notif
        runCatching { dao.insertNotification(merged) }
        if (existing == null && !merged.isRead && merged.createdAt >= launchTimestamp - 30_000L) {
            onIncomingNotification(merged)
        }
    }

    private suspend fun upsertProductsEnsuringFarmers(
        localDb: KrishiBondhuDatabase,
        products: List<ProductEntity>
    ) {
        val missingFarmer = products.any { p ->
            runCatching { localDb.userDao().getUserById(p.farmerId) == null }.getOrDefault(false)
        }
        if (missingFarmer) {
            pullUsersFromRestIntoDb(localDb)
        }
        products.forEach { prod ->
            runCatching { localDb.productDao().upsertProduct(prod) }
                .onFailure { Log.w(TAG, "Product upsert skipped (${prod.id}): ${it.message}") }
        }
    }

    /**
     * Pulls the entire shared RTDB tree over HTTPS REST (`/.json`) and merges into Room in safe FK order.
     * Guarantees Website <-> Android <-> Admin parity even if Firebase SDK WebSocket or `.indexOn` fails.
     */
    suspend fun pullAllFromRest(
        localDb: KrishiBondhuDatabase,
        onAdConfigChanged: (AdConfig) -> Unit = {},
        onIncomingNotification: (NotificationEntity) -> Unit = {}
    ) {
        if (!enableRemoteSync) return
        val bodyStr = restGetJson(".json") ?: return
        val rootObj = runCatching { JSONObject(bodyStr) }.getOrNull() ?: return

        // 1. Categories first (FK target of products)
        rootObj.optJSONObject("categories")?.let { catsObj ->
            val cats = parseJsonMap(catsObj) { id, obj -> jsonToCategory(id, obj) }
            if (cats.isNotEmpty()) {
                runCatching { localDb.categoryDao().insertAll(cats) }
            }
        }

        // 2. Locations
        rootObj.optJSONObject("locations")?.let { locsObj ->
            val locs = parseJsonMap(locsObj) { id, obj -> jsonToLocation(id, obj) }
            if (locs.isNotEmpty()) {
                runCatching { localDb.locationDao().insertAll(locs) }
            }
        }

        // 3. Users (FK target of products)
        rootObj.optJSONObject("users")?.let { usersObj ->
            val users = parseJsonMap(usersObj) { id, obj -> jsonToUser(id, obj) }
            users.forEach { u ->
                runCatching { localDb.userDao().upsertUser(u) }
            }
        }

        // 4. Products (after users & categories)
        rootObj.optJSONObject("products")?.let { prodsObj ->
            val prods = parseJsonMap(prodsObj) { id, obj -> jsonToProduct(id, obj) }
            prods.forEach { p ->
                runCatching { localDb.productDao().upsertProduct(p) }
            }
        }

        // 5. Orders + items
        rootObj.optJSONObject("orders")?.let { ordersObj ->
            val orders = mutableListOf<OrderEntity>()
            val items = mutableListOf<OrderItemEntity>()
            val keys = ordersObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = ordersObj.optJSONObject(key) ?: continue
                jsonToOrder(key, obj)?.let { ord ->
                    orders.add(ord)
                    obj.optJSONObject("items")?.let { itemsObj ->
                        val itemKeys = itemsObj.keys()
                        while (itemKeys.hasNext()) {
                            val ik = itemKeys.next()
                            itemsObj.optJSONObject(ik)?.let { iObj ->
                                jsonToOrderItem(ik, iObj, ord.id)?.let { items.add(it) }
                            }
                        }
                    }
                }
            }
            if (orders.isNotEmpty()) {
                runCatching {
                    localDb.orderDao().insertOrders(orders)
                    if (items.isNotEmpty()) localDb.orderDao().insertOrderItems(items)
                }
            }
        }

        // 6. Notifications
        rootObj.optJSONObject("notifications")?.let { notifsObj ->
            val notifs = parseJsonMap(notifsObj) { id, obj -> jsonToNotification(id, obj) }
            notifs.forEach { n ->
                ingestRemoteNotification(localDb, n, onIncomingNotification)
            }
        }

        // 7. Reviews, Complaints, AuditLogs
        rootObj.optJSONObject("reviews")?.let { revObj ->
            val list = parseJsonMap(revObj) { id, obj -> jsonToReview(id, obj) }
            if (list.isNotEmpty()) runCatching { localDb.reviewComplaintDao().insertReviews(list) }
        }
        rootObj.optJSONObject("complaints")?.let { cmpObj ->
            val list = parseJsonMap(cmpObj) { id, obj -> jsonToComplaint(id, obj) }
            if (list.isNotEmpty()) runCatching { localDb.reviewComplaintDao().insertComplaints(list) }
        }
        rootObj.optJSONObject("auditLogs")?.let { audObj ->
            val list = parseJsonMap(audObj) { id, obj -> jsonToAuditLog(id, obj) }
            if (list.isNotEmpty()) runCatching { localDb.notificationAuditDao().insertAuditLogs(list) }
        }

        // 8. AdConfig
        rootObj.optJSONObject("adConfig")?.let { adObj ->
            jsonToAdConfig(adObj)?.let { onAdConfigChanged(it) }
        }

        // 9. Activation Payments
        rootObj.optJSONObject("activationPayments")?.let { payObj ->
            val list = parseJsonMap(payObj) { id, obj -> jsonToActivationPayment(id, obj) }
            if (list.isNotEmpty()) {
                runCatching { localDb.activationPaymentDao().insertAll(list) }
            }
        }

        // 10. Payment Settings
        rootObj.optJSONObject("paymentSettings")?.let { psObj ->
            jsonToPaymentSettings(psObj)?.let { settings ->
                runCatching { localDb.paymentSettingsDao().upsertSettings(settings) }
            }
        }
    }

    /**
     * Pushes any locally created non-demo users (excluding offline-only superadmin) or products
     * that aren't yet on Firebase RTDB (e.g. created while offline or before REST fallback).
     */
    private suspend fun pushMissingLocalRecordsToRest(localDb: KrishiBondhuDatabase) {
        if (!enableRemoteSync) return
        val remoteUsersStr = restGetJson("users.json") ?: return
        val remoteUsersObj = if (remoteUsersStr == "null") JSONObject() else runCatching { JSONObject(remoteUsersStr) }.getOrNull() ?: return
        val localUsers = runCatching { localDb.userDao().getAllUsersOnce() }.getOrDefault(emptyList())
        localUsers.filter { !it.isDemoRecord && it.role != UserRole.ADMIN.name }.forEach { u ->
            if (!remoteUsersObj.has(u.id)) {
                pushUser(u)
            }
        }
        val remotePayStr = restGetJson("activationPayments.json")
        if (remotePayStr != null) {
            val remotePayObj = if (remotePayStr == "null") JSONObject() else runCatching { JSONObject(remotePayStr) }.getOrNull()
            if (remotePayObj != null) {
                val localPayments = runCatching { localDb.activationPaymentDao().getAllPaymentsOnce() }.getOrDefault(emptyList())
                localPayments.forEach { pay ->
                    if (!remotePayObj.has(pay.id)) {
                        pushActivationPayment(pay)
                    }
                }
            }
        }
    }

    private suspend fun pullUsersFromRestIntoDb(localDb: KrishiBondhuDatabase): List<UserEntity> {
        val bodyStr = restGetJson("users.json") ?: return emptyList()
        if (bodyStr == "null" || bodyStr.isBlank()) return emptyList()
        val obj = runCatching { JSONObject(bodyStr) }.getOrNull() ?: return emptyList()
        val users = parseJsonMap(obj) { id, uObj -> jsonToUser(id, uObj) }
        users.forEach { u -> runCatching { localDb.userDao().upsertUser(u) } }
        return users
    }

    /**
     * Looks up a user by normalized phone across BOTH Firebase SDK and RTDB REST `/users.json`,
     * never relying solely on `.orderByChild("phone")` which requires `.indexOn` in server rules.
     */
    suspend fun fetchUserByPhone(phone: String, localDb: KrishiBondhuDatabase? = null): UserEntity? {
        if (!enableRemoteSync) return null
        val targetPhone = SecurityHashUtil.normalizePhone(phone)
        if (targetPhone.isBlank()) return null

        // 1. Try scanning SDK users snapshot directly (works even without .indexOn)
        val sdkUser = runCatching {
            withTimeoutOrNull(3000L) {
                val root = rootRefOrNull() ?: return@withTimeoutOrNull null
                val snap = root.child("users").get().await()
                val allUsers = snap.children.mapNotNull { snapToUser(it) }
                if (localDb != null && allUsers.isNotEmpty()) {
                    allUsers.forEach { u -> runCatching { localDb.userDao().upsertUser(u) } }
                }
                allUsers.firstOrNull { SecurityHashUtil.normalizePhone(it.phone) == targetPhone }
            }
        }.getOrNull()
        if (sdkUser != null) return sdkUser

        // 2. Fallback to OkHttp REST `/users.json`
        val bodyStr = restGetJson("users.json") ?: return null
        if (bodyStr == "null" || bodyStr.isBlank()) return null
        val usersObj = runCatching { JSONObject(bodyStr) }.getOrNull() ?: return null
        val allRestUsers = parseJsonMap(usersObj) { id, obj -> jsonToUser(id, obj) }
        if (localDb != null && allRestUsers.isNotEmpty()) {
            allRestUsers.forEach { u -> runCatching { localDb.userDao().upsertUser(u) } }
        }
        return allRestUsers.firstOrNull { SecurityHashUtil.normalizePhone(it.phone) == targetPhone }
    }

    suspend fun pushUser(user: UserEntity) {
        pushNode("users/${user.id}", userToMap(user)) { root ->
            root.child("users").child(user.id).setValue(userToMap(user)).await()
        }
    }

    suspend fun pushProduct(product: ProductEntity) {
        pushNode("products/${product.id}", productToMap(product)) { root ->
            root.child("products").child(product.id).setValue(productToMap(product)).await()
        }
    }

    suspend fun pushOrder(order: OrderEntity, items: List<OrderItemEntity>) {
        val map = orderToMap(order, items)
        pushNode("orders/${order.id}", map) { root ->
            root.child("orders").child(order.id).setValue(map).await()
        }
    }

    suspend fun pushCategory(category: CategoryEntity) {
        val map = categoryToMap(category)
        pushNode("categories/${category.id}", map) { root ->
            root.child("categories").child(category.id).setValue(map).await()
        }
    }

    suspend fun pushLocation(location: LocationEntity) {
        val map = locationToMap(location)
        pushNode("locations/${location.id}", map) { root ->
            root.child("locations").child(location.id).setValue(map).await()
        }
    }

    suspend fun pushNotification(notification: NotificationEntity) {
        val map = notificationToMap(notification)
        pushNode("notifications/${notification.id}", map) { root ->
            root.child("notifications").child(notification.id).setValue(map).await()
        }
    }

    suspend fun pushReview(review: ReviewEntity) {
        val map = reviewToMap(review)
        pushNode("reviews/${review.id}", map) { root ->
            root.child("reviews").child(review.id).setValue(map).await()
        }
    }

    suspend fun pushComplaint(complaint: ComplaintEntity) {
        val map = complaintToMap(complaint)
        pushNode("complaints/${complaint.id}", map) { root ->
            root.child("complaints").child(complaint.id).setValue(map).await()
        }
    }

    suspend fun pushAuditLog(log: AuditLogEntity) {
        val map = auditLogToMap(log)
        pushNode("auditLogs/${log.id}", map) { root ->
            root.child("auditLogs").child(log.id).setValue(map).await()
        }
    }

    suspend fun pushNotificationRead(notificationId: String) {
        if (!enableRemoteSync) return
        val sdkDone = withTimeoutOrNull(2500L) {
            runCatching {
                val root = rootRefOrNull() ?: return@runCatching false
                root.child("notifications").child(notificationId).child("isRead").setValue(true).await()
                true
            }.getOrDefault(false)
        } ?: false
        if (!sdkDone) {
            restPutJson("notifications/$notificationId/isRead.json", "true")
        }
    }

    suspend fun pushAdConfig(config: AdConfig) {
        val map = adConfigToMap(config)
        pushNode("adConfig", map) { root ->
            root.child("adConfig").setValue(map).await()
        }
    }

    suspend fun pushActivationPayment(payment: ActivationPaymentEntity) {
        val map = activationPaymentToMap(payment)
        pushNode("activationPayments/${payment.id}", map) { root ->
            root.child("activationPayments").child(payment.id).setValue(map).await()
        }
    }

    suspend fun pushPaymentSettings(settings: PaymentSettingsEntity) {
        val map = paymentSettingsToMap(settings)
        pushNode("paymentSettings", map) { root ->
            root.child("paymentSettings").setValue(map).await()
        }
    }

    suspend fun fetchAllActivationPayments(localDb: KrishiBondhuDatabase? = null): List<ActivationPaymentEntity> {
        if (!enableRemoteSync) return emptyList()
        val bodyStr = restGetJson("activationPayments.json") ?: return emptyList()
        if (bodyStr == "null" || bodyStr.isBlank()) return emptyList()
        val obj = runCatching { JSONObject(bodyStr) }.getOrNull() ?: return emptyList()
        val list = parseJsonMap(obj) { id, pObj -> jsonToActivationPayment(id, pObj) }
        if (localDb != null && list.isNotEmpty()) {
            runCatching { localDb.activationPaymentDao().insertAll(list) }
        }
        return list
    }

    private suspend fun pushNode(
        path: String,
        payloadMap: Map<String, Any?>,
        sdkWrite: suspend (DatabaseReference) -> Unit
    ) {
        if (!enableRemoteSync) return
        val sdkOk = withTimeoutOrNull(3000L) {
            runCatching {
                val root = rootRefOrNull() ?: return@runCatching false
                sdkWrite(root)
                true
            }.onFailure { Log.w(TAG, "SDK push ($path) fallback to REST: ${it.message}") }
                .getOrDefault(false)
        } ?: false

        if (!sdkOk) {
            val json = mapToJsonObject(payloadMap).toString()
            restPutJson("$path.json", json)
        }
    }

    private suspend fun restGetJson(relativePath: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$RTDB_URL/$relativePath")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else {
                    Log.w(TAG, "REST GET $relativePath HTTP ${resp.code}")
                    null
                }
            }
        }.onFailure {
            Log.w(TAG, "REST GET $relativePath skipped: ${it.message}")
        }.getOrNull()
    }

    private suspend fun restPutJson(relativePath: String, jsonBody: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$RTDB_URL/$relativePath")
                .put(jsonBody.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "REST PUT $relativePath HTTP ${resp.code}")
                }
                resp.isSuccessful
            }
        }.onFailure {
            Log.w(TAG, "REST PUT $relativePath skipped: ${it.message}")
        }.getOrDefault(false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> mapToJsonObject(value as Map<String, Any?>)
        is List<*> -> JSONArray().apply { value.forEach { put(mapToJsonValue(it)) } }
        is Boolean, is Number, is String -> value
        else -> value.toString()
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, mapToJsonValue(v)) }
        return obj
    }

    private inline fun <T> parseJsonMap(
        obj: JSONObject,
        mapper: (String, JSONObject) -> T?
    ): List<T> {
        val list = mutableListOf<T>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val itemObj = obj.optJSONObject(k) ?: continue
            mapper(k, itemObj)?.let { list.add(it) }
        }
        return list
    }

    // ---------------------- Explicit Map / DataSnapshot / JSON Conversions ----------------------

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
        "profileImageUri" to u.profileImageUri,
        "verificationStatus" to u.verificationStatus,
        "isMobileVerified" to u.isMobileVerified,
        "rating" to u.rating,
        "reviewCount" to u.reviewCount,
        "createdAt" to u.createdAt,
        "accountStatus" to u.accountStatus,
        "activationDate" to u.activationDate,
        "expiryDate" to u.expiryDate,
        "validityYears" to u.validityYears,
        "lastPaymentId" to u.lastPaymentId
    )

    private fun resolveAccountStatus(role: String, rawAccountStatus: String?, verificationStatus: String): String {
        if (role == "ADMIN") return "ACTIVE"
        val clean = rawAccountStatus?.trim().orEmpty()
        if (clean.isNotBlank()) return clean
        return if (verificationStatus == "VERIFIED") "ACTIVE" else "PAYMENT_PENDING"
    }

    private fun snapToUser(s: DataSnapshot): UserEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val phone = s.str("phone") ?: return null
        val role = s.str("role") ?: "BUYER"
        val verificationStatus = s.str("verificationStatus") ?: "PENDING"
        val resolvedAccountStatus = resolveAccountStatus(role, s.str("accountStatus"), verificationStatus)
        return UserEntity(
            id = id,
            fullName = s.str("fullName") ?: "",
            phone = SecurityHashUtil.normalizePhone(phone),
            passwordHash = s.str("passwordHash") ?: "",
            role = role,
            division = s.str("division") ?: "ঢাকা",
            district = s.str("district") ?: "ঢাকা",
            upazila = s.str("upazila") ?: "",
            unionName = s.str("unionName") ?: "",
            village = s.str("village") ?: "",
            farmBio = s.str("farmBio") ?: "",
            nidMasked = s.str("nidMasked") ?: "",
            profileImageUri = s.str("profileImageUri") ?: "",
            verificationStatus = verificationStatus,
            isMobileVerified = s.bool("isMobileVerified") ?: s.bool("mobileVerified") ?: false,
            rating = s.dbl("rating") ?: 5.0,
            reviewCount = (s.lng("reviewCount") ?: 0L).toInt(),
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis(),
            isDemoRecord = false,
            accountStatus = resolvedAccountStatus,
            activationDate = s.lng("activationDate") ?: 0L,
            expiryDate = s.lng("expiryDate") ?: 0L,
            validityYears = (s.lng("validityYears") ?: 2L).toInt().coerceAtLeast(1),
            lastPaymentId = s.str("lastPaymentId") ?: ""
        )
    }

    private fun jsonToUser(key: String, o: JSONObject): UserEntity? {
        val id = o.optString("id", key).ifBlank { key }
        val phoneRaw = o.optString("phone", "")
        if (phoneRaw.isBlank()) return null
        val role = o.optString("role", "BUYER")
        val verificationStatus = o.optString("verificationStatus", "PENDING")
        val resolvedAccountStatus = resolveAccountStatus(role, o.optString("accountStatus", ""), verificationStatus)
        return UserEntity(
            id = id,
            fullName = o.optString("fullName", ""),
            phone = SecurityHashUtil.normalizePhone(phoneRaw),
            passwordHash = o.optString("passwordHash", ""),
            role = role,
            division = o.optString("division", "ঢাকা"),
            district = o.optString("district", "ঢাকা"),
            upazila = o.optString("upazila", ""),
            unionName = o.optString("unionName", ""),
            village = o.optString("village", ""),
            farmBio = o.optString("farmBio", ""),
            nidMasked = o.optString("nidMasked", ""),
            profileImageUri = o.optString("profileImageUri", ""),
            verificationStatus = verificationStatus,
            isMobileVerified = o.optBoolean("isMobileVerified", o.optBoolean("mobileVerified", false)),
            rating = o.optDouble("rating", 5.0).takeUnless { it.isNaN() } ?: 5.0,
            reviewCount = o.optInt("reviewCount", 0),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            isDemoRecord = false,
            accountStatus = resolvedAccountStatus,
            activationDate = o.optLong("activationDate", 0L),
            expiryDate = o.optLong("expiryDate", 0L),
            validityYears = o.optInt("validityYears", 2).coerceAtLeast(1),
            lastPaymentId = o.optString("lastPaymentId", "")
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

    private fun jsonToCategory(key: String, o: JSONObject): CategoryEntity? {
        val id = o.optString("id", key).ifBlank { key }
        val nameBn = o.optString("nameBn", "")
        if (nameBn.isBlank()) return null
        return CategoryEntity(
            id = id,
            nameBn = nameBn,
            nameEn = o.optString("nameEn", nameBn).ifBlank { nameBn },
            groupKey = o.optString("groupKey", "OTHER"),
            emoji = o.optString("emoji", "🌱"),
            sortOrder = o.optInt("sortOrder", 99),
            isActive = o.optBoolean("isActive", o.optBoolean("active", true))
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

    private fun jsonToLocation(key: String, o: JSONObject): LocationEntity? {
        val id = o.optString("id", key).ifBlank { key }
        val districtBn = o.optString("districtBn", "")
        val upazilaBn = o.optString("upazilaBn", "")
        if (districtBn.isBlank() || upazilaBn.isBlank()) return null
        return LocationEntity(
            id = id,
            divisionBn = o.optString("divisionBn", "ঢাকা"),
            districtBn = districtBn,
            upazilaBn = upazilaBn,
            unionBn = o.optString("unionBn", ""),
            villageBn = o.optString("villageBn", ""),
            isActive = o.optBoolean("isActive", o.optBoolean("active", true))
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
            unit = "মণ",
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

    private fun jsonToProduct(key: String, o: JSONObject): ProductEntity? {
        val id = o.optString("id", key).ifBlank { key }
        val nameBn = o.optString("nameBn", "")
        val farmerId = o.optString("farmerId", "")
        if (nameBn.isBlank() || farmerId.isBlank()) return null
        return ProductEntity(
            id = id,
            farmerId = farmerId,
            farmerName = o.optString("farmerName", ""),
            farmerVerified = o.optBoolean("farmerVerified", false),
            categoryId = o.optString("categoryId", "cat_other").ifBlank { "cat_other" },
            categoryNameBn = o.optString("categoryNameBn", ""),
            nameBn = nameBn,
            nameEn = o.optString("nameEn", nameBn).ifBlank { nameBn },
            descriptionBn = o.optString("descriptionBn", ""),
            imageUri = o.optString("imageUri", ""),
            availableQuantity = o.optDouble("availableQuantity", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            unit = "মণ",
            pricePerUnit = o.optDouble("pricePerUnit", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            harvestDate = o.optString("harvestDate", ""),
            availabilityDate = o.optString("availabilityDate", ""),
            division = o.optString("division", "ঢাকা"),
            district = o.optString("district", "ঢাকা"),
            upazila = o.optString("upazila", ""),
            unionName = o.optString("unionName", ""),
            village = o.optString("village", ""),
            pickupAddress = o.optString("pickupAddress", ""),
            supportsFarmPickup = o.optBoolean("supportsFarmPickup", true),
            supportsDirectDelivery = o.optBoolean("supportsDirectDelivery", true),
            deliveryChargeEstimate = o.optDouble("deliveryChargeEstimate", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            isWholesale = o.optBoolean("isWholesale", o.optBoolean("wholesale", false)),
            isPopular = o.optBoolean("isPopular", o.optBoolean("popular", false)),
            status = o.optString("status", "ACTIVE"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
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
        "paymentSenderNumber" to o.paymentSenderNumber,
        "paymentReceiptUri" to o.paymentReceiptUri,
        "farmerPaymentConfirmed" to o.farmerPaymentConfirmed,
        "farmerPaymentConfirmedAt" to o.farmerPaymentConfirmedAt,
        "adminPaymentVerified" to o.adminPaymentVerified,
        "adminPaymentVerifiedBy" to o.adminPaymentVerifiedBy,
        "adminPaymentVerifiedAt" to o.adminPaymentVerifiedAt,
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
            isDemoRecord = false,
            paymentSenderNumber = s.str("paymentSenderNumber") ?: "",
            paymentReceiptUri = s.str("paymentReceiptUri") ?: "",
            farmerPaymentConfirmed = s.bool("farmerPaymentConfirmed") ?: false,
            farmerPaymentConfirmedAt = s.lng("farmerPaymentConfirmedAt") ?: 0L,
            adminPaymentVerified = s.bool("adminPaymentVerified") ?: false,
            adminPaymentVerifiedBy = s.str("adminPaymentVerifiedBy") ?: "",
            adminPaymentVerifiedAt = s.lng("adminPaymentVerifiedAt") ?: 0L
        )
    }

    private fun jsonToOrder(key: String, o: JSONObject): OrderEntity? {
        val id = o.optString("id", key).ifBlank { key }
        val orderCode = o.optString("orderCode", "")
        if (orderCode.isBlank()) return null
        return OrderEntity(
            id = id,
            orderCode = orderCode,
            buyerId = o.optString("buyerId", ""),
            buyerName = o.optString("buyerName", ""),
            buyerPhone = o.optString("buyerPhone", ""),
            farmerId = o.optString("farmerId", ""),
            farmerName = o.optString("farmerName", ""),
            farmerPhone = o.optString("farmerPhone", ""),
            status = o.optString("status", "PENDING"),
            deliveryOption = o.optString("deliveryOption", "FARM_PICKUP"),
            deliveryAddress = o.optString("deliveryAddress", ""),
            division = o.optString("division", "ঢাকা"),
            district = o.optString("district", "ঢাকা"),
            upazila = o.optString("upazila", ""),
            productTotal = o.optDouble("productTotal", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            deliveryCharge = o.optDouble("deliveryCharge", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            grandTotal = o.optDouble("grandTotal", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            paymentMethod = o.optString("paymentMethod", "CASH_ON_PICKUP"),
            transactionReference = o.optString("transactionReference", ""),
            paymentStatus = o.optString("paymentStatus", "PENDING"),
            itemsSummary = o.optString("itemsSummary", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            isDemoRecord = false,
            paymentSenderNumber = o.optString("paymentSenderNumber", ""),
            paymentReceiptUri = o.optString("paymentReceiptUri", ""),
            farmerPaymentConfirmed = o.optBoolean("farmerPaymentConfirmed", false),
            farmerPaymentConfirmedAt = o.optLong("farmerPaymentConfirmedAt", 0L),
            adminPaymentVerified = o.optBoolean("adminPaymentVerified", false),
            adminPaymentVerifiedBy = o.optString("adminPaymentVerifiedBy", ""),
            adminPaymentVerifiedAt = o.optLong("adminPaymentVerifiedAt", 0L)
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
            unit = s.str("unit") ?: "মণ",
            quantity = s.dbl("quantity") ?: 0.0,
            pricePerUnit = s.dbl("pricePerUnit") ?: 0.0,
            lineTotal = s.dbl("lineTotal") ?: 0.0
        )
    }

    private fun jsonToOrderItem(key: String, o: JSONObject, orderId: String): OrderItemEntity? {
        val id = o.optString("id", key).ifBlank { key }
        val productId = o.optString("productId", "")
        if (productId.isBlank()) return null
        return OrderItemEntity(
            id = id,
            orderId = o.optString("orderId", orderId).ifBlank { orderId },
            productId = productId,
            productNameBn = o.optString("productNameBn", ""),
            unit = o.optString("unit", "মণ"),
            quantity = o.optDouble("quantity", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            pricePerUnit = o.optDouble("pricePerUnit", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            lineTotal = o.optDouble("lineTotal", 0.0).takeUnless { it.isNaN() } ?: 0.0
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

    private fun jsonToNotification(key: String, o: JSONObject): NotificationEntity? {
        val id = o.optString("id", key).ifBlank { key }
        val titleBn = o.optString("titleBn", "")
        if (titleBn.isBlank()) return null
        return NotificationEntity(
            id = id,
            userId = o.optString("userId", "ALL"),
            targetRole = o.optString("targetRole", ""),
            senderRole = o.optString("senderRole", "SYSTEM"),
            orderId = o.optString("orderId", ""),
            type = o.optString("type", "SYSTEM"),
            titleBn = titleBn,
            messageBn = o.optString("messageBn", ""),
            isRead = o.optBoolean("isRead", o.optBoolean("read", false)),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
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

    private fun jsonToReview(key: String, o: JSONObject): ReviewEntity {
        val id = o.optString("id", key).ifBlank { key }
        return ReviewEntity(
            id = id,
            orderId = o.optString("orderId", ""),
            productId = o.optString("productId", ""),
            farmerId = o.optString("farmerId", ""),
            buyerId = o.optString("buyerId", ""),
            buyerName = o.optString("buyerName", ""),
            rating = o.optInt("rating", 5),
            commentBn = o.optString("commentBn", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
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

    private fun jsonToComplaint(key: String, o: JSONObject): ComplaintEntity {
        val id = o.optString("id", key).ifBlank { key }
        return ComplaintEntity(
            id = id,
            orderId = o.optString("orderId", ""),
            reporterId = o.optString("reporterId", ""),
            reporterName = o.optString("reporterName", ""),
            subjectBn = o.optString("subjectBn", ""),
            descriptionBn = o.optString("descriptionBn", ""),
            status = o.optString("status", "OPEN"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
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

    private fun jsonToAuditLog(key: String, o: JSONObject): AuditLogEntity {
        val id = o.optString("id", key).ifBlank { key }
        return AuditLogEntity(
            id = id,
            actorId = o.optString("actorId", ""),
            actorRole = o.optString("actorRole", ""),
            actionBn = o.optString("actionBn", ""),
            targetEntity = o.optString("targetEntity", ""),
            targetId = o.optString("targetId", ""),
            detailsBn = o.optString("detailsBn", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun adConfigToMap(a: AdConfig): Map<String, Any?> = mapOf(
        "adsEnabled" to a.adsEnabled,
        "showAdsLabel" to a.showAdsLabel,
        "androidAdsEnabled" to a.androidAdsEnabled,
        "androidBannerId" to a.androidBannerId,
        "androidInterstitialId" to a.androidInterstitialId,
        "mobileWebAdsEnabled" to a.mobileWebAdsEnabled,
        "mobileAdNetworkPriority" to a.mobileAdNetworkPriority,
        "mobileCustomAdHtml" to a.mobileCustomAdHtml,
        "mobileDirectLinkUrl" to a.mobileDirectLinkUrl,
        "adsenseEnabled" to a.adsenseEnabled,
        "adsenseClient" to a.adsenseClient,
        "adsenseTopSlot" to a.adsenseTopSlot,
        "adsenseFeedSlot" to a.adsenseFeedSlot,
        "adsenseFooterSlot" to a.adsenseFooterSlot,
        "adsterraEnabled" to a.adsterraEnabled,
        "adsterraBannerKey" to a.adsterraBannerKey,
        "adsterraScriptUrl" to a.adsterraScriptUrl,
        "adsterraSocialBarUrl" to a.adsterraSocialBarUrl,
        "monetagEnabled" to a.monetagEnabled,
        "monetagZoneId" to a.monetagZoneId,
        "monetagScriptUrl" to a.monetagScriptUrl,
        "customAdEnabled" to a.customAdEnabled,
        "customAdName" to a.customAdName,
        "customTopHtml" to a.customTopHtml,
        "customFeedHtml" to a.customFeedHtml,
        "customFooterHtml" to a.customFooterHtml,
        "sponsorEnabled" to a.sponsorEnabled,
        "sponsorTitle" to a.sponsorTitle,
        "sponsorSubtitle" to a.sponsorSubtitle,
        "sponsorImageUrl" to a.sponsorImageUrl,
        "sponsorTargetUrl" to a.sponsorTargetUrl,
        "sponsorCtaText" to a.sponsorCtaText,
        "updatedAt" to a.updatedAt
    )

    private fun snapToAdConfig(s: DataSnapshot): AdConfig? = runCatching {
        AdConfig(
            adsEnabled = s.bool("adsEnabled") ?: true,
            showAdsLabel = s.bool("showAdsLabel") ?: true,
            androidAdsEnabled = s.bool("androidAdsEnabled") ?: true,
            androidBannerId = s.str("androidBannerId") ?: "",
            androidInterstitialId = s.str("androidInterstitialId") ?: "",
            mobileWebAdsEnabled = s.bool("mobileWebAdsEnabled") ?: true,
            mobileAdNetworkPriority = s.str("mobileAdNetworkPriority") ?: "ALL",
            mobileCustomAdHtml = s.str("mobileCustomAdHtml") ?: "",
            mobileDirectLinkUrl = s.str("mobileDirectLinkUrl") ?: "",
            adsenseEnabled = s.bool("adsenseEnabled") ?: false,
            adsenseClient = s.str("adsenseClient") ?: "",
            adsenseTopSlot = s.str("adsenseTopSlot") ?: "",
            adsenseFeedSlot = s.str("adsenseFeedSlot") ?: "",
            adsenseFooterSlot = s.str("adsenseFooterSlot") ?: "",
            adsterraEnabled = s.bool("adsterraEnabled") ?: false,
            adsterraBannerKey = s.str("adsterraBannerKey") ?: "",
            adsterraScriptUrl = s.str("adsterraScriptUrl") ?: "",
            adsterraSocialBarUrl = s.str("adsterraSocialBarUrl") ?: "",
            monetagEnabled = s.bool("monetagEnabled") ?: false,
            monetagZoneId = s.str("monetagZoneId") ?: "",
            monetagScriptUrl = s.str("monetagScriptUrl") ?: "",
            customAdEnabled = s.bool("customAdEnabled") ?: false,
            customAdName = s.str("customAdName") ?: "Ezoic / Media.net / Custom",
            customTopHtml = s.str("customTopHtml") ?: "",
            customFeedHtml = s.str("customFeedHtml") ?: "",
            customFooterHtml = s.str("customFooterHtml") ?: "",
            sponsorEnabled = s.bool("sponsorEnabled") ?: true,
            sponsorTitle = s.str("sponsorTitle") ?: "সরাসরি কৃষকের বীজ, সার ও আধুনিক কৃষি পরামর্শ",
            sponsorSubtitle = s.str("sponsorSubtitle") ?: "কৃষি বন্ধু স্পন্সর স্লট — অ্যাডমিন প্যানেল থেকে কাস্টম বিজ্ঞাপন বা ব্যানার যুক্ত করুন।",
            sponsorImageUrl = s.str("sponsorImageUrl") ?: "",
            sponsorTargetUrl = s.str("sponsorTargetUrl") ?: "",
            sponsorCtaText = s.str("sponsorCtaText") ?: "বিস্তারিত দেখুন",
            updatedAt = s.lng("updatedAt") ?: 0L
        )
    }.getOrNull()

    private fun jsonToAdConfig(o: JSONObject): AdConfig? = runCatching {
        AdConfig(
            adsEnabled = o.optBoolean("adsEnabled", true),
            showAdsLabel = o.optBoolean("showAdsLabel", true),
            androidAdsEnabled = o.optBoolean("androidAdsEnabled", true),
            androidBannerId = o.optString("androidBannerId", ""),
            androidInterstitialId = o.optString("androidInterstitialId", ""),
            mobileWebAdsEnabled = o.optBoolean("mobileWebAdsEnabled", true),
            mobileAdNetworkPriority = o.optString("mobileAdNetworkPriority", "ALL"),
            mobileCustomAdHtml = o.optString("mobileCustomAdHtml", ""),
            mobileDirectLinkUrl = o.optString("mobileDirectLinkUrl", ""),
            adsenseEnabled = o.optBoolean("adsenseEnabled", false),
            adsenseClient = o.optString("adsenseClient", ""),
            adsenseTopSlot = o.optString("adsenseTopSlot", ""),
            adsenseFeedSlot = o.optString("adsenseFeedSlot", ""),
            adsenseFooterSlot = o.optString("adsenseFooterSlot", ""),
            adsterraEnabled = o.optBoolean("adsterraEnabled", false),
            adsterraBannerKey = o.optString("adsterraBannerKey", ""),
            adsterraScriptUrl = o.optString("adsterraScriptUrl", ""),
            adsterraSocialBarUrl = o.optString("adsterraSocialBarUrl", ""),
            monetagEnabled = o.optBoolean("monetagEnabled", false),
            monetagZoneId = o.optString("monetagZoneId", ""),
            monetagScriptUrl = o.optString("monetagScriptUrl", ""),
            customAdEnabled = o.optBoolean("customAdEnabled", false),
            customAdName = o.optString("customAdName", "Ezoic / Media.net / Custom"),
            customTopHtml = o.optString("customTopHtml", ""),
            customFeedHtml = o.optString("customFeedHtml", ""),
            customFooterHtml = o.optString("customFooterHtml", ""),
            sponsorEnabled = o.optBoolean("sponsorEnabled", true),
            sponsorTitle = o.optString("sponsorTitle", "সরাসরি কৃষকের বীজ, সার ও আধুনিক কৃষি পরামর্শ"),
            sponsorSubtitle = o.optString("sponsorSubtitle", "কৃষি বন্ধু স্পন্সর স্লট — অ্যাডমিন প্যানেল থেকে কাস্টম বিজ্ঞাপন বা ব্যানার যুক্ত করুন।"),
            sponsorImageUrl = o.optString("sponsorImageUrl", ""),
            sponsorTargetUrl = o.optString("sponsorTargetUrl", ""),
            sponsorCtaText = o.optString("sponsorCtaText", "বিস্তারিত দেখুন"),
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }.getOrNull()

    private fun activationPaymentToMap(p: ActivationPaymentEntity): Map<String, Any?> = mapOf(
        "id" to p.id,
        "userId" to p.userId,
        "userName" to p.userName,
        "userPhone" to p.userPhone,
        "accountRole" to p.accountRole,
        "paymentType" to p.paymentType,
        "paymentMethod" to p.paymentMethod,
        "receiverNumber" to p.receiverNumber,
        "receiverAccountType" to p.receiverAccountType,
        "senderMobile" to p.senderMobile,
        "transactionId" to p.transactionId,
        "amount" to p.amount,
        "receiptUri" to p.receiptUri,
        "status" to p.status,
        "adminNotes" to p.adminNotes,
        "reviewedBy" to p.reviewedBy,
        "reviewedAt" to p.reviewedAt,
        "createdAt" to p.createdAt,
        "updatedAt" to p.updatedAt
    )

    private fun snapToActivationPayment(s: DataSnapshot): ActivationPaymentEntity? {
        val id = s.str("id") ?: s.key ?: return null
        val userId = s.str("userId") ?: return null
        return ActivationPaymentEntity(
            id = id,
            userId = userId,
            userName = s.str("userName") ?: "",
            userPhone = s.str("userPhone") ?: "",
            accountRole = s.str("accountRole") ?: "BUYER",
            paymentType = s.str("paymentType") ?: "ACTIVATION",
            paymentMethod = s.str("paymentMethod") ?: "BKASH",
            receiverNumber = s.str("receiverNumber") ?: "01756144900",
            receiverAccountType = s.str("receiverAccountType") ?: "PERSONAL",
            senderMobile = s.str("senderMobile") ?: "",
            transactionId = s.str("transactionId") ?: "",
            amount = s.dbl("amount") ?: 300.0,
            receiptUri = s.str("receiptUri") ?: "",
            status = s.str("status") ?: "PENDING",
            adminNotes = s.str("adminNotes") ?: "",
            reviewedBy = s.str("reviewedBy") ?: "",
            reviewedAt = s.lng("reviewedAt") ?: 0L,
            createdAt = s.lng("createdAt") ?: System.currentTimeMillis(),
            updatedAt = s.lng("updatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun jsonToActivationPayment(key: String, o: JSONObject): ActivationPaymentEntity? {
        val id = o.optString("id", key).ifBlank { key }
        val userId = o.optString("userId", "")
        if (userId.isBlank()) return null
        return ActivationPaymentEntity(
            id = id,
            userId = userId,
            userName = o.optString("userName", ""),
            userPhone = o.optString("userPhone", ""),
            accountRole = o.optString("accountRole", "BUYER"),
            paymentType = o.optString("paymentType", "ACTIVATION"),
            paymentMethod = o.optString("paymentMethod", "BKASH"),
            receiverNumber = o.optString("receiverNumber", "01756144900"),
            receiverAccountType = o.optString("receiverAccountType", "PERSONAL"),
            senderMobile = o.optString("senderMobile", ""),
            transactionId = o.optString("transactionId", ""),
            amount = o.optDouble("amount", 300.0).takeUnless { it.isNaN() } ?: 300.0,
            receiptUri = o.optString("receiptUri", ""),
            status = o.optString("status", "PENDING"),
            adminNotes = o.optString("adminNotes", ""),
            reviewedBy = o.optString("reviewedBy", ""),
            reviewedAt = o.optLong("reviewedAt", 0L),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun paymentSettingsToMap(ps: PaymentSettingsEntity): Map<String, Any?> = mapOf(
        "id" to ps.id,
        "bKashNumber" to ps.bKashNumber,
        "bKashAccountType" to ps.bKashAccountType,
        "nagadNumber" to ps.nagadNumber,
        "nagadAccountType" to ps.nagadAccountType,
        "farmerFee" to ps.farmerFee,
        "buyerFee" to ps.buyerFee,
        "renewalFee" to ps.renewalFee,
        "validityYears" to ps.validityYears,
        "paymentInstructions" to ps.paymentInstructions,
        "notify30Days" to ps.notify30Days,
        "notify7Days" to ps.notify7Days,
        "notifyOnExpiry" to ps.notifyOnExpiry,
        "updatedAt" to ps.updatedAt
    )

    private fun snapToPaymentSettings(s: DataSnapshot): PaymentSettingsEntity? = runCatching {
        PaymentSettingsEntity(
            id = "default",
            bKashNumber = s.str("bKashNumber") ?: "01756144900",
            bKashAccountType = s.str("bKashAccountType") ?: "PERSONAL",
            nagadNumber = s.str("nagadNumber") ?: "01756144900",
            nagadAccountType = s.str("nagadAccountType") ?: "PERSONAL",
            farmerFee = s.dbl("farmerFee") ?: 300.0,
            buyerFee = s.dbl("buyerFee") ?: 300.0,
            renewalFee = s.dbl("renewalFee") ?: 300.0,
            validityYears = (s.lng("validityYears") ?: 2L).toInt().coerceAtLeast(1),
            paymentInstructions = s.str("paymentInstructions") ?: PaymentSettingsEntity().paymentInstructions,
            notify30Days = s.bool("notify30Days") ?: true,
            notify7Days = s.bool("notify7Days") ?: true,
            notifyOnExpiry = s.bool("notifyOnExpiry") ?: true,
            updatedAt = s.lng("updatedAt") ?: 0L
        )
    }.getOrNull()

    private fun jsonToPaymentSettings(o: JSONObject): PaymentSettingsEntity? = runCatching {
        PaymentSettingsEntity(
            id = "default",
            bKashNumber = o.optString("bKashNumber", "01756144900").ifBlank { "01756144900" },
            bKashAccountType = o.optString("bKashAccountType", "PERSONAL").ifBlank { "PERSONAL" },
            nagadNumber = o.optString("nagadNumber", "01756144900").ifBlank { "01756144900" },
            nagadAccountType = o.optString("nagadAccountType", "PERSONAL").ifBlank { "PERSONAL" },
            farmerFee = o.optDouble("farmerFee", 300.0).takeUnless { it.isNaN() } ?: 300.0,
            buyerFee = o.optDouble("buyerFee", 300.0).takeUnless { it.isNaN() } ?: 300.0,
            renewalFee = o.optDouble("renewalFee", 300.0).takeUnless { it.isNaN() } ?: 300.0,
            validityYears = o.optInt("validityYears", 2).coerceAtLeast(1),
            paymentInstructions = o.optString("paymentInstructions", PaymentSettingsEntity().paymentInstructions),
            notify30Days = o.optBoolean("notify30Days", true),
            notify7Days = o.optBoolean("notify7Days", true),
            notifyOnExpiry = o.optBoolean("notifyOnExpiry", true),
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }.getOrNull()

    private fun DataSnapshot.str(key: String): String? = child(key).getValue(String::class.java)
    private fun DataSnapshot.bool(key: String): Boolean? = child(key).getValue(Boolean::class.java)
    private fun DataSnapshot.dbl(key: String): Double? = (child(key).value as? Number)?.toDouble()
    private fun DataSnapshot.lng(key: String): Long? = (child(key).value as? Number)?.toLong()
}
