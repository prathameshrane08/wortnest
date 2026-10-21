package com.prathameshrane.wortnest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WortNestTheme {
                WortNestApp(viewModel())
            }
        }
    }
}

sealed interface Route {
    data class EntryR(val id: Int) : Route
    data class VerbR(val infinitive: String) : Route
    data object Drill : Route
    data class TopicR(val id: String) : Route
}

class Nav(val push: (Route) -> Unit, val pop: () -> Unit)

private val tabs = listOf(
    "Wörterbuch" to Icons.Outlined.Book,
    "Der Die Das" to Icons.Outlined.GridView,
    "Verben" to Icons.Outlined.TableChart,
    "Grammatik" to Icons.AutoMirrored.Outlined.MenuBook,
    "Üben" to Icons.Outlined.StarOutline,
)

@Composable
fun WortNestApp(vm: AppViewModel) {
    val repo = vm.repo
    if (repo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val error = vm.loadError
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading 40,000 words…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val saveable = rememberSaveableStateHolder()   // each tab keeps its scroll position and quiz state
    val stacks = remember { List(tabs.size) { mutableStateListOf<Route>() } }
    val stack = stacks[tab]
    val nav = Nav(push = { stack.add(it) }, pop = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) })
    BackHandler(enabled = stack.isNotEmpty()) { nav.pop() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { if (tab == i) stacks[i].clear() else tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            saveable.SaveableStateProvider("tab$tab-${stack.size}") { Screen(vm, repo, nav, tab, stack.lastOrNull()) }
        }
    }
}

@Composable
private fun Screen(vm: AppViewModel, repo: Repository, nav: Nav, tab: Int, top: Route?) {
            when (top) {
                null -> when (tab) {
                    0 -> DictionaryScreen(vm, repo, nav)
                    1 -> ArticleScreen(vm, repo)
                    2 -> VerbsScreen(vm, repo, nav)
                    3 -> GrammarScreen(repo, nav)
                    else -> LearnScreen(vm, repo, nav)
                }
                is Route.EntryR -> EntryScreen(vm, repo, repo.entries[top.id], nav)
                is Route.VerbR -> VerbDetailScreen(vm, repo, top.infinitive, nav)
                Route.Drill -> DrillScreen(repo, nav)
                is Route.TopicR -> GrammarTopicScreen(vm, repo, top.id, nav)
            }
}
