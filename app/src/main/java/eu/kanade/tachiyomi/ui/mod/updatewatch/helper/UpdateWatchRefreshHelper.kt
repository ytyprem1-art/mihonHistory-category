package eu.kanade.tachiyomi.ui.mod.updatewatch.helper

import eu.kanade.tachiyomi.util.lang.toLocalDate
import tachiyomi.domain.history.model.UpdateWatch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object UpdateWatchRefreshHelper {

    enum class RefreshStatus {
        WAITING,
        ACTIVE,
        INVALID_DATE,
        NOT_ENABLED
    }

    data class RefreshEligibility(
        val ageDays: Long,
        val daysUntilDue: Long,
        val status: RefreshStatus,
        val plannedCadenceLabel: String? = null,
    )

    fun getEligibility(
        enabled: Boolean,
        expectedIntervalDays: Int,
        refreshProfile: UpdateWatch.RefreshProfile,
        latestChapterUploadDate: Long,
        today: LocalDate = LocalDate.now(),
    ): RefreshEligibility {
        if (latestChapterUploadDate <= 0) {
            return RefreshEligibility(0, 0, RefreshStatus.INVALID_DATE)
        }

        val releaseDate = latestChapterUploadDate.toLocalDate()
        val ageDays = ChronoUnit.DAYS.between(releaseDate, today)
        val daysUntilDue = expectedIntervalDays - ageDays

        if (!enabled) {
            return RefreshEligibility(ageDays, daysUntilDue, RefreshStatus.NOT_ENABLED)
        }

        val status = if (ageDays < expectedIntervalDays) {
            RefreshStatus.WAITING
        } else {
            RefreshStatus.ACTIVE
        }

        val cadenceLabel = if (status == RefreshStatus.ACTIVE) {
            getPlannedCadenceLabel(refreshProfile, ageDays, expectedIntervalDays.toLong())
        } else {
            null
        }

        return RefreshEligibility(
            ageDays = ageDays,
            daysUntilDue = daysUntilDue,
            status = status,
            plannedCadenceLabel = cadenceLabel,
        )
    }

    private fun getPlannedCadenceLabel(
        profile: UpdateWatch.RefreshProfile,
        ageDays: Long,
        expectedDays: Long,
    ): String? {
        if (expectedDays <= 0) return null

        return when (profile) {
            UpdateWatch.RefreshProfile.WEEKLY_STABLE -> {
                val dayInCycle = ageDays % expectedDays
                when (dayInCycle) {
                    0L, 1L -> "Planned check every 1.5–2 hours"
                    2L -> "Planned check about every 4 hours"
                    else -> "Planned check about twice daily"
                }
            }
            UpdateWatch.RefreshProfile.SLOW_PERIODIC -> {
                if (ageDays <= expectedDays + 14) {
                    "Planned check about twice daily"
                } else {
                    "Planned check about once daily"
                }
            }
            UpdateWatch.RefreshProfile.RAPID_IRREGULAR -> {
                when {
                    ageDays <= expectedDays + 1 -> "Planned check every 3–4 hours"
                    ageDays <= expectedDays + 6 -> "Planned check about twice daily"
                    else -> "Planned check about once daily"
                }
            }
        }
    }
}
