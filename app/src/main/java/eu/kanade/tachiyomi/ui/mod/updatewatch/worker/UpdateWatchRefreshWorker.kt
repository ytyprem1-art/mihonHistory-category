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
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.ManageUpdateWatch
import tachiyomi.domain.history.model.UpdateWatch
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
    private val getManga: GetManga = Injekt.get()
    private val chapterRepository: ChapterRepository = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get()

    private val simulationTriggered = AtomicBoolean(false)

    override suspend fun doWork(): Result = withIOContext {
        try {
            val simulationMode = if (eu.kanade.tachiyomi.BuildConfig.DEBUG) {
                inputData.getInt(KEY_SIMULATION_MODE, SIM_NONE)
            } else {
                SIM_NONE
            }
            simulationTriggered.set(false)

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
                                plannedCadence = eligibility.plannedCadenceLabel ?: "Unknown",
                                lastCheckAt = lastCheck
                            )
                        )
                    }
                }
            }

            if (allCandidates.isEmpty()) {
                logcat(LogPriority.INFO) { "Background refresh: No eligible candidates." }
                return@withIOContext Result.success()
            }

            // Group by source and prioritize internally
            val groupedBySource = allCandidates.groupBy { it.sourceId }
                .mapValues { (_, list) -> list.sortedBy { it.lastCheckAt }.take(10) }
                .toList()
                .sortedBy { it.second.first().lastCheckAt } // Sources that have waited longest

            // Interleave sources to select up to 3 total candidates
            val workQueue = mutableListOf<RefreshCandidate>()
            val sourceIterators = groupedBySource.map { it.second.iterator() }
            while (workQueue.size < 3) {
                var addedAny = false
                for (iterator in sourceIterators) {
                    if (iterator.hasNext()) {
                        workQueue.add(iterator.next())
                        addedAny = true
                        if (workQueue.size >= 3) break
                    }
                }
                if (!addedAny) break
            }

            logcat(LogPriority.INFO) { "Background refresh: Identified ${workQueue.size} candidates across ${workQueue.map { it.sourceId }.distinct().size} sources." }

            val processedCount = AtomicInteger(0)
            val updatedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val stoppedQueuesCount = AtomicInteger(0)

            // Process by source queue
            val workBySource = workQueue.groupBy { it.sourceId }
            coroutineScope {
                workBySource.values.map { sourceQueue ->
                    async {
                        // Sequential processing for same source
                        for (index in sourceQueue.indices) {
                            val candidate = sourceQueue[index]
                            if (index > 0) {
                                val delayMillis = if (eu.kanade.tachiyomi.BuildConfig.DEBUG) {
                                    Random.nextLong(5, 11) * 1000
                                } else {
                                    Random.nextLong(120, 301) * 1000
                                }
                                val sourceName = sourceManager.getOrStub(candidate.sourceId).name
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
                                    val sourceName = sourceManager.getOrStub(candidate.sourceId).name
                                    logcat(LogPriority.WARN) { "Source $sourceName queue stopped due to rate limiting (429)." }
                                    stoppedQueuesCount.incrementAndGet()
                                    break // Stop this source queue
                                }
                                RefreshStatus.FAILED_BLOCKED -> {
                                    failedCount.incrementAndGet()
                                    val sourceName = sourceManager.getOrStub(candidate.sourceId).name
                                    logcat(LogPriority.WARN) { "Source $sourceName queue stopped due to protection/blocking (403/Cloudflare)." }
                                    stoppedQueuesCount.incrementAndGet()
                                    break // Stop this source queue
                                }
                                else -> {}
                            }
                        }
                    }
                }.awaitAll()
            }

            logcat(LogPriority.INFO) {
                "Background refresh summary: Processed ${processedCount.get()}, Updated ${updatedCount.get()}, Failed ${failedCount.get()}, Source queues stopped: ${stoppedQueuesCount.get()}"
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
                    FailureType.RATE_LIMITED -> RefreshStatus.FAILED_RATE_LIMITED
                    FailureType.BLOCKED -> RefreshStatus.FAILED_BLOCKED
                    FailureType.TRANSIENT -> RefreshStatus.FAILED_TRANSIENT
                    FailureType.ORDINARY -> RefreshStatus.FAILED_ORDINARY
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

                logcat(LogPriority.INFO) {
                    "Refresh SUCCESS for ${candidate.title}. " +
                    "Old latest: ${oldLatest?.chapterNumber ?: "None"}, " +
                    "New latest: ${newLatest?.chapterNumber ?: "None"}. " +
                    "New chapters found: $foundNew"
                }
                if (foundNew) RefreshStatus.UPDATED else RefreshStatus.SUCCESS
            } else {
                val exception = refreshResult.exceptionOrNull()
                val type = classifyFailure(exception)
                val error = exception?.message ?: "Unknown error"

                logcat(LogPriority.WARN) { "Refresh FAILED for ${candidate.title} [$type]: $error" }

                when (type) {
                    FailureType.RATE_LIMITED -> RefreshStatus.FAILED_RATE_LIMITED
                    FailureType.BLOCKED -> RefreshStatus.FAILED_BLOCKED
                    FailureType.TRANSIENT -> RefreshStatus.FAILED_TRANSIENT
                    FailureType.ORDINARY -> RefreshStatus.FAILED_ORDINARY
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error refreshing ${candidate.title}" }
            RefreshStatus.FAILED_ORDINARY
        }
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
