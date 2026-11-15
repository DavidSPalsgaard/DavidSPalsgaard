package dk.binumre.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import java.io.File

private val colors = lightColorScheme(
    primary = Color(0xFFB8430B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCCB),
    onPrimaryContainer = Color(0xFF3A1200),
    secondary = Color(0xFF6B5B52),
    secondaryContainer = Color(0xFFF3E3DA),
    background = Color(0xFFFCFAF8),
    surface = Color(0xFFFCFAF8),
    surfaceVariant = Color(0xFFEFE7E2),
)

/** Starter brugerfladen. [args]: startlister der indlæses med det samme, og evt. `--project <fil>`. */
fun runGui(args: List<String>) {
    val state = AppState()
    val files = mutableListOf<File>()
    var i = 0
    while (i < args.size) {
        if (args[i] == "--project") args.getOrNull(++i)?.let { state.openProject(File(it)) } else files += File(args[i])
        i++
    }
    state.loadFiles(files)

    application {
        var confirmClose by remember { mutableStateOf(false) }
        val name = state.projectFile?.name
        val title = listOfNotNull(name, "BiNumreGenerator").joinToString(" – ") +
            if (state.dirty && name != null) " (${state.strings["status.unsaved"]})" else ""
        Window(
            onCloseRequest = { if (state.dirty) confirmClose = true else exitApplication() },
            state = rememberWindowState(size = DpSize(1280.dp, 860.dp)),
            title = title,
        ) {
            val dialogs = remember { FileDialogs(state, window) }
            MaterialTheme(colorScheme = colors) {
                App(state, dialogs)
                if (confirmClose) {
                    val s = state.strings
                    AlertDialog(
                        onDismissRequest = { confirmClose = false },
                        title = { Text(s["confirm.close.title"]) },
                        text = { Text(s["confirm.close.text"]) },
                        confirmButton = {
                            Button(onClick = {
                                confirmClose = false
                                if (saveProject(state, dialogs, false)) exitApplication()
                            }) { Text(s["confirm.save"]) }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = { exitApplication() }) { Text(s["confirm.discard"]) }
                                TextButton(onClick = { confirmClose = false }) { Text(s["cancel"]) }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Gemmer projektet (spørger om filnavn første gang eller ved [saveAs]). False når brugeren fortryder eller det fejler. */
private fun saveProject(state: AppState, dialogs: FileDialogs, saveAs: Boolean): Boolean {
    val file = state.projectFile.takeIf { !saveAs }
        ?: dialogs.save(state.strings["dialog.saveProject"], state.projectFile?.name ?: "startnumre.json", "json")
        ?: return false
    state.saveProject(file)
    return !state.dirty
}

@Composable
fun App(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    var tab by remember { mutableStateOf(0) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = { state.newProject() }) { Text(s["menu.new"]) }
                TextButton(onClick = {
                    dialogs.open(s["dialog.openProject"], listOf("json")).firstOrNull()?.let(state::openProject)
                }) { Text(s["menu.open"]) }
                TextButton(onClick = { saveProject(state, dialogs, false) }) { Text(s["menu.save"]) }
                TextButton(onClick = { saveProject(state, dialogs, true) }) { Text(s["menu.saveAs"]) }
                Spacer(Modifier.weight(1f))
                LanguageMenu(state)
                Button(onClick = {
                    if (state.bibSet.bibs.isEmpty()) state.error = s["error.noBibs"]
                    else dialogs.save(s["dialog.pdf"], state.lastPdf?.name ?: "startnumre.pdf", "pdf")?.let(state::writePdf)
                }) { Text(s["menu.pdf"]) }
            }
            TabRow(selectedTabIndex = tab) {
                listOf("tab.startlist", "tab.setup", "tab.editor").forEachIndexed { i, key ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(s[key]) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> StartListTab(state, dialogs)
                    1 -> SetupTab(state)
                    else -> EditorTab(state, dialogs)
                }
            }
            StatusBar(state)
        }
    }
    state.error?.let { text ->
        AlertDialog(
            onDismissRequest = { state.error = null },
            title = { Text(s["error.title"]) },
            text = { Text(text) },
            confirmButton = { Button(onClick = { state.error = null }) { Text(s["ok"]) } },
        )
    }
}

@Composable
private fun LanguageMenu(state: AppState) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(Strings.languageName(state.strings.language)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (code in Strings.available) {
                DropdownMenuItem(text = { Text(Strings.languageName(code)) }, onClick = { state.setLanguage(code); open = false })
            }
        }
    }
}

@Composable
private fun StatusBar(state: AppState) {
    val s = state.strings
    val set = state.bibSet
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(state.message, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        state.lastPdf?.let { pdf ->
            if (Desktop.isDesktopSupported()) {
                TextButton(onClick = { runCatching { Desktop.getDesktop().open(pdf) }.onFailure { state.error = it.message } }) {
                    Text(s["status.showPdf"])
                }
            }
        }
        if (set.withoutNumber > 0) {
            Text(s.format("status.missing", set.withoutNumber), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(16.dp))
        }
        Text(s.format("status.bibs", set.bibs.size), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}
