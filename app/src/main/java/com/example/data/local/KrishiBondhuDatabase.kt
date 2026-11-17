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
        PaymentSettingsEntity::class
    ],
    version = 4,
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

        fun getInstance(context: Context): KrishiBondhuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KrishiBondhuDatabase::class.java,
                    "krishi_bondhu_local_dev.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
