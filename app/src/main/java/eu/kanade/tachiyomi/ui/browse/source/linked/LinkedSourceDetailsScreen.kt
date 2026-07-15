package eu.kanade.tachiyomi.ui.browse.source.linked

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.presentation.browse.LinkedSourceDetailsScreen
import eu.kanade.presentation.util.Screen

class LinkedSourceDetailsScreen(private val groupId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LinkedSourceDetailsScreenModel(groupId) }
        val state by screenModel.state.collectAsState()

        LinkedSourceDetailsScreen(
            group = state.group,
            members = state.members,
            onClickMember = { navigator.push(MangaScreen(it.manga.id)) },
            navigateUp = navigator::pop,
        )
    }
}
