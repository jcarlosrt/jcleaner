package cl.jcleaner.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

object Escaner {

    val raiz: File get() = Environment.getExternalStorageDirectory()
    const val PAPELERA_DIR = ".JCLEANER_papelera"

    private val EXT_TEMPORAL = setOf("tmp", "temp", "log", "dmp", "chk", "part", "crdownload", "partial")
    private const val GRANDE = 100L * 1024 * 1024
    private const val DIA = 86_400_000L

    /** Todos los archivos del almacenamiento, sin entrar a Android/data ni Android/obb (Android no lo permite). */
    fun archivos(desde: File = raiz, incluirOcultos: Boolean = true): Sequence<File> {
        val android = File(raiz, "Android")
        return desde.walkTopDown()
            .onEnter { d ->
                d.name != PAPELERA_DIR &&
                    !(d.parentFile == android && (d.name == "data" || d.name == "obb")) &&
                    (incluirOcultos || d == desde || !d.name.startsWith("."))
            }
            .filter { it.isFile }
    }

    fun instalada(ctx: Context, paquete: String): Boolean = try {
        ctx.packageManager.getPackageInfo(paquete, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    // ------------------------------------------------------------------ basura

    suspend fun basura(progreso: (String) -> Unit): List<Categoria> = withContext(Dispatchers.IO) {
        val apks = mutableListOf<Archivo>()
        val miniaturas = mutableListOf<Archivo>()
        val temporales = mutableListOf<Archivo>()
        val grandes = mutableListOf<Archivo>()
        val viejas = mutableListOf<Archivo>()
        val capturas = mutableListOf<Archivo>()
        val limiteCaptura = System.currentTimeMillis() - 30 * DIA
        val descargas = File(raiz, Environment.DIRECTORY_DOWNLOADS).absolutePath + "/"
        val limiteViejo = System.currentTimeMillis() - 90 * DIA
        var n = 0
        for (f in archivos()) {
            n++
            if (n % 300 == 0) {
                currentCoroutineContext().ensureActive()
                progreso("Revisando… $n archivos")
            }
            val ext = f.extension.lowercase()
            val a = Archivo(f, f.length(), f.lastModified())
            val ruta = f.absolutePath
            when {
                ext == "apk" -> apks += a
                ruta.contains("/.thumbnails/") -> miniaturas += a
                ext in EXT_TEMPORAL || f.name.startsWith("~") -> temporales += a
            }
            if (a.tamano >= GRANDE) grandes += a
            if (ruta.startsWith(descargas) && a.fecha < limiteViejo && ext != "apk") viejas += a
            if (a.fecha < limiteCaptura && (a.esImagen || a.esVideo) && esCaptura(ruta)) capturas += a
        }
        progreso("Buscando carpetas vacías…")
        val vacias = carpetasVacias(raiz)
        listOf(
            Categoria("apk", "Instaladores APK", "Archivos .apk que ya no se necesitan después de instalar la app", apks, true),
            Categoria("miniaturas", "Miniaturas temporales", "Vistas previas que la galería vuelve a crear sola", miniaturas, true),
            Categoria("temporales", "Temporales y registros", "Archivos .tmp, .log y descargas a medio terminar", temporales, true),
            Categoria("vacias", "Carpetas vacías", "Carpetas que quedaron sin ningún archivo", vacias, true),
            Categoria("capturas", "Capturas de pantalla antiguas", "Capturas y grabaciones de pantalla de hace más de un mes. Revísalas antes de borrar", capturas.sortedBy { it.fecha }),
            Categoria("descargas", "Descargas antiguas", "En Descargas, sin modificar hace más de 90 días. Revísalas antes de borrar", viejas.sortedByDescending { it.tamano }),
            Categoria("grandes", "Archivos grandes", "Archivos de más de 100 MB en cualquier carpeta. Revísalos antes de borrar", grandes.sortedByDescending { it.tamano }),
        )
    }

    private fun esCaptura(ruta: String): Boolean {
        val r = ruta.lowercase()
        return r.contains("/screenshots/") || r.contains("/capturas") || r.contains("/screen recordings/") ||
            r.contains("/screenrecorder/") || r.contains("/screen_recordings/")
    }

    // ------------------------------------------------------------------ ¿qué ocupa mi espacio?

    suspend fun mapaEspacio(progreso: (String) -> Unit): MapaEspacio = withContext(Dispatchers.IO) {
        val porCarpeta = HashMap<String, Long>()
        val categorias = linkedMapOf(
            "Fotos" to 0L, "Videos" to 0L, "Audio" to 0L, "Documentos" to 0L, "APK" to 0L, "Otros" to 0L,
        )
        val base = raiz.absolutePath
        var n = 0
        var total = 0L
        for (f in archivos()) {
            n++
            if (n % 500 == 0) {
                currentCoroutineContext().ensureActive()
                progreso("Midiendo carpetas… $n archivos")
            }
            val len = f.length()
            total += len
            val ext = f.extension.lowercase()
            val cat = when {
                ext in EXT_IMAGEN -> "Fotos"
                ext in EXT_VIDEO -> "Videos"
                ext in EXT_AUDIO -> "Audio"
                ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "csv", "epub") -> "Documentos"
                ext == "apk" -> "APK"
                else -> "Otros"
            }
            categorias[cat] = (categorias[cat] ?: 0L) + len
            // suma el tamaño a cada carpeta que lo contiene, hasta la raíz
            var p = f.parentFile
            while (p != null) {
                val ruta = p.absolutePath
                porCarpeta[ruta] = (porCarpeta[ruta] ?: 0L) + len
                if (ruta == base) break
                p = p.parentFile
            }
        }
        MapaEspacio(porCarpeta, categorias, total)
    }

    private val PROTEGIDAS = setOf(
        "Android", "DCIM", "Pictures", "Download", "Documents", "Music", "Movies", "Alarms",
        "Notifications", "Ringtones", "Podcasts", "Audiobooks", "Recordings",
    )

    /** Carpetas sin ningún archivo dentro (se reporta la más alta de cada rama vacía). */
    fun carpetasVacias(raiz: File): List<Archivo> {
        val res = mutableListOf<Archivo>()
        fun vacia(d: File, nivel: Int): Boolean {
            val hijos = d.listFiles() ?: return false
            var todoVacio = true
            val hijasVacias = mutableListOf<File>()
            for (h in hijos) {
                if (h.isFile) {
                    todoVacio = false
                } else if (h.isDirectory) {
                    if (nivel == 0 && h.name == "Android") { todoVacio = false; continue }
                    if (vacia(h, nivel + 1)) hijasVacias += h else todoVacio = false
                }
            }
            if (!todoVacio || nivel == 0) {
                for (v in hijasVacias) {
                    if (!(nivel == 0 && v.name in PROTEGIDAS)) res += Archivo(v, 0, v.lastModified(), esCarpeta = true)
                }
            }
            return todoVacio
        }
        vacia(raiz, 0)
        return res
    }

    // ------------------------------------------------------------------ WhatsApp y Telegram

    private data class TipoChat(val id: String, val titulo: String, val descripcion: String)

    private val TIPOS = listOf(
        TipoChat("imagenes", "Imágenes", "Fotos de chats y grupos"),
        TipoChat("videos", "Videos", "Videos de chats y grupos"),
        TipoChat("voz", "Notas de voz", "Audios grabados en los chats"),
        TipoChat("audios", "Audios y música", "Archivos de audio compartidos"),
        TipoChat("documentos", "Documentos", "PDF, Word, Excel, APK y otros archivos"),
        TipoChat("videonotas", "Videomensajes", "Mensajes de video redondos"),
        TipoChat("gifs", "GIF animados", "GIFs recibidos"),
        TipoChat("stickers", "Stickers", "Stickers recibidos"),
        TipoChat("estados", "Estados guardados", "Estados que se descargaron al verlos"),
        TipoChat("perfil", "Fotos de perfil", "Fotos de perfil de tus contactos"),
        TipoChat("otros", "Otros", "Otros archivos"),
    )

    private fun tipoWhatsApp(carpeta: String): String {
        val n = carpeta.lowercase()
        return when {
            n.contains("video notes") -> "videonotas"
            n.contains("voice") -> "voz"
            n.contains("images") -> "imagenes"
            n.contains("video") -> "videos"
            n.contains("audio") -> "audios"
            n.contains("document") -> "documentos"
            n.contains("sticker") -> "stickers"
            n.contains("gif") -> "gifs"
            n.contains("statuses") -> "estados"
            n.contains("profile") -> "perfil"
            else -> "otros"
        }
    }

    private fun tipoPorExtension(a: Archivo): String = when {
        a.ext == "webp" && a.tamano < 200 * 1024 -> "stickers"
        a.esImagen -> "imagenes"
        a.esVideo -> "videos"
        a.ext == "ogg" || a.ext == "opus" -> "voz"
        a.esAudio -> "audios"
        else -> "documentos"
    }

    private fun categorias(porTipo: Map<String, List<Archivo>>): List<Categoria> =
        TIPOS.mapNotNull { t ->
            val lista = porTipo[t.id].orEmpty()
            if (lista.isEmpty()) null
            else Categoria(t.id, t.titulo, t.descripcion, lista.sortedByDescending { it.fecha })
        }

    suspend fun chats(ctx: Context, progreso: (String) -> Unit): List<AppChat> = withContext(Dispatchers.IO) {
        val res = mutableListOf<AppChat>()

        // WhatsApp guarda todos los chats y grupos juntos, separados solo por tipo de archivo
        val variantes = listOf(
            Triple("com.whatsapp", "WhatsApp", "WhatsApp"),
            Triple("com.whatsapp.w4b", "WhatsApp Business", "WhatsApp Business"),
        )
        for ((paquete, carpeta, nombre) in variantes) {
            progreso("Revisando $nombre…")
            val medias = listOf(
                File(raiz, "Android/media/$paquete/$carpeta/Media"),
                File(raiz, "$carpeta/Media"),
            ).filter { it.isDirectory }
            val porTipo = HashMap<String, MutableList<Archivo>>()
            var n = 0
            for (media in medias) {
                for (sub in media.listFiles().orEmpty()) {
                    if (!sub.isDirectory) continue
                    val tipo = tipoWhatsApp(sub.name)
                    for (f in sub.walkTopDown().filter { it.isFile && it.name != ".nomedia" }) {
                        n++
                        if (n % 300 == 0) {
                            currentCoroutineContext().ensureActive()
                            progreso("Revisando $nombre… $n archivos")
                        }
                        val ruta = f.absolutePath
                        val etiqueta = when {
                            ruta.contains("/Sent/") -> "Enviado"
                            ruta.contains("/Private/") -> "Privado"
                            else -> null
                        }
                        porTipo.getOrPut(tipo) { mutableListOf() } += Archivo(f, f.length(), f.lastModified(), etiqueta = etiqueta)
                    }
                }
            }
            val instalada = instalada(ctx, paquete)
            if (instalada || porTipo.isNotEmpty()) res += AppChat(nombre, paquete, instalada, categorias(porTipo))
        }

        // Telegram: solo lo que guardó en la galería y en Descargas (su carpeta interna es privada)
        progreso("Revisando Telegram…")
        val dirsTelegram = listOf(
            Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_MUSIC,
        ).map { File(File(raiz, it), "Telegram") } + File(raiz, "Telegram")
        val porTipo = HashMap<String, MutableList<Archivo>>()
        for (d in dirsTelegram.filter { it.isDirectory }) {
            for (f in d.walkTopDown().filter { it.isFile && it.name != ".nomedia" }) {
                val a = Archivo(f, f.length(), f.lastModified())
                porTipo.getOrPut(tipoPorExtension(a)) { mutableListOf() } += a
            }
        }
        val paqueteTelegram = PAQUETES_TELEGRAM.firstOrNull { instalada(ctx, it) }
        if (paqueteTelegram != null || porTipo.isNotEmpty()) {
            res += AppChat("Telegram", paqueteTelegram ?: PAQUETES_TELEGRAM[0], paqueteTelegram != null, categorias(porTipo))
        }
        res
    }

    val PAQUETES_TELEGRAM = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus", "org.thunderdog.challegram")
}
