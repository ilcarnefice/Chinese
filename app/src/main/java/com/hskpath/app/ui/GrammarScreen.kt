package com.hskpath.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.app.SessionKind
import com.hskpath.core.Content
import com.hskpath.core.GrammarPoint

private val SlotColors = listOf(Color(0xFF1E88E5), Color(0xFF5E35B1), Color(0xFF00897B), Color(0xFF6D4C41))

/** Grammar of a level, each point drawn as a row of building blocks. */
@Composable
fun GrammarScreen(app: AppState, content: Content, level: Int) {
    val points = content.grammarOf(level)
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Grammar · HSK $level")
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Red blocks are fixed Chinese words. The other blocks are slots you fill with your own words.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(points) { point -> GrammarCard(app, point) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrammarCard(app: AppState, point: GrammarPoint) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(point.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            for (row in point.structure) {
                var slot = 0
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                    row.forEachIndexed { i, block ->
                        if (i > 0) Text("+", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically))
                        val color = if (block.isKeyword) MaterialTheme.colorScheme.primary
                        else SlotColors[slot++ % SlotColors.size]
                        Surface(color = if (block.isKeyword) color else color.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
                            Text(block.text, fontSize = if (block.isKeyword) 22.sp else 16.sp, fontWeight = FontWeight.Bold,
                                color = if (block.isKeyword) MaterialTheme.colorScheme.onPrimary else color,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }
            Text(point.explanation, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            for (example in point.examples) SentenceRow(app, example) { point.isKeyword(it) }
            OutlinedButton(onClick = { app.open(Screen.Run(SessionKind.GrammarRun(point))) },
                modifier = Modifier.padding(top = 6.dp)) {
                Text("Practise this structure")
            }
        }
    }
}
