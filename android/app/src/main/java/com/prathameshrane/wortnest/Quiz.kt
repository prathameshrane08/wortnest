package com.prathameshrane.wortnest

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

// ---------- levels ----------

/** Every list from A1 up to the level, so an A2 learner also practises the A1 words. */
fun <T> upTo(lists: List<List<T>>, level: Int): List<T> = (1..level.coerceIn(1, 3)).flatMap { lists[it] }

fun levelRange(level: Int) = if (level > 1) "A1–${LEVEL_NAMES[level]}" else "A1"

// ---------- words in quizzes ----------

/** The first meaning without its notes in brackets, short enough for a button. */
val Entry.shortGloss: String
    get() {
        val first = glosses.firstOrNull().orEmpty()
        val g = first.replace(Regex("\\([^)]*\\)"), "").split(";")[0].replace(Regex("\\s+"), " ").replace(" ,", ",").trim().ifEmpty { first }
        return if (g.length > 44) g.take(42).replace(Regex("[ ,]+\\S*$"), "") + "…" else g
    }

/** For the small Match tiles: only the first meaning when the gloss is long. */
val Entry.tileGloss: String
    get() = shortGloss.let { g -> if (g.length > 22) g.substringBefore(", ") else g }

/** n random words with no repeated word or meaning, so every question has one right answer. */
fun pickUnique(repo: Repository, pool: List<Int>, n: Int): List<Int> {
    val out = ArrayList<Int>(); val words = HashSet<String>(); val glosses = HashSet<String>()
    for (i in pool.shuffled()) {
        val e = repo.entries[i]
        if (!words.add(e.word.lowercase()) || !glosses.add(e.shortGloss.lowercase())) continue
        out.add(i)
        if (out.size == n) break
    }
    return out
}

/** The right answer plus three others, the same part of speech where possible. */
fun options(repo: Repository, i: Int, pool: List<Int>, showEnglish: Boolean): List<Int> {
    val e = repo.entries[i]
    val from = if (pool.size < 12) pool + upTo(repo.levelWords, 3) else pool
    fun key(j: Int) = (if (showEnglish) repo.entries[j].shortGloss else repo.entries[j].word).lowercase()
    val (same, other) = from.shuffled().take(4000).filter { it != i }.partition { repo.entries[it].pos == e.pos }
    val out = arrayListOf(i); val seen = hashSetOf(key(i))
    for (j in same + other) { if (out.size == 4) break; if (seen.add(key(j))) out.add(j) }
    return out.shuffled()
}

class Checked(val ok: Boolean, val note: String = "")

private fun fold(s: String) = s.lowercase().replace(Regex("[.!?;:\"]"), "").replace(Regex("\\s+"), " ").trim()
private fun foldUmlauts(s: String) = fold(s).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
private fun foldEnglish(s: String) = fold(s.replace(Regex("\\([^)]*\\)"), " ")).replace(",", "").replace(Regex("^(to|a|an|the) "), "").trim()

private fun lev(a: String, b: String): Int {
    if (abs(a.length - b.length) > 1) return 2
    var prev = IntArray(b.length + 1) { it }
    for (x in 1..a.length) {
        val cur = IntArray(b.length + 1); cur[0] = x
        for (y in 1..b.length) cur[y] = minOf(prev[y] + 1, cur[y - 1] + 1, prev[y - 1] + if (a[x - 1] == b[y - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

/** German answers: ignore case, allow a missing umlaut or one typo, but a wrong article is wrong. */
fun checkGerman(given: String, e: Entry): Checked {
    var g = fold(given); var art = ""
    Regex("^(der|die|das) (.+)$").find(g)?.let { m -> if (e.isNoun) { art = m.groupValues[1]; g = m.groupValues[2] } }
    val w = fold(e.word)
    val exact = g == w
    val close = !exact && (foldUmlauts(g) == foldUmlauts(w) || (w.length >= 5 && lev(g, w) <= 1))
    if (!exact && !close) return Checked(false)
    val right = e.article
    if (art.isNotEmpty() && right != null && art != right) return Checked(false, "Right word, wrong article: it's $right ${e.word}.")
    return Checked(true, if (close) "Watch the spelling: ${e.word}." else "")
}

/** English answers: any meaning of the word counts, with or without "to" or "a". */
fun checkEnglish(given: String, e: Entry, repo: Repository): Checked {
    val g = foldEnglish(given)
    if (g.isEmpty()) return Checked(false)
    val answers = HashSet<String>()
    repo.entriesFor(e.word).filter { it.word == e.word }.forEach { x ->
        x.glosses.forEach { sense -> sense.replace(Regex("\\([^)]*\\)"), " ").split(Regex("[;,]")).forEach { p -> foldEnglish(p).takeIf { it.isNotEmpty() }?.let(answers::add) } }
    }
    if (g in answers) return Checked(true)
    answers.firstOrNull { it.length >= 5 && lev(it, g) <= 1 }?.let { return Checked(true, "Watch the spelling: $it.") }
    return Checked(false)
}

// ---------- shared pieces ----------

/** Labels in one row (tabs, segments) share a FitGroup so they all shrink by the same amount. */
class FitGroup { var scale by mutableFloatStateOf(1f) }

/** One line of text that shrinks until it fits, instead of being cut off by long compounds or a large system font. */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    group: FitGroup? = null,
) {
    val base = if (fontSize.isSpecified) fontSize else style.fontSize
    var own by remember(text, base) { mutableFloatStateOf(1f) }
    var ready by remember(text, base) { mutableStateOf(false) }
    val scale = group?.scale ?: own
    Text(
        text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        fontSize = base * scale, fontWeight = fontWeight, color = color, textAlign = textAlign, letterSpacing = letterSpacing,
        maxLines = 1, softWrap = false, style = style,
        onTextLayout = {
            if (it.hasVisualOverflow && scale > 0.4f) { if (group != null) group.scale = scale * 0.92f else own = scale * 0.92f }
            else ready = true
        },
    )
}

/** A flashcard you can tap to flip and drag sideways: right for "knew it", left for "not yet". */
@Composable
fun SwipeCard(
    key: Any,
    leftLabel: String,
    rightLabel: String,
    onTap: () -> Unit,
    onSwipe: (right: Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1000f) }
    val lean = offset.value / width
    val shape = RoundedCornerShape(24.dp)
    val border = when {
        lean > 0.12f -> okColor()
        lean < -0.12f -> badColor()
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationX = offset.value; rotationZ = offset.value / 40f; alpha = 1f - (abs(lean) - 0.6f).coerceIn(0f, 0.4f) * 2.5f }
            .pointerInput(key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > width * 0.28f) {
                                val right = offset.value > 0
                                offset.animateTo(sign(offset.value) * width * 1.3f, tween(170))
                                onSwipe(right)
                            } else offset.animateTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, d -> change.consume(); scope.launch { offset.snapTo(offset.value + d) } }
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(if (abs(lean) > 0.12f) 2.dp else 1.dp, border, shape)
                .clickable(onClick = onTap)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            content = content,
        )
        if (abs(lean) > 0.12f) {
            val right = lean > 0
            Text(
                if (right) rightLabel else leftLabel, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                modifier = Modifier.align(if (right) Alignment.TopEnd else Alignment.TopStart).padding(14.dp)
                    .background(if (right) okColor() else badColor(), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** An answer in Choose and in the level test. state: null before answering, then "right", "wrong" or "dim". */
@Composable
fun OptionButton(number: Int, label: String, color: Color, state: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val bg = when (state) { "right" -> okColor().copy(alpha = 0.12f); "wrong" -> badColor().copy(alpha = 0.12f); else -> MaterialTheme.colorScheme.surface }
    val line = when (state) { "right" -> okColor(); "wrong" -> badColor(); else -> MaterialTheme.colorScheme.outline }
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (state == "dim") 0.5f else 1f)
            .clip(shape)
            .background(bg)
            .border(1.5.dp, line, shape)
            .clickable(enabled = state == null, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(22.dp).border(1.dp, line, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text("$number", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f))
    }
}

/** The thin bar and "3 / 10" above a quiz question. */
@Composable
fun QuizProgress(n: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).heightIn(min = 6.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(n.toFloat() / total).heightIn(min = 6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
        }
        Text("${n + 1} / $total", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Colour for a word: the gender colour for nouns, plain text otherwise. */
@Composable
fun wordColor(e: Entry): Color = if (e.isNoun && e.gender.isNotEmpty()) genderColor(e.gender) else MaterialTheme.colorScheme.onSurface
