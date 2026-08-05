package com.bringyour.network.location

import android.content.Context
import android.location.Location

// github flavor: platform-only mock location (no Google Play services
// dependency). The LocationManager test providers cover gps/network/fused.

fun supportsFusedMockLocation(context: Context): Boolean = false

fun setFusedMockMode(context: Context, enabled: Boolean) = Unit

fun setFusedMockLocation(context: Context, location: Location) = Unit
