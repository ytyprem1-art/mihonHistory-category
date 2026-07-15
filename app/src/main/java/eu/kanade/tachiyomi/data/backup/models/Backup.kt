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
)

@Serializable
data class BackupModMember(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
)
