package com.prathameshrane.wortkiste

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val commonVerbs = listOf("sein", "haben", "werden", "können", "gehen", "kommen", "sehen", "geben", "nehmen", "anfangen", "bringen", "wissen")

@Composable
fun VerbsScreen(vm: AppViewModel, repo: Repository, nav: Nav) {
    var results by remember { mutableStateOf(emptyList<String>()) }
    val query = vm.verbQuery
    LaunchedEffect(query) {
        delay(150)
        results = withContext(Dispatchers.Default) { repo.findVerbs(query) }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Verben")
        SearchField(query, { vm.verbQuery = it }, "gehen, fing an, to bring")
        UmlautRow { vm.verbQuery += it }
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isBlank()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.push(Route.Drill) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Conjugation drill", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
                item { SectionHeader("Common verbs") }
                items(commonVerbs.filter { repo.verbs.containsKey(it) }) { VerbRow(repo, it, nav) }
            } else if (results.isEmpty()) {
                item { Text("No verb found for “$query”.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            } else {
                items(results) { VerbRow(repo, it, nav) }
            }
        }
    }
}

@Composable
private fun VerbRow(repo: Repository, inf: String, nav: Nav) {
    Column(Modifier.fillMaxWidth().clickable { nav.push(Route.VerbR(inf)) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(inf, fontWeight = FontWeight.SemiBold)
        repo.verbEntry(inf)?.let {
            Text(it.glosses.take(2).joinToString("; "), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerbDetailScreen(vm: AppViewModel, repo: Repository, inf: String, nav: Nav) {
    val v = repo.verbs[inf]
    Column(Modifier.fillMaxSize()) {
        BackBar(inf, nav)
        if (v == null) {
            Text("No conjugation for “$inf”.", modifier = Modifier.padding(16.dp))
            return@Column
        }
        val entry = repo.verbEntry(inf)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(inf, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                SpeakButton(vm, "$inf, ${v.pt.getOrElse(2) { "" }}, ${v.pp}")
                if (entry != null) SaveButton(vm, entry)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Conjugation.kindLabel(v)?.let { Badge(it, highlight = v.t != "weak") }
                Badge("Perfekt with " + Conjugation.auxiliaries(v).joinToString(" or "))
                Conjugation.separablePrefix(v, inf)?.let { Badge("Separable: $it-") }
            }
            entry?.let { Text(it.glosses.take(3).joinToString("; ")) }

            val parts = listOf(
                "Infinitiv" to inf,
                "Präsens er" to v.pr.getOrElse(2) { "" },
                "Präteritum er" to v.pt.getOrElse(2) { "" },
                "Partizip II" to (if (Conjugation.mainAux(v) == "sein") "ist " else "hat ") + v.pp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pair in parts.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, value) in pair) {
                            Column(
                                Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)).padding(10.dp),
                            ) {
                                Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("Orange") }
                    append(" = differs from the regular pattern")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (t in Conjugation.tables(v, inf)) TenseTable(t)
        }
    }
}

@Composable
private fun Badge(text: String, highlight: Boolean = false) {
    val c = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c,
        modifier = Modifier.background(c.copy(alpha = 0.15f), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private val endings = listOf("test", "est", "ten", "tet", "st", "et", "en", "te", "t", "e", "n")

@Composable
private fun TenseTable(t: Conjugation.Table) {
    val irregularColor = MaterialTheme.colorScheme.primary
    CardBox {
        Text(t.title, fontWeight = FontWeight.Bold)
        for (row in t.rows) {
            Row {
                Text(row.person, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                val color = if (row.irregular) irregularColor else Color.Unspecified
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = color, fontWeight = if (row.irregular) FontWeight.SemiBold else null)) {
                            val parts = row.form.split(" ", limit = 2)
                            val word = parts[0]
                            val end = if (t.showEndings) endings.firstOrNull { word.endsWith(it) && word.length > it.length + 1 } else null
                            if (end != null) {
                                append(word.dropLast(end.length))
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(end) }
                            } else append(word)
                            if (parts.size > 1) append(" " + parts[1])
                        }
                    },
                )
            }
        }
    }
}

private val tenseNames = listOf("pr" to "Präsens", "pt" to "Präteritum", "pf" to "Perfekt")

@Composable
fun DrillScreen(repo: Repository, nav: Nav) {
    var tense by rememberSaveable { mutableStateOf("pr") }
    var verb by rememberSaveable { mutableStateOf("") }
    var person by rememberSaveable { mutableIntStateOf(0) }
    var expected by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var right by rememberSaveable { mutableIntStateOf(0) }
    var total by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    fun next() {
        val pool = repo.verbList.take(150)
        repeat(20) {
            val inf = pool.random()
            val v = repo.verbs.getValue(inf)
            val p = (0 until 6).random()
            val form = when (tense) {
                "pt" -> v.pt.getOrElse(p) { "" }
                "pf" -> Conjugation.auxPresent(v)[p] + " " + v.pp
                else -> v.pr.getOrElse(p) { "" }
            }
            if (form.isNotEmpty()) {
                verb = inf; person = p; expected = form
                answer = ""; checked = null
                return
            }
        }
    }

    fun check() {
        if (checked != null) { next(); return }
        val given = answer.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
        if (given.isEmpty()) return
        val ok = given == expected.lowercase()
        checked = ok
        total++
        if (ok) right++
    }

    LaunchedEffect(Unit) { if (verb.isEmpty()) next(); runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        BackBar("Conjugation drill", nav)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                tenseNames.forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = tense == key,
                        onClick = { if (tense != key) { tense = key; next() } },
                        shape = SegmentedButtonDefaults.itemShape(i, tenseNames.size),
                        label = { Text(label) },
                    )
                }
            }
            CardBox {
                Text(verb, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                repo.verbEntry(verb)?.glosses?.firstOrNull()?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    "${tenseNames.first { it.first == tense }.second} · ${Conjugation.pronouns[person]}",
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text(Conjugation.pronouns[person] + " …") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { check() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { check() }) { Text(if (checked == null) "Check" else "Next verb") }
                    if (checked == null) OutlinedButton(onClick = { next() }) { Text("Skip") }
                }
                when (checked) {
                    true -> Text("Richtig! ${Conjugation.pronouns[person]} $expected", color = okColor(), fontWeight = FontWeight.SemiBold)
                    false -> Text("Not quite. ${Conjugation.pronouns[person]} $expected", color = badColor(), fontWeight = FontWeight.SemiBold)
                    null -> {}
                }
            }
            Text(
                if (total == 0) "Type the form and press Check." else "$right of $total right this session",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
