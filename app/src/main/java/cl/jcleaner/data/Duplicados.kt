package cl.jcleaner.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object Duplicados {

    private const val PARCIAL = 64 * 1024L

    fun md5(f: File, limite: Long = Long.MAX_VALUE): String? = try {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(64 * 1024)
        var leido = 0L
        FileInputStream(f).use { inp ->
            while (leido < limite) {
                val n = inp.read(buf, 0, minOf(buf.size.toLong(), limite - leido).toInt())
                if (n <= 0) break
                md.update(buf, 0, n)
                leido += n
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ fotos

    /**
     * Agrupa fotos idénticas (mismo contenido) y parecidas (misma imagen en otro tamaño, más comprimida, etc.).
     * [umbral] = diferencia máxima entre huellas visuales (0 = casi idénticas, 6 = muy parecidas, 10 = parecidas).
     */
    suspend fun fotos(ctx: Context, umbral: Int, progreso: (String) -> Unit): List<GrupoDuplicados> =
        withContext(Dispatchers.IO) {
            progreso("Buscando fotos…")
            val ids = mutableListOf<Long>()
            val fotos = mutableListOf<Archivo>()
            @Suppress("DEPRECATION")
            val columnas = arrayOf(
                MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.DATE_MODIFIED,
            )
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columnas, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val ruta = c.getString(1) ?: continue
                    val f = File(ruta)
                    val tam = c.getLong(2)
                    if (tam <= 0 || !f.exists()) continue
                    ids += c.getLong(0)
                    fotos += Archivo(f, tam, c.getLong(5) * 1000, ancho = c.getInt(3), alto = c.getInt(4))
                }
            }
            val n = fotos.size
            val padre = IntArray(n) { it }
            fun raiz(x: Int): Int {
                var r = x
                while (padre[r] != r) { padre[r] = padre[padre[r]]; r = padre[r] }
                return r
            }
            fun unir(a: Int, b: Int) {
                val ra = raiz(a); val rb = raiz(b)
                if (ra != rb) padre[rb] = ra
            }

            // 1) idénticas: mismo tamaño y mismo contenido
            progreso("Comparando $n fotos…")
            val md5s = arrayOfNulls<String>(n)
            val mismosTamanos = (0 until n).groupBy { fotos[it].tamano }.values.filter { it.size > 1 }
            for (grupo in mismosTamanos) {
                currentCoroutineContext().ensureActive()
                for (i in grupo) md5s[i] = md5(fotos[i].file)
                for ((h, g) in grupo.groupBy { md5s[it] }) {
                    if (h != null && g.size > 1) for (k in 1 until g.size) unir(g[0], g[k])
                }
            }

            // 2) parecidas: huella visual (dHash de 64 bits)
            val huellas = LongArray(n)
            val validas = BooleanArray(n)
            val cache = CacheHuellas(ctx)
            for (i in 0 until n) {
                if (i % 20 == 0) {
                    currentCoroutineContext().ensureActive()
                    progreso("Analizando fotos ${i + 1} de $n")
                }
                val a = fotos[i]
                val h = cache.obtener(a) ?: dhash(ctx, ids[i], a)?.also { cache.poner(a, it) }
                // las imágenes casi lisas (fondos de un color) se parecen todas: no se comparan
                if (h != null && java.lang.Long.bitCount(h) in 3..61) { huellas[i] = h; validas[i] = true }
            }
            cache.guardar()
            progreso("Agrupando fotos parecidas…")
            for (i in 0 until n) {
                if (!validas[i]) continue
                if (i % 200 == 0) currentCoroutineContext().ensureActive()
                val hi = huellas[i]
                for (j in i + 1 until n) {
                    if (validas[j] && java.lang.Long.bitCount(hi xor huellas[j]) <= umbral) unir(i, j)
                }
            }

            (0 until n).groupBy { raiz(it) }.values.filter { it.size > 1 }.map { idx ->
                val lista = idx.map { fotos[it] }.sortedWith(
                    compareByDescending<Archivo> { it.ancho.toLong() * it.alto }
                        .thenByDescending { it.tamano }
                        .thenBy { it.fecha }
                )
                val hs = idx.map { md5s[it] }
                GrupoDuplicados(lista, hs.all { it != null && it == hs[0] })
            }.sortedByDescending { it.recuperable }
        }

    private fun dhash(ctx: Context, id: Long, a: Archivo): Long? = try {
        val original: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            ctx.contentResolver.loadThumbnail(uri, Size(128, 128), null)
        } else {
            decodificarChica(a.file)
        }
        original?.let { bmp ->
            val normal = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
            val e = Bitmap.createScaledBitmap(normal, 9, 8, true)
            var h = 0L
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    h = h shl 1
                    if (luz(e.getPixel(x, y)) > luz(e.getPixel(x + 1, y))) h = h or 1L
                }
            }
            h
        }
    } catch (e: Exception) {
        null
    }

    private fun luz(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    private fun decodificarChica(f: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= 128 && o.outHeight / (s * 2) >= 128) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** Guarda las huellas ya calculadas para que la próxima búsqueda sea mucho más rápida. */
    private class CacheHuellas(ctx: Context) {
        private val archivo = File(ctx.filesDir, "huellas.txt")
        private val mapa = HashMap<String, Long>()

        init {
            try {
                archivo.forEachLine { l ->
                    val i = l.lastIndexOf('\t')
                    if (i > 0) l.substring(i + 1).toLongOrNull()?.let { mapa[l.substring(0, i)] = it }
                }
            } catch (_: Exception) {
            }
        }

        private fun clave(a: Archivo) = "${a.ruta}|${a.tamano}|${a.fecha}"
        fun obtener(a: Archivo): Long? = mapa[clave(a)]
        fun poner(a: Archivo, h: Long) { mapa[clave(a)] = h }
        fun guardar() {
            try {
                archivo.bufferedWriter().use { w -> for ((k, v) in mapa) { w.write(k); w.write("\t"); w.write(v.toString()); w.newLine() } }
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------ cualquier archivo

    suspend fun archivos(progreso: (String) -> Unit): List<GrupoDuplicados> = withContext(Dispatchers.IO) {
        val porTamano = HashMap<Long, MutableList<Archivo>>()
        var n = 0
        for (f in Escaner.archivos(incluirOcultos = false)) {
            n++
            if (n % 300 == 0) {
                currentCoroutineContext().ensureActive()
                progreso("Revisando… $n archivos")
            }
            val len = f.length()
            if (len >= 1024 && f.name != ".nomedia") {
                porTamano.getOrPut(len) { mutableListOf() } += Archivo(f, len, f.lastModified())
            }
        }
        val candidatos = porTamano.values.filter { it.size > 1 }
        val total = candidatos.sumOf { it.size }
        var hechos = 0
        val res = mutableListOf<GrupoDuplicados>()
        for (g in candidatos) {
            currentCoroutineContext().ensureActive()
            for ((parcial, g2) in g.groupBy { md5(it.file, PARCIAL) }) {
                if (parcial == null || g2.size < 2) continue
                val completos: Map<String?, List<Archivo>> =
                    if (g2[0].tamano <= PARCIAL) mapOf(parcial to g2) else g2.groupBy { md5(it.file) }
                for ((h, g3) in completos) {
                    if (h != null && g3.size > 1) {
                        res += GrupoDuplicados(g3.sortedWith(compareBy<Archivo> { it.fecha }.thenBy { it.ruta.length }), true)
                    }
                }
            }
            hechos += g.size
            progreso("Comparando contenido $hechos de $total")
        }
        res.sortedByDescending { it.recuperable }
    }
}
