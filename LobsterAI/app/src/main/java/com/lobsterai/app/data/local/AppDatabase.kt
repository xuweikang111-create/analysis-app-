package com.lobsterai.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LobsterEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ModelConfigEntity::class,
        KnowledgeItemEntity::class,
        DailyActionEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lobsterDao(): LobsterDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun dailyActionDao(): DailyActionDao
}
