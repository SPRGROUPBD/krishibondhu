package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.repository.MarketplaceRepository
import com.example.notifications.KrishiNotificationHelper
import com.example.util.NetworkMonitor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MarketplaceUiState(
    val isLoading: Boolean = true,
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
    val searchQuery: String = "",
    val selectedCategoryId: String? = null,
    val selectedDivision: String? = null,
    val selectedDistrict: String? = null,
    val selectedUpazila: String? = null,
    val wholesaleOnly: Boolean? = null,
    val priceSort: PriceSort = PriceSort.NEWEST,
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
            val base = products.filter { p ->
                val matchesQuery = searchQuery.isBlank() ||
                    p.nameBn.contains(searchQuery, ignoreCase = true) ||
                    p.nameEn.contains(searchQuery, ignoreCase = true) ||
                    p.farmerName.contains(searchQuery, ignoreCase = true) ||
                    p.division.contains(searchQuery, ignoreCase = true) ||
                    p.district.contains(searchQuery, ignoreCase = true) ||
                    p.upazila.contains(searchQuery, ignoreCase = true) ||
                    p.categoryNameBn.contains(searchQuery, ignoreCase = true)
                val matchesCat = selectedCategoryId == null || p.categoryId == selectedCategoryId
                val matchesDiv = selectedDivision == null || p.division == selectedDivision
                val matchesDist = selectedDistrict == null || p.district == selectedDistrict
                val matchesUpazila = selectedUpazila == null || p.upazila == selectedUpazila
                val matchesWholesale = wholesaleOnly == null || p.isWholesale == wholesaleOnly
                matchesQuery && matchesCat && matchesDiv && matchesDist && matchesUpazila && matchesWholesale && p.status == "ACTIVE"
            }
            return when (priceSort) {
                PriceSort.NEWEST -> base.sortedByDescending { it.createdAt }
                PriceSort.PRICE_LOW_HIGH -> base.sortedBy { it.pricePerUnit }
                PriceSort.PRICE_HIGH_LOW -> base.sortedByDescending { it.pricePerUnit }
            }
        }

    val filteredFarmers: List<UserEntity>
        get() = farmers.filter { f ->
            val matchesQuery = searchQuery.isBlank() ||
                f.fullName.contains(searchQuery, ignoreCase = true) ||
                f.division.contains(searchQuery, ignoreCase = true) ||
                f.district.contains(searchQuery, ignoreCase = true) ||
                f.upazila.contains(searchQuery, ignoreCase = true) ||
                f.farmBio.contains(searchQuery, ignoreCase = true)
            val matchesDiv = selectedDivision == null || f.division == selectedDivision
            val matchesDist = selectedDistrict == null || f.district == selectedDistrict
            val matchesUpazila = selectedUpazila == null || f.upazila == selectedUpazila
            matchesQuery && matchesDiv && matchesDist && matchesUpazila
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
            val activeProds = products.count { it.status == "ACTIVE" }
            val dayAgo = System.currentTimeMillis() - 86_400_000L
            val todaysOrders = orders.count { it.createdAt >= dayAgo }
            val completedOrders = orders.count { it.status == OrderStatus.COMPLETED.name }
            val totalSales = orders.filter { it.status != OrderStatus.CANCELLED.name && it.status != OrderStatus.REJECTED.name }
                .sumOf { it.productTotal }
            val districtSales = orders
                .filter { it.status != OrderStatus.CANCELLED.name && it.status != OrderStatus.REJECTED.name }
                .groupBy { it.district.ifBlank { "অন্যান্য" } }
                .mapValues { entry -> entry.value.sumOf { it.productTotal } }
            return AdminDashboardStats(
                totalFarmers = totalFarmers,
                verifiedFarmers = verifiedFarmers,
                totalBuyers = totalBuyers,
                activeProducts = activeProds,
                todaysOrders = todaysOrders,
                completedOrders = completedOrders,
                totalSalesAmount = totalSales,
                districtSales = districtSales
            )
        }
}

class KrishiBondhuViewModel(application: Application) : AndroidViewModel(application) {
    private val db = KrishiBondhuDatabase.getInstance(application)
    private val prefs = UserPreferences(application)
    private val repo = MarketplaceRepository(db, prefs)

    private val filterState = MutableStateFlow(FilterParams())
    private val toastFlow = MutableStateFlow<String?>(null)
    private val notifiedIds = mutableSetOf<String>()

    private data class FilterParams(
        val query: String = "",
        val categoryId: String? = null,
        val division: String? = null,
        val district: String? = null,
        val upazila: String? = null,
        val wholesaleOnly: Boolean? = null,
        val priceSort: PriceSort = PriceSort.NEWEST
    )

    private val launchTimestamp = System.currentTimeMillis()

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
    }

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

    val uiState: StateFlow<MarketplaceUiState> = combine(
        coreDataFlow,
        commerceDataFlow,
        repo.auditLogs,
        filterState,
        combine(toastFlow, NetworkMonitor.observeIsOnline(application)) { t, online -> t to online }
    ) { core, commerce, audits, filter, (toast, online) ->
        val activeUser = if (core.session.activeUserId.isNotBlank()) {
            core.users.firstOrNull { it.id == core.session.activeUserId }
        } else {
            null
        }
        MarketplaceUiState(
            isLoading = core.categories.isEmpty(),
            isOnline = online,
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
            auditLogs = audits,
            searchQuery = filter.query,
            selectedCategoryId = filter.categoryId,
            selectedDivision = filter.division,
            selectedDistrict = filter.district,
            selectedUpazila = filter.upazila,
            wholesaleOnly = filter.wholesaleOnly,
            priceSort = filter.priceSort,
            toastMessage = toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MarketplaceUiState(isLoading = true)
    )

    fun observeOrderItems(orderId: String): Flow<List<OrderItemEntity>> =
        repo.observeOrderItems(orderId)

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
        filterState.update { it.copy(division = division, district = null, upazila = null) }
    }

    fun setDistrictFilter(district: String?) {
        filterState.update { it.copy(district = district, upazila = null) }
    }

    fun setUpazilaFilter(upazila: String?) {
        filterState.update { it.copy(upazila = upazila) }
    }

    fun setLocationFilters(division: String?, district: String?, upazila: String?) {
        filterState.update { it.copy(division = division, district = district, upazila = upazila) }
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

    fun register(
        fullName: String,
        phone: String,
        password: String,
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
                farmBio = farmBio
            )
                .onSuccess {
                    showToast("নিবন্ধন সফল হয়েছে! স্বাগতম, ${it.fullName}")
                    onSuccess()
                }
                .onFailure { showToast(it.message ?: "নিবন্ধন ব্যর্থ হয়েছে") }
        }
    }

    fun addToCart(product: ProductEntity, qty: Double = 1.0) {
        viewModelScope.launch {
            repo.addToCart(product, qty)
                .onSuccess { showToast("${product.nameBn} কার্টে যোগ করা হয়েছে") }
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

    fun placeOrders(
        deliveryOption: DeliveryOption,
        deliveryAddress: String,
        paymentMethod: PaymentMethod,
        transactionRef: String,
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
            repo.placeOrdersFromCart(buyer, deliveryOption, deliveryAddress, paymentMethod, transactionRef)
                .onSuccess { orders ->
                    showToast("${orders.size}টি অর্ডার সম্পন্ন হয়েছে ও কৃষককে নোটিফিকেশন পাঠানো হয়েছে!")
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
                    showToast("আপনার ফসল সফলভাবে যুক্ত হয়েছে!")
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

    fun updateOrderStatus(orderId: String, status: OrderStatus) {
        val actor = uiState.value.activeUser ?: return
        viewModelScope.launch {
            repo.updateOrderStatus(orderId, status, actor)
                .onSuccess { showToast("অর্ডার অবস্থা: ${status.display(uiState.value.language)}") }
                .onFailure { showToast(it.message ?: "অবস্থা পরিবর্তন করা যায়নি") }
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
        val admin = uiState.value.activeUser ?: return
        if (admin.role != UserRole.ADMIN.name) return
        viewModelScope.launch {
            repo.adminVerifyFarmer(admin, farmerId, approved)
            showToast(if (approved) "কৃষক যাচাই সম্পন্ন হয়েছে" else "কৃষকের আবেদন বাতিল হয়েছে")
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
}
