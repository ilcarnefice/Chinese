package com.hskpath.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hskpath.app.AppState
import com.hskpath.core.Content

/**
 * How one character is built and how to remember it: its parts, each marked as
 * giving the meaning, the sound or a picture, and a short note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TipBlock(app: AppState, content: Content, character: String, modifier: Modifier = Modifier) {
    val tip = content.tip(character) ?: return
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Gold.copy(alpha = 0.12f)).padding(12.dp)) {
        Text("💡 How to remember $character", fontWeight = FontWeight.ExtraBold)
        if (tip.parts.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (part in tip.parts) {
                    val (label, color) = when (part.role) {
                        "meaning" -> "meaning" to Jade
                        "sound" -> "sound" to Sky
                        "picture" -> "picture" to Tangerine
                        "simplified" -> "short form" to Grape
                        else -> "part" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val known = content.lookup(part.char)
                    // What the part meant when the character was made (子 is "child" here, not a suffix).
                    val gloss = content.tip(part.char)?.meaning?.takeIf { it.isNotBlank() } ?: known?.shortGloss.orEmpty()
                    Column(
                        Modifier.widthIn(min = 72.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, color, RoundedCornerShape(14.dp)).clickable { app.speaker.speak(part.char) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(part.char, fontSize = 28.sp)
                        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (known != null && part.role == "sound") {
                            Text(known.pinyin, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        } else if (gloss.isNotEmpty()) {
                            Text(gloss, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(max = 96.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        if (tip.note.isNotEmpty()) Text(tip.note, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * A pop-up about one character or word, opened from an answer option: how it
 * sounds, how to remember it, and writing practice without leaving the question.
 */
@Composable
fun CharacterPopup(app: AppState, content: Content, text: String, onDismiss: () -> Unit) {
    val word = content.lookup(text)
    val characters = remember(text) { content.charactersOf(text) }
    // The character being written, when the pop-up has been switched to writing practice.
    var writing by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text, fontSize = 44.sp, fontWeight = FontWeight.Medium)
                    Column(Modifier.weight(1f)) {
                        if (word != null) {
                            Text(word.pinyin, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text(word.shortGloss, maxLines = 2)
                        }
                    }
                    SpeakButton(app, text)
                    Pill("✕", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, onDismiss)
                }
                val practising = writing
                if (practising == null) {
                    Column(Modifier.padding(top = 12.dp).heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        var any = false
                        for (character in characters) {
                            if (content.tip(character) != null) any = true
                            TipBlock(app, content, character, Modifier.padding(bottom = 10.dp))
                        }
                        if (!any) Text("No memory tip for this one yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (character in characters) {
                        if (app.strokes?.strokes(character) != null) {
                            BigButton("✍ Practise writing $character", Modifier.padding(top = 8.dp), color = Coral) { writing = character }
                        }
                    }
                } else {
                    WritePad(app, content, practising, Modifier.fillMaxWidth().height(500.dp))
                    Pill("← Back to the tip", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) {
                        writing = null
                    }
                }
            }
        }
    }
}
