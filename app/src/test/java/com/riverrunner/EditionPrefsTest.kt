package com.riverrunner

import com.riverrunner.data.EditionPrefs
import com.riverrunner.engine.strategy.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditionPrefsTest {
    @Test fun roundTrip() {
        val config = EditionConfig(AiMode.ADAPTIVE, mapOf(2 to OpponentPreset.STATION, 8 to OpponentPreset.LOOSE_PASSIVE), false)
        assertEquals(config, EditionPrefs.decode(EditionPrefs.encode(config)))
    }
    @Test fun absentCorruptAndNewerSettingsFallBackSafely() {
        assertEquals(EditionConfig(), EditionPrefs.decode(null))
        assertEquals(EditionConfig(), EditionPrefs.decode("not json"))
        assertEquals(EditionConfig(), EditionPrefs.decode("{\"schema\":99,\"mode\":\"ADAPTIVE\"}"))
        assertEquals(AiMode.LEGACY, EditionPrefs.decode("{\"schema\":1,\"mode\":\"FUTURE\"}").aiMode)
    }
}
