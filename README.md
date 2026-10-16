# WortNest

*Dein Zuhause für Deutsch.*

A German learning toolkit in one place:

- **Wörterbuch**: German ⇄ English dictionary (about 40,000 entries). Inflected forms like *ging* or *Häuser* find their base word.
- **Der Die Das**: instant article lookup (works for compound nouns), an article quiz with three levels and a mistakes round, and the ending rules that predict gender.
- **Verben**: full conjugation tables for about 8,900 verbs, with irregular forms highlighted, plus a typing drill.
- **Üben**: flashcards from your starred words with a five-box review schedule.

## Web app

The site is static: `index.html` plus three data files. Open it at the GitHub Pages address, or run it locally:

```
python3 -m http.server 8000
# then open http://localhost:8000
```

## Install on iPhone (no App Store)

1. Open https://prathameshrane08.github.io/wortnest/ in **Safari**.
2. Tap **Share**, then **Add to Home Screen**.

WortNest gets its own icon, opens full screen, and works offline after the first launch. The page shows this tip once on iPhone.

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
```

`de_50k.txt` is the German list from [FrequencyWords](https://github.com/hermitdave/FrequencyWords). After changing the JSON files, bump `DATA_VERSION` in both `index.html` and `sw.js`, and bump `VERSION` in `sw.js`, so phones replace their offline copy.

## Credits

Dictionary data from [Wiktionary](https://en.wiktionary.org), licensed CC BY-SA, extracted by [kaikki.org](https://kaikki.org). Word frequencies from OpenSubtitles via FrequencyWords (CC BY-SA).
