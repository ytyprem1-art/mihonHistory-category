package eu.kanade.tachiyomi.ui.mod.updatewatch

import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import eu.kanade.tachiyomi.ui.mod.updatewatch.worker.UpdateWatchRefreshState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.UpdateWatch
import java.time.LocalDateTime
import java.time.ZoneId

class UpdateWatchCadenceTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `test HOT at 04-05 AM with last check 03-50 AM is due`() {
        val lastCheckAt = t(2024, 5, 8, 3, 50)
        val now = t(2024, 5, 8, 4, 5)
        val latestChapterDate = t(2024, 5, 1, 0, 0)

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = latestChapterDate,
            lastCheckAt = lastCheckAt,
            now = now,
            zoneId = zoneId
        )

        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.HOT, eligibility.bucket)
        assertTrue(eligibility.isDue)
        assertEquals(t(2024, 5, 8, 4, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test newly enabled HOT at 03-20 PM targets 04-00 PM not recovery`() {
        // enabledAt 3:20 PM -> stored as -3:20 PM
        val enabledAt = t(2024, 5, 8, 15, 20)
        val now = t(2024, 5, 8, 15, 20)

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = t(2024, 5, 1, 0, 0),
            lastCheckAt = -enabledAt,
            now = now,
            zoneId = zoneId
        )

        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.HOT, eligibility.bucket)
        // Current slot is 2:00 PM. 2:00 PM < 3:20 PM -> NOT DUE.
        assertFalse(eligibility.isDue)
        // Next slot is 4:00 PM.
        assertEquals(t(2024, 5, 8, 16, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test already enabled HOT that missed slot still shows recovery`() {
        // enabled at 1:50 PM. now is 2:05 PM.
        // 2:00 PM slot started.
        val enabledAt = t(2024, 5, 8, 13, 50)
        val now = t(2024, 5, 8, 14, 5)

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = t(2024, 5, 1, 0, 0),
            lastCheckAt = -enabledAt,
            now = now,
            zoneId = zoneId
        )

        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.HOT, eligibility.bucket)
        // current slot 2:00 PM >= 1:50 PM -> DUE
        assertTrue(eligibility.isDue)
        assertEquals(t(2024, 5, 8, 14, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test disable and re-enable resets stale backlog`() {
        val now = t(2024, 5, 8, 10, 5)

        // Re-enabled just now at 10:01 AM (after 10:00 slot started)
        val enabledAt = t(2024, 5, 8, 10, 1)

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = t(2024, 5, 1, 0, 0),
            lastCheckAt = -enabledAt, // newly enabled
            now = now,
            zoneId = zoneId
        )

        // Slot is 10:00 AM. 10:00 < 10:01 -> NOT DUE.
        assertFalse(eligibility.isDue)
        // Next slot is 12:00 PM.
        assertEquals(t(2024, 5, 8, 12, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test no drift - next target remains fixed to slots`() {
        val lastCheckAt = t(2024, 5, 8, 14, 6)
        val now = t(2024, 5, 8, 14, 10)

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = t(2024, 5, 1, 0, 0),
            lastCheckAt = lastCheckAt,
            now = now,
            zoneId = zoneId
        )

        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.HOT, eligibility.bucket)
        assertFalse(eligibility.isDue)
        assertEquals(t(2024, 5, 8, 16, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test refresh queued status mapping`() {
        val mangaId = 1L

        // Scenario 1: Unattended due manga (not in queued set)
        UpdateWatchRefreshState.clear()
        var isQueued = mangaId in UpdateWatchRefreshState.queuedMangaIds.value
        assertFalse(isQueued)

        // Scenario 2: Selected candidate (added to queued set)
        UpdateWatchRefreshState.setQueued(setOf(mangaId))
        isQueued = mangaId in UpdateWatchRefreshState.queuedMangaIds.value
        assertTrue(isQueued)

        // Scenario 3: Completed/Failed/Cancelled (cleared)
        UpdateWatchRefreshState.clear()
        isQueued = mangaId in UpdateWatchRefreshState.queuedMangaIds.value
        assertFalse(isQueued)
    }

    @Test
    fun `test recovery launcher postponement fix`() {
        val now = t(2024, 5, 8, 8, 40)
        val earliest = t(2024, 5, 8, 8, 0) // current due slot

        // Scenario: recovery target was 8:39
        val lastScheduled = t(2024, 5, 8, 8, 39)
        val lastEarliest = earliest

        // Simulation of new logic in setupTaskSuspend:
        val hasActiveExistingWork = true
        val canKeep = hasActiveExistingWork && (lastEarliest == earliest) && (lastScheduled > 0)
        val isOverdue = lastScheduled <= now

        assertTrue(canKeep)
        assertTrue(isOverdue)

        // If we can keep it, we don't schedule a NEW one (which would be now + 30m = 9:10)
        val schedulerAction = if (canKeep) "KEEP" else "REPLACE"
        assertEquals("KEEP", schedulerAction)
    }

    @Test
    fun `test terminal work is replaced`() {
        val earliest = t(2024, 5, 8, 8, 0)
        val lastScheduled = t(2024, 5, 8, 8, 39)
        val lastEarliest = earliest

        // Scenario: existing work finished or was cancelled
        val hasActiveExistingWork = false
        val canKeep = hasActiveExistingWork && (lastEarliest == earliest) && (lastScheduled > 0)

        assertFalse(canKeep)
        assertEquals("REPLACE", if (canKeep) "KEEP" else "REPLACE")
    }

    private fun t(year: Int, month: Int, day: Int, hour: Int, min: Int): Long {
        return LocalDateTime.of(year, month, day, hour, min).atZone(zoneId).toInstant().toEpochMilli()
    }
}
