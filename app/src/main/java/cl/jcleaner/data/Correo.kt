package cl.jcleaner.data

import android.content.Context
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPMessage
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.search.ComparisonTerm
import javax.mail.search.SizeTerm

// ------------------------------------------------------------------ cuentas

enum class TipoAcceso { CONTRASENA, MICROSOFT }

data class Proveedor(val id: String, val nombre: String, val servidor: String, val puerto: Int, val acceso: TipoAcceso, val ayuda: String)

val PROVEEDORES = listOf(
    Proveedor(
        "gmail", "Gmail", "imap.gmail.com", 993, TipoAcceso.CONTRASENA,
        "Gmail no acepta tu contraseña normal. Activa la verificación en dos pasos en tu cuenta de Google y crea una " +
            "\"contraseña de aplicación\" en myaccount.google.com/apppasswords. Son 16 letras: pégalas aquí.",
    ),
    Proveedor(
        "outlook", "Outlook / Hotmail", "outlook.office365.com", 993, TipoAcceso.MICROSOFT,
        "Microsoft ya no acepta contraseñas en apps externas: se entra con la página oficial de Microsoft.",
    ),
    Proveedor(
        "yahoo", "Yahoo", "imap.mail.yahoo.com", 993, TipoAcceso.CONTRASENA,
        "Crea una \"contraseña de aplicación\" en Yahoo: Información de la cuenta → Seguridad de la cuenta → Generar contraseña de aplicación.",
    ),
    Proveedor(
        "icloud", "iCloud", "imap.mail.me.com", 993, TipoAcceso.CONTRASENA,
        "Crea una \"contraseña específica para apps\" en account.apple.com → Inicio de sesión y seguridad. El usuario es tu correo de iCloud.",
    ),
    Proveedor(
        "otro", "Otro correo", "", 993, TipoAcceso.CONTRASENA,
        "Escribe el servidor IMAP de tu correo (te lo indica tu proveedor; suele ser imap.tudominio.com, puerto 993). " +
            "Si tu proveedor ofrece contraseñas de aplicación, usa una.",
    ),
)

data class CuentaCorreo(val id: String, val correo: String, val proveedor: String, val servidor: String, val puerto: Int) {
    val prov: Proveedor get() = PROVEEDORES.find { it.id == proveedor } ?: PROVEEDORES.last()
    val claveSecreto: String get() = "correo_$id"
}

object Cuentas {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("jcleaner_correo", Context.MODE_PRIVATE)

    fun listar(ctx: Context): List<CuentaCorreo> = try {
        val arr = JSONArray(prefs(ctx).getString("cuentas", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CuentaCorreo(o.getString("id"), o.getString("correo"), o.getString("proveedor"), o.getString("servidor"), o.getInt("puerto"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun escribir(ctx: Context, l: List<CuentaCorreo>) {
        val arr = JSONArray()
        l.forEach {
            arr.put(JSONObject().put("id", it.id).put("correo", it.correo).put("proveedor", it.proveedor).put("servidor", it.servidor).put("puerto", it.puerto))
        }
        prefs(ctx).edit().putString("cuentas", arr.toString()).apply()
    }

    /** [secreto] es la contraseña de aplicación, o la sesión de Microsoft en JSON. */
    fun agregar(ctx: Context, c: CuentaCorreo, secreto: String) {
        Boveda.guardar(ctx, c.claveSecreto, secreto)
        escribir(ctx, listar(ctx).filter { it.correo != c.correo } + c)
    }

    fun quitar(ctx: Context, c: CuentaCorreo) {
        Boveda.borrar(ctx, c.claveSecreto)
        escribir(ctx, listar(ctx).filter { it.id != c.id })
    }

    fun desuscritos(ctx: Context, c: CuentaCorreo): Set<String> =
        prefs(ctx).getStringSet("desuscritos_${c.id}", emptySet()).orEmpty().toSet()

    fun marcarDesuscrito(ctx: Context, c: CuentaCorreo, remitente: String) {
        prefs(ctx).edit().putStringSet("desuscritos_${c.id}", desuscritos(ctx, c) + remitente).apply()
    }
}

// ------------------------------------------------------------------ resultados

/** Un correo, identificado por la carpeta y su UID (no cambia mientras el correo exista). */
data class Mensaje(
    val carpeta: String,
    val uid: Long,
    val remitente: String,
    val nombreRemitente: String,
    val asunto: String,
    val fecha: Long,
    val tamano: Long,
)

data class CarpetaEspecial(val nombre: String, val cantidad: Int, val tamano: Long)

data class Suscripcion(
    val remitente: String,
    val nombre: String,
    val mensajes: List<Mensaje>,
    /** enlace https para desuscribirse con un clic, sin abrir nada (RFC 8058) */
    val unClic: String?,
    val web: String?,
    val mailto: String?,
) {
    val tamano: Long get() = mensajes.sumOf { it.tamano }
    val ultimo: Long get() = mensajes.maxOf { it.fecha }
}

data class GrupoCorreos(val mensajes: List<Mensaje>) {
    val conservar: Mensaje get() = mensajes.first()
    val sobrantes: List<Mensaje> get() = mensajes.drop(1)
}

data class AnalisisCorreo(
    val cuenta: CuentaCorreo,
    val bandeja: Int,
    val revisados: Int,
    val spam: CarpetaEspecial?,
    val papelera: CarpetaEspecial?,
    val duplicados: List<GrupoCorreos>,
    val suscripciones: List<Suscripcion>,
    val pesados: List<Mensaje>,
)

class ErrorCorreo(mensaje: String) : Exception(mensaje)

// ------------------------------------------------------------------ IMAP

object Correo {

    /** Cuántos correos recientes de la bandeja de entrada se revisan en detalle. */
    const val LIMITE = 4000
    private const val PESADO = 5L * 1024 * 1024

    private val NOMBRES_SPAM = setOf("spam", "junk", "junk email", "junk e-mail", "correo no deseado", "bulk mail", "bulk", "no deseado", "[gmail]/spam")
    private val NOMBRES_PAPELERA = setOf("trash", "papelera", "deleted items", "deleted messages", "elementos eliminados", "deleted", "[gmail]/trash", "[gmail]/papelera")

    private suspend fun conectar(ctx: Context, c: CuentaCorreo): IMAPStore {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "60000")
            put("mail.imaps.partialfetch", "false")
            put("mail.imaps.fetchsize", "1048576")
        }
        val clave: String = when (c.prov.acceso) {
            TipoAcceso.CONTRASENA -> Boveda.leer(ctx, c.claveSecreto) ?: throw ErrorCorreo("No encontré la contraseña guardada. Quita la cuenta y agrégala de nuevo.")
            TipoAcceso.MICROSOFT -> {
                props["mail.imaps.auth.mechanisms"] = "XOAUTH2"
                props["mail.imaps.auth.login.disable"] = "true"
                props["mail.imaps.auth.plain.disable"] = "true"
                MicrosoftAuth.tokenVigente(ctx, c)
            }
        }
        val store = Session.getInstance(props).getStore("imaps") as IMAPStore
        try {
            store.connect(c.servidor, c.puerto, c.correo, clave)
        } catch (e: javax.mail.AuthenticationFailedException) {
            throw ErrorCorreo(
                if (c.prov.acceso == TipoAcceso.MICROSOFT) "Microsoft rechazó el acceso. Quita la cuenta y vuelve a iniciar sesión."
                else "El servidor rechazó el correo o la contraseña. Recuerda usar una contraseña de aplicación, no tu contraseña normal."
            )
        } catch (e: javax.mail.MessagingException) {
            throw ErrorCorreo("No pude conectarme a ${c.servidor}. Revisa tu conexión a internet y el servidor. (${e.message})")
        }
        return store
    }

    /** Comprueba que la cuenta funcione antes de guardarla. */
    suspend fun probar(ctx: Context, c: CuentaCorreo) = withContext(Dispatchers.IO) {
        conectar(ctx, c).close()
    }

    private fun todas(store: IMAPStore): List<IMAPFolder> =
        store.defaultFolder.list("*").filterIsInstance<IMAPFolder>().filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }

    private fun especial(carpetas: List<IMAPFolder>, atributo: String, nombres: Set<String>): IMAPFolder? =
        carpetas.firstOrNull { f -> f.attributes.any { it.equals(atributo, ignoreCase = true) } }
            ?: carpetas.firstOrNull { it.fullName.lowercase() in nombres || it.name.lowercase() in nombres }

    private fun medir(f: IMAPFolder?): CarpetaEspecial? {
        if (f == null) return null
        f.open(Folder.READ_ONLY)
        try {
            val msgs = f.messages
            if (msgs.isNotEmpty()) f.fetch(msgs, FetchProfile().apply { add(FetchProfile.Item.SIZE) })
            return CarpetaEspecial(f.fullName, msgs.size, msgs.sumOf { it.size.coerceAtLeast(0).toLong() })
        } finally {
            f.close(false)
        }
    }

    private fun aMensaje(f: IMAPFolder, m: Message): Mensaje {
        val de = (m.from?.firstOrNull() as? InternetAddress)
        return Mensaje(
            carpeta = f.fullName,
            uid = f.getUID(m),
            remitente = de?.address?.lowercase() ?: "",
            nombreRemitente = de?.personal ?: de?.address ?: "(sin remitente)",
            asunto = m.subject ?: "(sin asunto)",
            fecha = (m.sentDate ?: m.receivedDate)?.time ?: 0L,
            tamano = m.size.coerceAtLeast(0).toLong(),
        )
    }

    private val ENLACE = Regex("<([^>]+)>")

    suspend fun analizar(ctx: Context, c: CuentaCorreo, progreso: (String) -> Unit): AnalisisCorreo = withContext(Dispatchers.IO) {
        progreso("Conectando con ${c.prov.nombre}…")
        val store = conectar(ctx, c)
        try {
            val carpetas = todas(store)
            progreso("Revisando spam y papelera…")
            val spam = medir(especial(carpetas, "\\Junk", NOMBRES_SPAM))
            val papelera = medir(especial(carpetas, "\\Trash", NOMBRES_PAPELERA))

            val inbox = store.getFolder("INBOX") as IMAPFolder
            inbox.open(Folder.READ_ONLY)
            val total = inbox.messageCount
            val desde = maxOf(1, total - LIMITE + 1)
            val msgs = if (total > 0) inbox.getMessages(desde, total) else emptyArray()
            val perfil = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.SIZE)
                add(UIDFolder.FetchProfileItem.UID)
                add("List-Unsubscribe")
                add("List-Unsubscribe-Post")
            }
            // se descargan solo los encabezados, por partes, nunca el contenido de los correos
            msgs.toList().chunked(250).forEachIndexed { k, parte ->
                currentCoroutineContext().ensureActive()
                progreso("Leyendo encabezados ${minOf((k + 1) * 250, msgs.size)} de ${msgs.size}…")
                inbox.fetch(parte.toTypedArray(), perfil)
            }

            progreso("Buscando repetidos y suscripciones…")
            val lista = msgs.map { it to aMensaje(inbox, it) }

            // duplicados: mismo Message-ID, o mismo remitente + asunto + fecha + tamaño
            val duplicados = lista.groupBy { (m, x) ->
                (m as? IMAPMessage)?.messageID?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "${x.remitente}|${x.asunto}|${x.fecha}|${x.tamano}"
            }.values.filter { it.size > 1 }.map { g -> GrupoCorreos(g.map { it.second }.sortedBy { it.uid }) }
                .sortedByDescending { it.sobrantes.size }

            // suscripciones: correos con encabezado List-Unsubscribe, agrupados por remitente
            val suscripciones = lista.mapNotNull { (m, x) ->
                val cab = m.getHeader("List-Unsubscribe")?.joinToString(",") ?: return@mapNotNull null
                val post = m.getHeader("List-Unsubscribe-Post")?.joinToString(",") ?: ""
                Triple(x, cab, post)
            }.groupBy { it.first.remitente }.filterKeys { it.isNotEmpty() }.map { (rem, g) ->
                val ultimo = g.maxBy { it.first.fecha }
                val enlaces = ENLACE.findAll(ultimo.second).map { it.groupValues[1].trim() }.toList()
                val https = enlaces.firstOrNull { it.startsWith("https://", true) }
                Suscripcion(
                    remitente = rem,
                    nombre = ultimo.first.nombreRemitente,
                    mensajes = g.map { it.first }.sortedByDescending { it.fecha },
                    unClic = https?.takeIf { ultimo.third.contains("One-Click", ignoreCase = true) },
                    web = https ?: enlaces.firstOrNull { it.startsWith("http://", true) },
                    mailto = enlaces.firstOrNull { it.startsWith("mailto:", true) },
                )
            }.sortedByDescending { it.mensajes.size }
            inbox.close(false)

            // pesados: en "Todos" si existe (Gmail), si no en la bandeja de entrada
            progreso("Buscando correos pesados…")
            val donde = especial(carpetas, "\\All", emptySet()) ?: inbox
            donde.open(Folder.READ_ONLY)
            val grandes = donde.search(SizeTerm(ComparisonTerm.GT, PESADO.toInt()))
            if (grandes.isNotEmpty()) {
                donde.fetch(grandes, FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.SIZE); add(UIDFolder.FetchProfileItem.UID)
                })
            }
            val pesados = grandes.map { aMensaje(donde, it) }.sortedByDescending { it.tamano }
            donde.close(false)

            AnalisisCorreo(c, total, msgs.size, spam, papelera, duplicados, suscripciones, pesados)
        } finally {
            try { store.close() } catch (_: Exception) { }
        }
    }

    // ------------------------------------------------------------------ acciones

    /**
     * Mueve los correos a la papelera del servidor (se pueden recuperar desde tu correo).
     * Devuelve cuántos se movieron.
     */
    suspend fun aPapelera(ctx: Context, c: CuentaCorreo, mensajes: List<Mensaje>, progreso: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        if (mensajes.isEmpty()) return@withContext 0
        val store = conectar(ctx, c)
        try {
            val carpetas = todas(store)
            val papelera = especial(carpetas, "\\Trash", NOMBRES_PAPELERA)
                ?: throw ErrorCorreo("No encontré la carpeta Papelera de tu correo.")
            val puedeMover = store.hasCapability("MOVE")
            var n = 0
            for ((nombre, lista) in mensajes.groupBy { it.carpeta }) {
                if (nombre == papelera.fullName) continue
                val f = store.getFolder(nombre) as IMAPFolder
                f.open(Folder.READ_WRITE)
                try {
                    for (parte in lista.chunked(200)) {
                        currentCoroutineContext().ensureActive()
                        val msgs = f.getMessagesByUID(parte.map { it.uid }.toLongArray()).filterNotNull().toTypedArray()
                        if (msgs.isEmpty()) continue
                        if (puedeMover) {
                            f.moveMessages(msgs, papelera)
                        } else {
                            f.copyMessages(msgs, papelera)
                            f.setFlags(msgs, Flags(Flags.Flag.DELETED), true)
                            try { f.expunge(msgs) } catch (e: javax.mail.MessagingException) { f.expunge() }
                        }
                        n += msgs.size
                        progreso("Moviendo a la papelera… $n de ${mensajes.size}")
                    }
                } finally {
                    f.close(false)
                }
            }
            n
        } finally {
            try { store.close() } catch (_: Exception) { }
        }
    }

    /** Borra para siempre todo lo que hay en el spam o en la papelera del servidor. */
    suspend fun vaciar(ctx: Context, c: CuentaCorreo, carpeta: String): Int = withContext(Dispatchers.IO) {
        val store = conectar(ctx, c)
        try {
            val f = store.getFolder(carpeta) as IMAPFolder
            f.open(Folder.READ_WRITE)
            val msgs = f.messages
            if (msgs.isNotEmpty()) {
                f.setFlags(msgs, Flags(Flags.Flag.DELETED), true)
                f.expunge()
            }
            f.close(false)
            msgs.size
        } finally {
            try { store.close() } catch (_: Exception) { }
        }
    }

    /**
     * Desuscripción automática con un clic (RFC 8058): un POST al enlace que el remitente declaró.
     * Devuelve true si el remitente la aceptó.
     */
    suspend fun desuscribirUnClic(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val con = URL(url).openConnection() as HttpURLConnection
            con.requestMethod = "POST"
            con.doOutput = true
            con.connectTimeout = 15000
            con.readTimeout = 15000
            con.instanceFollowRedirects = true
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            con.outputStream.use { it.write("List-Unsubscribe=One-Click".toByteArray()) }
            val codigo = con.responseCode
            con.disconnect()
            codigo in 200..299
        } catch (e: Exception) {
            false
        }
    }
}

/** El mismo análisis, sin los correos que ya se movieron o borraron. */
fun AnalisisCorreo.sin(quitados: Collection<Mensaje>): AnalisisCorreo {
    val fuera = quitados.map { it.carpeta to it.uid }.toSet()
    fun ok(m: Mensaje) = (m.carpeta to m.uid) !in fuera
    return copy(
        duplicados = duplicados.map { GrupoCorreos(it.mensajes.filter(::ok)) }.filter { it.mensajes.size > 1 },
        suscripciones = suscripciones.map { it.copy(mensajes = it.mensajes.filter(::ok)) }.filter { it.mensajes.isNotEmpty() },
        pesados = pesados.filter(::ok),
        papelera = papelera?.let { p -> p.copy(cantidad = p.cantidad + quitados.size, tamano = p.tamano + quitados.sumOf { it.tamano }) },
    )
}
