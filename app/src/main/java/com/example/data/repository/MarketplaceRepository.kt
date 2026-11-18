package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.FirebaseRealtimeSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.Calendar
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
        const val FIXED_ACTIVATION_FEE_BDT = 300.0
        const val FIXED_VALIDITY_YEARS = 2
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
    val activationPayments: Flow<List<ActivationPaymentEntity>> = db.activationPaymentDao().observeAllPayments()
    val paymentSettings: Flow<PaymentSettingsEntity> = db.paymentSettingsDao().observeSettings().map {
        it ?: PaymentSettingsEntity()
    }
    val farmerPaymentSettings: Flow<List<FarmerPaymentSettingsEntity>> = db.farmerPaymentSettingsDao().observeAll()
    val orderPayments: Flow<List<OrderPaymentEntity>> = db.orderPaymentDao().observeAllPayments()

    private val _adConfig = MutableStateFlow(AdConfig())
    val adConfig: StateFlow<AdConfig> = _adConfig.asStateFlow()

    fun observeFarmerPaymentSettings(farmerId: String): Flow<FarmerPaymentSettingsEntity?> =
        db.farmerPaymentSettingsDao().observeForFarmer(farmerId)

    fun observeOrderPayments(orderId: String): Flow<List<OrderPaymentEntity>> =
        db.orderPaymentDao().observePaymentsForOrder(orderId)

    suspend fun getFarmerPaymentSettingsOnce(farmerId: String): FarmerPaymentSettingsEntity? =
        db.farmerPaymentSettingsDao().getForFarmerOnce(farmerId)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    fun observeOrderItems(orderId: String): Flow<List<OrderItemEntity>> =
        db.orderDao().observeOrderItems(orderId)

    private fun normalizeTransactionId(txId: String): String =
        txId.trim().replace(Regex("\\s+"), "").uppercase()

    private fun addCalendarYears(baseMillis: Long, years: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = baseMillis
            add(Calendar.YEAR, years.coerceAtLeast(1))
        }
        return cal.timeInMillis
    }

    suspend fun getEffectivePaymentSettings(): PaymentSettingsEntity {
        return db.paymentSettingsDao().getSettingsOnce() ?: PaymentSettingsEntity()
    }

    suspend fun requireActiveMarketplaceUser(
        user: UserEntity?,
        actionBn: String = "এই কাজটি করার",
        now: Long = System.currentTimeMillis()
    ): Result<UserEntity> {
        if (user == null) {
            return Result.failure(IllegalStateException("$actionBn জন্য অনুগ্রহ করে লগইন বা নিবন্ধন করুন।"))
        }
        if (user.role == UserRole.ADMIN.name) {
            return Result.success(user)
        }
        // Refresh from DB if available to ensure latest status
        val latest = db.userDao().getUserById(user.id) ?: user
        if (latest.accountStatus == AccountStatus.ACTIVE.name && latest.expiryDate > 0L && latest.expiryDate <= now) {
            evaluateAccountExpiries(now)
            return Result.failure(
                IllegalStateException("আপনার ২ বছরের অ্যাকাউন্টের মেয়াদ শেষ হয়েছে। পুনরায় সক্রিয় করতে ৩০০ টাকা দিয়ে রিনিউ করুন।")
            )
        }
        return when (latest.accountStatus) {
            AccountStatus.ACTIVE.name -> Result.success(latest)
            AccountStatus.PAYMENT_UNDER_REVIEW.name -> Result.failure(
                IllegalStateException("আপনার অ্যাক্টিভেশন পেমেন্টটি অ্যাডমিন যাচাইয়ের অপেক্ষায় রয়েছে। অনুমোদনের পর $actionBn অনুমতি পাবেন।")
            )
            AccountStatus.PAYMENT_REJECTED.name -> Result.failure(
                IllegalStateException("আপনার পেমেন্ট যাচাই প্রত্যাখ্যাত হয়েছে। অনুগ্রহ করে সঠিক তথ্য দিয়ে পুনরায় পেমেন্ট জমা দিন।")
            )
            AccountStatus.EXPIRED.name -> Result.failure(
                IllegalStateException("আপনার অ্যাকাউন্টের ২ বছরের মেয়াদ শেষ হয়েছে। অনুগ্রহ করে ৩০০ টাকা দিয়ে রিনিউ করুন।")
            )
            AccountStatus.SUSPENDED.name -> Result.failure(
                IllegalStateException("আপনার অ্যাকাউন্টটি অ্যাডমিন কর্তৃক স্থগিত (Suspended) রয়েছে।")
            )
            else -> Result.failure(
                IllegalStateException("$actionBn জন্য আপনার অ্যাকাউন্টটি সক্রিয় নয়। অনুগ্রহ করে ৩০০ টাকা (২ বছরের মেয়াদ) অ্যাক্টিভেশন ফি জমা দিন।")
            )
        }
    }

    suspend fun initializeCatalogsAndSync(
        scope: CoroutineScope? = null,
        onIncomingNotification: (NotificationEntity) -> Unit = {}
    ) {
        MasterCatalogSeeder.initialize(db)
        if (scope != null) {
            rtdbSync.startRealtimeListeners(
                localDb = db,
                scope = scope,
                onAdConfigChanged = { _adConfig.value = it },
                onSyncStateChanged = { syncing, err ->
                    _isSyncing.value = syncing
                    _syncError.value = err
                },
                onIncomingNotification = onIncomingNotification
            )
        }
        evaluateAccountExpiries()
    }

    suspend fun setLanguage(language: AppLanguage) = prefs.setLanguage(language)
    suspend fun setDarkMode(enabled: Boolean) = prefs.setDarkMode(enabled)

    suspend fun logoutUser() {
        prefs.setActiveUserId("")
    }

    suspend fun syncNow(): Boolean {
        _isSyncing.value = true
        val ok = rtdbSync.pullAllFromRest(db, onAdConfigChanged = { _adConfig.value = it })
        _isSyncing.value = false
        _syncError.value = if (ok) null else "সার্ভারের সাথে সংযোগ করতে সমস্যা হয়েছে। ইন্টারনেট চালু করে পুনরায় চেষ্টা করুন।"
        if (ok) {
            evaluateAccountExpiries()
        }
        return ok
    }

    suspend fun evaluateAccountExpiries(now: Long = System.currentTimeMillis()) {
        val settings = getEffectivePaymentSettings()
        val users = db.userDao().getAllUsersOnce()
        users.filter { it.role != UserRole.ADMIN.name && !it.isDemoRecord }.forEach { user ->
            if (user.accountStatus == AccountStatus.ACTIVE.name && user.expiryDate > 0L) {
                val cycleKey = user.expiryDate
                if (user.expiryDate <= now) {
                    val expiredUser = user.copy(accountStatus = AccountStatus.EXPIRED.name)
                    db.userDao().upsertUser(expiredUser)
                    rtdbSync.pushUser(expiredUser)
                    if (settings.notifyOnExpiry) {
                        val notifId = "exp_${user.id}_${cycleKey}_0d"
                        if (db.notificationAuditDao().getNotificationById(notifId) == null) {
                            val notif = NotificationEntity(
                                id = notifId,
                                userId = user.id,
                                targetRole = user.role,
                                senderRole = "SYSTEM",
                                type = "ACCOUNT_EXPIRED",
                                titleBn = "অ্যাকাউন্টের ২ বছরের মেয়াদ শেষ হয়েছে",
                                messageBn = "আপনার কৃষি বন্ধু অ্যাকাউন্টের মেয়াদ শেষ হওয়ায় কেনাবেচা সাময়িকভাবে বন্ধ রয়েছে। পুনরায় ২ বছরের জন্য সক্রিয় করতে ৩০০ টাকা রিনিউয়াল পেমেন্ট জমা দিন।",
                                isRead = false,
                                createdAt = now
                            )
                            db.notificationAuditDao().insertNotification(notif)
                            rtdbSync.pushNotification(notif)
                        }
                    }
                } else {
                    val daysLeft = user.daysUntilExpiry(now) ?: Int.MAX_VALUE
                    if (settings.notify7Days && daysLeft <= 7) {
                        val notifId = "exp_${user.id}_${cycleKey}_7d"
                        if (db.notificationAuditDao().getNotificationById(notifId) == null) {
                            val notif = NotificationEntity(
                                id = notifId,
                                userId = user.id,
                                targetRole = user.role,
                                senderRole = "SYSTEM",
                                type = "EXPIRY_REMINDER_7D",
                                titleBn = "মেয়াদ শেষ হতে আর মাত্র $daysLeft দিন বাকি",
                                messageBn = "নিরবচ্ছিন্নভাবে ফসল বিক্রি ও অর্ডার করতে আগামী $daysLeft দিনের মধ্যে ৩০০ টাকা দিয়ে ২ বছরের রিনিউ সম্পন্ন করুন।",
                                isRead = false,
                                createdAt = now
                            )
                            db.notificationAuditDao().insertNotification(notif)
                            rtdbSync.pushNotification(notif)
                        }
                    } else if (settings.notify30Days && daysLeft <= 30) {
                        val notifId = "exp_${user.id}_${cycleKey}_30d"
                        if (db.notificationAuditDao().getNotificationById(notifId) == null) {
                            val notif = NotificationEntity(
                                id = notifId,
                                userId = user.id,
                                targetRole = user.role,
                                senderRole = "SYSTEM",
                                type = "EXPIRY_REMINDER_30D",
                                titleBn = "অ্যাকাউন্টের মেয়াদ ৩০ দিনের মধ্যে শেষ হবে",
                                messageBn = "আপনার ২ বছরের অ্যাকাউন্টের মেয়াদ আর $daysLeft দিন পর শেষ হবে। সময়মতো ৩০০ টাকা দিয়ে রিনিউ করে নিন।",
                                isRead = false,
                                createdAt = now
                            )
                            db.notificationAuditDao().insertNotification(notif)
                            rtdbSync.pushNotification(notif)
                        }
                    }
                }
            }
        }
    }

    /**
     * Authenticates users:
     * 1. Superadmin (`id = admin`, `password = bondhu`) works offline/locally even if not in Firebase.
     * 2. Standard Farmers and Buyers check normalized phone in Room AND Firebase Realtime Database
     *    (re-checking remote if cached password hash differs, e.g. after Website password reset).
     */
    suspend fun loginUser(identifier: String, rawPassword: String): Result<UserEntity> {
        val cleanId = SecurityHashUtil.normalizePhone(identifier)
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
                isDemoRecord = false,
                accountStatus = AccountStatus.ACTIVE.name,
                activationDate = System.currentTimeMillis(),
                expiryDate = 0L,
                validityYears = FIXED_VALIDITY_YEARS,
                lastPaymentId = ""
            )
            db.userDao().upsertUser(adminUser)
            prefs.setActiveUserId(adminUser.id)
            return Result.success(adminUser)
        }

        val expectedHash = SecurityHashUtil.hashPassword(rawPassword)
        val user = when (val lookup = rtdbSync.fetchUserByPhoneStrict(cleanId, db)) {
            is FirebaseRealtimeSyncManager.RemoteUserLookup.Found -> {
                db.userDao().upsertUser(lookup.user)
                lookup.user
            }
            is FirebaseRealtimeSyncManager.RemoteUserLookup.NotFound -> {
                db.userDao().getAllUsersOnce()
                    .filter { it.id != SUPERADMIN_USER_ID && SecurityHashUtil.normalizePhone(it.phone) == cleanId }
                    .forEach { db.userDao().deleteNonAdminUserById(it.id) }
                null
            }
            is FirebaseRealtimeSyncManager.RemoteUserLookup.Unreachable -> {
                return Result.failure(IllegalStateException("অনলাইন সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। ইন্টারনেট সংযোগ চালু করে আবার চেষ্টা করুন।"))
            }
            is FirebaseRealtimeSyncManager.RemoteUserLookup.Disabled -> {
                db.userDao().getAllUsersOnce()
                    .firstOrNull { SecurityHashUtil.normalizePhone(it.phone) == cleanId }
            }
        }
        if (user == null) {
            return Result.failure(IllegalArgumentException("এই মোবাইল নম্বরে কোনো অ্যাকাউন্ট পাওয়া যায়নি।"))
        }
        if (user.passwordHash != expectedHash) {
            return Result.failure(IllegalArgumentException("পাসওয়ার্ড সঠিক নয়।"))
        }
        prefs.setActiveUserId(user.id)
        evaluateAccountExpiries()
        val refreshed = db.userDao().getUserById(user.id) ?: user
        return Result.success(refreshed)
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
        farmBio: String,
        confirmPassword: String = rawPassword
    ): Result<UserEntity> {
        val cleanPhone = SecurityHashUtil.normalizePhone(phone)
        if (fullName.isBlank() || cleanPhone.length < 10 || rawPassword.length < 6) {
            return Result.failure(IllegalArgumentException("নাম, সঠিক মোবাইল নম্বর ও কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন।"))
        }
        if (rawPassword != confirmPassword) {
            return Result.failure(IllegalArgumentException("পাসওয়ার্ড এবং কনফার্ম পাসওয়ার্ড মেলেনি।"))
        }
        if (cleanPhone.equals(SUPERADMIN_LOGIN_ID, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("এই আইডি ব্যবহার করা যাবে না।"))
        }
        if (role == UserRole.ADMIN) {
            return Result.failure(IllegalArgumentException("অ্যাডমিন হিসেবে সরাসরি নিবন্ধন করা যাবে না।"))
        }
        when (rtdbSync.fetchUserByPhoneStrict(cleanPhone, db)) {
            is FirebaseRealtimeSyncManager.RemoteUserLookup.Found -> {
                return Result.failure(IllegalArgumentException("এই মোবাইল নম্বরটি ইতোমধ্যে নিবন্ধিত।"))
            }
            is FirebaseRealtimeSyncManager.RemoteUserLookup.Unreachable -> {
                return Result.failure(IllegalStateException("অনলাইন সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। ইন্টারনেট সংযোগ চালু করে আবার চেষ্টা করুন।"))
            }
            is FirebaseRealtimeSyncManager.RemoteUserLookup.NotFound -> {
                db.userDao().getAllUsersOnce()
                    .filter { it.id != SUPERADMIN_USER_ID && SecurityHashUtil.normalizePhone(it.phone) == cleanPhone }
                    .forEach { db.userDao().deleteNonAdminUserById(it.id) }
            }
            is FirebaseRealtimeSyncManager.RemoteUserLookup.Disabled -> {
                val existsLocally = db.userDao().getAllUsersOnce()
                    .any { SecurityHashUtil.normalizePhone(it.phone) == cleanPhone }
                if (existsLocally) {
                    return Result.failure(IllegalArgumentException("এই মোবাইল নম্বরটি ইতোমধ্যে নিবন্ধিত।"))
                }
            }
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
            profileImageUri = "",
            verificationStatus = "PENDING",
            isMobileVerified = true,
            rating = 5.0,
            reviewCount = 0,
            isDemoRecord = false,
            accountStatus = AccountStatus.PAYMENT_PENDING.name,
            activationDate = 0L,
            expiryDate = 0L,
            validityYears = FIXED_VALIDITY_YEARS,
            lastPaymentId = ""
        )
        db.userDao().upsertUser(user)
        rtdbSync.pushUser(user)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = uid,
            actorRole = role.name,
            actionBn = "নতুন অ্যাকাউন্ট নিবন্ধন (পেমেন্ট অপেক্ষমান)",
            targetEntity = "User",
            targetId = uid,
            detailsBn = "${user.fullName} (${role.titleBn}) — ${user.upazila}, ${user.district}, ${user.division}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        prefs.setActiveUserId(uid)
        return Result.success(user)
    }

    suspend fun submitActivationPayment(
        user: UserEntity,
        paymentMethod: PaymentMethod,
        senderMobile: String,
        transactionId: String,
        amount: Double,
        receiptUri: String,
        isRenewal: Boolean = false
    ): Result<ActivationPaymentEntity> {
        if (user.role == UserRole.ADMIN.name) {
            return Result.failure(IllegalArgumentException("অ্যাডমিন অ্যাকাউন্টের জন্য অ্যাক্টিভেশন ফি প্রযোজ্য নয়।"))
        }
        if (paymentMethod != PaymentMethod.BKASH && paymentMethod != PaymentMethod.NAGAD) {
            return Result.failure(IllegalArgumentException("অ্যাক্টিভেশনের জন্য শুধুমাত্র বিকাশ (bKash) বা নগদ (Nagad) নির্বাচন করুন।"))
        }
        val cleanSender = SecurityHashUtil.normalizePhone(senderMobile)
        if (cleanSender.length < 10) {
            return Result.failure(IllegalArgumentException("প্রেরকের সঠিক মোবাইল নম্বর (১১ ডিজিট) লিখুন।"))
        }
        val cleanTxId = normalizeTransactionId(transactionId)
        if (cleanTxId.length < 4) {
            return Result.failure(IllegalArgumentException("সঠিক ট্রানজেকশন আইডি (Transaction ID) লিখুন।"))
        }

        val settings = getEffectivePaymentSettings()
        val expectedFee = when {
            isRenewal -> settings.renewalFee
            user.role == UserRole.FARMER.name -> settings.farmerFee
            else -> settings.buyerFee
        }
        if (kotlin.math.abs(amount - expectedFee) > 0.01) {
            return Result.failure(
                IllegalArgumentException("নির্ধারিত ফি ${expectedFee.formatBdt()} (২ বছরের মেয়াদ) — পরিমাণ পরিবর্তন করা যাবে না।")
            )
        }

        // Pull latest remote activation payments before duplicate checking when online
        rtdbSync.fetchAllActivationPayments(db)
        val allPayments = db.activationPaymentDao().getAllPaymentsOnce()

        // Block duplicate pending submission for the same user
        if (allPayments.any { it.userId == user.id && it.status == ActivationPaymentStatus.PENDING.name }) {
            return Result.failure(
                IllegalStateException("আপনার একটি পেমেন্ট আবেদন ইতোমধ্যে যাচাইাধীন (Pending) রয়েছে। অ্যাডমিন অনুমোদন পর্যন্ত অপেক্ষা করুন।")
            )
        }

        // Strictly reject duplicate Transaction ID across ALL payment records
        val duplicateTx = allPayments.firstOrNull {
            normalizeTransactionId(it.transactionId) == cleanTxId
        }
        if (duplicateTx != null) {
            return Result.failure(
                IllegalArgumentException("এই ট্রানজেকশন আইডি ($cleanTxId) পূর্বেই ব্যবহার করা হয়েছে! অনুগ্রহ করে আপনার সঠিক ও নতুন Transaction ID দিন।")
            )
        }

        val receiverNumber = if (paymentMethod == PaymentMethod.BKASH) settings.bKashNumber else settings.nagadNumber
        val receiverType = if (paymentMethod == PaymentMethod.BKASH) settings.bKashAccountType else settings.nagadAccountType
        val now = System.currentTimeMillis()
        val paymentType = if (isRenewal || user.activationDate > 0L) "RENEWAL" else "ACTIVATION"
        val payment = ActivationPaymentEntity(
            id = "act_${UUID.randomUUID().toString().take(8)}",
            userId = user.id,
            userName = user.fullName,
            userPhone = user.phone,
            accountRole = user.role,
            paymentType = paymentType,
            paymentMethod = paymentMethod.name,
            receiverNumber = receiverNumber,
            receiverAccountType = receiverType,
            senderMobile = cleanSender,
            transactionId = cleanTxId,
            amount = expectedFee,
            receiptUri = receiptUri.trim(),
            status = ActivationPaymentStatus.PENDING.name,
            adminNotes = "",
            reviewedBy = "",
            reviewedAt = 0L,
            createdAt = now,
            updatedAt = now
        )

        val updatedUser = user.copy(
            accountStatus = if (user.accountStatus == AccountStatus.ACTIVE.name && isRenewal) {
                AccountStatus.ACTIVE.name // early renewal keeps current active window until approved
            } else {
                AccountStatus.PAYMENT_UNDER_REVIEW.name
            },
            lastPaymentId = payment.id
        )

        db.withTransaction {
            db.activationPaymentDao().upsertPayment(payment)
            db.userDao().upsertUser(updatedUser)
        }
        rtdbSync.pushActivationPayment(payment)
        rtdbSync.pushUser(updatedUser)

        val adminNotif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = "ALL",
            targetRole = UserRole.ADMIN.name,
            senderRole = user.role,
            type = "ACTIVATION_PAYMENT_NEW",
            titleBn = "নতুন অ্যাকাউন্ট ${if (paymentType == "RENEWAL") "রিনিউ" else "অ্যাক্টিভেশন"} পেমেন্ট (${expectedFee.formatBdt()})",
            messageBn = "${user.fullName} (${if (user.role == "FARMER") "কৃষক" else "ক্রেতা"}) — ${paymentMethod.labelEn} TxID: $cleanTxId, প্রেরক: $cleanSender।",
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(adminNotif)
        rtdbSync.pushNotification(adminNotif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = user.id,
            actorRole = user.role,
            actionBn = "অ্যাকাউন্ট অ্যাক্টিভেশন পেমেন্ট জমা",
            targetEntity = "ActivationPayment",
            targetId = payment.id,
            detailsBn = "${user.fullName} — ${paymentMethod.labelEn} ${expectedFee.formatBdt()} | TxID: $cleanTxId"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(payment)
    }

    suspend fun adminApproveActivationPayment(
        admin: UserEntity,
        paymentId: String,
        notes: String = "",
        now: Long = System.currentTimeMillis()
    ): Result<UserEntity> {
        if (admin.role != UserRole.ADMIN.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সুপার অ্যাডমিন পেমেন্ট অনুমোদন করতে পারেন।"))
        }
        val payment = db.activationPaymentDao().getPaymentById(paymentId)
            ?: return Result.failure(IllegalArgumentException("পেমেন্ট রেকর্ড পাওয়া যায়নি।"))
        if (payment.status == ActivationPaymentStatus.APPROVED.name) {
            return Result.failure(IllegalStateException("এই পেমেন্টটি ইতোমধ্যে অনুমোদিত।"))
        }
        val targetUser = db.userDao().getUserById(payment.userId)
            ?: return Result.failure(IllegalArgumentException("সংশ্লিষ্ট ব্যবহারকারীকে পাওয়া যায়নি।"))

        val settings = getEffectivePaymentSettings()
        val years = settings.validityYears.coerceAtLeast(FIXED_VALIDITY_YEARS)
        val hasExistingUnexpiredWindow = targetUser.accountStatus == AccountStatus.ACTIVE.name &&
            targetUser.expiryDate > now
        val newActivationDate = if (hasExistingUnexpiredWindow && targetUser.activationDate > 0L) {
            targetUser.activationDate
        } else {
            now
        }
        val newExpiryDate = if (hasExistingUnexpiredWindow) {
            addCalendarYears(targetUser.expiryDate, years)
        } else {
            addCalendarYears(now, years)
        }

        val approvedPayment = payment.copy(
            status = ActivationPaymentStatus.APPROVED.name,
            adminNotes = notes.trim(),
            reviewedBy = admin.id,
            reviewedAt = now,
            updatedAt = now
        )
        val activatedUser = targetUser.copy(
            accountStatus = AccountStatus.ACTIVE.name,
            verificationStatus = "VERIFIED",
            activationDate = newActivationDate,
            expiryDate = newExpiryDate,
            validityYears = years,
            lastPaymentId = payment.id
        )

        db.withTransaction {
            db.activationPaymentDao().upsertPayment(approvedPayment)
            db.userDao().upsertUser(activatedUser)
            if (targetUser.role == UserRole.FARMER.name) {
                db.productDao().updateFarmerVerifiedOnProducts(targetUser.id, true)
            }
        }

        rtdbSync.pushActivationPayment(approvedPayment)
        rtdbSync.pushUser(activatedUser)
        if (targetUser.role == UserRole.FARMER.name) {
            db.productDao().getProductsForFarmer(targetUser.id).forEach { rtdbSync.pushProduct(it) }
        }

        val notif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = targetUser.id,
            targetRole = targetUser.role,
            senderRole = UserRole.ADMIN.name,
            type = "ACTIVATION_APPROVED",
            titleBn = "অভিনন্দন! আপনার অ্যাকাউন্ট ২ বছরের জন্য সক্রিয় হয়েছে ✓",
            messageBn = "আপনার ${payment.amount.formatBdt()} (${payment.transactionId}) পেমেন্ট অনুমোদিত হয়েছে। এখন আপনি কৃষি বন্ধুর সব সুবিধা ব্যবহার করতে পারবেন।",
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(notif)
        rtdbSync.pushNotification(notif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "পেমেন্ট অনুমোদন ও ২ বছরের অ্যাকাউন্ট সক্রিয়",
            targetEntity = "ActivationPayment",
            targetId = payment.id,
            detailsBn = "${targetUser.fullName} (${targetUser.phone}) — TxID: ${payment.transactionId} অনুমোদিত"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(activatedUser)
    }

    suspend fun adminRejectActivationPayment(
        admin: UserEntity,
        paymentId: String,
        reason: String,
        requestResubmission: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): Result<Unit> {
        if (admin.role != UserRole.ADMIN.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সুপার অ্যাডমিন পেমেন্ট প্রত্যাখ্যান করতে পারেন।"))
        }
        val payment = db.activationPaymentDao().getPaymentById(paymentId)
            ?: return Result.failure(IllegalArgumentException("পেমেন্ট রেকর্ড পাওয়া যায়নি।"))
        val targetUser = db.userDao().getUserById(payment.userId)
            ?: return Result.failure(IllegalArgumentException("সংশ্লিষ্ট ব্যবহারকারীকে পাওয়া যায়নি।"))

        val newPaymentStatus = if (requestResubmission) {
            ActivationPaymentStatus.RESUBMISSION_REQUESTED.name
        } else {
            ActivationPaymentStatus.REJECTED.name
        }
        val cleanReason = reason.trim().ifBlank {
            if (requestResubmission) "সঠিক ট্রানজেকশন আইডি ও স্পষ্ট রসিদসহ পুনরায় জমা দিন।"
            else "পেমেন্ট তথ্য যাচাইয়ে অমিল পাওয়া গেছে।"
        }
        val updatedPayment = payment.copy(
            status = newPaymentStatus,
            adminNotes = cleanReason,
            reviewedBy = admin.id,
            reviewedAt = now,
            updatedAt = now
        )
        // Only change accountStatus to PAYMENT_REJECTED if user is not currently on an active unexpired renewal
        val keepCurrentlyActive = targetUser.accountStatus == AccountStatus.ACTIVE.name &&
            (targetUser.expiryDate <= 0L || targetUser.expiryDate > now)
        val updatedUser = if (keepCurrentlyActive) {
            targetUser
        } else {
            targetUser.copy(
                accountStatus = AccountStatus.PAYMENT_REJECTED.name,
                verificationStatus = if (requestResubmission) "PENDING" else "REJECTED"
            )
        }

        db.withTransaction {
            db.activationPaymentDao().upsertPayment(updatedPayment)
            db.userDao().upsertUser(updatedUser)
        }
        rtdbSync.pushActivationPayment(updatedPayment)
        rtdbSync.pushUser(updatedUser)

        val notif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = targetUser.id,
            targetRole = targetUser.role,
            senderRole = UserRole.ADMIN.name,
            type = "ACTIVATION_REJECTED",
            titleBn = if (requestResubmission) "অ্যাক্টিভেশন পেমেন্ট পুনরায় জমা দিন" else "অ্যাক্টিভেশন পেমেন্ট প্রত্যাখ্যাত হয়েছে",
            messageBn = "কারণ: $cleanReason অনুগ্রহ করে সঠিক TxID ও রসিদসহ পুনরায় জমা দিন।",
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(notif)
        rtdbSync.pushNotification(notif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = if (requestResubmission) "পেমেন্ট পুনঃজমা অনুরোধ" else "পেমেন্ট প্রত্যাখ্যান",
            targetEntity = "ActivationPayment",
            targetId = payment.id,
            detailsBn = "${targetUser.fullName} — TxID: ${payment.transactionId} | $cleanReason"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(Unit)
    }

    suspend fun adminSetAccountStatus(
        admin: UserEntity,
        userId: String,
        newStatus: AccountStatus,
        reason: String = "",
        now: Long = System.currentTimeMillis()
    ): Result<UserEntity> {
        if (admin.role != UserRole.ADMIN.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সুপার অ্যাডমিন অ্যাকাউন্টের অবস্থা পরিবর্তন করতে পারেন।"))
        }
        val target = db.userDao().getUserById(userId)
            ?: return Result.failure(IllegalArgumentException("ব্যবহারকারী পাওয়া যায়নি।"))
        if (target.role == UserRole.ADMIN.name) {
            return Result.failure(IllegalArgumentException("সুপার অ্যাডমিন অ্যাকাউন্ট পরিবর্তন করা যাবে না।"))
        }
        val settings = getEffectivePaymentSettings()
        val updated = when (newStatus) {
            AccountStatus.ACTIVE -> {
                val act = if (target.activationDate > 0L) target.activationDate else now
                val exp = if (target.expiryDate > now) target.expiryDate else addCalendarYears(now, settings.validityYears)
                target.copy(
                    accountStatus = AccountStatus.ACTIVE.name,
                    verificationStatus = "VERIFIED",
                    activationDate = act,
                    expiryDate = exp,
                    validityYears = settings.validityYears
                )
            }
            AccountStatus.SUSPENDED -> target.copy(accountStatus = AccountStatus.SUSPENDED.name)
            AccountStatus.EXPIRED -> target.copy(accountStatus = AccountStatus.EXPIRED.name)
            else -> target.copy(accountStatus = newStatus.name)
        }

        db.userDao().upsertUser(updated)
        if (updated.role == UserRole.FARMER.name) {
            db.productDao().updateFarmerVerifiedOnProducts(updated.id, updated.accountStatus == AccountStatus.ACTIVE.name)
            db.productDao().getProductsForFarmer(updated.id).forEach { rtdbSync.pushProduct(it) }
        }
        rtdbSync.pushUser(updated)

        val notif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = updated.id,
            targetRole = updated.role,
            senderRole = UserRole.ADMIN.name,
            type = "ACCOUNT_STATUS_CHANGE",
            titleBn = "অ্যাকাউন্ট স্ট্যাটাস: ${newStatus.display(AppLanguage.BENGALI)}",
            messageBn = reason.ifBlank { "অ্যাডমিন আপনার অ্যাকাউন্টের অবস্থা ${newStatus.display(AppLanguage.BENGALI)} হিসেবে আপডেট করেছেন।" },
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(notif)
        rtdbSync.pushNotification(notif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "অ্যাকাউন্ট স্ট্যাটাস (${newStatus.name})",
            targetEntity = "User",
            targetId = updated.id,
            detailsBn = "${updated.fullName} — ${newStatus.labelBn} ${if (reason.isNotBlank()) "($reason)" else ""}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(updated)
    }

    suspend fun adminSavePaymentSettings(
        admin: UserEntity,
        settings: PaymentSettingsEntity
    ): Result<PaymentSettingsEntity> {
        if (admin.role != UserRole.ADMIN.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সুপার অ্যাডমিন পেমেন্ট সেটিংস পরিবর্তন করতে পারেন।"))
        }
        val cleanBkash = SecurityHashUtil.normalizePhone(settings.bKashNumber)
        val cleanNagad = SecurityHashUtil.normalizePhone(settings.nagadNumber)
        if (cleanBkash.length < 10 || cleanNagad.length < 10) {
            return Result.failure(IllegalArgumentException("সঠিক বিকাশ এবং নগদ পেমেন্ট নম্বর লিখুন।"))
        }
        val stamped = settings.copy(
            id = "default",
            bKashNumber = cleanBkash,
            nagadNumber = cleanNagad,
            bKashAccountType = if (settings.bKashAccountType.equals("MERCHANT", true)) "MERCHANT" else "PERSONAL",
            nagadAccountType = if (settings.nagadAccountType.equals("MERCHANT", true)) "MERCHANT" else "PERSONAL",
            farmerFee = settings.farmerFee.takeIf { it > 0 } ?: FIXED_ACTIVATION_FEE_BDT,
            buyerFee = settings.buyerFee.takeIf { it > 0 } ?: FIXED_ACTIVATION_FEE_BDT,
            renewalFee = settings.renewalFee.takeIf { it > 0 } ?: FIXED_ACTIVATION_FEE_BDT,
            validityYears = FIXED_VALIDITY_YEARS,
            updatedAt = System.currentTimeMillis()
        )
        db.paymentSettingsDao().upsertSettings(stamped)
        rtdbSync.pushPaymentSettings(stamped)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "পেমেন্ট ও অ্যাক্টিভেশন সেটিংস আপডেট",
            targetEntity = "PaymentSettings",
            targetId = "default",
            detailsBn = "bKash: ${stamped.bKashNumber} (${stamped.bKashAccountType}), Nagad: ${stamped.nagadNumber} (${stamped.nagadAccountType}), ফি: ৳${stamped.farmerFee.toInt()}/${stamped.validityYears} বছর"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(stamped)
    }

    suspend fun resetPasswordByPhone(
        phone: String,
        newRawPassword: String
    ): Result<Unit> {
        val cleanPhone = SecurityHashUtil.normalizePhone(phone)
        if (cleanPhone.equals(SUPERADMIN_LOGIN_ID, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("সুপার অ্যাডমিন পাসওয়ার্ড এখান থেকে পরিবর্তন করা যাবে না।"))
        }
        if (cleanPhone.length < 10) {
            return Result.failure(IllegalArgumentException("সঠিক নিবন্ধিত মোবাইল নম্বর দিন।"))
        }
        if (newRawPassword.length < 6) {
            return Result.failure(IllegalArgumentException("নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।"))
        }

        val remoteUser = rtdbSync.fetchUserByPhone(cleanPhone, db)
        val existingUser = remoteUser ?: db.userDao().getAllUsersOnce()
            .firstOrNull { SecurityHashUtil.normalizePhone(it.phone) == cleanPhone }
            ?: return Result.failure(IllegalArgumentException("এই মোবাইল নম্বরে কোনো অ্যাকাউন্ট পাওয়া যায়নি।"))

        val updated = existingUser.copy(
            passwordHash = SecurityHashUtil.hashPassword(newRawPassword)
        )
        db.userDao().upsertUser(updated)
        rtdbSync.pushUser(updated)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = updated.id,
            actorRole = updated.role,
            actionBn = "পাসওয়ার্ড রিসেট (Forgot Password)",
            targetEntity = "User",
            targetId = updated.id,
            detailsBn = "${updated.fullName} (${updated.phone}) পাসওয়ার্ড পরিবর্তন করেছেন"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(Unit)
    }

    suspend fun updateUserProfile(
        userId: String,
        fullName: String,
        division: String,
        district: String,
        upazila: String,
        unionName: String,
        village: String,
        farmBio: String,
        nidLastFour: String,
        profileImageUri: String
    ): Result<UserEntity> {
        val existing = db.userDao().getUserById(userId)
            ?: return Result.failure(IllegalArgumentException("ব্যবহারকারী পাওয়া যায়নি।"))
        if (fullName.isBlank()) {
            return Result.failure(IllegalArgumentException("অনুগ্রহ করে পূর্ণ নাম লিখুন।"))
        }
        val resolvedDistrict = district.ifBlank { existing.district }
        val resolvedDivision = division.ifBlank { BangladeshLocations.divisionForDistrict(resolvedDistrict) }
        val resolvedUpazila = upazila.ifBlank { existing.upazila }
        val resolvedNidMasked = if (nidLastFour.trim().length >= 4) {
            "NID-****-${nidLastFour.trim().takeLast(4)}"
        } else {
            existing.nidMasked
        }

        val updated = existing.copy(
            fullName = fullName.trim(),
            division = resolvedDivision,
            district = resolvedDistrict,
            upazila = resolvedUpazila,
            unionName = unionName.trim(),
            village = village.trim(),
            farmBio = farmBio.trim(),
            nidMasked = resolvedNidMasked,
            profileImageUri = profileImageUri.trim()
        )
        db.userDao().upsertUser(updated)
        rtdbSync.pushUser(updated)

        if (updated.role == UserRole.FARMER.name) {
            db.productDao().updateFarmerMetadataOnProducts(
                farmerId = updated.id,
                farmerName = updated.fullName,
                division = updated.division,
                district = updated.district,
                upazila = updated.upazila,
                unionName = updated.unionName,
                village = updated.village,
                now = System.currentTimeMillis()
            )
            db.productDao().getProductsForFarmer(updated.id).forEach { rtdbSync.pushProduct(it) }
        }

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = updated.id,
            actorRole = updated.role,
            actionBn = "প্রোফাইল তথ্য হালনাগাদ",
            targetEntity = "User",
            targetId = updated.id,
            detailsBn = "${updated.fullName} — ${updated.unionName.ifBlank { updated.upazila }}, ${updated.district}, ${updated.division}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(updated)
    }

    suspend fun saveFarmerPaymentSettings(
        farmer: UserEntity,
        bKashNumber: String,
        nagadNumber: String,
        rocketNumber: String,
        bankAccountInfo: String
    ): Result<FarmerPaymentSettingsEntity> {
        if (farmer.role != UserRole.FARMER.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র কৃষক নিজস্ব পেমেন্ট নম্বর সেটিংস সংরক্ষণ করতে পারেন।"))
        }
        val cleanBkash = if (bKashNumber.isBlank()) "" else SecurityHashUtil.normalizePhone(bKashNumber)
        val cleanNagad = if (nagadNumber.isBlank()) "" else SecurityHashUtil.normalizePhone(nagadNumber)
        val cleanRocket = if (rocketNumber.isBlank()) "" else SecurityHashUtil.normalizePhone(rocketNumber)
        val cleanBank = bankAccountInfo.trim()

        if (cleanBkash.isNotEmpty() && cleanBkash.length < 10) {
            return Result.failure(IllegalArgumentException("সঠিক বিকাশ (bKash) মোবাইল নম্বর লিখুন।"))
        }
        if (cleanNagad.isNotEmpty() && cleanNagad.length < 10) {
            return Result.failure(IllegalArgumentException("সঠিক নগদ (Nagad) মোবাইল নম্বর লিখুন।"))
        }
        if (cleanRocket.isNotEmpty() && cleanRocket.length < 10) {
            return Result.failure(IllegalArgumentException("সঠিক রকেট (Rocket) মোবাইল নম্বর লিখুন।"))
        }
        if (cleanBkash.isEmpty() && cleanNagad.isEmpty() && cleanRocket.isEmpty() && cleanBank.isEmpty()) {
            return Result.failure(IllegalArgumentException("অন্তত একটি পেমেন্ট মাধ্যম (bKash / Nagad / Rocket / Bank) লিখুন।"))
        }

        val now = System.currentTimeMillis()
        val settings = FarmerPaymentSettingsEntity(
            farmerId = farmer.id,
            bKashNumber = cleanBkash,
            nagadNumber = cleanNagad,
            rocketNumber = cleanRocket,
            bankAccountInfo = cleanBank,
            updatedAt = now
        )
        db.farmerPaymentSettingsDao().upsert(settings)
        rtdbSync.pushFarmerPaymentSettings(settings)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = farmer.id,
            actorRole = farmer.role,
            actionBn = "কৃষকের সরাসরি পেমেন্ট নম্বর আপডেট",
            targetEntity = "FarmerPaymentSettings",
            targetId = farmer.id,
            detailsBn = "bKash: ${cleanBkash.ifBlank { "-" }}, Nagad: ${cleanNagad.ifBlank { "-" }}, Rocket: ${cleanRocket.ifBlank { "-" }}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(settings)
    }

    private suspend fun isDuplicateTransactionIdAnywhere(txNormalized: String): Boolean {
        if (txNormalized.isBlank()) return false
        rtdbSync.fetchAllOrderPayments(db)
        rtdbSync.fetchAllActivationPayments(db)
        val inOrderPayments = db.orderPaymentDao().findByNormalizedTxId(txNormalized) != null
        val inActivation = db.activationPaymentDao().getAllPaymentsOnce().any {
            normalizeTransactionId(it.transactionId) == txNormalized
        }
        return inOrderPayments || inActivation
    }

    suspend fun addToCart(product: ProductEntity, requestedQty: Double, actor: UserEntity? = null): Result<Unit> {
        if (actor != null) {
            val gate = requireActiveMarketplaceUser(actor, "কার্টে ফসল যোগ করার")
            if (gate.isFailure) return Result.failure(gate.exceptionOrNull()!!)
        }
        if (requestedQty <= 0.0) {
            return Result.failure(IllegalArgumentException("সঠিক পরিমাণ (মণ) নির্বাচন করুন।"))
        }
        if (product.status != "ACTIVE" || product.availableQuantity <= 0.0) {
            return Result.failure(IllegalArgumentException("দুঃখিত, এই ফসলটির মজুত শেষ।"))
        }
        val existing = db.cartDao().getCartItemByProductId(product.id)
        val totalQty = (existing?.quantity ?: 0.0) + requestedQty
        if (totalQty > product.availableQuantity) {
            return Result.failure(
                IllegalArgumentException("সর্বোচ্চ মজুত ${product.availableQuantity.formatQty()} মণ পর্যন্ত যোগ করা যাবে।")
            )
        }
        val item = CartItemEntity(
            id = existing?.id ?: "cart_${UUID.randomUUID().toString().take(8)}",
            productId = product.id,
            farmerId = product.farmerId,
            farmerName = product.farmerName,
            productNameBn = product.nameBn,
            unit = ProductUnit.MON.labelBn,
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
            return Result.failure(IllegalArgumentException("পর্যাপ্ত মজুত নেই (সর্বোচ্চ ${maxAvailable.formatQty()} মণ)"))
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
        transactionRef: String,
        advanceByFarmer: Map<String, Double> = emptyMap(),
        senderNumber: String = "",
        paymentReferenceNote: String = ""
    ): Result<List<OrderEntity>> {
        val gate = requireActiveMarketplaceUser(buyer, "অর্ডার করার")
        if (gate.isFailure) return Result.failure(gate.exceptionOrNull()!!)

        val items = db.cartDao().getCartItemsOnce()
        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("আপনার কার্ট খালি।"))
        }
        if (deliveryOption == DeliveryOption.DIRECT_DELIVERY && deliveryAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("ডেলিভারির ঠিকানা লিখুন।"))
        }

        val cleanTxId = normalizeTransactionId(transactionRef)
        val cleanSender = SecurityHashUtil.normalizePhone(senderNumber).ifBlank { buyer.phone }
        val groupedByFarmer = items.groupBy { it.farmerId }
        val totalRequestedAdvance = groupedByFarmer.keys.sumOf { fId -> advanceByFarmer[fId] ?: 0.0 }

        if (totalRequestedAdvance > 0.0) {
            if (cleanTxId.length < 4) {
                return Result.failure(
                    IllegalArgumentException("অগ্রিম পেমেন্টের জন্য সঠিক Transaction ID লিখুন।")
                )
            }
            if (isDuplicateTransactionIdAnywhere(cleanTxId)) {
                return Result.failure(
                    IllegalArgumentException("এই Transaction ID ($cleanTxId) পূর্বেই ব্যবহার করা হয়েছে। নতুন ও সঠিক Transaction ID দিন।")
                )
            }
        }

        val now = System.currentTimeMillis()
        val createdBundles = mutableListOf<Triple<OrderEntity, List<OrderItemEntity>, NotificationEntity>>()
        val createdPayments = mutableListOf<OrderPaymentEntity>()
        val createdAudits = mutableListOf<AuditLogEntity>()
        val updatedProducts = mutableListOf<ProductEntity>()

        return runCatching {
            db.withTransaction {
                var txUsedForOrder = false
                groupedByFarmer.forEach { (farmerId, farmerItems) ->
                    val farmer = db.userDao().getUserById(farmerId)
                    val farmerPay = db.farmerPaymentSettingsDao().getForFarmerOnce(farmerId)
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
                                unit = ProductUnit.MON.labelBn,
                                quantity = cartItem.quantity,
                                pricePerUnit = cartItem.pricePerUnit,
                                lineTotal = lineTotal
                            )
                        )
                    }

                    val deliveryCharge = if (deliveryOption == DeliveryOption.FARM_PICKUP) 0.0 else maxDeliveryFee
                    val grandTotal = productTotal + deliveryCharge
                    val requestedAdvance = kotlin.math.round((advanceByFarmer[farmerId] ?: 0.0) * 100.0) / 100.0
                    if (requestedAdvance < 0.0 || requestedAdvance > grandTotal + 0.01) {
                        throw IllegalArgumentException(
                            "অগ্রিম পেমেন্টের পরিমাণ ০ থেকে মোট মূল্যের (${grandTotal.formatBdt()}) মধ্যে হতে হবে।"
                        )
                    }
                    val cappedAdvance = requestedAdvance.coerceIn(0.0, grandTotal)

                    val receiverNumber = when (paymentMethod) {
                        PaymentMethod.BKASH -> farmerPay?.bKashNumber.orEmpty()
                        PaymentMethod.NAGAD -> farmerPay?.nagadNumber.orEmpty()
                        PaymentMethod.ROCKET -> farmerPay?.rocketNumber.orEmpty()
                        PaymentMethod.BANK_TRANSFER -> farmerPay?.bankAccountInfo.orEmpty()
                        else -> ""
                    }
                    if (cappedAdvance > 0.0 && paymentMethod in setOf(PaymentMethod.BKASH, PaymentMethod.NAGAD, PaymentMethod.ROCKET, PaymentMethod.BANK_TRANSFER)) {
                        if (receiverNumber.isBlank()) {
                            throw IllegalArgumentException(
                                "কৃষক ${farmer?.fullName ?: farmerItems.first().farmerName} এখনো ${paymentMethod.labelEn} নম্বর সেট করেননি। অন্য পেমেন্ট মাধ্যম অথবা শূন্য (৳0) অগ্রিম বেছে নিন।"
                            )
                        }
                    }

                    val summary = farmerItems.joinToString(", ") {
                        "${it.productNameBn} × ${it.quantity.formatQty()} মণ"
                    }
                    val orderTxId = if (cappedAdvance > 0.0) {
                        if (!txUsedForOrder) {
                            txUsedForOrder = true
                            cleanTxId
                        } else {
                            "${cleanTxId}_${farmerId.takeLast(4)}"
                        }
                    } else {
                        ""
                    }
                    val paymentStatus = if (cappedAdvance > 0.0) "PENDING_VERIFICATION" else "ZERO_ADVANCE_PENDING"

                    val order = OrderEntity(
                        id = orderId,
                        orderCode = orderCode,
                        buyerId = buyer.id,
                        buyerName = buyer.fullName,
                        buyerPhone = buyer.phone,
                        farmerId = farmerId,
                        farmerName = farmer?.fullName ?: farmerItems.first().farmerName,
                        farmerPhone = farmer?.phone ?: "",
                        status = OrderStatus.PAYMENT_PENDING.name,
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
                        transactionReference = orderTxId,
                        paymentStatus = paymentStatus,
                        itemsSummary = summary,
                        createdAt = now,
                        updatedAt = now,
                        isDemoRecord = false,
                        paymentSenderNumber = if (cappedAdvance > 0.0) cleanSender else "",
                        paymentReceiptUri = "",
                        farmerPaymentConfirmed = false,
                        farmerPaymentConfirmedAt = 0L,
                        adminPaymentVerified = false,
                        adminPaymentVerifiedBy = "",
                        adminPaymentVerifiedAt = 0L,
                        advanceAmountRequested = cappedAdvance,
                        verifiedAdvanceTotal = 0.0,
                        remainingDue = grandTotal
                    )

                    if (cappedAdvance > 0.0) {
                        val op = OrderPaymentEntity(
                            id = "op_${UUID.randomUUID().toString().take(8)}",
                            orderId = orderId,
                            orderCode = orderCode,
                            buyerId = buyer.id,
                            buyerName = buyer.fullName,
                            farmerId = farmerId,
                            farmerName = order.farmerName,
                            amount = cappedAdvance,
                            paymentMethod = paymentMethod.name,
                            receiverNumber = receiverNumber,
                            senderNumber = cleanSender,
                            transactionId = orderTxId,
                            transactionIdNormalized = normalizeTransactionId(orderTxId),
                            referenceNote = paymentReferenceNote.trim(),
                            status = OrderPaymentStatus.PENDING_VERIFICATION.name,
                            createdAt = now,
                            updatedAt = now
                        )
                        db.orderPaymentDao().upsertPayment(op)
                        createdPayments.add(op)
                    }

                    val notifMsg = if (cappedAdvance > 0.0) {
                        "ক্রেতা ${buyer.fullName} $summary (মোট ${grandTotal.formatBdt()}) অর্ডার করেছেন এবং ${cappedAdvance.formatBdt()} অগ্রিম পেমেন্ট (${paymentMethod.labelEn}, TxID: $orderTxId) জমা দিয়েছেন। নিজ অ্যাকাউন্টে টাকা দেখে যাচাই করে নিশ্চিত করুন।"
                    } else {
                        "ক্রেতা ${buyer.fullName} $summary (মোট ${grandTotal.formatBdt()}) অর্ডার করেছেন (অগ্রিম ৳০)। অনুগ্রহ করে অর্ডারটি অনুমোদন বা প্রত্যাখ্যান করুন।"
                    }
                    val farmerNotification = NotificationEntity(
                        id = "not_${UUID.randomUUID().toString().take(8)}",
                        userId = farmerId,
                        targetRole = UserRole.FARMER.name,
                        senderRole = UserRole.BUYER.name,
                        orderId = orderId,
                        type = if (cappedAdvance > 0.0) "ORDER_ADVANCE_SUBMITTED" else "ORDER_NEW",
                        titleBn = if (cappedAdvance > 0.0) "নতুন অর্ডার ও অগ্রিম পেমেন্ট ($orderCode)" else "নতুন পাইকারি অর্ডার ($orderCode)",
                        messageBn = notifMsg,
                        isRead = false,
                        createdAt = now
                    )
                    val audit = AuditLogEntity(
                        id = "aud_${UUID.randomUUID().toString().take(8)}",
                        actorId = buyer.id,
                        actorRole = buyer.role,
                        actionBn = "পাইকারি অর্ডার ও অগ্রিম তথ্য তৈরি",
                        targetEntity = "Order",
                        targetId = orderId,
                        detailsBn = "$orderCode — মোট ${grandTotal.formatBdt()}, প্রস্তাবিত অগ্রিম ${cappedAdvance.formatBdt()}, অবশিষ্ট বকেয়া ${grandTotal.formatBdt()}"
                    )

                    db.orderDao().insertOrders(listOf(order))
                    db.orderDao().insertOrderItems(orderLineEntities)
                    db.notificationAuditDao().insertNotification(farmerNotification)
                    db.notificationAuditDao().insertAuditLog(audit)
                    createdBundles.add(Triple(order, orderLineEntities, farmerNotification))
                    createdAudits.add(audit)
                }
                db.cartDao().clearCart()
            }

            updatedProducts.forEach { rtdbSync.pushProduct(it) }
            createdPayments.forEach { rtdbSync.pushOrderPayment(it) }
            createdBundles.forEach { (order, lineItems, notif) ->
                rtdbSync.pushOrder(order, lineItems)
                rtdbSync.pushNotification(notif)
            }
            createdAudits.forEach { rtdbSync.pushAuditLog(it) }
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
        isWholesale: Boolean = true
    ): Result<ProductEntity> {
        if (farmer.role != UserRole.FARMER.name) {
            return Result.failure(IllegalStateException("শুধুমাত্র কৃষক নিজস্ব ফসল তালিকাভুক্ত করতে পারেন।"))
        }
        val gate = requireActiveMarketplaceUser(farmer, "ফসল তালিকাভুক্ত করার")
        if (gate.isFailure) return Result.failure(gate.exceptionOrNull()!!)

        if (!ProductUnit.isAllowedUnit(unit)) {
            return Result.failure(
                IllegalArgumentException("কৃষি বন্ধু পাইকারি বাজারে শুধুমাত্র 'মণ' এককে ফসল বিক্রি করা যাবে (১ মণ = ৪০ কেজি)।")
            )
        }
        if (nameBn.isBlank() || quantity <= 0.0 || pricePerUnit <= 0.0 || deliveryCharge < 0.0) {
            return Result.failure(IllegalArgumentException("ফসলের নাম, সঠিক পরিমাণ (মণ) ও প্রতি মণের পাইকারি দাম দিন।"))
        }
        val activeFarmer = gate.getOrThrow()
        val now = System.currentTimeMillis()
        val product = ProductEntity(
            id = "prod_${UUID.randomUUID().toString().take(8)}",
            farmerId = activeFarmer.id,
            farmerName = activeFarmer.fullName,
            farmerVerified = activeFarmer.verificationStatus == "VERIFIED" || activeFarmer.accountStatus == AccountStatus.ACTIVE.name,
            categoryId = category.id,
            categoryNameBn = category.nameBn,
            nameBn = nameBn.trim(),
            nameEn = nameBn.trim(),
            descriptionBn = descriptionBn.trim(),
            imageUri = imageUri,
            availableQuantity = quantity,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = pricePerUnit,
            harvestDate = harvestDate.ifBlank { "আজই সংগ্রহ" },
            availabilityDate = availabilityDate.ifBlank { "আজই পাওয়া যাবে" },
            division = activeFarmer.division,
            district = activeFarmer.district,
            upazila = activeFarmer.upazila,
            unionName = activeFarmer.unionName,
            village = activeFarmer.village,
            pickupAddress = pickupAddress.ifBlank {
                listOf(activeFarmer.village, activeFarmer.unionName, activeFarmer.upazila, activeFarmer.district)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            },
            supportsFarmPickup = supportsFarmPickup,
            supportsDirectDelivery = supportsDirectDelivery,
            deliveryChargeEstimate = if (supportsDirectDelivery) deliveryCharge else 0.0,
            isWholesale = true,
            isPopular = false,
            status = "ACTIVE",
            createdAt = now,
            updatedAt = now,
            isDemoRecord = false
        )
        db.productDao().upsertProduct(product)
        rtdbSync.pushProduct(product)
        val totalListingValue = quantity * pricePerUnit
        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = activeFarmer.id,
            actorRole = activeFarmer.role,
            actionBn = "নতুন ফসল যুক্ত",
            targetEntity = "Product",
            targetId = product.id,
            detailsBn = "${product.nameBn} (${product.district}) — ${quantity.formatQty()} মণ × ${pricePerUnit.formatBdt()}/মণ = ${totalListingValue.formatBdt()}"
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
        val farmer = db.userDao().getUserById(farmerId)
        val gate = requireActiveMarketplaceUser(farmer, "দাম ও মজুত পরিবর্তনের")
        if (gate.isFailure) return Result.failure(gate.exceptionOrNull()!!)

        if (newPrice <= 0.0 || newStock < 0.0) {
            return Result.failure(IllegalArgumentException("সঠিক প্রতি মণের মূল্য ও মজুতের পরিমাণ দিন।"))
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

    suspend fun submitOrderAdvancePayment(
        orderId: String,
        buyer: UserEntity,
        amount: Double,
        paymentMethod: PaymentMethod,
        senderNumber: String,
        transactionId: String,
        referenceNote: String = ""
    ): Result<OrderPaymentEntity> {
        val gate = requireActiveMarketplaceUser(buyer, "অগ্রিম পেমেন্ট জমা দেওয়ার")
        if (gate.isFailure) return Result.failure(gate.exceptionOrNull()!!)

        val order = db.orderDao().getOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("অর্ডার পাওয়া যায়নি।"))
        if (order.buyerId != buyer.id) {
            return Result.failure(IllegalAccessException("শুধুমাত্র অর্ডারকারী ক্রেতা পেমেন্ট তথ্য জমা দিতে পারবেন।"))
        }
        if (order.status in setOf(OrderStatus.REJECTED.name, OrderStatus.CANCELLED.name, OrderStatus.COMPLETED.name)) {
            return Result.failure(IllegalStateException("এই অবস্থায় অর্ডারে পেমেন্ট জমা দেওয়া যাবে না।"))
        }

        val currentRemainingDue = kotlin.math.max(0.0, order.grandTotal - order.verifiedAdvanceTotal)
        if (currentRemainingDue <= 0.0) {
            return Result.failure(IllegalStateException("এই অর্ডারের সম্পূর্ণ অর্থ ইতোমধ্যে পরিশোধিত।"))
        }
        val roundedAmount = kotlin.math.round(amount * 100.0) / 100.0
        if (roundedAmount <= 0.0 || roundedAmount > currentRemainingDue + 0.01) {
            return Result.failure(
                IllegalArgumentException("পেমেন্টের পরিমাণ অবশ্যই ৳১ থেকে অবশিষ্ট বকেয়া (${currentRemainingDue.formatBdt()}) এর মধ্যে হতে হবে।")
            )
        }
        val cleanTxId = normalizeTransactionId(transactionId)
        if (cleanTxId.length < 4) {
            return Result.failure(IllegalArgumentException("সঠিক ট্রানজেকশন আইডি (Transaction ID) লিখুন।"))
        }
        if (isDuplicateTransactionIdAnywhere(cleanTxId)) {
            return Result.failure(
                IllegalArgumentException("এই ট্রানজেকশন আইডি ($cleanTxId) পূর্বেই ব্যবহার করা হয়েছে।")
            )
        }

        val farmerPay = db.farmerPaymentSettingsDao().getForFarmerOnce(order.farmerId)
        val receiverNumber = when (paymentMethod) {
            PaymentMethod.BKASH -> farmerPay?.bKashNumber.orEmpty()
            PaymentMethod.NAGAD -> farmerPay?.nagadNumber.orEmpty()
            PaymentMethod.ROCKET -> farmerPay?.rocketNumber.orEmpty()
            PaymentMethod.BANK_TRANSFER -> farmerPay?.bankAccountInfo.orEmpty()
            else -> ""
        }
        val cleanSender = SecurityHashUtil.normalizePhone(senderNumber).ifBlank { buyer.phone }
        val now = System.currentTimeMillis()
        val payment = OrderPaymentEntity(
            id = "op_${UUID.randomUUID().toString().take(8)}",
            orderId = order.id,
            orderCode = order.orderCode,
            buyerId = buyer.id,
            buyerName = buyer.fullName,
            farmerId = order.farmerId,
            farmerName = order.farmerName,
            amount = roundedAmount.coerceAtMost(currentRemainingDue),
            paymentMethod = paymentMethod.name,
            receiverNumber = receiverNumber,
            senderNumber = cleanSender,
            transactionId = cleanTxId,
            transactionIdNormalized = cleanTxId,
            referenceNote = referenceNote.trim(),
            status = OrderPaymentStatus.PENDING_VERIFICATION.name,
            createdAt = now,
            updatedAt = now
        )

        db.withTransaction {
            db.orderPaymentDao().upsertPayment(payment)
            db.orderDao().updateBuyerPaymentSubmission(
                orderId = order.id,
                paymentMethod = paymentMethod.name,
                senderNumber = cleanSender,
                txId = cleanTxId,
                receiptUri = "",
                paymentStatus = "PENDING_VERIFICATION",
                now = now
            )
        }
        rtdbSync.pushOrderPayment(payment)
        db.orderDao().getOrderById(order.id)?.let { updatedOrder ->
            rtdbSync.pushOrder(updatedOrder, db.orderDao().getOrderItemsOnce(order.id))
        }

        val farmerNotif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = order.farmerId,
            targetRole = UserRole.FARMER.name,
            senderRole = UserRole.BUYER.name,
            orderId = order.id,
            type = "ORDER_PAYMENT_SUBMITTED",
            titleBn = "নতুন অগ্রিম/আংশিক পেমেন্ট জমা (${order.orderCode})",
            messageBn = "ক্রেতা ${buyer.fullName} ${payment.amount.formatBdt()} (${paymentMethod.labelEn}, TxID: $cleanTxId) পাঠিয়েছেন। নিজ অ্যাকাউন্টের ব্যালেন্স দেখে নিশ্চিত করুন।",
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(farmerNotif)
        rtdbSync.pushNotification(farmerNotif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = buyer.id,
            actorRole = buyer.role,
            actionBn = "অর্ডার অগ্রিম/আংশিক পেমেন্ট জমা",
            targetEntity = "OrderPayment",
            targetId = payment.id,
            detailsBn = "${order.orderCode} — ${payment.amount.formatBdt()} (${paymentMethod.labelEn}, TxID: $cleanTxId)"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(payment)
    }

    suspend fun farmerVerifyOrderPayment(
        paymentId: String,
        farmer: UserEntity,
        approved: Boolean,
        rejectionReason: String = ""
    ): Result<OrderEntity> {
        val gate = requireActiveMarketplaceUser(farmer, "পেমেন্ট যাচাই করার")
        if (gate.isFailure) return Result.failure(gate.exceptionOrNull()!!)

        val payment = db.orderPaymentDao().getPaymentById(paymentId)
            ?: return Result.failure(IllegalArgumentException("পেমেন্ট রেকর্ড পাওয়া যায়নি।"))
        if (payment.farmerId != farmer.id) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সংশ্লিষ্ট কৃষক এই পেমেন্ট যাচাই করতে পারবেন।"))
        }
        if (payment.status != OrderPaymentStatus.PENDING_VERIFICATION.name) {
            return Result.failure(IllegalStateException("এই পেমেন্টটি ইতোমধ্যে যাচাই করা হয়েছে (${payment.status})।"))
        }
        val order = db.orderDao().getOrderById(payment.orderId)
            ?: return Result.failure(IllegalArgumentException("সংশ্লিষ্ট অর্ডার পাওয়া যায়নি।"))

        val now = System.currentTimeMillis()
        val updatedPayment = if (approved) {
            payment.copy(
                status = OrderPaymentStatus.CONFIRMED.name,
                rejectionReason = "",
                verifiedBy = farmer.id,
                verifiedAt = now,
                updatedAt = now
            )
        } else {
            payment.copy(
                status = OrderPaymentStatus.REJECTED.name,
                rejectionReason = rejectionReason.trim().ifBlank { "কৃষক অ্যাকাউন্টে টাকা পাওয়া যায়নি বা তথ্য মেলেনি।" },
                verifiedBy = farmer.id,
                verifiedAt = now,
                updatedAt = now
            )
        }

        db.withTransaction {
            db.orderPaymentDao().upsertPayment(updatedPayment)
            val confirmedSum = kotlin.math.min(
                order.grandTotal,
                db.orderPaymentDao().getConfirmedTotalForOrder(order.id)
            )
            val newRemaining = kotlin.math.max(0.0, order.grandTotal - confirmedSum)
            val meetsRequiredAdvance = confirmedSum > 0.0 &&
                (order.advanceAmountRequested <= 0.0 || confirmedSum + 0.01 >= order.advanceAmountRequested)
            val nextStatus = when {
                approved && meetsRequiredAdvance && order.status in setOf(
                    OrderStatus.PAYMENT_PENDING.name,
                    OrderStatus.PENDING.name,
                    OrderStatus.ACCEPTED.name
                ) -> OrderStatus.CONFIRMED.name
                else -> order.status
            }
            val nextPayStatus = when {
                newRemaining <= 0.01 && confirmedSum > 0.0 -> "PAID"
                confirmedSum > 0.0 -> "PARTIAL_ADVANCE_VERIFIED"
                approved -> order.paymentStatus
                else -> "REJECTED"
            }
            db.orderDao().updateOrderAdvanceAndStatus(
                orderId = order.id,
                verifiedAdvanceTotal = confirmedSum,
                remainingDue = newRemaining,
                farmerConfirmed = confirmedSum > 0.0,
                farmerConfirmedAt = if (confirmedSum > 0.0) now else order.farmerPaymentConfirmedAt,
                paymentStatus = nextPayStatus,
                status = nextStatus,
                now = now
            )
        }

        val updatedOrder = db.orderDao().getOrderById(order.id)!!
        val items = db.orderDao().getOrderItemsOnce(order.id)
        rtdbSync.pushOrderPayment(updatedPayment)
        rtdbSync.pushOrder(updatedOrder, items)

        val buyerNotif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = order.buyerId,
            targetRole = UserRole.BUYER.name,
            senderRole = UserRole.FARMER.name,
            orderId = order.id,
            type = if (approved) "ORDER_PAYMENT_VERIFIED" else "ORDER_PAYMENT_REJECTED",
            titleBn = if (approved) "অগ্রিম পেমেন্ট নিশ্চিত হয়েছে (${order.orderCode})" else "অগ্রিম পেমেন্ট প্রত্যাখ্যাত হয়েছে (${order.orderCode})",
            messageBn = if (approved) {
                "কৃষক ${farmer.fullName} ${payment.amount.formatBdt()} (TxID: ${payment.transactionId}) নিশ্চিত করেছেন। মোট যাচাইকৃত অগ্রিম: ${updatedOrder.verifiedAdvanceTotal.formatBdt()}, অবশিষ্ট বকেয়া: ${updatedOrder.remainingDue.formatBdt()}।"
            } else {
                "কারণ: ${updatedPayment.rejectionReason}"
            },
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(buyerNotif)
        rtdbSync.pushNotification(buyerNotif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = farmer.id,
            actorRole = farmer.role,
            actionBn = if (approved) "কৃষক অগ্রিম পেমেন্ট নিশ্চিতকরণ" else "কৃষক অগ্রিম পেমেন্ট প্রত্যাখ্যান",
            targetEntity = "OrderPayment",
            targetId = payment.id,
            detailsBn = "${order.orderCode} | ${payment.amount.formatBdt()} | VerifiedTotal=${updatedOrder.verifiedAdvanceTotal.formatBdt()} | Due=${updatedOrder.remainingDue.formatBdt()}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(updatedOrder)
    }

    suspend fun approveZeroAdvanceOrder(
        orderId: String,
        farmer: UserEntity
    ): Result<OrderEntity> {
        val gate = requireActiveMarketplaceUser(farmer, "অর্ডার অনুমোদন করার")
        if (gate.isFailure) return Result.failure(gate.exceptionOrNull()!!)

        val order = db.orderDao().getOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("অর্ডার পাওয়া যায়নি।"))
        if (order.farmerId != farmer.id) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সংশ্লিষ্ট কৃষক এই অর্ডার অনুমোদন করতে পারেন।"))
        }
        if (order.status != OrderStatus.PAYMENT_PENDING.name && order.status != OrderStatus.PENDING.name) {
            return Result.failure(IllegalStateException("এই অর্ডারটি ইতোমধ্যে অনুমোদিত বা সম্পন্ন হয়েছে।"))
        }
        val now = System.currentTimeMillis()
        db.orderDao().updateOrderAdvanceAndStatus(
            orderId = order.id,
            verifiedAdvanceTotal = order.verifiedAdvanceTotal,
            remainingDue = kotlin.math.max(0.0, order.grandTotal - order.verifiedAdvanceTotal),
            farmerConfirmed = order.verifiedAdvanceTotal > 0.0,
            farmerConfirmedAt = order.farmerPaymentConfirmedAt,
            paymentStatus = "ZERO_ADVANCE_APPROVED",
            status = OrderStatus.CONFIRMED.name,
            now = now
        )
        val updatedOrder = db.orderDao().getOrderById(order.id)!!
        rtdbSync.pushOrder(updatedOrder, db.orderDao().getOrderItemsOnce(order.id))

        val buyerNotif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = order.buyerId,
            targetRole = UserRole.BUYER.name,
            senderRole = UserRole.FARMER.name,
            orderId = order.id,
            type = "ZERO_ADVANCE_APPROVED",
            titleBn = "কৃষক অর্ডারটি অনুমোদন করেছেন (${order.orderCode})",
            messageBn = "কৃষক ${farmer.fullName} শূন্য-অগ্রিম অর্ডারটি Confirmed করেছেন। অবশিষ্ট বকেয়া: ${updatedOrder.remainingDue.formatBdt()}।",
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(buyerNotif)
        rtdbSync.pushNotification(buyerNotif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = farmer.id,
            actorRole = farmer.role,
            actionBn = "শূন্য-অগ্রিম অর্ডার অনুমোদন",
            targetEntity = "Order",
            targetId = order.id,
            detailsBn = "${order.orderCode} — অবশিষ্ট বকেয়া ${updatedOrder.remainingDue.formatBdt()}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(updatedOrder)
    }

    suspend fun submitOrderPayment(
        orderId: String,
        buyer: UserEntity,
        paymentMethod: PaymentMethod,
        senderNumber: String,
        transactionReference: String,
        receiptUri: String
    ): Result<OrderEntity> {
        val order = db.orderDao().getOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("অর্ডার পাওয়া যায়নি।"))
        val amountToSubmit = kotlin.math.max(0.0, order.grandTotal - order.verifiedAdvanceTotal)
            .takeIf { it > 0.0 } ?: order.grandTotal
        val res = submitOrderAdvancePayment(
            orderId = orderId,
            buyer = buyer,
            amount = amountToSubmit,
            paymentMethod = paymentMethod,
            senderNumber = senderNumber,
            transactionId = transactionReference,
            referenceNote = ""
        )
        return if (res.isSuccess) {
            Result.success(db.orderDao().getOrderById(orderId)!!)
        } else {
            Result.failure(res.exceptionOrNull()!!)
        }
    }

    suspend fun farmerConfirmPaymentReceived(
        orderId: String,
        farmer: UserEntity
    ): Result<OrderEntity> {
        val gate = requireActiveMarketplaceUser(farmer, "টাকা প্রাপ্তি নিশ্চিত করার")
        if (gate.isFailure) return Result.failure(gate.exceptionOrNull()!!)

        val order = db.orderDao().getOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("অর্ডার পাওয়া যায়নি।"))
        if (order.farmerId != farmer.id) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সংশ্লিষ্ট কৃষক টাকা বুঝে পাওয়ার নিশ্চিতকরণ দিতে পারেন।"))
        }
        if (order.status == OrderStatus.REJECTED.name || order.status == OrderStatus.CANCELLED.name) {
            return Result.failure(IllegalStateException("এই অবস্থায় টাকা প্রাপ্তি নিশ্চিত করা যাবে না।"))
        }

        // If there are pending orderPayments, confirm them cleanly; otherwise confirm remaining balance (e.g. direct cash pickup)
        val pending = db.orderPaymentDao().getPaymentsForOrderOnce(orderId)
            .filter { it.status == OrderPaymentStatus.PENDING_VERIFICATION.name }
        if (pending.isNotEmpty()) {
            var latest = order
            for (p in pending) {
                val r = farmerVerifyOrderPayment(p.id, farmer, true)
                if (r.isSuccess) latest = r.getOrThrow()
            }
            return Result.success(latest)
        }

        val now = System.currentTimeMillis()
        db.orderDao().updateOrderAdvanceAndStatus(
            orderId = orderId,
            verifiedAdvanceTotal = order.grandTotal,
            remainingDue = 0.0,
            farmerConfirmed = true,
            farmerConfirmedAt = now,
            paymentStatus = "PAID",
            status = OrderStatus.CONFIRMED.name,
            now = now
        )
        val updatedOrder = db.orderDao().getOrderById(orderId)!!
        rtdbSync.pushOrder(updatedOrder, db.orderDao().getOrderItemsOnce(orderId))
        return Result.success(updatedOrder)
    }

    suspend fun adminVerifyOrderPayment(
        orderId: String,
        admin: UserEntity,
        approved: Boolean,
        notes: String = ""
    ): Result<OrderEntity> {
        if (admin.role != UserRole.ADMIN.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সুপার অ্যাডমিন অর্ডার পেমেন্ট যাচাই করতে পারেন।"))
        }
        val order = db.orderDao().getOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("অর্ডার পাওয়া যায়নি।"))
        val now = System.currentTimeMillis()
        val payStatus = when {
            !approved -> "REJECTED"
            order.farmerPaymentConfirmed -> "PAID"
            else -> "VERIFIED"
        }

        db.withTransaction {
            db.orderDao().updateAdminPaymentVerified(
                orderId = orderId,
                verified = approved,
                verifiedBy = admin.id,
                verifiedAt = now,
                paymentStatus = payStatus,
                now = now
            )
            // Security Rule: Only confirm order if Farmer has ALSO confirmed receiving money!
            if (approved && order.farmerPaymentConfirmed && order.status in setOf(
                    OrderStatus.ACCEPTED.name,
                    OrderStatus.PROCESSING.name,
                    OrderStatus.READY_FOR_PICKUP.name,
                    OrderStatus.SHIPPED.name,
                    OrderStatus.DELIVERED.name
                )
            ) {
                db.orderDao().updateOrderStatus(orderId, OrderStatus.CONFIRMED.name, now)
            }
        }

        val updatedOrder = db.orderDao().getOrderById(orderId)!!
        val items = db.orderDao().getOrderItemsOnce(orderId)
        rtdbSync.pushOrder(updatedOrder, items)

        val buyerNotif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = order.buyerId,
            targetRole = UserRole.BUYER.name,
            senderRole = UserRole.ADMIN.name,
            orderId = orderId,
            type = "ORDER_PAYMENT_VERIFICATION",
            titleBn = if (approved) "অর্ডার পেমেন্ট যাচাই সম্পন্ন (${order.orderCode})" else "অর্ডার পেমেন্ট যাচাই প্রত্যাখ্যাত (${order.orderCode})",
            messageBn = when {
                approved && updatedOrder.farmerPaymentConfirmed -> "অ্যাডমিন যাচাই এবং কৃষকের টাকা প্রাপ্তি নিশ্চিতকরণ সম্পন্ন হওয়ায় অর্ডারটি চূড়ান্তভাবে নিশ্চিত (Confirmed) হয়েছে।"
                approved -> "অ্যাডমিন পেমেন্ট যাচাই করেছেন। কৃষক টাকা বুঝে পাওয়ার নিশ্চিতকরণ দিলে অর্ডার চূড়ান্তভাবে Confirmed হবে।"
                else -> notes.ifBlank { "অ্যাডমিন পেমেন্ট যাচাইয়ে অমিল পেয়েছেন। অনুগ্রহ করে সঠিক তথ্য জমা দিন।" }
            },
            isRead = false,
            createdAt = now
        )
        db.notificationAuditDao().insertNotification(buyerNotif)
        rtdbSync.pushNotification(buyerNotif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "অর্ডার পেমেন্ট যাচাই ($payStatus)",
            targetEntity = "Order",
            targetId = orderId,
            detailsBn = "${order.orderCode} — $payStatus ${if (notes.isNotBlank()) "($notes)" else ""}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(updatedOrder)
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
        if (isBuyer && newStatus == OrderStatus.CANCELLED &&
            order.status !in setOf(OrderStatus.PENDING.name, OrderStatus.PAYMENT_PENDING.name)
        ) {
            return Result.failure(IllegalStateException("অর্ডার প্রক্রিয়াধীন হলে বাতিল করা যাবে না।"))
        }

        // Strict Security Rule: Order CANNOT be Confirmed or Completed using buyer screenshot alone!
        // Only Farmer's payment received confirmation is required (admin approval is NOT required).
        if (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.COMPLETED) {
            val isFarmerApproved = order.farmerPaymentConfirmed ||
                (newStatus == OrderStatus.COMPLETED && order.paymentStatus == "ZERO_ADVANCE_APPROVED")
            if (!isFarmerApproved) {
                return Result.failure(
                    IllegalStateException("শুধু ক্রেতার পেমেন্ট রসিদ বা স্ক্রিনশট দিয়ে অর্ডার কনফার্ম হবে না — কৃষক 'টাকা বুঝে পেয়েছি' অনুমোদন দিলে অর্ডার কনফার্ম বা সম্পন্ন হবে।")
                )
            }
        }

        val wasActive = order.status != OrderStatus.CANCELLED.name && order.status != OrderStatus.REJECTED.name
        val restoringStock = wasActive && (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.REJECTED)
        val restoredProducts = mutableListOf<ProductEntity>()

        db.withTransaction {
            db.orderDao().updateOrderStatus(orderId, newStatus.name, now)
            if (newStatus == OrderStatus.COMPLETED && order.paymentStatus != "PAID") {
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

    suspend fun adminVerifyFarmer(admin: UserEntity, farmerId: String, approved: Boolean): Result<Unit> =
        adminVerifyUser(admin, farmerId, approved)

    suspend fun adminVerifyUser(admin: UserEntity, userId: String, approved: Boolean): Result<Unit> {
        if (admin.role != UserRole.ADMIN.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সুপার অ্যাডমিন ব্যবহারকারী যাচাই করতে পারেন।"))
        }
        val targetUser = db.userDao().getUserById(userId)
            ?: return Result.failure(IllegalArgumentException("ব্যবহারকারী পাওয়া যায়নি।"))
        val isFarmer = targetUser.role == UserRole.FARMER.name
        val roleTitle = if (isFarmer) "কৃষক" else "ক্রেতা"
        val status = if (approved) "VERIFIED" else "REJECTED"
        val now = System.currentTimeMillis()
        val settings = getEffectivePaymentSettings()

        val updatedUser = if (approved) {
            val act = if (targetUser.activationDate > 0L) targetUser.activationDate else now
            val exp = if (targetUser.expiryDate > now) targetUser.expiryDate else addCalendarYears(now, settings.validityYears)
            targetUser.copy(
                verificationStatus = status,
                accountStatus = AccountStatus.ACTIVE.name,
                activationDate = act,
                expiryDate = exp,
                validityYears = settings.validityYears
            )
        } else {
            targetUser.copy(
                verificationStatus = status,
                accountStatus = AccountStatus.PAYMENT_REJECTED.name
            )
        }

        db.userDao().upsertUser(updatedUser)
        if (isFarmer) {
            db.productDao().updateFarmerVerifiedOnProducts(userId, approved)
            db.productDao().getProductsForFarmer(userId).forEach { rtdbSync.pushProduct(it) }
        }
        rtdbSync.pushUser(updatedUser)

        val notif = NotificationEntity(
            id = "not_${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            targetRole = targetUser.role,
            senderRole = UserRole.ADMIN.name,
            type = "VERIFICATION",
            titleBn = if (approved) "$roleTitle পরিচয় যাচাই ও অ্যাকাউন্ট সক্রিয় সম্পন্ন" else "$roleTitle পরিচয় যাচাই প্রত্যাখ্যাত",
            messageBn = if (approved) {
                "অভিনন্দন! আপনার $roleTitle অ্যাকাউন্টটি অ্যাডমিন কর্তৃক যাচাইকৃত ও ২ বছরের জন্য সক্রিয় হয়েছে।"
            } else {
                "অনুগ্রহ করে সঠিক তথ্য ও পেমেন্ট দিয়ে পুনরায় যাচাইয়ের জন্য আবেদন করুন।"
            },
            isRead = false
        )
        db.notificationAuditDao().insertNotification(notif)
        rtdbSync.pushNotification(notif)

        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "$roleTitle যাচাইকরণ ($status)",
            targetEntity = "User",
            targetId = userId,
            detailsBn = "${targetUser.fullName} ($roleTitle) — অ্যাডমিন দ্বারা $status করা হয়েছে"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(Unit)
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

    suspend fun adminSaveAdConfig(admin: UserEntity, config: AdConfig): Result<Unit> {
        if (admin.role != UserRole.ADMIN.name) {
            return Result.failure(IllegalAccessException("শুধুমাত্র সুপার অ্যাডমিন বিজ্ঞাপন সেটিংস পরিবর্তন করতে পারেন।"))
        }
        val stamped = config.copy(updatedAt = System.currentTimeMillis())
        _adConfig.value = stamped
        rtdbSync.pushAdConfig(stamped)
        val audit = AuditLogEntity(
            id = "aud_${UUID.randomUUID().toString().take(8)}",
            actorId = admin.id,
            actorRole = admin.role,
            actionBn = "বিজ্ঞাপন ও মনিটাইজেশন আপডেট",
            targetEntity = "AdConfig",
            targetId = "adConfig",
            detailsBn = "Android Ads: ${if (stamped.androidAdsEnabled) "চালু" else "বন্ধ"}, AdSense: ${if (stamped.adsenseEnabled) "চালু" else "বন্ধ"}, Adsterra: ${if (stamped.adsterraEnabled) "চালু" else "বন্ধ"}, Monetag: ${if (stamped.monetagEnabled) "চালু" else "বন্ধ"}"
        )
        db.notificationAuditDao().insertAuditLog(audit)
        rtdbSync.pushAuditLog(audit)
        return Result.success(Unit)
    }

    suspend fun markNotificationsRead(activeUser: UserEntity?) {
        if (activeUser == null) return
        val dao = db.notificationAuditDao()
        val toMark = if (activeUser.role == UserRole.ADMIN.name) {
            val list = dao.getUnreadNotifications()
            dao.markAllNotificationsRead()
            list
        } else {
            val list = dao.getUnreadNotificationsForUser(activeUser.id, activeUser.role)
            dao.markNotificationsReadForUser(activeUser.id, activeUser.role)
            list
        }
        toMark.forEach { rtdbSync.pushNotificationRead(it.id) }
    }
}
