package com.privatevpn.app.private_session

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class AndroidInstalledAppsRepository(
    private val appContext: Context
) : InstalledAppsRepository {

    override suspend fun listInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val packageManager = appContext.packageManager
        val resolveInfos = buildLauncherIntents()
            .asSequence()
            .flatMap { intent ->
                queryLauncherActivities(packageManager, intent).asSequence()
            }
            .toList()

        val launcherApps = resolveInfos
            .asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == appContext.packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim()
                    .takeUnless { it.isNullOrBlank() }
                    ?: packageName

                InstalledAppInfo(
                    packageName = packageName,
                    appName = label
                )
            }
            .toList()

        val launcherServiceApps = if (launcherApps.isEmpty()) {
            queryLauncherAppsService(packageManager)
        } else {
            emptyList()
        }

        val fallbackApps = if (launcherApps.isEmpty() && launcherServiceApps.isEmpty()) {
            queryInstalledLaunchableApps(packageManager)
        } else {
            emptyList()
        }

        (launcherApps + launcherServiceApps + fallbackApps)
            .asSequence()
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<InstalledAppInfo> {
                    it.appName.lowercase(Locale.getDefault())
                }.thenBy { it.packageName }
            )
            .toList()
    }

    private fun buildLauncherIntents(): List<Intent> = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    )

    private fun queryLauncherActivities(
        packageManager: PackageManager,
        launcherIntent: Intent
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
    }

    private fun queryLauncherAppsService(
        packageManager: PackageManager
    ): List<InstalledAppInfo> {
        val launcherApps = appContext.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val launcherActivities = runCatching {
            launcherApps.getActivityList(null, Process.myUserHandle())
        }.getOrElse {
            emptyList()
        }

        return launcherActivities
            .asSequence()
            .mapNotNull { launcherActivityInfo ->
                val appInfo = launcherActivityInfo.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName
                if (packageName == appContext.packageName) return@mapNotNull null

                val label = runCatching {
                    launcherActivityInfo.label?.toString()?.trim()
                }.getOrNull().takeUnless { it.isNullOrBlank() }
                    ?: runCatching { packageManager.getApplicationLabel(appInfo)?.toString()?.trim() }
                        .getOrNull()
                        .takeUnless { it.isNullOrBlank() }
                    ?: packageName

                InstalledAppInfo(
                    packageName = packageName,
                    appName = label
                )
            }
            .toList()
    }

    private fun queryInstalledLaunchableApps(
        packageManager: PackageManager
    ): List<InstalledAppInfo> {
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PackageManager.MATCH_ALL)
        }

        return installedPackages
            .asSequence()
            .mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName
                if (packageName == appContext.packageName) return@mapNotNull null
                if (packageManager.getLaunchIntentForPackage(packageName) == null) return@mapNotNull null

                val label = runCatching {
                    packageManager.getApplicationLabel(appInfo)?.toString()?.trim()
                }.getOrNull().takeUnless { it.isNullOrBlank() } ?: packageName

                InstalledAppInfo(
                    packageName = packageName,
                    appName = label
                )
            }
            .toList()
    }
}
