package dev.brandonvargas.veganscanner.core.database

import androidx.room.Room
import dev.brandonvargas.veganscanner.core.database.entity.LabelScanEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelScanDaoTest {
    private val db = Room.inMemoryDatabaseBuilder<VeganScannerDatabase>().buildDatabase()
    private val dao = db.labelScanDao()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun rescanReplacesTextAndDeleteRemovesIt() =
        runTest {
            dao.upsert(LabelScanEntity("111", "agua, azucar", 1))
            dao.upsert(LabelScanEntity("111", "agua, azucar, grenetina", 2))

            assertEquals("agua, azucar, grenetina", dao.get("111")?.ingredientsText)

            dao.delete("111")
            assertNull(dao.get("111"))
        }
}
