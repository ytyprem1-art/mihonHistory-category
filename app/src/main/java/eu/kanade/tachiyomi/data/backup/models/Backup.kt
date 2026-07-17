package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensionStores: List<BackupExtensionStore> = emptyList(),

    // History Mod
    @ProtoNumber(600) var backupHistoryCategories: List<BackupHistoryCategory> = emptyList(),
    @ProtoNumber(601) var backupLinkedSourceGroups: List<BackupLinkedSourceGroup> = emptyList(),
    @ProtoNumber(602) var backupManualHistoryGroups: List<BackupManualHistoryGroup> = emptyList(),
    @ProtoNumber(603) var backupUpdateWatch: List<BackupUpdateWatch> = emptyList(),
    @ProtoNumber(604) var backupUpdateWatchInbox: List<BackupUpdateWatchInboxItem> = emptyList(),
)

@Serializable
data class BackupHistoryCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var id: Long = 0,
    @ProtoNumber(3) var sort: Int = 0,
)

@Serializable
data class BackupLinkedSourceGroup(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var members: List<BackupModMember> = emptyList(),
)

@Serializable
data class BackupManualHistoryGroup(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var members: List<BackupModMember> = emptyList(),
)

@Serializable
data class BackupUpdateWatch(
    @ProtoNumber(1) var member: BackupModMember,
    @ProtoNumber(2) var isPaused: Boolean = false,
    @ProtoNumber(3) var backgroundRefreshEnabled: Boolean = false,
    @ProtoNumber(4) var expectedIntervalDays: Int = 7,
    @ProtoNumber(5) var refreshProfile: Int = 0,
    @ProtoNumber(6) var lastBackgroundCheckAt: Long? = null,
)

@Serializable
data class BackupUpdateWatchInboxItem(
    @ProtoNumber(1) var member: BackupModMember,
    @ProtoNumber(2) var mangaTitle: String,
    @ProtoNumber(3) var sourceName: String,
    @ProtoNumber(4) var chapterCount: Int,
    @ProtoNumber(5) var chapterRange: String,
    @ProtoNumber(6) var firstFoundAt: Long,
    @ProtoNumber(7) var lastFoundAt: Long,
    @ProtoNumber(8) var latestChapterUrl: String,
    @ProtoNumber(9) var latestChapterNumber: Double,
    @ProtoNumber(10) var chapterUrls: List<String> = emptyList(),
    @ProtoNumber(11) var latestChapterUploadAt: Long = 0L,
)

@Serializable
data class BackupModMember(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
)
