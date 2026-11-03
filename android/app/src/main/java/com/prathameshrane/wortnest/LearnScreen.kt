package com.prathameshrane.wortnest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/* Practice has four ways to practise, like Quizlet: flashcards (spaced repetition on your saved words),
   choose from four, write the answer, and a timed matching game. The last three use your level's
   words or your saved words, German to English or the other way round. */

private val MODES = listOf(
    Triple("cards", "Flashcards", Icons.Outlined.Style),
    Triple("mc", "Choose", Icons.Outlined.ViewAgenda),
    Triple("type", "Write", Icons.Outlined.Edit),
    Triple("match", "Match", Icons.Outlined.GridView),
)
private val MODE_TEXT = mapOf(
    "cards" to "Star words in the dictionary to add them here. Words you know come back less often; words you miss come back soon.",
    "mc" to "Pick the right answer out of four. Ten questions a round.",
    "type" to "Write the answer yourself. Ten questions a round.",
    "match" to "Tap a German word, then its meaning. Clear the board as fast as you can.",
)

@Composable
fun LearnScreen(vm: AppViewModel, repo: Repository, nav: Nav, openLevel: () -> Unit) {
    val mode = vm.practiceMode
    val canSaved = vm.saved.size >= 4
    val src = if (canSaved) vm.practiceSrc else "level"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle("Practice") }
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable(onClick = openLevel).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LevelBadge(vm.level)
                Text(if (vm.level < 3) "Take the ${LEVEL_NAMES[vm.level + 1]} test" else "Your level", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(MODE_TEXT.getValue(mode), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fit = remember { FitGroup() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MODES.forEach { (key, label, icon) -> ModeTile(label, icon, mode == key, Modifier.weight(1f), fit) { vm.setPractice(mode = key) } }
            }
            if (mode != "match") {
                TextButton(onClick = { vm.setPractice(dir = if (vm.practiceDir == "de") "en" else "de") }, contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text(if (vm.practiceDir == "de") "German → English" else "English → German", fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "Switch direction", modifier = Modifier.padding(start = 6.dp).size(18.dp))
                }
            }
            if (mode != "cards") {
                Segments(listOf("${levelRange(vm.level)} words", "My saved words"), if (src == "saved") 1 else 0) {
                    if (it == 0 || canSaved) vm.setPractice(src = if (it == 1) "saved" else "level")
                }
                if (!canSaved) Text("Save at least 4 words to practise only those.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (mode) {
                "cards" -> Flashcards(vm, repo)
                "match" -> MatchGame(vm, repo, src)
                else -> QuizRound(vm, repo, mode, vm.practiceDir, src)
            }
            if (mode == "cards") {
                val list = vm.savedEntries()
                Text("Saved words" + if (list.isEmpty()) "" else " (${list.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (mode == "cards") {
            val list = vm.savedEntries()
            if (list.isEmpty()) Text("Nothing saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            else list.forEach { e -> EntryRow(e) { nav.push(Route.EntryR(e.id)) } }
        }
    }
}

@Composable
private fun ModeTile(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, fit: FitGroup, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val c = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(22.dp))
        FitText(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c, group = fit)
    }
}

// ---------- flashcards ----------

@Composable
private fun Flashcards(vm: AppViewModel, repo: Repository) {
    var currentKey by rememberSaveable { mutableStateOf<String?>(null) }
    var flipped by rememberSaveable { mutableStateOf(false) }

    if (vm.saved.isEmpty()) {
        CardBox {
            Text("No saved words yet", fontWeight = FontWeight.Bold)
            Text("Tap the star on any dictionary entry, or start with the 20 most useful words of a level.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AddLevelRow(vm)
        }
        return
    }
    val queue = vm.studyQueue()
    val key = currentKey?.takeIf { it in queue } ?: queue.firstOrNull()
    if (key != currentKey) { currentKey = key; flipped = false }
    val parts = key?.split("|", limit = 2)
    val e = if (parts != null && parts.size == 2) repo.entry(parts[0], parts[1]) else null
    val card = key?.let { vm.saved[it] }

    if (key == null || e == null || card == null) {
        CardBox {
            Text("All done for now", fontWeight = FontWeight.Bold)
            Text(
                if (vm.hasNewCards()) "You've had today's ${Srs.NEW_PER_DAY} new words. More arrive tomorrow."
                else "No cards are due. Try Choose, Write or Match meanwhile, or star more words.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Stats(vm)
        return
    }
    val flip = { flipped = !flipped; if (flipped) vm.speak(e.spoken) }
    val grade = { g: Int -> vm.grade(key, g); currentKey = null; flipped = false }
    SwipeCard(key, "Again", "Good", onTap = flip, onSwipe = { right -> grade(if (right) 2 else 0) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.posName, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!flipped && (card.isNew || card.isLearning)) {
                Text(
                    if (card.isNew) "new" else "learning", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        if (flipped) {
            e.article?.let { Text(it, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = wordColor(e)) }
            FitText(e.word, fontSize = 36.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.ExtraBold, color = wordColor(e))
            if (e.isNoun && e.plural.isNotEmpty()) Text("die ${e.plural}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.glosses.take(2).joinToString("; "), textAlign = TextAlign.Center)
            repo.examples[e.key]?.firstOrNull()?.takeIf { it.size == 2 }?.let { ex ->
                HorizontalDivider()
                Text(ex[0], fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                Text(ex[1], color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        } else if (vm.practiceDir == "de") {
            FitText(e.word, fontSize = 36.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.ExtraBold)
            Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(e.glosses.take(2).joinToString("; "), fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Say it in German, then tap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (flipped) {
        val fit = remember { FitGroup() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (g in 0..3) {
                val m = Modifier.weight(1f)
                val pad = PaddingValues(horizontal = 4.dp, vertical = 14.dp)
                val label: @Composable () -> Unit = { FitText(Srs.GRADE_NAMES[g], fontWeight = FontWeight.Bold, group = fit) }
                // Good is the button you'll press most, so it stands out.
                if (g == 2) Button(onClick = { grade(g) }, modifier = m, contentPadding = pad) { label() }
                else OutlinedButton(
                    onClick = { grade(g) }, modifier = m, contentPadding = pad,
                    colors = if (g == 0) ButtonDefaults.outlinedButtonColors(contentColor = genderColor("f")) else ButtonDefaults.outlinedButtonColors(),
                ) { label() }
            }
        }
    } else {
        Button(onClick = flip, modifier = Modifier.fillMaxWidth()) { Text("Show answer") }
    }
    Text("Swipe right for Good, left for Again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Stats(vm)
}

@Composable
private fun Stats(vm: AppViewModel) {
    val c = vm.counts()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(c.fresh, "New", genderColor("m"))
            Stat(c.learning, "Learning", genderColor("f"))
            Stat(c.review, "To review", genderColor("n"))
        }
        Text("${c.known} of ${vm.saved.size} words known well · ${c.tomorrow} due by tomorrow",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AddLevelRow(vm)
    }
}

@Composable
private fun AddLevelRow(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Add 20 words from", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in 1..vm.level) {
                val left = vm.wordsLeft(l)
                OutlinedButton(onClick = { vm.addLevel(l) }, enabled = left > 0, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(LEVEL_NAMES[l], fontWeight = FontWeight.Bold)
                        Text("$left left", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(n: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 84.dp, height = 40.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("$n", fontWeight = FontWeight.Bold, color = color) }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Choose and Write ----------

private class Round(val pool: List<Int>, val items: List<Int>) {
    var n by mutableIntStateOf(0)
    var right by mutableIntStateOf(0)
    val missed = mutableStateListOf<Int>()
    var pick by mutableStateOf<Int?>(null)    // null until answered; -1 for a wrong written answer
    var note by mutableStateOf("")
}

private fun quizPool(vm: AppViewModel, repo: Repository, src: String): List<Int> {
    if (src == "saved") {
        val s = vm.savedEntries().filter { it.glosses.isNotEmpty() }.map { it.id }
        if (s.size >= 4) return s
    }
    return upTo(repo.levelWords, vm.level).filter { repo.entries[it].glosses.isNotEmpty() }
}

@Composable
private fun QuizRound(vm: AppViewModel, repo: Repository, kind: String, dir: String, src: String) {
    var roundId by remember { mutableIntStateOf(0) }
    val round = remember(kind, dir, src, vm.level, roundId) {
        val pool = quizPool(vm, repo, src)
        Round(pool, pickUnique(repo, pool, 10))
    }
    if (round.items.isEmpty()) {
        CardBox { Text("No words to practise", fontWeight = FontWeight.Bold); Text("Star a few words in the dictionary first.") }
        return
    }
    if (round.n >= round.items.size) { RoundResult(vm, repo, round) { roundId++ }; return }

    val de = dir == "de"
    val e = repo.entries[round.items[round.n]]
    val answered = round.pick != null
    val opts = remember(round, round.n) { if (kind == "mc") options(repo, e.id, round.pool, de) else emptyList() }
    fun answer(pick: Int, note: String = "") {
        if (round.pick != null) return
        round.pick = pick; round.note = note
        if (pick == e.id) round.right++ else round.missed.add(e.id)
        vm.speak(e.spoken)
    }
    fun next() { round.n++; round.pick = null; round.note = "" }
    // A right choice moves on by itself.
    LaunchedEffect(round, round.n, round.pick) {
        if (kind == "mc" && round.pick == e.id) { delay(1000); next() }
    }

    QuizProgress(round.n, round.items.size)
    CardBox {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (de) (if (kind == "mc") "Pick the meaning" else "Write the meaning in English")
                else (if (kind == "mc") "Pick the German" else "Write it in German") + " · ${e.posName}",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (de) {
                e.article?.let { Text(it, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = wordColor(e)) }
                FitText(e.word, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = wordColor(e))
                FilledTonalIconButton(onClick = { vm.speak(e.spoken) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Pronounce") }
            } else {
                Text(e.shortGloss, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
    if (kind == "mc") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEachIndexed { k, j ->
                val o = repo.entries[j]
                val state = if (!answered) null else if (j == e.id) "right" else if (j == round.pick) "wrong" else "dim"
                OptionButton(k + 1, if (de) o.shortGloss else listOfNotNull(o.article, o.word).joinToString(" "),
                    if (de) MaterialTheme.colorScheme.onSurface else wordColor(o), state) { answer(j) }
            }
        }
    } else {
        var typed by remember(round, round.n) { mutableStateOf("") }
        fun submit() {
            if (answered) { next(); return }
            if (typed.isBlank()) return
            val c = if (de) checkEnglish(typed, e, repo) else checkGerman(typed, e)
            answer(if (c.ok) e.id else -1, c.note)
        }
        OutlinedTextField(
            value = typed, onValueChange = { if (!answered) typed = it }, singleLine = true,
            label = { Text(if (de) "Meaning in English" else "German word") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!de && !answered) UmlautRowInline { typed += it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { submit() }) { Text(if (answered) "Next" else "Check") }
            if (!answered) OutlinedButton(onClick = { answer(-1, "The answer:") }) { Text("I don't know") }
        }
    }
    if (answered) {
        val ok = round.pick == e.id
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (ok) "Correct!" else round.note.ifEmpty { "Not quite." }, fontWeight = FontWeight.Bold, color = if (ok) okColor() else badColor(), textAlign = TextAlign.Center)
            Text(e.spoken, fontWeight = FontWeight.Bold, color = wordColor(e))
            Text(e.glosses.take(2).joinToString("; ") { it.replace(Regex("\\s*\\([^)]*\\)"), "") }, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ok && round.note.isNotEmpty()) Text(round.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (kind == "mc") Button(onClick = { next() }, modifier = Modifier.padding(top = 6.dp)) { Text("Next") }
        }
    }
}

@Composable
private fun UmlautRowInline(onInsert: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in listOf("ä", "ö", "ü", "ß")) {
            Text(c, fontWeight = FontWeight.Bold, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                .clickable { onInsert(c) }.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun RoundResult(vm: AppViewModel, repo: Repository, round: Round, again: () -> Unit) {
    val n = round.items.size
    val unsaved = round.missed.map { repo.entries[it] }.filter { !vm.isSaved(it) }
    CardBox {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${round.right}", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                Text(" / $n", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text(
                when {
                    round.right == n -> "Perfect round."
                    round.right >= n * 0.8 -> "Very good."
                    round.right >= n / 2 -> "Good start. The missed words are below."
                    else -> "These need more practice. Add them to your flashcards."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            round.missed.forEach { i ->
                val e = repo.entries[i]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(e.spoken, fontWeight = FontWeight.Bold, color = wordColor(e), modifier = Modifier.weight(1f))
                    Text(e.shortGloss, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
            Button(onClick = again) { Text("New round") }
            if (unsaved.isNotEmpty()) OutlinedButton(onClick = { unsaved.forEach { vm.toggleSaved(it) } }) {
                Text(if (unsaved.size == 1) "Add it to flashcards" else "Add these ${unsaved.size} to flashcards")
            }
        }
    }
}

// ---------- Match ----------

private class MatchBoard(val tiles: List<Pair<Int, Boolean>>) {   // (word, German side?)
    val gone = mutableStateListOf<Int>()
    var sel by mutableStateOf<Int?>(null)
    var wrong by mutableStateOf<Set<Int>>(emptySet())
    var start by mutableLongStateOf(0L)
    var penalty by mutableIntStateOf(0)
    var time by mutableFloatStateOf(0f)
    var best by mutableStateOf(false)
    val done get() = gone.size == tiles.size
}

@Composable
private fun MatchGame(vm: AppViewModel, repo: Repository, src: String) {
    var roundId by remember { mutableIntStateOf(0) }
    val board = remember(src, vm.level, roundId) {
        val picks = pickUnique(repo, quizPool(vm, repo, src), 6)
        MatchBoard(picks.flatMap { listOf(it to true, it to false) }.shuffled())
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(board, board.start, board.done) {
        while (board.start > 0 && !board.done) { now = System.currentTimeMillis(); delay(100) }
    }
    LaunchedEffect(board.wrong) { if (board.wrong.isNotEmpty()) { delay(400); board.wrong = emptySet() } }

    if (board.tiles.size < 8) {
        CardBox { Text("Not enough words", fontWeight = FontWeight.Bold); Text("Star a few more words first.") }
        return
    }
    if (board.done) {
        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(board.time), fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" s", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(
                    (if (board.best) "New best time!" else "Your best is %.1f s.".format(vm.matchBest)) +
                        if (board.penalty > 0) " Includes ${board.penalty} s for wrong pairs." else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Button(onClick = { roundId++ }) { Text("Play again") }
            }
        }
        return
    }
    val elapsed = if (board.start == 0L) 0f else (now - board.start) / 1000f + board.penalty
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text("%.1f s".format(elapsed), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Text((if (vm.matchBest > 0f) "Best %.1f s · ".format(vm.matchBest) else "") + "wrong pair +1 s",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    fun tap(k: Int) {
        if (board.start == 0L) { board.start = System.currentTimeMillis(); now = board.start }
        val s = board.sel
        if (s == null || s == k) { board.sel = if (s == k) null else k; return }
        val (a, aDe) = board.tiles[s]; val (b, bDe) = board.tiles[k]
        board.sel = null
        if (a == b && aDe != bDe) {
            board.gone.add(s); board.gone.add(k)
            if (board.done) {
                board.time = (System.currentTimeMillis() - board.start) / 1000f + board.penalty
                board.best = vm.recordMatch(board.time)
            }
        } else { board.penalty++; board.wrong = setOf(s, k) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        board.tiles.indices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val (i, isDe) = board.tiles[k]
                    val e = repo.entries[i]
                    val gone = k in board.gone
                    val shape = RoundedCornerShape(14.dp)
                    val line = when { k in board.wrong -> badColor(); board.sel == k -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.outline }
                    Box(
                        Modifier.weight(1f).height(86.dp).alpha(if (gone) 0f else 1f).clip(shape)
                            .background(if (board.sel == k) MaterialTheme.colorScheme.primaryContainer else if (k in board.wrong) badColor().copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, line, shape)
                            .clickable(enabled = !gone) { tap(k) }.padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isDe) e.spoken else e.tileGloss,
                            fontSize = if (isDe) 15.sp else 13.sp, lineHeight = 17.sp,
                            fontWeight = if (isDe) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDe) wordColor(e) else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center, maxLines = 4,
                        )
                    }
                }
            }
        }
    }
}
