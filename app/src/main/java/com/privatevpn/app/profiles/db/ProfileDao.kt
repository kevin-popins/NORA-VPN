package com.privatevpn.app.profiles.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query(
        "SELECT * FROM profiles " +
            "ORDER BY " +
            "CASE WHEN parentSubscriptionId IS NULL THEN 0 ELSE 1 END ASC, " +
            "importedAtMs DESC, sourceOrder ASC"
    )
    fun observeAll(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteById(profileId: String)

    @Query("DELETE FROM profiles WHERE parentSubscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: String)

    @Query("UPDATE profiles SET displayName = :displayName WHERE id = :profileId")
    suspend fun renameById(profileId: String, displayName: String)

    @Query("SELECT * FROM profiles WHERE id = :profileId LIMIT 1")
    suspend fun findById(profileId: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE parentSubscriptionId = :subscriptionId ORDER BY sourceOrder ASC, importedAtMs DESC")
    suspend fun findBySubscriptionId(subscriptionId: String): List<ProfileEntity>
}
