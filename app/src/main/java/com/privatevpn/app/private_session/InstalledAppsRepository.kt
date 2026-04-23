package com.privatevpn.app.private_session

interface InstalledAppsRepository {
    suspend fun listInstalledApps(): List<InstalledAppInfo>
}
