package com.bringyour.network.ui.shared.models

/**
 * A section's load: its spinner, its content, or an error with Try again.
 *
 * The account points, earnings wallet and referral code sections used to have
 * only "loaded or not". A failed fetch either left the spinner up for good
 * (referral code, points with no api) or was shown as real data (0 points, a
 * "connect wallet" offer). `Failed` is shown only while there is nothing
 * fetched to show; a failed refresh keeps what is already on screen.
 */
enum class SectionLoad {
    Loading,
    Loaded,
    Failed;

    companion object {
        fun afterFetch(failed: Boolean, hasContent: Boolean): SectionLoad = when {
            !failed -> Loaded
            hasContent -> Loaded
            else -> Failed
        }

        /** Try again puts the spinner back while the retry runs. */
        fun retrying(current: SectionLoad): SectionLoad =
            if (current == Failed) Loading else current
    }
}

/**
 * The account points section after a fetch. `failed` covers a transport
 * error and a missing api.
 */
fun accountPointsLoadAfterFetch(failed: Boolean, loadedBefore: Boolean): SectionLoad =
    SectionLoad.afterFetch(failed = failed, hasContent = loadedBefore)

/**
 * The referral code section after a fetch. A reply without a code counts as
 * a failure; a code already shown stays through a failed poll.
 */
fun referralCodeLoadAfterFetch(failed: Boolean, code: String?, shownCode: String): SectionLoad =
    SectionLoad.afterFetch(
        failed = failed || code.isNullOrBlank(),
        hasContent = shownCode.isNotBlank(),
    )
