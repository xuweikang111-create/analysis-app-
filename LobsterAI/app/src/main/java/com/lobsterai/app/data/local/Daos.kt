package com.lobsterai.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LobsterDao {
    @Query("SELECT * FROM lobsters ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<LobsterEntity>>

    @Query("SELECT * FROM lobsters WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<LobsterEntity?>

    @Query("SELECT * FROM lobsters WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LobsterEntity?

    @Insert
    suspend fun insert(entity: LobsterEntity): Long

    @Update
    suspend fun update(entity: LobsterEntity)

    @Delete
    suspend fun delete(entity: LobsterEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE lobsterId = :lobsterId ORDER BY updatedAt DESC")
    fun observeByLobster(lobsterId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE lobsterId = :lobsterId AND title LIKE '%' || :query || '%' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun searchByLobster(lobsterId: Long, query: String, limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :time WHERE id = :id")
    suspend fun rename(id: Long, title: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(entity: ConversationEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessageEntity?

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    @Update
    suspend fun update(entity: MessageEntity)

    @Delete
    suspend fun delete(entity: MessageEntity)

    @Query("DELETE FROM messages WHERE id >= :messageId AND conversationId = :conversationId")
    suspend fun deleteFrom(conversationId: Long, messageId: Long)
}

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_configs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM model_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ModelConfigEntity?

    @Insert
    suspend fun insert(entity: ModelConfigEntity): Long

    @Update
    suspend fun update(entity: ModelConfigEntity)

    @Delete
    suspend fun delete(entity: ModelConfigEntity)
}

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<KnowledgeItemEntity>>

    @Query("SELECT * FROM knowledge_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KnowledgeItemEntity?

    @Query("SELECT * FROM knowledge_items ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<KnowledgeItemEntity>

    @Insert
    suspend fun insert(entity: KnowledgeItemEntity): Long

    @Update
    suspend fun update(entity: KnowledgeItemEntity)

    @Delete
    suspend fun delete(entity: KnowledgeItemEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryEntity): Long

    @Query("UPDATE memories SET lastAccessedAt = :time WHERE id IN (:ids)")
    suspend fun touch(ids: List<Long>, time: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(entity: MemoryEntity)

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}

@Dao
interface DailyActionDao {
    @Query("SELECT * FROM daily_actions WHERE lobsterId = :lobsterId AND dayKey = :dayKey")
    fun observeDay(lobsterId: Long, dayKey: String): Flow<List<DailyActionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DailyActionEntity): Long
}
