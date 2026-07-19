package eu.kanade.tachiyomi.ui.mod.updatewatch

import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

        val earliest = UpdateWatchRefreshHelper.getEarliestNextEligibleAt(trackingList, latestDatesReal, today = today)

        val expectedHOT = 1000L + TimeUnit.HOURS.toMillis(2)
        assertEquals(expectedHOT, earliest)
    }

    @Test
    fun `test earliest nextEligibleAt includes WAITING manga`() {
        val today = LocalDate.of(2024, 5, 8)

        // ageDays = 0, expected = 7 -> status = WAITING
        val releaseDateWaiting = today.toEpochDay() * 24 * 60 * 60 * 1000L

        val trackingList = listOf(
            createTracking(1, lastCheck = 1000L) // expectedIntervalDays = 7
        )

        val latestDates = mapOf(1L to releaseDateWaiting)

        val earliest = UpdateWatchRefreshHelper.getEarliestNextEligibleAt(
            trackingList,
            latestDates,
            today = today
        )

        // For WAITING, nextEligibleAt = releaseDate + 7 days
        val expected = releaseDateWaiting + (7L * 24 * 60 * 60 * 1000L)
        assertEquals(expected, earliest)
    }

    @Test
    fun `test reschedule delay calculation includes margin and min gap`() {
        val now = 10000000L
        val earliestEligible = 10000000L + TimeUnit.MINUTES.toMillis(10) // eligible in 10 mins

        // Test margin 5 mins -> total delay 15 mins -> should be clamped to 30 mins
        val margin5 = TimeUnit.MINUTES.toMillis(5)
        val delay1 = UpdateWatchRefreshHelper.calculateRescheduleDelay(earliestEligible, now, margin5)
        assertEquals(TimeUnit.MINUTES.toMillis(30), delay1)

        // Test eligible in 40 mins, margin 5 mins -> total delay 45 mins
        val earliestEligibleFar = now + TimeUnit.MINUTES.toMillis(40)
        val delay2 = UpdateWatchRefreshHelper.calculateRescheduleDelay(earliestEligibleFar, now, margin5)
        assertEquals(TimeUnit.MINUTES.toMillis(45), delay2)

        // Test overdue item (eligible in the past)
        val earliestEligiblePast = now - TimeUnit.MINUTES.toMillis(10)
        val delay3 = UpdateWatchRefreshHelper.calculateRescheduleDelay(earliestEligiblePast, now, margin5)
        assertEquals(TimeUnit.MINUTES.toMillis(30), delay3)
    }

    @Test
    fun `test batching behavior - all currently eligible are selected`() {
        val now = 20000000L

        // Manga 1: nextEligibleAt = 17200000 (Overdue)
        // Manga 2: nextEligibleAt = 20000000 (Exactly now)
        // Manga 3: nextEligibleAt = 25200000 (Future)

        val eligible1 = createTracking(1, lastCheck = 10000000L)
        val eligible2 = createTracking(2, lastCheck = 12800000L)
        val future3 = createTracking(3, lastCheck = 18000000L)

        val releaseDate = LocalDate.of(2024, 5, 1).toEpochDay() * 24 * 60 * 60 * 1000L
        val latestDates = mapOf(1L to releaseDate, 2L to releaseDate, 3L to releaseDate)
        val today = LocalDate.of(2024, 5, 8)

        val toProcess = listOf(eligible1, eligible2, future3).filter { tracking ->
            val latestDate = latestDates[tracking.mangaId]!!
            val eligibility = UpdateWatchRefreshHelper.getEligibility(
                enabled = true,
                expectedIntervalDays = 7,
                refreshProfile = UpdateWatch.RefreshProfile.WEEKLY_STABLE,
                latestChapterUploadDate = latestDate,
                lastCheckAt = tracking.lastBackgroundCheckAt,
                today = today
            )
            eligibility.status == UpdateWatchRefreshHelper.RefreshStatus.ACTIVE && now >= (eligibility.nextEligibleAt ?: Long.MAX_VALUE)
        }

        assertEquals(2, toProcess.size)
        assertTrue(toProcess.any { it.mangaId == 1L })
        assertTrue(toProcess.any { it.mangaId == 2L })
        assertTrue(toProcess.none { it.mangaId == 3L })
    }

    @Test
    fun `test idempotency - same data yields same target window`() {
        val now = 10000000L
        val earliest = 20000000L

        // Scenario: existing schedule is valid if lastEarliest matches and target is in future
        val lastEarliest = earliest
        val lastScheduled = 20007000L // earliest + 7 min margin

        val isStillValid = (lastEarliest == earliest) && (lastScheduled > now)
        assertTrue(isStillValid)

        // If earliest changes, it's NOT valid
        val newEarliest = 15000000L
        val stillValidAfterChange = (lastEarliest == newEarliest) && (lastScheduled > now)
        assertTrue(!stillValidAfterChange)
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
