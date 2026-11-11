package com.hskpath.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hskpath.app.AppState
import com.hskpath.app.Screen
import com.hskpath.core.Content
import com.hskpath.core.Pinyin
import com.hskpath.core.Sounds
import com.hskpath.core.Word

/** Short dialogues of a level, shown as a chat with audio for every line. */
@Composable
fun StoriesScreen(app: AppState, content: Content, level: Int) {
    val dialogues = content.dialoguesOf(level)
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Dialogues · HSK $level")
        VoiceNotice(app)
        if (dialogues.isEmpty()) {
            Text("There are no dialogues for this level yet.", Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(dialogues) { dialogue ->
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dialogue.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Pill("▶ Play all", Tangerine, Color.White) { app.speaker.speakAll(dialogue.lines.map { it.sentence.text }) }
                }
                val first = dialogue.lines.firstOrNull()?.speaker
                for (line in dialogue.lines) {
                    val mine = line.speaker == first
                    Surface(
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(
                            start = if (mine) 0.dp else 28.dp, end = if (mine) 28.dp else 0.dp, top = 8.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { SentenceRow(app, line.sentence) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class ToneExample(val tone: Int, val hanzi: String, val meaning: String, val shape: List<Pair<Float, Float>>)

private val ToneExamples = listOf(
    ToneExample(1, "妈", "mother", listOf(0f to 0.15f, 1f to 0.15f)),
    ToneExample(2, "麻", "hemp; numb", listOf(0f to 0.6f, 1f to 0.1f)),
    ToneExample(3, "马", "horse", listOf(0f to 0.55f, 0.45f to 0.9f, 1f to 0.35f)),
    ToneExample(4, "骂", "to scold", listOf(0f to 0.1f, 1f to 0.9f)),
    ToneExample(5, "吗", "(question particle)", listOf(0.4f to 0.5f, 0.6f to 0.5f)),
)

/** Sounds of Mandarin: the tones, then every initial and final with a sample to hear. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PinyinScreen(app: AppState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Pinyin and tones")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            VoiceNotice(app)
            Text("Pinyin spells the sound of each character with Latin letters. Every syllable has an initial " +
                "(the opening consonant), a final (the rest) and a tone. Tap any sound to see where your lips, " +
                "teeth and tongue go, and to hear it.")
            SectionTitle("The tones")
            Text("The same syllable means different things in different tones.", style = MaterialTheme.typography.bodyMedium)
            val lineColor = MaterialTheme.colorScheme.primary
            for (t in ToneExamples) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .clickable { app.speaker.speak(t.hanzi, slow = true) }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(width = 56.dp, height = 40.dp)) {
                        t.shape.zipWithNext().forEach { (a, b) ->
                            drawLine(lineColor, Offset(a.first * size.width, a.second * size.height),
                                Offset(b.first * size.width, b.second * size.height),
                                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(Pinyin.applyTone("ma", t.tone), fontSize = 26.sp, modifier = Modifier.width(56.dp))
                    Text(t.hanzi, fontSize = 26.sp, modifier = Modifier.width(44.dp))
                    Column {
                        Text(Pinyin.toneNames[t.tone - 1], fontWeight = FontWeight.Bold)
                        Text(t.meaning, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionTitle("Tone changes")
            Text("• Two 3rd tones in a row: the first becomes a 2nd tone. 你好 is written nǐ hǎo but said ní hǎo.\n" +
                "• 不 (bù) becomes bú before a 4th tone: 不是 bú shì.\n" +
                "• 一 (yī) becomes yí before a 4th tone and yì before other tones: 一个 yí ge, 一起 yìqǐ.")
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = SpacedSmall) {
                for (sample in listOf("你好", "不是", "一个", "一起")) {
                    Pill("🔊 $sample", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface) {
                        app.speaker.speak(sample, slow = true)
                    }
                }
            }
            app.mouth?.let { mouth ->
                SectionTitle("Inside the mouth")
                MouthMap(mouth)
            }
            SectionTitle("Initials: tap for mouth position")
            FlowRow(horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                for (sound in Sounds.initials) SoundChip(app, sound.letters, sound.example, true)
            }
            SectionTitle("Finals: tap for mouth position")
            FlowRow(horizontalArrangement = SpacedSmall, verticalArrangement = SpacedSmall) {
                for (sound in Sounds.finals) SoundChip(app, sound.letters, sound.example, false)
            }
            SectionTitle("Tricky sounds")
            Text("• j, q, x are made with the tongue flat behind the lower teeth; zh, ch, sh, r with the tongue curled back.\n" +
                "• After j, q, x and y the letter u is pronounced ü: 去 qù, 月 yuè.\n" +
                "• The i in zi, ci, si, zhi, chi, shi, ri is a buzz, not an \"ee\" sound.\n" +
                "• c sounds like \"ts\" in cats, z like \"ds\" in kids, q like \"ch\" in cheese.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Look up any HSK word by characters, pinyin or English. */
@Composable
fun SearchScreen(app: AppState, content: Content) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { content.search(query) }
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "Dictionary")
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Characters, pinyin or English") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (query.isNotBlank() && results.isEmpty()) {
            Text("No HSK word matches \"${query.trim()}\".", Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(results) { word -> WordRow(app, word) }
        }
    }
}

@Composable
private fun WordRow(app: AppState, word: Word) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { app.open(Screen.WordInfo(word)) }
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(word.hanzi, fontSize = 24.sp, modifier = Modifier.width(110.dp))
        Column(Modifier.weight(1f)) {
            Text(word.pinyin, color = MaterialTheme.colorScheme.secondary)
            Text(word.gloss, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
        Text(word.levelLabel, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WordInfoScreen(app: AppState, content: Content, word: Word) {
    Column(Modifier.fillMaxSize()) {
        TopBar(app, word.levelLabel)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            WordCard(app, content, word)
        }
    }
}

@Composable
fun AboutScreen(app: AppState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(app, "About")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("HSK Path teaches the 5,000 words of HSK levels 1 to 6 in short lessons, with a word map of 11,000 " +
                "words, a sentence map of 24,000 sentences, 100 grammar formulas, 900 phrase-book phrases, mouth " +
                "positions for every sound, stroke-order writing, day-by-day training plans, dialogues " +
                "and spaced practice. It works fully offline and keeps your progress on this device only.")
            Text("How it works", fontWeight = FontWeight.Bold)
            Text("• Each lesson teaches five words, then drills them: meaning, characters, pronunciation, " +
                "listening, matching and sentence building.\n" +
                "• A missed question comes back at the end of the lesson.\n" +
                "• Learned words return in Practice after 1 day, then 3 days, then at growing intervals.\n" +
                "• Each unit ends with a review; each level ends with a test that can unlock the whole level.")
            Text("Sources and licences", fontWeight = FontWeight.Bold)
            Text("Word definitions and readings come from CC-CEDICT (cc-cedict.org), used under the Creative " +
                "Commons Attribution-ShareAlike 4.0 licence. HSK word lists are the published HSK 2.0 lists, " +
                "taken from the chinese-lexicon project (ISC licence); words beyond HSK are ranked with SUBTLEX-CH " +
                "film-subtitle frequencies. The sentence corpus comes from Tatoeba (tatoeba.org), licensed CC BY 2.0 FR; " +
                "its word splitting and pinyin were added automatically and may contain occasional mistakes. Stroke " +
                "order comes from Hanzi Writer data, built on Make Me a Hanzi and the Arphic fonts (Arphic Public " +
                "License). Beginner glosses, the hand-written examples, grammar notes and dialogues were written for " +
                "this app. Audio is produced by your device's text-to-speech voice.")
            Text("This app is independent and is not affiliated with Hanban, Chinese Testing International, " +
                "or any textbook publisher.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}
