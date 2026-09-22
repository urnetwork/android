package com.bringyour.network.ui.settings

/**
 * The typed gate on the delete-account dialog.
 *
 * The dialog's button used to be live the moment it opened, one tap past the
 * settings list. Now the user types the network name back and the button only
 * enables once the entry matches, so a stray tap cannot delete an account.
 *
 * The comparison ignores surrounding whitespace and case: soft keyboards
 * append a space after an autocomplete accept and may capitalise the first
 * letter despite the field's hint, and neither is the user changing their
 * mind. Whitespace INSIDE the name is still compared.
 */
object DeleteAccountConfirmation {

    /**
     * Whether the dialog shows the entry field at all.
     *
     * The network name comes from the account view model and can be absent:
     * not answered yet, or answered with nothing to show. Then the dialog
     * falls back to the plain confirm it always had, rather than locking the
     * user out of deleting at all.
     */
    fun requiresTypedName(networkName: String?): Boolean =
        !networkName.isNullOrBlank()

    /**
     * Whether the delete button enables for this entry.
     */
    fun confirms(networkName: String?, typed: String): Boolean {
        if (!requiresTypedName(networkName)) return true
        return typed.trim().equals(networkName!!.trim(), ignoreCase = true)
    }
}
