package dev.brandonvargas.veganscanner.android.feature.label

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.brandonvargas.veganscanner.feature.scanner.domain.rules.OcrLine

/**
 * Takes a photo with CameraX and reads it with ML Kit's on-device Latin text recognizer.
 * The photo stays in memory and is discarded right after recognition; only the text is kept.
 */
class LabelCamera(private val context: Context) {
    val controller =
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            // Small print needs the sharpest photo; the extra capture time is fine for a single shot.
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Recognized lines are positioned on the upright photo so shared code can follow the ingredient column. */
    fun capture(onText: (List<OcrLine>) -> Unit, onFailure: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val input = InputImage.fromBitmap(image.toBitmap(), rotation)
                    image.close()
                    // ML Kit reports boxes on the rotated (upright) image.
                    val sideways = rotation % 180 != 0
                    val width = if (sideways) input.height else input.width
                    val height = if (sideways) input.width else input.height
                    recognizer.process(input)
                        .addOnSuccessListener { onText(it.lines(width.toFloat(), height.toFloat())) }
                        .addOnFailureListener { onFailure() }
                }

                override fun onError(exception: ImageCaptureException) = onFailure()
            },
        )
    }

    fun close() = recognizer.close()

    private fun Text.lines(width: Float, height: Float): List<OcrLine> =
        textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, box.left / width, box.top / height, box.right / width, box.bottom / height)
        }
}

@Composable
fun rememberLabelCamera(): LabelCamera {
    val context = LocalContext.current
    val camera = remember { LabelCamera(context) }
    DisposableEffect(camera) { onDispose { camera.close() } }
    return camera
}

@Composable
fun LabelCameraPreview(camera: LabelCamera, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, camera) {
        camera.controller.bindToLifecycle(lifecycleOwner)
        onDispose { camera.controller.unbind() }
    }
    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = camera.controller
            }
        },
        modifier = modifier,
    )
}
