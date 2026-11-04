package cl.jcleaner.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guarda los resultados de cada análisis mientras la app está abierta y corre las tareas largas
 * (siguen funcionando aunque cambies de pestaña).
 */
object Motor {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val principal = Handler(Looper.getMainLooper())
    private val trabajos = HashMap<String, Job>()
    private val generacion = HashMap<String, Int>()

    var basura by mutableStateOf<List<Categoria>?>(null)
    var progBasura by mutableStateOf<String?>(null)

    var chats by mutableStateOf<List<AppChat>?>(null)
    var progChats by mutableStateOf<String?>(null)

    var gruposFotos by mutableStateOf<List<GrupoDuplicados>?>(null)
    var progFotos by mutableStateOf<String?>(null)

    var gruposArchivos by mutableStateOf<List<GrupoDuplicados>?>(null)
    var progArchivos by mutableStateOf<String?>(null)

    var apps by mutableStateOf<List<InfoApp>?>(null)
    var progApps by mutableStateOf<String?>(null)

    var liberadoTotal by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)

    private fun <T> tarea(
        nombre: String,
        inicio: String,
        ponerProg: (String?) -> Unit,
        trabajo: suspend (aviso: (String) -> Unit) -> T,
        alTerminar: (T) -> Unit,
    ) {
        trabajos[nombre]?.cancel()
        val gen = (generacion[nombre] ?: 0) + 1
        generacion[nombre] = gen
        ponerProg(inicio)
        val aviso: (String) -> Unit = { m -> principal.post { if (generacion[nombre] == gen) ponerProg(m) } }
        trabajos[nombre] = scope.launch {
            try {
                alTerminar(trabajo(aviso))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                if (generacion[nombre] == gen) {
                    generacion[nombre] = gen + 1  // ignora avisos atrasados
                    ponerProg(null)
                }
            }
        }
    }

    fun cancelar(nombre: String) {
        trabajos[nombre]?.cancel()
    }

    fun analizarBasura() =
        tarea("basura", "Buscando archivos basura…", { progBasura = it }, { av -> Escaner.basura(av) }) { basura = it }

    fun analizarChats(ctx: Context) =
        tarea("chats", "Revisando chats…", { progChats = it }, { av -> Escaner.chats(ctx, av) }) { chats = it }

    fun buscarFotos(ctx: Context, umbral: Int) =
        tarea("fotos", "Buscando fotos…", { progFotos = it }, { av -> Duplicados.fotos(ctx, umbral, av) }) { gruposFotos = it }

    fun buscarArchivos() =
        tarea("archivos", "Buscando archivos…", { progArchivos = it }, { av -> Duplicados.archivos(av) }) { gruposArchivos = it }

    fun cargarApps(ctx: Context, silencioso: Boolean = false) {
        if (silencioso) {
            scope.launch {
                try {
                    apps = CacheApps.listar(ctx)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        } else {
            tarea("apps", "Midiendo el caché de tus apps…", { progApps = it }, { _ -> CacheApps.listar(ctx) }) { apps = it }
        }
    }

    // ------------------------------------------------------------------ fotos borrosas y espacio

    var borrosas by mutableStateOf<List<Archivo>?>(null)
    var progBorrosas by mutableStateOf<String?>(null)

    var espacio by mutableStateOf<MapaEspacio?>(null)
    var progEspacio by mutableStateOf<String?>(null)

    fun buscarBorrosas(ctx: Context) =
        tarea("borrosas", "Buscando fotos borrosas…", { progBorrosas = it }, { av -> Duplicados.borrosas(ctx, av) }) { borrosas = it }

    fun analizarEspacio() =
        tarea("espacio", "Midiendo carpetas…", { progEspacio = it }, { av -> Escaner.mapaEspacio(av) }) { espacio = it }

    // ------------------------------------------------------------------ papelera

    var enPapelera by mutableLongStateOf(0L)
    var cantidadPapelera by mutableIntStateOf(0)

    fun refrescarPapelera() {
        scope.launch {
            val l = withContext(Dispatchers.IO) { Papelera.listar() }
            enPapelera = l.sumOf { it.tamano }
            cantidadPapelera = l.size
        }
    }

    /** Al abrir la app: elimina lo que lleva más de 7 días en la papelera. */
    fun alIniciar(ctx: Context) {
        scope.launch {
            val b = withContext(Dispatchers.IO) {
                try { Papelera.purgarVencidas(ctx) } catch (e: Exception) { 0L }
            }
            liberadoTotal += b
            refrescarPapelera()
        }
    }

    // ------------------------------------------------------------------ borrar

    /**
     * Borra (o mueve a la papelera, si [definitivo] es false) los archivos.
     * Las carpetas vacías se borran siempre de inmediato.
     */
    suspend fun borrar(ctx: Context, archivos: Collection<Archivo>, definitivo: Boolean = false): Resultado {
        val r = withContext(Dispatchers.IO) {
            var n = 0
            var bytes = 0L
            val borrados = HashSet<String>()
            val escanear = ArrayList<String>()
            val aPapelera = if (definitivo) emptyList() else archivos.filter { !it.esCarpeta }
            if (aPapelera.isNotEmpty()) {
                val movidos = Papelera.mover(aPapelera)
                for (a in aPapelera) if (a.ruta in movidos) { n++; bytes += a.tamano; borrados += a.ruta; escanear += a.ruta }
            }
            val directos = if (definitivo) archivos else archivos.filter { it.esCarpeta }
            var bytesDirectos = 0L
            var nDirectos = 0
            for (a in directos) {
                val f = a.file
                val ok = try {
                    if (f.isDirectory) {
                        // solo carpetas que siguen vacías
                        f.walkTopDown().none { it.isFile } && f.deleteRecursively()
                    } else {
                        f.delete() || !f.exists()
                    }
                } catch (e: Exception) {
                    false
                }
                if (ok) {
                    n++; nDirectos++
                    bytes += a.tamano; bytesDirectos += a.tamano
                    borrados += a.ruta
                    if (!a.esCarpeta) escanear += a.ruta
                }
            }
            if (nDirectos > 0) Historial.registrar(ctx, bytesDirectos, nDirectos, "Borrado definitivo")
            // avisa a la galería que esos archivos ya no están
            if (escanear.isNotEmpty()) {
                escanear.chunked(500).forEach { MediaScannerConnection.scanFile(ctx, it.toTypedArray(), null, null) }
            }
            Resultado(n, bytes, borrados, archivos.size - n, papelera = !definitivo && aPapelera.isNotEmpty(), liberados = bytesDirectos)
        }
        liberadoTotal += r.liberados
        quitar(r.rutas)
        // descuenta lo borrado del mapa de espacio, sin volver a medir todo
        espacio?.let { m ->
            val nuevo = HashMap(m.porCarpeta)
            val base = Escaner.raiz.absolutePath
            for (a in archivos) {
                if (a.ruta !in r.rutas || a.esCarpeta) continue
                var p = a.file.parentFile
                while (p != null) {
                    val k = p.absolutePath
                    nuevo[k] = ((nuevo[k] ?: 0L) - a.tamano).coerceAtLeast(0L)
                    if (k == base) break
                    p = p.parentFile
                }
            }
            espacio = m.copy(porCarpeta = nuevo, totalArchivos = (m.totalArchivos - r.bytes).coerceAtLeast(0L))
        }
        refrescarPapelera()
        return r
    }

    private fun quitar(rutas: Set<String>) {
        if (rutas.isEmpty()) return
        fun limpiar(c: Categoria) = c.copy(archivos = c.archivos.filterNot { it.ruta in rutas })
        fun limpiarGrupos(l: List<GrupoDuplicados>?) =
            l?.map { g -> g.copy(archivos = g.archivos.filterNot { it.ruta in rutas }) }?.filter { it.archivos.size > 1 }
        basura = basura?.map(::limpiar)
        chats = chats?.map { app -> app.copy(categorias = app.categorias.map(::limpiar).filter { it.cantidad > 0 }) }
        gruposFotos = limpiarGrupos(gruposFotos)
        gruposArchivos = limpiarGrupos(gruposArchivos)
        borrosas = borrosas?.filterNot { it.ruta in rutas }
    }
}
