package com.bringyour.network.acceptance

/**
 * Pure parts of the host-commanded insufficient-balance session that MAIN's
 * test-insufficient-balance-driver drives. The host writes one
 * `insufficient-balance-command` record `ID|VERB|ARG` at a time; the session
 * answers in `insufficient-balance-status` with `key=value` lines that the
 * host converts to the driver's JSON protocol. Values never carry credentials.
 */

internal const val INSUFFICIENT_BALANCE_NOTIFICATION_ID = 102

internal const val INSUFFICIENT_BALANCE_ALERT_TAG = "acceptance.insufficient_balance_notice"
internal const val INSUFFICIENT_BALANCE_UPGRADE_TAG = "acceptance.insufficient_balance_upgrade"
internal const val INSUFFICIENT_BALANCE_DISCONNECT_TAG = "acceptance.disconnect"
internal const val INSUFFICIENT_BALANCE_CONNECT_TAG = "acceptance.connect"

internal enum class InsufficientBalanceVerb(val wireValue: String) {
    OBSERVE("observe"),
    CONNECT("connect"),
    EGRESS("egress"),
    TRAFFIC("traffic"),
    PRESS_DISCONNECT("press-disconnect"),
    KILL_SWITCH("kill-switch"),
    FINISH("finish"),
}

internal data class InsufficientBalanceCommand(
    val id: String,
    val verb: InsufficientBalanceVerb,
    val argument: String,
)

private val commandIdPattern = Regex("[A-Za-z0-9._-]{1,32}")

/** Rejects anything but the closed verb set; kill-switch requires on or off. */
internal fun parseInsufficientBalanceCommand(text: String): InsufficientBalanceCommand {
    val parts = text.trim().split('|')
    require(parts.size == 3) { "invalid insufficient-balance command" }
    val (id, verbText, argument) = parts
    require(commandIdPattern.matches(id)) { "invalid insufficient-balance command id" }
    val verb = InsufficientBalanceVerb.entries.firstOrNull { it.wireValue == verbText }
        ?: throw IllegalArgumentException("unsupported insufficient-balance command")
    when (verb) {
        InsufficientBalanceVerb.KILL_SWITCH ->
            require(argument == "on" || argument == "off") { "kill-switch takes on or off" }
        else -> require(argument.isEmpty()) { "${verb.wireValue} takes no argument" }
    }
    return InsufficientBalanceCommand(id, verb, argument)
}

/**
 * Counts posts of the insufficient-balance notification since the session
 * started. Every notify() gives the record a new post time, so distinct post
 * times of the notification id are distinct posts; a record still showing on a
 * later sample is not counted again. Thread safe.
 */
internal class InsufficientBalanceNotificationCounter(
    private val notificationId: Int = INSUFFICIENT_BALANCE_NOTIFICATION_ID,
) {
    private val postTimes = mutableSetOf<Long>()

    /** One sample of the app's active notifications as (id, post time) pairs. */
    @Synchronized
    fun sample(activeNotifications: List<Pair<Int, Long>>) {
        for ((id, postTime) in activeNotifications) {
            if (id == notificationId) postTimes.add(postTime)
        }
    }

    @Synchronized
    fun count(): Int = postTimes.size
}

internal data class InsufficientBalanceObservation(
    val connectRequested: Boolean,
    val connected: Boolean,
    val alert: Boolean,
    val disconnectVisible: Boolean,
    val upgradeVisible: Boolean,
    val notifications: Int,
)

internal fun InsufficientBalanceObservation.statusFields(): List<Pair<String, String>> = listOf(
    "connect_requested" to connectRequested.toString(),
    "connected" to connected.toString(),
    "alert" to alert.toString(),
    "disconnect_visible" to disconnectVisible.toString(),
    "upgrade_visible" to upgradeVisible.toString(),
    "notifications" to notifications.toString(),
)

private val statusKeyPattern = Regex("[a-z_]{1,32}")

/** One line, bounded, so an error message cannot forge another status line. */
internal fun insufficientBalanceStatusValue(value: String): String =
    value.replace(Regex("[\\r\\n\\u0000-\\u001f]"), " ").take(240)

/**
 * The status record: `command`, `state` (ready, running, complete, failed),
 * then the command's fields in order.
 */
internal fun insufficientBalanceStatusText(
    commandId: String,
    state: String,
    fields: List<Pair<String, String>> = emptyList(),
): String = buildString {
    append("command=").append(insufficientBalanceStatusValue(commandId)).append('\n')
    append("state=").append(insufficientBalanceStatusValue(state)).append('\n')
    for ((key, value) in fields) {
        require(statusKeyPattern.matches(key)) { "invalid status key" }
        append(key).append('=').append(insufficientBalanceStatusValue(value)).append('\n')
    }
}
