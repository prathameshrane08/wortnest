package com.prathameshrane.wortkiste

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

data class Card(val box: Int, val due: Long)

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

    private val prefs = app.getSharedPreferences("wortkiste", Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        restore()
        viewModelScope.launch {
            try {
                val start = System.currentTimeMillis()
                repo = withContext(Dispatchers.Default) { Repository.load(app) }
                android.util.Log.i("Wortkiste", "dictionary loaded in ${System.currentTimeMillis() - start} ms")
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
        if (saved.remove(e.key) == null) saved[e.key] = Card(1, 0)
        persistSaved()
    }

    fun addCommonNouns(n: Int = 20) {
        val r = repo ?: return
        for (i in r.quizNouns.take(n)) saved.putIfAbsent(r.entries[i].key, Card(1, 0))
        persistSaved()
    }

    fun dueKeys(now: Long = System.currentTimeMillis()) = saved.filterValues { it.due <= now }.keys.toList()

    fun grade(key: String, knew: Boolean) {
        val c = saved[key] ?: return
        val box = if (knew) minOf(5, c.box + 1) else 1
        val due = System.currentTimeMillis() + (BOX_DAYS[box] * 86_400_000L).toLong() + if (knew) 0 else 60_000
        saved[key] = Card(box, due)
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
        for ((k, c) in saved) o.put(k, JSONObject().put("box", c.box).put("due", c.due))
        prefs.edit().putString("saved", o.toString()).apply()
    }

    private fun restore() {
        prefs.getString("saved", null)?.let { s ->
            runCatching {
                val o = JSONObject(s)
                for (k in o.keys()) {
                    val c = o.getJSONObject(k)
                    saved[k] = Card(c.getInt("box"), c.getLong("due"))
                }
            }
        }
        prefs.getString("missed", null)?.split("\n")?.filter { it.isNotEmpty() }?.let { missedNouns.addAll(it) }
        bestStreak = prefs.getInt("best", 0)
    }

    override fun onCleared() {
        tts?.shutdown()
    }

    companion object {
        val BOX_DAYS = doubleArrayOf(0.0, 0.0, 1.0, 3.0, 7.0, 21.0)
    }
}
