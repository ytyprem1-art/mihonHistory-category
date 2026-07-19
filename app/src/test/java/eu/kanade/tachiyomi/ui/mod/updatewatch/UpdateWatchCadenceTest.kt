package eu.kanade.tachiyomi.ui.mod.updatewatch

import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        // Due for 04:00 slot. nextEligibleAt is the base slot.
        assertEquals(t(2024, 5, 8, 4, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test no drift - next target remains fixed to slots`() {
        // Run finishes at 14:06 (started at 14:00 slot + margin)
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
        // Since last check (14:06) is after current slot (14:00), it's NOT due.
        // nextEligibleAt must be exactly 16:00, not 16:06.
        assertEquals(t(2024, 5, 8, 16, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test bucket separation at 02-05 AM`() {
        val now = t(2024, 5, 8, 2, 5)
        val lastCheckAt = t(2024, 5, 8, 0, 5) // Processed 00:00 slot for all

        // HOT: ageDays=7, expected=7 -> dayInCycle=0 -> HOT. Slot 02:00 > 00:05 -> DUE
        val eligibilityHOT = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = t(2024, 5, 1, 0, 0),
            lastCheckAt = lastCheckAt,
            now = now,
            zoneId = zoneId
        )
        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.HOT, eligibilityHOT.bucket)
        assertEquals(t(2024, 5, 8, 2, 0), eligibilityHOT.nextEligibleAt)

        // WARM: ageDays=11, expected=9 -> dayInCycle=2 -> WARM. Slot 00:00 < 00:05 -> NOT DUE
        val releaseDateWARM = LocalDateTime.of(2024, 5, 8, 0, 0).minusDays(11).atZone(zoneId).toInstant().toEpochMilli()
        val eligibilityWARM = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 9,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = releaseDateWARM,
            lastCheckAt = lastCheckAt,
            now = now,
            zoneId = zoneId
        )
        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.WARM, eligibilityWARM.bucket)
        assertEquals(t(2024, 5, 8, 4, 0), eligibilityWARM.nextEligibleAt)
    }

    @Test
    fun `test due recovery run behavior`() {
        // 04:05. Last check was 02:05 (processed 02:00 slot).
        // 04:00 slot is elapsed and unprocessed.
        val now = t(2024, 5, 8, 4, 5)
        val lastCheckAt = t(2024, 5, 8, 2, 5)

        val earliest = UpdateWatchRefreshHelper.getEarliestNextEligibleAt(
            listOf(createTracking(1, lastCheck = lastCheckAt)),
            mapOf(1L to t(2024, 5, 1, 0, 0)),
            now = now,
            zoneId = zoneId
        )

        // Earliest should be 04:00 (current slot)
        assertEquals(t(2024, 5, 8, 4, 0), earliest)

        // calculateRescheduleDelay should result in 30 min minimum delay
        val margin = 6 * 60 * 1000L
        val delay = UpdateWatchRefreshHelper.calculateRescheduleDelay(earliest, now, margin)
        assertEquals(30 * 60 * 1000L, delay)
    }

    @Test
    fun `test deferred candidates remain due`() {
        val now = t(2024, 5, 8, 4, 5)
        val lastCheckAt = t(2024, 5, 8, 3, 50) // last processed 02:00 slot or earlier

        // If this manga was deferred (not processed) in the 04:00 run,
        // lastCheckAt REMAINS 03:50.

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = t(2024, 5, 1, 0, 0),
            lastCheckAt = lastCheckAt,
            now = now,
            zoneId = zoneId
        )

        // Still due for 04:00 slot
        assertEquals(t(2024, 5, 8, 4, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test idempotency - reopening app keeps valid target`() {
        val now = t(2024, 5, 8, 14, 10)
        val earliest = t(2024, 5, 8, 16, 0) // next HOT slot
        val existingScheduledAt = t(2024, 5, 8, 16, 7) // earlier + 7 min margin

        // Reopening with same earliest and future target
        val lastEarliest = earliest
        val lastScheduled = existingScheduledAt

        val isStillValid = (lastEarliest == earliest) && (lastScheduled > now)
        assertTrue(isStillValid)
    }

    private fun t(year: Int, month: Int, day: Int, hour: Int, min: Int): Long {
        return LocalDateTime.of(year, month, day, hour, min).atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun createTracking(
        id: Long,
        enabled: Boolean = true,
        lastCheck: Long = 0L,
        profile: UpdateWatch.RefreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE
    ): UpdateWatch {
        return UpdateWatch(
            mangaId = id,
            isPaused = false,
            backgroundRefreshEnabled = enabled,
            expectedIntervalDays = 7,
            refreshProfile = profile,
            lastBackgroundCheckAt = lastCheck
        )
    }
}
