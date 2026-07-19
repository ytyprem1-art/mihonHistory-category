package eu.kanade.tachiyomi.ui.mod.updatewatch.helper

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object UpdateWatchSlotHelper {

    /**
     * Returns the latest slot start time (epoch millis) at or before [now]
     * for the given [bucket].
     */
    fun getCurrentSlot(
        bucket: UpdateWatchRefreshHelper.PriorityBucket,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val intervalHours = getSlotIntervalHours(bucket) ?: return 0L
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)

        val slotHour = (ldt.hour / intervalHours) * intervalHours
        val currentSlotLdt = ldt.withHour(slotHour).withMinute(0).withSecond(0).withNano(0)

        return currentSlotLdt.atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Returns the first slot start time (epoch millis) strictly after [now]
     * for the given [bucket].
     */
    fun getNextSlot(
        bucket: UpdateWatchRefreshHelper.PriorityBucket,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val intervalHours = getSlotIntervalHours(bucket) ?: return 0L
        val currentSlot = getCurrentSlot(bucket, now, zoneId)
        val currentSlotLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentSlot), zoneId)

        val nextSlotLdt = currentSlotLdt.plusHours(intervalHours.toLong())

        return nextSlotLdt.atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Returns true if the manga is due for the current slot.
     * [lastProcessedSlotAt] is the start time of the slot that was last processed for this manga.
     */
    fun isDue(
        bucket: UpdateWatchRefreshHelper.PriorityBucket,
        now: Long,
        lastProcessedSlotAt: Long?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (bucket == UpdateWatchRefreshHelper.PriorityBucket.NONE) return false

        val currentSlot = getCurrentSlot(bucket, now, zoneId)
        return lastProcessedSlotAt == null || currentSlot > lastProcessedSlotAt
    }

    /**
     * Returns the next wall-clock slot timestamp across all provided buckets.
     */
    fun getNextWorkerTargetBase(
        buckets: Set<UpdateWatchRefreshHelper.PriorityBucket>,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val validBuckets = buckets.filter { it != UpdateWatchRefreshHelper.PriorityBucket.NONE }
        if (validBuckets.isEmpty()) return null

        return validBuckets.map { getNextSlot(it, now, zoneId) }.minOrNull()
    }

    private fun getSlotIntervalHours(bucket: UpdateWatchRefreshHelper.PriorityBucket): Int? {
        return when (bucket) {
            UpdateWatchRefreshHelper.PriorityBucket.HOT -> 2
            UpdateWatchRefreshHelper.PriorityBucket.WARM -> 4
            UpdateWatchRefreshHelper.PriorityBucket.COLD -> 12
            UpdateWatchRefreshHelper.PriorityBucket.STALE -> 24
            UpdateWatchRefreshHelper.PriorityBucket.NONE -> null
        }
    }
}
