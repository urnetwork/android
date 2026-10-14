package com.bringyour.network.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

// What tapping the "Ignore battery optimizations" row does.
enum class BatteryOptimizationAction {
    // the system exemption dialog (needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    // which the Google Play build removes)
    RequestExemption,
    // the system battery optimization list (no permission, Play-allowed)
    OpenOptimizationSettings,
    // this app's App info screen, when the list does not resolve on the device
    OpenAppDetails,
}

// The row is shown on every flavor. A flavor that can ask for the exemption
// does so while the app is still optimized; otherwise (the Google Play build,
// or turning an existing exemption back off) the user is sent to the system
// setting, since the app has no API to change it.
fun batteryOptimizationAction(
    canRequestExemption: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    optimizationSettingsResolves: Boolean,
): BatteryOptimizationAction {
    return if (canRequestExemption && !isIgnoringBatteryOptimizations) {
        BatteryOptimizationAction.RequestExemption
    } else if (optimizationSettingsResolves) {
        BatteryOptimizationAction.OpenOptimizationSettings
    } else {
        BatteryOptimizationAction.OpenAppDetails
    }
}

fun batteryOptimizationSettingsIntent(): Intent {
    return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

fun appDetailsSettingsIntent(context: Context): Intent {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = Uri.fromParts("package", context.packageName, null)
    return intent
}

fun performBatteryOptimizationAction(context: Context, isIgnoringBatteryOptimizations: Boolean) {
    val settingsIntent = batteryOptimizationSettingsIntent()
    val action = batteryOptimizationAction(
        canRequestExemption = supportsBatteryOptimizationExemption(),
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
        optimizationSettingsResolves = settingsIntent.resolveActivity(context.packageManager) != null,
    )
    when (action) {
        BatteryOptimizationAction.RequestExemption -> requestBatteryOptimizationExemption(context)
        BatteryOptimizationAction.OpenOptimizationSettings -> {
            if (!startSettingsActivity(context, settingsIntent)) {
                startSettingsActivity(context, appDetailsSettingsIntent(context))
            }
        }
        BatteryOptimizationAction.OpenAppDetails -> startSettingsActivity(context, appDetailsSettingsIntent(context))
    }
}

private fun startSettingsActivity(context: Context, intent: Intent): Boolean {
    if (context !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
