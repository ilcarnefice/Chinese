package com.hskpath.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.core.Sentence

val Red = Color(0xFFB3261E)
val Teal = Color(0xFF00796B)
val Amber = Color(0xFFF9A825)
val Good = Color(0xFF2E7D32)
val Bad = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Red, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5), onPrimaryContainer = Color(0xFF410001),
    secondary = Teal, onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB), onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF7A5900),
    background = Color(0xFFFFFBF8), surface = Color(0xFFFFFBF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A9), onPrimary = Color(0xFF690002),
    primaryContainer = Color(0xFF930005), onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFF80CBC4), onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F4A), onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFF5C04A),
)

@Composable
fun HskTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Title row with a back arrow, used by every screen except the home path. */
@Composable
fun TopBar(app: AppState, title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable { app.back() }, contentAlignment = Alignment.Center) {
            Text("←", fontSize = 24.sp)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Round button that reads Chinese text aloud. */
@Composable
fun SpeakButton(app: AppState, text: String, slow: Boolean = false, big: Boolean = false) {
    val size = if (big) 72.dp else 40.dp
    Surface(
        shape = CircleShape,
        color = if (slow) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(size).clip(CircleShape).clickable { app.speaker.speak(text, slow) },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (slow) "🐢" else "🔊", fontSize = if (big) 30.sp else 18.sp)
        }
    }
}

/** Shown once where sound matters, when the phone has no Mandarin voice. */
@Composable
fun VoiceNotice(app: AppState) {
    if (!app.speaker.unavailable) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "No Chinese voice found on this device, so audio is off. Install Chinese under " +
                "Settings → Accessibility → Text-to-speech, then reopen the app.",
            modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** A sentence with characters, pinyin, translation and a speak button. */
@Composable
fun SentenceRow(app: AppState, sentence: Sentence, highlight: (String) -> Boolean = { false }) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SpeakButton(app, sentence.text)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(highlighted(sentence, highlight, MaterialTheme.colorScheme.primary), fontSize = 20.sp)
            Text(sentence.pinyin, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Text(sentence.english, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun highlighted(sentence: Sentence, highlight: (String) -> Boolean, color: Color): AnnotatedString =
    buildAnnotatedString {
        for (token in sentence.tokens) {
            if (highlight(token)) withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(token) }
            else append(token)
        }
    }

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
fun Pill(text: String, color: Color, onColor: Color, onClick: (() -> Unit)? = null) {
    Surface(color = color, shape = RoundedCornerShape(50),
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else Modifier) {
        Text(text, color = onColor, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge)
    }
}

val SpacedSmall = Arrangement.spacedBy(8.dp)
