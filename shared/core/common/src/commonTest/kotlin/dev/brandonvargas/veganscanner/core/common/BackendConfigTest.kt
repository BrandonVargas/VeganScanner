package dev.brandonvargas.veganscanner.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackendConfigTest {
    @Test
    fun blankSettingsTurnOnlineFeaturesOff() {
        assertNull(BackendConfig.fromBuildSettings(null, "key"))
        assertNull(BackendConfig.fromBuildSettings("abc.supabase.co", " "))
        assertNull(BackendConfig.fromBuildSettings("", ""))
    }

    @Test
    fun acceptsHostWithOrWithoutScheme() {
        val config = BackendConfig.fromBuildSettings(" https://abc.supabase.co/ ", "sb_publishable_x")

        assertEquals("abc.supabase.co", config?.host)
        assertEquals("https://abc.supabase.co", config?.url)
    }
}
