package dev.brandonvargas.veganscanner.android.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import dev.brandonvargas.veganscanner.feature.scanner.domain.research.OnDeviceLanguageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gemini Nano through ML Kit's Prompt API (Android AICore). Runs on the phone, so it also works offline; only
 * on supported devices. When the model can be downloaded, the first availability check starts the download in
 * [scope] and the model is used once AICore reports it ready.
 */
class GeminiNanoModel(private val scope: CoroutineScope) : OnDeviceLanguageModel {
    private val model by lazy { Generation.getClient() }
    private val downloadStarted = AtomicBoolean(false)

    override suspend fun isAvailable(): Boolean =
        when (val status = runCatching { model.checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)) {
            FeatureStatus.AVAILABLE -> {
                true
            }

            FeatureStatus.DOWNLOADABLE -> {
                startDownload()
                false
            }

            else -> {
                Log.d(TAG, "Gemini Nano not available (status $status)")
                false
            }
        }

    override suspend fun generate(instructions: String, prompt: String): String? {
        val request =
            if (model.isSystemPromptAvailable()) {
                generateContentRequest(SystemInstruction(instructions), TextPart(prompt)) { settings() }
            } else {
                generateContentRequest(TextPart("$instructions\n\n$prompt")) { settings() }
            }
        return model.generateContent(request).candidates.firstOrNull()?.text
    }

    private fun com.google.mlkit.genai.prompt.GenerateContentRequest.Builder.settings() {
        temperature = 0f
        topK = 1
        maxOutputTokens = MAX_OUTPUT_TOKENS
    }

    private fun startDownload() {
        if (!downloadStarted.compareAndSet(false, true)) return
        scope.launch {
            runCatching {
                model.download()
                    .takeWhile { it !is DownloadStatus.DownloadCompleted && it !is DownloadStatus.DownloadFailed }
                    .collect {}
                Log.i(TAG, "Gemini Nano download finished")
            }.onFailure {
                downloadStarted.set(false)
                Log.w(TAG, "Gemini Nano download failed", it)
            }
        }
    }

    private companion object {
        const val TAG = "GeminiNano"
        const val MAX_OUTPUT_TOKENS = 160
    }
}
