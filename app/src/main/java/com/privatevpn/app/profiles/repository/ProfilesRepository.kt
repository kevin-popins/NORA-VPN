package com.privatevpn.app.profiles.repository

import com.privatevpn.app.profiles.model.VpnProfile
import kotlinx.coroutines.flow.Flow

interface ProfilesRepository {
    val profiles: Flow<List<VpnProfile>>

    suspend fun addProfile(profile: VpnProfile)
    suspend fun deleteProfile(profileId: String)
    suspend fun renameProfile(profileId: String, displayName: String)
    suspend fun getProfile(profileId: String): VpnProfile?
}
