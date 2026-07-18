package eu.kanade.tachiyomi.ui.mod.updatewatch.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.ManageUpdateWatch
import tachiyomi.domain.history.interactor.ManageUpdateWatchHistory
import tachiyomi.domain.history.interactor.ManageUpdateWatchInbox
import tachiyomi.domain.history.model.UpdateWatch
import tachiyomi.domain.history.model.UpdateWatchHistory
import tachiyomi.domain.history.model.UpdateWatchInboxItem
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class UpdateWatchRefreshWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val manageUpdateWatch: ManageUpdateWatch = Injekt.get()
    private val manageUpdateWatchHistory: ManageUpdateWatchHistory = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val chapterRepository: ChapterRepository = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get()
    private val manageUpdateWatchInbox: ManageUpdateWatchInbox = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()

    private val simulationTriggered = AtomicBoolean(false)
    private val newlyFoundInboxItems = mutableListOf<UpdateWatchInboxItem>()

    override suspend fun doWork(): Result = withIOContext {
        try {
            val simulationMode = if (eu.kanade.tachiyomi.BuildConfig.DEBUG) {
                inputData.getInt(KEY_SIMULATION_MODE, SIM_NONE)
            } else {
                SIM_NONE
            }
            simulationTriggered.set(false)
            newlyFoundInboxItems.clear()

            val trackedManga: List<UpdateWatch> = manageUpdateWatch.subscribeAll().first()
            val allCandidates = mutableListOf<RefreshCandidate>()

            trackedManga.filter { it.backgroundRefreshEnabled && !it.isPaused }.forEach { tracking ->
                val manga = getManga.await(tracking.mangaId) ?: return@forEach
                val chapters = chapterRepository.getChapterByMangaId(manga.id)
                val latestChapter = chapters.filter { it.dateUpload > 0 }.maxByOrNull { it.dateUpload } ?: return@forEach

                val eligibility = UpdateWatchRefreshHelper.getEligibility(
                    enabled = tracking.backgroundRefreshEnabled,
                    expectedIntervalDays = tracking.expectedIntervalDays,
                    refreshProfile = tracking.refreshProfile,
                    latestChapterUploadDate = latestChapter.dateUpload,
                    today = LocalDate.now(),
                )

                if (eligibility.status == UpdateWatchRefreshHelper.RefreshStatus.ACTIVE) {
                    val lastCheck = tracking.lastBackgroundCheckAt ?: 0L
                    val now = System.currentTimeMillis()
                    val interval = eligibility.plannedCadenceIntervalMillis ?: 0L

                    if (now - lastCheck >= interval) {
                        allCandidates.add(
                            RefreshCandidate(
                                mangaId = manga.id,
                                title = manga.title,
                                sourceId = manga.source,
                                ageDays = eligibility.ageDays,
                                expectedIntervalDays = tracking.expectedIntervalDays,
                                refreshProfile = tracking.refreshProfile.name,
                                bucket = eligibility.bucket,
                                plannedCadence = eligibility.plannedCadenceLabel ?: "Unknown",
                                lastCheckAt = lastCheck
                            )
                        )
                    }
                }

                // CHECK FOR INACTIVITY WARNING MILESTONES (28, 56, 84...)
                if (tracking.backgroundRefreshEnabled && eligibility.isStale) {
                    val daysOver = eligibility.ageDays - tracking.expectedIntervalDays
                    val milestone = ((daysOver / 28) * 28).toInt()
                    if (milestone >= 28 && milestone > tracking.lastWarnedMilestone) {
                        manageUpdateWatchInbox.insertOrMerge(
                            UpdateWatchInboxItem(
                                mangaId = manga.id,
                                mangaTitle = manga.title,
                                sourceId = manga.source,
                                sourceName = sourceManager.getOrStub(manga.source).name,
                                chapterCount = 0,
                                chapterRange = "",
                                firstFoundAt = System.currentTimeMillis(),
                                lastFoundAt = System.currentTimeMillis(),
                                latestChapterId = 0,
                                latestChapterNumber = 0.0,
                                chapterIds = emptyList(),
                                type = UpdateWatchInboxItem.TYPE_INACTIVITY_WARNING,
                                milestone = milestone
                            )
                        )
                        manageUpdateWatch.updateStaleMilestone(manga.id, milestone)
                    }
                }
            }

            if (allCandidates.isEmpty()) {
                logcat(LogPriority.INFO) { "Background refresh: No eligible candidates." }
                return@withIOContext Result.success()
            }

            logcat(LogPriority.INFO) { "Background refresh: Total potential eligible candidates found: ${allCandidates.size}" }

            // GROUP AND APPLY BUCKET CAPS PER SOURCE
            val workBySource = allCandidates.groupBy { it.sourceId }
                .mapValues { (sourceId, list) ->
                    val sourceName = sourceManager.getOrStub(sourceId).name

                    val hotEligible = list.filter { it.bucket == UpdateWatchRefreshHelper.PriorityBucket.HOT }.sortedBy { it.lastCheckAt }
                    val warmEligible = list.filter { it.bucket == UpdateWatchRefreshHelper.PriorityBucket.WARM }.sortedBy { it.lastCheckAt }
                    val coldEligible = list.filter { it.bucket == UpdateWatchRefreshHelper.PriorityBucket.COLD }.sortedBy { it.lastCheckAt }
                    val staleEligible = list.filter { it.bucket == UpdateWatchRefreshHelper.PriorityBucket.STALE }.sortedBy { it.lastCheckAt }

                    val selected = mutableListOf<RefreshCandidate>()

                    // Add HOT up to CAP_HOT
                    selected.addAll(hotEligible.take(UpdateWatchRefreshHelper.CAP_HOT))

                    // Add WARM up to CAP_WARM, keeping total under CAP_TOTAL
                    val remainingTotal = UpdateWatchRefreshHelper.CAP_TOTAL - selected.size
                    if (remainingTotal > 0) {
                        selected.addAll(warmEligible.take(minOf(UpdateWatchRefreshHelper.CAP_WARM, remainingTotal)))
                    }

                    // Add COLD up to CAP_COLD, keeping total under CAP_TOTAL
                    val remainingTotal2 = UpdateWatchRefreshHelper.CAP_TOTAL - selected.size
                    if (remainingTotal2 > 0) {
                        selected.addAll(coldEligible.take(minOf(UpdateWatchRefreshHelper.CAP_COLD, remainingTotal2)))
                    }

                    // Add STALE up to CAP_STALE, keeping total under CAP_TOTAL
                    val remainingTotal3 = UpdateWatchRefreshHelper.CAP_TOTAL - selected.size
                    if (remainingTotal3 > 0) {
                        selected.addAll(staleEligible.take(minOf(UpdateWatchRefreshHelper.CAP_STALE, remainingTotal3)))
                    }

                    val deferred = list.size - selected.size

                    if (eu.kanade.tachiyomi.BuildConfig.DEBUG) {
                        logcat(LogPriority.INFO) {
                            "[UpdateWatchRefreshWorker] source=$sourceName id=$sourceId " +
                            "eligible hot=${hotEligible.size} warm=${warmEligible.size} cold=${coldEligible.size} stale=${staleEligible.size} " +
                            "selected hot=${selected.count { it.bucket == UpdateWatchRefreshHelper.PriorityBucket.HOT }} " +
                            "warm=${selected.count { it.bucket == UpdateWatchRefreshHelper.PriorityBucket.WARM }} " +
                            "cold=${selected.count { it.bucket == UpdateWatchRefreshHelper.PriorityBucket.COLD }} " +
                            "stale=${selected.count { it.bucket == UpdateWatchRefreshHelper.PriorityBucket.STALE }} " +
                            "total=${selected.size} deferred=$deferred"
                        }
                    }
                    selected
                }

            // ORDER SOURCE QUEUES BY FAIRNESS (Source that has waited longest starts first)
            val sortedSourceQueues = workBySource.toList()
                .filter { it.second.isNotEmpty() }
                .sortedBy { (_, queue) -> queue.first().lastCheckAt }

            val processedCount = AtomicInteger(0)
            val updatedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val stoppedQueuesCount = AtomicInteger(0)

            // GLOBAL SOURCE CONCURRENCY
            val sourceSemaphore = Semaphore(UpdateWatchRefreshHelper.GLOBAL_CONCURRENCY)
            val sourcesStartedCount = AtomicInteger(0)

            coroutineScope {
                sortedSourceQueues.map { (sourceId, sourceQueue) ->
                    async {
                        val sourceName = sourceManager.getOrStub(sourceId).name
                        logcat(LogPriority.INFO) { "Source queue $sourceName waiting for global concurrency slot" }
                        sourceSemaphore.withPermit {
                            logcat(LogPriority.INFO) { "Source queue $sourceName acquired global concurrency slot" }
                            try {
                                val startedIndex = sourcesStartedCount.getAndIncrement()
                                if (startedIndex > 0) {
                                    val staggerMillis = if (eu.kanade.tachiyomi.BuildConfig.DEBUG) {
                                        Random.nextLong(UpdateWatchRefreshHelper.STAGGER_DEBUG_MIN_MS, UpdateWatchRefreshHelper.STAGGER_DEBUG_MAX_MS + 1)
                                    } else {
                                        Random.nextLong(UpdateWatchRefreshHelper.STAGGER_RELEASE_MIN_MS, UpdateWatchRefreshHelper.STAGGER_RELEASE_MAX_MS + 1)
                                    }
                                    logcat(LogPriority.INFO) { "Source queue $sourceName staggered start: waiting ${staggerMillis / 1000}s" }
                                    delay(staggerMillis)
                                }

                                logcat(LogPriority.INFO) { "Source queue $sourceName actually starting" }

                                // Sequential processing within source queue
                                for (index in sourceQueue.indices) {
                                    val candidate = sourceQueue[index]
                                    if (index > 0) {
                                        val delayMillis = if (eu.kanade.tachiyomi.BuildConfig.DEBUG) {
                                            Random.nextLong(5, 11) * 1000
                                        } else {
                                            Random.nextLong(120, 301) * 1000
                                        }
                                        logcat(LogPriority.INFO) {
                                            "Waiting ${delayMillis / 1000} seconds before next $sourceName refresh"
                                        }
                                        delay(delayMillis)
                                    }

                                    val status = processCandidate(candidate, simulationMode)
                                    processedCount.incrementAndGet()

                                    when (status) {
                                        RefreshStatus.UPDATED -> updatedCount.incrementAndGet()
                                        RefreshStatus.FAILED_ORDINARY, RefreshStatus.FAILED_TRANSIENT -> failedCount.incrementAndGet()
                                        RefreshStatus.FAILED_RATE_LIMITED -> {
                                            failedCount.incrementAndGet()
                                            logcat(LogPriority.WARN) { "Source $sourceName queue stopped due to rate limiting (429)." }
                                            stoppedQueuesCount.incrementAndGet()
                                            return@withPermit // Release slot and stop this source
                                        }
                                        RefreshStatus.FAILED_BLOCKED -> {
                                            failedCount.incrementAndGet()
                                            logcat(LogPriority.WARN) { "Source $sourceName queue stopped due to protection/blocking (403/Cloudflare)." }
                                            stoppedQueuesCount.incrementAndGet()
                                            return@withPermit // Release slot and stop this source
                                        }
                                        else -> {}
                                    }
                                }
                            } finally {
                                logcat(LogPriority.INFO) { "Source queue $sourceName released global concurrency slot" }
                            }
                        }
                    }
                }.awaitAll()
            }

            logcat(LogPriority.INFO) {
                "Background refresh summary: Processed ${processedCount.get()}, Updated ${updatedCount.get()}, Failed ${failedCount.get()}, Source queues stopped: ${stoppedQueuesCount.get()}"
            }

            if (newlyFoundInboxItems.isNotEmpty() && libraryPreferences.notifyTrackedUpdates.get()) {
                UpdateWatchNotifier(applicationContext).showUpdateNotification(newlyFoundInboxItems)
            }

            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.failure()
        }
    }

    private suspend fun processCandidate(
        candidate: RefreshCandidate,
        simulationMode: Int = SIM_NONE
    ): RefreshStatus {
        val manga = getManga.await(candidate.mangaId) ?: return RefreshStatus.SKIPPED
        val source = sourceManager.getOrStub(manga.source)
        val startTime = System.currentTimeMillis()

        logcat(LogPriority.INFO) {
            "Background refresh: Starting ${candidate.title} (Source: ${source.name}) at ${java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(startTime))}"
        }

        // Update last check timestamp immediately before refresh starts
        manageUpdateWatch.updateLastBackgroundCheckAt(candidate.mangaId, startTime)

        if (simulationMode != SIM_NONE && !simulationTriggered.getAndSet(true)) {
            val simulatedException = when (simulationMode) {
                SIM_HTTP_429 -> HttpException(429)
                SIM_HTTP_403 -> HttpException(403)
                SIM_TRANSIENT -> java.net.SocketTimeoutException("Simulated timeout")
                SIM_ORDINARY -> Exception("Simulated ordinary failure")
                else -> null
            }

            if (simulatedException != null) {
                val type = classifyFailure(simulatedException)
                logcat(LogPriority.WARN) { "SIMULATED FAILURE for ${candidate.title} (Source: ${source.name}) [$type]" }

                return when (type) {
                    FailureType.RATE_LIMITED -> {
                        recordHistory(candidate, false, 0, UpdateWatchHistory.FailureCategory.RATE_LIMITED, "Simulated 429")
                        RefreshStatus.FAILED_RATE_LIMITED
                    }
                    FailureType.BLOCKED -> {
                        recordHistory(candidate, false, 0, UpdateWatchHistory.FailureCategory.ACCESS_BLOCKED_OR_CLOUDFLARE, "Simulated 403")
                        RefreshStatus.FAILED_BLOCKED
                    }
                    FailureType.TRANSIENT -> {
                        recordHistory(candidate, false, 0, UpdateWatchHistory.FailureCategory.TRANSIENT_NETWORK, "Simulated timeout")
                        RefreshStatus.FAILED_TRANSIENT
                    }
                    FailureType.ORDINARY -> {
                        recordHistory(candidate, false, 0, UpdateWatchHistory.FailureCategory.UNKNOWN, "Simulated error")
                        RefreshStatus.FAILED_ORDINARY
                    }
                }
            }
        }

        return try {
            // Get latest chapter before refresh
            val oldChapters = chapterRepository.getChapterByMangaId(manga.id)
            val oldLatest = oldChapters.filter { it.dateUpload > 0 }.maxByOrNull { it.dateUpload }

            val refreshResult = updateMangaFromRemote(manga, fetchChapters = true)

            if (refreshResult.isSuccess) {
                val newChapters = refreshResult.getOrThrow().newChapters
                val currentChapters = chapterRepository.getChapterByMangaId(manga.id)
                val newLatest = currentChapters.filter { it.dateUpload > 0 }.maxByOrNull { it.dateUpload }

                val foundNew = newChapters.isNotEmpty() || (newLatest != null && newLatest.id != oldLatest?.id)
                val newCount = newChapters.size.coerceAtLeast(if (foundNew) 1 else 0)

                if (foundNew && newLatest != null) {
                    val range = if (newChapters.size > 1) {
                        val sorted = newChapters.sortedBy { it.chapterNumber }
                        val min = sorted.first().chapterNumber
                        val max = sorted.last().chapterNumber

                        val isContiguous = (max - min).toInt() == newChapters.size - 1
                        if (isContiguous) {
                            "Ch. ${formatChapterNumber(min)}–${formatChapterNumber(max)}"
                        } else {
                            // Compact list if too many
                            if (newChapters.size > 3) {
                                "Ch. ${formatChapterNumber(min)}, ..., ${formatChapterNumber(max)}"
                            } else {
                                "Ch. " + sorted.joinToString(", ") { formatChapterNumber(it.chapterNumber) }
                            }
                        }
                    } else {
                        "Ch. ${formatChapterNumber(newLatest.chapterNumber)}"
                    }

                    val item = UpdateWatchInboxItem(
                        mangaId = manga.id,
                        mangaTitle = manga.title,
                        sourceId = source.id,
                        sourceName = source.name,
                        chapterCount = newCount,
                        chapterRange = range,
                        firstFoundAt = startTime,
                        lastFoundAt = startTime,
                        latestChapterId = newLatest.id,
                        latestChapterNumber = newLatest.chapterNumber,
                        chapterIds = newChapters.map { it.id }.ifEmpty { listOf(newLatest.id) },
                        latestChapterUploadAt = newLatest.dateUpload,
                    )
                    manageUpdateWatchInbox.insertOrMerge(item)
                    manageUpdateWatch.resetStaleMilestone(manga.id)
                    synchronized(newlyFoundInboxItems) {
                        newlyFoundInboxItems.add(item)
                    }
                }

                logcat(LogPriority.INFO) {
                    "Refresh SUCCESS for ${candidate.title}. " +
                    "Old latest: ${oldLatest?.chapterNumber ?: "None"}, " +
                    "New latest: ${newLatest?.chapterNumber ?: "None"}. " +
                    "New chapters found: $foundNew"
                }

                recordHistory(candidate, true, newCount, UpdateWatchHistory.FailureCategory.NONE, null)

                if (foundNew) RefreshStatus.UPDATED else RefreshStatus.SUCCESS
            } else {
                val exception = refreshResult.exceptionOrNull()
                val type = classifyFailure(exception)
                val error = exception?.message ?: "Unknown error"

                logcat(LogPriority.WARN) { "Refresh FAILED for ${candidate.title} [$type]: $error" }

                val (category, safeDetail) = mapFailureToHistory(type, exception)
                recordHistory(candidate, false, 0, category, safeDetail)

                when (type) {
                    FailureType.RATE_LIMITED -> RefreshStatus.FAILED_RATE_LIMITED
                    FailureType.BLOCKED -> RefreshStatus.FAILED_BLOCKED
                    FailureType.TRANSIENT -> RefreshStatus.FAILED_TRANSIENT
                    FailureType.ORDINARY -> RefreshStatus.FAILED_ORDINARY
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error refreshing ${candidate.title}" }
            recordHistory(candidate, false, 0, UpdateWatchHistory.FailureCategory.UNKNOWN, e.message?.take(100))
            RefreshStatus.FAILED_ORDINARY
        }
    }

    private suspend fun recordHistory(
        candidate: RefreshCandidate,
        success: Boolean,
        newChapters: Int,
        category: UpdateWatchHistory.FailureCategory,
        detail: String?
    ) {
        try {
            manageUpdateWatchHistory.insert(
                UpdateWatchHistory(
                    mangaId = candidate.mangaId,
                    timestamp = System.currentTimeMillis(),
                    success = success,
                    newChapters = newChapters,
                    category = category,
                    detail = detail?.take(150),
                )
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to record refresh history for ${candidate.title}" }
        }
    }

    private fun mapFailureToHistory(
        type: FailureType,
        e: Throwable?
    ): Pair<UpdateWatchHistory.FailureCategory, String?> {
        val category = when (type) {
            FailureType.RATE_LIMITED -> UpdateWatchHistory.FailureCategory.RATE_LIMITED
            FailureType.BLOCKED -> UpdateWatchHistory.FailureCategory.ACCESS_BLOCKED_OR_CLOUDFLARE
            FailureType.TRANSIENT -> UpdateWatchHistory.FailureCategory.TRANSIENT_NETWORK
            FailureType.ORDINARY -> {
                val msg = e?.message ?: ""
                when {
                    msg.contains("Source not installed", ignoreCase = true) -> UpdateWatchHistory.FailureCategory.SOURCE_NOT_INSTALLED
                    msg.contains("HttpException", ignoreCase = true) -> UpdateWatchHistory.FailureCategory.SOURCE_OR_PARSING_ERROR
                    else -> UpdateWatchHistory.FailureCategory.SOURCE_OR_PARSING_ERROR
                }
            }
        }
        val safeDetail = e?.message?.let { msg ->
            // Simple sanitization: remove anything that looks like a token/URL param
            msg.replace(Regex("[?&][^= ]+=[^& ]+"), "")
                .take(150)
        }
        return category to safeDetail
    }

    private fun classifyFailure(e: Throwable?): FailureType {
        if (e == null) return FailureType.ORDINARY

        if (e is HttpException) {
            return when (e.code) {
                429 -> FailureType.RATE_LIMITED
                403 -> FailureType.BLOCKED
                in 500..599 -> FailureType.TRANSIENT
                else -> FailureType.ORDINARY
            }
        }

        val message = e.message ?: ""
        if (message.contains("Cloudflare", ignoreCase = true) ||
            message.contains("Access denied", ignoreCase = true)) {
            return FailureType.BLOCKED
        }

        if (e is java.net.SocketTimeoutException ||
            e is java.net.ConnectException ||
            e is java.net.UnknownHostException ||
            e is java.io.InterruptedIOException) {
            return FailureType.TRANSIENT
        }

        return FailureType.ORDINARY
    }

    private fun formatChapterNumber(number: Double): String {
        return if (number % 1 == 0.0) number.toInt().toString() else number.toString()
    }

    private enum class RefreshStatus {
        SUCCESS, UPDATED, FAILED_ORDINARY, FAILED_TRANSIENT, FAILED_RATE_LIMITED, FAILED_BLOCKED, SKIPPED
    }

    private enum class FailureType {
        RATE_LIMITED, BLOCKED, TRANSIENT, ORDINARY
    }

    private data class RefreshCandidate(
        val mangaId: Long,
        val title: String,
        val sourceId: Long,
        val ageDays: Long,
        val expectedIntervalDays: Int,
        val refreshProfile: String,
        val bucket: UpdateWatchRefreshHelper.PriorityBucket,
        val plannedCadence: String,
        val lastCheckAt: Long,
    )

    companion object {
        const val KEY_SIMULATION_MODE = "simulation_mode"

        const val SIM_NONE = 0
        const val SIM_HTTP_429 = 1
        const val SIM_HTTP_403 = 2
        const val SIM_TRANSIENT = 3
        const val SIM_ORDINARY = 4
    }
}
