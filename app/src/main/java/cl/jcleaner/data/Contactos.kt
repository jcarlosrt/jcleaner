package cl.jcleaner.data

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Telefono(val dataId: Long, val rawId: Long, val numero: String, val clave: String)

data class Contacto(
    val id: Long,
    val nombre: String,
    /** rawContactId -> tipo de cuenta (Google, teléfono, WhatsApp…) */
    val crudos: Map<Long, String>,
    val cuentas: List<String>,
    val telefonos: List<Telefono>,
    val correos: List<String>,
    /** fila del nombre en una cuenta editable, para elegirlo como nombre principal */
    val nombreDataId: Long?,
) {
    val telefonosUnicos: List<String> get() = telefonos.distinctBy { it.clave }.map { it.numero }
    val vacio: Boolean get() = telefonos.isEmpty() && correos.isEmpty()
    val cantidadDatos: Int get() = telefonosUnicos.size + correos.size
}

data class GrupoContactos(val contactos: List<Contacto>, val razones: List<String>) {
    val firma: String = contactos.map { it.id }.sorted().joinToString(",")
}

/** Un mismo número guardado más de una vez dentro de un contacto. */
data class Repetido(val contacto: Contacto, val numero: String, val sobrantes: List<Telefono>)

data class AnalisisContactos(
    val total: Int,
    val grupos: List<GrupoContactos>,
    val repetidos: List<Repetido>,
    val vacios: List<Contacto>,
)

object Contactos {

    /** Cuentas que administran otras apps: se pueden unir, pero no se modifican ni borran. */
    private val SOLO_LECTURA = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "com.viber.voip", "org.thoughtcrime.securesms", "com.google.android.apps.tachyon",
        "com.skype.raider", "com.microsoft.teams", "com.facebook.orca",
    )

    var respaldoHecho = false

    fun editable(tipo: String?): Boolean = tipo == null || tipo !in SOLO_LECTURA

    private fun etiquetaCuenta(tipo: String?, nombre: String?): String = when {
        tipo.isNullOrBlank() -> "Teléfono"
        tipo == "com.google" -> if (nombre.isNullOrBlank()) "Google" else "Google · $nombre"
        tipo.contains("sim", ignoreCase = true) -> "SIM"
        tipo.contains("whatsapp") -> "WhatsApp"
        tipo.contains("telegram") -> "Telegram"
        tipo.contains("exchange", true) || tipo.contains("outlook", true) -> "Outlook"
        tipo.contains("phone", true) || tipo.contains("local", true) || tipo.startsWith("vnd.sec") -> "Teléfono"
        else -> tipo.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    /**
     * Compara por los últimos 8 dígitos: así "+56 9 1234 5678", "912345678", "09-1234-5678"
     * y el antiguo "1234 5678" se reconocen como el mismo número.
     */
    fun claveTelefono(numero: String): String? {
        val d = numero.filter { it.isDigit() }
        return if (d.length >= 7) d.takeLast(8) else null
    }

    /** "Pérez, Juan" y "juan perez" dan lo mismo. */
    fun claveNombre(nombre: String): String {
        val sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        return sinTildes.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.sorted().joinToString(" ")
    }

    private class Acumulado(val id: Long) {
        var nombre = ""
        val crudos = LinkedHashMap<Long, String>()
        val cuentas = LinkedHashSet<String>()
        val telefonos = mutableListOf<Telefono>()
        val correos = LinkedHashSet<String>()
        var nombreDataId: Long? = null
    }

    suspend fun analizar(ctx: Context): AnalisisContactos = withContext(Dispatchers.IO) {
        val mapa = LinkedHashMap<Long, Acumulado>()
        val columnas = arrayOf(
            Data.CONTACT_ID, Data.RAW_CONTACT_ID, Data._ID, Data.MIMETYPE, Data.DATA1,
            Contacts.DISPLAY_NAME, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME,
        )
        val seleccion = "${Data.MIMETYPE} IN (?,?,?)"
        val tipos = arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredName.CONTENT_ITEM_TYPE)
        ctx.contentResolver.query(Data.CONTENT_URI, columnas, seleccion, tipos, null)?.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getLong(0)
                val raw = c.getLong(1)
                val dataId = c.getLong(2)
                val mime = c.getString(3)
                val valor = c.getString(4) ?: ""
                val a = mapa.getOrPut(contactId) { Acumulado(contactId) }
                if (a.nombre.isEmpty()) a.nombre = c.getString(5) ?: ""
                val tipoCuenta = c.getString(6)
                if (raw !in a.crudos) {
                    a.crudos[raw] = tipoCuenta ?: ""
                    a.cuentas += etiquetaCuenta(tipoCuenta, c.getString(7))
                }
                when (mime) {
                    Phone.CONTENT_ITEM_TYPE -> claveTelefono(valor)?.let { a.telefonos += Telefono(dataId, raw, valor.trim(), it) }
                    Email.CONTENT_ITEM_TYPE -> if (valor.contains("@")) a.correos += valor.trim().lowercase()
                    StructuredName.CONTENT_ITEM_TYPE -> if (editable(tipoCuenta) && a.nombreDataId == null) a.nombreDataId = dataId
                }
            }
        }

        val contactos = mapa.values.map { a ->
            Contacto(
                id = a.id,
                nombre = a.nombre.ifBlank { a.telefonos.firstOrNull()?.numero ?: a.correos.firstOrNull() ?: "(sin nombre)" },
                crudos = a.crudos.toMap(),
                cuentas = a.cuentas.toList(),
                telefonos = a.telefonos.toList(),
                correos = a.correos.toList(),
                nombreDataId = a.nombreDataId,
            )
        }

        // ---- grupos de duplicados: comparten teléfono, correo o nombre
        val n = contactos.size
        val padre = IntArray(n) { it }
        fun raiz(x: Int): Int {
            var r = x
            while (padre[r] != r) { padre[r] = padre[padre[r]]; r = padre[r] }
            return r
        }
        val porClave = HashMap<String, MutableList<Int>>()
        contactos.forEachIndexed { i, c ->
            c.telefonos.map { it.clave }.toSet().forEach { porClave.getOrPut("t:$it") { mutableListOf() } += i }
            c.correos.forEach { porClave.getOrPut("e:$it") { mutableListOf() } += i }
            val cn = claveNombre(c.nombre)
            if (cn.length >= 3 && cn.any { it.isLetter() }) porClave.getOrPut("n:$cn") { mutableListOf() } += i
        }
        for (l in porClave.values) {
            if (l.size > 1) for (k in 1 until l.size) {
                val ra = raiz(l[0]); val rb = raiz(l[k])
                if (ra != rb) padre[rb] = ra
            }
        }
        val razonesPorRaiz = HashMap<Int, MutableSet<String>>()
        for ((clave, l) in porClave) {
            if (l.size < 2) continue
            val razon = when (clave[0]) {
                't' -> "Mismo teléfono"
                'e' -> "Mismo correo"
                else -> "Mismo nombre"
            }
            razonesPorRaiz.getOrPut(raiz(l[0])) { LinkedHashSet() } += razon
        }
        val grupos = (0 until n).groupBy { raiz(it) }.filter { it.value.size > 1 }.map { (r, idx) ->
            val miembros = idx.map { contactos[it] }
                .sortedWith(compareByDescending<Contacto> { it.cantidadDatos }.thenByDescending { it.nombre.length })
            val orden = listOf("Mismo teléfono", "Mismo correo", "Mismo nombre")
            GrupoContactos(miembros, razonesPorRaiz[r].orEmpty().sortedBy { orden.indexOf(it) })
        }.sortedWith(compareByDescending<GrupoContactos> { "Mismo teléfono" in it.razones }.thenByDescending { it.contactos.size })

        // ---- números repetidos dentro de una misma cuenta del contacto
        val repetidos = contactos.flatMap { c ->
            c.telefonos.groupBy { it.rawId to it.clave }.values
                .filter { it.size > 1 && editable(c.crudos[it[0].rawId]) }
                .map { g ->
                    val ordenados = g.sortedByDescending { it.numero.length }  // conserva el más completo (+56…)
                    Repetido(c, ordenados[0].numero, ordenados.drop(1))
                }
        }

        val vacios = contactos.filter { it.vacio && it.crudos.values.any { t -> editable(t) } }
        AnalisisContactos(n, grupos, repetidos, vacios)
    }

    // ------------------------------------------------------------------ acciones

    /** Copia de seguridad de todos los contactos en Descargas/JCLEANER (archivo .vcf). */
    suspend fun respaldo(ctx: Context): String = withContext(Dispatchers.IO) {
        val claves = mutableListOf<String>()
        ctx.contentResolver.query(Contacts.CONTENT_URI, arrayOf(Contacts.LOOKUP_KEY), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { claves += it }
        }
        val nombre = "contactos-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".vcf"
        val salida: OutputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val valores = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nombre)
                put(MediaStore.Downloads.MIME_TYPE, "text/x-vcard")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JCLEANER")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                ?: throw IllegalStateException("no se pudo crear el archivo de respaldo")
            ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("no se pudo escribir el respaldo")
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "JCLEANER")
            dir.mkdirs()
            File(dir, nombre).outputStream()
        }
        salida.use { out ->
            for (k in claves) {
                try {
                    ctx.contentResolver.openInputStream(Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, k))?.use { it.copyTo(out) }
                } catch (_: Exception) {
                }
            }
        }
        respaldoHecho = true
        "Descargas/JCLEANER/$nombre"
    }

    private fun aplicar(ctx: Context, ops: List<ContentProviderOperation>) {
        ops.chunked(300).forEach { ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(it)) }
    }

    private val COLUMNAS_NOMBRE = arrayOf(
        StructuredName.DISPLAY_NAME, StructuredName.GIVEN_NAME, StructuredName.FAMILY_NAME,
        StructuredName.MIDDLE_NAME, StructuredName.PREFIX, StructuredName.SUFFIX,
    )

    private fun leerNombre(ctx: Context, dataId: Long): Map<String, String?>? =
        ctx.contentResolver.query(Data.CONTENT_URI, COLUMNAS_NOMBRE, "${Data._ID}=?", arrayOf(dataId.toString()), null)?.use { c ->
            if (c.moveToFirst()) COLUMNAS_NOMBRE.mapIndexed { i, col -> col to c.getString(i) }.toMap() else null
        }

    private fun tieneNombre(ctx: Context, rawId: Long): Boolean =
        ctx.contentResolver.query(
            Data.CONTENT_URI, arrayOf(Data._ID),
            "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(rawId.toString(), StructuredName.CONTENT_ITEM_TYPE), null,
        )?.use { it.count > 0 } ?: false

    /**
     * Une los contactos del grupo en uno solo (como "Vincular" en la app Contactos).
     * No borra ningún teléfono ni correo. Si [mismoNombre], deja en todos el nombre de [principal].
     */
    suspend fun unir(ctx: Context, grupo: GrupoContactos, principal: Contacto, mismoNombre: Boolean) = withContext(Dispatchers.IO) {
        val ops = ArrayList<ContentProviderOperation>()
        val otros = grupo.contactos.filter { it.id != principal.id }
        for (a in principal.crudos.keys) {
            for (o in otros) {
                for (b in o.crudos.keys) {
                    ops += ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                        .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
                        .withValue(AggregationExceptions.RAW_CONTACT_ID1, a)
                        .withValue(AggregationExceptions.RAW_CONTACT_ID2, b)
                        .build()
                }
            }
        }
        val idNombre = principal.nombreDataId
        if (idNombre != null) {
            ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                .withSelection("${Data._ID}=?", arrayOf(idNombre.toString()))
                .withValue(Data.IS_PRIMARY, 1)
                .withValue(Data.IS_SUPER_PRIMARY, 1)
                .build()
            val partes = if (mismoNombre) leerNombre(ctx, idNombre) else null
            if (partes != null) {
                for (o in otros) {
                    for ((raw, tipo) in o.crudos) {
                        if (!editable(tipo)) continue
                        val op = if (tieneNombre(ctx, raw)) {
                            ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                                .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(raw.toString(), StructuredName.CONTENT_ITEM_TYPE))
                        } else {
                            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                .withValue(Data.RAW_CONTACT_ID, raw)
                                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        }
                        for ((col, valor) in partes) op.withValue(col, valor)
                        ops += op.build()
                    }
                }
            }
        }
        aplicar(ctx, ops)
    }

    /** Borra contactos (solo sus cuentas editables; los de WhatsApp y similares los maneja su app). */
    suspend fun borrarContactos(ctx: Context, lista: List<Contacto>) = withContext(Dispatchers.IO) {
        val ops = lista.flatMap { c ->
            c.crudos.filter { editable(it.value) }.keys.map { raw ->
                ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
                    .withSelection("${RawContacts._ID}=?", arrayOf(raw.toString()))
                    .build()
            }
        }
        aplicar(ctx, ops)
    }

    suspend fun quitarRepetidos(ctx: Context, lista: List<Repetido>) = withContext(Dispatchers.IO) {
        val ops = lista.flatMap { r ->
            r.sobrantes.map { t ->
                ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection("${Data._ID}=?", arrayOf(t.dataId.toString()))
                    .build()
            }
        }
        aplicar(ctx, ops)
    }
}
