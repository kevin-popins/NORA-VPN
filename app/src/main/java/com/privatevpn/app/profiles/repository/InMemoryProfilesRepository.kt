package com.privatevpn.app.profiles.repository

import com.privatevpn.app.profiles.model.VpnProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryProfilesRepository : ProfilesRepository {

    private val profilesFlow = MutableStateFlow<List<VpnProfile>>(emptyList())
    override val profiles: Flow<List<VpnProfile>> = profilesFlow

    override suspend fun addProfile(profile: VpnProfile) {
        profilesFlow.update { current -> listOf(profile) + current }
    }

    override suspend fun deleteProfile(profileId: String) {
        profilesFlow.update { current -> current.filterNot { it.id == profileId } }
    }

    override suspend fun renameProfile(profileId: String, displayName: String) {
        profilesFlow.update { current ->
            current.map { profile ->
                if (profile.id == profileId) profile.copy(displayName = displayName) else profile
            }
        }
    }

    override suspend fun getProfile(profileId: String): VpnProfile? {
        return profilesFlow.value.firstOrNull { it.id == profileId }
    }
}
