# HSK Path

An offline Android app for learning Mandarin Chinese through the six HSK levels,
in short game-like lessons.

## What is in it

| Part | What you get |
| --- | --- |
| Learning path | All 5,001 words of HSK 1-6, split into units of 20 words and lessons of 5, each unit ending in a review |
| Lessons | Each new word is introduced with audio, meaning, characters and examples, then drilled: meaning, characters, pinyin and tones, listening, pair matching, sentence building. Missed questions come back at the end |
| Word webs | Tap any character to see the words built from it branching out around it; tap a branch to hear it or jump to the next web |
| Grammar structures | 39 patterns drawn as colour-coded building blocks, each with an explanation, highlighted examples and a sentence-building drill |
| Dialogues | 9 short conversations (HSK 1-4) with line-by-line audio and "play all" |
| Pinyin guide | The tones with pitch contours, tone-change rules, and every initial and final with a sample to hear |
| Practice | Spaced repetition: learned words return after 1 day, 3 days, then growing intervals |
| Level tests | A 24-question test per level; 80% unlocks the whole level |
| Dictionary | Search by characters, pinyin (tones optional) or English |
| Motivation | XP, a daily goal and a day streak |

Pronunciation uses the phone's own text-to-speech voice, so a Chinese voice must be
installed (Settings → Accessibility → Text-to-speech). The app says so if it is missing.

## Building

Open the folder in Android Studio, or run:

```
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew :core:test             # unit tests
```

Every push also builds the APK on GitHub Actions; download it from the run's
"hsk-path-debug-apk" artifact.

## Layout

- `core/` — plain Kotlin, no Android: content parsing, pinyin, lesson and exercise
  generation, sessions, spaced repetition and progress. All unit tests live here and
  run against the real lesson data. `./gradlew -PcoreOnly :core:test` runs them
  without the Android SDK.
- `app/` — the Jetpack Compose interface, text-to-speech and saved progress.
- `app/src/main/assets/` — the lesson data as plain text: word lists, example
  sentences, grammar and dialogues. Edit these files to add or correct content.
- `tools/build_wordlists.py` — regenerates the word lists from open data.

## Content sources

- Word lists: the published HSK 2.0 lists (levels 1-6), via the
  [chinese-lexicon](https://www.npmjs.com/package/chinese-lexicon) package (ISC).
- Definitions and readings: [CC-CEDICT](https://cc-cedict.org/), licensed
  [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The files in
  `app/src/main/assets/words/` are derived from it and carry the same licence.
- Beginner glosses for HSK 1-2, the example sentences, grammar notes and dialogues
  were written for this app.

The app is independent and not affiliated with any textbook publisher or with the
organisations that run the HSK exam.
