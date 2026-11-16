package com.bringyour.network.ui.upgrade

import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The F-Droid build's pay page web view (PayPageWebView) and a bank's 3D Secure
 * window. The page here stands in for ur.io's pay page: the bank's challenge is a
 * frame of another origin on it (as Stripe.js puts it), which opens a window on the
 * reader's tap; the window reports back through `window.opener` and closes itself;
 * the challenge tells the page, which hands back through the return url. Offline:
 * the page is loaded from a string, the frame from a data url.
 */
@RunWith(AndroidJUnit4::class)
class PayPageWebViewTest {

    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var container: FrameLayout
    private lateinit var page: WebView
    private val popup = AtomicReference<WebView?>(null)
    private val opened = CountDownLatch(1)
    private val closed = CountDownLatch(1)
    private val returned = CountDownLatch(1)
    private var returns = 0

    @Before
    fun openThePage() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            container = FrameLayout(activity)
            activity.setContentView(container)
            page = PayPageWebView.create(
                context = activity,
                returnUrl = RETURN_URL,
                onReturn = {
                    returns += 1
                    returned.countDown()
                },
                onPopup = { window ->
                    // PayPageDialog shows it over the page
                    container.addView(window, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    popup.set(window)
                    opened.countDown()
                },
                onPopupClosed = { window ->
                    container.removeView(window)
                    window.destroy()
                    closed.countDown()
                },
            )
            container.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            page.loadDataWithBaseURL(PAGE_URL, PAGE, "text/html", "utf-8", null)
        }
        awaitScript(page, "window.payPageReady === true && document.getElementById('challenge').dataset.loaded === '1'", "true")
    }

    @After
    fun closeThePage() {
        scenario.close()
    }

    @Test
    fun aWindowTheChallengeOpensStaysOverThePageAndReportsBack() {
        assertTrue("the challenge's window opened in a web view of its own", tapTheChallenge())

        // the pay page, and the challenge waiting on the window, are still there
        assertEquals("\"$PAGE_URL|true\"", script(page, "location.href + '|' + window.payPageReady"))
        val window = popup.get()!!
        awaitScript(window, "document.getElementById('approve') !== null", "true")
        // no files, no content, no windows of its own
        val settings = AtomicReference<List<Boolean>>()
        scenario.onActivity {
            settings.set(listOf(window.settings.allowFileAccess, window.settings.allowContentAccess, window.settings.supportMultipleWindows()))
        }
        assertEquals(listOf(false, false, false), settings.get())

        // the bank approves: the window reports to its opener and closes itself
        // (after this script has answered, so the closing never races the answer)
        script(window, "setTimeout(function () { document.getElementById('approve').click(); }, 50); 'ok'")

        assertTrue("the window closed itself", closed.await(10, TimeUnit.SECONDS))
        assertTrue("the page handed back through the return url", returned.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(1, returns)
    }

    @Test
    fun aScriptCannotOpenAWindowWithoutATap() {
        // the challenge's own script, with no tap behind it, gets no window
        script(page, "window.frames[0].postMessage('open-now', '*'); 'sent'")
        awaitScript(page, "window.challengeAnswer || ''", "\"blocked\"")
        assertEquals(1L, opened.count)
        assertEquals("\"$PAGE_URL\"", script(page, "location.href"))
    }

    /** Whether the tap opened the challenge's window. */
    private fun tapTheChallenge(): Boolean {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // a tap while the activity is still coming in is lost, so it waits for the
        // window to settle, and taps again only while nothing opened (an open
        // window covers the page, so a second tap never reaches the challenge)
        device.waitForIdle()
        repeat(3) {
            val location = IntArray(2)
            var width = 0
            var height = 0
            scenario.onActivity {
                page.getLocationOnScreen(location)
                width = page.width
                height = page.height
            }
            // the challenge's button fills the page: a real tap, so a user gesture
            device.click(location[0] + width / 2, location[1] + height / 2)
            if (opened.await(3, TimeUnit.SECONDS)) {
                return true
            }
        }
        return false
    }

    private fun script(webView: WebView, js: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        scenario.onActivity {
            webView.evaluateJavascript(js) { value ->
                result.set(value)
                done.countDown()
            }
        }
        assertTrue("script answered: $js", done.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun awaitScript(webView: WebView, js: String, expected: String) {
        val deadline = System.currentTimeMillis() + 15_000
        var last: String? = null
        while (System.currentTimeMillis() < deadline) {
            last = script(webView, js)
            if (last == expected) {
                return
            }
            Thread.sleep(100)
        }
        throw AssertionError("$js answered $last, not $expected")
    }

    private companion object {
        const val PAGE_URL = "https://pay.test/app/pay-sheet"
        const val RETURN_URL = "urnetwork://pay/done"

        // the bank's challenge: a tap opens its window, which approves through
        // window.opener and closes itself; a message with no tap behind it
        // tries the same and reports what it got
        val CHALLENGE = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0">
            <button id="verify" style="width:100vw;height:100vh;font-size:32px">Verify with your bank</button>
            <script>
            function openChallenge() {
              var w = window.open('', 'bank-challenge');
              if (!w) { return false; }
              w.document.write('<!doctype html><body><button id="approve">Approve</button><script>' +
                'document.getElementById("approve").onclick = function () { window.opener.postMessage("approved", "*"); window.close(); };' +
                '<' + '/script></body>');
              w.document.close();
              return true;
            }
            document.getElementById('verify').addEventListener('click', openChallenge);
            window.addEventListener('message', function (e) {
              if (e.data === 'approved') { parent.postMessage('challenge-complete', '*'); }
              if (e.data === 'open-now') { parent.postMessage(openChallenge() ? 'opened' : 'blocked', '*'); }
            });
            parent.postMessage('challenge-loaded', '*');
            </script>
            </body></html>
        """.trimIndent()

        // the pay page: Stripe.js's part is the frame and the hand back
        val PAGE = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0">
            <iframe id="challenge" style="border:0;width:100vw;height:100vh;display:block"
              src="data:text/html;base64,${Base64.encodeToString(CHALLENGE.toByteArray(), Base64.NO_WRAP)}"></iframe>
            <script>
            window.payPageReady = true;
            window.addEventListener('message', function (e) {
              if (e.data === 'challenge-loaded') { document.getElementById('challenge').dataset.loaded = '1'; }
              if (e.data === 'opened' || e.data === 'blocked') { window.challengeAnswer = e.data; }
              if (e.data === 'challenge-complete') { location.assign('$RETURN_URL?status=succeeded'); }
            });
            </script>
            </body></html>
        """.trimIndent()
    }
}
