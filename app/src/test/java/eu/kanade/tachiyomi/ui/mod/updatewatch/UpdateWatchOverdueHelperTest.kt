package eu.kanade.tachiyomi.ui.mod.updatewatch

import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchOverdueHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

class UpdateWatchOverdueHelperTest {

    @Test
    fun `test overdue message preference OFF`() {
        val isFunEnabled = false
        // 1000 days since release
        val message = UpdateWatchOverdueHelper.getOverdueMessage(1000L, isFunEnabled)
        assertEquals(MR.strings.overdue_delayed, message)
    }

    @Test
    fun `test overdue message thresholds with preference ON`() {
        val isFunEnabled = true

        // 13 days
        assertEquals(MR.strings.overdue_delayed, UpdateWatchOverdueHelper.getOverdueMessage(13L, isFunEnabled))

        // 14 days
        assertEquals(MR.strings.overdue_fun_1, UpdateWatchOverdueHelper.getOverdueMessage(14L, isFunEnabled))

        // 89 days
        assertEquals(MR.strings.overdue_fun_1, UpdateWatchOverdueHelper.getOverdueMessage(89L, isFunEnabled))

        // 90 days
        assertEquals(MR.strings.overdue_fun_2, UpdateWatchOverdueHelper.getOverdueMessage(90L, isFunEnabled))

        // 180 days
        assertEquals(MR.strings.overdue_fun_3, UpdateWatchOverdueHelper.getOverdueMessage(180L, isFunEnabled))

        // 365 days
        assertEquals(MR.strings.overdue_fun_4, UpdateWatchOverdueHelper.getOverdueMessage(365L, isFunEnabled))

        // 730 days
        assertEquals(MR.strings.overdue_fun_5, UpdateWatchOverdueHelper.getOverdueMessage(730L, isFunEnabled))
    }
}
