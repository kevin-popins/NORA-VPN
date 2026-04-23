package com.privatevpn.app.profiles.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_sources")
data class SubscriptionSourceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val sourceUrl: String,
    val sourceType: String,
    val enabled: Boolean,
    val autoUpdateEnabled: Boolean,
    val updateIntervalMinutes: Int,
    val lastUpdatedAtMs: Long?,
    val lastSuccessAtMs: Long?,
    val lastError: String?,
    val isCollapsed: Boolean,
    val profileCount: Int,
    val etag: String?,
    val lastModified: String?,
    val metadata: String?,
    val childProfileIdsCsv: String,
    val lastSelectedProfileId: String?,
    val syncStatus: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)
