package com.hskpath.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.core.Content

/**
 * Shows the screen on top of the back stack. Every screen keeps its own state
 * (scroll position, what was typed or selected, a lesson in progress) while
 * another screen is on top of it, and gets it back when the learner returns.
 */
@Composable
fun HostedScreen(app: AppState, content: Content) {
    val states = rememberSaveableStateHolder()
    // State of screens that were closed for good is dropped.
    LaunchedEffect(app.closed.size) {
        app.closed.forEach { states.removeState(it) }
        app.closed.clear()
    }
    val entry = app.entry
    states.SaveableStateProvider(entry.id) {
        when (val screen = entry.screen) {
            Screen.Home -> HomeScreen(app, content)
            is Screen.Run -> SessionScreen(app, content, screen.kind, screen.planKey, entry)
            is Screen.Plan -> PlanScreen(app, content, screen.level)
            Screen.PhraseBooks -> PhraseBooksScreen(app, content)
            is Screen.Phrases -> PhrasesScreen(app, content, screen.bookId, screen.topic)
            is Screen.Grammar -> GrammarScreen(app, content, screen.level)
            is Screen.Stories -> StoriesScreen(app, content, screen.level)
            is Screen.Webs -> WebListScreen(app, content, screen.level)
            is Screen.Web -> WordWebScreen(app, content, screen.character)
            is Screen.SentenceMap -> SentenceMapScreen(app, content, screen.level, screen.word)
            is Screen.WriteList -> WriteListScreen(app, content, screen.level)
            is Screen.Write -> WriteScreen(app, content, screen.character)
            is Screen.WordInfo -> WordInfoScreen(app, content, screen.word)
            Screen.PinyinGuide -> PinyinScreen(app)
            is Screen.SoundGuide -> SoundScreen(app, content, screen.letters, screen.isInitial)
            Screen.Search -> SearchScreen(app, content)
            Screen.About -> AboutScreen(app)
        }
    }
}
