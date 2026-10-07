package dev.brandonvargas.veganscanner.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.brandonvargas.veganscanner.android.navigation.VeganScannerApp
import dev.brandonvargas.veganscanner.android.ui.theme.VeganScannerTheme
import dev.brandonvargas.veganscanner.feature.scanner.presentation.ScannerDeepLink

class MainActivity : ComponentActivity() {
    /** Barcode from a `veganscanner://product/...` link, waiting to be pushed onto the back stack. */
    private var pendingDeepLinkBarcode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleDeepLink(intent)
        setContent {
            VeganScannerTheme {
                VeganScannerApp(
                    deepLinkBarcode = pendingDeepLinkBarcode,
                    onDeepLinkConsume = { pendingDeepLinkBarcode = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val url = intent?.dataString ?: return
        pendingDeepLinkBarcode = ScannerDeepLink.barcodeFrom(url)
    }
}
