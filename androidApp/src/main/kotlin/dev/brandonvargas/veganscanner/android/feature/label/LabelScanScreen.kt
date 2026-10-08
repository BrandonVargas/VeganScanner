package dev.brandonvargas.veganscanner.android.feature.label

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.brandonvargas.veganscanner.R
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanAction
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanEffect
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanError
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanStep
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanUiState
import dev.brandonvargas.veganscanner.feature.scanner.presentation.label.LabelScanViewModel
import kotlinx.coroutines.flow.drop
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LabelScanScreen(
    barcode: String,
    onBack: () -> Unit,
    onShowResult: (String) -> Unit,
    viewModel: LabelScanViewModel = koinViewModel { parametersOf(barcode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reviewText = rememberTextFieldState()
    val currentOnShowResult by rememberUpdatedState(onShowResult)

    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LabelScanEffect.ShowResult -> {
                    keyboard?.hide()
                    currentOnShowResult(effect.barcode)
                }
            }
        }
    }
    // Load recognized text into the field whenever the ViewModel produces a new draft.
    LaunchedEffect(state.draft?.revision) {
        state.draft?.let { reviewText.setTextAndPlaceCursorAtEnd(it.text) }
    }
    LaunchedEffect(reviewText) {
        snapshotFlow { reviewText.text.toString() }
            .drop(1)
            .collect { viewModel.onAction(LabelScanAction.TextEdited) }
    }

    LabelScanContent(state = state, reviewText = reviewText, onAction = viewModel::onAction, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelScanContent(
    state: LabelScanUiState,
    reviewText: TextFieldState,
    onAction: (LabelScanAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.step) {
                LabelScanStep.CAPTURE -> LabelCapture(state, onAction)
                LabelScanStep.REVIEW -> LabelReview(state, reviewText, onAction)
            }
        }
    }
}

@Composable
private fun LabelCapture(state: LabelScanUiState, onAction: (LabelScanAction) -> Unit) {
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission && !isPreview) {
            val camera = rememberLabelCamera()
            LabelCameraPreview(camera, Modifier.fillMaxSize())
            CaptureControls(
                state = state,
                onCapture = {
                    onAction(LabelScanAction.CaptureStarted)
                    camera.capture(
                        onText = { onAction(LabelScanAction.TextRecognized(it)) },
                        onFailure = { onAction(LabelScanAction.RecognitionFailed) },
                    )
                },
                onTypeManually = { onAction(LabelScanAction.TypeManually) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.camera_permission_rationale_label),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.camera_permission_grant))
                }
                TextButton(onClick = { onAction(LabelScanAction.TypeManually) }) {
                    Text(stringResource(R.string.label_type_instead), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CaptureControls(
    state: LabelScanUiState,
    onCapture: () -> Unit,
    onTypeManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.6f)) {
            Text(
                text =
                    stringResource(
                        if (state.error ==
                            LabelScanError.NOTHING_RECOGNIZED
                        ) {
                            R.string.label_nothing_recognized
                        } else {
                            R.string.label_scan_hint
                        },
                    ),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        LargeFloatingActionButton(onClick = { if (!state.isRecognizing) onCapture() }) {
            if (state.isRecognizing) {
                CircularProgressIndicator(Modifier.size(36.dp))
            } else {
                Icon(
                    Icons.Rounded.CameraAlt,
                    contentDescription = stringResource(R.string.label_capture),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        TextButton(onClick = onTypeManually) {
            Text(stringResource(R.string.label_type_instead), color = Color.White)
        }
    }
}

@Composable
private fun LabelReview(state: LabelScanUiState, reviewText: TextFieldState, onAction: (LabelScanAction) -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.label_review_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.label_review_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            state = reviewText,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.label_review_placeholder)) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 6),
            isError = state.error == LabelScanError.TEXT_TOO_SHORT,
            supportingText =
                if (state.error == LabelScanError.TEXT_TOO_SHORT) {
                    { Text(stringResource(R.string.label_text_too_short)) }
                } else {
                    null
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onAction(LabelScanAction.Retake) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.label_retake))
            }
            Button(
                onClick = { onAction(LabelScanAction.Submit(reviewText.text.toString())) },
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.label_check))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.label_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
