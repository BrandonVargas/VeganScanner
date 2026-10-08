package dev.brandonvargas.veganscanner.android.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.brandonvargas.veganscanner.R
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerAction
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerEffect
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerUiState
import dev.brandonvargas.veganscanner.feature.scanner.presentation.scanner.ScannerViewModel
import kotlinx.coroutines.flow.drop
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ScannerScreen(onOpenResult: (String) -> Unit, viewModel: ScannerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Text field state stays in the UI (TextFieldState keeps the IME in sync); the ViewModel validates on submit.
    val manualEntry = rememberTextFieldState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentOnOpenResult by rememberUpdatedState(onOpenResult)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScannerEffect.OpenResult -> {
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                    manualEntry.clearText()
                    currentOnOpenResult(effect.barcode)
                }
            }
        }
    }
    LaunchedEffect(manualEntry) {
        snapshotFlow { manualEntry.text.toString() }
            .drop(1)
            .collect { viewModel.onAction(ScannerAction.ManualEntryEdited) }
    }
    ScannerContent(state = state, manualEntry = manualEntry, onAction = viewModel::onAction)
}

/** Digits only, at most GTIN-14 length. The rule itself lives in shared code. */
private val BarcodeInputTransformation =
    InputTransformation {
        val current = asCharSequence().toString()
        val sanitized = ScannerViewModel.sanitizeManualEntry(current)
        if (sanitized != current) replace(0, length, sanitized)
    }

@Composable
fun ScannerContent(
    state: ScannerUiState,
    manualEntry: TextFieldState,
    onAction: (ScannerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasCameraPermission = it
        }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !isPreview) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission && !isPreview) {
            BarcodeCameraPreview(
                onBarcodeScan = { onAction(ScannerAction.BarcodeDetected(it)) },
                modifier = Modifier.fillMaxSize(),
            )
            Viewfinder(Modifier.align(Alignment.Center))
        } else {
            CameraPermissionRationale(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp)) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f)) {
                Text(
                    text = stringResource(R.string.scanner_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        ManualEntryCard(
            state = state,
            textFieldState = manualEntry,
            onSubmit = { onAction(ScannerAction.ManualEntrySubmitted(manualEntry.text.toString())) },
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().padding(16.dp),
        )
    }
}

@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(0.8f)
            .aspectRatio(1.8f)
            .border(BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)), RoundedCornerShape(20.dp)),
    )
}

@Composable
private fun CameraPermissionRationale(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.NoPhotography,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.camera_permission_rationale),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequest) { Text(stringResource(R.string.camera_permission_grant)) }
    }
}

@Composable
private fun ManualEntryCard(
    state: ScannerUiState,
    textFieldState: TextFieldState,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.manual_entry_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    state = textFieldState,
                    modifier = Modifier.weight(1f),
                    inputTransformation = BarcodeInputTransformation,
                    placeholder = { Text(stringResource(R.string.manual_entry_placeholder)) },
                    isError = state.isManualEntryInvalid,
                    supportingText =
                        if (state.isManualEntryInvalid) {
                            { Text(stringResource(R.string.manual_entry_invalid)) }
                        } else {
                            null
                        },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    onKeyboardAction = { onSubmit() },
                )
                Spacer(Modifier.size(8.dp))
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = textFieldState.text.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.manual_entry_submit))
                }
            }
        }
    }
}
