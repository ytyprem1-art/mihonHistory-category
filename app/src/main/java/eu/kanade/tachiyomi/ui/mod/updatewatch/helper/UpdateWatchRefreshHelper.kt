package eu.kanade.tachiyomi.ui.mod.updatewatch.helper

import eu.kanade.tachiyomi.util.lang.toLocalDate
import tachiyomi.domain.history.model.UpdateWatch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object UpdateWatchRefreshHelper {

    const val CAP_HOT = 12
    const val CAP_WARM = 8
    const val CAP_COLD = 8
    const val CAP_STALE = 4
    const val CAP_TOTAL = 20
    const val GLOBAL_CONCURRENCY = 4

    const val STAGGER_DEBUG_MIN_MS = 3000L
    const val STAGGER_DEBUG_MAX_MS = 8000L
    const val STAGGER_RELEASE_MIN_MS = 30000L
    const val STAGGER_RELEASE_MAX_MS = 90000L

    const val MIN_RESCHEDULE_GAP_MILLIS = 30 * 60 * 1000L

    enum class RefreshStatus {
        WAITING,
        ACTIVE,
        INVALID_DATE,
        NOT_ENABLED
    }

    enum class PriorityBucket {
        HOT,
        WARM,
        COLD,
        STALE,
        NONE
    }

    data class RefreshEligibility(
        val ageDays: Long,
        val daysUntilDue: Long,
        val status: RefreshStatus,
        val isStale: Boolean = false,
        val bucket: PriorityBucket = PriorityBucket.NONE,
        val plannedCadenceLabel: String? = null,
        val plannedCadenceIntervalMillis: Long? = null,
        val nextEligibleAt: Long? = null,
    )

    fun getEligibility(
        enabled: Boolean,
        expectedIntervalDays: Int,
        refreshProfile: UpdateWatch.RefreshProfile,
        latestChapterUploadDate: Long,
        lastCheckAt: Long? = null,
        today: LocalDate = LocalDate.now(),
    ): RefreshEligibility {
        if (latestChapterUploadDate <= 0) {
            return RefreshEligibility(0, 0, RefreshStatus.INVALID_DATE, false)
        }

        val releaseDate = latestChapterUploadDate.toLocalDate()
        val ageDays = ChronoUnit.DAYS.between(releaseDate, today)
        val daysUntilDue = expectedIntervalDays - ageDays
        val isStale = ageDays >= expectedIntervalDays + 28

        if (!enabled) {
            return RefreshEligibility(ageDays, daysUntilDue, RefreshStatus.NOT_ENABLED, isStale)
        }

        val status = if (ageDays < expectedIntervalDays) {
            RefreshStatus.WAITING
        } else {
            RefreshStatus.ACTIVE
        }

        var cadenceIntervalMillis: Long? = null
        var cycleBucket = PriorityBucket.NONE
        val cadenceLabel = if (status == RefreshStatus.ACTIVE) {
            val (info, bucket) = getPlannedCadenceInfo(refreshProfile, ageDays, expectedIntervalDays.toLong())
            cadenceIntervalMillis = info.second
            cycleBucket = bucket
            info.first
        } else {
            null
        }

        val finalBucket = when {
            status != RefreshStatus.ACTIVE -> PriorityBucket.NONE
            cycleBucket == PriorityBucket.HOT || cycleBucket == PriorityBucket.WARM -> cycleBucket
            isStale -> PriorityBucket.STALE
            else -> cycleBucket
        }

        val nextEligibleAt = if (status == RefreshStatus.ACTIVE && cadenceIntervalMillis != null) {
            (lastCheckAt ?: 0L) + cadenceIntervalMillis
        } else {
            null
        }

        return RefreshEligibility(
            ageDays = ageDays,
            daysUntilDue = daysUntilDue,
            status = status,
            isStale = isStale,
            bucket = finalBucket,
            plannedCadenceLabel = cadenceLabel,
            plannedCadenceIntervalMillis = cadenceIntervalMillis,
            nextEligibleAt = nextEligibleAt,
        )
    }

    /**
     * Finds the earliest nextEligibleAt timestamp across all provided candidates.
     * Only considers enabled and active (eligible for refresh) manga.
     * Includes WAITING manga based on their expected release date.
     */
    fun getEarliestNextEligibleAt(
        trackingList: List<UpdateWatch>,
        latestChapterDates: Map<Long, Long>,
        today: LocalDate = LocalDate.now(),
    ): Long? {
        return trackingList
            .filter { it.backgroundRefreshEnabled && !it.isPaused }
            .mapNotNull { tracking ->
                val latestDate = latestChapterDates[tracking.mangaId] ?: return@mapNotNull null
                val eligibility = getEligibility(
                    enabled = tracking.backgroundRefreshEnabled,
                    expectedIntervalDays = tracking.expectedIntervalDays,
                    refreshProfile = tracking.refreshProfile,
                    latestChapterUploadDate = latestDate,
                    lastCheckAt = tracking.lastBackgroundCheckAt,
                    today = today
                )
                when (eligibility.status) {
                    RefreshStatus.ACTIVE -> eligibility.nextEligibleAt
                    RefreshStatus.WAITING -> {
                        // releaseDate + expectedIntervalDays
                        latestDate + (tracking.expectedIntervalDays.toLong() * 24 * 60 * 60 * 1000L)
                    }
                    else -> null
                }
            }
            .minOrNull()
    }

    /**
     * Calculates the initial delay for the next OneTimeWorkRequest.
     * Includes a safety margin and enforces a minimum gap.
     */
    fun calculateRescheduleDelay(
        earliestNextEligibleAt: Long?,
        now: Long,
        marginMillis: Long,
    ): Long? {
        if (earliestNextEligibleAt == null) return null

        var delayMillis = earliestNextEligibleAt - now
        delayMillis += marginMillis

        return delayMillis.coerceAtLeast(MIN_RESCHEDULE_GAP_MILLIS)
    }

    private fun getPlannedCadenceInfo(
        profile: UpdateWatch.RefreshProfile,
        ageDays: Long,
        expectedDays: Long,
    ): Pair<Pair<String?, Long?>, PriorityBucket> {
        if (expectedDays <= 0) return (null to null) to PriorityBucket.NONE

        return when (profile) {
            UpdateWatch.RefreshProfile.WEEKLY_STABLE -> {
                val dayInCycle = (ageDays - expectedDays) % expectedDays
                when {
                    dayInCycle <= 1L -> {
                        ("Planned check every 2 hours" to 120 * 60 * 1000L) to PriorityBucket.HOT
                    }
                    dayInCycle == 2L -> {
                        ("Planned check about every 4 hours" to 4 * 60 * 60 * 1000L) to PriorityBucket.WARM
                    }
                    else -> {
                        ("Planned check about twice daily" to 12 * 60 * 60 * 1000L) to PriorityBucket.COLD
                    }
                }
            }
            UpdateWatch.RefreshProfile.SLOW_PERIODIC -> {
                if (ageDays <= expectedDays + 14) {
                    ("Planned check about twice daily" to 12 * 60 * 60 * 1000L) to PriorityBucket.COLD
                } else {
                    ("Planned check about once daily" to 24 * 60 * 60 * 1000L) to PriorityBucket.COLD
                }
            }
            UpdateWatch.RefreshProfile.RAPID_IRREGULAR -> {
                when {
                    ageDays <= expectedDays + 1 -> {
                        ("Planned check every 3–4 hours" to 3 * 60 * 60 * 1000L) to PriorityBucket.HOT
                    }
                    ageDays <= expectedDays + 6 -> {
                        ("Planned check about twice daily" to 12 * 60 * 60 * 1000L) to PriorityBucket.WARM
                    }
                    else -> {
                        ("Planned check about once daily" to 24 * 60 * 60 * 1000L) to PriorityBucket.COLD
                    }
                }
            }
        }
    }
}
