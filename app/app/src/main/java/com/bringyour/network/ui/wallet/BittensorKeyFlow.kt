package com.bringyour.network.ui.wallet

import com.bringyour.sdk.Api
import com.bringyour.sdk.BittensorLocalKeyFlow
import com.bringyour.sdk.BittensorLocalKeyFlowListener
import com.bringyour.sdk.BittensorLocalKeyStore
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub

/*
 * The door to the sdk's payout key flow (sdk BittensorLocalKeyFlow,
 * apple/BTEASY.md): the key's states, what the card offers, the disclaimer
 * each screen shows, the two-word check and the rule that nothing signs until
 * the phrase is safe all live there, with their tests, so every app has the
 * same ones. The screens read the value types here, never gomobile objects,
 * and a preview hands them a stand-in for the flow. The app never makes a key
 * or signs with one itself.
 */

/** What the payout card shows (sdk BittensorLocalKeyCard). Holds no secret. */
data class BittensorKeyCardInfo(
    // a BittensorLocalKeyState*
    val state: String = Sdk.BittensorLocalKeyStateLoading,
    val address: String = "",
    val storedAddress: String = "",
    val showsKeyPath: Boolean = false,
    val offersSetUp: Boolean = false,
    val offersSaveFirst: Boolean = false,
    val offersWallet: Boolean = false,
    val offersFinishSetup: Boolean = false,
    val offersKeyScreen: Boolean = false,
    val replacesPayoutWallet: Boolean = false,
    val ownerOnly: Boolean = false,
    val payoutsStartNextEpoch: Boolean = false,
    val backupReminder: Boolean = false,
    val keySaved: Boolean = false,
    val busy: Boolean = false,
    val linkGeneration: Long = 0,
    val failureCode: String = "",
    val failureMessage: String = "",
    // the disclaimer the card shows, a BittensorFundsDisclaimer*
    val fundsDisclaimer: String = Sdk.BittensorFundsDisclaimerNone,
    // the disclaimer the claim tiles show, a BittensorFundsDisclaimer*: none
    // while the card shows one
    val claimFundsDisclaimer: String = Sdk.BittensorFundsDisclaimerFundsOnly,
)

/** What the Key screen shows (sdk BittensorLocalKeyScreen). Holds no secret: the words are [BittensorKeyFlowing.revealedWords]. */
data class BittensorKeyScreenInfo(
    val open: Boolean = false,
    val saveNow: Boolean = false,
    val address: String = "",
    val revealed: Boolean = false,
    // while revealed, when the words hide by themselves
    val revealedUntilMillis: Long = 0,
    val needsAuthentication: Boolean = false,
    val hiddenByCapture: Boolean = false,
    // a BittensorLocalKeyConfirm*
    val confirming: String = Sdk.BittensorLocalKeyConfirmNone,
    // the confirmation's button asks for device authentication first: deleting a stored key
    val confirmNeedsAuthentication: Boolean = false,
    val firstWordPosition: Long = 0,
    val secondWordPosition: Long = 0,
    val wordsRejected: Boolean = false,
    val warnsTop200: Boolean = false,
    val offersKeep: Boolean = false,
    val offersRetry: Boolean = false,
    val busy: Boolean = false,
    val done: Boolean = false,
    val failureCode: String = "",
    val failureMessage: String = "",
    // the disclaimer the screen and its confirmations show, a BittensorFundsDisclaimer*
    val fundsDisclaimer: String = Sdk.BittensorFundsDisclaimerNone,
)

/** The two words as the user types them (sdk BittensorLocalKeyWordCheck): a BittensorLocalKeyWord* each, and whether the button can act. */
data class BittensorKeyWordCheckInfo(
    val first: String = Sdk.BittensorLocalKeyWordEmpty,
    val second: String = Sdk.BittensorLocalKeyWordEmpty,
    val ready: Boolean = false,
)

/** What to do about the one reminder of an unsaved key (sdk BittensorLocalKeyReminder). */
data class BittensorKeyReminderInfo(
    // a BittensorLocalKeyReminder*
    val action: String = Sdk.BittensorLocalKeyReminderNone,
    val atMillis: Long = 0,
)

/**
 * The sdk's flow as the screens use it. The words leave it only through
 * [revealedWords] and [phraseToCopy], and only while they show. A call that
 * reaches the store (marked "store") may read a file or the Keystore, so the
 * model makes it off the main thread.
 */
interface BittensorKeyFlowing {
    val card: BittensorKeyCardInfo
    val screen: BittensorKeyScreenInfo
    // empty while hidden
    val revealedWords: List<String>
    // "" while hidden, so there is no Copy then
    val phraseToCopy: String

    // on an arbitrary thread
    fun onChange(handler: () -> Unit)

    // store
    fun reload()
    fun setConsent(active: Boolean, coldkeySs58: String, fromEpoch: Long)
    fun setConsentUnavailable()
    fun setCurrentEpoch(currentEpoch: Long)
    fun setGuest(guest: Boolean)
    fun setTop200Registered(registered: Boolean)

    fun setUpPayouts(replaceConfirmed: Boolean)
    fun finishSetup(replaceConfirmed: Boolean)
    // makes a key in memory
    fun saveFirst(replaceConfirmed: Boolean)
    fun retrySaveNow()

    // store
    fun openScreen()
    fun closeScreen()
    // store, for a stored key
    fun reveal(authenticated: Boolean)
    fun hide()
    // store, for a stored key
    fun confirmDelete()
    // store, for a stored key
    fun confirmKeep()
    fun cancelConfirmation()
    // synchronous, and changes nothing
    fun checkWords(first: String, second: String): BittensorKeyWordCheckInfo
    // authenticated: the device's authentication passed, which deleting a stored key needs
    fun submitWords(first: String, second: String, authenticated: Boolean)

    // store
    fun reminder(currentEpoch: Long, claimOpenMillis: Long, epochBlocks: Long, claimable: Boolean): BittensorKeyReminderInfo
    // store
    fun reminderScheduled(atMillis: Long)
    // store
    fun reminderDelivered()

    // a BittensorFundsDisclaimer* for a BittensorFundsDisclaimerPlace*
    fun fundsDisclaimerAt(place: String): String

    fun close()
}

/** The sdk's flow for one network on this device. */
class SdkBittensorKeyFlow(api: Api?, networkId: String, store: BittensorLocalKeyStore) : BittensorKeyFlowing {

    private val flow: BittensorLocalKeyFlow = Sdk.newBittensorLocalKeyFlow(api, networkId, store)
    private var subscription: Sub? = null

    @Volatile
    private var handler: (() -> Unit)? = null

    override fun onChange(handler: () -> Unit) {
        this.handler = handler
        if (subscription == null) {
            subscription = flow.addBittensorLocalKeyFlowListener(object : BittensorLocalKeyFlowListener {
                override fun bittensorLocalKeyFlowChanged() {
                    // an exception can't cross back into Go
                    runCatching { this@SdkBittensorKeyFlow.handler?.invoke() }
                }
            })
        }
    }

    override val card: BittensorKeyCardInfo
        get() {
            val card = flow.card ?: return BittensorKeyCardInfo()
            return BittensorKeyCardInfo(
                state = card.state,
                address = card.address,
                storedAddress = card.storedAddress,
                showsKeyPath = card.showsKeyPath,
                offersSetUp = card.offersSetUp,
                offersSaveFirst = card.offersSaveFirst,
                offersWallet = card.offersWallet,
                offersFinishSetup = card.offersFinishSetup,
                offersKeyScreen = card.offersKeyScreen,
                replacesPayoutWallet = card.replacesPayoutWallet,
                ownerOnly = card.ownerOnly,
                payoutsStartNextEpoch = card.payoutsStartNextEpoch,
                backupReminder = card.backupReminder,
                keySaved = card.keySaved,
                busy = card.busy,
                linkGeneration = card.linkGeneration,
                failureCode = card.failureCode,
                failureMessage = card.failureMessage,
                fundsDisclaimer = card.fundsDisclaimer,
                claimFundsDisclaimer = card.claimFundsDisclaimer,
            )
        }

    override val screen: BittensorKeyScreenInfo
        get() {
            val screen = flow.screen ?: return BittensorKeyScreenInfo()
            return BittensorKeyScreenInfo(
                open = screen.open,
                saveNow = screen.saveNow,
                address = screen.address,
                revealed = screen.revealed,
                revealedUntilMillis = screen.revealedUntilMillis,
                needsAuthentication = screen.needsAuthentication,
                hiddenByCapture = screen.hiddenByCapture,
                confirming = screen.confirming,
                confirmNeedsAuthentication = screen.confirmNeedsAuthentication,
                firstWordPosition = screen.firstWordPosition,
                secondWordPosition = screen.secondWordPosition,
                wordsRejected = screen.wordsRejected,
                warnsTop200 = screen.warnsTop200,
                offersKeep = screen.offersKeep,
                offersRetry = screen.offersRetry,
                busy = screen.busy,
                done = screen.done,
                failureCode = screen.failureCode,
                failureMessage = screen.failureMessage,
                fundsDisclaimer = screen.fundsDisclaimer,
            )
        }

    override val revealedWords: List<String>
        get() {
            val list = flow.revealedWords() ?: return emptyList()
            return (0 until list.len()).map { list.get(it) }
        }

    override val phraseToCopy: String
        get() = flow.phraseToCopy() ?: ""

    override fun reload() = flow.reload()

    override fun setConsent(active: Boolean, coldkeySs58: String, fromEpoch: Long) =
        flow.setConsent(active, coldkeySs58, fromEpoch)

    override fun setConsentUnavailable() = flow.setConsentUnavailable()

    override fun setCurrentEpoch(currentEpoch: Long) = flow.setCurrentEpoch(currentEpoch)

    override fun setGuest(guest: Boolean) = flow.setGuest(guest)

    override fun setTop200Registered(registered: Boolean) = flow.setTop200Registered(registered)

    override fun setUpPayouts(replaceConfirmed: Boolean) = flow.setUpPayouts(replaceConfirmed)

    override fun finishSetup(replaceConfirmed: Boolean) = flow.finishSetup(replaceConfirmed)

    override fun saveFirst(replaceConfirmed: Boolean) = flow.saveFirst(replaceConfirmed)

    override fun retrySaveNow() = flow.retrySaveNow()

    override fun openScreen() = flow.openScreen()

    override fun closeScreen() = flow.closeScreen()

    override fun reveal(authenticated: Boolean) = flow.reveal(authenticated)

    override fun hide() = flow.hide()

    override fun confirmDelete() = flow.confirmDelete()

    override fun confirmKeep() = flow.confirmKeep()

    override fun cancelConfirmation() = flow.cancelConfirmation()

    override fun checkWords(first: String, second: String): BittensorKeyWordCheckInfo {
        val check = flow.checkWords(first, second) ?: return BittensorKeyWordCheckInfo()
        return BittensorKeyWordCheckInfo(first = check.first, second = check.second, ready = check.ready)
    }

    override fun submitWords(first: String, second: String, authenticated: Boolean) =
        flow.submitWords(first, second, authenticated)

    override fun reminder(currentEpoch: Long, claimOpenMillis: Long, epochBlocks: Long, claimable: Boolean): BittensorKeyReminderInfo {
        val reminder = flow.reminder(currentEpoch, claimOpenMillis, epochBlocks, claimable) ?: return BittensorKeyReminderInfo()
        return BittensorKeyReminderInfo(action = reminder.action, atMillis = reminder.atMillis)
    }

    override fun reminderScheduled(atMillis: Long) = flow.reminderScheduled(atMillis)

    override fun reminderDelivered() = flow.reminderDelivered()

    override fun fundsDisclaimerAt(place: String): String = flow.fundsDisclaimerAt(place) ?: Sdk.BittensorFundsDisclaimerNone

    override fun close() {
        subscription?.close()
        subscription = null
        handler = null
        flow.close()
    }
}

/** Canned states for previews. A real phrase never reaches one. */
class BittensorKeyPreviewFlow(
    override val card: BittensorKeyCardInfo = BittensorKeyCardInfo(
        state = Sdk.BittensorLocalKeyStateNone,
        showsKeyPath = true,
        offersSetUp = true,
        offersSaveFirst = true,
        offersWallet = true,
        fundsDisclaimer = Sdk.BittensorFundsDisclaimerFundsOnly,
        claimFundsDisclaimer = Sdk.BittensorFundsDisclaimerNone,
    ),
    override val screen: BittensorKeyScreenInfo = BittensorKeyScreenInfo(),
) : BittensorKeyFlowing {
    override val revealedWords: List<String> = emptyList()
    override val phraseToCopy: String = ""
    override fun onChange(handler: () -> Unit) {}
    override fun reload() {}
    override fun setConsent(active: Boolean, coldkeySs58: String, fromEpoch: Long) {}
    override fun setConsentUnavailable() {}
    override fun setCurrentEpoch(currentEpoch: Long) {}
    override fun setGuest(guest: Boolean) {}
    override fun setTop200Registered(registered: Boolean) {}
    override fun setUpPayouts(replaceConfirmed: Boolean) {}
    override fun finishSetup(replaceConfirmed: Boolean) {}
    override fun saveFirst(replaceConfirmed: Boolean) {}
    override fun retrySaveNow() {}
    override fun openScreen() {}
    override fun closeScreen() {}
    override fun reveal(authenticated: Boolean) {}
    override fun hide() {}
    override fun confirmDelete() {}
    override fun confirmKeep() {}
    override fun cancelConfirmation() {}
    override fun checkWords(first: String, second: String): BittensorKeyWordCheckInfo = BittensorKeyWordCheckInfo()
    override fun submitWords(first: String, second: String, authenticated: Boolean) {}
    override fun reminder(currentEpoch: Long, claimOpenMillis: Long, epochBlocks: Long, claimable: Boolean): BittensorKeyReminderInfo =
        BittensorKeyReminderInfo()
    override fun reminderScheduled(atMillis: Long) {}
    override fun reminderDelivered() {}
    override fun fundsDisclaimerAt(place: String): String = Sdk.BittensorFundsDisclaimerNone
    override fun close() {}
}
