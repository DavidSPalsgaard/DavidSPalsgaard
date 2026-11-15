package dk.binumre.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dk.binumre.layout.BibTexts
import dk.binumre.model.BibProject
import dk.binumre.model.BibType
import dk.binumre.model.Sheet
import dk.binumre.model.SortBy
import dk.binumre.startlist.NameFormat
import java.time.ZoneId

private val palette = listOf(
    "#FFD400", "#F97316", "#EF4444", "#EC4899", "#A855F7", "#3B82F6", "#14B8A6", "#22C55E",
    "#FFFFFF", "#D4D4D4", "#737373", "#000000", "#92400E", "#1E3A8A", "#166534", "#FDE68A",
)
private val hexColor = Regex("^#[0-9A-Fa-f]{6}$")

@Composable
fun SetupTab(state: AppState) {
    val s = state.strings
    val p = state.project
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(scroll).padding(24.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section(s["setup.general"]) {
                RadioRow(s["setup.template"], p.type, BibType.entries, { s["type.${it.name}"] }) { state.project = p.copy(type = it) }
                OutlinedTextField(
                    p.eventName, { state.project = p.copy(eventName = it) },
                    label = { Text(s["setup.eventName"]) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Dropdown(s["setup.language"], p.language, BibTexts.languages, { Strings.languageName(it) + " ($it)" }) {
                        state.project = p.copy(language = it)
                    }
                    val system = ZoneId.systemDefault().id
                    val zones = remember(p.zone) {
                        (listOf("", "UTC") + ZoneId.getAvailableZoneIds().filter { it.startsWith("Europe/") }.sorted() + p.zone).distinct()
                    }
                    Dropdown(s["setup.zone"], p.zone, zones, { it.ifEmpty { s.format("setup.zone.system", system) } }) {
                        state.project = p.copy(zone = it)
                    }
                }
                RadioRow(s["setup.nameFormat"], p.nameFormat, NameFormat.entries, { s["name.${it.name}"] }) {
                    state.project = p.copy(nameFormat = it)
                }
            }

            val multiday = p.type == BibType.MULTIDAY
            Section(s[if (multiday) "setup.stages" else "setup.distances"]) {
                val count = if (multiday) maxOf(state.lists.size, p.stages.size, 1) else 1
                for (i in 0 until count) StageFields(state, i, multiday)
            }

            if (p.type == BibType.RELAY) Section(s["setup.legColors"]) { LegColors(state) }

            Section(s["setup.print"]) {
                RadioRow(s["setup.sheet"], p.sheet, Sheet.entries, { s["sheet.${it.name}"] }) { state.project = p.copy(sheet = it) }
                RadioRow(s["setup.sort"], p.sortBy, SortBy.entries, { s["sort.${it.name}"] }) { state.project = p.copy(sortBy = it) }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** Overskrift (flerdagesløb) og afstand til start pr. startsted for etape [index]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageFields(state: AppState, index: Int, multiday: Boolean) {
    val s = state.strings
    val stage = state.project.stage(index)
    val list = state.lists.getOrNull(index)
    fun change(f: (dk.binumre.model.Stage) -> dk.binumre.model.Stage) {
        state.project = TableLogic.withStage(state.project, index, f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (multiday) {
            val title = s.format("list.stage", index + 1)
            Text(title + (list?.let { " – " + it.source } ?: ""), style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (multiday) {
                OutlinedTextField(
                    stage.label, { v -> change { it.copy(label = v) } }, label = { Text(s["setup.stageLabel"]) },
                    placeholder = { Text(s.format("list.stage", index + 1)) }, singleLine = true, modifier = Modifier.width(220.dp),
                )
            }
            val names = (TableLogic.startNames(list) + stage.distances.keys.filter { it.isNotEmpty() }).distinct()
            for (name in names) {
                OutlinedTextField(
                    stage.distances[name].orEmpty(), { v -> change { TableLogic.withDistance(it, name, v) } },
                    label = { Text(s.format("setup.distance", name)) }, placeholder = { Text(s["setup.distanceHint"]) },
                    singleLine = true, modifier = Modifier.width(220.dp),
                )
            }
            OutlinedTextField(
                stage.distances[""].orEmpty(), { v -> change { TableLogic.withDistance(it, "", v) } },
                label = { Text(s[if (names.isEmpty()) "setup.distanceAll" else "setup.distanceOther"]) },
                placeholder = { Text(s["setup.distanceHint"]) }, singleLine = true, modifier = Modifier.width(if (names.isEmpty()) 220.dp else 320.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegColors(state: AppState) {
    val s = state.strings
    val colors = state.project.legColors
    fun set(list: List<String>) {
        state.project = state.project.copy(legColors = list)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { i, hex ->
            var open by remember { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.format("setup.leg", i + 1), style = MaterialTheme.typography.labelMedium)
                Box {
                    Swatch(hex, 44) { open = true }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (row in palette.chunked(8)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in row) Swatch(c, 28) { set(colors.mapIndexed { j, old -> if (j == i) c else old }); open = false }
                            }
                            var text by remember { mutableStateOf(hex) }
                            OutlinedTextField(
                                text,
                                { t ->
                                    text = t
                                    if (hexColor.matches(t.trim())) set(colors.mapIndexed { j, old -> if (j == i) t.trim().uppercase() else old })
                                },
                                label = { Text(s["setup.color"]) }, isError = !hexColor.matches(text.trim()), singleLine = true,
                                modifier = Modifier.width(266.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { set(colors + BibProject.DEFAULT_LEG_COLORS[colors.size % BibProject.DEFAULT_LEG_COLORS.size]) }) {
            Text(s["setup.addLeg"])
        }
        OutlinedButton(enabled = colors.size > 1, onClick = { set(colors.dropLast(1)) }) { Text(s["setup.removeLeg"]) }
    }
}

@Composable
private fun Swatch(hex: String, size: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(size.dp).clip(shape).background(Color(0xFF000000 or BibProject.parseColor(hex).toLong()))
            .border(1.dp, Color(0x55000000), shape).clickable(onClick = onClick),
    )
}
