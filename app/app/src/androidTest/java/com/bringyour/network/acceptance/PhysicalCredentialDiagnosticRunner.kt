package com.bringyour.network.acceptance

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Explicitly selected, test-APK-only observer; the default runner is unchanged. */
class PhysicalCredentialDiagnosticRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        withPhysicalCredentialRunnerCheckpoints(
            enabled = physicalCredentialDiagnosticsEnabled(arguments?.getString(PHYSICAL_CREDENTIAL_DIAGNOSTICS_ARGUMENT)),
            checkpoint = { stage -> PhysicalCredentialDiagnosticRecorder.checkpoint(targetContext, stage) },
            createRunner = { super.onCreate(arguments) },
        )
    }
}
