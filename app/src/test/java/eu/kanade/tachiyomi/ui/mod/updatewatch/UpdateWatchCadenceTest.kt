package eu.kanade.tachiyomi.ui.mod.updatewatch

import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import org.junit.jupiter.api.Assertions.assertEquals
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
        // Due for 04:00 slot.
        assertEquals(t(2024, 5, 8, 4, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test HOT at 04-05 AM with last check 04-02 AM is not due`() {
        val lastCheckAt = t(2024, 5, 8, 4, 2)
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
        // Not due for 04:00 slot because last check was inside/after it.
        // nextEligibleAt should be 06:00.
        assertEquals(t(2024, 5, 8, 6, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test WARM follows 00-04-08-12-16-20 slots`() {
        val now = t(2024, 5, 8, 5, 0)
        val lastCheckAt = t(2024, 5, 8, 3, 0)
        // ageDays = 9, expected = 7 -> dayInCycle = 2 -> WARM
        val releaseDate = LocalDateTime.of(2024, 5, 8, 0, 0).minusDays(9).atZone(zoneId).toInstant().toEpochMilli()

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = releaseDate,
            lastCheckAt = lastCheckAt,
            now = now,
            zoneId = zoneId
        )

        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.WARM, eligibility.bucket)
        assertEquals(t(2024, 5, 8, 4, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test COLD follows 00-12 slots`() {
        val now = t(2024, 5, 8, 13, 0)
        val lastCheckAt = t(2024, 5, 8, 11, 0)
        // ageDays = 10, expected = 7 -> dayInCycle = 3 -> COLD
        val releaseDate = LocalDateTime.of(2024, 5, 8, 0, 0).minusDays(10).atZone(zoneId).toInstant().toEpochMilli()

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = releaseDate,
            lastCheckAt = lastCheckAt,
            now = now,
            zoneId = zoneId
        )

        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.COLD, eligibility.bucket)
        assertEquals(t(2024, 5, 8, 12, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test missed multiple slots creates only one due refresh`() {
        val now = t(2024, 5, 8, 9, 59) // 09:59
        val lastCheckAt = t(2024, 5, 8, 2, 0) // Missed 04, 06, 08 slots
        val releaseDate = t(2024, 5, 1, 0, 0)

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = releaseDate,
            lastCheckAt = lastCheckAt,
            now = now,
            zoneId = zoneId
        )

        // It is due for the LATEST elapsed slot (08:00).
        assertEquals(t(2024, 5, 8, 8, 0), eligibility.nextEligibleAt)
    }

    @Test
    fun `test earliest nextEligibleAt includes WAITING manga`() {
        val now = t(2024, 5, 8, 10, 0)
        val releaseDateWaiting = t(2024, 5, 8, 0, 0)
        val trackingList = listOf(createTracking(1, lastCheck = 1000L))
        val latestDates = mapOf(1L to releaseDateWaiting)

        val earliest = UpdateWatchRefreshHelper.getEarliestNextEligibleAt(
            trackingList,
            latestDates,
            now = now,
            zoneId = zoneId
        )

        // ACTIVE on May 15th 00:00.
        val expected = t(2024, 5, 15, 0, 0)
        assertEquals(expected, earliest)
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
