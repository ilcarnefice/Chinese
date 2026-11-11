package com.hskpath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hskpath.app.ui.HostedScreen
import com.hskpath.app.ui.HskTheme
import com.hskpath.app.ui.Mood
import com.hskpath.app.ui.Panda

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
                        HostedScreen(app, content)
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
