# HSK Path

An offline Android app for learning Mandarin Chinese through the six HSK levels,
in short game-like lessons.

## What is in it

| Part | What you get |
| --- | --- |
| Learning path | All 5,001 words of HSK 1-6 in units of 20 words and lessons of 5, each unit ending in a review. Lessons unlock one after another; a level test lets you jump ahead |
| Lessons | Each new word is introduced with audio, meaning, its characters and examples, then drilled: meaning, characters, pinyin and tones, listening, pair matching, fill-the-gap and sentence building. Missed questions come back at the end |
| Word Map | 11,001 words (HSK plus 6,000 common everyday words) linked by their characters: a character sits in the middle and the words built from it branch out |
| Sentence Map | About 24,300 sentences with pinyin and English, grouped by key word and graded by HSK level |
| Grammar Map | 100 formulas drawn as colour-coded building blocks, each with an explanation, examples pulled from the corpus and a sentence-building drill |
| Writing | Stroke-order animation and stroke-by-stroke tracing for all 2,663 characters in HSK 1-6 |
| Dialogues | 9 short conversations (HSK 1-4) with line-by-line audio and "play all" |
| Sounds and mouth positions | An animated side-view diagram of lips, teeth and tongue for all 21 initials and 35 finals, with instructions, the nearest English sound and practice words; plus the tones with pitch contours and the tone-change rules |
| Practice | Spaced-repetition word review, sentence workouts (translate, listen, build), grammar drills, and a 30-question test per level |
| Dictionary | Search all 11,001 words by characters, pinyin (tones optional) or English |
| Motivation | XP, ranks, a daily goal, day streaks, a weekly chart, 13 achievements, and a panda that cheers you on |

Pronunciation uses the phone's own text-to-speech voice, so a Chinese voice must be
installed (Settings → Accessibility → Text-to-speech). The app says so if it is missing.

## Building

Open the folder in Android Studio, or run:

```
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew :core:test             # logic and content tests
./gradlew :app:testDebugUnitTest # renders every screen (add -Pscreenshots to save PNGs to app/build/screens)
```

Every push also builds the APK on GitHub Actions; download it from the run's
"hsk-path-debug-apk" artifact.

## Layout

- `core/` — plain Kotlin, no Android: content parsing, pinyin, lesson and exercise
  generation, sessions, spaced repetition, stroke matching, achievements and progress.
  Its unit tests run against the real lesson data. `./gradlew -PcoreOnly :core:test`
  runs them without the Android SDK.
- `app/` — the Jetpack Compose interface, text-to-speech and saved progress.
- `app/src/main/assets/` — the lesson data as plain text: word lists, sentences,
  grammar, dialogues and strokes. Edit `content/` by hand to add or correct material.
- `tools/` — scripts that regenerate the word lists, sentence corpus and stroke data
  from the open sources below.

## Content sources and licences

- Word lists: the published HSK 2.0 lists (levels 1-6), via the
  [chinese-lexicon](https://www.npmjs.com/package/chinese-lexicon) package (ISC).
  Words beyond HSK are ranked by SUBTLEX-CH film-subtitle frequency.
- Definitions and readings: [CC-CEDICT](https://cc-cedict.org/), licensed
  [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The files in
  `app/src/main/assets/words/` are derived from it and carry the same licence.
- Sentence corpus (`app/src/main/assets/sentences/`): [Tatoeba](https://tatoeba.org),
  licensed [CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/). Word
  splitting and pinyin were added automatically and can contain occasional mistakes.
- Stroke order (`app/src/main/assets/strokes.tsv`): derived from
  [Hanzi Writer data](https://github.com/chanind/hanzi-writer-data), built on Make Me
  a Hanzi and the Arphic fonts; see `licenses/ARPHICPL.TXT`.
- Beginner glosses for HSK 1-2, about 215 hand-written example sentences, the grammar
  formulas and the dialogues were written for this app.

The app is independent. It is not affiliated with any textbook publisher or with the
organisations that run the HSK exam, and it contains no material from commercial books.

## Screens

| Learn | Explore | Lesson | Word Map |
| --- | --- | --- | --- |
| ![Learning path](docs/screens/1-learn.png) | ![Explore tab](docs/screens/2-explore.png) | ![Lesson question](docs/screens/6-lesson-question.png) | ![Word map](docs/screens/10-word-map.png) |

| Grammar Map | Sentence Map | Writing | Profile |
| --- | --- | --- | --- |
| ![Grammar map](docs/screens/12-grammar-map.png) | ![Sentence map](docs/screens/11-sentence-map.png) | ![Writing](docs/screens/13-writing.png) | ![Profile](docs/screens/4-me.png) |

| Mouth position (zh) | Mouth position (ü) |
| --- | --- |
| ![Mouth position for zh](docs/screens/18-mouth-zh.png) | ![Mouth position for ü](docs/screens/19-mouth-u.png) |
