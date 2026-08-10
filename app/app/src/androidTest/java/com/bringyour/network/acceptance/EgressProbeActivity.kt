package com.bringyour.network.acceptance

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

/**
 * A deliberately tiny, second-UID data-plane probe for the acceptance test.
 *
 * MainService excludes com.bringyour.network from its VPN, as every Android
 * VPN must do to avoid recursively tunnelling itself.  An HTTP request made by
 * the instrumentation process would therefore always use the physical path.
 * This activity is packaged as com.bringyour.network.test and its request is
 * captured by the VPN exactly like traffic from an ordinary installed app.
 */
class EgressProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = TextView(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 18f
            text = "ACCEPTANCE_IP_PENDING"
        }
        setContentView(result)

        Thread {
            val message = try {
                val connection = URL("https://checkip.amazonaws.com").openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                try {
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "HTTP ${connection.responseCode}"
                    }
                    val address = connection.inputStream.bufferedReader().use { it.readText().trim() }
                    check(address.matches(Regex("[0-9a-fA-F:.]+"))) { "invalid address response" }
                    "ACCEPTANCE_IP=$address"
                } finally {
                    connection.disconnect()
                }
            } catch (error: Throwable) {
                "ACCEPTANCE_ERROR=${error.javaClass.simpleName}:${error.message ?: "unknown"}"
            }

            runOnUiThread { result.text = message }
        }.apply {
            name = "acceptance-egress-probe"
            isDaemon = true
            start()
        }
    }
}
