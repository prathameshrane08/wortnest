package com.prathameshrane.wortnest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** One topic of the A1–B1 grammar guide (grammar.json, shared with the web and iOS apps). */
data class GrammarTopic(
    val id: String, val level: Int, val title: String, val de: String, val summary: String,
    val blocks: List<GrammarBlock>, val verbs: List<String>,
)

/** A paragraph ("p"), a tip ("tip"), example sentences ("ex") or a table ("table"). */
data class GrammarBlock(
    val type: String, val text: String = "", val examples: List<List<String>> = emptyList(),
    val head: List<String> = emptyList(), val rows: List<List<String>> = emptyList(),
)

fun parseGrammar(json: String): List<GrammarTopic> {
    fun JSONArray.strings() = List(length()) { optString(it) }
    fun JSONArray.table() = List(length()) { getJSONArray(it).strings() }
    val topics = JSONObject(json).getJSONArray("topics")
    return List(topics.length()) { i ->
        val t = topics.getJSONObject(i)
        val blocks = t.getJSONArray("blocks")
        GrammarTopic(
            id = t.getString("id"), level = t.getInt("level"), title = t.getString("title"), de = t.getString("de"),
            summary = t.getString("summary"), verbs = t.optJSONArray("verbs")?.strings().orEmpty(),
            blocks = List(blocks.length()) { k ->
                val b = blocks.getJSONObject(k)
                when (val type = b.getString("t")) {
                    "ex" -> GrammarBlock(type, examples = b.getJSONArray("x").table())
                    "table" -> GrammarBlock(type, head = b.getJSONArray("head").strings(), rows = b.getJSONArray("rows").table())
                    else -> GrammarBlock(type, text = b.optString("x"))
                }
            },
        )
    }
}

val LEVEL_NAMES = listOf("", "A1", "A2", "B1")
private val LEVEL_TITLES = listOf("", "Beginner", "Elementary", "Intermediate")

@Composable
fun levelColor(level: Int): Color = when (level) { 1 -> genderColor("n"); 2 -> genderColor("m"); else -> genderColor("p") }

/** The small A1 / A2 / B1 tag. */
@Composable
fun LevelBadge(level: Int) {
    if (level <= 0) return
    val c = levelColor(level)
    Text(
        LEVEL_NAMES[level], fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = c,
        modifier = Modifier.background(c.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Turns **bold** markers into bold text. */
fun rich(s: String): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part) }
}

@Composable
fun GrammarScreen(repo: Repository, nav: Nav) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Grammatik")
        Text(
            "The topics German courses cover from A1 to B1, each with tables, examples and the mistakes learners make most.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
        )
        for (level in 1..3) {
            Row(Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelBadge(level)
                Text(LEVEL_TITLES[level], fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            repo.grammar.filter { it.level == level }.forEach { t ->
                Column(Modifier.fillMaxWidth().clickable { nav.push(Route.TopicR(t.id)) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(t.title, fontWeight = FontWeight.SemiBold)
                    Text(t.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.padding(12.dp))
    }
}

@Composable
fun GrammarTopicScreen(vm: AppViewModel, repo: Repository, id: String, nav: Nav) {
    val all = repo.grammar
    val i = all.indexOfFirst { it.id == id }
    val t = all.getOrNull(i) ?: return
    Column(Modifier.fillMaxSize()) {
        BackBar(t.title, nav)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelBadge(t.level)
                Text(t.de.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(t.title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
            t.blocks.forEach { b ->
                when (b.type) {
                    "tip" -> Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("WATCH OUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(rich(b.text))
                    }
                    "ex" -> CardBox {
                        b.examples.filter { it.size == 2 }.forEach { ex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                SpeakButton(vm, ex[0].replace("**", ""))
                                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                                    Text(rich(ex[0]), fontWeight = FontWeight.SemiBold)
                                    Text(rich(ex[1]), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    "table" -> GrammarTable(b)
                    else -> Text(rich(b.text), lineHeight = 22.sp)
                }
            }
            val verbs = t.verbs.filter { it in repo.verbs }
            if (verbs.isNotEmpty()) {
                Text("PRACTISE IN THE CONJUGATOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    verbs.forEach { v -> FilledTonalButton(onClick = { nav.push(Route.VerbR(v)) }) { Text(v) } }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                all.getOrNull(i - 1)?.let { p -> TextButton(onClick = { nav.pop(); nav.push(Route.TopicR(p.id)) }) { Text("‹ ${p.title}", maxLines = 1) } } ?: Spacer(Modifier)
                all.getOrNull(i + 1)?.let { n -> TextButton(onClick = { nav.pop(); nav.push(Route.TopicR(n.id)) }) { Text("${n.title} ›", maxLines = 1) } }
            }
        }
    }
}

/** A table that scrolls sideways when it is wider than the screen. Columns size to their longest cell. */
@Composable
private fun GrammarTable(b: GrammarBlock) {
    val cols = b.head.size
    val widths = (0 until cols).map { c ->
        val longest = (listOf(b.head) + b.rows).maxOf { it.getOrElse(c) { "" }.replace("**", "").length }
        (longest * 8 + 16).coerceIn(56, 200).dp
    }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            b.head.forEachIndexed { c, h ->
                Text(rich(h), Modifier.width(widths[c]), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.width(widths.fold(0.dp) { a, w -> a + w }))
        b.rows.forEach { row ->
            Row {
                row.forEachIndexed { c, cell ->
                    Text(
                        rich(cell), Modifier.width(widths.getOrElse(c) { 80.dp }),
                        fontSize = if (c == 0) 13.sp else 15.sp,
                        color = if (c == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
