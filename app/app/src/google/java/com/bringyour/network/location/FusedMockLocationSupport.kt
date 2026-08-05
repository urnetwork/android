package com.bringyour.network.location

import android.content.Context
import android.location.Location
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices

// GMS layer of the mock location engine (MOCKLOCATION.md §3.2): mirror the
// mock fix into the Google Play services fused location provider so
// FLP-based consumers (Google Maps et al.) reliably follow it. Requires the
// same developer-options selection as the platform path — no extra user step.
// Every call is fire-and-forget: the Task results are intentionally ignored
// and failures surface through the platform path instead.

fun supportsFusedMockLocation(context: Context): Boolean {
    return try {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (e: Throwable) {
        false
    }
}

// setMockMode is device-global (affects all FLP clients in every process) —
// callers must always exit mock mode on every teardown path.
fun setFusedMockMode(context: Context, enabled: Boolean) {
    if (!supportsFusedMockLocation(context)) {
        return
    }
    try {
        LocationServices.getFusedLocationProviderClient(context).setMockMode(enabled)
    } catch (e: SecurityException) {
        // not the selected mock location app
    } catch (e: Throwable) {
        // broken/ancient play services; the platform path still works
    }
}

fun setFusedMockLocation(context: Context, location: Location) {
    if (!supportsFusedMockLocation(context)) {
        return
    }
    try {
        LocationServices.getFusedLocationProviderClient(context).setMockLocation(location)
    } catch (e: SecurityException) {
        // not the selected mock location app
    } catch (e: Throwable) {
        // broken/ancient play services; the platform path still works
    }
}
