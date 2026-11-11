package com.hskpath.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
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
        val lesson = Curriculum.units(1, content.wordsOf(1)).first().lessons.first()
        show("5-lesson-teach") { SessionScreen(app, content, SessionKind.LessonRun(lesson)) }
        rule.onNodeWithText("GOT IT").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("What does this mean?").assertExists()
        if (System.getProperty("screenshots") == "true") rule.onRoot().captureRoboImage("build/screens/6-lesson-question.png")
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

    @Test fun phraseBooks() = show("23-phrase-books") { com.hskpath.app.ui.PhraseBooksScreen(app, content) }

    @Test fun phraseTopic() = show("24-phrases") { com.hskpath.app.ui.PhrasesScreen(app, content, "travel", 0) }

    @Test fun phraseDrill() = show("25-phrase-drill") { SessionScreen(app, content, SessionKind.PhraseDrill("business", 3)) }

    @Test fun mouthGuideForAnInitial() = show("18-mouth-zh") { SoundScreen(app, content, "zh", true) }

    @Test fun mouthGuideForAFinal() = show("19-mouth-u") { SoundScreen(app, content, "ü", false) }

    @Test fun everyMouthShapeDraws() {
        var index by mutableStateOf(0)
        val names = com.hskpath.core.Sounds.shapes.toList()
        show("20-mouth-shapes") { com.hskpath.app.ui.MouthDiagram(com.hskpath.app.ui.MouthPoses.getValue(names[index])) }
        for (i in names.indices) {
            rule.runOnUiThread { index = i }
            rule.waitForIdle()
            if (System.getProperty("screenshots") == "true") rule.onRoot().captureRoboImage("build/screens/mouth-${names[i]}.png")
        }
    }

    @Test fun everyLevelRendersItsPathAndGrammar() {
        var showGrammar by mutableStateOf(false)
        show("level-1") {
            if (showGrammar) GrammarScreen(app, content, app.level) else HomeScreen(app, content)
        }
        for (level in content.levels) {
            for (grammar in listOf(false, true)) {
                rule.runOnUiThread { app.selectLevel(level); showGrammar = grammar }
                rule.waitForIdle()
            }
        }
        rule.runOnUiThread { app.selectLevel(1) }
    }
}
