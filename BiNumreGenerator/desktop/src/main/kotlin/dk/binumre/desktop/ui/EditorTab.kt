package dk.binumre.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dk.binumre.desktop.SkiaBibCanvas
import dk.binumre.layout.BibLayout
import dk.binumre.layout.GraphicEdit
import dk.binumre.model.BibProject
import java.io.File

private val selection = Color(0xFF2563EB)

@Composable
fun EditorTab(state: AppState, dialogs: FileDialogs) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFD9D4CF))) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                val ratio = (BibLayout.WIDTH / BibLayout.HEIGHT).toFloat()
                val width = if (maxWidth / ratio <= maxHeight) maxWidth else maxHeight * ratio
                Preview(state, Modifier.size(width, width / ratio))
            }
            Navigation(state)
        }
        GraphicsPanel(state, dialogs)
    }
}

/** Startnummeret tegnet med [BibLayout.draw] – samme kode som PDF'en – med ramme og hjørner om den valgte grafik. */
@Composable
private fun Preview(state: AppState, modifier: Modifier) {
    Canvas(
        modifier.shadow(6.dp).background(Color.White).clipToBounds().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val scale = size.width / BibLayout.WIDTH
                val hit = GraphicEdit.hit(
                    state.project.graphics, down.position.x / scale, down.position.y / scale,
                    state.selectedGraphic, tolerance = 10.dp.toPx() / scale,
                )
                state.selectedGraphic = hit?.first
                if (hit != null) {
                    val (index, handle) = hit
                    val start = state.project.graphics[index]
                    drag(down.id) { change ->
                        val moved = change.position - down.position
                        state.updateGraphic(index, GraphicEdit.drag(start, handle, moved.x / scale, moved.y / scale))
                        change.consume()
                    }
                }
            }
        },
    ) {
        val scale = size.width / BibLayout.WIDTH.toFloat()
        val project = state.project
        drawIntoCanvas { BibLayout.draw(SkiaBibCanvas(it.nativeCanvas, scale, state.images::get), state.previewBib, project, state.zone) }
        project.graphics.forEachIndexed { i, g ->
            val topLeft = Offset((g.x * scale).toFloat(), (g.y * scale).toFloat())
            val size = Size((g.width * scale).toFloat(), (g.height * scale).toFloat())
            if (state.images.get(g.file) == null) {
                drawRect(Color(0x33DC2626), topLeft, size)
                drawRect(Color(0xFFDC2626), topLeft, size, style = Stroke(1.dp.toPx()))
            }
            if (i == state.selectedGraphic) {
                drawRect(selection, topLeft, size, style = Stroke(1.5.dp.toPx()))
                val h = 8.dp.toPx()
                for (corner in listOf(topLeft, topLeft + Offset(size.width, 0f), topLeft + Offset(0f, size.height), topLeft + Offset(size.width, size.height))) {
                    drawRect(Color.White, corner - Offset(h / 2, h / 2), Size(h, h))
                    drawRect(selection, corner - Offset(h / 2, h / 2), Size(h, h), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

/** Bladring mellem løberne. */
@Composable
private fun Navigation(state: AppState) {
    val s = state.strings
    val bibs = state.bibSet.bibs
    val index = state.previewIndex.coerceIn(0, maxOf(0, bibs.size - 1))
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(enabled = index > 0, onClick = { state.previewIndex = index - 1 }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s["editor.prev"])
        }
        val bib = bibs.getOrNull(index)
        Text(
            if (bib == null) s["editor.sample"]
            else s.format("editor.runner", index + 1, bibs.size) + "  ·  " +
                listOf(bib.number, bib.displayName(state.project.nameFormat), bib.cls).filter { it.isNotEmpty() }.joinToString("  "),
            Modifier.padding(horizontal = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        IconButton(enabled = index < bibs.size - 1, onClick = { state.previewIndex = index + 1 }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = s["editor.next"])
        }
    }
}

@Composable
private fun GraphicsPanel(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    val graphics = state.project.graphics
    Column(
        Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(s.format("editor.graphics", graphics.size, BibProject.MAX_GRAPHICS), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Button(enabled = state.canAddGraphic, onClick = {
            dialogs.open(s["dialog.image"], listOf("png", "jpg", "jpeg"), multiple = true).forEach(state::addGraphic)
        }) { Text(s["editor.add"]) }
        Text(s["editor.hint"], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        graphics.forEachIndexed { i, g ->
            val selected = i == state.selectedGraphic
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier.fillMaxWidth()
                    .border(if (selected) 2.dp else 1.dp, if (selected) selection else MaterialTheme.colorScheme.surfaceVariant, shape)
                    .clickable { state.selectedGraphic = i }.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(File(g.file).name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.images.get(g.file) == null) {
                            Text(s["editor.missing"], color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    IconButton(onClick = { state.removeGraphic(i) }) { Icon(Icons.Default.Delete, contentDescription = s["remove"]) }
                }
                // Felterne går gennem GraphicEdit, så grafikken holdes inden for startnummeret ligesom ved træk
                val gap = Arrangement.spacedBy(6.dp)
                Row(horizontalArrangement = gap) {
                    NumberField(s["editor.x"], g.x, Modifier.weight(1f)) { state.updateGraphic(i, g.copy(x = it.coerceIn(0.0, maxOf(0.0, BibLayout.WIDTH - g.width)))) }
                    NumberField(s["editor.y"], g.y, Modifier.weight(1f)) { state.updateGraphic(i, g.copy(y = it.coerceIn(0.0, maxOf(0.0, BibLayout.HEIGHT - g.height)))) }
                }
                Row(horizontalArrangement = gap) {
                    NumberField(s["editor.width"], g.width, Modifier.weight(1f)) { state.updateGraphic(i, g.copy(width = it.coerceIn(GraphicEdit.MIN_SIZE, BibLayout.WIDTH - g.x))) }
                    NumberField(s["editor.height"], g.height, Modifier.weight(1f)) { state.updateGraphic(i, g.copy(height = it.coerceIn(GraphicEdit.MIN_SIZE, BibLayout.HEIGHT - g.y))) }
                }
            }
        }
    }
}
