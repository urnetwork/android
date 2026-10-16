package com.bringyour.network.ui.stats

/**
 * Whether a settings editor's Update applies (the DNS and transport editors):
 * only while the settings it opened on (`original`) are still the ones in
 * force (`current`), a compare and set. Otherwise the settings changed under
 * the editor (another surface, the sdk's own normalization, a new device),
 * and the editor re-seeds from `current` instead of overwriting them with a
 * form edited from older ones. Unknown settings never apply: an editor opened
 * on a blank or default form while they were unknown would replace the real
 * resolver or policy (as the Apple app's editors, 2026-10-16).
 */
internal fun <T> settingsEditApplies(original: T?, current: T?): Boolean =
    original != null && current != null && original == current
