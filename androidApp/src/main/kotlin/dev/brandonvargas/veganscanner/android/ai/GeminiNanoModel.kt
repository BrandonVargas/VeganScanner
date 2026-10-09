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
 * on supported devices. Even where AICore already runs Google's own features, the model for apps is a separate
 * download: [prepare] requests it at app start, while the phone is likely online, so it's ready when the app is
 * later used offline.
 */
class GeminiNanoModel(private val scope: CoroutineScope) : OnDeviceLanguageModel {
    private val model by lazy { Generation.getClient() }
    private val downloadStarted = AtomicBoolean(false)

    /** Starts the model download in the background when the phone supports it but doesn't have it yet. */
    fun prepare() {
        scope.launch { isAvailable() }
    }

    override suspend fun isAvailable(): Boolean {
        val status =
            runCatching { model.checkStatus() }
                .onFailure { Log.w(TAG, "Gemini Nano status check failed", it) }
                .getOrDefault(FeatureStatus.UNAVAILABLE)
        Log.i(TAG, "Gemini Nano status: ${statusName(status)}")
        if (status == FeatureStatus.DOWNLOADABLE) startDownload()
        return status == FeatureStatus.AVAILABLE
    }

    override suspend fun generate(instructions: String, prompt: String): String? =
        runCatching {
            val request =
                if (model.isSystemPromptAvailable()) {
                    generateContentRequest(SystemInstruction(instructions), TextPart(prompt)) { settings() }
                } else {
                    generateContentRequest(TextPart("$instructions\n\n$prompt")) { settings() }
                }
            model.generateContent(request).candidates.firstOrNull()?.text
        }.onSuccess { Log.i(TAG, "Gemini Nano answered: ${it?.take(LOGGED_ANSWER_LENGTH)}") }
            .onFailure { Log.w(TAG, "Gemini Nano request failed", it) }
            .getOrThrow()

    private fun com.google.mlkit.genai.prompt.GenerateContentRequest.Builder.settings() {
        temperature = 0f
        topK = 1
        maxOutputTokens = MAX_OUTPUT_TOKENS
    }

    private fun startDownload() {
        if (!downloadStarted.compareAndSet(false, true)) return
        Log.i(TAG, "Starting the Gemini Nano download")
        scope.launch {
            runCatching {
                var lastLoggedPercent = -PROGRESS_LOG_STEP
                var total = 0L
                model.download()
                    .takeWhile { status ->
                        when (status) {
                            is DownloadStatus.DownloadStarted -> {
                                total = status.bytesToDownload
                                Log.i(TAG, "Gemini Nano download started: $total bytes")
                            }

                            is DownloadStatus.DownloadProgress -> {
                                val percent = if (total > 0) (status.totalBytesDownloaded * 100 / total).toInt() else 0
                                if (percent >= lastLoggedPercent + PROGRESS_LOG_STEP) {
                                    lastLoggedPercent = percent
                                    Log.i(TAG, "Gemini Nano download: $percent%")
                                }
                            }

                            is DownloadStatus.DownloadFailed -> {
                                Log.w(TAG, "Gemini Nano download failed", status.e)
                            }

                            DownloadStatus.DownloadCompleted -> {
                                Log.i(TAG, "Gemini Nano download finished")
                            }
                        }
                        status !is DownloadStatus.DownloadCompleted && status !is DownloadStatus.DownloadFailed
                    }.collect {}
            }.onFailure {
                downloadStarted.set(false)
                Log.w(TAG, "Gemini Nano download failed", it)
            }
        }
    }

    private fun statusName(status: Int) =
        when (status) {
            FeatureStatus.AVAILABLE -> "AVAILABLE"
            FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
            FeatureStatus.DOWNLOADING -> "DOWNLOADING"
            FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
            else -> "UNKNOWN($status)"
        }

    private companion object {
        const val TAG = "GeminiNano"
        const val MAX_OUTPUT_TOKENS = 160
        const val LOGGED_ANSWER_LENGTH = 200
        const val PROGRESS_LOG_STEP = 10
    }
}
