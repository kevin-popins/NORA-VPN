package com.privatevpn.app.profiles.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionSourceDao {
    @Query("SELECT * FROM subscription_sources ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<SubscriptionSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubscriptionSourceEntity)

    @Query("SELECT * FROM subscription_sources WHERE id = :subscriptionId LIMIT 1")
    suspend fun findById(subscriptionId: String): SubscriptionSourceEntity?

    @Query("SELECT * FROM subscription_sources WHERE enabled = 1")
    suspend fun findEnabled(): List<SubscriptionSourceEntity>

    @Query("DELETE FROM subscription_sources WHERE id = :subscriptionId")
    suspend fun deleteById(subscriptionId: String)
}
