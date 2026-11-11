package com.hskpath.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hskpath.core.Content
import com.hskpath.core.Exercise
import com.hskpath.core.Session
import com.hskpath.core.SessionResult
import com.hskpath.core.GrammarPoint
import com.hskpath.core.Lesson
import com.hskpath.core.Mouth
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
    data class PhraseDrill(val bookId: String, val topic: Int) : SessionKind
}

sealed interface Screen {
    data object Home : Screen
    /** [planKey] ticks off a training-plan task when the session is finished. */
    data class Run(val kind: SessionKind, val planKey: String? = null) : Screen
    data class Plan(val level: Int) : Screen
    data object PhraseBooks : Screen
    data class Phrases(val bookId: String, val topic: Int) : Screen
    data class Grammar(val level: Int) : Screen
    data class Stories(val level: Int) : Screen
    data class Webs(val level: Int) : Screen
    data class Web(val character: String) : Screen
    data class SentenceMap(val level: Int, val word: String? = null) : Screen
    data class WriteList(val level: Int) : Screen
    data class Write(val character: String) : Screen
    data class WordInfo(val word: Word) : Screen
    data object PinyinGuide : Screen
    data object Radicals : Screen
    data class SoundGuide(val letters: String, val isInitial: Boolean) : Screen
    data object Search : Screen
    data object About : Screen
}

/** A lesson or drill that is under way: the exercises and how far the learner has got. */
class LiveSession(val exercises: List<Exercise>, retryMistakes: Boolean) {
    val session = Session(exercises, retryMistakes)
    var step by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var result by mutableStateOf<SessionResult?>(null)
}

/** App-wide state: loaded lessons, saved progress, navigation and speech. */
class AppState(private val context: Context) {
    val speaker = Speaker(context)
    private val prefs = context.getSharedPreferences("hskpath", Context.MODE_PRIVATE)

    var content by mutableStateOf<Content?>(null)
        private set
    var strokes by mutableStateOf<StrokeBook?>(null)
        private set
    /** The mouth-position diagrams for the sound guides. */
    var mouth by mutableStateOf<Mouth?>(null)
        private set
    /** Selected bottom tab on the home screen. */
    var tab by mutableIntStateOf(0)
    var progress by mutableStateOf(Progress.fromJson(prefs.getString(KEY_PROGRESS, null)))
        private set
    var level by mutableIntStateOf(prefs.getInt(KEY_LEVEL, 1))
        private set

    /** One screen on the back stack. Its id keys the state the screen keeps while covered. */
    class Entry(val screen: Screen, val id: Int) {
        /** A lesson in progress on this screen, so it survives a visit to another screen. */
        var live: LiveSession? = null
    }

    private var nextId = 1
    private val stack = mutableStateListOf(Entry(Screen.Home, 0))
    /** Ids of entries that were closed; their saved state can be dropped. */
    val closed = mutableStateListOf<Int>()
    val entry: Entry get() = stack.last()
    val screen: Screen get() = entry.screen
    val canGoBack: Boolean get() = stack.size > 1

    /** Whether pinyin is shown above characters in lessons. */
    var showPinyin by mutableStateOf(prefs.getBoolean(KEY_PINYIN, true))
        private set

    fun togglePinyin() {
        showPinyin = !showPinyin
        prefs.edit().putBoolean(KEY_PINYIN, showPinyin).apply()
    }

    /** Whether writing characters by hand is offered (daily plan tasks, practice card). */
    var showWriting by mutableStateOf(prefs.getBoolean(KEY_WRITING, true))
        private set

    fun toggleWriting() {
        showWriting = !showWriting
        prefs.edit().putBoolean(KEY_WRITING, showWriting).apply()
    }

    /** Today as a day number in the user's time zone. */
    fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }

    suspend fun load() {
        val read = { path: String -> context.assets.open(path).bufferedReader().use { it.readText() } }
        content = withContext(Dispatchers.IO) { Content.load(read) }
        strokes = withContext(Dispatchers.IO) { StrokeBook(read("strokes.tsv")) }
        mouth = withContext(Dispatchers.IO) {
            Mouth.parse(read("mouth.json")).also { com.hskpath.app.ui.MouthPictures.warm(context, it) }
        }
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
        stack.add(Entry(screen, nextId++))
    }

    fun back() {
        speaker.stop()
        if (stack.size > 1) closed.add(stack.removeAt(stack.lastIndex).id)
    }

    private companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_LEVEL = "level"
        const val KEY_PINYIN = "pinyin"
        const val KEY_WRITING = "writing"
    }
}
