package com.prathameshrane.wortnest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The app icon in small: a box holding der, die and das. */
@Composable
fun BrandTitle() {
    val box = MaterialTheme.colorScheme.primary
    val bars = listOf(genderColor("m"), genderColor("f"), genderColor("n"))
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(30.dp)) {
            val u = size.width / 32f
            bars.forEachIndexed { i, c ->
                drawRoundRect(c, Offset(9 * u, (5 + 7 * i) * u), Size(14 * u, 4.4f * u), CornerRadius(1.4f * u))
            }
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(5 * u, 9 * u); lineTo(5 * u, 24 * u)
                quadraticTo(5 * u, 27 * u, 8 * u, 27 * u); lineTo(24 * u, 27 * u)
                quadraticTo(27 * u, 27 * u, 27 * u, 24 * u); lineTo(27 * u, 9 * u)
            }
            drawPath(path, box, style = Stroke(width = 2.6f * u, cap = StrokeCap.Round))
        }
        Text(
            buildAnnotatedString {
                append("Deutsch ")
                withStyle(SpanStyle(color = box)) { append("Täglich") }
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** The same A1/A2 noun, verb or adjective for everyone on a given day (the website picks it the same way). */
fun wordOfDay(repo: Repository): Entry? {
    val seen = HashSet<String>()
    val pool = repo.entries.filter {
        (it.level == 1 || it.level == 2) && (it.pos == "verb" || it.pos == "adj" || (it.pos == "noun" && it.gender in setOf("m", "f", "n"))) && seen.add(it.word)
    }
    if (pool.isEmpty()) return null
    return pool[((LocalDate.now().toEpochDay() * 7919) % pool.size).toInt()]
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeContent(vm: AppViewModel, repo: Repository, nav: Nav, goTab: (Int) -> Unit, onTry: (String) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        wordOfDay(repo)?.let { WordOfDayCard(vm, repo, it, nav) }
        val n = vm.counts()
        val due = n.fresh + n.learning + n.review
        Tile("Flashcards", "$due", due == 0,
            when { due > 0 -> "cards ready to study"; vm.saved.isNotEmpty() -> "nothing due, well done"; else -> "start with 20 A1 words" }) { goTab(4) }
        Tile("Der, die oder das", "${vm.missedNouns.size}", vm.missedNouns.isEmpty(),
            when { vm.missedNouns.isNotEmpty() -> "nouns you missed to review"; vm.bestStreak > 0 -> "best streak ${vm.bestStreak}"; else -> "guess the article" }) { goTab(1) }
        val read = vm.topicsSeen.count { id -> repo.grammar.any { it.id == id } }
        val next = repo.grammar.firstOrNull { it.id !in vm.topicsSeen }
        Tile("Grammatik", "$read/${repo.grammar.size}", read == 0, next?.let { "next: ${it.title}" } ?: "all topics read") { goTab(3) }
        Text("Or look something up", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in listOf("Haus", "gehen", "schön", "Zeitung", "Mädchen", "bringen", "Freund", "trotzdem")) {
                Text(
                    w,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
                        .clickable { onTry(w) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun WordOfDayCard(vm: AppViewModel, repo: Repository, e: Entry, nav: Nav) {
    val gendered = e.isNoun && e.gender.isNotEmpty()
    val c = if (gendered) genderColor(e.gender) else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        Spacer(Modifier.fillMaxWidth().height(6.dp).background(c))
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Wort des Tages", color = c, fontWeight = FontWeight.ExtraBold)
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                )
            }
            Text(
                buildAnnotatedString {
                    if (gendered) withStyle(SpanStyle(fontSize = 26.sp)) { append(Gender.article(e.gender) + " ") }
                    append(e.word)
                },
                fontSize = 50.sp, lineHeight = 54.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp,
                color = if (gendered) c else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosLabel(e.posName)
                LevelBadge(e.level)
                if (gendered && e.plural.isNotEmpty()) Text("plural: die ${e.plural}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Text(e.glosses.take(2).joinToString("; "), fontSize = 18.sp, lineHeight = 25.sp)
            repo.examples[e.key]?.firstOrNull { it.size == 2 }?.let { ex ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
                    SpeakButton(vm, ex[0])
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        Text(ex[0], fontWeight = FontWeight.SemiBold)
                        Text(ex[1], color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                SpeakButton(vm, e.spoken)
                val on = vm.isSaved(e)
                OutlinedButton(onClick = { vm.toggleSaved(e) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(if (on) "Saved" else "Add to flashcards", maxLines = 1)
                }
                OutlinedButton(onClick = { nav.push(Route.EntryR(e.id)) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Full entry", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Tile(title: String, number: String, zero: Boolean, line: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 2)
        }
        Text(
            number, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            color = if (zero) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
