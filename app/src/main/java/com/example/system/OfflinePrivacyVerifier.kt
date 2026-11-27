package com.example.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class OfflinePrivacyReport(
    val hasInternetPermission: Boolean,
    val broadStoragePermissionsFound: List<String>,
    val declaredPermissions: List<String>
) {
    val isFullyOffline: Boolean get() = !hasInternetPermission
    val usesSafOnly: Boolean get() = broadStoragePermissionsFound.isEmpty()
}

object OfflinePrivacyVerifier {

    private val prohibitedBroadStoragePermissions = setOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO"
    )

    fun inspect(context: Context): OfflinePrivacyReport {
        val requested = runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS
                )
            }
            info.requestedPermissions?.toList().orEmpty()
        }.getOrDefault(emptyList())

        val hasInternet = requested.contains(Manifest.permission.INTERNET)
        val broadStorage = requested.filter { it in prohibitedBroadStoragePermissions }

        return OfflinePrivacyReport(
            hasInternetPermission = hasInternet,
            broadStoragePermissionsFound = broadStorage,
            declaredPermissions = requested
        )
    }
}
