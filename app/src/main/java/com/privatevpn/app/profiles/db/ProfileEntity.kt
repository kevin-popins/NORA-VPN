package com.privatevpn.app.profiles.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["parentSubscriptionId"])]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String,
    val sourceRaw: String,
    val normalizedJson: String?,
    val dnsServersCsv: String,
    val dnsFallbackApplied: Boolean,
    val partialImport: Boolean,
    val warningsCsv: String,
    val importedAtMs: Long,
    val parentSubscriptionId: String?,
    @ColumnInfo(defaultValue = "0")
    val sourceOrder: Int
)
