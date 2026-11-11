package com.hskpath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.hskpath.app.ui.Mood
import com.hskpath.app.ui.Panda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hskpath.app.ui.AboutScreen
import com.hskpath.app.ui.GrammarScreen
import com.hskpath.app.ui.HomeScreen
import com.hskpath.app.ui.HskTheme
import com.hskpath.app.ui.PinyinScreen
import com.hskpath.app.ui.SearchScreen
import com.hskpath.app.ui.SoundScreen
import com.hskpath.app.ui.SentenceMapScreen
import com.hskpath.app.ui.WriteListScreen
import com.hskpath.app.ui.WriteScreen
import com.hskpath.app.ui.SessionScreen
import com.hskpath.app.ui.StoriesScreen
import com.hskpath.app.ui.WebListScreen
import com.hskpath.app.ui.WordInfoScreen
import com.hskpath.app.ui.WordWebScreen

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(this)
        setContent {
            HskTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(Unit) { app.load() }
                    BackHandler(enabled = app.canGoBack) { app.back() }
                    val content = app.content
                    if (content == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Panda(Mood.HAPPY, size = 120.dp)
                                Text("加载中… Loading lessons", modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    } else {
                        val screen = app.screen
                        key(screen) {
                            when (screen) {
                                Screen.Home -> HomeScreen(app, content)
                                is Screen.Run -> SessionScreen(app, content, screen.kind)
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
                }
            }
        }
    }

    override fun onDestroy() {
        app.speaker.shutdown()
        super.onDestroy()
    }
}
