package com.example.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.data.model.AppLanguage
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KrishiTopBar
import com.example.ui.screens.*
import com.example.ui.viewmodel.KrishiBondhuViewModel

object Routes {
    const val HOME = "home"
    const val BROWSE = "browse?farmers={farmers}"
    const val SELL = "sell"
    const val ORDERS = "orders"
    const val PROFILE = "profile"
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
    val testTag: String
)

@Composable
fun KrishiBondhuApp(viewModel: KrishiBondhuViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.consumeToast()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    val topLevelRoutes = setOf(Routes.HOME, Routes.BROWSE, Routes.SELL, Routes.ORDERS, Routes.PROFILE)
    val isTopLevel = topLevelRoutes.any { currentRoute.startsWith(it.substringBefore("?")) }

    val tabs = remember {
        listOf(
            BottomNavTab(Routes.HOME, "হোম", "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            BottomNavTab(Routes.browse(false), "ফসল খুঁজুন", "Browse", Icons.Filled.Search, Icons.Outlined.Search, "nav_browse"),
            BottomNavTab(Routes.SELL, "বিক্রি করুন", "Sell", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_sell"),
            BottomNavTab(Routes.ORDERS, "অর্ডার", "Orders", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag, "nav_orders"),
            BottomNavTab(Routes.PROFILE, "প্রোফাইল", "Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (isTopLevel) {
                    KrishiTopBar(
                        language = uiState.language,
                        isOnline = uiState.isOnline,
                        cartCount = uiState.cartBadgeCount,
                        unreadNotifs = uiState.unreadNotificationCount,
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
                            val selected = currentRoute.startsWith(tab.route.substringBefore("?"))
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
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.labelBn
                                    )
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
                            val selected = currentRoute.startsWith(tab.route.substringBefore("?"))
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
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.labelBn
                                    )
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
                    startDestination = Routes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.HOME) {
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
                            onFindFarmersTap = { navController.navigate(Routes.browse(true)) },
                            onStartSellingTap = { navController.navigate(Routes.SELL) }
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
                            onLocationAutoSelect = viewModel::setLocationFilters,
                            onWholesaleSelect = viewModel::setWholesaleFilter,
                            onSortSelect = viewModel::setPriceSort,
                            onResetFilters = viewModel::resetFilters,
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
                        SellScreen(
                            uiState = uiState,
                            onLoginFarmer = { navController.navigate(Routes.LOGIN) },
                            onRegisterFarmer = { navController.navigate(Routes.register(UserRole.FARMER)) },
                            onAddProduct = { cat, name, desc, uri, qty, unit, price, harvest, avail, addr, pickup, deliv, fee, wholesale ->
                                viewModel.addFarmerProduct(
                                    cat, name, desc, uri, qty, unit, price,
                                    harvest, avail, addr, pickup, deliv, fee, wholesale
                                ) {}
                            },
                            onUpdatePriceAndStock = viewModel::updateFarmerPriceAndStock
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
                            onOpenAdminDashboard = { navController.navigate(Routes.ADMIN) },
                            onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                            onOpenInfo = { topic -> navController.navigate(Routes.info(topic)) },
                            onToggleLanguage = viewModel::toggleLanguage,
                            onToggleDarkMode = viewModel::setDarkMode,
                            onPermissionMessage = viewModel::showToast
                        )
                    }

                    composable(Routes.PRODUCT_DETAIL) { backStack ->
                        val pid = backStack.arguments?.getString("productId") ?: ""
                        ProductDetailScreen(
                            productId = pid,
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onFarmerClick = { fid -> navController.navigate(Routes.farmerProfile(fid)) },
                            onAddToCart = { qty ->
                                uiState.products.firstOrNull { it.id == pid }?.let {
                                    viewModel.addToCart(it, qty)
                                }
                            },
                            onGoToCart = { navController.navigate(Routes.CART) }
                        )
                    }

                    composable(Routes.FARMER_PROFILE) { backStack ->
                        val fid = backStack.arguments?.getString("farmerId") ?: ""
                        FarmerProfileScreen(
                            farmerId = fid,
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onProductClick = { pid -> navController.navigate(Routes.productDetail(pid)) },
                            onQuickAddToCart = { pid ->
                                uiState.products.firstOrNull { it.id == pid }?.let {
                                    viewModel.addToCart(it, 1.0)
                                }
                            }
                        )
                    }

                    composable(Routes.CART) {
                        CartCheckoutScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onBrowseClick = { navController.navigate(Routes.browse(false)) },
                            onUpdateQty = viewModel::updateCartQty,
                            onRemoveItem = viewModel::removeCartItem,
                            onPlaceOrder = { deliveryOpt, addr, payMethod, txnRef ->
                                viewModel.placeOrders(
                                    deliveryOption = deliveryOpt,
                                    deliveryAddress = addr,
                                    paymentMethod = payMethod,
                                    transactionRef = txnRef,
                                    onRequireAuth = { navController.navigate(Routes.LOGIN) }
                                ) {
                                    navController.navigate(Routes.ORDERS) {
                                        popUpTo(Routes.HOME)
                                    }
                                }
                            }
                        )
                    }

                    composable(Routes.ORDER_DETAIL) { backStack ->
                        val oid = backStack.arguments?.getString("orderId") ?: ""
                        OrderDetailScreen(
                            orderId = oid,
                            uiState = uiState,
                            orderItemsFlow = viewModel.observeOrderItems(oid),
                            onBack = { navController.popBackStack() },
                            onUpdateStatus = { newStatus -> viewModel.updateOrderStatus(oid, newStatus) },
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
                                onVerifyFarmer = viewModel::adminVerifyFarmer,
                                onSendNotification = viewModel::adminSendNotification,
                                onAddCategory = viewModel::adminAddCategory,
                                onAddLocation = viewModel::adminAddLocation,
                                onResolveComplaint = viewModel::adminResolveComplaint,
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
                                viewModel.login(identifier, pass) { loggedInUser ->
                                    if (loggedInUser.role == UserRole.ADMIN.name) {
                                        navController.navigate(Routes.ADMIN) {
                                            popUpTo(Routes.HOME)
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
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
                            onRegister = { name, phone, pass, role, division, dist, upazila, union, village, nid, bio ->
                                viewModel.register(name, phone, pass, role, division, dist, upazila, union, village, nid, bio) {
                                    navController.popBackStack()
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
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
