package dev.brandonvargas.veganscanner.core.database

import androidx.room.Room
import app.cash.turbine.test
import dev.brandonvargas.veganscanner.core.database.entity.ScanHistoryEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ScanHistoryDaoTest {
    private val db = Room.inMemoryDatabaseBuilder<VeganScannerDatabase>().buildDatabase()
    private val dao = db.scanHistoryDao()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun rescanReplacesEntryAndOrdersByMostRecent() =
        runTest {
            dao.upsert(entry("111", scannedAt = 1))
            dao.upsert(entry("222", scannedAt = 2))
            dao.upsert(entry("111", scannedAt = 3, status = "NON_VEGAN"))

            dao.observeRecent(limit = 10).test {
                val rows = awaitItem()
                assertEquals(listOf("111", "222"), rows.map { it.barcode })
                assertEquals("NON_VEGAN", rows.first().status)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun deleteAndClear() =
        runTest {
            dao.upsert(entry("111", scannedAt = 1))
            dao.upsert(entry("222", scannedAt = 2))
            dao.delete("111")
            dao.observeRecent(10).test {
                assertEquals(listOf("222"), awaitItem().map { it.barcode })
                dao.clear()
                assertEquals(emptyList(), awaitItem())
            }
        }

    private fun entry(barcode: String, scannedAt: Long, status: String = "VEGAN") =
        ScanHistoryEntity(
            barcode = barcode,
            productName = "Product $barcode",
            brands = null,
            imageUrl = null,
            status = status,
            source = "OPEN_FOOD_FACTS",
            scannedAtEpochMs = scannedAt,
        )
}
