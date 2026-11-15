package dk.binumre.desktop

import dk.binumre.model.BibProject
import java.io.File

/**
 * Projektfilen (JSON). I filen er grafikstierne relative til projektfilens mappe, så mappen kan
 * flyttes; i programmet er de absolutte.
 */
object ProjectFiles {

    fun load(file: File): BibProject = absolute(BibProject.fromJson(file.readText()), file.absoluteFile.parentFile)

    fun save(project: BibProject, file: File) {
        file.writeText(relative(project, file.absoluteFile.parentFile).toJson())
    }

    fun absolute(project: BibProject, dir: File?): BibProject = project.copy(graphics = project.graphics.map { g ->
        val f = File(g.file)
        g.copy(file = (if (f.isAbsolute || dir == null) f else File(dir, g.file)).absoluteFile.normalize().path)
    })

    /** Stier under [dir] (eller ved siden af) bliver relative med "/"; på et andet drev forbliver de absolutte. */
    fun relative(project: BibProject, dir: File?): BibProject = project.copy(graphics = project.graphics.map { g ->
        if (dir == null) return@map g
        val rel = runCatching { dir.absoluteFile.normalize().toPath().relativize(File(g.file).absoluteFile.normalize().toPath()) }.getOrNull()
        if (rel == null) g else g.copy(file = rel.joinToString("/"))
    })
}
