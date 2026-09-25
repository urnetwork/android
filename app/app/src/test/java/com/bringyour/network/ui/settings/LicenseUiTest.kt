package com.bringyour.network.ui.settings

import com.bringyour.network.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseUiTest {

    private fun license(version: String = "", spdx: String = "", kind: String = "software") = LicenseUi(
        name = "x",
        version = version,
        kind = kind,
        url = "",
        spdx = spdx,
        copyright = "",
        notice = "",
        text = "",
    )

    @Test
    fun subtitleOmitsEmptyParts() {
        assertEquals("1.2.3 · MIT", license(version = "1.2.3", spdx = "MIT").subtitle)
        assertEquals("MIT", license(spdx = "MIT").subtitle)
        assertEquals("1.2.3", license(version = "1.2.3").subtitle)
        assertEquals("", license().subtitle)
    }

    @Test
    fun onlyDataKindIsAnAttribution() {
        assertTrue(license(kind = "data").isData)
        assertFalse(license(kind = "software").isData)
        assertFalse(license(kind = "font").isData)
    }

    @Test
    fun licenseRoutesResolveDistinctly() {
        // Route.fromString matches by qualified-name containment
        val licenses = "com.bringyour.network.ui.Route.Licenses"
        val detail = "com.bringyour.network.ui.Route.LicenseDetail/{index}"
        assertEquals(Route.Licenses, Route.fromString(licenses))
        assertEquals(null, Route.fromString(detail))
    }
}
