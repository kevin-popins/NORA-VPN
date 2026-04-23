package com.privatevpn.app.profiles.subscriptions

import com.privatevpn.app.profiles.model.SubscriptionSyncStatus

data class SubscriptionRefreshResult(
    val subscriptionId: String,
    val status: SubscriptionSyncStatus,
    val importedProfilesCount: Int,
    val invalidEntriesCount: Int,
    val message: String? = null
)
