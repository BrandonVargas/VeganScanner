package dev.brandonvargas.veganscanner.umbrella

import androidx.room.Room
import androidx.room.RoomDatabase
import dev.brandonvargas.veganscanner.core.common.AppInfo
import dev.brandonvargas.veganscanner.core.database.VeganScannerDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Starts the real Koin graph (with in-memory DB and mock HTTP) and resolves every Swift entry point. */
class AppModulesTest {
    @BeforeTest
    fun setUp() {
        val testOverrides =
            module {
                single<RoomDatabase.Builder<VeganScannerDatabase>> { Room.inMemoryDatabaseBuilder() }
                single<HttpClientEngine> { MockEngine { respondError(HttpStatusCode.NotFound) } }
            }
        startKoin {
            allowOverride(true)
            modules(appModules(AppInfo("test", isDebug = false, platform = "test")) + testOverrides)
        }
    }

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun viewModelStoreHolderResolvesAndCachesViewModels() {
        val holder = ViewModelStoreHolder()

        val scanner = holder.scannerViewModel()
        assertSame(scanner, holder.scannerViewModel())
        holder.historyViewModel()
        val result = holder.productResultViewModel("3017620422003")
        assertNotSame(result, holder.productResultViewModel("7500327047878"))

        holder.clear()
        assertNotSame(scanner, holder.scannerViewModel())
    }
}
