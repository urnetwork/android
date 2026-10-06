package com.bringyour.network.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.bringyour.network.R

/**
 * Semantics for a row that shows or hides a section below it. TalkBack reads
 * the row's state, expanded or collapsed, which its chevron only draws, and
 * offers the expand or collapse action under its own names. The row's click
 * still toggles it, and the chevron stays decorative.
 */
@Composable
fun Modifier.expandableRow(expanded: Boolean, onToggle: () -> Unit): Modifier {
    val state = stringResource(id = if (expanded) R.string.expanded else R.string.collapsed)
    return this.semantics {
        stateDescription = state
        if (expanded) {
            collapse {
                onToggle()
                true
            }
        } else {
            expand {
                onToggle()
                true
            }
        }
    }
}
