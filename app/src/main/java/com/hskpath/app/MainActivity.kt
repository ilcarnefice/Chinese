package com.hskpath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
                                is Screen.WordInfo -> WordInfoScreen(app, content, screen.word)
                                Screen.PinyinGuide -> PinyinScreen(app)
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
