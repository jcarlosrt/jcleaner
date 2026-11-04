package cl.jcleaner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.jcleaner.data.AnalisisContactos
import cl.jcleaner.data.Contacto
import cl.jcleaner.data.Contactos
import cl.jcleaner.data.GrupoContactos
import cl.jcleaner.data.Motor
import cl.jcleaner.util.Permisos
import kotlinx.coroutines.launch

private val PERMISOS_CONTACTOS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

private fun tienePermisoContactos(ctx: Context) =
    PERMISOS_CONTACTOS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

private sealed interface AccionContactos {
    class UnirUno(val grupo: GrupoContactos) : AccionContactos
    data object UnirTodos : AccionContactos
    data object QuitarRepetidos : AccionContactos
    class BorrarVacios(val lista: List<Contacto>) : AccionContactos
}

@Composable
fun PantallaContactos(onAtras: () -> Unit) {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(tienePermisoContactos(ctx)) }
    var negado by remember { mutableStateOf(false) }
    AlReanudar { ok = tienePermisoContactos(ctx) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        ok = r.values.all { it }
        if (!ok) negado = true
    }
    if (!ok) {
        Column(Modifier.fillMaxSize()) {
            Encabezado("Contactos", onAtras = onAtras)
            AvisoPermiso(
                icono = Icons.Rounded.Contacts,
                titulo = "Acceso a tus contactos",
                texto = "Para encontrar contactos repetidos y unirlos, JCLEANER necesita ver y editar tus contactos.\n\nNada sale de tu teléfono: la app no tiene acceso a internet.",
                boton = if (negado) "Abrir ajustes de la app" else "Dar permiso",
            ) {
                if (negado) Permisos.abrirInfoApp(ctx, ctx.packageName) else lanzador.launch(PERMISOS_CONTACTOS)
            }
        }
        return
    }

    val prefs = remember { ctx.getSharedPreferences("jcleaner", Context.MODE_PRIVATE) }
    var ignorados by remember { mutableStateOf(prefs.getStringSet("contactos_ignorados", emptySet()).orEmpty().toSet()) }
    var analisis by remember { mutableStateOf<AnalisisContactos?>(null) }
    var trabajando by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var pestana by remember { mutableIntStateOf(0) }
    var mismoNombre by remember { mutableStateOf(true) }
    val elegidos = remember { mutableStateMapOf<String, Long>() }
    var selVacios by remember { mutableStateOf(setOf<Long>()) }
    var confirmar by remember { mutableStateOf<AccionContactos?>(null) }

    LaunchedEffect(recarga) {
        trabajando = "Leyendo tus contactos…"
        try {
            analisis = Contactos.analizar(ctx.applicationContext)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No pude leer los contactos: ${e.message}", Toast.LENGTH_LONG).show()
        }
        trabajando = null
    }

    fun principalDe(g: GrupoContactos): Contacto =
        elegidos[g.firma]?.let { id -> g.contactos.find { it.id == id } } ?: g.contactos.first()

    /** Respalda (la primera vez), ejecuta y vuelve a leer los contactos. */
    fun ejecutar(texto: String, trabajo: suspend () -> String) {
        trabajando = texto
        Motor.scope.launch {
            try {
                if (!Contactos.respaldoHecho) {
                    trabajando = "Guardando respaldo de tus contactos…"
                    val ruta = Contactos.respaldo(ctx.applicationContext)
                    Toast.makeText(ctx, "Respaldo guardado en $ruta", Toast.LENGTH_LONG).show()
                    trabajando = texto
                }
                val mensaje = trabajo()
                Toast.makeText(ctx, mensaje, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "No se pudo completar: ${e.message}", Toast.LENGTH_LONG).show()
            }
            selVacios = emptySet()
            recarga++
        }
    }

    val a = analisis
    val grupos = a?.grupos?.filter { it.firma !in ignorados }.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Encabezado("Contactos", if (a != null) "${a.total} contactos revisados" else null, onAtras) {
            IconButton(enabled = trabajando == null, onClick = {
                trabajando = "Guardando respaldo…"
                Motor.scope.launch {
                    try {
                        Toast.makeText(ctx, "Respaldo guardado en ${Contactos.respaldo(ctx.applicationContext)}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "No se pudo respaldar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    trabajando = null
                }
            }) { Icon(Icons.Rounded.Backup, "Crear respaldo") }
        }
        val t = trabajando
        when {
            t != null -> Cargando(t)
            a == null -> Vacio(Icons.Rounded.Contacts, "No pude leer tus contactos", "Reintentar") { recarga++ }
            else -> {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Opcion("Duplicados (${grupos.size})", pestana == 0) { pestana = 0 }
                    Opcion("Números repetidos (${a.repetidos.sumOf { it.sobrantes.size }})", pestana == 1) { pestana = 1 }
                    Opcion("Sin teléfono ni correo (${a.vacios.size})", pestana == 2) { pestana = 2 }
                }
                when (pestana) {
                    0 -> Duplicados(
                        grupos, mismoNombre, { mismoNombre = it }, ::principalDe,
                        onElegir = { g, c -> elegidos[g.firma] = c.id },
                        onUnir = { confirmar = AccionContactos.UnirUno(it) },
                        onUnirTodos = { confirmar = AccionContactos.UnirTodos },
                        onIgnorar = { g ->
                            ignorados = ignorados + g.firma
                            prefs.edit().putStringSet("contactos_ignorados", ignorados).apply()
                        },
                    )
                    1 -> {
                        if (a.repetidos.isEmpty()) {
                            Vacio(Icons.Rounded.TaskAlt, "Ningún contacto tiene el mismo número guardado dos veces")
                        } else {
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    Nota("Mismo número guardado varias veces en un contacto (por ejemplo \"912345678\" y \"+56 9 1234 5678\"). Se conserva el más completo.")
                                }
                                items(a.repetidos, key = { it.contacto.id.toString() + it.numero }) { r ->
                                    Tarjeta {
                                        Text(r.contacto.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text("Se conserva: ${r.numero}", color = MaterialTheme.colorScheme.primary)
                                        Text("Se quitan: " + r.sobrantes.joinToString(", ") { it.numero }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            BarraAccion("Quitar ${a.repetidos.sumOf { it.sobrantes.size }} números repetidos", true, Icons.Rounded.MergeType) {
                                confirmar = AccionContactos.QuitarRepetidos
                            }
                        }
                    }
                    else -> {
                        if (a.vacios.isEmpty()) {
                            Vacio(Icons.Rounded.TaskAlt, "Todos tus contactos tienen teléfono o correo")
                        } else {
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                                item {
                                    Row(Modifier.padding(horizontal = 16.dp)) {
                                        Nota("Contactos que solo tienen nombre, sin ningún teléfono ni correo.")
                                    }
                                    TextButton(onClick = {
                                        selVacios = if (selVacios.size == a.vacios.size) emptySet() else a.vacios.map { it.id }.toSet()
                                    }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(if (selVacios.size == a.vacios.size) "Quitar selección" else "Seleccionar todos")
                                    }
                                }
                                items(a.vacios, key = { it.id }) { c ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { selVacios = if (c.id in selVacios) selVacios - c.id else selVacios + c.id }
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = c.id in selVacios, onCheckedChange = { selVacios = if (it) selVacios + c.id else selVacios - c.id })
                                        Column {
                                            Text(c.nombre, style = MaterialTheme.typography.bodyLarge)
                                            Text(c.cuentas.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            BarraAccion(
                                if (selVacios.isEmpty()) "Marca los que quieras borrar" else "Borrar ${selVacios.size} contactos",
                                selVacios.isNotEmpty(),
                            ) { confirmar = AccionContactos.BorrarVacios(a.vacios.filter { it.id in selVacios }) }
                        }
                    }
                }
            }
        }
    }

    // ---- confirmaciones
    val accion = confirmar
    if (accion != null && a != null) {
        val (titulo, texto) = when (accion) {
            is AccionContactos.UnirUno -> {
                val p = principalDe(accion.grupo)
                "¿Unir ${accion.grupo.contactos.size} contactos?" to
                    "Quedarán como un solo contacto llamado \"${p.nombre}\". Se conservan todos los teléfonos y correos.\n\nSi te equivocas, puedes separarlos desde la app Contactos."
            }
            AccionContactos.UnirTodos ->
                "¿Unir los ${grupos.size} grupos?" to
                    "Cada grupo quedará como un solo contacto, con el nombre que marcaste (o el más completo). Se conservan todos los teléfonos y correos.\n\nRevisa antes los grupos marcados como \"Mismo nombre\": podrían ser personas distintas."
            AccionContactos.QuitarRepetidos ->
                "¿Quitar números repetidos?" to "Se borrarán ${a.repetidos.sumOf { it.sobrantes.size }} copias de números que ya están en el mismo contacto."
            is AccionContactos.BorrarVacios ->
                "¿Borrar ${accion.lista.size} contactos?" to "Se borrarán contactos que no tienen ningún teléfono ni correo."
        }
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(titulo) },
            text = {
                Column {
                    Text(texto)
                    if (!Contactos.respaldoHecho) {
                        Text("Antes se guardará un respaldo de todos tus contactos en Descargas/JCLEANER.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmar = null
                    when (accion) {
                        is AccionContactos.UnirUno -> {
                            val g = accion.grupo
                            val p = principalDe(g)
                            ejecutar("Uniendo contactos…") {
                                Contactos.unir(ctx.applicationContext, g, p, mismoNombre); "Listo: quedó un solo contacto \"${p.nombre}\""
                            }
                        }
                        AccionContactos.UnirTodos -> {
                            val pares = grupos.map { it to principalDe(it) }
                            ejecutar("Uniendo ${pares.size} grupos…") {
                                var hechos = 0
                                for ((g, p) in pares) {
                                    try { Contactos.unir(ctx.applicationContext, g, p, mismoNombre); hechos++ } catch (_: Exception) { }
                                }
                                "Listo: se unieron $hechos de ${pares.size} grupos"
                            }
                        }
                        AccionContactos.QuitarRepetidos -> {
                            val lista = a.repetidos
                            ejecutar("Quitando números repetidos…") {
                                Contactos.quitarRepetidos(ctx.applicationContext, lista); "Listo: se quitaron ${lista.sumOf { it.sobrantes.size }} números repetidos"
                            }
                        }
                        is AccionContactos.BorrarVacios -> {
                            val lista = accion.lista
                            ejecutar("Borrando contactos…") {
                                Contactos.borrarContactos(ctx.applicationContext, lista); "Listo: se borraron ${lista.size} contactos"
                            }
                        }
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Nota(texto: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Duplicados(
    grupos: List<GrupoContactos>,
    mismoNombre: Boolean,
    onMismoNombre: (Boolean) -> Unit,
    principalDe: (GrupoContactos) -> Contacto,
    onElegir: (GrupoContactos, Contacto) -> Unit,
    onUnir: (GrupoContactos) -> Unit,
    onUnirTodos: () -> Unit,
    onIgnorar: (GrupoContactos) -> Unit,
) {
    if (grupos.isEmpty()) {
        Vacio(Icons.Rounded.TaskAlt, "¡No encontré contactos duplicados!")
        return
    }
    Column {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dejar el mismo nombre en todos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Usa el nombre que marques en cada grupo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = mismoNombre, onCheckedChange = onMismoNombre)
                    }
                }
            }
            items(grupos, key = { it.firma }) { g ->
                val p = principalDe(g)
                Tarjeta {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.razones.forEach { r ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (r == "Mismo nombre") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(r, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text("Elige con qué nombre queda:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    g.contactos.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onElegir(g, c) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            RadioButton(selected = c.id == p.id, onClick = { onElegir(g, c) })
                            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                                Text(c.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                c.telefonosUnicos.take(3).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                c.correos.take(2).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Text(c.cuentas.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onIgnorar(g) }) { Text("No son duplicados") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onUnir(g) }) { Text("Unir") }
                    }
                }
            }
        }
        if (grupos.size > 1) {
            BarraAccion("Unir los ${grupos.size} grupos", true, Icons.Rounded.MergeType, onUnirTodos)
        }
    }
}
