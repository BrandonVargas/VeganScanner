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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Takes a photo with CameraX and reads it with ML Kit's on-device Latin text recognizer.
 * The photo stays in memory and is discarded right after recognition; only the text is kept.
 */
class LabelCamera(private val context: Context) {
    val controller =
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun capture(onText: (String) -> Unit, onFailure: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val input = InputImage.fromBitmap(image.toBitmap(), image.imageInfo.rotationDegrees)
                    image.close()
                    recognizer.process(input)
                        .addOnSuccessListener { onText(it.text) }
                        .addOnFailureListener { onFailure() }
                }

                override fun onError(exception: ImageCaptureException) = onFailure()
            },
        )
    }

    fun close() = recognizer.close()
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
