package com.bringyour.network.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.bringyour.network.TAG
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Mock location engine: syncs the device's reported location with the oldest
// connected provider while the tunnel is up. Sole owner of every
// LocationManager test-provider call. Design: ~/urnetwork/android/MOCKLOCATION.md.
//
// Threading: every provider mutation, the repost tick, and all private state
// live on one dedicated handler thread. Public methods only post to it; `state`
// is safe to read anywhere.
//
// Key operational facts driving the shape of this class:
// - test providers are NEVER auto-removed by the OS — not on crash, force-stop,
//   or uninstall (§6.3) — so start() defensively cleans up when the persisted
//   toggle is off, and the registered provider-name set is persisted before
//   registration so a later process can remove exactly what was claimed.
// - if the app op is revoked while providers are registered (deselected in
//   Developer options), cleanup throws SecurityException and is impossible
//   until re-selection or reboot (§6.4) -> ORPHANED, with automatic deferred
//   cleanup when the op watcher reports the op restored.
@Singleton
class MockLocationController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val PREFS_NAME = "mock_location"
        const val PREF_KEY_ENABLED = "enabled"
        const val PREF_KEY_REGISTERED_PROVIDERS = "registered_providers"

        // 1 Hz keeps every consumer inside its freshness window
        // (getCurrentLocation applies a 30 s rule; FLP expects monotonically
        // increasing timestamps) — §6.1/§6.2.
        private const val POST_INTERVAL_MILLIS = 1_000L
        private const val ERROR_RETRY_MILLIS = 5_000L

        // plausible GNSS fix; widened for one post after (re)targeting so
        // consumers treat the teleport as a new fix instead of filtering an
        // implausible velocity (§10.5)
        private const val ACCURACY_METERS = 8f
        private const val TELEPORT_ACCURACY_METERS = 25f
        private const val ALTITUDE_METERS = 10.0
        private const val VERTICAL_ACCURACY_METERS = 12f
        private const val SPEED_ACCURACY_METERS_PER_SECOND = 0.1f
        private const val BEARING_ACCURACY_DEGREES = 1f
        private const val ELAPSED_REALTIME_UNCERTAINTY_NANOS = 1_000_000.0
    }

    private val handlerThread = HandlerThread("mock_location").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // all fields below are confined to the handler thread
    private var started = false
    private var enabled = false
    private var tunnelUp = false
    private var target: MockLocationTarget? = null
    private var orphaned = false
    private var errorTransient = false
    private var posting = false
    private var teleport = false
    private var fusedActive = false
    private var registeredProviders = listOf<String>()
    private var unwatchOp: (() -> Unit)? = null
    private var devOptionsEnabled = false
    private var selectedMockApp = false
    private var locationServicesEnabled = false

    private val _state = MutableStateFlow(
        MockLocationState(MockLocationStatus.DISABLED, enabled = false, target = null)
    )
    val state: StateFlow<MockLocationState> = _state.asStateFlow()

    // Idempotent. Loads the persisted toggle, registers the op watcher, and —
    // when the toggle is off — best-effort removes every provider a previous
    // process may have left registered (mandatory startup cleanup, §6.3).
    // Public entry points also ensure this ran, so call order is not fragile;
    // still call it from app/service startup so leftovers are cleaned even if
    // the feature UI is never opened.
    fun start() {
        handler.post { ensureStarted() }
    }

    fun setEnabled(value: Boolean) {
        handler.post {
            ensureStarted()
            enabled = value
            prefs.edit().putBoolean(PREF_KEY_ENABLED, value).apply()
            errorTransient = false
            refreshEligibilitySignals()
            reconcile()
        }
    }

    // The oldest connected provider with coordinates, or null when none has
    // them. Changing coordinates while active never re-adds providers — the
    // next post simply teleports (speed/bearing are always 0, accuracy is
    // widened for one post).
    fun onTargetChanged(newTarget: MockLocationTarget?) {
        handler.post {
            ensureStarted()
            if (target == newTarget) {
                return@post
            }
            target = newTarget
            errorTransient = false
            if (posting && newTarget != null) {
                teleport = true
                // repost immediately so the teleport doesn't wait a tick
                handler.removeCallbacks(postRunnable)
                handler.post(postRunnable)
            }
            reconcile()
        }
    }

    fun onTunnelChanged(up: Boolean) {
        handler.post {
            ensureStarted()
            if (tunnelUp == up) {
                return@post
            }
            tunnelUp = up
            errorTransient = false
            reconcile()
        }
    }

    // Re-reads developer options / mock-app selection / location services and
    // recomputes. Call from UI ON_RESUME; also runs on op-watcher callbacks.
    fun refreshEligibility() {
        handler.post {
            ensureStarted()
            refreshEligibilitySignals()
            errorTransient = false
            reconcile()
        }
    }

    // Tears down mocking (stop ticker, remove providers, GMS mock mode off)
    // and drops the runtime inputs. The controller can be start()ed again.
    fun shutdown() {
        handler.post {
            unwatchOp?.invoke()
            unwatchOp = null
            handler.removeCallbacks(errorRetryRunnable)
            handler.removeCallbacks(postRunnable)
            if (started) {
                disarm()
            }
            tunnelUp = false
            target = null
            errorTransient = false
            started = false
            publishState()
        }
    }

    private fun ensureStarted() {
        if (started) {
            return
        }
        started = true
        enabled = prefs.getBoolean(PREF_KEY_ENABLED, false)
        refreshEligibilitySignals()
        unwatchOp = startWatchingMockLocationOp(context) {
            // the op callback arrives on a binder thread
            handler.post { onMockLocationOpChanged() }
        }
        if (!enabled) {
            // §6.3: nothing removes test providers on process death — clear
            // anything a previous process left behind
            removeAllTestProviders()
        }
        reconcile()
    }

    private fun onMockLocationOpChanged() {
        refreshEligibilitySignals()
        if (selectedMockApp && !posting && (orphaned || !enabled)) {
            // the op is back: the deferred cleanup is now possible (§6.4).
            // On success this clears the orphaned flag; reconcile then lands
            // on DISABLED (toggle off) or re-arms cleanly (toggle on).
            removeAllTestProviders()
        }
        reconcile()
    }

    private fun refreshEligibilitySignals() {
        devOptionsEnabled = isDeveloperOptionsEnabled(context)
        selectedMockApp = isSelectedMockLocationApp(context)
        locationServicesEnabled = isLocationServicesEnabled(context)
    }

    private fun resolveStatus(): MockLocationStatus {
        return resolveMockLocationStatus(
            enabled = enabled,
            devOptionsEnabled = devOptionsEnabled,
            isSelectedMockApp = selectedMockApp,
            locationServicesEnabled = locationServicesEnabled,
            tunnelUp = tunnelUp,
            target = target,
            orphaned = orphaned,
        )
    }

    private fun reconcile() {
        val shouldPost = resolveStatus() == MockLocationStatus.ACTIVE && !errorTransient
        if (shouldPost && !posting) {
            arm()
        } else if (!shouldPost && posting) {
            disarm()
        }
        publishState()
    }

    private fun publishState() {
        val resolved = resolveStatus()
        val status = if (
            errorTransient &&
            resolved != MockLocationStatus.DISABLED &&
            resolved != MockLocationStatus.ORPHANED
        ) {
            MockLocationStatus.ERROR_TRANSIENT
        } else {
            resolved
        }
        _state.value = MockLocationState(
            status = status,
            enabled = enabled,
            target = target,
            devOptionsEnabled = devOptionsEnabled,
            mockAppSelected = selectedMockApp,
            locationServicesEnabled = locationServicesEnabled,
        )
    }

    // -- provider registration / teardown (handler thread only) --

    private fun defaultProviderNames(): List<String> {
        val names = mutableListOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // public + the platform default provider only on 31+; pointless
            // (and hidden) before that. NEVER "passive" (§3.1).
            names.add(LocationManager.FUSED_PROVIDER)
        }
        return names
    }

    private fun locationManager(): LocationManager? {
        return context.getSystemService(LocationManager::class.java)
    }

    private fun arm() {
        val locationManager = locationManager()
        if (locationManager == null) {
            errorTransient = true
            scheduleErrorRetry()
            return
        }
        val names = defaultProviderNames()
        // persist the claimed set BEFORE registering so a crash mid-add still
        // leaves an exact cleanup list for the next process (§6.3)
        prefs.edit().putStringSet(PREF_KEY_REGISTERED_PROVIDERS, names.toSet()).apply()
        try {
            for (name in names) {
                addTestProvider(locationManager, name)
            }
            registeredProviders = names
            fusedActive = supportsFusedMockLocation(context)
            if (fusedActive) {
                setFusedMockMode(context, true)
            }
            teleport = true
            posting = true
            handler.removeCallbacks(postRunnable)
            handler.post(postRunnable)
            Log.i(TAG, "mock location armed providers=$names fused=$fusedActive")
        } catch (e: SecurityException) {
            // op revoked between eligibility check and add: any provider that
            // did get added cannot be removed until re-selection (§6.4). Keep
            // the persisted set for the deferred cleanup.
            Log.i(TAG, "mock location arm failed, op not allowed: ${e.message}")
            posting = false
            orphaned = true
        } catch (e: Throwable) {
            Log.i(TAG, "mock location arm failed: $e")
            errorTransient = true
            removeAllTestProviders()
            scheduleErrorRetry()
        }
    }

    private fun addTestProvider(locationManager: LocationManager, name: String) {
        try {
            // required pre-S (re-adding an existing test provider throws);
            // harmless on S+ where add replaces
            locationManager.removeTestProvider(name)
        } catch (e: Throwable) {
            // unknown provider pre-S; the add below surfaces real failures
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager.addTestProvider(
                name,
                ProviderProperties.Builder()
                    .setHasNetworkRequirement(false)
                    .setHasSatelliteRequirement(false)
                    .setHasCellRequirement(false)
                    .setHasMonetaryCost(false)
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build(),
            )
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                name,
                /* requiresNetwork = */ false,
                /* requiresSatellite = */ false,
                /* requiresCell = */ false,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                // == ProviderProperties.POWER_USAGE_LOW / ACCURACY_FINE (1/1)
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE,
            )
        }
        // MANDATORY: a fresh test provider starts disallowed; without this,
        // isProviderEnabled() flips false device-wide and nothing is
        // delivered (§2.3)
        locationManager.setTestProviderEnabled(name, true)
    }

    private fun disarm() {
        handler.removeCallbacks(postRunnable)
        posting = false
        fusedActive = false
        removeAllTestProviders()
    }

    // Best-effort removal of every provider this app may have registered
    // (in-memory set ∪ persisted set ∪ default set — remove is a no-op for
    // unregistered names). On full success clears the persisted set and the
    // orphaned flag; a SecurityException while we believe providers exist
    // marks ORPHANED and keeps the set for the deferred cleanup (§6.4).
    // Always exits GMS mock mode (device-global, §3.2).
    private fun removeAllTestProviders() {
        val persisted = prefs.getStringSet(PREF_KEY_REGISTERED_PROVIDERS, null) ?: emptySet()
        val mayHaveProviders =
            orphaned || persisted.isNotEmpty() || registeredProviders.isNotEmpty()
        val names = LinkedHashSet<String>()
        names.addAll(registeredProviders)
        names.addAll(persisted)
        names.addAll(defaultProviderNames())
        var securityFailure = false
        val locationManager = locationManager() ?: return
        for (name in names) {
            try {
                locationManager.removeTestProvider(name)
            } catch (e: SecurityException) {
                securityFailure = true
            } catch (e: Throwable) {
                // pre-S throws IllegalArgumentException for unknown names:
                // nothing registered under that name
            }
        }
        // no-op on non-GMS flavors/devices; fire-and-forget elsewhere
        setFusedMockMode(context, false)
        if (securityFailure && mayHaveProviders) {
            if (!orphaned) {
                Log.i(TAG, "mock location cleanup blocked; providers linger until the app is re-selected or reboot")
            }
            orphaned = true
        } else if (!securityFailure) {
            orphaned = false
            registeredProviders = listOf()
            if (persisted.isNotEmpty()) {
                prefs.edit().remove(PREF_KEY_REGISTERED_PROVIDERS).apply()
            }
        }
        // securityFailure && !mayHaveProviders: not the selected mock app and
        // nothing of ours registered — not an orphan condition
    }

    // -- 1 Hz post loop (handler thread only) --

    private val postRunnable = object : Runnable {
        override fun run() {
            if (!posting) {
                return
            }
            val postTarget = target ?: return
            val locationManager = locationManager() ?: return
            val accuracy = if (teleport) TELEPORT_ACCURACY_METERS else ACCURACY_METERS
            try {
                for (name in registeredProviders) {
                    locationManager.setTestProviderLocation(
                        name,
                        buildLocation(name, postTarget, accuracy),
                    )
                }
                if (fusedActive) {
                    setFusedMockLocation(
                        context,
                        buildLocation(LocationManager.GPS_PROVIDER, postTarget, accuracy),
                    )
                }
                teleport = false
                handler.postDelayed(this, POST_INTERVAL_MILLIS)
            } catch (e: SecurityException) {
                // deselected while active: the ticker stops and cleanup is
                // deferred until the op watcher reports the op restored (§6.4)
                Log.i(TAG, "mock location op revoked while active: ${e.message}")
                posting = false
                orphaned = true
                reconcile()
            } catch (e: Throwable) {
                Log.i(TAG, "mock location post failed: $e")
                errorTransient = true
                disarm()
                scheduleErrorRetry()
                reconcile()
            }
        }
    }

    private fun buildLocation(
        providerName: String,
        target: MockLocationTarget,
        accuracy: Float,
    ): Location {
        val location = Location(providerName)
        location.latitude = target.lat
        location.longitude = target.lon
        location.accuracy = accuracy
        location.altitude = ALTITUDE_METERS
        location.speed = 0f
        location.bearing = 0f
        // API 26+ (minSdk): cheap, and makes the fix look complete
        location.verticalAccuracyMeters = VERTICAL_ACCURACY_METERS
        location.speedAccuracyMetersPerSecond = SPEED_ACCURACY_METERS_PER_SECOND
        location.bearingAccuracyDegrees = BEARING_ACCURACY_DEGREES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            location.elapsedRealtimeUncertaintyNanos = ELAPSED_REALTIME_UNCERTAINTY_NANOS
        }
        // both timestamps fresh on EVERY post: a stale/reused
        // elapsedRealtimeNanos is rejected by the server-side validation and
        // trips FLP monotonicity (§2.4, §6.2)
        location.time = System.currentTimeMillis()
        location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        return location
    }

    // -- transient error retry --

    private val errorRetryRunnable = Runnable {
        if (errorTransient) {
            errorTransient = false
            refreshEligibilitySignals()
            reconcile()
        }
    }

    private fun scheduleErrorRetry() {
        handler.removeCallbacks(errorRetryRunnable)
        handler.postDelayed(errorRetryRunnable, ERROR_RETRY_MILLIS)
    }
}
