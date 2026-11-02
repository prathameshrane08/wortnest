package com.prathameshrane.wortnest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private class TourStep(val title: String, val text: String, val demo: @Composable () -> Unit)

/** A short walkthrough of the five sections, shown on first launch and from the Tour button.
 *  The last step asks the learner's level, which sets the quizzes and can add the first 20 words. */
@Composable
fun TourDialog(vm: AppViewModel, repo: Repository, start: Int, onClose: (addedWords: Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(start) }
    var level by remember { mutableIntStateOf(vm.level) }
    var addWords by remember { mutableStateOf(vm.saved.isEmpty()) }
    val steps = listOf(
        TourStep("Willkommen! A little German every day", "Five tools in one place. This walkthrough shows each one in under a minute.") { SectionsDemo() },
        TourStep("Look up any word, both ways", "Type German or English. Forms like ging or Häuser find their base word, with the meaning, examples and pronunciation.") { SearchDemo(repo) },
        TourStep("Blue, red, green", "Every noun shows its article in colour: blue for der, red for die, green for das. The Der Die Das tab quizzes you until the colours stick.") { ColoursDemo() },
        TourStep("Every verb, every tense", "The Verben tab conjugates about 8,900 verbs. Highlighted forms break the regular pattern, so you know which ones to learn by heart.") { VerbDemo() },
        TourStep("Flashcards that remember for you", "Star a word to add it to Üben. After each card, tap how well you knew it. Words you know come back less often; words you miss come back soon.") { CardDemo() },
        TourStep("Where are you starting?", "This sets the level for the quizzes. You can change it any time.") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple(1, "Beginner", "I'm just starting out"), Triple(2, "Elementary", "I know the basics"), Triple(3, "Intermediate", "I can hold a conversation"))
                    .forEach { (l, name, line) ->
                        val on = level == l
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .border(1.5.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                .clickable { level = l }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LevelBadge(l)
                            Column {
                                Text(name, fontWeight = FontWeight.Bold)
                                Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                if (vm.saved.isEmpty()) {
                    Row(Modifier.clickable { addWords = !addWords }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = addWords, onCheckedChange = { addWords = it })
                        Text("Add my first 20 words to the flashcards", fontSize = 14.sp)
                    }
                }
            }
        },
    )
    val last = step == steps.lastIndex
    fun finish() {
        vm.chooseLevel(level)
        val add = addWords && vm.saved.isEmpty()
        if (add) vm.addLevel(level)
        vm.finishTour()
        onClose(add)
    }

    Dialog(onDismissRequest = { vm.finishTour(); onClose(false) }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier.size(width = if (i == step) 22.dp else 7.dp, height = 7.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        )
                    }
                }
                if (!last) TextButton(onClick = { vm.finishTour(); onClose(false) }) { Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) { steps[step].demo() }
            Text(steps[step].title, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Text(steps[step].text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) OutlinedButton(onClick = { step-- }, shape = RoundedCornerShape(12.dp)) { Text("Back") } else Box(Modifier)
                Button(onClick = { if (last) finish() else step++ }, shape = RoundedCornerShape(12.dp)) { Text(if (last) "Start learning" else "Next") }
            }
        }
    }
}

@Composable
private fun SectionsDemo() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TABS.forEach { (label, icon) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(60.dp)) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Types ging, to run and Häuser into a fake search box and shows the word each one finds. */
@Composable
private fun SearchDemo(repo: Repository) {
    val demos = listOf("ging" to "gehen", "to run" to "laufen", "Häuser" to "Haus")
    var d by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val q = demos[d].first
            for (k in 0..q.length) { typed = q.take(k); delay(140) }
            delay(1700)
            d = (d + 1) % demos.size
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) { Text("$typed|", fontSize = 17.sp) }
    val (q, w) = demos[d]
    val e = if (typed == q) repo.entriesFor(w).firstOrNull { it.word == w } else null
    Box(Modifier.heightIn(min = 64.dp)) {
        if (e != null) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.isNoun) ArticleChip(e.gender)
                Text(e.word, fontWeight = FontWeight.Bold, color = if (e.isNoun) genderColor(e.gender) else MaterialTheme.colorScheme.onSurface)
                if (!e.isNoun) PosLabel(e.posName)
                LevelBadge(e.level)
            }
            Text(e.glosses.take(2).joinToString("; "), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun ColoursDemo() {
    listOf("m" to "Tisch", "f" to "Lampe", "n" to "Buch").forEach { (g, w) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArticleChip(g, large = true)
            Text(w, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = genderColor(g))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("m", "f", "n").forEach { g ->
            Text(
                Gender.article(g)!!, color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(genderColor(g)).padding(vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun VerbDemo() {
    val irr = irregularColor()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("fahren · Präsens", fontWeight = FontWeight.Bold)
        listOf(Triple("ich", "fahre", false), Triple("du", "fährst", true), Triple("er/sie/es", "fährt", true), Triple("wir", "fahren", false)).forEach { (p, f, odd) ->
            Row {
                Text(p, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
                Text(buildAnnotatedString { withStyle(SpanStyle(color = if (odd) irr else Color.Unspecified, fontWeight = if (odd) FontWeight.Bold else null)) { append(f) } })
            }
        }
    }
}

@Composable
private fun CardDemo() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("das Haus", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = genderColor("n"))
        Text("house, home", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Again" to "1 min", "Hard" to "6 min", "Good" to "10 min", "Easy" to "4 days").forEachIndexed { i, (name, wait) ->
            val hit = i == 2
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val c = if (hit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c)
                Text(wait, fontSize = 11.sp, color = c.copy(alpha = 0.7f))
            }
        }
    }
}
