package com.bringyour.network.ui.account

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

/**
 * Import extenders (EXTENDER.md K7, K8): a scanned code, a chosen photo or
 * pasted text. The sdk decodes and applies the payload; this screen only
 * shows what it says and asks the questions K7 requires — the operator
 * settings before they replace this space's, and the refusal of another
 * operator's network when they are not taken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExtendersScreen(
    navController: NavController,
    viewModel: ExtendersViewModel = hiltViewModel(),
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var payload by remember { mutableStateOf("") }
    var decoded by remember { mutableStateOf<ExtenderDecodeUi?>(null) }
    var useSettings by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var confirmSettingsHost by remember { mutableStateOf<String?>(null) }
    // the last thing that happened, as a localized line under the actions
    var message by remember { mutableStateOf<String?>(null) }

    val onPayload: (String) -> Unit = { text ->
        payload = text
        val result = viewModel.decodeShare(text)
        decoded = result
        useSettings = false
        scanning = false
        message = if (result.ok) {
            null
        } else {
            context.getString(extenderImportErrorResId(result.errorKey), result.networkHost)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scanning = true
        } else {
            message = context.getString(R.string.camera_permission_needed)
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        val text = decodeExtenderQrFromImage(context, uri)
        if (text == null) {
            decoded = null
            message = context.getString(R.string.qr_code_not_found)
        } else {
            onPayload(text)
        }
    }

    val runImport: (Boolean) -> Unit = { withSettings ->
        val result = viewModel.importShare(payload, withSettings)
        message = if (result.ok) {
            context.resources.getQuantityString(
                R.plurals.import_extenders_imported,
                result.importedCount,
                result.importedCount,
            )
        } else {
            context.getString(
                extenderImportErrorResId(result.errorKey),
                decoded?.networkHost ?: "",
            )
        }
        if (result.ok) {
            decoded = null
            payload = ""
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.import_extenders),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp)),
        ) {

            if (scanning) {
                ExtenderQrPreview(onDecoded = onPayload)

                Spacer(modifier = Modifier.height(16.dp))
            }

            URButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        message = null
                        scanning = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            ) { style ->
                Text(stringResource(id = R.string.scan_qr_code), style = style)
            }

            Spacer(modifier = Modifier.height(12.dp))

            URButton(
                onClick = {
                    message = null
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                style = ButtonStyle.SECONDARY,
            ) { style ->
                Text(stringResource(id = R.string.choose_photo), style = style)
            }

            Spacer(modifier = Modifier.height(12.dp))

            URButton(
                onClick = {
                    val text = clipboardManager.getText()?.text.orEmpty().trim()
                    if (text.isEmpty()) {
                        decoded = null
                        message = context.getString(R.string.import_extenders_invalid)
                    } else {
                        onPayload(text)
                    }
                },
                style = ButtonStyle.SECONDARY,
            ) { style ->
                Text(stringResource(id = R.string.paste_share_text), style = style)
            }

            Spacer(modifier = Modifier.height(24.dp))

            val decodedShare = decoded
            if (decodedShare != null && decodedShare.ok) {

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    pluralStringResource(
                        id = R.plurals.share_extenders_count,
                        count = decodedShare.count,
                        decodedShare.count,
                    ),
                )

                if (extenderUseSettingsOffered(decodedShare)) {

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(id = R.string.use_extender_settings))
                        Spacer(modifier = Modifier.width(16.dp))
                        URSwitch(
                            checked = useSettings,
                            toggle = { useSettings = !useSettings },
                        )
                    }
                }

                val step = extenderImportStep(decodedShare, useSettings)
                if (step is ExtenderImportStep.ForeignHost) {

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(
                            id = R.string.import_extenders_foreign_host,
                            step.networkHost,
                        ),
                        style = TextStyle(fontSize = 12.sp),
                        color = TextMuted,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                URButton(
                    onClick = {
                        when (step) {
                            // K7 asks before the payload's operator replaces
                            // this space's
                            is ExtenderImportStep.ConfirmSettings ->
                                confirmSettingsHost = step.settingsHost
                            ExtenderImportStep.Ready -> runImport(false)
                            // a foreign payload with no settings taken is
                            // refused outright
                            is ExtenderImportStep.ForeignHost -> message =
                                context.getString(
                                    R.string.import_extenders_foreign_host,
                                    step.networkHost,
                                )
                            // and an undecodable one has nothing to import
                            ExtenderImportStep.Invalid -> message =
                                context.getString(R.string.import_extenders_invalid)
                        }
                    },
                ) { style ->
                    Text(stringResource(id = R.string.import_extenders), style = style)
                }
            }

            message?.let { line ->

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    line,
                    style = TextStyle(fontSize = 12.sp),
                    color = TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // the settings confirmation: which operator host the payload would put in
    // force here (K7)
    val settingsHost = confirmSettingsHost
    URDialog(
        visible = settingsHost != null,
        onDismiss = { confirmSettingsHost = null },
    ) {
        Column {
            Text(
                stringResource(
                    id = R.string.import_extenders_confirm_settings,
                    settingsHost.orEmpty(),
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                URButton(
                    onClick = { confirmSettingsHost = null },
                    style = ButtonStyle.SECONDARY,
                ) { style ->
                    Text(stringResource(id = R.string.cancel), style = style)
                }

                Spacer(modifier = Modifier.width(8.dp))

                URButton(
                    onClick = {
                        confirmSettingsHost = null
                        runImport(true)
                    },
                ) { style ->
                    Text(stringResource(id = R.string.import_extenders), style = style)
                }
            }
        }
    }
}

/**
 * The camera preview with the zxing frame analyzer behind it (K8). The
 * analyzer keeps reading until this leaves the composition, which the first
 * decoded payload causes.
 */
@Composable
private fun ExtenderQrPreview(
    onDecoded: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context) }

    DisposableEffect(controller, lifecycleOwner) {
        // analysis only: the default controller also binds image and video
        // capture, which this screen never uses and which would make the
        // scanner depend on permissions it has no business asking for
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            ExtenderQrAnalyzer(onDecoded),
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
