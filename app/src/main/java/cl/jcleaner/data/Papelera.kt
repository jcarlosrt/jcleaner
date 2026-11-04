package cl.jcleaner.data

import android.content.Context
import java.io.File

data class EnPapelera(val id: String, val original: String, val fecha: Long, val tamano: Long) {
    val file: File get() = File(Papelera.dir, id)
    val nombre: String get() = File(original).name
    val diasRestantes: Int
        get() = (Papelera.DIAS - ((System.currentTimeMillis() - fecha) / 86_400_000L).toInt()).coerceAtLeast(0)

    /** Para mostrar la miniatura con los mismos componentes que el resto de la app. */
    fun comoArchivo(): Archivo = Archivo(file, tamano, fecha)
}

/**
 * Papelera propia: en vez de borrar al tiro, los archivos se mueven a una carpeta oculta
 * y se eliminan de verdad después de [DIAS] días (o cuando la vacías).
 */
object Papelera {
    const val DIAS = 7
    val dir: File get() = File(Escaner.raiz, ".JCLEANER_papelera")
    private val indice: File get() = File(dir, ".indice.tsv")
    private val candado = Any()

    fun listar(): List<EnPapelera> = synchronized(candado) { leer() }

    private fun leer(): List<EnPapelera> = try {
        if (!indice.exists()) emptyList()
        else indice.readLines().mapNotNull { l ->
            val p = l.split('\t')
            if (p.size >= 4) EnPapelera(p[0], p[3], p[1].toLongOrNull() ?: 0, p[2].toLongOrNull() ?: 0) else null
        }.filter { it.file.exists() }
    } catch (e: Exception) {
        emptyList()
    }

    private fun escribir(lista: List<EnPapelera>) {
        dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
        indice.writeText(lista.joinToString("") { "${it.id}\t${it.fecha}\t${it.tamano}\t${it.original}\n" })
    }

    /** Mueve los archivos a la papelera. Devuelve las rutas originales que se movieron. */
    fun mover(archivos: List<Archivo>): Set<String> = synchronized(candado) {
        dir.mkdirs()
        val actuales = leer().toMutableList()
        val movidos = HashSet<String>()
        var n = 0
        for (a in archivos) {
            if (a.esCarpeta || !a.file.isFile) continue
            n++
            val id = "${System.currentTimeMillis()}_${n}_${a.nombre}".replace('\t', ' ').replace('\n', ' ').take(180)
            val destino = File(dir, id)
            val ok = a.file.renameTo(destino) || try {
                a.file.copyTo(destino, overwrite = true)
                a.file.delete()
            } catch (e: Exception) {
                destino.delete()
                false
            }
            if (ok) {
                actuales += EnPapelera(id, a.ruta.replace('\t', ' ').replace('\n', ' '), System.currentTimeMillis(), a.tamano)
                movidos += a.ruta
            }
        }
        escribir(actuales)
        movidos
    }

    /** Devuelve los archivos a su carpeta original. Devuelve las rutas restauradas. */
    fun restaurar(items: List<EnPapelera>): List<String> = synchronized(candado) {
        val ids = items.map { it.id }.toSet()
        val restaurados = mutableListOf<String>()
        val quedan = leer().filter { e ->
            if (e.id !in ids) return@filter true
            var destino = File(e.original)
            destino.parentFile?.mkdirs()
            if (destino.exists()) {
                destino = File(destino.parentFile, destino.nameWithoutExtension + " (restaurado)" +
                    if (destino.extension.isNotEmpty()) "." + destino.extension else "")
            }
            val ok = e.file.renameTo(destino) || try {
                e.file.copyTo(destino, overwrite = false)
                e.file.delete()
            } catch (ex: Exception) {
                false
            }
            if (ok) restaurados += destino.absolutePath
            !ok
        }
        escribir(quedan)
        restaurados
    }

    /** Borra definitivamente. Devuelve los bytes liberados. */
    fun eliminar(items: List<EnPapelera>): Long = synchronized(candado) {
        val ids = items.map { it.id }.toSet()
        var bytes = 0L
        val quedan = leer().filter { e ->
            if (e.id !in ids) return@filter true
            val ok = e.file.delete() || !e.file.exists()
            if (ok) bytes += e.tamano
            !ok
        }
        escribir(quedan)
        bytes
    }

    /** Elimina lo que lleva más de [DIAS] días en la papelera. */
    fun purgarVencidas(ctx: Context): Long {
        val vencidos = listar().filter { it.diasRestantes <= 0 }
        if (vencidos.isEmpty()) return 0
        val bytes = eliminar(vencidos)
        Historial.registrar(ctx, bytes, vencidos.size, "Papelera vaciada automáticamente")
        return bytes
    }
}

data class Limpieza(val fecha: Long, val bytes: Long, val cantidad: Int, val tipo: String)

/** Registro de lo que se ha liberado de verdad (borrado definitivo o papelera vaciada). */
object Historial {
    private fun archivo(ctx: Context) = File(ctx.filesDir, "historial.tsv")

    fun registrar(ctx: Context, bytes: Long, cantidad: Int, tipo: String) {
        if (bytes <= 0 && cantidad <= 0) return
        try {
            archivo(ctx).appendText("${System.currentTimeMillis()}\t$bytes\t$cantidad\t${tipo.replace('\t', ' ')}\n")
        } catch (_: Exception) {
        }
    }

    fun listar(ctx: Context): List<Limpieza> = try {
        archivo(ctx).takeIf { it.exists() }?.readLines()?.mapNotNull { l ->
            val p = l.split('\t')
            if (p.size >= 4) Limpieza(p[0].toLongOrNull() ?: 0, p[1].toLongOrNull() ?: 0, p[2].toIntOrNull() ?: 0, p[3]) else null
        }?.reversed() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
