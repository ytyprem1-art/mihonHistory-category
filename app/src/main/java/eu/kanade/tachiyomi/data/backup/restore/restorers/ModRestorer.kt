package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupLinkedSourceGroup
import eu.kanade.tachiyomi.data.backup.models.BackupManualHistoryGroup
import eu.kanade.tachiyomi.data.backup.models.BackupUpdateWatch
import tachiyomi.domain.history.group.interactor.ManageHistoryGroups
import tachiyomi.domain.history.interactor.ManageUpdateWatch
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.repository.LinkedSourceRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlinx.coroutines.flow.first

class ModRestorer(
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
    private val manageHistoryGroups: ManageHistoryGroups = Injekt.get(),
    private val manageUpdateWatch: ManageUpdateWatch = Injekt.get(),
    private val linkedSourceRepository: LinkedSourceRepository = Injekt.get(),
) {

    suspend fun restoreGroups(
        backupLinkedSourceGroups: List<BackupLinkedSourceGroup>,
        backupManualHistoryGroups: List<BackupManualHistoryGroup>,
        backupUpdateWatch: List<BackupUpdateWatch>,
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
            } else {
                skippedCount++
            }
        }

        return skippedCount
    }
}
