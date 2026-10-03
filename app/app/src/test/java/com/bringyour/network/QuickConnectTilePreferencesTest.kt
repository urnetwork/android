package com.bringyour.network

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Long-pressing a Quick Settings tile starts the app's activity for
 * `android.service.quicksettings.action.QS_TILE_PREFERENCES`. Without one, the
 * system opens the app info page instead of the app.
 */
class QuickConnectTilePreferencesTest {
    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val QS_TILE_PREFERENCES = "android.service.quicksettings.action.QS_TILE_PREFERENCES"

        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private val manifestCandidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("app/app/src/main/AndroidManifest.xml"),
        )
    }

    private fun activities(): List<Element> {
        val manifest = manifestCandidates.first { it.isFile }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(manifest)
        val nodes = document.getElementsByTagName("activity")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.handles(action: String, category: String? = null): Boolean {
        return childElements("intent-filter").any { filter ->
            filter.childElements("action").any { it.getAttributeNS(ANDROID_NS, "name") == action } &&
                (category == null || filter.childElements("category").any { it.getAttributeNS(ANDROID_NS, "name") == category })
        }
    }

    @Test
    fun theLauncherActivityHandlesTileLongPress() {
        val tileActivities = activities().filter { it.handles(QS_TILE_PREFERENCES, "android.intent.category.DEFAULT") }
        assertEquals("activities handling $QS_TILE_PREFERENCES", 1, tileActivities.size)

        val tileActivity = tileActivities.single()
        assertEquals("true", tileActivity.getAttributeNS(ANDROID_NS, "exported"))
        assertTrue(
            "the tile long-press opens the app's launcher activity",
            tileActivity.handles("android.intent.action.MAIN", "android.intent.category.LAUNCHER"),
        )
    }
}
