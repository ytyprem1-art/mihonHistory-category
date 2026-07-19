package eu.kanade.tachiyomi.ui.mod.updatewatch.helper

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

object UpdateWatchOverdueHelper {

    fun getOverdueMessage(daysSinceRelease: Long, isFunEnabled: Boolean): StringResource {
        if (!isFunEnabled) return MR.strings.overdue_delayed

        return when {
            daysSinceRelease < 14 -> MR.strings.overdue_delayed
            daysSinceRelease < 90 -> MR.strings.overdue_fun_1
            daysSinceRelease < 180 -> MR.strings.overdue_fun_2
            daysSinceRelease < 365 -> MR.strings.overdue_fun_3
            daysSinceRelease < 730 -> MR.strings.overdue_fun_4
            else -> MR.strings.overdue_fun_5
        }
    }
}
