package com.bringyour.network.acceptance;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.ResultReceiver;
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
    public static final String EXTRA_RESULT_RECEIVER = "com.bringyour.network.acceptance.RESULT_RECEIVER";
    public static final String EXTRA_REQUEST_NONCE = "com.bringyour.network.acceptance.REQUEST_NONCE";
    public static final String EXTRA_FINISH_AFTER_RESULT = "com.bringyour.network.acceptance.FINISH_AFTER_RESULT";
    public static final String EXTRA_FIXED_RESULT = "com.bringyour.network.acceptance.FIXED_RESULT";
    public static final String RESULT_MESSAGE = "message";
    public static final String RESULT_NONCE = "nonce";

    private Thread probe;

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

        String fixedResult = getIntent().getStringExtra(EXTRA_FIXED_RESULT);
        if (fixedResult != null) {
            publish(result, fixedResult);
            return;
        }

        probe = new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    message = "ACCEPTANCE_IP=" + EgressProbeClient.queryPublicIp();
                } catch (Exception error) {
                    String detail = error.getMessage();
                    if (detail == null) {
                        detail = "unknown";
                    }
                    message = "ACCEPTANCE_ERROR="
                        + error.getClass().getSimpleName()
                        + ":"
                        + detail;
                }
                publish(result, message);
            }
        }, "acceptance-egress-probe");
        probe.setDaemon(true);
        probe.start();
    }

    @SuppressWarnings("deprecation")
    private void publish(TextView result, String message) {
        ResultReceiver receiver = getIntent().getParcelableExtra(EXTRA_RESULT_RECEIVER);
        String nonce = getIntent().getStringExtra(EXTRA_REQUEST_NONCE);
        boolean finishAfterResult = getIntent().getBooleanExtra(EXTRA_FINISH_AFTER_RESULT, false);

        // Deliver the acceptance result independently of Activity rendering. A
        // system UI ANR or overlay must not hide a completed network probe from
        // the instrumentation process.
        if (receiver != null) {
            Bundle data = new Bundle();
            data.putString(RESULT_MESSAGE, message);
            data.putString(RESULT_NONCE, nonce);
            receiver.send(Activity.RESULT_OK, data);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                result.setText(message);
                if (finishAfterResult) {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        Thread activeProbe = probe;
        if (activeProbe != null) {
            activeProbe.interrupt();
        }
        super.onDestroy();
    }
}
