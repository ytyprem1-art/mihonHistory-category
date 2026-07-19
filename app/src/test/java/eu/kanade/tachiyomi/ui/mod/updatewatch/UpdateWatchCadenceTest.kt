package eu.kanade.tachiyomi.ui.mod.updatewatch

import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.UpdateWatch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class UpdateWatchCadenceTest {

    @Test
    fun `test HOT checked at 1-30 AM is next eligible at 3-30 AM`() {
        val lastCheckAt = 1715052600000L // 2024-05-07 01:30:00 UTC
        val latestChapterDate = 1714446000000L // Some days ago

        val eligibility = UpdateWatchRefreshHelper.getEligibility(
            enabled = true,
            expectedIntervalDays = 7,
            refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
            latestChapterUploadDate = latestChapterDate,
            lastCheckAt = lastCheckAt,
            today = LocalDate.of(2024, 5, 7)
        )

        assertEquals(UpdateWatchRefreshHelper.PriorityBucket.HOT, eligibility.bucket)

        val expectedNextEligible = lastCheckAt + TimeUnit.HOURS.toMillis(2)
        assertEquals(expectedNextEligible, eligibility.nextEligibleAt)
    }

    @Test
    fun `test earliest nextEligibleAt is selected from mixed manga`() {
        val today = LocalDate.of(2024, 5, 8)

        // ageDays = 7, expected = 7 -> dayInCycle = 0 (HOT, 2h)
        val releaseDateHOT = today.minusDays(7).toEpochDay() * 24 * 60 * 60 * 1000L
        // ageDays = 9, expected = 7 -> dayInCycle = 2 (WARM, 4h)
        val releaseDateWARM = today.minusDays(9).toEpochDay() * 24 * 60 * 60 * 1000L

        val trackingList = listOf(
            // HOT: check at 1000 + 2h = 7201000
            createTracking(1, lastCheck = 1000L, profile = UpdateWatch.RefreshProfile.WEEKLY_STABLE),
            // WARM: check at 500 + 4h = 500 + 14400000 = 14400500
            createTracking(2, lastCheck = 500L, profile = UpdateWatch.RefreshProfile.WEEKLY_STABLE),
            // OFF: should be ignored
            createTracking(3, enabled = false, lastCheck = 10L)
        )

        val latestDatesReal = mapOf(
            1L to releaseDateHOT,
            2L to releaseDateWARM,
            3L to releaseDateHOT
        )

        val earliest = UpdateWatchRefreshHelper.getEarliestNextEligibleAt(
            trackingList,
            latestDatesReal,
            today = today
        )

        val expectedHOT = 1000L + TimeUnit.HOURS.toMillis(2)
        assertEquals(expectedHOT, earliest)
    }

    @Test
    fun `test manga with Auto Refresh OFF is ignored for earliest nextEligibleAt`() {
        val trackingList = listOf(
            createTracking(1, enabled = false, lastCheck = 100L)
        )
        val releaseDate = LocalDate.of(2024, 5, 1).toEpochDay() * 24 * 60 * 60 * 1000L
        val latestDates = mapOf(1L to releaseDate)

        val earliest = UpdateWatchRefreshHelper.getEarliestNextEligibleAt(
            trackingList,
            latestDates,
            today = LocalDate.of(2024, 5, 8)
        )

        assertNull(earliest)
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
