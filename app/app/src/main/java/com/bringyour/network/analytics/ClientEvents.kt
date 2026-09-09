package com.bringyour.network.analytics

import android.content.Context
import com.bringyour.sdk.ClientEvent
import com.bringyour.sdk.ClientEventQueue
import com.bringyour.sdk.Sdk

/**
 * The app's product events (mmm/onboarding/PLAN.md "Optimization loop"): one
 * queue per app process, owned by MainApplication, and this facade in front of
 * it so a screen never touches the queue or builds an event by hand. Every
 * event comes from an SDK constructor, which is the only way to make one: the
 * schema is closed on the server, and the constructors are what the server
 * accepts.
 *
 * Nothing here blocks: `send` hands the event to the queue, which batches and
 * persists it (30 s, at most 200 per call, three retries, held while there is
 * no signed-in jwt).
 */
object ClientEvents {

    // onboarding step names, in order (index 0..5)
    const val STEP_WELCOME = "welcome"
    const val STEP_USAGE = "usage"
    const val STEP_PROVIDE = "provide"
    const val STEP_REFERRAL = "referral"
    const val STEP_WIDGETS = "widgets"
    const val STEP_OFFER = "offer"

    // widget kinds
    const val WIDGET_DASHBOARD = "dashboard"
    const val WIDGET_GLOBE = "globe"
    const val WIDGET_CONTRACTS = "contracts"
    const val WIDGET_QUICK_SETTINGS = "quick_settings"

    // the Play product id (the store's product on a purchase event)
    const val PRODUCT_PLAY_SUPPORTER = "supporter"
    const val PRODUCT_STRIPE_PRO = "pro"
    const val PRODUCT_SOLANA_PRO_YEARLY = "pro_yearly_usdc"

    private const val PREFS = "client_events"

    @Volatile
    private var queueProvider: () -> ClientEventQueue? = { null }

    @Volatile
    private var appContext: Context? = null

    /** MainApplication installs the provider once the active network space is known. */
    fun install(context: Context, provider: () -> ClientEventQueue?) {
        appContext = context.applicationContext
        queueProvider = provider
    }

    fun send(event: ClientEvent?) {
        if (event == null) {
            return
        }
        queueProvider()?.add(event)
    }

    // ----- onboarding -----

    fun onboardingStepShown(step: String, index: Int, elapsedMs: Long = 0L) =
        send(Sdk.newOnboardingStepShownEvent(step, index.toLong(), elapsedMs))

    fun onboardingStepCompleted(step: String, index: Int, elapsedMs: Long) =
        send(Sdk.newOnboardingStepCompletedEvent(step, index.toLong(), elapsedMs))

    fun onboardingStepSkipped(step: String, index: Int, elapsedMs: Long) =
        send(Sdk.newOnboardingStepSkippedEvent(step, index.toLong(), elapsedMs))

    // ----- the welcome offer -----

    fun offerScreenShown(
        surface: String,
        experiment: String,
        variant: String,
        tier: String,
        priceShown: Double,
        currency: String,
        expiresInS: Long,
    ) = send(Sdk.newOfferScreenShownEvent(surface, experiment, variant, tier, priceShown, currency, expiresInS))

    fun offerCardTapped(plan: String) = send(Sdk.newOfferCardTappedEvent(plan))

    fun offerCtaTapped(plan: String, store: String) = send(Sdk.newOfferCtaTappedEvent(plan, store))

    fun offerDeclined(control: String, elapsedMs: Long) = send(Sdk.newOfferDeclinedEvent(control, elapsedMs))

    // ----- purchases -----

    fun purchaseStarted(store: String, product: String, plan: String, trial: Boolean, price: Double, currency: String) =
        send(Sdk.newPurchaseStartedEvent(store, product, plan, trial, price, currency))

    fun purchaseCompleted(store: String, product: String, plan: String, trial: Boolean, price: Double, currency: String) =
        send(Sdk.newPurchaseCompletedEvent(store, product, plan, trial, price, currency))

    fun purchaseCancelled(store: String, product: String, plan: String, trial: Boolean, price: Double, currency: String) =
        send(Sdk.newPurchaseCancelledEvent(store, product, plan, trial, price, currency))

    fun purchaseFailed(store: String, product: String, plan: String, trial: Boolean, price: Double, currency: String, errorClass: String) =
        send(Sdk.newPurchaseFailedEvent(store, product, plan, trial, price, currency, errorClass))

    // ----- activation -----

    /**
     * `connect.first`, once per network: the first successful connection this
     * install has seen for the signed-in network. The mark is per network id so
     * a second account on the same phone gets its own first connection.
     */
    fun connectFirst(networkId: String?) {
        val context = appContext ?: return
        if (networkId.isNullOrEmpty()) {
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "connect_first:$networkId"
        if (prefs.getBoolean(key, false)) {
            return
        }
        prefs.edit().putBoolean(key, true).apply()
        send(Sdk.newConnectFirstEvent())
    }

    fun widgetAdded(kind: String) = send(Sdk.newWidgetAddedEvent(kind))

    fun feedbackSubmitted(rating: Int, reason: String, text: String) =
        send(Sdk.newFeedbackSubmittedEvent(rating.toLong(), reason, text))

    fun signupOptoutChanged(productUpdates: Boolean) = send(Sdk.newSignupOptoutChangedEvent(productUpdates))
}
