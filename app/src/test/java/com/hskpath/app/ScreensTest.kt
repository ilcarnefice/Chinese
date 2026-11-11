package com.hskpath.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.hskpath.app.ui.GrammarScreen
import com.hskpath.app.ui.HomeScreen
import com.hskpath.app.ui.HskTheme
import com.hskpath.app.ui.PinyinScreen
import com.hskpath.app.ui.SentenceMapScreen
import com.hskpath.app.ui.SessionScreen
import com.hskpath.app.ui.SoundScreen
import com.hskpath.app.ui.StoriesScreen
import com.hskpath.app.ui.WordInfoScreen
import com.hskpath.app.ui.WordWebScreen
import com.hskpath.app.ui.WriteListScreen
import com.hskpath.app.ui.WriteScreen
import com.hskpath.core.Content
import com.hskpath.core.Curriculum
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen with the real lesson data to prove it composes without
 * crashing. Run with -Pscreenshots to save a PNG of each under app/build/screens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: AppState
    private lateinit var content: Content

    @Before fun load() {
        app = AppState(rule.activity)
        runBlocking { app.load() }
        content = app.content!!
    }

    private fun show(name: String, screen: @Composable () -> Unit) {
        rule.setContent {
            HskTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { screen() } }
        }
        rule.waitForIdle()
        if (System.getProperty("screenshots") == "true") {
            rule.onRoot().captureRoboImage("build/screens/$name.png")
        }
    }

    @Test fun learnTab() = show("1-learn") { HomeScreen(app, content) }

    @Test fun exploreTab() {
        app.tab = 1
        show("2-explore") { HomeScreen(app, content) }
    }

    @Test fun practiceTab() {
        app.tab = 2
        show("3-practice") { HomeScreen(app, content) }
    }

    @Test fun meTab() {
        app.tab = 3
        show("4-me") { HomeScreen(app, content) }
    }

    @Test fun lessonTeachesThenAsks() {
        val lesson = Curriculum.units(1, content).first().lessons.first()
        show("5-lesson-teach") { SessionScreen(app, content, SessionKind.LessonRun(lesson)) }
        rule.onNodeWithText("GOT IT").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("What does this mean?").assertExists()
        if (System.getProperty("screenshots") == "true") rule.onRoot().captureRoboImage("build/screens/6-lesson-question.png")
    }

    @Test fun grammarLesson() {
        val lesson = Curriculum.units(1, content).flatMap { it.lessons }.first { it.isGrammar }
        show("30-grammar-lesson") { SessionScreen(app, content, SessionKind.LessonRun(lesson)) }
        rule.onNodeWithText("GOT IT").performClick()
        rule.waitForIdle()
        if (System.getProperty("screenshots") == "true") rule.onRoot().captureRoboImage("build/screens/31-grammar-practice.png")
    }

    /** Leaving a lesson for another screen and coming back must not restart it. */
    @Test fun lessonKeepsItsPlaceWhenAnotherScreenIsOpenedFromIt() {
        val lesson = Curriculum.units(1, content).first().lessons.first()
        val word = lesson.words.first()
        rule.runOnUiThread { app.open(Screen.Run(SessionKind.LessonRun(lesson))) }
        show("32-lesson-resume") { com.hskpath.app.ui.HostedScreen(app, content) }
        rule.onNodeWithText("GOT IT").performClick()          // past the first teaching card
        rule.waitForIdle()
        rule.onNodeWithText("What does this mean?").assertExists()
        val live = app.entry.live!!
        val stepBefore = live.step
        org.junit.Assert.assertEquals(1, stepBefore)
        rule.runOnUiThread { app.open(Screen.Web(word.hanzi.take(1))) }   // look something up
        rule.waitForIdle()
        rule.onNodeWithText("What does this mean?").assertDoesNotExist()
        rule.runOnUiThread { app.back() }
        rule.waitForIdle()
        rule.onNodeWithText("What does this mean?").assertExists()   // same question, not the first card again
        org.junit.Assert.assertSame(live, app.entry.live)
        org.junit.Assert.assertEquals(stepBefore, app.entry.live!!.step)
        rule.runOnUiThread { app.back() }
        rule.waitForIdle()
    }

    @Test fun pinyinCanBeSwitchedOff() {
        val lesson = Curriculum.units(1, content).first().lessons.first()
        val word = lesson.words.first()
        show("33-pinyin-off") { SessionScreen(app, content, SessionKind.LessonRun(lesson)) }
        rule.onNodeWithText("GOT IT").performClick()
        rule.waitForIdle()
        if (!app.showPinyin) rule.runOnUiThread { app.togglePinyin() }
        rule.waitForIdle()
        rule.onNodeWithText(word.pinyin).assertExists()
        rule.runOnUiThread { app.togglePinyin() }
        rule.waitForIdle()
        rule.onNodeWithText(word.pinyin).assertDoesNotExist()
        rule.runOnUiThread { app.togglePinyin() }
    }

    @Test fun sentenceDrill() = show("7-sentence-drill") { SessionScreen(app, content, SessionKind.SentenceDrill(1)) }

    @Test fun levelTest() = show("8-level-test") { SessionScreen(app, content, SessionKind.LevelTest(3)) }

    @Test fun grammarDrill() = show("9-grammar-drill") {
        SessionScreen(app, content, SessionKind.GrammarRun(content.grammarOf(2).first()))
    }

    @Test fun wordMap() = show("10-word-map") { WordWebScreen(app, content, "学") }

    @Test fun sentenceMap() = show("11-sentence-map") { SentenceMapScreen(app, content, 1, "喜欢") }

    @Test fun grammarMap() = show("12-grammar-map") { GrammarScreen(app, content, 2) }

    @Test fun writing() = show("13-writing") { WriteScreen(app, content, "我") }

    @Test fun writingList() = show("14-writing-list") { WriteListScreen(app, content, 1) }

    @Test fun wordInfo() = show("15-word") { WordInfoScreen(app, content, content.lookup("朋友")!!) }

    @Test fun dialogues() = show("16-dialogues") { StoriesScreen(app, content, 1) }

    @Test fun pinyin() = show("17-pinyin") { PinyinScreen(app) }

    @Test fun trainingPlan() = show("21-plan") { com.hskpath.app.ui.PlanScreen(app, content, 1) }

    @Test fun longestTrainingPlan() = show("22-plan-hsk6") { com.hskpath.app.ui.PlanScreen(app, content, 6) }

    /** Writing characters by hand can be switched off; the plan then leaves the writing tasks out. */
    @Test fun characterWritingCanBeSkipped() {
        if (!app.showWriting) rule.runOnUiThread { app.toggleWriting() }
        show("37-plan-writing") { com.hskpath.app.ui.PlanScreen(app, content, 1) }
        assert(rule.onAllNodes(androidx.compose.ui.test.hasText("Write:", substring = true)).fetchSemanticsNodes().isNotEmpty())
        rule.runOnUiThread { app.toggleWriting() }
        rule.waitForIdle()
        assert(rule.onAllNodes(androidx.compose.ui.test.hasText("Write:", substring = true)).fetchSemanticsNodes().isEmpty())
        rule.runOnUiThread { app.toggleWriting() }
    }

    @Test fun phraseBooks() = show("23-phrase-books") { com.hskpath.app.ui.PhraseBooksScreen(app, content) }

    @Test fun phraseTopic() = show("24-phrases") { com.hskpath.app.ui.PhrasesScreen(app, content, "travel", 0) }

    @Test fun phraseDrill() = show("25-phrase-drill") { SessionScreen(app, content, SessionKind.PhraseDrill("business", 3)) }

    @Test fun mouthGuideForAnInitial() = show("18-mouth-zh") { SoundScreen(app, content, "zh", true) }

    @Test fun mouthGuideForAFinal() = show("19-mouth-u") { SoundScreen(app, content, "ü", false) }

    @Test fun mouthGuideForAnAspiratedSound() = show("26-mouth-p") { SoundScreen(app, content, "p", true) }

    @Test fun mouthGuideForANasalFinal() = show("27-mouth-ang") { SoundScreen(app, content, "ang", false) }

    /** The picture moves through the steps of a sound, and says in words what it is showing. */
    @Test fun mouthGuidePlaysItsSteps() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HskTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { SoundScreen(app, content, "zh", true) } }
        }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("Relaxed: lips together, breathing through the nose").assertExists()
        rule.mainClock.advanceTimeBy(1300)      // past the opening pause and the first movement
        rule.onNodeWithText("1. Tongue tip curled up, touching just behind the ridge").assertExists()
        if (System.getProperty("screenshots") == "true") rule.onRoot().captureRoboImage("build/screens/34-mouth-zh-step-1.png")
        rule.mainClock.advanceTimeBy(1700)
        if (System.getProperty("screenshots") == "true") rule.onRoot().captureRoboImage("build/screens/35-mouth-zh-step-2.png")
        // Naming the parts swaps in the relaxed, labelled picture.
        rule.onNodeWithText("Name the parts").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Hide names").assertExists()
        if (System.getProperty("screenshots") == "true") rule.onRoot().captureRoboImage("build/screens/36-mouth-names.png")
    }

    @Test fun everySoundGuideRenders() {
        var index by mutableStateOf(0)
        val sounds = com.hskpath.core.Sounds.all
        show("28-mouth-first") { SoundScreen(app, content, sounds[index].letters, sounds[index].isInitial) }
        for (i in sounds.indices) {
            rule.runOnUiThread { index = i }
            rule.waitForIdle()
        }
    }

    /** Every pose drawn by the app, for comparing with tools/mouth/preview.py. */
    @Test fun everyMouthPoseDraws() {
        val mouth = app.mouth!!
        val pages = com.hskpath.core.Sounds.shapes.toList().chunked(6)
        var page by mutableStateOf(0)
        show("mouth-poses-1") { PoseGrid(mouth, pages[page]) }
        for (i in 1 until pages.size) {
            rule.runOnUiThread { page = i }
            rule.waitForIdle()
            if (System.getProperty("screenshots") == "true") {
                rule.onRoot().captureRoboImage("build/screens/mouth-poses-${i + 1}.png")
            }
        }
    }

    @Composable
    private fun PoseGrid(mouth: com.hskpath.core.Mouth, names: List<String>) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            for (row in names.chunked(2)) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                    for (name in row) {
                        val typical = com.hskpath.core.Sounds.all.flatMap { it.steps }.firstOrNull { it.shape == name }
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            com.hskpath.app.ui.MouthCanvas(mouth, mouth.frame(name),
                                overlay = typical?.let { com.hskpath.app.ui.MouthOverlay(name, it.air, it.voiced) })
                            androidx.compose.material3.Text(name)
                        }
                    }
                }
            }
        }
    }

    @Test fun mouthMap() = show("29-mouth-map") { com.hskpath.app.ui.MouthMap(app.mouth!!) }

    @Test fun everyLevelRendersItsPathAndGrammar() {
        var showGrammar by mutableStateOf(false)
        show("level-1") {
            if (showGrammar) GrammarScreen(app, content, app.level) else HomeScreen(app, content)
        }
        for (level in content.levels) {
            for (grammar in listOf(false, true)) {
                rule.runOnUiThread { app.selectLevel(level); showGrammar = grammar }
                rule.waitForIdle()
                if (!grammar && level > 1 && System.getProperty("screenshots") == "true") {
                    rule.onRoot().captureRoboImage("build/screens/level-$level.png")
                }
            }
        }
        rule.runOnUiThread { app.selectLevel(1) }
    }
}
