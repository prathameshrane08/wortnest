package com.prathameshrane.wortkiste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val examples = listOf("Haus", "gehen", "schön", "Zeitung", "Mädchen", "bringen", "Freund", "trotzdem")

@Composable
fun DictionaryScreen(vm: AppViewModel, repo: Repository, nav: Nav) {
    var result by remember { mutableStateOf(SearchResult()) }
    val query = vm.dictQuery
    LaunchedEffect(query) {
        delay(150)
        result = withContext(Dispatchers.Default) { repo.search(query) }
    }
    val open = { e: Entry -> nav.push(Route.EntryR(e.id)) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Wörterbuch")
        SearchField(query, { vm.dictQuery = it }, "German or English: Haus, ging, to run")
        UmlautRow { vm.dictQuery += it }
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isBlank()) {
                item { SectionHeader("Try one of these") }
                items(examples.mapNotNull { repo.entriesFor(it).firstOrNull() }) { EntryRow(it) { open(it) } }
            } else if (result.isEmpty) {
                item {
                    Text(
                        "No match for “$query”. Check the spelling, or try the base form (gehen instead of gegangen).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                if (result.exact.isNotEmpty()) {
                    item { SectionHeader("German") }
                    items(result.exact) { EntryRow(it) { open(it) } }
                }
                if (result.viaForm.isNotEmpty()) {
                    item { SectionHeader("“$query” is a form of ${result.lemmas.joinToString(", ")}") }
                    items(result.viaForm) { EntryRow(it) { open(it) } }
                }
                if (result.english.isNotEmpty()) {
                    item { SectionHeader("English → German") }
                    items(result.english) { EntryRow(it) { open(it) } }
                }
                if (result.prefix.isNotEmpty()) {
                    item { SectionHeader("Starting with “$query”") }
                    items(result.prefix) { EntryRow(it) { open(it) } }
                }
            }
        }
    }
}

@Composable
fun EntryScreen(vm: AppViewModel, repo: Repository, entry: Entry, nav: Nav) {
    val verb = if (entry.pos == "verb") repo.verbs[entry.word] else null
    Column(Modifier.fillMaxSize()) {
        BackBar(entry.word, nav)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry.isNoun) ArticleChip(entry.gender, large = true)
                Text(
                    entry.word,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (entry.isNoun && entry.gender.isNotEmpty()) genderColor(entry.gender) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    lineHeight = 38.sp,
                )
                SpeakButton(vm, entry.spoken)
                SaveButton(vm, entry)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PosLabel(entry.posName)
                if (entry.isNoun && entry.plural.isNotEmpty()) Text("plural: die ${entry.plural}", fontWeight = FontWeight.Medium)
                if (entry.gender == "p") Text("plural only", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (verb != null) {
                val aux = if (Conjugation.mainAux(verb) == "sein") "ist" else "hat"
                Text("${verb.pr.getOrElse(2) { "" }} · ${verb.pt.getOrElse(2) { "" }} · $aux ${verb.pp}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CardBox {
                entry.glosses.forEachIndexed { i, g ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${i + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(g)
                    }
                }
            }
            if (verb != null) {
                Button(onClick = { nav.push(Route.VerbR(entry.word)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.TableChart, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Full conjugation")
                }
            }
        }
    }
}
