package com.prathameshrane.wortnest

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var repo by mutableStateOf<Repository?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    // Search boxes keep their text while you open an entry and come back.
    var dictQuery by mutableStateOf("")
    var verbQuery by mutableStateOf("")
    var articleQuery by mutableStateOf("")

    val saved = mutableStateMapOf<String, Card>()
    val missedNouns = mutableStateListOf<String>()
    var bestStreak by mutableIntStateOf(0)
        private set

    private val prefs = app.getSharedPreferences("wortnest", Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        restore()
        viewModelScope.launch {
            try {
                val start = System.currentTimeMillis()
                repo = withContext(Dispatchers.Default) { Repository.load(app) }
                android.util.Log.i("WortNest", "dictionary loaded in ${System.currentTimeMillis() - start} ms")
                repo?.let { r -> launch(Dispatchers.Default) { r.warmUp() } }
            } catch (e: Exception) {
                loadError = "The dictionary files could not be read: ${e.message}"
            }
        }
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.GERMANY
                tts?.setSpeechRate(0.9f)
                ttsReady = true
            }
        }
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wk")
    }

    // ---- saved words (flashcards) ----

    fun isSaved(e: Entry) = saved.containsKey(e.key)

    fun toggleSaved(e: Entry) {
        if (saved.remove(e.key) == null) saved[e.key] = Card()
        persistSaved()
    }

    /** Adds the next 20 words of a level that aren't saved yet. */
    fun addLevel(level: Int) {
        val r = repo ?: return
        val now = System.currentTimeMillis()
        r.levelWords[level].filter { r.entries[it].key !in saved }.take(20)
            .forEachIndexed { j, i -> saved[r.entries[i].key] = Card(added = now + j) }   // keep the list order
        persistSaved()
    }

    fun wordsLeft(level: Int): Int {
        val r = repo ?: return 0
        return r.levelWords[level].count { r.entries[it].key !in saved }
    }

    private val today get() = java.time.LocalDate.now().toString()
    private fun newDoneToday() = if (prefs.getString("newDay", "") == today) prefs.getInt("newCount", 0) else 0
    private fun newLeftToday() = maxOf(0, Srs.NEW_PER_DAY - newDoneToday())

    /** What to study now: due learning and review cards first (oldest first), then today's share of new cards. */
    fun studyQueue(now: Long = System.currentTimeMillis()): List<String> {
        val due = saved.filter { !it.value.isNew && it.value.due <= now }.entries.sortedBy { it.value.due }.map { it.key }
        val fresh = saved.filter { it.value.isNew }.entries.sortedBy { it.value.added }.take(newLeftToday()).map { it.key }
        if (due.isNotEmpty() || fresh.isNotEmpty()) return due + fresh
        // Rather than make you wait a few minutes, show a learning card early.
        return saved.filter { it.value.isLearning && it.value.due <= now + Srs.LEARN_AHEAD }.entries
            .sortedBy { it.value.due }.take(1).map { it.key }
    }

    data class Counts(val fresh: Int, val learning: Int, val review: Int, val tomorrow: Int, val known: Int)

    fun counts(now: Long = System.currentTimeMillis()): Counts {
        val v = saved.values
        val endOfTomorrow = java.time.LocalDate.now().plusDays(2).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Counts(
            fresh = minOf(v.count { it.isNew }, newLeftToday()),
            learning = v.count { it.isLearning && it.due <= now + Srs.LEARN_AHEAD },
            review = v.count { !it.isNew && it.step < 0 && it.due <= now },
            tomorrow = v.count { !it.isNew && it.due > now && it.due < endOfTomorrow },
            known = v.count { it.step < 0 && it.ivl >= 21 },
        )
    }

    fun hasNewCards() = saved.values.any { it.isNew }

    fun grade(key: String, grade: Int) {
        val c = saved[key] ?: return
        if (c.isNew) prefs.edit().putString("newDay", today).putInt("newCount", newDoneToday() + 1).apply()
        saved[key] = Srs.next(c, grade)
        persistSaved()
    }

    fun savedEntries(): List<Entry> {
        val r = repo ?: return emptyList()
        return saved.keys.sorted().mapNotNull { key ->
            val parts = key.split("|", limit = 2)
            if (parts.size == 2) r.entry(parts[0], parts[1]) else null
        }
    }

    // ---- article quiz memory ----

    fun recordQuiz(word: String, correct: Boolean, streak: Int) {
        if (correct) missedNouns.remove(word) else if (word !in missedNouns) missedNouns.add(word)
        if (streak > bestStreak) bestStreak = streak
        prefs.edit()
            .putString("missed", missedNouns.joinToString("\n"))
            .putInt("best", bestStreak)
            .apply()
    }

    private fun persistSaved() {
        val o = JSONObject()
        for ((k, c) in saved) o.put(k, c.toJson())
        prefs.edit().putString("saved", o.toString()).apply()
    }

    private fun restore() {
        prefs.getString("saved", null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                for (k in o.keys()) {
                    saved[k] = Card.fromJson(o.getJSONObject(k))
                }
            }
        }
        prefs.getString("missed", null)?.split("\n")?.filter { it.isNotEmpty() }?.let { missedNouns.addAll(it) }
        bestStreak = prefs.getInt("best", 0)
    }

    override fun onCleared() {
        tts?.shutdown()
    }
}
