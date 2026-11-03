package com.prathameshrane.wortnest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeroText = Color.White
private val HeroMuted = Color(0xFFCBD5E1)
private val HeroLabel = Color(0xFFA5B4FC)
private val HeroButton = Color.White.copy(alpha = 0.1f)

/** The app icon in small (a box holding der, die and das), the name and the walkthrough button. */
@Composable
fun BrandTitle(dark: Boolean, onToggleDark: () -> Unit, onTour: () -> Unit) {
    val box = MaterialTheme.colorScheme.primary
    val bars = listOf(genderColor("m"), genderColor("f"), genderColor("n"))
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Canvas(Modifier.size(28.dp)) {
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
            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onToggleDark,
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = Modifier.size(34.dp),
        ) {
            Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode", modifier = Modifier.size(18.dp))
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onTour)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
            Text("Tour", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/** "Guten Morgen" and how much is waiting, above the search box. */
@Composable
fun Greeting(vm: AppViewModel, repo: Repository) {
    val h = LocalTime.now().hour
    val n = vm.counts()
    val todo = listOf(n.fresh + n.learning + n.review > 0, vm.missedNouns.isNotEmpty(), repo.grammar.any { it.id !in vm.topicsSeen }).count { it }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
        Text(
            (if (h < 11) "Guten Morgen" else if (h < 18) "Guten Tag" else "Guten Abend") + " 👋",
            fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
        )
        Text(
            (if (h < 11) "Good morning. " else if (h < 18) "Good afternoon. " else "Good evening. ") +
                if (todo == 0) "All caught up for today" else "$todo thing${if (todo > 1) "s" else ""} to do today",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The same noun, verb or adjective for everyone at a level on a given day (the website picks it the same way).
 *  The pool holds every word up to the level, so B1 learners still meet A1 and A2 words. */
fun wordOfDay(repo: Repository, level: Int): Entry? {
    val seen = HashSet<String>()
    val pool = repo.entries.filter {
        it.level in 1..level && (it.pos == "verb" || it.pos == "adj" || (it.pos == "noun" && it.gender in setOf("m", "f", "n"))) && seen.add(it.word)
    }
    if (pool.isEmpty()) return null
    return pool[((LocalDate.now().toEpochDay() * 7919) % pool.size).toInt()]
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeContent(vm: AppViewModel, repo: Repository, nav: Nav, goTab: (Int) -> Unit, openLevel: () -> Unit, onTry: (String) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        wordOfDay(repo, vm.level)?.let { WordOfDayCard(vm, repo, it, nav) }

        val n = vm.counts()
        val due = n.fresh + n.learning + n.review
        val total = vm.saved.size
        val read = vm.topicsSeen.count { id -> repo.grammar.any { it.id == id } }
        val next = repo.grammar.firstOrNull { it.id !in vm.topicsSeen }
        val levelWords = upTo(repo.levelWords, vm.level)
        val inLevel = levelWords.count { vm.saved.containsKey(repo.entries[it].key) }
        fun frac(a: Int, b: Int) = if (b == 0) 0f else (a.toFloat() / b).coerceIn(0f, 1f)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("$due", "Cards due", if (total == 0) "tap to start" else "${n.known} of $total known well",
                frac(n.known, total), MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { goTab(4) }
            Tile("${vm.missedNouns.size}", "Nouns to review", "best streak ${vm.bestStreak}",
                frac(vm.bestStreak, 50), genderColor("n"), Modifier.weight(1f)) { goTab(1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("$read/${repo.grammar.size}", "Grammar topics", next?.let { "next: ${it.title}" } ?: "all read",
                frac(read, repo.grammar.size), Color(0xFFF59E0B), Modifier.weight(1f)) { goTab(3) }
            Tile(LEVEL_NAMES[vm.level], "Your level", if (vm.level < 3) "${LEVEL_NAMES[vm.level + 1]} test when you're ready" else "$inLevel of ${levelWords.size} words saved",
                frac(inLevel, levelWords.size), Color(0xFFEC4899), Modifier.weight(1f), openLevel)
        }

        Text("Or look something up", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in listOf("Haus", "gehen", "schön", "Zeitung", "Mädchen", "bringen", "Freund", "trotzdem")) {
                Text(
                    w,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clickable { onTry(w) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun WordOfDayCard(vm: AppViewModel, repo: Repository, e: Entry, nav: Nav) {
    val gendered = e.isNoun && e.gender.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(heroColor()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Word of the day", color = HeroLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)), color = HeroMuted, fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                if (gendered) Text(Gender.article(e.gender)!!, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = genderColorOnDark(e.gender))
                FitText(e.word, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, color = HeroText)
            }
            IconButton(
                onClick = { vm.speak(e.spoken) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = HeroButton, contentColor = HeroText),
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Pronounce") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(e.posName, color = HeroMuted, fontStyle = FontStyle.Italic, fontSize = 14.sp)
            if (e.level > 0) Text(
                LEVEL_NAMES[e.level], color = HeroText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(HeroButton, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
            )
            if (gendered && e.plural.isNotEmpty()) Text("plural: die ${e.plural}", color = HeroMuted, fontSize = 14.sp)
        }
        Text(e.glosses.take(2).joinToString("; "), fontSize = 17.sp, lineHeight = 24.sp, color = HeroMuted, modifier = Modifier.padding(top = 4.dp))
        repo.examples[e.key]?.firstOrNull { it.size == 2 }?.let { ex ->
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconButton(
                    onClick = { vm.speak(ex[0]) },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = HeroButton, contentColor = HeroText),
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read the sentence aloud", modifier = Modifier.size(18.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(ex[0], fontWeight = FontWeight.SemiBold, color = HeroText)
                    Text(ex[1], color = HeroMuted, fontSize = 14.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            val on = vm.isSaved(e)
            val colors = ButtonDefaults.buttonColors(containerColor = HeroButton, contentColor = HeroText)
            Button(onClick = { vm.toggleSaved(e) }, colors = colors, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                FitText(if (on) "Saved" else "Add to flashcards")
            }
            Button(onClick = { nav.push(Route.EntryR(e.id)) }, colors = colors, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                FitText("Full entry")
            }
        }
    }
}

@Composable
private fun Tile(number: String, title: String, line: String, progress: Float, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(number, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(line, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.padding(top = 12.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceAtLeast(0.03f)).clip(CircleShape).background(color))
        }
    }
}
