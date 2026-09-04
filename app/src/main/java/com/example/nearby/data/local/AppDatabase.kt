package com.example.nearby.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.nearby.data.local.dao.ActivityDao
import com.example.nearby.data.local.dao.BackupMetadataDao
import com.example.nearby.data.local.dao.BlockedUserDao
import com.example.nearby.data.local.dao.ConnectionDao
import com.example.nearby.data.local.dao.ConversationDao
import com.example.nearby.data.local.dao.MessageDao
import com.example.nearby.data.local.dao.ReportDao
import com.example.nearby.data.local.dao.SettingsDao
import com.example.nearby.data.local.dao.UserDao
import com.example.nearby.data.local.entity.ActivityEntity
import com.example.nearby.data.local.entity.BackupMetadataEntity
import com.example.nearby.data.local.entity.BlockedUserEntity
import com.example.nearby.data.local.entity.ConnectionEntity
import com.example.nearby.data.local.entity.ConversationEntity
import com.example.nearby.data.local.entity.MessageEntity
import com.example.nearby.data.local.entity.ReportEntity
import com.example.nearby.data.local.entity.SettingsEntity
import com.example.nearby.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ConnectionEntity::class,
        ActivityEntity::class,
        BlockedUserEntity::class,
        SettingsEntity::class,
        BackupMetadataEntity::class,
        ReportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun connectionDao(): ConnectionDao
    abstract fun activityDao(): ActivityDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun settingsDao(): SettingsDao
    abstract fun backupMetadataDao(): BackupMetadataDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexvora_nearby_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
