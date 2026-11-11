package com.hskpath.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.core.Content
import com.hskpath.core.Radical

/**
 * A picture chart of the common radicals: each one with a picture of what it
 * means, its pinyin and its meaning. Tapping one says it and shows characters
 * built with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadicalsScreen(app: AppState, content: Content) {
    val radicals = content.radicals
    var picked by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Radicals")
        if (radicals.isEmpty()) return@Column
        val chosen = radicals[picked.coerceIn(radicals.indices)]
        // The radical that was tapped, large, with characters that contain it.
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(chosen.picture, fontSize = 46.sp)
                    Text(chosen.form, fontSize = 52.sp, fontWeight = FontWeight.Medium)
                    Column(Modifier.weight(1f)) {
                        Text(chosen.pinyin, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Text(chosen.meaning, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    SpeakButton(app, chosen.say)
                }
                Text("Found in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                    for (character in chosen.examples) {
                        val word = content.lookup(character)
                        Raised(MaterialTheme.colorScheme.surface, edge = 4.dp, edgeColor = MaterialTheme.colorScheme.outlineVariant,
                            onClick = { if (word != null) app.open(Screen.WordInfo(word)) else app.speaker.speak(character) }) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(character, fontSize = 26.sp)
                                if (word != null) {
                                    Text(word.pinyin, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                    Text(word.shortGloss, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 84.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Radicals are the building blocks of characters. They often tell you what a character is about. Tap one.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp))
            for (row in radicals.indices.chunked(4)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in row) RadicalTile(radicals[i], i == picked, Modifier.weight(1f)) {
                        picked = i
                        app.speaker.speak(radicals[i].say)
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One square of the chart: picture, radical, pinyin, meaning. */
@Composable
private fun RadicalTile(radical: Radical, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val edge = if (selected) Sky else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Sky.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(2.dp, edge, RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(radical.picture, fontSize = 30.sp)
        Text(radical.form, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Text(radical.pinyin, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Text(radical.meaning, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, minLines = 2, maxLines = 2,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
