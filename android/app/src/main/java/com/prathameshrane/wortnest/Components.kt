package com.prathameshrane.wortnest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, nav: Nav) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = nav.pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

/** Quick buttons for ä ö ü ß, which many keyboards hide. */
@Composable
fun UmlautRow(onInsert: (String) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in listOf("ä", "ö", "ü", "ß")) {
            Text(
                c,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                    .clickable { onInsert(c) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
fun ArticleChip(gender: String, large: Boolean = false) {
    val art = Gender.article(gender) ?: return
    val c = genderColor(gender)
    Text(
        art,
        color = c,
        fontWeight = FontWeight.Bold,
        fontSize = if (large) 17.sp else 13.sp,
        modifier = Modifier
            .background(c.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
            .padding(horizontal = if (large) 10.dp else 6.dp, vertical = if (large) 3.dp else 0.dp),
    )
}

@Composable
fun PosLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontStyle = FontStyle.Italic,   // part of speech, set like a printed dictionary
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun EntryRow(entry: Entry, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (entry.isNoun) ArticleChip(entry.gender)
            Text(
                entry.word,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.isNoun && entry.gender.isNotEmpty()) genderColor(entry.gender) else MaterialTheme.colorScheme.onSurface,
            )
            if (entry.isNoun && entry.plural.isNotEmpty()) {
                Text("pl. ${entry.plural}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!entry.isNoun) PosLabel(entry.posName)
            LevelBadge(entry.level)
        }
        Text(
            entry.glosses.take(3).joinToString("; "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Composable
fun SpeakButton(vm: AppViewModel, text: String) {
    FilledTonalIconButton(onClick = { vm.speak(text) }) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Pronounce")
    }
}

@Composable
fun SaveButton(vm: AppViewModel, entry: Entry) {
    val on = vm.isSaved(entry)
    FilledTonalIconButton(
        onClick = { vm.toggleSaved(entry) },
        colors = if (on) IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ) else IconButtonDefaults.filledTonalIconButtonColors(),
    ) {
        Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = if (on) "Remove from flashcards" else "Add to flashcards")
    }
}

/** A row of choices like Quiz | Flashcards; labels shrink to fit with large system fonts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val fit = androidx.compose.runtime.remember(labels) { FitGroup() }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = selected == i,
                onClick = { if (selected != i) onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                icon = {},
                label = { FitText(label, fontSize = 14.sp, group = fit) },
            )
        }
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
