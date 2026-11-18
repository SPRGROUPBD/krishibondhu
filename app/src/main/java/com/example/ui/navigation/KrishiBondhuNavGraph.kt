package com.example.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ads.AdMonetizationManager
import com.example.data.model.AppLanguage
import com.example.data.model.OrderStatus
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KrishiTopBar
import com.example.ui.screens.*
import com.example.ui.viewmodel.KrishiBondhuViewModel

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val BROWSE = "browse?farmers={farmers}"
    const val FARMERS = "farmers"
    const val SELL = "sell"
    const val ORDERS = "orders"
    const val PROFILE = "profile"
    const val ACTIVATION = "activation"
    const val PRODUCT_DETAIL = "product/{productId}"
    const val FARMER_PROFILE = "farmer/{farmerId}"
    const val CART = "cart"
    const val ORDER_DETAIL = "order/{orderId}"
    const val ADMIN = "admin"
    const val LOGIN = "login"
    const val REGISTER = "register?role={role}"
    const val NOTIFICATIONS = "notifications"
    const val INFO = "info/{topic}"

    fun browse(farmersOnly: Boolean = false) = "browse?farmers=$farmersOnly"
    fun productDetail(id: String) = "product/$id"
    fun farmerProfile(id: String) = "farmer/$id"
    fun orderDetail(id: String) = "order/$id"
    fun register(role: UserRole = UserRole.BUYER) = "register?role=${role.name}"
    fun info(topic: String) = "info/$topic"
}

private data class BottomNavTab(
    val route: String,
    val labelBn: String,
    val labelEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val badgeCount: Int = 0
)

@Composable
fun KrishiBondhuApp(viewModel: KrishiBondhuViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalActivity.current

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.consumeToast()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.SPLASH

    LaunchedEffect(currentRoute) {
        AdMonetizationManager.setCurrentRoute(currentRoute)
    }

    val topLevelRoutes = setOf(Routes.HOME, Routes.BROWSE, Routes.FARMERS, Routes.SELL, Routes.ORDERS, Routes.PROFILE)
    val isTopLevel = topLevelRoutes.any { currentRoute.startsWith(it.substringBefore("?")) }

    val farmerPendingOrderCount = remember(uiState.orders, uiState.activeUser) {
        val activeUser = uiState.activeUser
        if (activeUser?.role == UserRole.FARMER.name) {
            uiState.orders.count { it.farmerId == activeUser.id && it.status == OrderStatus.PENDING.name }
        } else {
            0
        }
    }

    val tabs = remember(uiState.activeRole, farmerPendingOrderCount) {
        when (uiState.activeRole) {
            UserRole.FARMER -> listOf(
                BottomNavTab(Routes.HOME, "খামার", "Farm", Icons.Filled.Agriculture, Icons.Outlined.Agriculture, "nav_home"),
                BottomNavTab(Routes.SELL, "আমার ফসল", "My Crops", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "nav_sell"),
                BottomNavTab(Routes.ORDERS, "অর্ডার", "Orders", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag, "nav_orders", badgeCount = farmerPendingOrderCount),
                BottomNavTab(Routes.browse(false), "বাজার", "Market", Icons.Filled.Storefront, Icons.Outlined.Storefront, "nav_browse"),
                BottomNavTab(Routes.PROFILE, "প্রোফাইল", "Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
            )
            UserRole.BUYER -> listOf(
                BottomNavTab(Routes.HOME, "হোম", "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
                BottomNavTab(Routes.browse(false), "ফসল খুঁজুন", "Crops", Icons.Filled.Search, Icons.Outlined.Search, "nav_browse"),
                BottomNavTab(Routes.FARMERS, "কৃষক", "Farmers", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_farmers"),
                BottomNavTab(Routes.ORDERS, "অর্ডার", "Orders", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag, "nav_orders"),
                BottomNavTab(Routes.PROFILE, "প্রোফাইল", "Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
            )
            UserRole.ADMIN -> listOf(
                BottomNavTab(Routes.HOME, "হোম", "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
                BottomNavTab(Routes.browse(false), "ফসল খুঁজুন", "Crops", Icons.Filled.Search, Icons.Outlined.Search, "nav_browse"),
                BottomNavTab(Routes.FARMERS, "কৃষক", "Farmers", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_farmers"),
                BottomNavTab(Routes.ORDERS, "অর্ডার", "Orders", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag, "nav_orders"),
                BottomNavTab(Routes.PROFILE, "প্রোফাইল", "Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
            )
            null -> listOf(
                BottomNavTab(Routes.HOME, "হোম", "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
                BottomNavTab(Routes.browse(false), "ফসল খুঁজুন", "Browse", Icons.Filled.Search, Icons.Outlined.Search, "nav_browse"),
                BottomNavTab(Routes.SELL, "বিক্রি করুন", "Sell", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_sell"),
                BottomNavTab(Routes.ORDERS, "অর্ডার", "Orders", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag, "nav_orders"),
                BottomNavTab(Routes.PROFILE, "প্রোফাইল", "Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
            )
        }
    }

    val requireActiveAccountAction: () -> Unit = {
        val currentUser = uiState.activeUser
        if (currentUser == null) {
            viewModel.showToast("এই সুবিধা পেতে অনুগ্রহ করে লগইন বা নিবন্ধন করুন")
            navController.navigate(Routes.LOGIN)
        } else {
            viewModel.showToast("সুবিধাটি ব্যবহার করতে ৩০০ টাকা দিয়ে অ্যাকাউন্ট সক্রিয়/রিনিউ করুন")
            navController.navigate(Routes.ACTIVATION)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = if (currentRoute == Routes.SPLASH) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (isTopLevel) {
                    KrishiTopBar(
                        language = uiState.language,
                        isOnline = uiState.isOnline,
                        cartCount = uiState.cartBadgeCount,
                        unreadNotifs = uiState.unreadNotificationCount,
                        activeRole = uiState.activeRole,
                        verificationStatus = uiState.activeUser?.verificationStatus ?: "",
                        accountStatus = uiState.activeUser?.accountStatus ?: "",
                        profileImageUri = uiState.activeUser?.profileImageUri.orEmpty(),
                        onCartClick = { navController.navigate(Routes.CART) },
                        onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                        onLanguageToggle = { viewModel.toggleLanguage() }
                    )
                }
            },
            bottomBar = {
                if (isTopLevel && !useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute.substringBefore("?") == tab.route.substringBefore("?")
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab.badgeCount > 0) {
                                                Badge { Text(tab.badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.labelBn
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (uiState.language == AppLanguage.BENGALI) tab.labelBn else tab.labelEn,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useNavigationRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        Spacer(modifier = Modifier.height(8.dp))
                        tabs.forEach { tab ->
                            val selected = currentRoute.substringBefore("?") == tab.route.substringBefore("?")
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab.badgeCount > 0) {
                                                Badge { Text(tab.badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.labelBn
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (uiState.language == AppLanguage.BENGALI) tab.labelBn else tab.labelEn,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.SPLASH,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(
                        route = Routes.SPLASH,
                        exitTransition = { fadeOut(animationSpec = tween(350)) }
                    ) {
                        SplashScreen(
                            isAppLoading = uiState.isLoading,
                            isInitialSyncing = uiState.isSyncing,
                            onSplashComplete = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(
                        route = Routes.HOME,
                        enterTransition = { fadeIn(animationSpec = tween(350)) }
                    ) {
                        HomeScreen(
                            uiState = uiState,
                            onNavigateToBrowse = { navController.navigate(Routes.browse(false)) },
                            onCategoryClick = { catId ->
                                viewModel.setCategoryFilter(catId)
                                navController.navigate(Routes.browse(false))
                            },
                            onProductClick = { pid -> navController.navigate(Routes.productDetail(pid)) },
                            onFarmerClick = { fid -> navController.navigate(Routes.farmerProfile(fid)) },
                            onQuickAddToCart = { pid ->
                                uiState.products.firstOrNull { it.id == pid }?.let {
                                    viewModel.addToCart(it, 1.0)
                                }
                            },
                            onSearchTap = { navController.navigate(Routes.browse(false)) },
                            onFindFarmersTap = {
                                if (uiState.activeRole == UserRole.BUYER || uiState.activeRole == UserRole.ADMIN) {
                                    navController.navigate(Routes.FARMERS)
                                } else {
                                    navController.navigate(Routes.browse(true))
                                }
                            },
                            onStartSellingTap = { navController.navigate(Routes.SELL) },
                            onNavigateToOrders = { navController.navigate(Routes.ORDERS) },
                            onOrderClick = { oid -> navController.navigate(Routes.orderDetail(oid)) },
                            onOpenCart = { navController.navigate(Routes.CART) },
                            onOpenAdminDashboard = { navController.navigate(Routes.ADMIN) },
                            onRefreshSync = viewModel::syncNow
                        )
                    }

                    composable(
                        route = Routes.BROWSE,
                        arguments = listOf(navArgument("farmers") {
                            type = NavType.BoolType
                            defaultValue = false
                        })
                    ) { backStack ->
                        val farmersOnly = backStack.arguments?.getBoolean("farmers") ?: false
                        BrowseScreen(
                            uiState = uiState,
                            initialTabFarmers = farmersOnly,
                            onSearchChange = viewModel::setSearchQuery,
                            onCategorySelect = viewModel::setCategoryFilter,
                            onDivisionSelect = viewModel::setDivisionFilter,
                            onDistrictSelect = viewModel::setDistrictFilter,
                            onUpazilaSelect = viewModel::setUpazilaFilter,
                            onUnionSelect = viewModel::setUnionFilter,
                            onLocationAutoSelect = { div, dist, upz -> viewModel.setLocationFilters(div, dist, upz) },
                            onUseMyLocation = viewModel::applyMyLocationFilter,
                            onWholesaleSelect = viewModel::setWholesaleFilter,
                            onSortSelect = viewModel::setPriceSort,
                            onResetFilters = viewModel::resetFilters,
                            onRefreshSync = viewModel::syncNow,
                            onProductClick = { pid -> navController.navigate(Routes.productDetail(pid)) },
                            onFarmerClick = { fid -> navController.navigate(Routes.farmerProfile(fid)) },
                            onQuickAddToCart = { pid ->
                                uiState.products.firstOrNull { it.id == pid }?.let {
                                    viewModel.addToCart(it, 1.0)
                                }
                            }
                        )
                    }

                    composable(Routes.FARMERS) {
                        BrowseScreen(
                            uiState = uiState,
                            initialTabFarmers = true,
                            onSearchChange = viewModel::setSearchQuery,
                            onCategorySelect = viewModel::setCategoryFilter,
                            onDivisionSelect = viewModel::setDivisionFilter,
                            onDistrictSelect = viewModel::setDistrictFilter,
                            onUpazilaSelect = viewModel::setUpazilaFilter,
                            onUnionSelect = viewModel::setUnionFilter,
                            onLocationAutoSelect = { div, dist, upz -> viewModel.setLocationFilters(div, dist, upz) },
                            onUseMyLocation = viewModel::applyMyLocationFilter,
                            onWholesaleSelect = viewModel::setWholesaleFilter,
                            onSortSelect = viewModel::setPriceSort,
                            onResetFilters = viewModel::resetFilters,
                            onRefreshSync = viewModel::syncNow,
                            onProductClick = { pid -> navController.navigate(Routes.productDetail(pid)) },
                            onFarmerClick = { fid -> navController.navigate(Routes.farmerProfile(fid)) },
                            onQuickAddToCart = { pid ->
                                uiState.products.firstOrNull { it.id == pid }?.let {
                                    viewModel.addToCart(it, 1.0)
                                }
                            }
                        )
                    }

                    composable(Routes.SELL) {
                        val activeFarmerId = uiState.activeUser?.id.orEmpty()
                        val farmerPaySettings by remember(activeFarmerId) {
                            viewModel.observeFarmerPaymentSettings(activeFarmerId)
                        }.collectAsStateWithLifecycle(initialValue = null)

                        SellScreen(
                            uiState = uiState,
                            farmerPaymentSettings = farmerPaySettings,
                            onLoginFarmer = { navController.navigate(Routes.LOGIN) },
                            onRegisterFarmer = { navController.navigate(Routes.register(UserRole.FARMER)) },
                            onOpenActivation = { navController.navigate(Routes.ACTIVATION) },
                            onSavePaymentSettings = { bKash, nagad, rocket, bank ->
                                viewModel.saveFarmerPaymentSettings(bKash, nagad, rocket, bank)
                            },
                            onAddProduct = { cat, name, desc, uri, qty, unit, price, harvest, avail, addr, pickup, deliv, fee, wholesale ->
                                viewModel.addFarmerProduct(
                                    cat, name, desc, uri, qty, unit, price,
                                    harvest, avail, addr, pickup, deliv, fee, wholesale
                                ) {}
                            },
                            onUpdatePriceAndStock = viewModel::updateFarmerPriceAndStock,
                            onDeleteProduct = viewModel::deleteProduct
                        )
                    }

                    composable(Routes.ORDERS) {
                        OrdersScreen(
                            uiState = uiState,
                            onOrderClick = { oid -> navController.navigate(Routes.orderDetail(oid)) },
                            onGoToCart = { navController.navigate(Routes.CART) },
                            onBrowseClick = { navController.navigate(Routes.browse(false)) }
                        )
                    }

                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            uiState = uiState,
                            onOpenLogin = { navController.navigate(Routes.LOGIN) },
                            onOpenRegister = { navController.navigate(Routes.register(UserRole.BUYER)) },
                            onLogout = viewModel::logout,
                            onOpenActivation = { navController.navigate(Routes.ACTIVATION) },
                            onUpdateProfile = { name, div, dist, upz, union, village, bio, nid4, photoUri ->
                                viewModel.updateProfile(name, div, dist, upz, union, village, bio, nid4, photoUri) {}
                            },
                            onOpenAdminDashboard = { navController.navigate(Routes.ADMIN) },
                            onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                            onOpenInfo = { topic -> navController.navigate(Routes.info(topic)) },
                            onToggleLanguage = viewModel::toggleLanguage,
                            onToggleDarkMode = viewModel::setDarkMode,
                            onPermissionMessage = viewModel::showToast
                        )
                    }

                    composable(Routes.ACTIVATION) {
                        ActivationScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onSubmitPayment = { method, sender, txId, amount, receiptUri, isRenewal ->
                                viewModel.submitActivationPayment(method, sender, txId, amount, receiptUri, isRenewal)
                            },
                            onRequireLogin = { navController.navigate(Routes.LOGIN) },
                            onCopyToast = viewModel::showToast
                        )
                    }

                    composable(Routes.PRODUCT_DETAIL) { backStack ->
                        val pid = backStack.arguments?.getString("productId") ?: ""
                        ProductDetailScreen(
                            productId = pid,
                            uiState = uiState,
                            onBack = {
                                navController.popBackStack()
                                AdMonetizationManager.maybeShowInterstitial(
                                    activity = activity,
                                    overrideUnitId = uiState.adConfig.resolvedInterstitialUnitId(),
                                    enabled = uiState.adConfig.adsEnabled &&
                                        uiState.adConfig.androidAdsEnabled &&
                                        uiState.adConfig.interstitialEnabled
                                )
                            },
                            onFarmerClick = { fid -> navController.navigate(Routes.farmerProfile(fid)) },
                            onAddToCart = { qty ->
                                uiState.products.firstOrNull { it.id == pid }?.let {
                                    viewModel.addToCart(it, qty)
                                }
                            },
                            onGoToCart = { navController.navigate(Routes.CART) },
                            onOpenActivation = { navController.navigate(Routes.ACTIVATION) },
                            onRequireActiveAccount = requireActiveAccountAction
                        )
                    }

                    composable(Routes.FARMER_PROFILE) { backStack ->
                        val fid = backStack.arguments?.getString("farmerId") ?: ""
                        FarmerProfileScreen(
                            farmerId = fid,
                            uiState = uiState,
                            onBack = {
                                navController.popBackStack()
                                AdMonetizationManager.maybeShowInterstitial(
                                    activity = activity,
                                    overrideUnitId = uiState.adConfig.resolvedInterstitialUnitId(),
                                    enabled = uiState.adConfig.adsEnabled &&
                                        uiState.adConfig.androidAdsEnabled &&
                                        uiState.adConfig.interstitialEnabled
                                )
                            },
                            onProductClick = { pid -> navController.navigate(Routes.productDetail(pid)) },
                            onQuickAddToCart = { pid ->
                                uiState.products.firstOrNull { it.id == pid }?.let {
                                    viewModel.addToCart(it, 1.0)
                                }
                            },
                            onRequireActiveAccount = requireActiveAccountAction
                        )
                    }

                    composable(Routes.CART) {
                        CartCheckoutScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onBrowseClick = { navController.navigate(Routes.browse(false)) },
                            onUpdateQty = viewModel::updateCartQty,
                            onRemoveItem = viewModel::removeCartItem,
                            onPlaceOrder = { deliveryOpt, addr, payMethod, txnRef, advanceByFarmer, senderNum, refNote ->
                                viewModel.placeOrders(
                                    deliveryOption = deliveryOpt,
                                    deliveryAddress = addr,
                                    paymentMethod = payMethod,
                                    transactionRef = txnRef,
                                    advanceByFarmer = advanceByFarmer,
                                    senderNumber = senderNum,
                                    paymentReferenceNote = refNote,
                                    onRequireAuth = { navController.navigate(Routes.LOGIN) }
                                ) {
                                    navController.navigate(Routes.ORDERS) {
                                        popUpTo(Routes.HOME)
                                    }
                                }
                            },
                            onOpenActivation = { navController.navigate(Routes.ACTIVATION) }
                        )
                    }

                    composable(Routes.ORDER_DETAIL) { backStack ->
                        val oid = backStack.arguments?.getString("orderId") ?: ""
                        val orderFarmerId = uiState.orders.firstOrNull { it.id == oid }?.farmerId.orEmpty()
                        OrderDetailScreen(
                            orderId = oid,
                            uiState = uiState,
                            orderItemsFlow = viewModel.observeOrderItems(oid),
                            orderPaymentsFlow = viewModel.observeOrderPayments(oid),
                            farmerPaymentSettingsFlow = viewModel.observeFarmerPaymentSettings(orderFarmerId),
                            onBack = { navController.popBackStack() },
                            onUpdateStatus = { newStatus -> viewModel.updateOrderStatus(oid, newStatus) },
                            onSubmitOrderAdvancePayment = { amt, method, sender, txId, note ->
                                viewModel.submitOrderAdvancePayment(oid, amt, method, sender, txId, note)
                            },
                            onFarmerVerifyOrderPayment = { payId, approved, reason ->
                                viewModel.farmerVerifyOrderPayment(payId, approved, reason)
                            },
                            onApproveZeroAdvanceOrder = { viewModel.approveZeroAdvanceOrder(oid) },
                            onSubmitReview = { prodId, rating, comment ->
                                uiState.orders.firstOrNull { it.id == oid }?.let { ord ->
                                    viewModel.submitReview(ord, prodId, rating, comment)
                                }
                            },
                            onSubmitComplaint = { subj, desc ->
                                viewModel.submitComplaint(oid, subj, desc)
                            }
                        )
                    }

                    composable(Routes.ADMIN) {
                        if (uiState.isSuperAdmin) {
                            AdminDashboardScreen(
                                uiState = uiState,
                                onBack = { navController.popBackStack() },
                                onVerifyUser = viewModel::adminVerifyUser,
                                onApproveActivationPayment = viewModel::adminApproveActivationPayment,
                                onRejectActivationPayment = viewModel::adminRejectActivationPayment,
                                onSetAccountStatus = viewModel::adminSetAccountStatus,
                                onVerifyOrderPayment = viewModel::adminVerifyOrderPayment,
                                onSavePaymentSettings = viewModel::adminSavePaymentSettings,
                                onSendNotification = viewModel::adminSendNotification,
                                onAddCategory = viewModel::adminAddCategory,
                                onAddLocation = viewModel::adminAddLocation,
                                onResolveComplaint = viewModel::adminResolveComplaint,
                                onVerifyProduct = viewModel::adminVerifyProduct,
                                onUpdateProduct = viewModel::adminUpdateProduct,
                                onDeleteProduct = viewModel::deleteProduct,
                                onSaveAdConfig = viewModel::adminSaveAdConfig,
                                onBuildCsvReport = viewModel::buildCsvReport
                            )
                        } else {
                            EmptyStateView(
                                emoji = "🔒",
                                title = "অনুমতি নেই",
                                subtitle = "সুপার অ্যাডমিন ড্যাশবোর্ড শুধুমাত্র অনুমোদিত অ্যাডমিন অ্যাকাউন্টের জন্য সংরক্ষিত।",
                                actionText = "ফিরে যান",
                                onAction = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(Routes.LOGIN) {
                        LoginScreen(
                            onBack = { navController.popBackStack() },
                            onLogin = { identifier, pass ->
                                val cameFromCart = navController.previousBackStackEntry?.destination?.route == Routes.CART
                                viewModel.login(identifier, pass) { loggedInUser ->
                                    when {
                                        loggedInUser.role == UserRole.ADMIN.name -> {
                                            navController.navigate(Routes.ADMIN) {
                                                popUpTo(Routes.HOME)
                                            }
                                        }
                                        cameFromCart -> navController.popBackStack()
                                        else -> {
                                            navController.navigate(Routes.HOME) {
                                                popUpTo(Routes.HOME) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            },
                            onResetPassword = { phone, newPass, onDone ->
                                viewModel.resetPassword(phone, newPass, onDone)
                            },
                            onGoToRegister = {
                                navController.navigate(Routes.register(UserRole.BUYER))
                            }
                        )
                    }

                    composable(
                        route = Routes.REGISTER,
                        arguments = listOf(navArgument("role") {
                            type = NavType.StringType
                            defaultValue = UserRole.BUYER.name
                        })
                    ) { backStack ->
                        val roleStr = backStack.arguments?.getString("role") ?: UserRole.BUYER.name
                        val initRole = runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.BUYER)
                        RegisterScreen(
                            uiState = uiState,
                            initialRole = initRole,
                            onBack = { navController.popBackStack() },
                            onRegister = { name, phone, pass, confirmPass, role, division, dist, upazila, union, village, nid, bio ->
                                viewModel.register(name, phone, pass, confirmPass, role, division, dist, upazila, union, village, nid, bio) {
                                    navController.navigate(Routes.ACTIVATION) {
                                        popUpTo(Routes.HOME)
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }

                    composable(Routes.NOTIFICATIONS) {
                        NotificationsScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onMarkAllRead = viewModel::markAllNotificationsRead
                        )
                    }

                    composable(Routes.INFO) { backStack ->
                        val topic = backStack.arguments?.getString("topic") ?: "about"
                        InfoScreen(
                            topic = topic,
                            adConfig = uiState.adConfig,
                            onBack = {
                                navController.popBackStack()
                                AdMonetizationManager.maybeShowInterstitial(
                                    activity = activity,
                                    overrideUnitId = uiState.adConfig.resolvedInterstitialUnitId(),
                                    enabled = uiState.adConfig.adsEnabled &&
                                        uiState.adConfig.androidAdsEnabled &&
                                        uiState.adConfig.interstitialEnabled
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
