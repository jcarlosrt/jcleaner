package cl.jcleaner.data

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

val EXT_IMAGEN = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
val EXT_VIDEO = setOf("mp4", "3gp", "mkv", "webm", "mov", "avi", "m4v")
val EXT_AUDIO = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "amr", "flac")

data class Archivo(
    val file: File,
    val tamano: Long,
    val fecha: Long,
    val esCarpeta: Boolean = false,
    val etiqueta: String? = null,
    val ancho: Int = 0,
    val alto: Int = 0,
) {
    val ruta: String get() = file.absolutePath
    val nombre: String get() = file.name
    val ext: String get() = file.extension.lowercase()
    val esImagen: Boolean get() = ext in EXT_IMAGEN
    val esVideo: Boolean get() = ext in EXT_VIDEO
    val esAudio: Boolean get() = ext in EXT_AUDIO
    val esMedia: Boolean get() = esImagen || esVideo
}

data class Categoria(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val archivos: List<Archivo>,
    val preseleccionada: Boolean = false,
) {
    val tamano: Long = archivos.sumOf { it.tamano }
    val cantidad: Int get() = archivos.size
}

data class AppChat(
    val nombre: String,
    val paquete: String,
    val instalada: Boolean,
    val categorias: List<Categoria>,
) {
    val tamano: Long = categorias.sumOf { it.tamano }
}

/** El primer archivo de la lista es el que se recomienda conservar. */
data class GrupoDuplicados(val archivos: List<Archivo>, val identicos: Boolean) {
    val conservar: Archivo get() = archivos.first()
    val recuperable: Long = archivos.drop(1).sumOf { it.tamano }
}

data class Resultado(
    val cantidad: Int,
    val bytes: Long,
    val rutas: Set<String>,
    val fallidos: Int,
    /** true si los archivos quedaron en la papelera (el espacio se libera al vaciarla) */
    val papelera: Boolean = false,
    /** bytes liberados de inmediato */
    val liberados: Long = bytes,
)

data class InfoApp(
    val paquete: String,
    val nombre: String,
    val icono: ImageBitmap?,
    val apk: Long,
    val datos: Long,
    val cache: Long,
    val sistema: Boolean,
    /** Última vez que se abrió (0 si Android no tiene registro). */
    val ultimoUso: Long = 0,
    val instaladaEl: Long = 0,
) {
    /** Android incluye el caché dentro de "datos"; acá se muestran separados. */
    val datosSinCache: Long get() = (datos - cache).coerceAtLeast(0)
}

/** Tamaño de cada carpeta del almacenamiento y de cada tipo de archivo. */
data class MapaEspacio(val porCarpeta: Map<String, Long>, val categorias: Map<String, Long>, val totalArchivos: Long)
