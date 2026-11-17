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
    }

    @After
    fun teardown() {
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

        assertTrue(repo.addToCart(prod1, 2.0, buyer).isSuccess)
        assertTrue(repo.addToCart(prod2, 5.0, buyer).isSuccess)

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

        repo.addToCart(prod, 2.0, buyer)
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
    fun admobResourcesAndManifestMetaDataAreConfigured() {
        val appId = context.getString(R.string.admob_app_id)
        val bannerId = context.getString(R.string.admob_banner_unit_id)
        val interstitialId = context.getString(R.string.admob_interstitial_unit_id)

        assertTrue(appId.startsWith("ca-app-pub-"))
        assertTrue(bannerId.startsWith("ca-app-pub-"))
        assertTrue(interstitialId.startsWith("ca-app-pub-"))

        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_META_DATA
        )
        val manifestAppId = appInfo.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
        assertEquals(appId, manifestAppId)
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
            androidBannerId = "ca-app-pub-1234567890123456/1111111111",
            mobileWebAdsEnabled = true,
            mobileAdNetworkPriority = "ADSTERRA",
            mobileCustomAdHtml = "<div id='unity-banner'>Ad</div>",
            mobileDirectLinkUrl = "https://example.com/smartlink",
            adsenseEnabled = true,
            adsenseClient = "ca-pub-1234567890123456",
            adsterraEnabled = true,
            monetagEnabled = true
        )

        val blocked = repo.adminSaveAdConfig(buyer, updatedConfig)
        assertTrue(blocked.isFailure)

        val allowed = repo.adminSaveAdConfig(admin, updatedConfig)
        assertTrue(allowed.isSuccess)
        assertEquals("ca-app-pub-1234567890123456/1111111111", repo.adConfig.value.androidBannerId)
        assertTrue(repo.adConfig.value.mobileWebAdsEnabled)
        assertEquals("ADSTERRA", repo.adConfig.value.mobileAdNetworkPriority)
        assertEquals("<div id='unity-banner'>Ad</div>", repo.adConfig.value.mobileCustomAdHtml)
        assertEquals("https://example.com/smartlink", repo.adConfig.value.mobileDirectLinkUrl)
        assertTrue(repo.adConfig.value.adsenseEnabled)
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
    fun fiveStepOrderSecurityRequiresBothFarmerConfirmAndAdminVerifyBeforeConfirmedOrCompleted() = runBlocking {
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

        // Step 1: Buyer places order
        repo.addToCart(prod, 10.0, buyer).getOrThrow()
        val order = repo.placeOrdersFromCart(
            buyer = buyer,
            deliveryOption = DeliveryOption.FARM_PICKUP,
            deliveryAddress = "",
            paymentMethod = PaymentMethod.CASH_ON_PICKUP,
            transactionRef = ""
        ).getOrThrow().first()
        assertEquals(OrderStatus.PENDING.name, order.status)

        // Cannot submit payment before farmer accepts
        assertTrue(
            repo.submitOrderPayment(
                orderId = order.id,
                buyer = buyer,
                paymentMethod = PaymentMethod.BKASH,
                senderNumber = "01777000045",
                transactionReference = "ORDTX1234",
                receiptUri = "data:image/jpeg;base64,/9j/4AAQ"
            ).isFailure
        )

        // Step 2: Farmer accepts order
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.ACCEPTED, farmer).isSuccess)

        // Step 3: Buyer submits payment TxID + receipt -> must NOT auto-confirm!
        val submitted = repo.submitOrderPayment(
            orderId = order.id,
            buyer = buyer,
            paymentMethod = PaymentMethod.BKASH,
            senderNumber = "01777000045",
            transactionReference = "ORDTX1234",
            receiptUri = "data:image/jpeg;base64,/9j/4AAQ"
        ).getOrThrow()
        assertEquals(OrderStatus.ACCEPTED.name, submitted.status)
        assertEquals("SUBMITTED", submitted.paymentStatus)
        assertFalse(submitted.farmerPaymentConfirmed)
        assertFalse(submitted.adminPaymentVerified)

        // Cannot transition to CONFIRMED or COMPLETED from buyer receipt alone
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.CONFIRMED, farmer).isFailure)
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.COMPLETED, buyer).isFailure)

        // Step 4a: Farmer confirms money received -> still awaits admin verify before CONFIRMED
        val afterFarmer = repo.farmerConfirmPaymentReceived(order.id, farmer).getOrThrow()
        assertTrue(afterFarmer.farmerPaymentConfirmed)
        assertNotEquals(OrderStatus.CONFIRMED.name, afterFarmer.status)
        assertTrue(repo.updateOrderStatus(order.id, OrderStatus.COMPLETED, buyer).isFailure)

        // Step 4b & Step 5: Admin verifies order payment -> dual-confirmed -> CONFIRMED & PAID -> COMPLETED succeeds
        val afterAdmin = repo.adminVerifyOrderPayment(order.id, admin, approved = true, notes = "TxID verified").getOrThrow()
        assertTrue(afterAdmin.adminPaymentVerified)
        assertEquals(OrderStatus.CONFIRMED.name, afterAdmin.status)
        assertEquals("PAID", afterAdmin.paymentStatus)

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
}
