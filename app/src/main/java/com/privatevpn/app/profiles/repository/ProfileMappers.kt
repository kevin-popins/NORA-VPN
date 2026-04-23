package com.privatevpn.app.profiles.repository

import com.privatevpn.app.profiles.db.ProfileEntity
import com.privatevpn.app.profiles.db.SubscriptionSourceEntity
import com.privatevpn.app.profiles.model.ProfileType
import com.privatevpn.app.profiles.model.SubscriptionSource
import com.privatevpn.app.profiles.model.SubscriptionSourceType
import com.privatevpn.app.profiles.model.SubscriptionSyncStatus
import com.privatevpn.app.profiles.model.VpnProfile

internal fun VpnProfile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    displayName = displayName,
    type = type.name,
    sourceRaw = sourceRaw,
    normalizedJson = normalizedJson,
    dnsServersCsv = dnsServers.joinToString(","),
    dnsFallbackApplied = dnsFallbackApplied,
    partialImport = isPartialImport,
    warningsCsv = importWarnings.joinToString("\n"),
    importedAtMs = importedAtMs,
    parentSubscriptionId = parentSubscriptionId,
    sourceOrder = sourceOrder
)

internal fun ProfileEntity.toDomain(): VpnProfile = VpnProfile(
    id = id,
    displayName = displayName,
    type = ProfileType.entries.firstOrNull { it.name == type } ?: ProfileType.XRAY_JSON,
    sourceRaw = sourceRaw,
    normalizedJson = normalizedJson,
    dnsServers = dnsServersCsv
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() },
    dnsFallbackApplied = dnsFallbackApplied,
    isPartialImport = partialImport,
    importWarnings = warningsCsv
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotBlank() },
    importedAtMs = importedAtMs,
    parentSubscriptionId = parentSubscriptionId?.trim()?.takeIf { it.isNotBlank() },
    sourceOrder = sourceOrder
)

private const val CHILD_IDS_SEPARATOR = ";"

internal fun SubscriptionSource.toEntity(): SubscriptionSourceEntity = SubscriptionSourceEntity(
    id = id,
    displayName = displayName,
    sourceUrl = sourceUrl,
    sourceType = sourceType.name,
    enabled = enabled,
    autoUpdateEnabled = autoUpdateEnabled,
    updateIntervalMinutes = updateIntervalMinutes,
    lastUpdatedAtMs = lastUpdatedAtMs,
    lastSuccessAtMs = lastSuccessAtMs,
    lastError = lastError,
    isCollapsed = isCollapsed,
    profileCount = profileCount,
    etag = etag,
    lastModified = lastModified,
    metadata = metadata,
    childProfileIdsCsv = childProfileIds.joinToString(CHILD_IDS_SEPARATOR),
    lastSelectedProfileId = lastSelectedProfileId,
    syncStatus = syncStatus.name,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)

internal fun SubscriptionSourceEntity.toDomain(): SubscriptionSource = SubscriptionSource(
    id = id,
    displayName = displayName,
    sourceUrl = sourceUrl,
    sourceType = SubscriptionSourceType.entries.firstOrNull { it.name == sourceType }
        ?: SubscriptionSourceType.HTTP_TEXT,
    enabled = enabled,
    autoUpdateEnabled = autoUpdateEnabled,
    updateIntervalMinutes = updateIntervalMinutes,
    lastUpdatedAtMs = lastUpdatedAtMs,
    lastSuccessAtMs = lastSuccessAtMs,
    lastError = lastError,
    isCollapsed = isCollapsed,
    profileCount = profileCount,
    etag = etag,
    lastModified = lastModified,
    metadata = metadata,
    childProfileIds = childProfileIdsCsv
        .split(CHILD_IDS_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotBlank() },
    lastSelectedProfileId = lastSelectedProfileId?.trim()?.takeIf { it.isNotBlank() },
    syncStatus = SubscriptionSyncStatus.entries.firstOrNull { it.name == syncStatus }
        ?: SubscriptionSyncStatus.EMPTY,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)
