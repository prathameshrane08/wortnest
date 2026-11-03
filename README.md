# Deutsch Täglich

*Jeden Tag ein bisschen besser.*

A German learning toolkit in one place:

- **Walkthrough**: a short tour on first launch. It asks your level (A1, A2 or B1) first, can add your first 20 words, then shows each section with a small demo. Replay it from the Tour button.
- **Your level**: the word of the day, quizzes, flashcards and the verb drill use the words of your level and the ones below it (A2 includes A1). A 12-question test moves you up a level; you can also set it by hand.
- **Home**: a greeting, a word of the day (the same for everyone at a level) and four progress tiles: cards due, nouns to review, grammar topics read and your level.
- **Dictionary**: German ⇄ English dictionary (about 40,000 entries) with example sentences, checked by hand for A1 to B1 words (`tidy_examples.py` and `example_fixes.tsv`). Inflected forms like *ging* or *Häuser* find their base word. About 3,300 words carry their CEFR level (A1, A2, B1).
- **Articles**: instant article lookup (works for compound nouns), an article quiz or swipeable flashcards for your level, a mistakes round, and the ending rules that predict gender.
- **Verbs**: full conjugation tables for about 8,900 verbs, with irregular forms highlighted, plus a typing drill for your level.
- **Grammar**: 26 grammar topics from A1 to B1 (cases, word order, Perfekt, adjective endings, relative clauses, passive, Konjunktiv II …) with tables, spoken examples and common mistakes.
- **Practice**: four ways to practice. Flashcards use spaced repetition on your starred words: rate each card Again, Hard, Good or Easy, or swipe right or left (20 new words a day). Choose (multiple choice), Write (type the answer) and Match (a timed pairing game) use your level's words or your saved words, German to English or English to German.

## Web app

The site is static: `index.html` plus five data files. Open it at the GitHub Pages address, or run it locally:

```
python3 -m http.server 8000
# then open http://localhost:8000
```

## Install on iPhone (no App Store)

1. Open https://prathameshrane08.github.io/wortnest/ in **Safari**.
2. Tap **Share**, then **Add to Home Screen**.

Deutsch Täglich gets its own icon, opens full screen, and works offline after the first launch. The page shows this tip once on iPhone.

## iPhone app (SwiftUI)

The SwiftUI app lives in `ios/WortNest.swiftpm`. Open that folder in Xcode (or Swift Playgrounds on iPad/Mac) and press Run.

## Android app

The Kotlin / Jetpack Compose app lives in `android/`. Open that folder in Android Studio and press Run, or build it from the terminal (needs JDK 17 and the Android SDK):

```
cd android
./gradlew assembleRelease
# APK: android/app/build/outputs/apk/release/app-release.apk
```

Copy the APK to your phone and open it to install (allow "install unknown apps" when asked).

## Rebuilding the data

```
curl -s https://kaikki.org/dictionary/German/kaikki.org-dictionary-German.jsonl \
  | python3 extract.py de_50k.txt .
python3 rescore.py de_50k.txt
python3 levels.py A1_SD1_Wortliste_02.pdf Goethe-Zertifikat_A2_Wortliste.pdf Goethe-Zertifikat_B1_Wortliste.pdf
curl -s https://kaikki.org/dictionary/German/kaikki.org-dictionary-German.jsonl | python3 examples.py
```

`levels.py` needs `pip install pypdf` and the three word lists, which are free downloads from goethe.de (Start Deutsch 1, Goethe-Zertifikat A2 and B1). Only each word's level is stored, not the lists themselves. `grammar.json` is written by hand.

`de_50k.txt` is the German list from [FrequencyWords](https://github.com/hermitdave/FrequencyWords). Copy the JSON files into `ios/WortNest.swiftpm/Resources` and `android/app/src/main/assets`. After changing them, bump `DATA_VERSION` in both `index.html` and `sw.js`, and bump `VERSION` in `sw.js`, so phones replace their offline copy.

## Credits

Dictionary data from [Wiktionary](https://en.wiktionary.org), licensed CC BY-SA, extracted by [kaikki.org](https://kaikki.org). Word frequencies from OpenSubtitles via FrequencyWords (CC BY-SA). CEFR levels from the Goethe-Institut word lists for Start Deutsch 1, Goethe-Zertifikat A2 and Goethe-Zertifikat B1.
