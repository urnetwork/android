package com.bringyour.network.location

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings

// Thin Android-facing eligibility reads and Settings intent launchers for the
// mock location engine. No side effects beyond startActivity. See
// ~/urnetwork/android/MOCKLOCATION.md §4 (detection) and §5 (deep links).

fun isDeveloperOptionsEnabled(context: Context): Boolean {
    return Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        0,
    ) != 0
}

// True when this app is the one chosen under Developer options -> Select mock
// location app. The MOCK_LOCATION app op is the real gate for every
// test-provider call; the manifest permission is only the picker marker.
fun isSelectedMockLocationApp(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    } catch (e: Throwable) {
        false
    }
}

// The Location master switch gates delivery: with it off, every test-provider
// call succeeds but no app receives the fixes (MOCKLOCATION.md §6.6).
fun isLocationServicesEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

// Watches the MOCK_LOCATION app op for this package (fires when the user
// selects or deselects the app in Developer options). Watching your own uid
// needs no permission. Returns an unwatch lambda. Note the callback may
// arrive on an arbitrary binder thread — hop to your own thread before
// touching state.
fun startWatchingMockLocationOp(context: Context, onChanged: () -> Unit): () -> Unit {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return {}
    val listener = AppOpsManager.OnOpChangedListener { _, _ -> onChanged() }
    try {
        appOps.startWatchingMode(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            context.packageName,
            listener,
        )
    } catch (e: Throwable) {
        return {}
    }
    return {
        try {
            appOps.stopWatchingMode(listener)
        } catch (e: Throwable) {
            // already unwatched or the service is gone; nothing to do
        }
    }
}

// Opens Developer options. There is no public deep link to the "Select mock
// location app" picker itself; the fragment-args extras are the undocumented
// but harmless best-effort scroll-to-and-highlight of that row on stock
// Android (SettingsActivity.EXTRA_FRAGMENT_ARG_KEY / MOCK_LOCATION_APP_KEY).
fun openDeveloperOptions(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    intent.putExtra(":settings:fragment_args_key", "mock_location_app")
    val fragmentArgs = Bundle()
    fragmentArgs.putString(":settings:fragment_args_key", "mock_location_app")
    intent.putExtra(":settings:show_fragment_args", fragmentArgs)
    startSettingsActivity(context, intent)
}

// About phone — the closest launchable screen to the Build-number row used to
// enable Developer options (no intent exists for that step itself).
fun openAboutPhone(context: Context) {
    startSettingsActivity(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
}

fun openLocationSettings(context: Context) {
    startSettingsActivity(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}

private fun startSettingsActivity(context: Context, intent: Intent) {
    val resolved = if (intent.resolveActivity(context.packageManager) != null) {
        intent
    } else {
        // a matching activity is not guaranteed on all devices; fall back to
        // the Settings root
        Intent(Settings.ACTION_SETTINGS)
    }
    if (context !is Activity) {
        resolved.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(resolved)
    } catch (e: Throwable) {
        // no resolvable settings activity at all; nothing further to do
    }
}
