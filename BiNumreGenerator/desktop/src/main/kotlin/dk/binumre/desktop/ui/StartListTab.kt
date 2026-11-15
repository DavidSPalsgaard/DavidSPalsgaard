package dk.binumre.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.binumre.model.BibType
import dk.binumre.startlist.StartEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val startListExtensions = listOf("xml", "xlsx", "csv", "txt")

@Composable
fun StartListTab(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    val multiday = state.project.type == BibType.MULTIDAY
    var current by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf<String?>(null) }
    var onlyMissing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var ofeed by remember { mutableStateOf(false) }

    val listIndex = current.coerceIn(0, maxOf(0, state.lists.size - 1))
    val list = state.lists.getOrNull(listIndex)
    if (cls != null && list?.classes?.contains(cls) != true) cls = null

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val before = state.lists.size
                state.loadFiles(dialogs.open(s["dialog.startlist"], startListExtensions, multiple = true))
                current = if (state.project.type == BibType.MULTIDAY) before.coerceAtMost(maxOf(0, state.lists.size - 1)) else 0
            }) { Text(s[if (multiday && state.lists.isNotEmpty()) "list.addStage" else "list.load"]) }
            OutlinedButton(onClick = { ofeed = true }) { Text(s["list.ofeed"]) }
            state.lists.forEachIndexed { i, l ->
                val used = multiday || i == 0
                FilterChip(
                    selected = i == listIndex,
                    onClick = { current = i },
                    label = {
                        val name = if (multiday) s.format("list.stage", i + 1) + ": " + l.source else l.source
                        Text(name + if (used) "" else " (${s["list.notUsed"]})", maxLines = 1)
                    },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = s["remove"], modifier = Modifier.clickable { state.removeList(i) })
                    },
                )
            }
        }

        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s["list.empty"], Modifier.widthIn(max = 520.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val entries = list.entries
            val shown = remember(entries, query, cls, onlyMissing) { TableLogic.filter(entries, query, cls, onlyMissing) }
            val relay = state.project.type == BibType.RELAY
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text(s["list.search"]) }, singleLine = true, modifier = Modifier.width(260.dp))
                Dropdown("", cls, listOf<String?>(null) + list.classes, { it ?: s["list.allClasses"] }) { cls = it }
                Row(Modifier.clickable { onlyMissing = !onlyMissing }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(onlyMissing, { onlyMissing = it })
                    Text(s["list.onlyMissing"])
                }
                Spacer(Modifier.weight(1f))
                val missing = entries.count { it.bib.isEmpty() && !it.excluded }
                Text(
                    s.format("list.summary", shown.size, entries.size, entries.count { !it.excluded }, missing),
                    color = if (missing > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                // Overskrift: afkrydsningen vælger/fravælger alle viste (fx en hel klasse)
                val included = shown.count { !entries[it].excluded }
                val shownSet = remember(shown) { shown.toHashSet() }
                Row(
                    Modifier.fillMaxWidth().padding(end = 10.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp).height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(
                        state = when (included) {
                            0 -> ToggleableState.Off
                            shown.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        },
                        onClick = {
                            val exclude = included == shown.size
                            state.updateEntries(listIndex) { i, e -> if (i in shownSet) e.copy(excluded = exclude) else e }
                        },
                        modifier = Modifier.width(40.dp),
                    )
                    for ((key, weight) in columns(relay)) Cell(s[key], weight, bold = true)
                    Spacer(Modifier.width(40.dp))
                }
                Box(Modifier.weight(1f)) {
                    val scroll = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = scroll) {
                        items(shown, key = { it }) { i ->
                            val e = entries[i]
                            EntryRow(state, e, relay, onToggle = { on ->
                                state.updateEntries(listIndex) { j, x -> if (j == i) x.copy(excluded = !on) else x }
                            }, onEdit = { editing = i })
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }

    val edit = editing
    if (edit != null && list != null && edit in list.entries.indices) {
        EditDialog(state, list.entries[edit], state.dateOf(listIndex), state.project.type == BibType.RELAY, onClose = { editing = null }) { changed ->
            state.updateEntries(listIndex) { j, x -> if (j == edit) changed else x }
            editing = null
        }
    }
    if (ofeed) OFeedDialog(state) { ofeed = false }
}

private fun columns(relay: Boolean): List<Pair<String, Float>> = listOf(
    "col.bib" to 0.6f, "col.name" to 2.2f, "col.club" to 1.8f, "col.class" to 0.9f, "col.card" to 1f,
    "col.start" to 0.9f, "col.course" to 0.7f, "col.startName" to 0.7f,
) + if (relay) listOf("col.team" to 1.6f, "col.leg" to 0.5f) else emptyList()

@Composable
private fun RowScope.Cell(text: String, weight: Float, bold: Boolean = false, dim: Boolean = false) {
    Text(
        text, Modifier.weight(weight).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (dim) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (bold) FontWeight.Bold else null, style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun EntryRow(state: AppState, e: StartEntry, relay: Boolean, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val missing = e.bib.isEmpty() && !e.excluded
    Row(
        Modifier.fillMaxWidth()
            .background(if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.background)
            .clickable(onClick = onEdit).padding(horizontal = 8.dp).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(!e.excluded, onToggle, Modifier.width(40.dp))
        val values = listOf(
            e.bib, e.displayName(state.project.nameFormat), e.club, e.cls, e.card?.toString().orEmpty(),
            TableLogic.time(e.start, state.zone), e.course, e.startName,
        ) + if (relay) listOf(e.teamName, e.leg) else emptyList()
        columns(relay).forEachIndexed { i, (_, weight) -> Cell(values[i], weight, bold = i == 0, dim = e.excluded) }
        IconButton(onClick = onEdit, modifier = Modifier.width(40.dp)) {
            Icon(Icons.Default.Edit, contentDescription = state.strings["list.edit"], tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditDialog(state: AppState, entry: StartEntry, date: java.time.LocalDate, relay: Boolean, onClose: () -> Unit, onSave: (StartEntry) -> Unit) {
    val s = state.strings
    var f by remember { mutableStateOf(TableLogic.fields(entry, state.zone)) }
    var badField by remember { mutableStateOf<String?>(null) }

    @Composable
    fun RowScope.Field(key: String, value: String, weight: Float = 1f, error: Boolean = false, modifier: Modifier = Modifier, set: (String) -> Unit) {
        OutlinedTextField(value, set, label = { Text(s[key], maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, isError = error, modifier = modifier.weight(weight))
    }
    // Startnummeret er det man oftest retter: markøren står der fra start
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s["edit.title"]) },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val gap = Arrangement.spacedBy(8.dp)
                Row(horizontalArrangement = gap) {
                    Field("edit.bib", f.bib, modifier = Modifier.focusRequester(focus)) { f = f.copy(bib = it) }
                    Field("col.class", f.cls) { f = f.copy(cls = it) }
                    Field("col.card", f.card, error = badField == "card") { f = f.copy(card = it) }
                }
                Row(horizontalArrangement = gap) {
                    Field("edit.given", f.given) { f = f.copy(given = it) }
                    Field("edit.family", f.family) { f = f.copy(family = it) }
                }
                if (f.name.isNotEmpty() || (entry.given.isEmpty() && entry.family.isEmpty())) {
                    Row { Field("edit.fullName", f.name) { f = f.copy(name = it) } }
                }
                Row { Field("col.club", f.club) { f = f.copy(club = it) } }
                Row(horizontalArrangement = gap) {
                    Field("edit.start", f.start, weight = 2f, error = badField == "start") { f = f.copy(start = it) }
                    Field("col.course", f.course) { f = f.copy(course = it) }
                    Field("col.startName", f.startName) { f = f.copy(startName = it) }
                }
                if (relay) Row(horizontalArrangement = gap) {
                    Field("col.team", f.teamName, weight = 3f) { f = f.copy(teamName = it) }
                    Field("col.leg", f.leg) { f = f.copy(leg = it) }
                }
                badField?.let { Text(s["edit.error.$it"], color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    onSave(TableLogic.apply(entry, f, state.zone, date))
                } catch (e: FieldException) {
                    badField = e.field
                }
            }) { Text(s["ok"]) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(s["cancel"]) } },
    )
}

@Composable
private fun OFeedDialog(state: AppState, onClose: () -> Unit) {
    val s = state.strings
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(s["ofeed.title"]) },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(link, { link = it }, label = { Text(s["ofeed.link"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(s["ofeed.password"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = !busy && link.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { state.fetchOFeed(link, password) } }
                    busy = false
                    result.onSuccess { state.addLists(listOf(it)); onClose() }
                        .onFailure { state.error = it.message ?: it.toString() }
                }
            }) { Text(s[if (busy) "ofeed.fetching" else "ofeed.fetch"]) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(s["cancel"]) } },
    )
}
