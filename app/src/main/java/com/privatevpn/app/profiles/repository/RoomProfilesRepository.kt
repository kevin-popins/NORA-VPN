package com.privatevpn.app.profiles.repository

import com.privatevpn.app.profiles.db.ProfileDao
import com.privatevpn.app.profiles.model.VpnProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProfilesRepository(
    private val profileDao: ProfileDao
) : ProfilesRepository {

    override val profiles: Flow<List<VpnProfile>> = profileDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun addProfile(profile: VpnProfile) {
        profileDao.insert(profile.toEntity())
    }

    override suspend fun deleteProfile(profileId: String) {
        profileDao.deleteById(profileId)
    }

    override suspend fun renameProfile(profileId: String, displayName: String) {
        profileDao.renameById(profileId, displayName)
    }

    override suspend fun getProfile(profileId: String): VpnProfile? {
        return profileDao.findById(profileId)?.toDomain()
    }
}
