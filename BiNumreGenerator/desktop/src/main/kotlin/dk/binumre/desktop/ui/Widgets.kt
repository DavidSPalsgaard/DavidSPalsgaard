package dk.binumre.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Rullemenu med overskrift. */
@Composable
fun <T> Dropdown(label: String, value: T, options: List<T>, text: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(text(value))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (o in options) DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

/** Vandret række af radioknapper med overskrift. */
@Composable
fun <T> RadioRow(label: String, value: T, options: List<T>, text: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (o in options) {
                Row(Modifier.selectable(selected = o == value, onClick = { onSelect(o) }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = o == value, onClick = { onSelect(o) })
                    Text(text(o))
                    Spacer(Modifier.width(16.dp))
                }
            }
        }
    }
}

/** Talfelt (mm med én decimal; komma eller punktum). Følger [value] når den ændres udefra, fx ved træk med musen. */
@Composable
fun NumberField(label: String, value: Double, modifier: Modifier = Modifier, onChange: (Double) -> Unit) {
    fun parse(t: String) = t.trim().replace(',', '.').toDoubleOrNull()
    var text by remember { mutableStateOf(formatMm(value)) }
    var last by remember { mutableStateOf(value) }
    if (value != last) {
        last = value
        if (parse(text) != value) text = formatMm(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            parse(t)?.takeIf { it >= 0 && it <= 1000 }?.let(onChange)
        },
        label = { Text(label) },
        isError = parse(text) == null,
        singleLine = true,
        modifier = modifier,
    )
}

fun formatMm(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v)
