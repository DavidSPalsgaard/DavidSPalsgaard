package dk.binumre.desktop.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Fildialoger: systemets egen på Windows og Mac; på Linux Javas (AWT's GTK-dialog er upålidelig).
 * Sidst brugte mappe huskes i [AppState.lastDir].
 */
class FileDialogs(private val state: AppState, private val frame: Frame?) {
    private val native = System.getProperty("os.name").lowercase().let { "win" in it || "mac" in it }

    fun open(title: String, extensions: List<String>, multiple: Boolean = false): List<File> {
        val files = if (native) {
            FileDialog(frame, title, FileDialog.LOAD).run {
                isMultipleMode = multiple
                state.lastDir?.let { directory = it.path }
                // Windows filtrerer på filnavnsmønstret; på Mac bruges filteret
                file = extensions.joinToString(";") { "*.$it" }
                setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
                isVisible = true
                files.toList()
            }
        } else {
            JFileChooser(state.lastDir).run {
                dialogTitle = title
                isMultiSelectionEnabled = multiple
                fileFilter = FileNameExtensionFilter(extensions.joinToString(", ") { "*.$it" }, *extensions.toTypedArray())
                if (showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) emptyList()
                else if (multiple) selectedFiles.toList() else listOfNotNull(selectedFile)
            }
        }
        files.firstOrNull()?.parentFile?.let { state.lastDir = it }
        return files
    }

    /** Fil at gemme i; [extension] sættes på, hvis brugeren ikke selv skriver den. */
    fun save(title: String, suggested: String, extension: String): File? {
        val picked = if (native) {
            FileDialog(frame, title, FileDialog.SAVE).run {
                state.lastDir?.let { directory = it.path }
                file = suggested
                isVisible = true
                files.firstOrNull()
            }
        } else {
            JFileChooser(state.lastDir).run {
                dialogTitle = title
                fileFilter = FileNameExtensionFilter("*.$extension", extension)
                selectedFile = File(state.lastDir, suggested)
                if (showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) selectedFile else null
            }
        } ?: return null
        val file = if (picked.name.lowercase().endsWith(".$extension")) picked else File(picked.parentFile, picked.name + ".$extension")
        if (!native && file.exists()) {
            // Systemets egen dialog spørger selv; Javas gør ikke
            val answer = JOptionPane.showConfirmDialog(frame, state.strings.format("confirm.overwrite", file.name), title, JOptionPane.OK_CANCEL_OPTION)
            if (answer != JOptionPane.OK_OPTION) return null
        }
        file.absoluteFile.parentFile?.let { state.lastDir = it }
        return file
    }
}
