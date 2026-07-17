package eu.kanade.tachiyomi.ui.mod.updatewatch.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.ManageUpdateWatch
import tachiyomi.domain.history.model.UpdateWatch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

class UpdateWatchRefreshWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val manageUpdateWatch: ManageUpdateWatch = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val chapterRepository: ChapterRepository = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()

    override suspend fun doWork(): Result = withIOContext {
        try {
            val trackedManga: List<UpdateWatch> = manageUpdateWatch.subscribeAll().first()
            val candidates = mutableListOf<RefreshCandidate>()

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
                        candidates.add(
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

            if (candidates.isEmpty()) {
                logcat(LogPriority.INFO) { "Background refresh dry run: No eligible candidates." }
                return@withIOContext Result.success()
            }

            // Group by source
            val groupedBySource = candidates.groupBy { it.sourceId }
            val finalCandidates = mutableListOf<RefreshCandidate>()

            val now = System.currentTimeMillis()

            groupedBySource.forEach { (sourceId, sourceCandidates) ->
                // Order by longest since last check
                val ordered = sourceCandidates.sortedBy { it.lastCheckAt }

                // Apply per-source cap of 10
                val capped = ordered.take(10)
                finalCandidates.addAll(capped)

                val sourceName = sourceManager.getOrStub(sourceId).name
                logcat(LogPriority.INFO) {
                    "Source: $sourceName ($sourceId) has ${capped.size} capped candidates (total ${ordered.size})"
                }

                capped.forEach { candidate ->
                    logcat(LogPriority.INFO) {
                        "  Candidate: ${candidate.title} (ID: ${candidate.mangaId}), Age: ${candidate.ageDays}d, Expected: ${candidate.expectedIntervalDays}d, Profile: ${candidate.refreshProfile}, Cadence: ${candidate.plannedCadence}"
                    }
                    // PERSIST LAST CHECK TIMESTAMP
                    manageUpdateWatch.updateLastBackgroundCheckAt(candidate.mangaId, now)
                }
            }

            logcat(LogPriority.INFO) {
                "Background refresh dry run: ${finalCandidates.size} candidates across ${groupedBySource.size} sources"
            }

            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.failure()
        }
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
}
