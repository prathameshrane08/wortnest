package com.prathameshrane.wortnest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val TEST_N = 12
private const val TEST_PASS = 10
private val LEVEL_TITLES = listOf("", "Beginner", "Elementary", "Intermediate")

/** One question of the move-up test: the meaning of a German word (de), the German for a meaning (en), or a noun's article (art). */
private class TestQ(val id: Int, val kind: String, val opts: List<Int>)

/** Your level: take the twelve-question test to move up, or set the level by hand.
 *  onClose(true) means "go to Üben", after adding the new level's first words. */
@Composable
fun LevelDialog(vm: AppViewModel, repo: Repository, onClose: (goLearn: Boolean) -> Unit) {
    var view by remember { mutableStateOf("home") }
    var items by remember { mutableStateOf<List<TestQ>>(emptyList()) }
    var n by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<String?>(null) }   // the chosen article, or the chosen word's id as text
    var target by remember { mutableIntStateOf(0) }

    fun start() {
        val l = vm.level + 1
        val pool = repo.levelWords[l].take(600).filter { repo.entries[it].glosses.isNotEmpty() }
        val nouns = pickUnique(repo, pool.filter { val e = repo.entries[it]; e.isNoun && e.gender in setOf("m", "f", "n") }, 4)
        val words = pickUnique(repo, pool.filter { it !in nouns }, TEST_N - nouns.size)
        items = (nouns.map { TestQ(it, "art", emptyList()) } +
            words.mapIndexed { k, i -> TestQ(i, if (k % 2 == 0) "de" else "en", options(repo, i, pool, k % 2 == 0)) }).shuffled()
        target = l; n = 0; right = 0; pick = null; view = "test"
    }

    Dialog(onDismissRequest = { onClose(false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (view == "home") "Your level" else "${LEVEL_NAMES[target]} test", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onClose(false) }) { Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            when (view) {
                "home" -> LevelHome(vm) { start() }
                "result" -> {
                    val pass = right >= TEST_PASS
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$right", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                            Text(" / $TEST_N", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        if (pass) {
                            Text("Welcome to ${LEVEL_NAMES[target]}! 🎉", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("The word of the day, quizzes and flashcards now include ${LEVEL_NAMES[target]} words.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Button(onClick = { vm.addLevel(target); onClose(true) }) { Text("Add 20 ${LEVEL_NAMES[target]} words to flashcards") }
                            OutlinedButton(onClick = { onClose(false) }) { Text("Done") }
                        } else {
                            Text("Not quite yet", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("You need $TEST_PASS right to move up. Keep practising and try again whenever you like.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Button(onClick = { start() }) { Text("Try again") }
                            OutlinedButton(onClick = { onClose(false) }) { Text("Close") }
                        }
                    }
                }
                else -> {
                    val q = items[n]
                    val e = repo.entries[q.id]
                    val answer = if (q.kind == "art") e.gender else "${e.id}"
                    val done = pick != null
                    fun choose(p: String) { if (pick == null) { pick = p; if (p == answer) right++ } }
                    QuizProgress(n, TEST_N)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)).padding(vertical = 22.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        when (q.kind) {
                            "art" -> {
                                Text("der, die or das?", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FitText(e.word, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = if (done) genderColor(e.gender) else MaterialTheme.colorScheme.onSurface)
                                Text(e.shortGloss, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                            "de" -> {
                                Text("Pick the meaning", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FitText(e.spoken, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = wordColor(e))
                            }
                            else -> {
                                Text("Pick the German · ${e.posName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(e.shortGloss, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    if (q.kind == "art") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (g in listOf("m", "f", "n")) {
                                val c = genderColor(g)
                                val isRight = done && g == e.gender
                                Text(
                                    Gender.article(g)!!, color = if (isRight) Color.White else c, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f).alpha(if (done && !isRight) 0.35f else 1f)
                                        .background(if (isRight) c else c.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                        .clickable(enabled = !done) { choose(g) }.heightIn(min = 56.dp).padding(vertical = 14.dp),
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            q.opts.forEachIndexed { k, j ->
                                val o = repo.entries[j]
                                val state = if (!done) null else if (j == e.id) "right" else if ("$j" == pick) "wrong" else "dim"
                                OptionButton(k + 1, if (q.kind == "de") o.shortGloss else o.spoken,
                                    if (q.kind == "de") MaterialTheme.colorScheme.onSurface else wordColor(o), state) { choose("$j") }
                            }
                        }
                    }
                    if (done) Button(onClick = {
                        n++; pick = null
                        if (n >= TEST_N) { view = "result"; if (right >= TEST_PASS) vm.chooseLevel(target) }
                    }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (n + 1 < TEST_N) "Next" else "See result") }
                }
            }
        }
    }
}

@Composable
private fun LevelHome(vm: AppViewModel, onStart: () -> Unit) {
    val l = vm.level
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            LEVEL_NAMES[l], fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = levelColor(l),
            modifier = Modifier.background(levelColor(l).copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Column {
            Text("You're at ${LEVEL_NAMES[l]}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("The word of the day, quizzes and flashcards use ${levelRange(l)} words.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (l < 3) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Ready for ${LEVEL_NAMES[l + 1]}?", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("$TEST_N quick questions on ${LEVEL_NAMES[l + 1]} words. Get $TEST_PASS right to move up.", fontSize = 14.sp)
            Button(onClick = onStart, modifier = Modifier.padding(top = 4.dp)) { Text("Take the ${LEVEL_NAMES[l + 1]} test") }
        }
    } else {
        Text("B1 is the highest level here. Keep practising with the quizzes and the grammar topics.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Or choose your level yourself", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (k in 1..3) {
        val on = k == l
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .border(1.5.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { vm.chooseLevel(k) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LevelBadge(k)
            Column {
                Text(LEVEL_TITLES[k], fontWeight = FontWeight.Bold)
                Text(if (k == 1) "A1 words" else "A1 to ${LEVEL_NAMES[k]} words", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
