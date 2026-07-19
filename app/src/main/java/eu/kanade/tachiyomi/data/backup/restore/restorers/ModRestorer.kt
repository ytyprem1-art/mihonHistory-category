package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupLinkedSourceGroup
import eu.kanade.tachiyomi.data.backup.models.BackupManualHistoryGroup
import eu.kanade.tachiyomi.data.backup.models.BackupUpdateWatch
import eu.kanade.tachiyomi.data.backup.models.BackupUpdateWatchHistory
import eu.kanade.tachiyomi.data.backup.models.BackupUpdateWatchInboxItem
import eu.kanade.tachiyomi.ui.mod.updatewatch.worker.UpdateWatchRefreshScheduler
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.group.interactor.ManageHistoryGroups
import tachiyomi.domain.history.interactor.ManageUpdateWatch
import tachiyomi.domain.history.interactor.ManageUpdateWatchHistory
import tachiyomi.domain.history.interactor.ManageUpdateWatchInbox
import tachiyomi.domain.history.model.UpdateWatch
import tachiyomi.domain.history.model.UpdateWatchHistory
import tachiyomi.domain.history.model.UpdateWatchInboxItem
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.repository.LinkedSourceRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlinx.coroutines.flow.first

class ModRestorer(
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
    private val manageHistoryGroups: ManageHistoryGroups = Injekt.get(),
    private val manageUpdateWatch: ManageUpdateWatch = Injekt.get(),
    private val manageUpdateWatchInbox: ManageUpdateWatchInbox = Injekt.get(),
    private val manageUpdateWatchHistory: ManageUpdateWatchHistory = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val linkedSourceRepository: LinkedSourceRepository = Injekt.get(),
) {

    suspend fun restoreGroups(
        backupLinkedSourceGroups: List<BackupLinkedSourceGroup>,
        backupManualHistoryGroups: List<BackupManualHistoryGroup>,
        backupUpdateWatch: List<BackupUpdateWatch>,
        backupUpdateWatchInbox: List<BackupUpdateWatchInboxItem>,
        backupUpdateWatchHistory: List<BackupUpdateWatchHistory>,
        mangaUrlToIdMap: Map<Pair<Long, String>, Long>,
    ): Int {
        var skippedCount = 0

        // 1. Linked Source Groups
        if (backupLinkedSourceGroups.isNotEmpty()) {
            val dbGroups = manageLinkedSourceGroup.subscribe().first()
            backupLinkedSourceGroups.forEach { backupGroup ->
                val groupId = dbGroups.find { it.name == backupGroup.name }?.id
                    ?: manageLinkedSourceGroup.create(backupGroup.name)

                backupGroup.members.forEach { member ->
                    val mangaId = mangaUrlToIdMap[member.source to member.url]
                    if (mangaId != null) {
                        val existingGroupId = linkedSourceRepository.getGroupIdForManga(mangaId, member.source)
                        if (existingGroupId != groupId) {
                            manageLinkedSourceGroup.joinGroup(groupId, mangaId, member.source)
                        }
                    } else {
                        skippedCount++
                    }
                }
            }
        }

        // 2. Manual History Groups
        if (backupManualHistoryGroups.isNotEmpty()) {
            val dbGroups = manageHistoryGroups.getGroups()
            val memberships = manageHistoryGroups.getAllMemberships()
            backupManualHistoryGroups.forEach { backupGroup ->
                val groupId = dbGroups.find { it.name == backupGroup.name }?.id
                    ?: manageHistoryGroups.createGroup(backupGroup.name)

                backupGroup.members.forEach { member ->
                    val mangaId = mangaUrlToIdMap[member.source to member.url]
                    if (mangaId != null) {
                        if (memberships[mangaId] != groupId) {
                            manageHistoryGroups.assignMangaToGroup(mangaId, groupId)
                        }
                    } else {
                        skippedCount++
                    }
                }
            }
        }

        // 3. Update Watch
        backupUpdateWatch.forEach { watch ->
            val mangaId = mangaUrlToIdMap[watch.member.source to watch.member.url]
            if (mangaId != null) {
                manageUpdateWatch.updatePaused(mangaId, watch.isPaused)

                // Restore new fields
                manageUpdateWatch.updateBackgroundRefresh(
                    mangaId = mangaId,
                    enabled = watch.backgroundRefreshEnabled,
                    interval = watch.expectedIntervalDays,
                    profile = UpdateWatch.RefreshProfile.entries.getOrElse(watch.refreshProfile) { UpdateWatch.RefreshProfile.WEEKLY_STABLE }
                )
                watch.lastBackgroundCheckAt?.let {
                    manageUpdateWatch.updateLastBackgroundCheckAt(mangaId, it)
                }
                if (watch.lastWarnedMilestone > 0) {
                    manageUpdateWatch.updateStaleMilestone(mangaId, watch.lastWarnedMilestone)
                }
            } else {
                skippedCount++
            }
        }

        // 4. Update Watch Inbox
        backupUpdateWatchInbox.forEach { backupItem ->
            val mangaId = mangaUrlToIdMap[backupItem.member.source to backupItem.member.url]
            if (mangaId != null) {
                val latestChapterId = chapterRepository.getChapterByUrlAndMangaId(backupItem.latestChapterUrl, mangaId)?.id
                if (latestChapterId != null) {
                    val chapterIds = backupItem.chapterUrls.mapNotNull { url ->
                        chapterRepository.getChapterByUrlAndMangaId(url, mangaId)?.id
                    }

                    manageUpdateWatchInbox.insertOrMerge(
                        UpdateWatchInboxItem(
                            mangaId = mangaId,
                            mangaTitle = backupItem.mangaTitle,
                            sourceId = backupItem.member.source,
                            sourceName = backupItem.sourceName,
                            chapterCount = backupItem.chapterCount,
                            chapterRange = backupItem.chapterRange,
                            firstFoundAt = backupItem.firstFoundAt,
                            lastFoundAt = backupItem.lastFoundAt,
                            latestChapterId = latestChapterId,
                            latestChapterNumber = backupItem.latestChapterNumber,
                            chapterIds = chapterIds,
                            latestChapterUploadAt = backupItem.latestChapterUploadAt,
                            type = backupItem.type,
                            milestone = backupItem.milestone,
                        )
                    )
                }
            }
        }

        // 5. Update Watch History
        backupUpdateWatchHistory.forEach { backupHistory ->
            val mangaId = mangaUrlToIdMap[backupHistory.member.source to backupHistory.member.url]
            if (mangaId != null) {
                manageUpdateWatchHistory.insert(
                    UpdateWatchHistory(
                        mangaId = mangaId,
                        timestamp = backupHistory.timestamp,
                        success = backupHistory.success,
                        newChapters = backupHistory.newChapters,
                        category = UpdateWatchHistory.FailureCategory.entries.getOrElse(backupHistory.category) { UpdateWatchHistory.FailureCategory.UNKNOWN },
                        detail = backupHistory.detail,
                    )
                )
            }
        }

        if (backupUpdateWatch.isNotEmpty()) {
            UpdateWatchRefreshScheduler.setupTask(Injekt.get())
        }

        return skippedCount
    }
}
