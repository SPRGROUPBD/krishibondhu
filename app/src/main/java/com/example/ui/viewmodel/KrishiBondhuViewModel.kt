package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.FirebaseRealtimeSyncManager
import com.example.data.repository.MarketplaceRepository
import com.example.notifications.KrishiNotificationHelper
import com.example.util.NetworkMonitor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MarketplaceUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val isOnline: Boolean = true,
    val activeUser: UserEntity? = null,
    val language: AppLanguage = AppLanguage.BENGALI,
    val darkMode: Boolean = false,
    val users: List<UserEntity> = emptyList(),
    val farmers: List<UserEntity> = emptyList(),
    val buyers: List<UserEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val cartItems: List<CartItemEntity> = emptyList(),
    val orders: List<OrderEntity> = emptyList(),
    val reviews: List<ReviewEntity> = emptyList(),
    val complaints: List<ComplaintEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val activationPayments: List<ActivationPaymentEntity> = emptyList(),
    val paymentSettings: PaymentSettingsEntity = PaymentSettingsEntity(),
    val farmerPaymentSettings: List<FarmerPaymentSettingsEntity> = emptyList(),
    val orderPayments: List<OrderPaymentEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryId: String? = null,
    val selectedDivision: String? = null,
    val selectedDistrict: String? = null,
    val selectedUpazila: String? = null,
    val selectedUnion: String? = null,
    val wholesaleOnly: Boolean? = true,
    val priceSort: PriceSort = PriceSort.NEWEST,
    val adConfig: AdConfig = AdConfig(),
    val toastMessage: String? = null
) {
    val activeRole: UserRole?
        get() = activeUser?.let {
            runCatching { UserRole.valueOf(it.role) }.getOrNull()
        }

    val isSuperAdmin: Boolean
        get() = activeRole == UserRole.ADMIN

    val visibleNotifications: List<NotificationEntity>
        get() {
            val user = activeUser
            if (isSuperAdmin) return notifications
            return notifications.filter { n ->
                if (user == null) {
                    n.userId == "ALL" && n.targetRole == "ALL"
                } else {
                    n.userId == user.id ||
                        (n.userId == "ALL" && (n.targetRole == "ALL" || n.targetRole.isBlank() || n.targetRole == user.role))
                }
            }
        }

    val filteredProducts: List<ProductEntity>
        get() {
            val q = searchQuery.normalizeBn()
            val div = selectedDivision?.normalizeBn()?.takeIf { it.isNotBlank() }
            val dist = selectedDistrict?.normalizeBn()?.takeIf { it.isNotBlank() }
            val upz = selectedUpazila?.normalizeBn()?.takeIf { it.isNotBlank() }
            val union = selectedUnion?.normalizeBn()?.takeIf { it.isNotBlank() }

            val base = products.filter { p ->
                val matchesQuery = q.isBlank() ||
                    p.nameBn.containsBn(q) ||
                    p.nameEn.containsBn(q) ||
                    p.descriptionBn.containsBn(q) ||
                    p.farmerName.containsBn(q) ||
                    p.division.containsBn(q) ||
                    p.district.containsBn(q) ||
                    p.upazila.containsBn(q) ||
                    p.unionName.containsBn(q) ||
                    p.village.containsBn(q) ||
                    p.pickupAddress.containsBn(q) ||
                    p.categoryNameBn.containsBn(q)
                val matchesCat = selectedCategoryId == null || p.categoryId == selectedCategoryId
                val matchesDiv = div == null || p.division.equalsBn(div)
                val matchesDist = dist == null || p.district.equalsBn(dist)
                val matchesUpazila = upz == null || p.upazila.equalsBn(upz)
                val matchesUnion = union == null ||
                    p.unionName.equalsBn(union) ||
                    p.village.containsBn(union) ||
                    p.pickupAddress.containsBn(union)
                matchesQuery && matchesCat && matchesDiv && matchesDist && matchesUpazila && matchesUnion &&
                    p.status.equals("ACTIVE", ignoreCase = true)
            }
            return when (priceSort) {
                PriceSort.NEWEST -> base.sortedByDescending { it.createdAt }
                PriceSort.PRICE_LOW_HIGH -> base.sortedBy { it.pricePerUnit }
                PriceSort.PRICE_HIGH_LOW -> base.sortedByDescending { it.pricePerUnit }
            }
        }

    val filteredFarmers: List<UserEntity>
        get() {
            val q = searchQuery.normalizeBn()
            val div = selectedDivision?.normalizeBn()?.takeIf { it.isNotBlank() }
            val dist = selectedDistrict?.normalizeBn()?.takeIf { it.isNotBlank() }
            val upz = selectedUpazila?.normalizeBn()?.takeIf { it.isNotBlank() }
            val union = selectedUnion?.normalizeBn()?.takeIf { it.isNotBlank() }

            return farmers.filter { f ->
                val matchesQuery = q.isBlank() ||
                    f.fullName.containsBn(q) ||
                    f.division.containsBn(q) ||
                    f.district.containsBn(q) ||
                    f.upazila.containsBn(q) ||
                    f.unionName.containsBn(q) ||
                    f.village.containsBn(q) ||
                    f.farmBio.containsBn(q)
                val matchesDiv = div == null || f.division.equalsBn(div)
                val matchesDist = dist == null || f.district.equalsBn(dist)
                val matchesUpazila = upz == null || f.upazila.equalsBn(upz)
                val matchesUnion = union == null ||
                    f.unionName.equalsBn(union) ||
                    f.village.containsBn(union)
                matchesQuery && matchesDiv && matchesDist && matchesUpazila && matchesUnion
            }
        }

    val unreadNotificationCount: Int
        get() = visibleNotifications.count { !it.isRead }

    val cartBadgeCount: Int
        get() = cartItems.size

    val adminStats: AdminDashboardStats
        get() {
            val totalFarmers = farmers.size
            val verifiedFarmers = farmers.count { it.verificationStatus == "VERIFIED" }
            val totalBuyers = buyers.size
            val verifiedBuyers = buyers.count { it.verificationStatus == "VERIFIED" }
            val activeProds = products.count { it.status == "ACTIVE" }
            val pendingProds = products.count { it.status == "PENDING_APPROVAL" }
            val dayAgo = System.currentTimeMillis() - 86_400_000L
            val todaysOrders = orders.count { it.createdAt >= dayAgo }
            val completedOrders = orders.count { it.status == OrderStatus.COMPLETED.name }
            val totalSales = orders.filter { it.status != OrderStatus.CANCELLED.name && it.status != OrderStatus.REJECTED.name }
                .sumOf { it.productTotal }
            val districtSales = orders
                .filter { it.status != OrderStatus.CANCELLED.name && it.status != OrderStatus.REJECTED.name }
                .groupBy { it.district.ifBlank { "অন্যান্য" } }
                .mapValues { entry -> entry.value.sumOf { it.productTotal } }
            val nonAdmins = users.filter { it.role != UserRole.ADMIN.name }
            val pendingPays = activationPayments.count { it.status == ActivationPaymentStatus.PENDING.name }
            val approvedPays = activationPayments.count { it.status == ActivationPaymentStatus.APPROVED.name }
            val rejectedPays = activationPayments.count {
                it.status == ActivationPaymentStatus.REJECTED.name || it.status == ActivationPaymentStatus.RESUBMISSION_REQUESTED.name
            }
            val activeAcc = nonAdmins.count { it.isMarketplaceActive() }
            val expiredAcc = nonAdmins.count { it.accountStatus == AccountStatus.EXPIRED.name }
            val suspendedAcc = nonAdmins.count { it.accountStatus == AccountStatus.SUSPENDED.name }
            val pendingPaymentVerifs = orderPayments.count { it.status == OrderPaymentStatus.PENDING_VERIFICATION.name }
            val legacyUnconfirmed = orders.count {
                it.status !in setOf(OrderStatus.PENDING.name, OrderStatus.PAYMENT_PENDING.name, OrderStatus.CANCELLED.name, OrderStatus.REJECTED.name, OrderStatus.COMPLETED.name) &&
                    !it.farmerPaymentConfirmed &&
                    orderPayments.none { op -> op.orderId == it.id }
            }
            val pendingOrderVerifs = pendingPaymentVerifs + legacyUnconfirmed
            return AdminDashboardStats(
                totalFarmers = totalFarmers,
                verifiedFarmers = verifiedFarmers,
                totalBuyers = totalBuyers,
                verifiedBuyers = verifiedBuyers,
                activeProducts = activeProds,
                pendingProductsCount = pendingProds,
                totalProductsCount = products.size,
                todaysOrders = todaysOrders,
                completedOrders = completedOrders,
                totalSalesAmount = totalSales,
                districtSales = districtSales,
                pendingActivationPayments = pendingPays,
                approvedActivationPayments = approvedPays,
                rejectedActivationPayments = rejectedPays,
                activeAccounts = activeAcc,
                expiredAccounts = expiredAcc,
                suspendedAccounts = suspendedAcc,
                pendingOrderPaymentVerifications = pendingOrderVerifs
            )
        }
}

class KrishiBondhuViewModel(application: Application) : AndroidViewModel(application) {
    private val db = KrishiBondhuDatabase.getInstance(application)
    private val prefs = UserPreferences(application)
    private val repo = MarketplaceRepository(
        db = db,
        prefs = prefs,
        rtdbSync = FirebaseRealtimeSyncManager(enableRemoteSync = true, appContext = application)
    )

    private val filterState = MutableStateFlow(FilterParams())
    private val toastFlow = MutableStateFlow<String?>(null)
    private val notifiedIds = mutableSetOf<String>()

    private data class FilterParams(
        val query: String = "",
        val categoryId: String? = null,
        val division: String? = null,
        val district: String? = null,
        val upazila: String? = null,
        val unionName: String? = null,
        val wholesaleOnly: Boolean? = null,
        val priceSort: PriceSort = PriceSort.NEWEST
    )

    private val launchTimestamp = System.currentTimeMillis()

    private val coreDataFlow = combine(
        repo.sessionPrefs,
        repo.allUsers,
        repo.categories,
        repo.locations,
        repo.products
    ) { session, users, cats, locs, prods ->
        CoreBundle(session, users, cats, locs, prods)
    }

    private val commerceDataFlow = combine(
        repo.cartItems,
        repo.orders,
        repo.reviews,
        repo.complaints,
        repo.notifications
    ) { cart, orders, reviews, complaints, notifs ->
        CommerceBundle(cart, orders, reviews, complaints, notifs)
    }

    private val syncExtrasFlow = combine(
        NetworkMonitor.observeIsOnline(application),
        repo.adConfig,
        repo.isSyncing,
        repo.syncError
    ) { online, adCfg, syncing, syncErr ->
        SyncStatusBundle(online, adCfg, syncing, syncErr)
    }

    private val paymentBundlesFlow = combine(
        repo.paymentSettings,
        repo.farmerPaymentSettings,
        repo.orderPayments
    ) { paySettings, farmerPaySettings, ordPayments ->
        PaymentBundle(paySettings, farmerPaySettings, ordPayments)
    }

    private val extrasFlow = combine(
        repo.auditLogs,
        repo.activationPayments,
        paymentBundlesFlow,
        toastFlow,
        syncExtrasFlow
    ) { audits, actPayments, payBundle, toast, syncBundle ->
        ExtrasBundle(
            audits = audits,
            activationPayments = actPayments,
            paymentSettings = payBundle.paymentSettings,
            farmerPaymentSettings = payBundle.farmerPaymentSettings,
            orderPayments = payBundle.orderPayments,
            toast = toast,
            online = syncBundle.online,
            adConfig = syncBundle.adConfig,
            isSyncing = syncBundle.isSyncing,
            syncError = syncBundle.syncError
        )
    }

    val uiState: StateFlow<MarketplaceUiState> = combine(
        coreDataFlow,
        commerceDataFlow,
        extrasFlow,
        filterState
    ) { core, commerce, extras, filter ->
        val activeUser = if (core.session.activeUserId.isNotBlank()) {
            core.users.firstOrNull { it.id == core.session.activeUserId }
        } else {
            null
        }
        MarketplaceUiState(
            isLoading = core.categories.isEmpty(),
            isSyncing = extras.isSyncing,
            syncError = extras.syncError,
            isOnline = extras.online,
            activeUser = activeUser,
            language = core.session.language,
            darkMode = core.session.darkMode,
            users = core.users,
            farmers = core.users.filter { it.role == "FARMER" },
            buyers = core.users.filter { it.role == "BUYER" },
            categories = core.categories,
            locations = core.locations,
            products = core.products,
            cartItems = commerce.cart,
            orders = commerce.orders,
            reviews = commerce.reviews,
            complaints = commerce.complaints,
            notifications = commerce.notifications,
            auditLogs = extras.audits,
            activationPayments = extras.activationPayments,
            paymentSettings = extras.paymentSettings,
            farmerPaymentSettings = extras.farmerPaymentSettings,
            orderPayments = extras.orderPayments,
            searchQuery = filter.query,
            selectedCategoryId = filter.categoryId,
            selectedDivision = filter.division,
            selectedDistrict = filter.district,
            selectedUpazila = filter.upazila,
            selectedUnion = filter.unionName,
            wholesaleOnly = true,
            priceSort = filter.priceSort,
            adConfig = extras.adConfig,
            toastMessage = extras.toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MarketplaceUiState(isLoading = true)
    )

    init {
        KrishiNotificationHelper.ensureChannel(application)
        viewModelScope.launch {
            repo.initializeCatalogsAndSync(viewModelScope) { incomingNotif ->
                val current = uiState.value.activeUser
                val matchesActive = current != null && current.role != UserRole.ADMIN.name && (
                    incomingNotif.userId == current.id ||
                        (incomingNotif.userId == "ALL" && (
                            incomingNotif.targetRole.isBlank() ||
                                incomingNotif.targetRole == "ALL" ||
                                incomingNotif.targetRole == current.role
                            ))
                    )
                val isRecent = incomingNotif.createdAt >= launchTimestamp - 30_000L
                if (matchesActive && isRecent && notifiedIds.add(incomingNotif.id)) {
                    KrishiNotificationHelper.showLocalNotification(
                        context = getApplication(),
                        title = incomingNotif.titleBn,
                        message = incomingNotif.messageBn,
                        notificationKey = incomingNotif.id
                    )
                }
            }
        }
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            NetworkMonitor.observeIsOnline(application).collect { online ->
                val needsReconnectSync = wasOnline == false ||
                    (wasOnline == true && !repo.isSyncing.value && uiState.value.products.isEmpty())
                if (online && needsReconnectSync) {
                    repo.syncNow()
                }
                wasOnline = online
            }
        }
    }

    fun observeOrderItems(orderId: String): Flow<List<OrderItemEntity>> =
        repo.observeOrderItems(orderId)

    fun observeOrderPayments(orderId: String): Flow<List<OrderPaymentEntity>> =
        repo.observeOrderPayments(orderId)

    fun observeFarmerPaymentSettings(farmerId: String): Flow<FarmerPaymentSettingsEntity?> =
        repo.observeFarmerPaymentSettings(farmerId)

    fun consumeToast() {
        toastFlow.value = null
    }

    fun showToast(msg: String) {
        toastFlow.value = msg
    }

    fun setSearchQuery(q: String) {
        filterState.update { it.copy(query = q) }
    }

    fun setCategoryFilter(catId: String?) {
        filterState.update { it.copy(categoryId = catId) }
    }

    fun setDivisionFilter(division: String?) {
        filterState.update { it.copy(division = division, district = null, upazila = null, unionName = null) }
    }

    fun setDistrictFilter(district: String?) {
        filterState.update { it.copy(district = district, upazila = null, unionName = null) }
    }

    fun setUpazilaFilter(upazila: String?) {
        filterState.update { it.copy(upazila = upazila, unionName = null) }
    }

    fun setUnionFilter(unionName: String?) {
        filterState.update { it.copy(unionName = unionName) }
    }

    fun setLocationFilters(division: String?, district: String?, upazila: String?, unionName: String? = null) {
        filterState.update {
            it.copy(
                division = division,
                district = district,
                upazila = upazila,
                unionName = unionName
            )
        }
    }

    fun applyMyLocationFilter() {
        val user = uiState.value.activeUser ?: return
        filterState.update {
            it.copy(
                division = user.division.ifBlank { null },
                district = user.district.ifBlank { null },
                upazila = user.upazila.ifBlank { null },
                unionName = user.unionName.ifBlank { null }
            )
        }
        showToast("আপনার নিজস্ব এলাকার ফসল দেখানো হচ্ছে ✓")
    }

    fun setWholesaleFilter(wholesaleOnly: Boolean?) {
        filterState.update { it.copy(wholesaleOnly = wholesaleOnly) }
    }

    fun setPriceSort(sort: PriceSort) {
        filterState.update { it.copy(priceSort = sort) }
    }

    fun resetFilters() {
        filterState.value = FilterParams()
    }

    fun toggleLanguage() {
        val next = if (uiState.value.language == AppLanguage.BENGALI) AppLanguage.ENGLISH else AppLanguage.BENGALI
        viewModelScope.launch {
            repo.setLanguage(next)
            showToast(if (next == AppLanguage.BENGALI) "ভাষা বাংলায় পরিবর্তন করা হয়েছে" else "Language switched to English")
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repo.setDarkMode(enabled) }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logoutUser()
            showToast("সফলভাবে লগআউট সম্পন্ন হয়েছে")
        }
    }

    fun login(identifier: String, password: String, onSuccess: (UserEntity) -> Unit) {
        viewModelScope.launch {
            repo.loginUser(identifier, password)
                .onSuccess { user ->
                    showToast("স্বাগতম, ${user.fullName}!")
                    onSuccess(user)
                }
                .onFailure { showToast(it.message ?: "লগইন ব্যর্থ হয়েছে") }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val ok = repo.syncNow()
            if (!ok) {
                showToast("সার্ভার থেকে তথ্য আনা যায়নি। ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।")
            } else {
                showToast("সর্বশেষ তথ্য আপডেট সম্পন্ন হয়েছে ✓")
            }
        }
    }

    fun resetPassword(phone: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repo.resetPasswordByPhone(phone, newPassword)
                .onSuccess {
                    showToast("পাসওয়ার্ড সফলভাবে পরিবর্তন হয়েছে! নতুন পাসওয়ার্ড দিয়ে লগইন করুন।")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "পাসওয়ার্ড পরিবর্তন করা যায়নি") }
        }
    }

    fun updateProfile(
        fullName: String,
        division: String,
        district: String,
        upazila: String,
        unionName: String,
        village: String,
        farmBio: String,
        nidLastFour: String,
        profileImageUri: String,
        onSuccess: () -> Unit
    ) {
        val currentUser = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.updateUserProfile(
                userId = currentUser.id,
                fullName = fullName,
                division = division,
                district = district,
                upazila = upazila,
                unionName = unionName,
                village = village,
                farmBio = farmBio,
                nidLastFour = nidLastFour,
                profileImageUri = profileImageUri
            )
                .onSuccess {
                    showToast("আপনার প্রোফাইল সফলভাবে আপডেট হয়েছে ✓")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "প্রোফাইল আপডেট করা যায়নি") }
        }
    }

    fun register(
        fullName: String,
        phone: String,
        password: String,
        confirmPassword: String,
        role: UserRole,
        division: String,
        district: String,
        upazila: String,
        unionName: String,
        village: String,
        nidLastFour: String,
        farmBio: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repo.registerUser(
                fullName = fullName,
                phone = phone,
                rawPassword = password,
                role = role,
                division = division,
                district = district,
                upazila = upazila,
                unionName = unionName,
                village = village,
                nidLastFour = nidLastFour,
                farmBio = farmBio,
                confirmPassword = confirmPassword
            )
                .onSuccess {
                    showToast("নিবন্ধন সম্পন্ন হয়েছে! অ্যাকাউন্ট সক্রিয় করতে ৩০০ টাকা পেমেন্ট জমা দিন।")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "নিবন্ধন ব্যর্থ হয়েছে") }
        }
    }

    fun submitActivationPayment(
        paymentMethod: PaymentMethod,
        senderMobile: String,
        transactionId: String,
        amount: Double,
        receiptUri: String,
        isRenewal: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val user = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.submitActivationPayment(
                user = user,
                paymentMethod = paymentMethod,
                senderMobile = senderMobile,
                transactionId = transactionId,
                amount = amount,
                receiptUri = receiptUri,
                isRenewal = isRenewal
            )
                .onSuccess {
                    showToast("পেমেন্ট তথ্য জমা হয়েছে! অ্যাডমিন যাচাইয়ের পর ২ বছরের জন্য সক্রিয় হবে ✓")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "পেমেন্ট জমা দেওয়া যায়নি") }
        }
    }

    fun adminApproveActivationPayment(paymentId: String, notes: String = "") {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminApproveActivationPayment(admin, paymentId, notes)
                .onSuccess { showToast("${it.fullName}-এর অ্যাকাউন্ট ২ বছরের জন্য সক্রিয় হয়েছে ✓") }
                .onFailure { showToast(it.message ?: "অনুমোদন করা যায়নি") }
        }
    }

    fun adminRejectActivationPayment(paymentId: String, reason: String, requestResubmission: Boolean = false) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminRejectActivationPayment(admin, paymentId, reason, requestResubmission)
                .onSuccess {
                    showToast(
                        if (requestResubmission) "পুনরায় পেমেন্ট জমার অনুরোধ পাঠানো হয়েছে"
                        else "পেমেন্ট আবেদন প্রত্যাখ্যাত হয়েছে"
                    )
                }
                .onFailure { showToast(it.message ?: "প্রক্রিয়া ব্যর্থ হয়েছে") }
        }
    }

    fun adminSetAccountStatus(userId: String, status: AccountStatus, reason: String = "") {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminSetAccountStatus(admin, userId, status, reason)
                .onSuccess { showToast("${it.fullName} -> ${status.display(uiState.value.language)}") }
                .onFailure { showToast(it.message ?: "অবস্থা পরিবর্তন করা যায়নি") }
        }
    }

    fun adminSavePaymentSettings(settings: PaymentSettingsEntity) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminSavePaymentSettings(admin, settings)
                .onSuccess { showToast("অ্যাক্টিভেশন পেমেন্ট সেটিংস সংরক্ষিত হয়েছে ✓") }
                .onFailure { showToast(it.message ?: "পেমেন্ট সেটিংস সংরক্ষণ ব্যর্থ হয়েছে") }
        }
    }

    fun addToCart(product: ProductEntity, qty: Double = 1.0) {
        viewModelScope.launch {
            repo.addToCart(product, qty, uiState.value.activeUser)
                .onSuccess { showToast("${product.nameBn} (${qty.formatQty()} মণ) কার্টে যোগ করা হয়েছে") }
                .onFailure { showToast(it.message ?: "কার্টে যোগ করা যায়নি") }
        }
    }

    fun updateCartQty(item: CartItemEntity, newQty: Double) {
        viewModelScope.launch {
            repo.updateCartQuantity(item.id, newQty, item.maxAvailableQuantity)
                .onFailure { showToast(it.message ?: "পরিমাণ পরিবর্তন করা যায়নি") }
        }
    }

    fun removeCartItem(itemId: String) {
        viewModelScope.launch { repo.removeFromCart(itemId) }
    }

    fun saveFarmerPaymentSettings(
        bKashNumber: String,
        nagadNumber: String,
        rocketNumber: String,
        bankAccountInfo: String,
        onSuccess: () -> Unit = {}
    ) {
        val farmer = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.saveFarmerPaymentSettings(farmer, bKashNumber, nagadNumber, rocketNumber, bankAccountInfo)
                .onSuccess {
                    showToast("সরাসরি পেমেন্ট নম্বর (bKash / Nagad / Rocket / Bank) সংরক্ষিত হয়েছে ✓")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "পেমেন্ট সেটিংস সংরক্ষণ করা যায়নি") }
        }
    }

    fun placeOrders(
        deliveryOption: DeliveryOption,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        transactionRef: String,
        advanceByFarmer: Map<String, Double> = emptyMap(),
        senderNumber: String = "",
        paymentReferenceNote: String = "",
        onRequireAuth: () -> Unit,
        onSuccess: (List<OrderEntity>) -> Unit
    ) {
        val buyer = uiState.value.activeUser
        if (buyer == null) {
            showToast("অর্ডার করতে অনুগ্রহ করে লগইন বা নিবন্ধন করুন")
            onRequireAuth()
            return
        }
        viewModelScope.launch {
            repo.placeOrdersFromCart(
                buyer = buyer,
                deliveryOption = deliveryOption,
                deliveryAddress = deliveryAddress,
                paymentMethod = paymentMethod,
                transactionRef = transactionRef,
                advanceByFarmer = advanceByFarmer,
                senderNumber = senderNumber,
                paymentReferenceNote = paymentReferenceNote
            )
                .onSuccess { orders ->
                    showToast("${orders.size}টি পাইকারি অর্ডার পাঠানো হয়েছে ও কৃষককে নোটিফিকেশন পাঠানো হয়েছে!")
                    onSuccess(orders)
                }
                .onFailure { showToast(it.message ?: "অর্ডার সম্পন্ন হয়নি") }
        }
    }

    fun addFarmerProduct(
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
        isWholesale: Boolean,
        onSuccess: () -> Unit
    ) {
        val farmer = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.createFarmerProduct(
                farmer, category, nameBn, descriptionBn, imageUri, quantity, unit,
                pricePerUnit, harvestDate, availabilityDate, pickupAddress,
                supportsFarmPickup, supportsDirectDelivery, deliveryCharge, isWholesale
            )
                .onSuccess {
                    showToast("আপনার ফসল জমা হয়েছে! অ্যাডমিন যাচাই ও অনুমোদনের পর সবার জন্য প্রকাশিত হবে ✓")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "ফসল যুক্ত করা যায়নি") }
        }
    }

    fun updateFarmerPriceAndStock(productId: String, newPrice: Double, newStock: Double) {
        val farmerId = uiState.value.activeUser?.id ?: return
        viewModelScope.launch {
            repo.updateFarmerPriceAndStock(productId, farmerId, newPrice, newStock)
                .onSuccess { showToast("দাম ও মজুত আপডেট হয়েছে") }
                .onFailure { showToast(it.message ?: "আপডেট ব্যর্থ হয়েছে") }
        }
    }

    fun adminVerifyProduct(productId: String, approved: Boolean, reason: String = "") {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminVerifyProduct(admin, productId, approved, reason)
                .onSuccess {
                    showToast(if (approved) "ফসল অনুমোদিত ও সবার জন্য উন্মুক্ত করা হয়েছে ✓" else "ফসল প্রত্যাখ্যান করা হয়েছে")
                }
                .onFailure { showToast(it.message ?: "ফসল যাচাই করা যায়নি") }
        }
    }

    fun adminUpdateProduct(
        productId: String,
        nameBn: String,
        category: CategoryEntity,
        pricePerUnit: Double,
        availableQuantity: Double,
        descriptionBn: String,
        status: String
    ) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminUpdateProduct(admin, productId, nameBn, category, pricePerUnit, availableQuantity, descriptionBn, status)
                .onSuccess { showToast("ফসলের তথ্য অ্যাডমিন কর্তৃক আপডেট হয়েছে ✓") }
                .onFailure { showToast(it.message ?: "ফসল আপডেট করা যায়নি") }
        }
    }

    fun deleteProduct(productId: String) {
        val actor = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.deleteProduct(actor, productId)
                .onSuccess { showToast("ফসলটি তালিকা থেকে মুছে ফেলা হয়েছে ✓") }
                .onFailure { showToast(it.message ?: "ফসল মুছে ফেলা যায়নি") }
        }
    }

    fun updateOrderStatus(orderId: String, status: OrderStatus) {
        val actor = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.updateOrderStatus(orderId, status, actor)
                .onSuccess { showToast("অর্ডার অবস্থা: ${status.display(uiState.value.language)}") }
                .onFailure { showToast(it.message ?: "অবস্থা পরিবর্তন করা যায়নি") }
        }
    }

    fun submitOrderPayment(
        orderId: String,
        paymentMethod: PaymentMethod,
        senderNumber: String,
        transactionReference: String,
        receiptUri: String,
        onSuccess: () -> Unit = {}
    ) {
        val buyer = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.submitOrderPayment(
                orderId = orderId,
                buyer = buyer,
                paymentMethod = paymentMethod,
                senderNumber = senderNumber,
                transactionReference = transactionReference,
                receiptUri = receiptUri
            )
                .onSuccess {
                    showToast("পেমেন্ট তথ্য ও রসিদ জমা হয়েছে! কৃষক টাকা প্রাপ্তি অনুমোদন করলে অর্ডার Confirmed হবে ✓")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "পেমেন্ট জমা দেওয়া যায়নি") }
        }
    }

    fun farmerConfirmPaymentReceived(orderId: String) {
        val farmer = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.farmerConfirmPaymentReceived(orderId, farmer)
                .onSuccess {
                    showToast("টাকা প্রাপ্তি নিশ্চিত ও অনুমোদিত হয়েছে — অর্ডার চূড়ান্ত Confirmed সম্পন্ন ✓")
                }
                .onFailure { showToast(it.message ?: "নিশ্চিতকরণ করা যায়নি") }
        }
    }

    fun submitOrderAdvancePayment(
        orderId: String,
        amount: Double,
        paymentMethod: PaymentMethod,
        senderNumber: String,
        transactionId: String,
        referenceNote: String = "",
        onSuccess: () -> Unit = {}
    ) {
        val buyer = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.submitOrderAdvancePayment(
                orderId = orderId,
                buyer = buyer,
                amount = amount,
                paymentMethod = paymentMethod,
                senderNumber = senderNumber,
                transactionId = transactionId,
                referenceNote = referenceNote
            )
                .onSuccess {
                    showToast("অগ্রিম/আংশিক পেমেন্ট (${it.amount.formatBdt()}) জমা হয়েছে! কৃষক যাচাইয়ের পর যুক্ত হবে ✓")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "পেমেন্ট জমা দেওয়া যায়নি") }
        }
    }

    fun farmerVerifyOrderPayment(
        paymentId: String,
        approved: Boolean,
        rejectionReason: String = ""
    ) {
        val farmer = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.farmerVerifyOrderPayment(paymentId, farmer, approved, rejectionReason)
                .onSuccess { updated ->
                    showToast(
                        if (approved) "পেমেন্ট নিশ্চিত হয়েছে ✓ অবশিষ্ট বকেয়া: ${updated.remainingDue.formatBdt()}"
                        else "পেমেন্ট প্রত্যাখ্যাত হয়েছে"
                    )
                }
                .onFailure { showToast(it.message ?: "যাচাই করা যায়নি") }
        }
    }

    fun approveZeroAdvanceOrder(orderId: String) {
        val farmer = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.approveZeroAdvanceOrder(orderId, farmer)
                .onSuccess { updated ->
                    showToast("শূন্য-অগ্রিম অর্ডার অনুমোদিত ও Confirmed হয়েছে ✓ (বকেয়া: ${updated.remainingDue.formatBdt()})")
                }
                .onFailure { showToast(it.message ?: "অনুমোদন করা যায়নি") }
        }
    }

    fun adminVerifyOrderPayment(orderId: String, approved: Boolean, notes: String = "") {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminVerifyOrderPayment(orderId, admin, approved, notes)
                .onSuccess {
                    showToast(if (approved) "অর্ডার পেমেন্ট যাচাই সম্পন্ন ✓" else "অর্ডার পেমেন্ট প্রত্যাখ্যাত হয়েছে")
                }
                .onFailure { showToast(it.message ?: "যাচাই ব্যর্থ হয়েছে") }
        }
    }

    fun submitReview(order: OrderEntity, productId: String, rating: Int, comment: String) {
        val buyer = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.submitReview(order, productId, buyer, rating, comment)
            showToast("আপনার রিভিউ যুক্ত হয়েছে, ধন্যবাদ!")
        }
    }

    fun submitComplaint(orderId: String, subject: String, description: String) {
        val reporter = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.submitComplaint(orderId, reporter, subject, description)
            showToast("অভিযোগ অ্যাডমিন প্যানেলে পাঠানো হয়েছে")
        }
    }

    fun adminVerifyFarmer(farmerId: String, approved: Boolean) {
        adminVerifyUser(farmerId, approved)
    }

    fun adminVerifyUser(userId: String, approved: Boolean) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminVerifyUser(admin, userId, approved)
            showToast(if (approved) "অ্যাকাউন্ট যাচাই সম্পন্ন হয়েছে ✓" else "যাচাই আবেদন প্রত্যাখ্যান করা হয়েছে")
        }
    }

    fun adminSendNotification(
        targetUserId: String,
        targetRole: String,
        titleBn: String,
        messageBn: String
    ) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminSendNotification(admin, targetUserId, targetRole, titleBn, messageBn)
                .onSuccess { showToast("নোটিফিকেশন সফলভাবে পাঠানো হয়েছে!") }
                .onFailure { showToast(it.message ?: "নোটিফিকেশন পাঠানো যায়নি") }
        }
    }

    fun adminAddCategory(nameBn: String, nameEn: String, emoji: String) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminAddCategory(admin, nameBn, nameEn, emoji)
            showToast("নতুন ক্যাটাগরি যোগ হয়েছে")
        }
    }

    fun adminAddLocation(division: String, district: String, upazila: String, union: String, village: String) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminAddLocation(admin, division, district, upazila, union, village)
            showToast("নতুন এলাকা যুক্ত হয়েছে: $upazila, $district")
        }
    }

    fun adminResolveComplaint(complaintId: String) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminResolveComplaint(admin, complaintId)
            showToast("অভিযোগ সমাধান হিসেবে চিহ্নিত")
        }
    }

    fun adminSaveAdConfig(config: AdConfig) {
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminSaveAdConfig(admin, config)
                .onSuccess { showToast("বিজ্ঞাপন ও মনিটাইজেশন সেটিংস সংরক্ষিত হয়েছে!") }
                .onFailure { showToast(it.message ?: "বিজ্ঞাপন সেটিংস সংরক্ষণ ব্যর্থ হয়েছে") }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { repo.markNotificationsRead(uiState.value.activeUser) }
    }

    fun buildCsvReport(): String {
        val sb = StringBuilder()
        sb.appendLine("OrderCode,Farmer,Buyer,Division,District,Upazila,ProductTotalBDT,DeliveryChargeBDT,GrandTotalBDT,PaymentMethod,Status")
        uiState.value.orders.forEach { o ->
            sb.appendLine("${o.orderCode},\"${o.farmerName}\",\"${o.buyerName}\",\"${o.division}\",\"${o.district}\",\"${o.upazila}\",${o.productTotal},${o.deliveryCharge},${o.grandTotal},\"${o.paymentMethod}\",${o.status}")
        }
        return sb.toString()
    }

    private data class CoreBundle(
        val session: UserSessionPrefs,
        val users: List<UserEntity>,
        val categories: List<CategoryEntity>,
        val locations: List<LocationEntity>,
        val products: List<ProductEntity>
    )

    private data class CommerceBundle(
        val cart: List<CartItemEntity>,
        val orders: List<OrderEntity>,
        val reviews: List<ReviewEntity>,
        val complaints: List<ComplaintEntity>,
        val notifications: List<NotificationEntity>
    )
    private data class SyncStatusBundle(
        val online: Boolean,
        val adConfig: AdConfig,
        val isSyncing: Boolean,
        val syncError: String?
    )

    private data class PaymentBundle(
        val paymentSettings: PaymentSettingsEntity,
        val farmerPaymentSettings: List<FarmerPaymentSettingsEntity>,
        val orderPayments: List<OrderPaymentEntity>
    )

    private data class ExtrasBundle(
        val audits: List<AuditLogEntity>,
        val activationPayments: List<ActivationPaymentEntity>,
        val paymentSettings: PaymentSettingsEntity,
        val farmerPaymentSettings: List<FarmerPaymentSettingsEntity>,
        val orderPayments: List<OrderPaymentEntity>,
        val toast: String?,
        val online: Boolean,
        val adConfig: AdConfig,
        val isSyncing: Boolean,
        val syncError: String?
    )
}
