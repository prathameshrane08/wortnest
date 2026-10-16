package com.prathameshrane.wortnest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ArticleScreen(vm: AppViewModel, repo: Repository) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Der Die Das")
        SearchField(vm.articleQuery, { vm.articleQuery = it }, "Which article? Kinderzimmer")
        UmlautRow { vm.articleQuery += it }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (vm.articleQuery.isNotBlank()) LookupResult(vm, repo, vm.articleQuery)
            QuizCard(vm, repo)
            RulesSection()
        }
    }
}

@Composable
private fun LookupResult(vm: AppViewModel, repo: Repository, query: String) {
    val hit = repo.nounLookup(query)
    CardBox {
        if (hit == null) {
            Text(
                "No noun found for “${query.trim()}”. Nouns are listed in the singular: try Haus, not Häuser.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val (e, compound) = hit
            val shown = compound ?: e.word
            val art = Gender.article(e.gender).orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(art, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = genderColor(e.gender))
                Text(shown, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), lineHeight = 38.sp)
                SpeakButton(vm, "$art $shown")
            }
            if (compound != null) Text("Compound: takes the article of $art ${e.word}", fontWeight = FontWeight.Medium)
            else if (e.plural.isNotEmpty()) Text("plural: die ${e.plural}", fontWeight = FontWeight.Medium)
            e.glosses.firstOrNull()?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Gender.rule(shown, e.gender)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private val levels = listOf("Top 500", "500–2k", "2k+", "Mistakes")

@Composable
private fun QuizCard(vm: AppViewModel, repo: Repository) {
    var level by rememberSaveable { mutableIntStateOf(0) }
    var currentId by rememberSaveable { mutableStateOf<Int?>(null) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var streak by rememberSaveable { mutableIntStateOf(0) }
    var right by rememberSaveable { mutableIntStateOf(0) }
    var total by rememberSaveable { mutableIntStateOf(0) }

    fun pool(): List<Entry> {
        if (level == 3) return vm.missedNouns.mapNotNull { w -> repo.entriesFor(w).firstOrNull { it.isNoun } }
        val (a, b) = listOf(0 to 500, 500 to 2000, 2000 to 6000)[level]
        val ids = repo.quizNouns
        return ids.subList(minOf(a, ids.size), minOf(b, ids.size)).map { repo.entries[it] }
    }

    fun next() {
        val p = pool()
        chosen = null
        if (p.isEmpty()) { currentId = null; return }
        var pick = p.random()
        if (p.size > 1) while (pick.id == currentId) pick = p.random()
        currentId = pick.id
    }

    LaunchedEffect(Unit) { if (currentId == null) next() }
    val current = currentId?.let { repo.entries[it] }

    // After a right answer, move on by itself.
    LaunchedEffect(chosen, currentId) {
        if (current != null && chosen == current.gender) {
            delay(1100)
            next()
        }
    }

    CardBox {
        Text("ARTICLE QUIZ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("der, die oder das?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            levels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = level == i,
                    onClick = { if (level != i) { level = i; next() } },
                    shape = SegmentedButtonDefaults.itemShape(i, levels.size),
                    label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (current == null) {
                Text("All clear", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("No mistakes left to review. Pick another level.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    current.word,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (chosen == null) MaterialTheme.colorScheme.onSurface else genderColor(current.gender),
                    textAlign = TextAlign.Center,
                    lineHeight = 44.sp,
                )
                Text(current.glosses.firstOrNull().orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (g in listOf("m", "f", "n")) {
                        val c = genderColor(g)
                        val isRight = chosen != null && g == current.gender
                        Text(
                            Gender.article(g)!!,
                            color = c,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (chosen != null && !isRight) 0.4f else 1f)
                                .background(c.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(BorderStroke(3.dp, if (isRight) okColor() else Color.Transparent), RoundedCornerShape(12.dp))
                                .clickable(enabled = chosen == null) {
                                    chosen = g
                                    total++
                                    val ok = g == current.gender
                                    if (ok) { right++; streak++ } else streak = 0
                                    vm.recordQuiz(current.word, ok, streak)
                                    vm.speak(current.spoken)
                                }
                                .heightIn(min = 56.dp)
                                .padding(vertical = 14.dp),
                        )
                    }
                }
                val picked = chosen
                if (picked == null) {
                    Text("Tap the article you think is right.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val ok = picked == current.gender
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (ok) "Richtig!" else "Falsch.", fontWeight = FontWeight.Bold, color = if (ok) okColor() else badColor())
                        Text(current.spoken + if (current.plural.isNotEmpty()) ", die ${current.plural}" else "", fontWeight = FontWeight.Medium)
                        Gender.rule(current.word, current.gender)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    if (!ok) Button(onClick = { next() }) { Text("Next word") }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("$streak", "Streak")
            Stat("${vm.bestStreak}", "Best")
            Stat(if (total == 0) "–" else "${(right * 100 + total / 2) / total}%", "Accuracy")
            Stat("${vm.missedNouns.size}", "To review")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), fontSize = 10.sp, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val ruleColumns = listOf(
    "m" to listOf("-ling  der Frühling", "-ismus  der Tourismus", "-or  der Motor", "-ig, -ich  der Honig", "-ist, -ant  der Polizist", "days, months, seasons", "weather: der Regen"),
    "f" to listOf("-ung  die Zeitung", "-heit, -keit  die Freiheit", "-schaft  die Freundschaft", "-ion, -tät  die Nation", "-ie, -ei, -ik  die Bäckerei", "-in (people)  die Lehrerin", "-ur, -enz, -anz  die Natur"),
    "n" to listOf("-chen, -lein  das Mädchen", "-ment  das Instrument", "-um  das Museum", "-tum  das Eigentum", "Ge- collectives: das Gebirge", "infinitives: das Essen", "metals, colours: das Gold"),
)

@Composable
private fun RulesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("RULES OF THUMB", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Endings that give the article away", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        for ((g, items) in ruleColumns) {
            val c = genderColor(g)
            Column(
                Modifier.fillMaxWidth().background(c.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(Gender.article(g)!!, color = c, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                items.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Text(
            "Compound nouns take the article of their last part: das Kind + das Zimmer = das Kinderzimmer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
