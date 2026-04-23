package com.privatevpn.app.profiles.model

data class SubscriptionSource(
    val id: String,
    val displayName: String,
    val sourceUrl: String,
    val sourceType: SubscriptionSourceType,
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
    val childProfileIds: List<String>,
    val lastSelectedProfileId: String?,
    val syncStatus: SubscriptionSyncStatus,
    val createdAtMs: Long,
    val updatedAtMs: Long
)
