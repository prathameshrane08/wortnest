package com.prathameshrane.wortnest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
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
                "Star any word in the dictionary to add it here. Words you know come back less and less often; words you miss come back soon.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (vm.saved.isEmpty()) {
                CardBox {
                    Text("No saved words yet", fontWeight = FontWeight.Bold)
                    Text("Tap the star on any dictionary entry, or start with the 20 most useful words of a level.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AddLevelRow(vm)
                }
            } else {
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
                            else "No cards are due. Come back later, or star more words.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val flip = {
                        flipped = !flipped
                        if (flipped) vm.speak(e.spoken)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                            .clickable { flip() }
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(e.posName.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!flipped && (card.isNew || card.isLearning)) {
                                Text(
                                    if (card.isNew) "NEW" else "LEARNING", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = genderColor(if (card.isNew) "m" else "f"),
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        if (flipped) {
                            Text(e.spoken, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                                color = if (e.isNoun && e.gender.isNotEmpty()) genderColor(e.gender) else MaterialTheme.colorScheme.onSurface)
                            if (e.isNoun && e.plural.isNotEmpty()) Text("die ${e.plural}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(e.glosses.take(2).joinToString("; "), textAlign = TextAlign.Center)
                            repo.examples[e.key]?.firstOrNull()?.takeIf { it.size == 2 }?.let { ex ->
                                HorizontalDivider()
                                Text(ex[0], fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                                Text(ex[1], color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(e.word, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (flipped) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (g in 0..3) {
                                val onClick = { vm.grade(key, g); currentKey = null; flipped = false }
                                val label: @Composable () -> Unit = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(Srs.GRADE_NAMES[g], fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(Srs.label(card, g), fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                                val m = Modifier.weight(1f)
                                val pad = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                // Good is the button you'll press most, so it stands out.
                                if (g == 2) Button(onClick = onClick, modifier = m, contentPadding = pad) { label() }
                                else OutlinedButton(
                                    onClick = onClick, modifier = m, contentPadding = pad,
                                    colors = if (g == 0) ButtonDefaults.outlinedButtonColors(contentColor = genderColor("f")) else ButtonDefaults.outlinedButtonColors(),
                                ) { label() }
                            }
                        }
                    } else {
                        Button(onClick = flip, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Show answer") }
                    }
                }
                Stats(vm)
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
            for (l in 1..3) {
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
            Modifier.size(width = 84.dp, height = 36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("$n", fontWeight = FontWeight.Bold, color = color) }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
