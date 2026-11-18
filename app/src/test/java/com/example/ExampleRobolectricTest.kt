package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KrishiBondhuDatabase
import com.example.data.local.MasterCatalogSeeder
import com.example.data.local.PaymentSettingsEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserPreferences
import com.example.data.model.AccountStatus
import com.example.data.model.ActivationPaymentStatus
import com.example.data.model.BangladeshLocations
import com.example.data.model.DeliveryOption
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.ProductUnit
import com.example.data.model.UserRole
import com.example.data.remote.FirebaseRealtimeSyncManager
import com.example.data.repository.MarketplaceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: KrishiBondhuDatabase
    private lateinit var repo: MarketplaceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KrishiBondhuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = MarketplaceRepository(
            db = db,
            prefs = UserPreferences(context),
            rtdbSync = FirebaseRealtimeSyncManager(enableRemoteSync = false)
        )
        com.example.ads.AdMonetizationManager.resetForTest(context)
    }

    @After
    fun teardown() {
        com.example.ads.AdMonetizationManager.resetForTest(context)
        db.close()
    }

    private suspend fun activateAsAdmin(admin: UserEntity, user: UserEntity): UserEntity {
        return repo.adminSetAccountStatus(admin, user.id, AccountStatus.ACTIVE).getOrThrow()
    }

    @Test
    fun appNameAndPackageIdMatchRequirements() {
        val appName = context.getString(R.string.app_name)
        assertEquals("KrishiBondhu", appName)
        assertEquals("com.krishibondhu.bd", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun masterCatalogSeederInitializesAllBangladeshCatalogsWithoutDemoData() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val farmers = repo.farmers.first()
        val buyers = repo.buyers.first()
        val products = repo.products.first()
        val orders = repo.orders.first()
        val categories = repo.categories.first()
        val locations = repo.locations.first()

        assertTrue(farmers.isEmpty())
        assertTrue(buyers.isEmpty())
        assertTrue(products.isEmpty())
        assertTrue(orders.isEmpty())
        assertEquals(24, categories.size)
        assertEquals(8, BangladeshLocations.divisions.size)
        assertEquals(64, BangladeshLocations.allDistricts.size)
        // 63 other districts (1 primary upazila each) + 6 Kushtia upazilas = 69 seeded locations
        assertEquals(69, locations.size)
    }

    @Test
    fun superAdminOfflineLoginWorksWithExactCredentialsAndRejectsWrongPassword() = runBlocking {
        MasterCatalogSeeder.initialize(db)

        val wrongPass = repo.loginUser("admin", "wrongpass")
        assertTrue(wrongPass.isFailure)

        val adminResult = repo.loginUser("admin", "bondhu")
        assertTrue(adminResult.isSuccess)
        val adminUser = adminResult.getOrThrow()
        assertEquals(UserRole.ADMIN.name, adminUser.role)
        assertEquals(MarketplaceRepository.SUPERADMIN_USER_ID, adminUser.id)

        val buyerResult = repo.registerUser(
            fullName = "রহিম ক্রেতা",
            phone = "01711112233",
            rawPassword = "password123",
            role = UserRole.BUYER,
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "কুষ্টিয়া সদর",
            unionName = "বটতৈল",
            village = "কবুরহাট",
            nidLastFour = "",
            farmBio = ""
        )
        assertTrue(buyerResult.isSuccess)
        assertNotEquals(UserRole.ADMIN.name, buyerResult.getOrThrow().role)
    }

    @Test
    fun checkoutSplitsOrdersSendsFarmerNotificationAndRestoresStockOnCancelOrReject() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val categories = repo.categories.first()
        val riceCat = categories.first()

        val farmer1 = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "কৃষক ১",
                phone = "01711000001",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "কুমারখালী",
                unionName = "বাঁশগ্রাম",
                village = "শিমুলিয়া",
                nidLastFour = "1234",
                farmBio = "ধান চাষি"
            ).getOrThrow()
        )

        val farmer2 = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "কৃষক ২",
                phone = "01711000002",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "রাজশাহী",
                district = "বগুড়া",
                upazila = "বগুড়া সদর",
                unionName = "নুনগোলা",
                village = "চরপাড়া",
                nidLastFour = "5678",
                farmBio = "সবজি চাষি"
            ).getOrThrow()
        )

        val buyer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "ক্রেতা ১",
                phone = "01811000003",
                rawPassword = "pass1234",
                role = UserRole.BUYER,
                division = "ঢাকা",
                district = "ঢাকা",
                upazila = "সাভার",
                unionName = "",
                village = "",
                nidLastFour = "",
                farmBio = ""
            ).getOrThrow()
        )

        val prod1 = repo.createFarmerProduct(
            farmer = farmer1,
            category = riceCat,
            nameBn = "মিনিকেট চাল",
            descriptionBn = "তাজা মিনিকেট চাল",
            imageUri = "",
            quantity = 40.0,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = 1300.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = true,
            deliveryCharge = 50.0,
            isWholesale = false
        ).getOrThrow()

        val prod2 = repo.createFarmerProduct(
            farmer = farmer2,
            category = riceCat,
            nameBn = "দেশি পেঁয়াজ",
            descriptionBn = "বগুড়ার পেঁয়াজ",
            imageUri = "",
            quantity = 100.0,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = 1600.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "বগুড়া সদর",
            supportsFarmPickup = true,
            supportsDirectDelivery = true,
            deliveryCharge = 40.0,
            isWholesale = false
        ).getOrThrow()

        val approvedProd1 = repo.adminVerifyProduct(admin, prod1.id, approved = true).getOrThrow()
        val approvedProd2 = repo.adminVerifyProduct(admin, prod2.id, approved = true).getOrThrow()
        assertTrue(repo.addToCart(approvedProd1, 2.0, buyer).isSuccess)
        assertTrue(repo.addToCart(approvedProd2, 5.0, buyer).isSuccess)

        val ordersResult = repo.placeOrdersFromCart(
            buyer = buyer,
            deliveryOption = DeliveryOption.FARM_PICKUP,
            deliveryAddress = "",
            paymentMethod = PaymentMethod.CASH_ON_PICKUP,
            transactionRef = ""
        )

        assertTrue(ordersResult.isSuccess)
        val createdOrders = ordersResult.getOrThrow()
        assertEquals(2, createdOrders.size)
        assertEquals(setOf(farmer1.id, farmer2.id), createdOrders.map { it.farmerId }.toSet())

        // Verify stock deduction
        assertEquals(38.0, db.productDao().getProductById(prod1.id)!!.availableQuantity, 0.01)
        assertEquals(95.0, db.productDao().getProductById(prod2.id)!!.availableQuantity, 0.01)

        // Verify ORDER_NEW notifications sent strictly to each farmer
        val allNotifs = repo.notifications.first()
        val f1Notifs = allNotifs.filter { it.userId == farmer1.id && it.type == "ORDER_NEW" }
        val f2Notifs = allNotifs.filter { it.userId == farmer2.id && it.type == "ORDER_NEW" }
        assertEquals(1, f1Notifs.size)
        assertEquals(1, f2Notifs.size)

        // Cancel farmer1's order as buyer and confirm stock restoration
        val f1Order = createdOrders.first { it.farmerId == farmer1.id }
        val cancelResult = repo.updateOrderStatus(f1Order.id, OrderStatus.CANCELLED, buyer)
        assertTrue(cancelResult.isSuccess)
        assertEquals(40.0, db.productDao().getProductById(prod1.id)!!.availableQuantity, 0.01)

        // Reject farmer2's order as farmer2 and confirm stock restoration
        val f2Order = createdOrders.first { it.farmerId == farmer2.id }
        val rejectResult = repo.updateOrderStatus(f2Order.id, OrderStatus.REJECTED, farmer2)
        assertTrue(rejectResult.isSuccess)
        assertEquals(100.0, db.productDao().getProductById(prod2.id)!!.availableQuantity, 0.01)

        // Unauthorized third party cannot change order status
        val outsider = farmer1
        val outsiderUpdate = repo.updateOrderStatus(f2Order.id, OrderStatus.COMPLETED, outsider)
        assertTrue(outsiderUpdate.isFailure)
    }

    @Test
    fun submitReviewRecalculatesFarmerAverageRatingAndReviewCount() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()

        val farmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "সাব্বির কৃষক",
                phone = "01733000001",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "সিলেট",
                district = "সিলেট",
                upazila = "সিলেট সদর",
                unionName = "",
                village = "",
                nidLastFour = "1122",
                farmBio = ""
            ).getOrThrow()
        )

        val buyer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "নাসির ক্রেতা",
                phone = "01733000002",
                rawPassword = "pass1234",
                role = UserRole.BUYER,
                division = "ঢাকা",
                district = "ঢাকা",
                upazila = "সাভার",
                unionName = "",
                village = "",
                nidLastFour = "",
                farmBio = ""
            ).getOrThrow()
        )

        val prod = repo.createFarmerProduct(
            farmer = farmer,
            category = cat,
            nameBn = "হাওরের চাল",
            descriptionBn = "তাজা চাল",
            imageUri = "",
            quantity = 20.0,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = 1400.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "সিলেট সদর",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        ).getOrThrow()

        val approvedProd = repo.adminVerifyProduct(admin, prod.id, approved = true).getOrThrow()
        repo.addToCart(approvedProd, 2.0, buyer)
        val order = repo.placeOrdersFromCart(
            buyer = buyer,
            deliveryOption = DeliveryOption.FARM_PICKUP,
            deliveryAddress = "",
            paymentMethod = PaymentMethod.CASH_ON_PICKUP,
            transactionRef = ""
        ).getOrThrow().first()

        repo.submitReview(order, prod.id, buyer, 4, "খুব ভালো চাল")
        repo.submitReview(order, prod.id, buyer, 5, "দারুণ!")

        val updatedFarmer = db.userDao().getUserById(farmer.id)!!
        assertEquals(2, updatedFarmer.reviewCount)
        assertEquals(4.5, updatedFarmer.rating, 0.01)
    }

    @Test
    fun adminSendNotificationRequiresAdminAndCreatesNotificationAndAudit() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val buyer = repo.registerUser(
            fullName = "সাধারণ ক্রেতা",
            phone = "01944000001",
            rawPassword = "pass1234",
            role = UserRole.BUYER,
            division = "চট্টগ্রাম",
            district = "কুমিল্লা",
            upazila = "বরুড়া",
            unionName = "",
            village = "",
            nidLastFour = "",
            farmBio = ""
        ).getOrThrow()

        val blocked = repo.adminSendNotification(buyer, "ALL", "ALL", "Test", "Should fail")
        assertTrue(blocked.isFailure)

        val sent = repo.adminSendNotification(
            admin = admin,
            targetUserId = "ALL",
            targetRole = "FARMER",
            titleBn = "জরুরি নোটিশ",
            messageBn = "সকল জেলায় আজ ন্যায্য মূল্যে ফসল বিক্রি চলছে।"
        )
        assertTrue(sent.isSuccess)

        val notifications = repo.notifications.first()
        assertTrue(notifications.any { it.titleBn == "জরুরি নোটিশ" && it.targetRole == "FARMER" })

        val logs = repo.auditLogs.first()
        assertTrue(logs.any { it.actionBn == "অ্যাডমিন নোটিফিকেশন প্রেরণ" })
    }

    @Test
    fun onlyOwningFarmerCanUpdateProductPrice() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()

        val ownerFarmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "মালিক কৃষক",
                phone = "01722000001",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "খোকসা",
                unionName = "",
                village = "",
                nidLastFour = "4321",
                farmBio = ""
            ).getOrThrow()
        )

        val otherFarmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "অন্য কৃষক",
                phone = "01722000002",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "মিরপুর",
                unionName = "",
                village = "",
                nidLastFour = "8765",
                farmBio = ""
            ).getOrThrow()
        )

        val prod = repo.createFarmerProduct(
            farmer = ownerFarmer,
            category = cat,
            nameBn = "দেশি আলু",
            descriptionBn = "টাটকা আলু",
            imageUri = "",
            quantity = 50.0,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = 900.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "খোকসা",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        ).getOrThrow()

        val unauthorizedResult = repo.updateFarmerPriceAndStock(
            productId = prod.id,
            farmerId = otherFarmer.id,
            newPrice = 999.0,
            newStock = 10.0
        )
        assertTrue(unauthorizedResult.isFailure)

        val ownerResult = repo.updateFarmerPriceAndStock(
            productId = prod.id,
            farmerId = ownerFarmer.id,
            newPrice = 1000.0,
            newStock = 45.0
        )
        assertTrue(ownerResult.isSuccess)
    }

    @Test
    fun locationHelperSupportsAll64BangladeshDistrictsAndMediaCaptureHelperDecodesDataUrls() {
        val centroids = com.example.util.LocationHelper.districtCentroids
        assertEquals(64, centroids.size)

        val kushtia = com.example.util.LocationHelper.locationForDistrictCentroid("কুষ্টিয়া")
        assertEquals("খুলনা", kushtia.divisionBn)
        assertEquals("কুষ্টিয়া", kushtia.districtBn)
        assertTrue(kushtia.upazilaBn.isNotBlank())
        assertTrue(kushtia.latitude in 20.0..27.0)
        assertTrue(kushtia.longitude in 88.0..93.0)

        val dhaka = com.example.util.LocationHelper.locationForDistrictCentroid("ঢাকা")
        assertEquals("ঢাকা", dhaka.divisionBn)
        assertEquals("ঢাকা", dhaka.districtBn)

        assertNull(com.example.util.MediaCaptureHelper.imageModelFromUri(""))
        val sampleDataUrl = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD="
        val decoded = com.example.util.MediaCaptureHelper.imageModelFromUri(sampleDataUrl)
        assertTrue(decoded is ByteArray)
        assertEquals("https://example.com/crop.jpg", com.example.util.MediaCaptureHelper.imageModelFromUri("https://example.com/crop.jpg"))
    }

    @Test
    fun unityLevelPlayConfiguredWithoutAdMobAndZeroFakeRevenue() {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_META_DATA
        )
        assertNull(appInfo.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID"))

        val defaultConfig = com.example.data.model.AdConfig()
        assertEquals("UNITY_LEVELPLAY", defaultConfig.mediationPlatform)

        com.example.ads.AdMonetizationManager.resetForTest()
        com.example.ads.AdMonetizationManager.syncConfig(context, defaultConfig)
        val telemetry = com.example.ads.AdMonetizationManager.telemetry.value
        assertEquals(0.0, telemetry.dailyRevenueUsd, 0.00001)
        assertEquals(0.0, telemetry.monthlyRevenueUsd, 0.00001)
        assertEquals(0, telemetry.impressionCount)
        assertEquals(10, telemetry.adapterStatuses.size)
        val unityStatus = telemetry.adapterStatuses.first { it.key == "unity_ads" }
        assertFalse(unityStatus.requiresSeparateApproval)
        assertEquals(9, telemetry.adapterStatuses.count { it.requiresSeparateApproval })
    }

    @Test
    fun adminSaveAdConfigEnforcesAdminPermissionAndWritesAuditLog() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val buyer = repo.registerUser(
            fullName = "অ্যাড টেস্ট ক্রেতা",
            phone = "01755000001",
            rawPassword = "pass1234",
            role = UserRole.BUYER,
            division = "ঢাকা",
            district = "ঢাকা",
            upazila = "সাভার",
            unionName = "",
            village = "",
            nidLastFour = "",
            farmBio = ""
        ).getOrThrow()

        val updatedConfig = com.example.data.model.AdConfig(
            adsEnabled = true,
            androidAdsEnabled = true,
            mediationPlatform = "UNITY_LEVELPLAY",
            mediationAppKey = "lp_app_key_live_01",
            adTestMode = true,
            bannerEnabled = true,
            androidBannerId = "lp_banner_unit_01",
            nativeEnabled = true,
            androidNativePlacement = "lp_native_unit_01",
            interstitialEnabled = true,
            androidInterstitialId = "lp_interstitial_unit_01",
            rewardedEnabled = true,
            androidRewardedId = "lp_rewarded_unit_01",
            appOpenEnabled = true,
            androidAppOpenId = "lp_appopen_unit_01",
            unityAdsEnabled = true,
            unityGameId = "5789123",
            appLovinEnabled = true,
            appLovinSdkKey = "applovin_key_01",
            mobileWebAdsEnabled = true,
            mobileAdNetworkPriority = "ADSTERRA",
            mobileCustomAdHtml = "<div id='unity-banner'>Ad</div>",
            mobileDirectLinkUrl = "https://example.com/smartlink",
            adsterraEnabled = true,
            monetagEnabled = true
        )

        val blocked = repo.adminSaveAdConfig(buyer, updatedConfig)
        assertTrue(blocked.isFailure)

        val allowed = repo.adminSaveAdConfig(admin, updatedConfig)
        assertTrue(allowed.isSuccess)
        assertEquals("lp_app_key_live_01", repo.adConfig.value.mediationAppKey)
        assertEquals("lp_banner_unit_01", repo.adConfig.value.androidBannerId)
        assertEquals("5789123", repo.adConfig.value.unityGameId)
        assertTrue(repo.adConfig.value.adTestMode)
        assertTrue(repo.adConfig.value.rewardedEnabled)
        assertTrue(repo.adConfig.value.appOpenEnabled)
        assertTrue(repo.adConfig.value.mobileWebAdsEnabled)
        assertEquals("ADSTERRA", repo.adConfig.value.mobileAdNetworkPriority)
        assertEquals("<div id='unity-banner'>Ad</div>", repo.adConfig.value.mobileCustomAdHtml)
        assertEquals("https://example.com/smartlink", repo.adConfig.value.mobileDirectLinkUrl)
        assertTrue(repo.adConfig.value.adsterraEnabled)
        assertTrue(repo.adConfig.value.monetagEnabled)

        val logs = repo.auditLogs.first()
        assertTrue(logs.any { it.actionBn == "বিজ্ঞাপন ও মনিটাইজেশন আপডেট" })
    }

    @Test
    fun bothFarmerAndBuyerStartPaymentPendingAndAdminCanVerifyBoth() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()

        val farmer = repo.registerUser(
            fullName = "যাচাই কৃষক",
            phone = "01777000001",
            rawPassword = "pass1234",
            role = UserRole.FARMER,
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "কুমারখালী",
            unionName = "",
            village = "",
            nidLastFour = "1234",
            farmBio = "ধান"
        ).getOrThrow()

        val buyer = repo.registerUser(
            fullName = "যাচাই ক্রেতা",
            phone = "01777000002",
            rawPassword = "pass1234",
            role = UserRole.BUYER,
            division = "ঢাকা",
            district = "ঢাকা",
            upazila = "সাভার",
            unionName = "",
            village = "",
            nidLastFour = "5678",
            farmBio = ""
        ).getOrThrow()

        // Both start PENDING / PAYMENT_PENDING and inactive cannot list crops yet
        assertEquals("PENDING", farmer.verificationStatus)
        assertEquals("PENDING", buyer.verificationStatus)
        assertEquals(AccountStatus.PAYMENT_PENDING.name, farmer.accountStatus)
        assertEquals(AccountStatus.PAYMENT_PENDING.name, buyer.accountStatus)
        assertTrue(farmer.isMobileVerified)
        assertTrue(buyer.isMobileVerified)

        val blockedListing = repo.createFarmerProduct(
            farmer = farmer,
            category = cat,
            nameBn = "নতুন ধান",
            descriptionBn = "টাটকা ধান",
            imageUri = "",
            quantity = 10.0,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = 1300.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        )
        assertTrue(blockedListing.isFailure)

        // Non-admin cannot verify
        assertTrue(repo.adminVerifyUser(buyer, farmer.id, true).isFailure)

        // Admin approves buyer
        assertTrue(repo.adminVerifyUser(admin, buyer.id, true).isSuccess)
        val verifiedBuyer = db.userDao().getUserById(buyer.id)!!
        assertEquals("VERIFIED", verifiedBuyer.verificationStatus)
        assertEquals(AccountStatus.ACTIVE.name, verifiedBuyer.accountStatus)

        // Admin rejects then approves farmer, then farmer can list crops as verified
        assertTrue(repo.adminVerifyUser(admin, farmer.id, false).isSuccess)
        assertEquals("REJECTED", db.userDao().getUserById(farmer.id)!!.verificationStatus)
        assertTrue(repo.adminVerifyUser(admin, farmer.id, true).isSuccess)
        val activeFarmer = db.userDao().getUserById(farmer.id)!!
        assertEquals("VERIFIED", activeFarmer.verificationStatus)
        assertEquals(AccountStatus.ACTIVE.name, activeFarmer.accountStatus)

        val prod = repo.createFarmerProduct(
            farmer = activeFarmer,
            category = cat,
            nameBn = "নতুন ধান",
            descriptionBn = "টাটকা ধান",
            imageUri = "",
            quantity = 10.0,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = 1300.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        ).getOrThrow()
        assertTrue(prod.farmerVerified)

        val notifications = repo.notifications.first()
        assertTrue(notifications.any { it.userId == buyer.id && it.type == "VERIFICATION" && it.targetRole == "BUYER" })
        assertTrue(notifications.any { it.userId == farmer.id && it.type == "VERIFICATION" && it.targetRole == "FARMER" })

        val logs = repo.auditLogs.first()
        assertTrue(logs.any { it.actionBn == "ক্রেতা যাচাইকরণ (VERIFIED)" })
        assertTrue(logs.any { it.actionBn == "কৃষক যাচাইকরণ (VERIFIED)" })
    }

    @Test
    fun registrationRequiresConfirmPasswordAndBlocksDuplicatePhone() = runBlocking {
        MasterCatalogSeeder.initialize(db)

        val mismatch = repo.registerUser(
            fullName = "ভুল পাসওয়ার্ড কৃষক",
            phone = "01712345678",
            rawPassword = "password1",
            confirmPassword = "password2",
            role = UserRole.FARMER,
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "কুমারখালী",
            unionName = "বাঁশগ্রাম",
            village = "শিমুলিয়া",
            nidLastFour = "1234",
            farmBio = "ধান"
        )
        assertTrue(mismatch.isFailure)

        val first = repo.registerUser(
            fullName = "সঠিক কৃষক",
            phone = "01712345678",
            rawPassword = "password1",
            confirmPassword = "password1",
            role = UserRole.FARMER,
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "কুমারখালী",
            unionName = "বাঁশগ্রাম",
            village = "শিমুলিয়া",
            nidLastFour = "1234",
            farmBio = "ধান"
        )
        assertTrue(first.isSuccess)
        val created = first.getOrThrow()
        assertEquals(AccountStatus.PAYMENT_PENDING.name, created.accountStatus)
        assertEquals(0L, created.activationDate)
        assertEquals(0L, created.expiryDate)
        assertEquals(2, created.validityYears)

        val dup = repo.registerUser(
            fullName = "ডুপ্লিকেট কৃষক",
            phone = "+8801712345678",
            rawPassword = "password1",
            confirmPassword = "password1",
            role = UserRole.BUYER,
            division = "ঢাকা",
            district = "ঢাকা",
            upazila = "সাভার",
            unionName = "",
            village = "",
            nidLastFour = "",
            farmBio = ""
        )
        assertTrue(dup.isFailure)
    }

    @Test
    fun activationPaymentEnforcesFixedFeeDuplicateTxIdAnd2YearValidityRenewal() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()

        val farmer = repo.registerUser(
            fullName = "পেমেন্ট কৃষক",
            phone = "01744000011",
            rawPassword = "pass1234",
            confirmPassword = "pass1234",
            role = UserRole.FARMER,
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "ভেড়ামারা",
            unionName = "",
            village = "",
            nidLastFour = "1010",
            farmBio = "ভুট্টা"
        ).getOrThrow()

        val buyer = repo.registerUser(
            fullName = "পেমেন্ট ক্রেতা",
            phone = "01744000012",
            rawPassword = "pass1234",
            confirmPassword = "pass1234",
            role = UserRole.BUYER,
            division = "ঢাকা",
            district = "ঢাকা",
            upazila = "সাভার",
            unionName = "",
            village = "",
            nidLastFour = "",
            farmBio = ""
        ).getOrThrow()

        // Wrong amount (not 300) rejected
        assertTrue(
            repo.submitActivationPayment(
                user = farmer,
                paymentMethod = PaymentMethod.BKASH,
                senderMobile = "01744000011",
                transactionId = "TX99887766",
                amount = 200.0,
                receiptUri = ""
            ).isFailure
        )

        // Valid BDT 300 submission succeeds & transitions user to PAYMENT_UNDER_REVIEW
        val pay1 = repo.submitActivationPayment(
            user = farmer,
            paymentMethod = PaymentMethod.BKASH,
            senderMobile = "01744000011",
            transactionId = "tx 9988 7766",
            amount = 300.0,
            receiptUri = ""
        ).getOrThrow()
        assertEquals("TX99887766", pay1.transactionId)
        assertEquals(AccountStatus.PAYMENT_UNDER_REVIEW.name, db.userDao().getUserById(farmer.id)!!.accountStatus)

        // Same user cannot submit duplicate PENDING payment
        assertTrue(
            repo.submitActivationPayment(
                user = db.userDao().getUserById(farmer.id)!!,
                paymentMethod = PaymentMethod.NAGAD,
                senderMobile = "01744000011",
                transactionId = "TX11112222",
                amount = 300.0,
                receiptUri = ""
            ).isFailure
        )

        // Different user cannot reuse normalized duplicate TxID
        assertTrue(
            repo.submitActivationPayment(
                user = buyer,
                paymentMethod = PaymentMethod.NAGAD,
                senderMobile = "01744000012",
                transactionId = "tx99887766",
                amount = 300.0,
                receiptUri = ""
            ).isFailure
        )

        // Non-admin cannot approve
        assertTrue(repo.adminApproveActivationPayment(buyer, pay1.id).isFailure)

        // Admin approves farmer -> ACTIVE for 2 calendar years from approval timestamp
        val approvalNow = 1_760_000_000_000L
        val activatedFarmer = repo.adminApproveActivationPayment(admin, pay1.id, "ভেরিফায়েড", now = approvalNow).getOrThrow()
        assertEquals(AccountStatus.ACTIVE.name, activatedFarmer.accountStatus)
        assertEquals(approvalNow, activatedFarmer.activationDate)
        assertEquals(2, activatedFarmer.validityYears)
        val expectedCal = Calendar.getInstance().apply {
            timeInMillis = approvalNow
            add(Calendar.YEAR, 2)
        }
        assertEquals(expectedCal.timeInMillis, activatedFarmer.expiryDate)

        // Early renewal before expiry adds 2 more calendar years to existing expiryDate
        val renewalPay = repo.submitActivationPayment(
            user = activatedFarmer,
            paymentMethod = PaymentMethod.NAGAD,
            senderMobile = "01744000011",
            transactionId = "RENEW2028",
            amount = 300.0,
            receiptUri = "",
            isRenewal = true
        ).getOrThrow()
        val renewedFarmer = repo.adminApproveActivationPayment(admin, renewalPay.id, now = approvalNow + 1000L).getOrThrow()
        val expectedRenewedCal = Calendar.getInstance().apply {
            timeInMillis = expectedCal.timeInMillis
            add(Calendar.YEAR, 2)
        }
        assertEquals(expectedRenewedCal.timeInMillis, renewedFarmer.expiryDate)

        // Rejecting with resubmission request resets status and lets buyer submit new TxID
        val buyerPay = repo.submitActivationPayment(
            user = buyer,
            paymentMethod = PaymentMethod.NAGAD,
            senderMobile = "01744000012",
            transactionId = "BADTX001",
            amount = 300.0,
            receiptUri = ""
        ).getOrThrow()
        assertTrue(repo.adminRejectActivationPayment(admin, buyerPay.id, "অস্পষ্ট TxID", requestResubmission = true).isSuccess)
        assertEquals(ActivationPaymentStatus.RESUBMISSION_REQUESTED.name, db.activationPaymentDao().getPaymentById(buyerPay.id)!!.status)
        assertEquals(AccountStatus.PAYMENT_REJECTED.name, db.userDao().getUserById(buyer.id)!!.accountStatus)
        assertTrue(
            repo.submitActivationPayment(
                user = db.userDao().getUserById(buyer.id)!!,
                paymentMethod = PaymentMethod.BKASH,
                senderMobile = "01744000012",
                transactionId = "GOODTX002",
                amount = 300.0,
                receiptUri = ""
            ).isSuccess
        )
    }

    @Test
    fun expiryEvaluatorCreatesDeduplicatedRemindersAndDisablesExpiredUser() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()

        val farmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "মেয়াদ কৃষক",
                phone = "01755000022",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "দৌলতপুর",
                unionName = "",
                village = "",
                nidLastFour = "2233",
                farmBio = "ধান"
            ).getOrThrow()
        )
        val expiry = farmer.expiryDate
        val dayMs = 24L * 60L * 60L * 1000L

        // 20 days left -> 30-day reminder once
        repo.evaluateAccountExpiries(now = expiry - 20L * dayMs)
        repo.evaluateAccountExpiries(now = expiry - 19L * dayMs)
        val notifs30 = repo.notifications.first().filter { it.userId == farmer.id && it.type == "EXPIRY_REMINDER_30D" }
        assertEquals(1, notifs30.size)

        // 5 days left -> 7-day reminder once
        repo.evaluateAccountExpiries(now = expiry - 5L * dayMs)
        val notifs7 = repo.notifications.first().filter { it.userId == farmer.id && it.type == "EXPIRY_REMINDER_7D" }
        assertEquals(1, notifs7.size)

        // At expiry -> transitions to EXPIRED, sends ACCOUNT_EXPIRED notification, and blocks listing
        repo.evaluateAccountExpiries(now = expiry + 1000L)
        val expiredUser = db.userDao().getUserById(farmer.id)!!
        assertEquals(AccountStatus.EXPIRED.name, expiredUser.accountStatus)
        assertTrue(repo.notifications.first().any { it.userId == farmer.id && it.type == "ACCOUNT_EXPIRED" })

        val blockedListing = repo.createFarmerProduct(
            farmer = expiredUser,
            category = cat,
            nameBn = "মেয়াদোত্তীর্ণ ধান",
            descriptionBn = "",
            imageUri = "",
            quantity = 10.0,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = 1300.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "দৌলতপুর",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        )
        assertTrue(blockedListing.isFailure)
    }

    @Test
    fun monOnlyUnitEnforcementRejectsKgAndAcceptsMon() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()
        val farmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "মণ একক কৃষক",
                phone = "01766000033",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "কুমারখালী",
                unionName = "",
                village = "",
                nidLastFour = "4455",
                farmBio = ""
            ).getOrThrow()
        )

        val kgResult = repo.createFarmerProduct(
            farmer = farmer,
            category = cat,
            nameBn = "ধান",
            descriptionBn = "",
            imageUri = "",
            quantity = 10.0,
            unit = "কেজি",
            pricePerUnit = 50.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        )
        assertTrue(kgResult.isFailure)

        val monResult = repo.createFarmerProduct(
            farmer = farmer,
            category = cat,
            nameBn = "ধান",
            descriptionBn = "",
            imageUri = "",
            quantity = 10.0,
            unit = "মণ",
            pricePerUnit = 1300.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        )
        assertTrue(monResult.isSuccess)
        assertEquals("মণ", monResult.getOrThrow().unit)
        assertEquals(40, ProductUnit.MON_TO_KG)
    }

    @Test
    fun fiveStepOrderSecurityConfirmsOnFarmerApprovalWithoutAdminApproval() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()

        val farmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "নিরাপদ অর্ডার কৃষক",
                phone = "01777000044",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "কুমারখালী",
                unionName = "বাঁশগ্রাম",
                village = "শিমুলিয়া",
                nidLastFour = "6677",
                farmBio = ""
            ).getOrThrow()
        )
        val buyer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "নিরাপদ অর্ডার ক্রেতা",
                phone = "01777000045",
                rawPassword = "pass1234",
                role = UserRole.BUYER,
                division = "ঢাকা",
                district = "ঢাকা",
                upazila = "সাভার",
                unionName = "",
                village = "",
                nidLastFour = "",
                farmBio = ""
            ).getOrThrow()
        )

        val prod = repo.createFarmerProduct(
            farmer = farmer,
            category = cat,
            nameBn = "আমন ধান",
            descriptionBn = "",
            imageUri = "",
            quantity = 15.0,
            unit = "মণ",
            pricePerUnit = 1300.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        ).getOrThrow()

        // Step 1: Admin approves product, then buyer places wholesale order -> starts PAYMENT_PENDING
        val approvedProd = repo.adminVerifyProduct(admin, prod.id, approved = true).getOrThrow()
        repo.addToCart(approvedProd, 10.0, buyer).getOrThrow()
        val order = repo.placeOrdersFromCart(
            buyer = buyer,
            deliveryOption = DeliveryOption.FARM_PICKUP,
            deliveryAddress = "",
            paymentMethod = PaymentMethod.CASH_ON_PICKUP,
            transactionRef = ""
        ).getOrThrow().first()
        assertEquals(OrderStatus.PAYMENT_PENDING.name, order.status)
        assertTrue(prod.isWholesale)
        assertEquals(ProductUnit.MON.labelBn, prod.unit)

        // Cannot transition to CONFIRMED or COMPLETED before farmer verifies payment
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.CONFIRMED, farmer).isFailure)
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.COMPLETED, buyer).isFailure)

        // Step 2: Farmer accepts order
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.ACCEPTED, farmer).isSuccess)

        // Step 3: Buyer submits payment TxID -> must NOT auto-confirm!
        val submitted = repo.submitOrderPayment(
            orderId = order.id,
            buyer = buyer,
            paymentMethod = PaymentMethod.BKASH,
            senderNumber = "01777000045",
            transactionReference = "ORDTX1234",
            receiptUri = ""
        ).getOrThrow()
        assertEquals(OrderStatus.ACCEPTED.name, submitted.status)
        assertEquals("PENDING_VERIFICATION", submitted.paymentStatus)
        assertFalse(submitted.farmerPaymentConfirmed)
        assertFalse(submitted.adminPaymentVerified)

        // Still cannot transition to CONFIRMED or COMPLETED until farmer actually verifies
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.CONFIRMED, farmer).isFailure)
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.COMPLETED, buyer).isFailure)

        // Step 4 & 5: Farmer confirms money received -> immediately CONFIRMED & PAID without admin approval!
        val afterFarmer = repo.farmerConfirmPaymentReceived(order.id, farmer).getOrThrow()
        assertTrue(afterFarmer.farmerPaymentConfirmed)
        assertFalse(afterFarmer.adminPaymentVerified)
        assertEquals(OrderStatus.CONFIRMED.name, afterFarmer.status)
        assertEquals("PAID", afterFarmer.paymentStatus)

        // Buyer/Farmer can immediately mark COMPLETED without needing admin verification
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.COMPLETED, buyer).isSuccess)
        assertEquals(OrderStatus.COMPLETED.name, db.orderDao().getOrderById(order.id)!!.status)
    }

    @Test
    fun profileUpdatePropagatesToFarmerProductsAndAdminUpdatesPaymentSettings() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()

        val farmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "পুরোনো নাম",
                phone = "01788000055",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "কুমারখালী",
                unionName = "পুরোনো ইউনিয়ন",
                village = "পুরোনো গ্রাম",
                nidLastFour = "1234",
                farmBio = "ধান"
            ).getOrThrow()
        )
        val prod = repo.createFarmerProduct(
            farmer = farmer,
            category = cat,
            nameBn = "সরিষা",
            descriptionBn = "",
            imageUri = "",
            quantity = 5.0,
            unit = "মণ",
            pricePerUnit = 3200.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        ).getOrThrow()

        val updatedUser = repo.updateUserProfile(
            userId = farmer.id,
            fullName = "নতুন নাম কৃষক",
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "খোকসা",
            unionName = "জানিপুর",
            village = "নতুন গ্রাম",
            farmBio = "ধান ও সরিষা চাষি",
            nidLastFour = "9876",
            profileImageUri = "data:image/jpeg;base64,/9j/4AAQ"
        ).getOrThrow()
        assertEquals("নতুন নাম কৃষক", updatedUser.fullName)
        assertEquals("NID-****-9876", updatedUser.nidMasked)
        val updatedProd = db.productDao().getProductById(prod.id)!!
        assertEquals("নতুন নাম কৃষক", updatedProd.farmerName)
        assertEquals("খোকসা", updatedProd.upazila)
        assertEquals("জানিপুর", updatedProd.unionName)

        // Payment settings permission & update
        assertTrue(repo.adminSavePaymentSettings(updatedUser, PaymentSettingsEntity()).isFailure)
        val savedSettings = repo.adminSavePaymentSettings(
            admin,
            PaymentSettingsEntity(
                bKashNumber = "01756144900",
                bKashAccountType = "MERCHANT",
                nagadNumber = "01756144900",
                nagadAccountType = "PERSONAL",
                paymentInstructions = "Send Money করুন"
            )
        ).getOrThrow()
        assertEquals("MERCHANT", savedSettings.bKashAccountType)
        assertEquals("PERSONAL", savedSettings.nagadAccountType)
        assertEquals(300.0, savedSettings.farmerFee, 0.01)
        assertEquals(2, savedSettings.validityYears)
    }

    @Test
    fun markNotificationsReadMarksOnlyMatchingUserOrRoleAndPreservesOthers() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val farmer = repo.registerUser(
            fullName = "নোটিফিকেশন কৃষক",
            phone = "01766000001",
            rawPassword = "pass1234",
            role = UserRole.FARMER,
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "কুষ্টিয়া সদর",
            unionName = "",
            village = "",
            nidLastFour = "9988",
            farmBio = ""
        ).getOrThrow()

        val buyer = repo.registerUser(
            fullName = "নোটিফিকেশন ক্রেতা",
            phone = "01766000002",
            rawPassword = "pass1234",
            role = UserRole.BUYER,
            division = "ঢাকা",
            district = "ঢাকা",
            upazila = "সাভার",
            unionName = "",
            village = "",
            nidLastFour = "",
            farmBio = ""
        ).getOrThrow()

        val farmerDirect = com.example.data.local.NotificationEntity(
            id = "not_f1",
            userId = farmer.id,
            targetRole = UserRole.FARMER.name,
            titleBn = "কৃষকের অর্ডার",
            messageBn = "অর্ডার ১",
            isRead = false
        )
        val farmerRoleBroadcast = com.example.data.local.NotificationEntity(
            id = "not_fall",
            userId = "ALL",
            targetRole = UserRole.FARMER.name,
            titleBn = "সকল কৃষক",
            messageBn = "ঘোষণা",
            isRead = false
        )
        val buyerDirect = com.example.data.local.NotificationEntity(
            id = "not_b1",
            userId = buyer.id,
            targetRole = UserRole.BUYER.name,
            titleBn = "ক্রেতার আপডেট",
            messageBn = "অর্ডার ২",
            isRead = false
        )

        val dao = db.notificationAuditDao()
        dao.insertNotification(farmerDirect)
        dao.insertNotification(farmerRoleBroadcast)
        dao.insertNotification(buyerDirect)

        repo.markNotificationsRead(farmer)

        assertTrue(dao.getNotificationById("not_f1")!!.isRead)
        assertTrue(dao.getNotificationById("not_fall")!!.isRead)
        assertFalse(dao.getNotificationById("not_b1")!!.isRead)
    }

    @Test
    fun roleBasedUiStateReflectsLoggedInFarmerBuyerAdminAndGuest() = runBlocking {
        MasterCatalogSeeder.initialize(db)

        val guestState = com.example.ui.viewmodel.MarketplaceUiState(isLoading = false, activeUser = null)
        assertNull(guestState.activeRole)
        assertFalse(guestState.isSuperAdmin)

        val farmer = repo.registerUser(
            fullName = "রোল টেস্ট কৃষক",
            phone = "01788000001",
            rawPassword = "pass1234",
            role = UserRole.FARMER,
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "কুমারখালী",
            unionName = "",
            village = "",
            nidLastFour = "1111",
            farmBio = "ধান"
        ).getOrThrow()

        val farmerState = com.example.ui.viewmodel.MarketplaceUiState(isLoading = false, activeUser = farmer)
        assertEquals(UserRole.FARMER, farmerState.activeRole)
        assertFalse(farmerState.isSuperAdmin)

        val buyer = repo.registerUser(
            fullName = "রোল টেস্ট ক্রেতা",
            phone = "01788000002",
            rawPassword = "pass1234",
            role = UserRole.BUYER,
            division = "ঢাকা",
            district = "ঢাকা",
            upazila = "সাভার",
            unionName = "",
            village = "",
            nidLastFour = "",
            farmBio = ""
        ).getOrThrow()

        val buyerState = com.example.ui.viewmodel.MarketplaceUiState(isLoading = false, activeUser = buyer)
        assertEquals(UserRole.BUYER, buyerState.activeRole)
        assertFalse(buyerState.isSuperAdmin)

        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val adminState = com.example.ui.viewmodel.MarketplaceUiState(isLoading = false, activeUser = admin)
        assertEquals(UserRole.ADMIN, adminState.activeRole)
        assertTrue(adminState.isSuperAdmin)
    }

    @Test
    fun banglaUnicodeNormalizationMatchesNfdAndNfcAcrossSearchAndLocationFilters() {
        val kushtiaNfc = "কুষ্টি\u09DFা" // NFC য়
        val kushtiaNfd = "কুষ্টি\u09AF\u09BCা" // NFD য + ়
        val mirpur = "মিরপুর"

        assertTrue(BangladeshLocations.upazilasForDistrict(kushtiaNfd).isNotEmpty())
        assertEquals(
            BangladeshLocations.upazilasForDistrict(kushtiaNfc),
            BangladeshLocations.upazilasForDistrict(kushtiaNfd)
        )

        val farmer = UserEntity(
            id = "farmer_bn_norm",
            fullName = "সাজ্জাদ আহমেদ",
            phone = "01712345678",
            passwordHash = "hash",
            role = UserRole.FARMER.name,
            division = "খুলনা",
            district = kushtiaNfc,
            upazila = mirpur,
            unionName = "পোড়াদহ",
            village = "হালসা",
            farmBio = "মিনিকেট ধান",
            verificationStatus = "VERIFIED",
            accountStatus = AccountStatus.ACTIVE.name
        )
        val product = com.example.data.local.ProductEntity(
            id = "prod_bn_norm",
            farmerId = farmer.id,
            farmerName = farmer.fullName,
            farmerVerified = true,
            categoryId = "cat_rice",
            categoryNameBn = "চাল",
            nameBn = "মিনিকেট চাল",
            nameEn = "Miniket Rice",
            descriptionBn = "টাটকা ফসল",
            availableQuantity = 10.0,
            pricePerUnit = 1200.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            division = "খুলনা",
            district = kushtiaNfc,
            upazila = mirpur,
            unionName = "পোড়াদহ",
            village = "হালসা",
            pickupAddress = "$mirpur, $kushtiaNfc",
            status = "ACTIVE"
        )

        val filteredByNfdDistrict = com.example.ui.viewmodel.MarketplaceUiState(
            isLoading = false,
            products = listOf(product),
            farmers = listOf(farmer),
            selectedDistrict = kushtiaNfd
        )
        assertEquals(1, filteredByNfdDistrict.filteredProducts.size)
        assertEquals(1, filteredByNfdDistrict.filteredFarmers.size)

        val filteredByNfdSearch = com.example.ui.viewmodel.MarketplaceUiState(
            isLoading = false,
            products = listOf(product),
            farmers = listOf(farmer),
            searchQuery = kushtiaNfd
        )
        assertEquals(1, filteredByNfdSearch.filteredProducts.size)
        assertEquals(1, filteredByNfdSearch.filteredFarmers.size)
    }

    @Test
    fun syncManagerSafelySkipsSdkWithoutGoogleServicesAndPreservesLocalCache() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val syncManagerWithAppContext = FirebaseRealtimeSyncManager(enableRemoteSync = true, appContext = context)
        val syncScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined
        )
        // Verifies SDK initialization guard skips FirebaseDatabase cleanly when google-services.json is absent
        syncManagerWithAppContext.startRealtimeListeners(
            localDb = db,
            scope = syncScope,
            onIncomingNotification = {}
        )
        syncScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()

        val testRepo = MarketplaceRepository(
            db = db,
            prefs = UserPreferences(context),
            rtdbSync = FirebaseRealtimeSyncManager(enableRemoteSync = false, appContext = context)
        )
        val categoriesBefore = db.categoryDao().observeCategories().first()
        assertTrue(categoriesBefore.isNotEmpty())

        val farmer = repo.registerUser(
            fullName = "লোকাল ক্যাশ কৃষক",
            phone = "01799000011",
            rawPassword = "pass1234",
            role = UserRole.FARMER,
            division = "খুলনা",
            district = "কুষ্টিয়া",
            upazila = "ভেড়ামারা",
            unionName = "",
            village = "",
            nidLastFour = "1234",
            farmBio = "ভুট্টা"
        ).getOrThrow()

        testRepo.initializeCatalogsAndSync()
        assertFalse(testRepo.syncNow())
        assertFalse(testRepo.isSyncing.value)
        assertNotNull(db.userDao().getUserById(farmer.id))
        assertTrue(db.categoryDao().observeCategories().first().isNotEmpty())
    }

    @Test
    fun wholesaleOnlyAndCustomAdvancePaymentVerificationWorkflow() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()

        val farmer1 = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "পাইকারি কৃষক ১",
                phone = "01711110001",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "কুমারখালী",
                unionName = "",
                village = "",
                nidLastFour = "1111",
                farmBio = "ধান"
            ).getOrThrow()
        )
        val farmer2 = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "পাইকারি কৃষক ২",
                phone = "01711110002",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "রাজশাহী",
                district = "বগুড়া",
                upazila = "সদর",
                unionName = "",
                village = "",
                nidLastFour = "2222",
                farmBio = "আলু"
            ).getOrThrow()
        )
        val buyer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "পাইকারি ক্রেতা",
                phone = "01711110003",
                rawPassword = "pass1234",
                role = UserRole.BUYER,
                division = "ঢাকা",
                district = "ঢাকা",
                upazila = "সাভার",
                unionName = "",
                village = "",
                nidLastFour = "",
                farmBio = ""
            ).getOrThrow()
        )

        // Farmer payment settings isolation check
        repo.saveFarmerPaymentSettings(
            farmer = farmer1,
            bKashNumber = "01700001111",
            nagadNumber = "01800001111",
            rocketNumber = "01900001111",
            bankAccountInfo = "Sonali Bank AC-111"
        ).getOrThrow()
        repo.saveFarmerPaymentSettings(
            farmer = farmer2,
            bKashNumber = "01700002222",
            nagadNumber = "",
            rocketNumber = "",
            bankAccountInfo = ""
        ).getOrThrow()
        assertEquals("01700001111", repo.getFarmerPaymentSettingsOnce(farmer1.id)?.bKashNumber)
        assertEquals("01700002222", repo.getFarmerPaymentSettingsOnce(farmer2.id)?.bKashNumber)

        // Wholesale-only enforcement: even if isWholesale=false is passed, stored product isWholesale=true
        val product = repo.createFarmerProduct(
            farmer = farmer1,
            category = cat,
            nameBn = "হাইব্রিড আলু (পাইকারি)",
            descriptionBn = "",
            imageUri = "",
            quantity = 10.0,
            unit = "মণ",
            pricePerUnit = 100.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        ).getOrThrow()
        assertTrue(product.isWholesale)
        assertEquals("মণ", product.unit)

        // Admin approves crop before buyer can order it
        val approvedProduct = repo.adminVerifyProduct(admin, product.id, approved = true).getOrThrow()

        // Order total = ৳100, buyer advance = ৳50
        repo.addToCart(approvedProduct, 1.0, buyer).getOrThrow()
        val placed = repo.placeOrdersFromCart(
            buyer = buyer,
            deliveryOption = DeliveryOption.FARM_PICKUP,
            deliveryAddress = "",
            paymentMethod = PaymentMethod.BKASH,
            transactionRef = "TXADV50",
            senderNumber = "01711110003",
            advanceByFarmer = mapOf(farmer1.id to 50.0),
            paymentReferenceNote = "First advance"
        ).getOrThrow().first()

        // Before farmer verifies, verifiedAdvanceTotal must remain 0 and remainingDue = 100
        assertEquals(OrderStatus.PAYMENT_PENDING.name, placed.status)
        assertEquals(100.0, placed.grandTotal, 0.01)
        assertEquals(50.0, placed.advanceAmountRequested, 0.01)
        assertEquals(0.0, placed.verifiedAdvanceTotal, 0.01)
        assertEquals(100.0, placed.remainingDue, 0.01)

        val firstPayment = db.orderPaymentDao().getPaymentsForOrderOnce(placed.id).single()
        assertEquals("01700001111", firstPayment.receiverNumber)
        assertEquals("TXADV50", firstPayment.transactionId)

        // Duplicate TxID across system must be blocked
        assertTrue(
            repo.submitOrderAdvancePayment(
                orderId = placed.id,
                buyer = buyer,
                amount = 20.0,
                paymentMethod = PaymentMethod.NAGAD,
                senderNumber = "01711110003",
                transactionId = " txadv50 "
            ).isFailure
        )

        // Unauthorized farmer2 cannot confirm farmer1's payment
        assertTrue(repo.farmerVerifyOrderPayment(firstPayment.id, farmer2, approved = true).isFailure)

        // Farmer1 confirms ৳50 -> Verified Advance = ৳50, Remaining Due = ৳50, Status = CONFIRMED
        val afterFirstVerify = repo.farmerVerifyOrderPayment(firstPayment.id, farmer1, approved = true).getOrThrow()
        assertEquals(50.0, afterFirstVerify.verifiedAdvanceTotal, 0.01)
        assertEquals(50.0, afterFirstVerify.remainingDue, 0.01)
        assertEquals(OrderStatus.CONFIRMED.name, afterFirstVerify.status)
        assertEquals("PARTIAL_ADVANCE_VERIFIED", afterFirstVerify.paymentStatus)

        // Multiple verified partial payments (৳30 + ৳20 = ৳50 remaining)
        val p2 = repo.submitOrderAdvancePayment(
            orderId = placed.id,
            buyer = buyer,
            amount = 30.0,
            paymentMethod = PaymentMethod.NAGAD,
            senderNumber = "01711110003",
            transactionId = "TXPART30",
            referenceNote = "Second partial"
        ).getOrThrow()
        val afterSecondVerify = repo.farmerVerifyOrderPayment(p2.id, farmer1, approved = true).getOrThrow()
        assertEquals(80.0, afterSecondVerify.verifiedAdvanceTotal, 0.01)
        assertEquals(20.0, afterSecondVerify.remainingDue, 0.01)

        // Overpaying remaining due (> ৳20) must be rejected
        assertTrue(
            repo.submitOrderAdvancePayment(
                orderId = placed.id,
                buyer = buyer,
                amount = 25.0,
                paymentMethod = PaymentMethod.ROCKET,
                senderNumber = "01711110003",
                transactionId = "TXOVER25"
            ).isFailure
        )

        val p3 = repo.submitOrderAdvancePayment(
            orderId = placed.id,
            buyer = buyer,
            amount = 20.0,
            paymentMethod = PaymentMethod.ROCKET,
            senderNumber = "01711110003",
            transactionId = "TXFINAL20",
            referenceNote = "Final payment"
        ).getOrThrow()
        val afterFinalVerify = repo.farmerVerifyOrderPayment(p3.id, farmer1, approved = true).getOrThrow()
        assertEquals(100.0, afterFinalVerify.verifiedAdvanceTotal, 0.01)
        assertEquals(0.0, afterFinalVerify.remainingDue, 0.01)
        assertEquals("PAID", afterFinalVerify.paymentStatus)
    }

    @Test
    fun zeroAdvanceOrderStartsPaymentPendingAndFarmerApprovesToConfirmedAndCompleted() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val cat = repo.categories.first().first()

        val farmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "শূন্য অগ্রিম কৃষক",
                phone = "01722220001",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "ভেড়ামারা",
                unionName = "",
                village = "",
                nidLastFour = "3333",
                farmBio = "মরিচ"
            ).getOrThrow()
        )
        val buyer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "শূন্য অগ্রিম ক্রেতা",
                phone = "01722220002",
                rawPassword = "pass1234",
                role = UserRole.BUYER,
                division = "ঢাকা",
                district = "ঢাকা",
                upazila = "সাভার",
                unionName = "",
                village = "",
                nidLastFour = "",
                farmBio = ""
            ).getOrThrow()
        )
        val prod = repo.createFarmerProduct(
            farmer = farmer,
            category = cat,
            nameBn = "কাঁচা মরিচ",
            descriptionBn = "",
            imageUri = "",
            quantity = 5.0,
            unit = "মণ",
            pricePerUnit = 200.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "ভেড়ামারা",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0
        ).getOrThrow()

        val approvedProd = repo.adminVerifyProduct(admin, prod.id, approved = true).getOrThrow()
        repo.addToCart(approvedProd, 1.0, buyer).getOrThrow()
        val order = repo.placeOrdersFromCart(
            buyer = buyer,
            deliveryOption = DeliveryOption.FARM_PICKUP,
            deliveryAddress = "",
            paymentMethod = PaymentMethod.CASH_ON_PICKUP,
            transactionRef = "",
            advanceByFarmer = mapOf(farmer.id to 0.0)
        ).getOrThrow().first()

        assertEquals(OrderStatus.PAYMENT_PENDING.name, order.status)
        assertEquals("ZERO_ADVANCE_PENDING", order.paymentStatus)
        assertEquals(200.0, order.remainingDue, 0.01)

        val approved = repo.approveZeroAdvanceOrder(order.id, farmer).getOrThrow()
        assertEquals(OrderStatus.CONFIRMED.name, approved.status)
        assertEquals("ZERO_ADVANCE_APPROVED", approved.paymentStatus)

        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.COMPLETED, buyer).isSuccess)
        assertEquals(OrderStatus.COMPLETED.name, db.orderDao().getOrderById(order.id)!!.status)
    }

    @Test
    fun directUnityAds6227313DefaultsAndHydrationAreActive() {
        val defaultConfig = com.example.data.model.AdConfig()
        assertEquals("6227313", defaultConfig.unityGameId)
        assertEquals("Banner_Android", defaultConfig.androidBannerId)
        assertEquals("Interstitial_Android", defaultConfig.androidInterstitialId)
        assertEquals("Rewarded_Android", defaultConfig.androidRewardedId)
        assertTrue(defaultConfig.adsEnabled)
        assertTrue(defaultConfig.androidAdsEnabled)
        assertTrue(defaultConfig.unityAdsEnabled)
        assertTrue(defaultConfig.rewardedEnabled)
        assertTrue(defaultConfig.usesDirectUnityAds())

        val legacyBlankConfig = com.example.data.model.AdConfig(
            mediationAppKey = "",
            unityGameId = "",
            androidBannerId = "",
            androidInterstitialId = "",
            androidRewardedId = ""
        )
        val hydrated = legacyBlankConfig.hydratedWithDefaults()
        assertEquals("6227313", hydrated.unityGameId)
        assertEquals("Banner_Android", hydrated.androidBannerId)
        assertEquals("Interstitial_Android", hydrated.androidInterstitialId)
        assertEquals("Rewarded_Android", hydrated.androidRewardedId)
        assertTrue(hydrated.usesDirectUnityAds())

        com.example.ads.AdMonetizationManager.resetForTest()
        com.example.ads.AdMonetizationManager.syncConfig(context, hydrated)
        val telemetry = com.example.ads.AdMonetizationManager.telemetry.value
        assertTrue(telemetry.mediationPlatform.contains("Unity", ignoreCase = true))
        assertEquals("Banner_Android", telemetry.formatMetrics[com.example.ads.AdFormatType.BANNER]?.unitOrPlacementId)
        assertEquals("Interstitial_Android", telemetry.formatMetrics[com.example.ads.AdFormatType.INTERSTITIAL]?.unitOrPlacementId)
        assertEquals("Rewarded_Android", telemetry.formatMetrics[com.example.ads.AdFormatType.REWARDED]?.unitOrPlacementId)
        val unityAdapter = telemetry.adapterStatuses.first { it.key == "unity_ads" }
        assertTrue(unityAdapter.enabledInConfig)
        assertTrue(unityAdapter.appKeyConfigured)
    }

    @Test
    fun farmerCropRequiresAdminApprovalAndAdminCanEditOrDelete() = runBlocking {
        MasterCatalogSeeder.initialize(db)
        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val categories = repo.categories.first()
        val firstCat = categories[0]
        val secondCat = categories[1]

        val farmer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "অনুমোদন পরীক্ষক কৃষক",
                phone = "01733330001",
                rawPassword = "pass1234",
                role = UserRole.FARMER,
                division = "খুলনা",
                district = "কুষ্টিয়া",
                upazila = "কুমারখালী",
                unionName = "",
                village = "",
                nidLastFour = "4444",
                farmBio = "ধান"
            ).getOrThrow()
        )
        val buyer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "অনুমোদন পরীক্ষক ক্রেতা",
                phone = "01733330002",
                rawPassword = "pass1234",
                role = UserRole.BUYER,
                division = "ঢাকা",
                district = "ঢাকা",
                upazila = "সাভার",
                unionName = "",
                village = "",
                nidLastFour = "",
                farmBio = ""
            ).getOrThrow()
        )

        // Farmer creates product -> starts PENDING_APPROVAL and notifies Admin
        val created = repo.createFarmerProduct(
            farmer = farmer,
            category = firstCat,
            nameBn = "কাটারিভোগ ধান",
            descriptionBn = "নতুন ফসল",
            imageUri = "",
            quantity = 12.0,
            unit = ProductUnit.MON.labelBn,
            pricePerUnit = 1500.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "কুমারখালী",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0
        ).getOrThrow()
        assertEquals("PENDING_APPROVAL", created.status)
        assertTrue(repo.notifications.first().any { it.targetRole == UserRole.ADMIN.name && it.type == "PRODUCT_APPROVAL_PENDING" })

        // Hidden from public/buyer feed & cannot be added to cart before admin approves
        val unapprovedUiState = com.example.ui.viewmodel.MarketplaceUiState(
            isLoading = false,
            products = listOf(created)
        )
        assertEquals(0, unapprovedUiState.filteredProducts.size)
        assertTrue(repo.addToCart(created, 1.0, buyer).isFailure)

        // Farmer updating stock/price cannot bypass PENDING_APPROVAL
        repo.updateFarmerPriceAndStock(
            productId = created.id,
            farmerId = farmer.id,
            newPrice = 1550.0,
            newStock = 15.0
        ).getOrThrow()
        assertEquals("PENDING_APPROVAL", db.productDao().getProductById(created.id)!!.status)

        // Non-admin cannot approve or edit
        assertTrue(repo.adminVerifyProduct(buyer, created.id, approved = true).isFailure)
        assertTrue(
            repo.adminUpdateProduct(
                admin = buyer,
                productId = created.id,
                nameBn = "হ্যাকড",
                category = secondCat,
                pricePerUnit = 999.0,
                availableQuantity = 5.0,
                descriptionBn = "",
                status = "ACTIVE"
            ).isFailure
        )

        // Admin approves -> becomes ACTIVE, visible in feed, buyer can add to cart
        val approved = repo.adminVerifyProduct(admin, created.id, approved = true).getOrThrow()
        assertEquals("ACTIVE", approved.status)
        val approvedUiState = com.example.ui.viewmodel.MarketplaceUiState(
            isLoading = false,
            products = listOf(approved)
        )
        assertEquals(1, approvedUiState.filteredProducts.size)
        assertTrue(repo.addToCart(approved, 2.0, buyer).isSuccess)

        // Admin edits product details
        val edited = repo.adminUpdateProduct(
            admin = admin,
            productId = created.id,
            nameBn = "উন্নত কাটারিভোগ চাল",
            category = secondCat,
            pricePerUnit = 2100.0,
            availableQuantity = 20.0,
            descriptionBn = "যাচাইকৃত প্রিমিয়াম মান",
            status = "ACTIVE"
        ).getOrThrow()
        assertEquals("উন্নত কাটারিভোগ চাল", edited.nameBn)
        assertEquals(secondCat.id, edited.categoryId)
        assertEquals(2100.0, edited.pricePerUnit, 0.01)
        assertEquals(20.0, edited.availableQuantity, 0.01)

        // Admin deletes product -> removed from products DB and buyer cart
        assertTrue(repo.deleteProduct(admin, created.id).isSuccess)
        assertNull(db.productDao().getProductById(created.id))
        assertTrue(db.cartDao().observeCartItems().first().none { it.productId == created.id })
    }

    @Test
    fun adMonetizationEnforcesBlockedRoutesSanitizesIosUnitsAndTracksClickRetryTelemetry() {
        // Blocked routes (Sell, Checkout/Cart, Order payment, Login, Register, Activation, Splash)
        listOf("sell", "cart", "activation", "login", "register", "register?role=BUYER", "order/o1", "splash").forEach { route ->
            assertTrue("Expected blocked route: $route", com.example.ads.AdMonetizationManager.isAdBlockedRoute(route))
        }
        // Allowed mobile ad routes (including Home, Crops, Farmers, Orders, Profile, Details, Notifications, Info, Admin)
        listOf("home", "browse?farmers=false", "farmers", "orders", "profile", "product/p1", "farmer/f1", "notifications", "info/about", "admin").forEach { route ->
            assertFalse("Expected allowed route: $route", com.example.ads.AdMonetizationManager.isAdBlockedRoute(route))
        }

        // Reject any accidental iOS placement IDs and keep strict Android defaults
        val dirtyConfig = com.example.data.model.AdConfig(
            androidBannerId = "Banner_iOS",
            androidInterstitialId = "Interstitial_iOS",
            androidRewardedId = "Rewarded_iOS",
            chartboostEnabled = true,
            chartboostAppId = "cb_android_01",
            startIoEnabled = true,
            startIoAppId = "20261117",
            consentGranted = false
        ).hydratedWithDefaults()

        assertEquals(com.example.data.model.AdConfig.DEFAULT_UNITY_BANNER_ID, dirtyConfig.resolvedBannerUnitId())
        assertEquals(com.example.data.model.AdConfig.DEFAULT_UNITY_INTERSTITIAL_ID, dirtyConfig.resolvedInterstitialUnitId())
        assertEquals(com.example.data.model.AdConfig.DEFAULT_UNITY_REWARDED_ID, dirtyConfig.resolvedRewardedUnitId())

        com.example.ads.AdMonetizationManager.resetForTest()
        com.example.ads.AdMonetizationManager.syncConfig(context, dirtyConfig)
        com.example.ads.AdMonetizationManager.trackFormatRequested(com.example.ads.AdFormatType.BANNER, "Banner_Android")
        com.example.ads.AdMonetizationManager.trackFormatLoaded(com.example.ads.AdFormatType.BANNER, "Unity Ads")
        com.example.ads.AdMonetizationManager.trackFormatDisplayed(com.example.ads.AdFormatType.BANNER, "Unity Ads")
        com.example.ads.AdMonetizationManager.trackFormatClicked(com.example.ads.AdFormatType.BANNER, "Unity Ads")

        val snap = com.example.ads.AdMonetizationManager.telemetry.value
        val bannerMetrics = snap.formatMetrics.getValue(com.example.ads.AdFormatType.BANNER)
        assertEquals(1, bannerMetrics.requestCount)
        assertEquals(1, bannerMetrics.loadedCount)
        assertEquals(1, bannerMetrics.displayedCount)
        assertEquals(1, bannerMetrics.clickedCount)
        assertEquals(100.0, bannerMetrics.fillRatePercent, 0.01)
        assertEquals(100.0, bannerMetrics.ctrPercent, 0.01)
        assertFalse(snap.consentGranted)
        assertTrue(snap.debugLogs.any { it.code == com.example.ads.AdDebugEventCode.AD_CLICKED })
        assertTrue(snap.adapterStatuses.any { it.key == "chartboost" && it.enabledInConfig && it.appKeyConfigured })
        assertTrue(snap.adapterStatuses.any { it.key == "startio" && it.enabledInConfig && it.appKeyConfigured })

        // Exponential retry cap (max 3 attempts: 5s, 15s, 30s, then stop)
        assertEquals(5_000L, com.example.ads.AdMonetizationManager.nextRetryDelayMs(0))
        assertEquals(15_000L, com.example.ads.AdMonetizationManager.nextRetryDelayMs(1))
        assertEquals(30_000L, com.example.ads.AdMonetizationManager.nextRetryDelayMs(2))
        assertNull(com.example.ads.AdMonetizationManager.nextRetryDelayMs(3))
    }

    @Test
    fun productionReadinessReportsLiveTestConsentZeroAdMobAndBlockedRoutes() {
        val testModeConfig = com.example.data.model.AdConfig(
            adTestMode = true,
            consentGranted = false,
            startIoEnabled = true,
            startIoAppId = "startio_test_app"
        ).hydratedWithDefaults()
        val testReport = com.example.ads.AdMonetizationManager.buildProductionReadiness(testModeConfig)
        assertFalse(testReport.isProductionReady)
        assertFalse(testReport.checks.first { it.key == "test_mode" }.passed)
        assertFalse(testReport.checks.first { it.key == "consent" }.passed)
        assertTrue(testReport.checks.first { it.key == "zero_admob" }.passed)
        assertTrue(testReport.checks.first { it.key == "blocked_routes" }.passed)
        assertTrue(testReport.checks.first { it.key == "release_r8" }.passed)
        assertTrue(testReport.checks.first { it.key == "app_ads_txt" }.passed)

        val liveReadyConfig = testModeConfig.copy(adTestMode = false, consentGranted = true)
        com.example.ads.AdMonetizationManager.syncConfig(context, liveReadyConfig)
        val liveReport = com.example.ads.AdMonetizationManager.telemetry.value.productionReadiness
        assertTrue(liveReport.isProductionReady)
        assertEquals(liveReport.totalCount, liveReport.passedCount)
        assertTrue(liveReport.checks.all { it.passed })
        assertTrue(liveReport.startIoKeySource.contains("Admin Panel"))
    }

    @Test
    fun persistentAdTelemetrySurvivesReloadAndRollsOverDailyAndMonthly() {
        val day1Ms = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day2Ms = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val nextMonthMs = Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var currentClockMs = day1Ms
        com.example.ads.AdMonetizationManager.clockMillisProvider = { currentClockMs }

        com.example.ads.AdMonetizationManager.initTelemetryStore(context, forceReload = true)
        com.example.ads.AdMonetizationManager.recordRealImpressionRevenue(0.0125, "Unity Ads", "banner")
        com.example.ads.AdMonetizationManager.recordRealImpressionRevenue(0.0075, "Start.io", "banner")
        com.example.ads.AdMonetizationManager.trackFormatClicked(com.example.ads.AdFormatType.BANNER, "Unity Ads")

        // Simulate fresh app process restart on same day without wiping SharedPreferences
        com.example.ads.AdMonetizationManager.resetForTest(clearContextStore = null, resetClock = false)
        com.example.ads.AdMonetizationManager.initTelemetryStore(context, forceReload = true)
        val reloadedSameDay = com.example.ads.AdMonetizationManager.telemetry.value
        assertEquals(0.02, reloadedSameDay.dailyRevenueUsd, 0.0001)
        assertEquals(0.02, reloadedSameDay.monthlyRevenueUsd, 0.0001)
        assertEquals(2, reloadedSameDay.impressionCount)
        assertEquals(1, reloadedSameDay.totalClicks)
        assertEquals(0.0125, reloadedSameDay.networkRevenueUsd["Unity Ads"] ?: 0.0, 0.0001)
        assertEquals(0.0075, reloadedSameDay.networkRevenueUsd["Start.io"] ?: 0.0, 0.0001)

        // Next day in same month -> dailyRevenueUsd resets, monthly totals stay intact
        currentClockMs = day2Ms
        com.example.ads.AdMonetizationManager.rolloverTelemetryDateIfNeeded()
        val nextDaySnap = com.example.ads.AdMonetizationManager.telemetry.value
        assertEquals(0.0, nextDaySnap.dailyRevenueUsd, 0.0001)
        assertEquals(0.02, nextDaySnap.monthlyRevenueUsd, 0.0001)
        assertEquals(2, nextDaySnap.impressionCount)
        assertEquals(1, nextDaySnap.totalClicks)

        // New month -> monthly totals & network revenue reset cleanly
        currentClockMs = nextMonthMs
        com.example.ads.AdMonetizationManager.rolloverTelemetryDateIfNeeded()
        val nextMonthSnap = com.example.ads.AdMonetizationManager.telemetry.value
        assertEquals(0.0, nextMonthSnap.dailyRevenueUsd, 0.0001)
        assertEquals(0.0, nextMonthSnap.monthlyRevenueUsd, 0.0001)
        assertEquals(0, nextMonthSnap.impressionCount)
        assertEquals(0, nextMonthSnap.totalClicks)
        assertTrue(nextMonthSnap.networkRevenueUsd.isEmpty())
    }

    @Test
    fun adminDailyTipsCrudSupportsAddEditToggleDeleteAndDoesNotResurrectEmptyList() = runBlocking {
        repo.initializeCatalogsAndSync()
        assertEquals(com.example.data.model.DefaultDailyTips.initialTips.size, repo.dailyTips.value.size)

        val admin = repo.loginUser("admin", "bondhu").getOrThrow()
        val buyer = activateAsAdmin(
            admin,
            repo.registerUser(
                fullName = "টিপস পরীক্ষক ক্রেতা",
                phone = "01744440001",
                rawPassword = "pass1234",
                role = UserRole.BUYER,
                division = "ঢাকা",
                district = "ঢাকা",
                upazila = "সাভার",
                unionName = "",
                village = "",
                nidLastFour = "",
                farmBio = ""
            ).getOrThrow()
        )

        // Non-admin cannot add or delete daily tips
        assertTrue(
            repo.adminSaveDailyTip(
                buyer,
                com.example.data.model.DailyTip(
                    titleBn = "অবৈধ টিপস",
                    contentBn = "ক্রেতা টিপস যোগ করতে পারবে না"
                )
            ).isFailure
        )
        assertTrue(repo.adminDeleteDailyTip(buyer, repo.dailyTips.value.first().id).isFailure)

        // Admin creates a new daily tip
        val created = repo.adminSaveDailyTip(
            admin,
            com.example.data.model.DailyTip(
                titleBn = "শীতকালীন সবজি প্যাকেজিং",
                contentBn = "ছিদ্রযুক্ত প্লাস্টিক ক্রেটে টমেটো ও বেগুন পরিবহন করলে ক্ষতি ২৫% কমে।",
                categoryBn = "প্যাকেজিং",
                emoji = "🍅",
                isActive = true
            )
        ).getOrThrow()
        assertEquals(4, repo.dailyTips.value.size)
        assertTrue(repo.dailyTips.value.any { it.id == created.id && it.isActive })

        // Admin edits & pauses the tip
        val edited = repo.adminSaveDailyTip(
            admin,
            created.copy(
                titleBn = "টমেটো প্যাকেজিং আপডেট",
                isActive = false
            )
        ).getOrThrow()
        assertEquals("টমেটো প্যাকেজিং আপডেট", edited.titleBn)
        assertFalse(repo.dailyTips.value.first { it.id == created.id }.isActive)

        // Admin deletes all tips one by one -> persists empty list across re-initialization
        val allIds = repo.dailyTips.value.map { it.id }
        allIds.forEach { tipId ->
            assertTrue(repo.adminDeleteDailyTip(admin, tipId).isSuccess)
        }
        assertTrue(repo.dailyTips.value.isEmpty())

        val freshRepo = MarketplaceRepository(
            db = db,
            prefs = UserPreferences(context),
            rtdbSync = FirebaseRealtimeSyncManager(enableRemoteSync = false)
        )
        freshRepo.initializeCatalogsAndSync()
        assertTrue(freshRepo.dailyTips.value.isEmpty())
    }

    @Test
    fun appOpenAdDefaultsUnitFallbackAndRouteBlockingWorkTogether() {
        val defaultCfg = com.example.data.model.AdConfig().hydratedWithDefaults()
        assertTrue(defaultCfg.appOpenEnabled)
        assertEquals(
            com.example.data.model.AdConfig.DEFAULT_UNITY_INTERSTITIAL_ID,
            com.example.ads.AdMonetizationManager.resolveEffectiveAppOpenUnitId(defaultCfg)
        )

        val customAppOpenCfg = defaultCfg.copy(androidAppOpenId = "AppOpen_Android")
        assertEquals(
            "AppOpen_Android",
            com.example.ads.AdMonetizationManager.resolveEffectiveAppOpenUnitId(customAppOpenCfg)
        )

        val dirtyIosAppOpenCfg = defaultCfg.copy(androidAppOpenId = "AppOpen_iOS")
        assertEquals(
            com.example.data.model.AdConfig.DEFAULT_UNITY_INTERSTITIAL_ID,
            com.example.ads.AdMonetizationManager.resolveEffectiveAppOpenUnitId(dirtyIosAppOpenCfg)
        )

        com.example.ads.AdMonetizationManager.syncConfig(context, defaultCfg)
        val appOpenMetric = com.example.ads.AdMonetizationManager.telemetry.value.formatMetrics[com.example.ads.AdFormatType.APP_OPEN]
        assertNotNull(appOpenMetric)
        assertTrue(appOpenMetric!!.enabled)
        assertEquals(com.example.data.model.AdConfig.DEFAULT_UNITY_INTERSTITIAL_ID, appOpenMetric.unitOrPlacementId)

        // Check blocked vs allowed currentRoute tracking
        listOf("splash", "cart", "sell", "sell?productId=p1", "order/ord_1", "activation", "login", "register", "register?role=FARMER")
            .forEach { route ->
                com.example.ads.AdMonetizationManager.setCurrentRoute(route)
                assertTrue("Route $route should be ad-blocked", com.example.ads.AdMonetizationManager.isCurrentRouteAdBlocked())
            }
        listOf("home", "browse", "farmers", "orders", "profile", "farmer/f1", "product/p1", "admin")
            .forEach { route ->
                com.example.ads.AdMonetizationManager.setCurrentRoute(route)
                assertFalse("Route $route should allow ads", com.example.ads.AdMonetizationManager.isCurrentRouteAdBlocked())
            }
    }
}
