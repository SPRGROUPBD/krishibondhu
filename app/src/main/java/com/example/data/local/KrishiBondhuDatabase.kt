package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        LocationEntity::class,
        ProductEntity::class,
        CartItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        ReviewEntity::class,
        ComplaintEntity::class,
        NotificationEntity::class,
        AuditLogEntity::class,
        ActivationPaymentEntity::class,
        PaymentSettingsEntity::class,
        FarmerPaymentSettingsEntity::class,
        OrderPaymentEntity::class,
        HeroSliderEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class KrishiBondhuDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun locationDao(): LocationDao
    abstract fun productDao(): ProductDao
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao
    abstract fun reviewComplaintDao(): ReviewComplaintDao
    abstract fun notificationAuditDao(): NotificationAuditDao
    abstract fun activationPaymentDao(): ActivationPaymentDao
    abstract fun paymentSettingsDao(): PaymentSettingsDao
    abstract fun farmerPaymentSettingsDao(): FarmerPaymentSettingsDao
    abstract fun orderPaymentDao(): OrderPaymentDao
    abstract fun heroSliderDao(): HeroSliderDao

    companion object {
        @Volatile
        private var INSTANCE: KrishiBondhuDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE users ADD COLUMN profileImageUri TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN accountStatus TEXT NOT NULL DEFAULT 'PAYMENT_PENDING'")
                db.execSQL("ALTER TABLE users ADD COLUMN activationDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN expiryDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN validityYears INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE users ADD COLUMN lastPaymentId TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE orders ADD COLUMN paymentSenderNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN paymentReceiptUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN farmerPaymentConfirmed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN farmerPaymentConfirmedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN adminPaymentVerified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN adminPaymentVerifiedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN adminPaymentVerifiedAt INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activation_payments (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        userPhone TEXT NOT NULL,
                        accountRole TEXT NOT NULL,
                        paymentType TEXT NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        receiverNumber TEXT NOT NULL,
                        receiverAccountType TEXT NOT NULL,
                        senderMobile TEXT NOT NULL,
                        transactionId TEXT NOT NULL,
                        amount REAL NOT NULL,
                        receiptUri TEXT NOT NULL,
                        status TEXT NOT NULL,
                        adminNotes TEXT NOT NULL,
                        reviewedBy TEXT NOT NULL,
                        reviewedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activation_payments_userId ON activation_payments(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activation_payments_status ON activation_payments(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activation_payments_transactionId ON activation_payments(transactionId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_settings (
                        id TEXT NOT NULL PRIMARY KEY,
                        bKashNumber TEXT NOT NULL,
                        bKashAccountType TEXT NOT NULL,
                        nagadNumber TEXT NOT NULL,
                        nagadAccountType TEXT NOT NULL,
                        farmerFee REAL NOT NULL,
                        buyerFee REAL NOT NULL,
                        renewalFee REAL NOT NULL,
                        validityYears INTEGER NOT NULL,
                        paymentInstructions TEXT NOT NULL,
                        notify30Days INTEGER NOT NULL,
                        notify7Days INTEGER NOT NULL,
                        notifyOnExpiry INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN advanceAmountRequested REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE orders ADD COLUMN verifiedAdvanceTotal REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE orders ADD COLUMN remainingDue REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    "UPDATE orders SET verifiedAdvanceTotal = CASE WHEN farmerPaymentConfirmed = 1 THEN grandTotal ELSE 0.0 END, " +
                        "remainingDue = CASE WHEN farmerPaymentConfirmed = 1 THEN 0.0 ELSE grandTotal END"
                )
                db.execSQL("UPDATE products SET isWholesale = 1, unit = 'মণ'")
                db.execSQL("DELETE FROM products WHERE isDemoRecord = 1")
                db.execSQL("DELETE FROM orders WHERE isDemoRecord = 1")
                db.execSQL("DELETE FROM users WHERE id != 'admin_super' AND (isDemoRecord = 1 OR phone LIKE 'farmer_%')")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS farmer_payment_settings (
                        farmerId TEXT NOT NULL PRIMARY KEY,
                        bKashNumber TEXT NOT NULL,
                        nagadNumber TEXT NOT NULL,
                        rocketNumber TEXT NOT NULL,
                        bankAccountInfo TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS order_payments (
                        id TEXT NOT NULL PRIMARY KEY,
                        orderId TEXT NOT NULL,
                        orderCode TEXT NOT NULL,
                        buyerId TEXT NOT NULL,
                        buyerName TEXT NOT NULL,
                        farmerId TEXT NOT NULL,
                        farmerName TEXT NOT NULL,
                        amount REAL NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        receiverNumber TEXT NOT NULL,
                        senderNumber TEXT NOT NULL,
                        transactionId TEXT NOT NULL,
                        transactionIdNormalized TEXT NOT NULL,
                        referenceNote TEXT NOT NULL,
                        status TEXT NOT NULL,
                        rejectionReason TEXT NOT NULL,
                        verifiedBy TEXT NOT NULL,
                        verifiedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_payments_orderId ON order_payments(orderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_payments_farmerId ON order_payments(farmerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_payments_buyerId ON order_payments(buyerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_order_payments_transactionIdNormalized ON order_payments(transactionIdNormalized)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN adsDisabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payment_settings ADD COLUMN adRemovalFee REAL NOT NULL DEFAULT 200.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hero_sliders (
                        id TEXT NOT NULL PRIMARY KEY,
                        badgeBn TEXT NOT NULL,
                        titleBn TEXT NOT NULL,
                        subtitleBn TEXT NOT NULL,
                        bullet1Bn TEXT NOT NULL,
                        bullet2Bn TEXT NOT NULL,
                        bullet3Bn TEXT NOT NULL,
                        ctaTextBn TEXT NOT NULL,
                        targetRoute TEXT NOT NULL,
                        imageUri TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): KrishiBondhuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KrishiBondhuDatabase::class.java,
                    "krishi_bondhu_local_dev.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
