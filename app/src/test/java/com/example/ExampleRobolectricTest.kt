package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KrishiBondhuDatabase
import com.example.data.local.MasterCatalogSeeder
import com.example.data.local.UserPreferences
import com.example.data.model.BangladeshLocations
import com.example.data.model.DeliveryOption
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
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
            rtdbSync = FirebaseRealtimeSyncManager()
        )
    }

    @After
    fun teardown() {
        db.close()
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
        val categories = repo.categories.first()
        val riceCat = categories.first()

        val farmer1 = repo.registerUser(
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

        val farmer2 = repo.registerUser(
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

        val buyer = repo.registerUser(
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

        val prod1 = repo.createFarmerProduct(
            farmer = farmer1,
            category = riceCat,
            nameBn = "মিনিকেট চাল",
            descriptionBn = "তাজা মিনিকেট চাল",
            imageUri = "",
            quantity = 40.0,
            unit = "কেজি",
            pricePerUnit = 75.0,
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
            unit = "কেজি",
            pricePerUnit = 60.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "বগুড়া সদর",
            supportsFarmPickup = true,
            supportsDirectDelivery = true,
            deliveryCharge = 40.0,
            isWholesale = false
        ).getOrThrow()

        assertTrue(repo.addToCart(prod1, 2.0).isSuccess)
        assertTrue(repo.addToCart(prod2, 5.0).isSuccess)

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
        val cat = repo.categories.first().first()

        val farmer = repo.registerUser(
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

        val buyer = repo.registerUser(
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

        val prod = repo.createFarmerProduct(
            farmer = farmer,
            category = cat,
            nameBn = "হাওরের চাল",
            descriptionBn = "তাজা চাল",
            imageUri = "",
            quantity = 20.0,
            unit = "কেজি",
            pricePerUnit = 70.0,
            harvestDate = "আজ",
            availabilityDate = "আজ",
            pickupAddress = "সিলেট সদর",
            supportsFarmPickup = true,
            supportsDirectDelivery = false,
            deliveryCharge = 0.0,
            isWholesale = false
        ).getOrThrow()

        repo.addToCart(prod, 2.0)
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
        val cat = repo.categories.first().first()

        val ownerFarmer = repo.registerUser(
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

        val otherFarmer = repo.registerUser(
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

        val prod = repo.createFarmerProduct(
            farmer = ownerFarmer,
            category = cat,
            nameBn = "দেশি আলু",
            descriptionBn = "টাটকা আলু",
            imageUri = "",
            quantity = 50.0,
            unit = "কেজি",
            pricePerUnit = 35.0,
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
            newPrice = 40.0,
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
        assertTrue(repo.adConfig.value.adsenseEnabled)
        assertTrue(repo.adConfig.value.adsterraEnabled)
        assertTrue(repo.adConfig.value.monetagEnabled)

        val logs = repo.auditLogs.first()
        assertTrue(logs.any { it.actionBn == "বিজ্ঞাপন ও মনিটাইজেশন আপডেট" })
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
}
