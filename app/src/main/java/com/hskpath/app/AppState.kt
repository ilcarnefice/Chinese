package com.hskpath.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hskpath.core.Content
import com.hskpath.core.GrammarPoint
import com.hskpath.core.Lesson
import com.hskpath.core.Progress
import com.hskpath.core.StrokeBook
import com.hskpath.core.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** What a practice session is made of. */
sealed interface SessionKind {
    data class LessonRun(val lesson: Lesson) : SessionKind
    data object Practice : SessionKind
    data class LevelTest(val level: Int) : SessionKind
    data class GrammarRun(val point: GrammarPoint) : SessionKind
    data class SentenceDrill(val level: Int) : SessionKind
}

sealed interface Screen {
    data object Home : Screen
    data class Run(val kind: SessionKind) : Screen
    data class Grammar(val level: Int) : Screen
    data class Stories(val level: Int) : Screen
    data class Webs(val level: Int) : Screen
    data class Web(val character: String) : Screen
    data class SentenceMap(val level: Int, val word: String? = null) : Screen
    data class WriteList(val level: Int) : Screen
    data class Write(val character: String) : Screen
    data class WordInfo(val word: Word) : Screen
    data object PinyinGuide : Screen
    data object Search : Screen
    data object About : Screen
}

/** App-wide state: loaded lessons, saved progress, navigation and speech. */
class AppState(private val context: Context) {
    val speaker = Speaker(context)
    private val prefs = context.getSharedPreferences("hskpath", Context.MODE_PRIVATE)

    var content by mutableStateOf<Content?>(null)
        private set
    var strokes by mutableStateOf<StrokeBook?>(null)
        private set
    /** Selected bottom tab on the home screen. */
    var tab by mutableIntStateOf(0)
    var progress by mutableStateOf(Progress.fromJson(prefs.getString(KEY_PROGRESS, null)))
        private set
    var level by mutableIntStateOf(prefs.getInt(KEY_LEVEL, 1))
        private set

    private val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** Today as a day number in the user's time zone. */
    fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }

    suspend fun load() {
        val read = { path: String -> context.assets.open(path).bufferedReader().use { it.readText() } }
        content = withContext(Dispatchers.IO) { Content.load(read) }
        strokes = withContext(Dispatchers.IO) { StrokeBook(read("strokes.tsv")) }
    }

    fun save(updated: Progress) {
        progress = updated
        prefs.edit().putString(KEY_PROGRESS, updated.toJson()).apply()
    }

    fun selectLevel(value: Int) {
        level = value
        prefs.edit().putInt(KEY_LEVEL, value).apply()
    }

    fun open(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        speaker.stop()
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    private companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_LEVEL = "level"
    }
}
