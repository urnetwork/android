package com.bringyour.network.acceptance

import java.util.regex.Pattern

/** Selects only a completed second-UID egress probe result. */
internal object EgressProbeResult {
    val terminalText: Pattern = Pattern.compile("^ACCEPTANCE_(?:IP|ERROR)=.+$")
}
