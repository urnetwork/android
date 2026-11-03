package com.bringyour.network.ui.wallet

import com.bringyour.sdk.Api
import com.bringyour.sdk.BittensorLocalKeyStore
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Earnings screen's side of the sdk's payout key flow (apple/BTEASY.md,
 * the port brief): it mirrors the card and the Key screen into state the
 * screens collect, feeds the flow what Earnings reads (the consent, the
 * epoch, the guest flag, the Top 200 standing), and does for the flow what
 * only the app can do: device authentication, the clipboard, the warning
 * before a replacement and the local notification. Every decision about the
 * key is the flow's (sdk BittensorLocalKeyFlow); a decision here is a bug.
 *
 * The earnings view model owns one and points it at the signed-in network
 * with [reset], as it does its consent and ledger flows; each Earnings visit
 * reads the store again ([visit]), and leaving Earnings closes the Key screen,
 * which discards a phrase kept only in memory. A call that reaches the store
 * goes off the main thread. A stored key's words are read only after device
 * authentication, and they leave [words] as soon as the flow hides them.
 * Nothing here prints or logs.
 *
 * Not safe for concurrent use: call it on the main thread, as the screens do.
 */
class BittensorKeyModel(
    private val scope: CoroutineScope,
    private val store: BittensorLocalKeyStore,
    private val reminderNotice: BittensorKeyReminderPosting,
    private val makeFlow: (Api?, String, BittensorLocalKeyStore) -> BittensorKeyFlowing = ::SdkBittensorKeyFlow,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** What the warning before a replacement goes on to do. */
    enum class PendingReplacement {
        // a new key, in one tap
        SET_UP,
        // a new key, its phrase saved first
        SAVE_FIRST,
        // the key on this device, which the consent doesn't pay
        FINISH_SETUP,
    }

    // the consent as Earnings last read it, kept for a flow made later
    private sealed class ConsentInput {
        object Unread : ConsentInput()
        object Unavailable : ConsentInput()
        data class Read(val active: Boolean, val coldkeySs58: String, val fromEpoch: Long) : ConsentInput()
    }

    private var flow: BittensorKeyFlowing? = null
    private var networkId: String = ""
    // counts the flows asked for, so one made for an earlier network is dropped
    private var flowGeneration = 0
    private var consent: ConsentInput = ConsentInput.Unread
    private var guest = false
    private var top200Registered = false
    private var currentEpoch = 0L
    // the last earnings read, for a flow made after it: the schedule, and whether something is claimable
    private var earnings: Pair<SnEpochScheduleState?, Boolean>? = null
    private var observedLinkGeneration = 0L
    private var copiedJob: Job? = null

    private val _ready = MutableStateFlow(false)
    /** There is a flow for the signed-in network. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _card = MutableStateFlow(BittensorKeyCardInfo())
    val card: StateFlow<BittensorKeyCardInfo> = _card.asStateFlow()

    private val _screen = MutableStateFlow(BittensorKeyScreenInfo())
    val screen: StateFlow<BittensorKeyScreenInfo> = _screen.asStateFlow()

    // the words on the screen, empty while hidden
    private val _words = MutableStateFlow<List<String>>(emptyList())
    val words: StateFlow<List<String>> = _words.asStateFlow()

    // the warning that a new key replaces the payout wallet
    private val _pendingReplacement = MutableStateFlow<PendingReplacement?>(null)
    val pendingReplacement: StateFlow<PendingReplacement?> = _pendingReplacement.asStateFlow()

    // what deleting means, before the two words are asked: the step Continue leaves
    private val _deleteExplained = MutableStateFlow(false)
    val deleteExplained: StateFlow<Boolean> = _deleteExplained.asStateFlow()

    // Copy was tapped: the short Copied state
    private val _copied = MutableStateFlow(false)
    val copied: StateFlow<Boolean> = _copied.asStateFlow()

    /** A consent went through the flow: read the consent and earnings again. */
    var onLinked: () -> Unit = {}

    // ---- the network

    /**
     * A new device or network, or none: the last flow ends, and its words and
     * any phrase in memory with it. The sdk reads the store as it makes the
     * new flow, so that happens off the main thread.
     */
    fun reset(api: Api?, networkId: String?) {
        closeFlow()
        this.networkId = networkId.orEmpty()
        consent = ConsentInput.Unread
        currentEpoch = 0
        earnings = null
        top200Registered = false
        publishEmpty()
        val id = this.networkId
        if (id.isEmpty()) {
            return
        }
        val generation = flowGeneration
        scope.launch {
            val made = try {
                withContext(io) { makeFlow(api, id, store) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // no flow: the card stays the wallet card
                return@launch
            }
            if (generation != flowGeneration) {
                made.close()
                return@launch
            }
            install(made)
        }
    }

    /** The view model is done: the flow ends. */
    fun close() {
        closeFlow()
        publishEmpty()
    }

    private fun closeFlow() {
        flowGeneration += 1
        val old = flow
        flow = null
        _ready.value = false
        copiedJob?.cancel()
        // ends a consent on its way and lets the phrase go; it reads no store
        old?.close()
    }

    private fun install(made: BittensorKeyFlowing) {
        flow = made
        observedLinkGeneration = made.card.linkGeneration
        made.onChange {
            scope.launch { refresh() }
        }
        made.setGuest(guest)
        made.setTop200Registered(top200Registered)
        made.setCurrentEpoch(currentEpoch)
        applyConsent(made)
        _ready.value = true
        refresh()
        // earnings read before the flow was made still decide the reminder
        earnings?.let { (schedule, claimable) ->
            remind(made, schedule, claimable)
        }
    }

    private fun applyConsent(made: BittensorKeyFlowing) {
        when (val read = consent) {
            ConsentInput.Unread -> {}
            ConsentInput.Unavailable -> made.setConsentUnavailable()
            is ConsentInput.Read -> made.setConsent(read.active, read.coldkeySs58, read.fromEpoch)
        }
    }

    private fun publishEmpty() {
        _card.value = BittensorKeyCardInfo()
        _screen.value = BittensorKeyScreenInfo()
        _words.value = emptyList()
        _pendingReplacement.value = null
        _deleteExplained.value = false
        _copied.value = false
    }

    /** Mirrors the flow. A finished screen closes itself. */
    fun refresh() {
        val current = flow ?: return
        val card = current.card
        var screen = current.screen
        if (screen.done && screen.open) {
            current.closeScreen()
            screen = current.screen
        }
        // saved or deleted since the last look: a notification still on its
        // way would remind of nothing
        val reminderEnded = _card.value.backupReminder && !card.backupReminder
        _card.value = card
        if (reminderEnded) {
            val id = networkId
            scope.launch {
                withContext(io) {
                    reminderNotice.apply(current.reminder(0, 0, 0, false), current, id)
                }
            }
        }
        _screen.value = screen
        _words.value = if (screen.revealed) current.revealedWords else emptyList()
        if (!screen.open || screen.confirming != Sdk.BittensorLocalKeyConfirmNone) {
            _deleteExplained.value = false
        }
        if (card.linkGeneration != observedLinkGeneration) {
            observedLinkGeneration = card.linkGeneration
            onLinked()
        }
    }

    // ---- what the rest of Earnings read

    /** Earnings read the network's consent; loading says nothing new. */
    fun consentRead(load: SnConsentLoad) {
        consent = when (load) {
            SnConsentLoad.Loading -> return
            SnConsentLoad.Unavailable -> ConsentInput.Unavailable
            is SnConsentLoad.Loaded -> ConsentInput.Read(
                active = load.status.active,
                coldkeySs58 = load.status.coldkeySs58,
                fromEpoch = if (load.status.active) load.status.fromEpoch else 0,
            )
        }
        flow?.let {
            applyConsent(it)
            refresh()
        }
    }

    fun setGuest(guest: Boolean) {
        this.guest = guest
        flow?.let {
            it.setGuest(guest)
            refresh()
        }
    }

    fun setTop200Registered(registered: Boolean) {
        top200Registered = registered
        flow?.let {
            it.setTop200Registered(registered)
            refresh()
        }
    }

    /** The earnings read, for "Payouts start next epoch" and the one reminder. */
    fun earningsRead(schedule: SnEpochScheduleState?, claimable: Boolean) {
        currentEpoch = schedule?.epoch ?: 0
        earnings = schedule to claimable
        val current = flow ?: return
        current.setCurrentEpoch(currentEpoch)
        refresh()
        remind(current, schedule, claimable)
    }

    // the flow says what the one reminder needs now, and the notice does it
    private fun remind(current: BittensorKeyFlowing, schedule: SnEpochScheduleState?, claimable: Boolean) {
        val id = networkId
        scope.launch {
            withContext(io) {
                val reminder = current.reminder(
                    schedule?.epoch ?: 0,
                    schedule?.claimOpenMillis ?: 0,
                    schedule?.epochBlocks ?: 0,
                    claimable,
                )
                reminderNotice.apply(reminder, current, id)
            }
        }
    }

    /** A visit reads the store again: this is where a key returns after a logout, a crash or a reinstall. */
    fun visit() {
        val current = flow ?: return
        scope.launch {
            withContext(io) { current.reload() }
            refresh()
        }
    }

    // ---- the card

    /** The one tap. With a consent in force the warning comes first. */
    fun setUpPayouts() {
        val current = flow ?: return
        if (_card.value.replacesPayoutWallet) {
            _pendingReplacement.value = PendingReplacement.SET_UP
        } else {
            current.setUpPayouts(false)
        }
    }

    fun saveFirst() {
        val current = flow ?: return
        if (_card.value.replacesPayoutWallet) {
            _pendingReplacement.value = PendingReplacement.SAVE_FIRST
        } else {
            makeSaveNowKey(current, replaceConfirmed = false)
        }
    }

    fun finishSetup() {
        val current = flow ?: return
        if (_card.value.replacesPayoutWallet) {
            _pendingReplacement.value = PendingReplacement.FINISH_SETUP
        } else {
            current.finishSetup(false)
        }
    }

    /** The user read the warning and replaces the payout wallet. */
    fun replacementConfirmed() {
        val pending = _pendingReplacement.value
        _pendingReplacement.value = null
        val current = flow ?: return
        when (pending) {
            PendingReplacement.SET_UP -> current.setUpPayouts(true)
            PendingReplacement.SAVE_FIRST -> makeSaveNowKey(current, replaceConfirmed = true)
            PendingReplacement.FINISH_SETUP -> current.finishSetup(true)
            null -> {}
        }
    }

    fun dismissReplacement() {
        _pendingReplacement.value = null
    }

    // the key is made in memory, which takes a moment
    private fun makeSaveNowKey(current: BittensorKeyFlowing, replaceConfirmed: Boolean) {
        scope.launch {
            withContext(io) { current.saveFirst(replaceConfirmed) }
            refresh()
        }
    }

    fun openKeyScreen() {
        val current = flow ?: return
        scope.launch {
            withContext(io) { current.openScreen() }
            refresh()
        }
    }

    // ---- the Key screen

    /** Tap to reveal. A stored key asks for device authentication first. */
    suspend fun tapToReveal(authenticator: BittensorDeviceAuthenticating) {
        val current = flow ?: return
        val authenticated = if (_screen.value.needsAuthentication) authenticator.authenticate() else false
        withContext(io) { current.reveal(authenticated) }
        refresh()
    }

    fun hide() {
        flow?.hide()
        refresh()
    }

    /** The app left the foreground: the words hide. */
    fun leftForeground() {
        hide()
    }

    /** Copy. The flow answers only while the words show, so this can't copy while hidden. */
    fun copy(pasteboard: BittensorKeyPasteboarding) {
        val phrase = flow?.phraseToCopy ?: return
        if (phrase.isEmpty()) {
            return
        }
        pasteboard.copy(phrase)
        _copied.value = true
        copiedJob?.cancel()
        copiedJob = scope.launch {
            delay(COPIED_SHOWN_MILLIS)
            _copied.value = false
        }
    }

    /**
     * I have saved the key, it's OK to delete it now: what deleting means
     * comes first. The words hide before it shows, so none can be read
     * behind it.
     */
    fun explainDelete() {
        hide()
        _deleteExplained.value = true
    }

    fun cancelDelete() {
        _deleteExplained.value = false
    }

    /** Continue: two of the words are asked for. */
    fun confirmDelete() {
        _deleteExplained.value = false
        val current = flow ?: return
        scope.launch {
            withContext(io) { current.confirmDelete() }
            refresh()
        }
    }

    /**
     * I've saved it. Keep it on this device: two of the words, and no device
     * authentication, since keeping shows and deletes nothing. The flow hides
     * the words first.
     */
    fun confirmKeep() {
        val current = flow ?: return
        scope.launch {
            withContext(io) { current.confirmKeep() }
            refresh()
        }
    }

    fun cancelConfirmation() {
        flow?.cancelConfirmation()
        refresh()
    }

    /** The two words as the user types them. */
    fun checkWords(first: String, second: String): BittensorKeyWordCheckInfo =
        flow?.checkWords(first, second) ?: BittensorKeyWordCheckInfo()

    /**
     * The confirmation's button. Deleting a stored key asks for device
     * authentication first, at the button, after the words; only an
     * authenticated answer reaches the flow.
     */
    suspend fun submitWords(first: String, second: String, authenticator: BittensorDeviceAuthenticating) {
        val current = flow ?: return
        var authenticated = false
        if (_screen.value.confirmNeedsAuthentication) {
            authenticated = authenticator.authenticate()
            if (!authenticated) {
                return
            }
        }
        current.submitWords(first, second, authenticated)
    }

    fun retry() {
        flow?.retrySaveNow()
    }

    /** Leaving discards a phrase in memory: no earnings went to it. */
    fun closeScreen() {
        flow?.closeScreen()
        refresh()
    }

    /**
     * The disclaimer a screen of the easy flows shows, for a
     * BittensorFundsDisclaimerPlace*. With no flow for a network yet the sdk
     * is still asked, through a flow with no session and no store, which is
     * what such a place shows with no key here.
     */
    fun fundsDisclaimerAt(place: String): String {
        flow?.let {
            return it.fundsDisclaimerAt(place)
        }
        val standIn = Sdk.newBittensorLocalKeyFlow(null, "", null) ?: return Sdk.BittensorFundsDisclaimerNone
        return try {
            standIn.fundsDisclaimerAt(place) ?: Sdk.BittensorFundsDisclaimerNone
        } finally {
            standIn.close()
        }
    }

    companion object {
        // how long Copy reads Copied
        private const val COPIED_SHOWN_MILLIS = 2_000L
    }
}
