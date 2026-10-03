package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockActionSafetyRuleTest {
    private fun row(
        safetyRule: Boolean,
        routeLocalOverridable: Boolean,
        overrideId: String? = null,
        hasBlockOverride: Boolean = false,
        hasRouteOverride: Boolean = false,
        hosts: List<String> = listOf("a.unrecognized.test"),
        ips: List<String> = listOf("192.0.2.1"),
    ) = BlockActionUi(
        id = "row", timeMillis = 1, hosts = hosts, ips = ips,
        matchedHosts = emptyList(), matchedIps = emptyList(),
        hostBaseNames = emptyList(), block = true, local = false,
        hasBlockOverride = hasBlockOverride, hasRouteOverride = hasRouteOverride,
        overrideId = overrideId, byteCount = 0,
        safetyRule = safetyRule, routeLocalOverridable = routeLocalOverridable,
    )

    @Test
    fun overridableSafetyRuleOffersRouteLocal() {
        // security-encrypted and security-port
        val action = row(safetyRule = true, routeLocalOverridable = true)
        assertTrue(action.offersRouteLocal)
        val target = routeLocalEditorTarget(action)!!
        assertEquals(listOf("a.unrecognized.test", "192.0.2.1"), target.candidates)
        assertEquals(setOf("a.unrecognized.test", "192.0.2.1"), target.selected)
        assertNull(target.ruleId)
    }

    @Test
    fun nonOverridableSafetyRuleNeverOffersRouteLocal() {
        // security-bittorrent, security-ip, security-smtp, security
        val action = row(safetyRule = true, routeLocalOverridable = false)
        assertTrue(action.safetyRule)
        assertFalse(action.offersRouteLocal)
        assertNull(routeLocalEditorTarget(action))
    }

    @Test
    fun ordinaryActionsAreNotSafetyRules() {
        // blocker, override, and provider-routed traffic
        val action = row(safetyRule = false, routeLocalOverridable = false)
        assertFalse(action.safetyRule)
        assertFalse(action.offersRouteLocal)
        assertNull(routeLocalEditorTarget(action))
    }

    @Test
    fun anExistingOverrideSuppressesRouteLocal() {
        listOf(
            row(safetyRule = true, routeLocalOverridable = true, overrideId = "rule"),
            row(safetyRule = true, routeLocalOverridable = true, hasBlockOverride = true),
            row(safetyRule = true, routeLocalOverridable = true, hasRouteOverride = true),
        ).forEach { action ->
            assertFalse(action.offersRouteLocal)
            assertNull(routeLocalEditorTarget(action))
        }
    }

    @Test
    fun noHostValuesSuppressesRouteLocal() {
        val action = row(
            safetyRule = true,
            routeLocalOverridable = true,
            hosts = emptyList(),
            ips = emptyList(),
        )
        assertFalse(action.offersRouteLocal)
        assertNull(routeLocalEditorTarget(action))
    }

    @Test
    fun projectionKeepsTheSafetyRuleThroughRowAndExitRefreshes() {
        var input = listOf(row(safetyRule = true, routeLocalOverridable = true))
        var exits: Map<String, Set<String>> = emptyMap()
        val projection = BlockActionsProjection(
            readRows = { input },
            readExits = { exits },
            collapseHosts = { it },
        )
        val first = projection.refreshRows().single()
        assertTrue(first.safetyRule)
        assertTrue(first.offersRouteLocal)

        exits = mapOf("192.0.2.1" to setOf("exit"))
        val ticked = projection.refreshExits().single()
        assertEquals(listOf("exit"), ticked.exitShortIds)
        assertTrue(ticked.safetyRule)
        assertTrue(ticked.routeLocalOverridable)

        // a rule now decides the same cluster
        input = listOf(
            row(safetyRule = true, routeLocalOverridable = true, overrideId = "rule", hasRouteOverride = true)
        )
        val decided = projection.refreshRows().single()
        assertTrue(decided.safetyRule)
        assertFalse(decided.offersRouteLocal)
    }
}
