package com.bringyour.network.acceptance;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/**
 * A deliberately tiny, second-UID data-plane probe for the acceptance test.
 *
 * <p>MainService excludes com.bringyour.network from the VPN, as every Android
 * VPN must do to avoid recursively tunnelling itself. An HTTP request made by
 * the instrumentation process would therefore always use the physical path.
 * This activity is packaged as com.bringyour.network.test and its request is
 * captured by the VPN exactly like traffic from an ordinary installed app.
 *
 * <p>This class intentionally uses only Android platform and Java runtime
 * classes. The instrumentation APK normally omits Kotlin classes supplied by
 * the target APK. Because Android starts this exported activity as the test
 * APK's standalone application rather than inside the instrumentation
 * process, it cannot borrow the target APK's Kotlin runtime.
 */
public final class EgressProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView result = new TextView(this);
        result.setGravity(Gravity.CENTER);
        result.setBackgroundColor(Color.BLACK);
        result.setTextColor(Color.WHITE);
        result.setTextSize(18f);
        result.setText("ACCEPTANCE_IP_PENDING");
        setContentView(result);

        Thread probe = new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    message = "ACCEPTANCE_IP=" + EgressProbeClient.queryPublicIp();
                } catch (Throwable error) {
                    String detail = error.getMessage();
                    if (detail == null) {
                        detail = "unknown";
                    }
                    message = "ACCEPTANCE_ERROR="
                        + error.getClass().getSimpleName()
                        + ":"
                        + detail;
                }

                final String finalMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        result.setText(finalMessage);
                    }
                });
            }
        }, "acceptance-egress-probe");
        probe.setDaemon(true);
        probe.start();
    }
}
