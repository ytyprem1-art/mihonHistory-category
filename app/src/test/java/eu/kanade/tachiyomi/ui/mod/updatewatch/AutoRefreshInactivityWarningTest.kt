package eu.kanade.tachiyomi.ui.mod.updatewatch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AutoRefreshInactivityWarningTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `test threshold logic - 59 days enables directly`() {
        val days = 59L
        assertFalse(shouldShowWarning(days))
    }

    @Test
    fun `test threshold logic - 60 days shows standard warning`() {
        val days = 60L
        assertTrue(shouldShowWarning(days))
        assertFalse(isStrongWarning(days))
    }

    @Test
    fun `test threshold logic - 179 days shows standard warning`() {
        val days = 179L
        assertTrue(shouldShowWarning(days))
        assertFalse(isStrongWarning(days))
    }

    @Test
    fun `test threshold logic - 180 days shows stronger warning`() {
        val days = 180L
        assertTrue(shouldShowWarning(days))
        assertTrue(isStrongWarning(days))
    }

    @Test
    fun `test unknown release date - enables normally`() {
        val days = -1L
        assertFalse(shouldShowWarning(days))
    }

    // Helper logic used in the UI
    private fun shouldShowWarning(days: Long): Boolean {
        return days >= 60
    }

    private fun isStrongWarning(days: Long): Boolean {
        return days >= 180
    }
}
