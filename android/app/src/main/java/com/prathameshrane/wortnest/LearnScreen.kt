package com.prathameshrane.wortnest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LearnScreen(vm: AppViewModel, repo: Repository, nav: Nav) {
    var currentKey by rememberSaveable { mutableStateOf<String?>(null) }
    var flipped by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Üben")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "Star any word in the dictionary to add it here. Cards you know move up a box and come back less often.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (vm.saved.isEmpty()) {
                CardBox {
                    Text("No saved words yet", fontWeight = FontWeight.Bold)
                    Text("Tap the star on any dictionary entry, or start with 20 everyday nouns.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { vm.addCommonNouns() }) { Text("Add 20 common nouns") }
                }
            } else {
                val due = vm.dueKeys()
                val key = currentKey?.takeIf { it in due } ?: due.randomOrNull()
                if (key != currentKey) { currentKey = key; flipped = false }
                val parts = key?.split("|", limit = 2)
                val e = if (parts != null && parts.size == 2) repo.entry(parts[0], parts[1]) else null

                if (key == null || e == null) {
                    CardBox {
                        Text("All caught up", fontWeight = FontWeight.Bold)
                        Text("No cards are due right now. Come back later or add more words.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                            .clickable {
                                flipped = !flipped
                                if (flipped) vm.speak(e.spoken)
                            }
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    ) {
                        Text("${e.posName.uppercase()} · ${due.size} DUE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (flipped) {
                            Text(e.spoken, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                                color = if (e.isNoun && e.gender.isNotEmpty()) genderColor(e.gender) else MaterialTheme.colorScheme.onSurface)
                            if (e.isNoun && e.plural.isNotEmpty()) Text("die ${e.plural}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(e.glosses.take(2).joinToString("; "), textAlign = TextAlign.Center)
                        } else {
                            Text(e.word, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (flipped) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                            OutlinedButton(onClick = { vm.grade(key, false); currentKey = null }) { Text("Again") }
                            Button(onClick = { vm.grade(key, true); currentKey = null }) { Text("I knew it") }
                        }
                    }
                }
                Boxes(vm)
            }

            val list = vm.savedEntries()
            Text("Saved words" + if (list.isEmpty()) "" else " (${list.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        val list = vm.savedEntries()
        if (list.isEmpty()) {
            Text("Nothing saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        } else {
            list.forEach { e -> EntryRow(e) { nav.push(Route.EntryR(e.id)) } }
        }
    }
}

@Composable
private fun Boxes(vm: AppViewModel) {
    val values = vm.saved.values
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        for (b in 1..5) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(width = 44.dp, height = 34.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("${values.count { it.box == b }}", fontWeight = FontWeight.Bold) }
                Text("Box $b", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
