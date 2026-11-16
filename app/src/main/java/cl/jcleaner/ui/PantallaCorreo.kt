package cl.jcleaner.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Unsubscribe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.AnalisisCorreo
import cl.jcleaner.data.Correo
import cl.jcleaner.data.CuentaCorreo
import cl.jcleaner.data.Cuentas
import cl.jcleaner.data.Historial
import cl.jcleaner.data.Mensaje
import cl.jcleaner.data.MicrosoftAuth
import cl.jcleaner.data.Motor
import cl.jcleaner.data.PROVEEDORES
import cl.jcleaner.data.Suscripcion
import cl.jcleaner.data.TipoAcceso
import cl.jcleaner.data.sin
import cl.jcleaner.util.hace
import cl.jcleaner.util.legible
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PantallaCorreo(onAtras: () -> Unit) {
    val ctx = LocalContext.current
    var cuentas by remember { mutableStateOf(Cuentas.listar(ctx)) }
    var abierta by remember { mutableStateOf<CuentaCorreo?>(null) }
    var agregando by remember { mutableStateOf(false) }
    var quitar by remember { mutableStateOf<CuentaCorreo?>(null) }

    val c = abierta
    if (c != null) {
        BackHandler { abierta = null }
        CuentaAbierta(c) { abierta = null }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Correo", "Spam, papelera, correos repetidos y suscripciones", onAtras)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cuentas.isEmpty()) {
                item {
                    Tarjeta {
                        Text("Agrega tu correo para empezar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "JCLEANER se conecta directo con tu servidor de correo (Gmail, Outlook, Yahoo, iCloud u otro). " +
                                "Solo lee los encabezados (remitente, asunto, fecha, tamaño), nunca el contenido, y no pasa por ningún servidor de JCLEANER. " +
                                "La contraseña o sesión se guarda cifrada en tu teléfono.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            items(cuentas, key = { it.id }) { cu ->
                Tarjeta(onClick = { abierta = cu }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconoRedondo(Icons.Rounded.Mail)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cu.correo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val a = Motor.correos[cu.id]
                            Text(
                                if (a != null) "${a.suscripciones.size} suscripciones · ${a.duplicados.sumOf { it.sobrantes.size }} repetidos · spam: ${a.spam?.cantidad ?: 0}"
                                else cu.prov.nombre,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { quitar = cu }) { Text("Quitar") }
                    }
                }
            }
        }
        BarraAccion("Agregar cuenta de correo", true, Icons.Rounded.Mail) { agregando = true }
    }

    if (agregando) {
        DialogoAgregar(
            onCerrar = { agregando = false },
            onListo = { nueva ->
                agregando = false
                cuentas = Cuentas.listar(ctx)
                abierta = nueva
                Motor.analizarCorreo(ctx.applicationContext, nueva)
            },
        )
    }
    quitar?.let { cu ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("¿Quitar ${cu.correo}?") },
            text = { Text("Se borra de JCLEANER la contraseña o sesión guardada. Tu correo no cambia.") },
            confirmButton = {
                Button(onClick = {
                    Cuentas.quitar(ctx, cu); Motor.correos.remove(cu.id); cuentas = Cuentas.listar(ctx); quitar = null
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar") } },
        )
    }
}

// ------------------------------------------------------------------ agregar cuenta

@Composable
private fun DialogoAgregar(onCerrar: () -> Unit, onListo: (CuentaCorreo) -> Unit) {
    val ctx = LocalContext.current
    var prov by remember { mutableStateOf(PROVEEDORES[0]) }
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var servidor by remember { mutableStateOf("") }
    var probando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val inicioMicrosoft = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        probando = true
        Motor.scope.launch {
            try {
                val (dir, sesion) = MicrosoftAuth.completar(ctx.applicationContext, r.data)
                val nueva = CuentaCorreo(UUID.randomUUID().toString(), dir, prov.id, prov.servidor, prov.puerto)
                Cuentas.agregar(ctx, nueva, sesion)
                try {
                    Correo.probar(ctx.applicationContext, nueva)
                    onListo(nueva)
                } catch (e: Exception) {
                    Cuentas.quitar(ctx, nueva); throw e
                }
            } catch (e: Exception) {
                error = e.message
            }
            probando = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!probando) onCerrar() },
        title = { Text("Agregar correo") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PROVEEDORES.forEach { p -> Opcion(p.nombre, p == prov) { prov = p; error = null } }
                    }
                }
                item { Text(prov.ayuda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (prov.acceso == TipoAcceso.MICROSOFT) {
                    item {
                        if (!MicrosoftAuth.disponible) {
                            Text(
                                "Esta versión de JCLEANER todavía no tiene activado el inicio de sesión de Microsoft (falta registrar la app en Microsoft). " +
                                    "Revisa el README del proyecto en GitHub.",
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(correo, { correo = it.trim() }, label = { Text("Correo") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(clave, { clave = it }, label = { Text("Contraseña de aplicación") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    }
                    if (prov.id == "otro") {
                        item {
                            OutlinedTextField(servidor, { servidor = it.trim() }, label = { Text("Servidor IMAP") }, singleLine = true,
                                placeholder = { Text("imap.tudominio.com") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (prov.id == "gmail") {
                        item {
                            TextButton(onClick = {
                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/apppasswords"))) } catch (_: Exception) { }
                            }) { Text("Crear contraseña de aplicación de Google") }
                        }
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                if (probando) item { Text("Comprobando la conexión…", color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            if (prov.acceso == TipoAcceso.MICROSOFT) {
                Button(enabled = !probando && MicrosoftAuth.disponible, onClick = {
                    error = null
                    try { inicioMicrosoft.launch(MicrosoftAuth.intentInicio(ctx)) } catch (e: Exception) { error = e.message }
                }) { Text("Iniciar sesión con Microsoft") }
            } else {
                val host = if (prov.id == "otro") servidor else prov.servidor
                Button(enabled = !probando && "@" in correo && clave.isNotBlank() && host.isNotBlank(), onClick = {
                    error = null
                    probando = true
                    val nueva = CuentaCorreo(UUID.randomUUID().toString(), correo.lowercase(), prov.id, host, prov.puerto)
                    Motor.scope.launch {
                        Cuentas.agregar(ctx, nueva, clave.replace(" ", ""))
                        try {
                            Correo.probar(ctx.applicationContext, nueva)
                            onListo(nueva)
                        } catch (e: Exception) {
                            Cuentas.quitar(ctx, nueva)
                            error = e.message
                        }
                        probando = false
                    }
                }) { Text("Conectar") }
            }
        },
        dismissButton = { if (!probando) TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ------------------------------------------------------------------ cuenta abierta

private sealed interface AccionCorreo {
    class Mover(val titulo: String, val mensajes: List<Mensaje>) : AccionCorreo
    class Vaciar(val titulo: String, val carpeta: String, val cantidad: Int, val tamano: Long) : AccionCorreo
}

@Composable
private fun CuentaAbierta(c: CuentaCorreo, onAtras: () -> Unit) {
    val ctx = LocalContext.current
    val a = Motor.correos[c.id]
    val prog = Motor.progCorreo
    var pestana by remember { mutableIntStateOf(0) }
    var accion by remember { mutableStateOf<AccionCorreo?>(null) }
    var trabajando by remember { mutableStateOf<String?>(null) }
    var desuscritos by remember { mutableStateOf(Cuentas.desuscritos(ctx, c)) }
    var ofrecerBorrar by remember { mutableStateOf<Suscripcion?>(null) }

    fun desuscribir(s: Suscripcion) {
        trabajando = "Pidiendo la desuscripción a ${s.nombre}…"
        Motor.scope.launch {
            val automatico = s.unClic?.let { Correo.desuscribirUnClic(it) } ?: false
            trabajando = null
            val abrir = when {
                automatico -> null
                s.web != null -> Intent(Intent.ACTION_VIEW, Uri.parse(s.web))
                s.mailto != null -> Intent(Intent.ACTION_SENDTO, Uri.parse(s.mailto))
                else -> null
            }
            if (automatico) {
                Toast.makeText(ctx, "Listo: ${s.nombre} aceptó la desuscripción", Toast.LENGTH_LONG).show()
            } else if (abrir != null) {
                try {
                    ctx.startActivity(abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    Toast.makeText(ctx, if (s.web != null) "Confirma la desuscripción en la página que se abrió" else "Envía el correo que se preparó para desuscribirte", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "No pude abrir el enlace de desuscripción", Toast.LENGTH_LONG).show()
                    return@launch
                }
            } else {
                Toast.makeText(ctx, "Este remitente no ofrece forma de desuscribirse", Toast.LENGTH_LONG).show()
                return@launch
            }
            Cuentas.marcarDesuscrito(ctx, c, s.remitente)
            desuscritos = Cuentas.desuscritos(ctx, c)
            ofrecerBorrar = s
        }
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado(c.correo, c.prov.nombre, onAtras) {
            IconButton(onClick = { Motor.analizarCorreo(ctx.applicationContext, c) }, enabled = prog == null && trabajando == null) {
                Icon(Icons.Rounded.Refresh, "Volver a analizar")
            }
        }
        val t = trabajando
        when {
            prog != null -> Cargando(prog) { Motor.cancelar("correo") }
            t != null -> Cargando(t)
            a == null -> Vacio(Icons.Rounded.Mail, "Analiza tu correo", "Analizar") { Motor.analizarCorreo(ctx.applicationContext, c) }
            else -> {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Opcion("Resumen", pestana == 0) { pestana = 0 }
                    Opcion("Suscripciones (${a.suscripciones.size})", pestana == 1) { pestana = 1 }
                    Opcion("Repetidos (${a.duplicados.sumOf { it.sobrantes.size }})", pestana == 2) { pestana = 2 }
                    Opcion("Pesados (${a.pesados.size})", pestana == 3) { pestana = 3 }
                }
                when (pestana) {
                    0 -> Resumen(a) { accion = it }
                    1 -> Suscripciones(a, desuscritos, ::desuscribir) { s -> accion = AccionCorreo.Mover("los ${s.mensajes.size} correos de ${s.nombre}", s.mensajes) }
                    2 -> Repetidos(a) { accion = AccionCorreo.Mover("${it.size} correos repetidos", it) }
                    else -> Pesados(a) { accion = AccionCorreo.Mover("${it.size} correos pesados", it) }
                }
            }
        }
    }

    ofrecerBorrar?.let { s ->
        AlertDialog(
            onDismissRequest = { ofrecerBorrar = null },
            title = { Text("¿Borrar también sus correos?") },
            text = { Text("Tienes ${s.mensajes.size} correos de ${s.nombre} (${s.tamano.legible()}). ¿Los mueves a la papelera?") },
            confirmButton = { Button(onClick = { ofrecerBorrar = null; accion = AccionCorreo.Mover("los ${s.mensajes.size} correos de ${s.nombre}", s.mensajes) }) { Text("Sí, revisar") } },
            dismissButton = { TextButton(onClick = { ofrecerBorrar = null }) { Text("No") } },
        )
    }

    accion?.let { ac ->
        val (titulo, texto) = when (ac) {
            is AccionCorreo.Mover -> "¿Mover ${ac.titulo} a la papelera?" to
                "Irán a la papelera de tu correo (${ac.mensajes.sumOf { it.tamano }.legible()}). Puedes recuperarlos desde ahí; tu proveedor los borra solo después de un tiempo (en Gmail y Outlook, 30 días)."
            is AccionCorreo.Vaciar -> "¿Vaciar ${ac.titulo}?" to
                "Se borrarán para siempre ${ac.cantidad} correos (${ac.tamano.legible()}). No se podrán recuperar."
        }
        AlertDialog(
            onDismissRequest = { accion = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(titulo) },
            text = { Text(texto) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        accion = null
                        trabajando = "Conectando…"
                        Motor.scope.launch {
                            try {
                                when (ac) {
                                    is AccionCorreo.Mover -> {
                                        val n = Correo.aPapelera(ctx.applicationContext, c, ac.mensajes) { trabajando = it }
                                        Motor.correos[c.id]?.let { Motor.correos[c.id] = it.sin(ac.mensajes) }
                                        Toast.makeText(ctx, "Se movieron $n correos a la papelera", Toast.LENGTH_LONG).show()
                                    }
                                    is AccionCorreo.Vaciar -> {
                                        trabajando = "Vaciando ${ac.titulo}…"
                                        val n = Correo.vaciar(ctx.applicationContext, c, ac.carpeta)
                                        Historial.registrar(ctx.applicationContext, ac.tamano, n, "Correo: ${ac.titulo} vaciado")
                                        Motor.correos[c.id]?.let { an ->
                                            Motor.correos[c.id] = an.copy(
                                                spam = an.spam?.takeIf { it.nombre != ac.carpeta } ?: an.spam?.copy(cantidad = 0, tamano = 0),
                                                papelera = an.papelera?.takeIf { it.nombre != ac.carpeta } ?: an.papelera?.copy(cantidad = 0, tamano = 0),
                                            )
                                        }
                                        Toast.makeText(ctx, "Se borraron $n correos", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(ctx, e.message ?: "No se pudo completar", Toast.LENGTH_LONG).show()
                            }
                            trabajando = null
                        }
                    },
                ) { Text(if (ac is AccionCorreo.Vaciar) "Borrar para siempre" else "Mover a la papelera") }
            },
            dismissButton = { TextButton(onClick = { accion = null }) { Text("Cancelar") } },
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
private fun Resumen(a: AnalisisCorreo, onAccion: (AccionCorreo) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Tarjeta {
                Text("Bandeja de entrada: ${a.bandeja} correos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (a.revisados < a.bandeja) "Se revisaron en detalle los ${a.revisados} más recientes." else "Se revisaron todos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        listOfNotNull(
            a.spam?.let { Triple(it, "el spam", Icons.Rounded.Report) },
            a.papelera?.let { Triple(it, "la papelera", Icons.Rounded.Delete) },
        ).forEach { (car, nombre, icono) ->
            item {
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconoRedondo(icono)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(nombre.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${car.cantidad} correos · ${car.tamano.legible()}", color = MaterialTheme.colorScheme.primary)
                        }
                        FilledTonalButton(enabled = car.cantidad > 0, onClick = { onAccion(AccionCorreo.Vaciar(nombre, car.nombre, car.cantidad, car.tamano)) }) { Text("Vaciar") }
                    }
                }
            }
        }
        item {
            Tarjeta {
                Text("Encontré", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("• ${a.suscripciones.size} remitentes con suscripción (${a.suscripciones.sumOf { it.mensajes.size }} correos, ${a.suscripciones.sumOf { it.tamano }.legible()})")
                Text("• ${a.duplicados.sumOf { it.sobrantes.size }} correos repetidos")
                Text("• ${a.pesados.size} correos de más de 5 MB (${a.pesados.sumOf { it.tamano }.legible()})")
            }
        }
        item { Nota("Lo que borres va a la papelera de tu correo, no se elimina de inmediato. El espacio de tu cuenta se libera al vaciar la papelera.") }
    }
}

@Composable
private fun Suscripciones(a: AnalisisCorreo, desuscritos: Set<String>, onDesuscribir: (Suscripcion) -> Unit, onBorrar: (Suscripcion) -> Unit) {
    if (a.suscripciones.isEmpty()) {
        Vacio(Icons.Rounded.TaskAlt, "No encontré correos de suscripciones en tu bandeja de entrada")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Nota(
                "Boletines y publicidad que declaran cómo desuscribirse. Si es spam de verdad (alguien que no conoces), no te desuscribas: " +
                    "eso le confirma que tu correo existe. Mejor bórralo o márcalo como spam en tu correo."
            )
        }
        items(a.suscripciones, key = { it.remitente }) { s ->
            val hecho = s.remitente in desuscritos
            Tarjeta {
                Text(s.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.remitente, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${s.mensajes.size} correos · ${s.tamano.legible()} · último ${hace(s.ultimo)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(onClick = { onBorrar(s) }) { Text("Borrar ${s.mensajes.size}") }
                    if (hecho) {
                        FilledTonalButton(onClick = { onDesuscribir(s) }) {
                            Icon(Icons.Rounded.MarkEmailRead, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Desuscrito")
                        }
                    } else {
                        Button(onClick = { onDesuscribir(s) }, enabled = s.unClic != null || s.web != null || s.mailto != null) {
                            Icon(Icons.Rounded.Unsubscribe, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Desuscribir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Repetidos(a: AnalisisCorreo, onBorrar: (List<Mensaje>) -> Unit) {
    if (a.duplicados.isEmpty()) {
        Vacio(Icons.Rounded.TaskAlt, "No hay correos repetidos")
        return
    }
    Column {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Nota("El mismo correo guardado más de una vez. Se conserva uno de cada grupo y se mueven las copias a la papelera.") }
            items(a.duplicados, key = { "${it.conservar.carpeta}/${it.conservar.uid}" }) { g ->
                Tarjeta {
                    Text(g.conservar.asunto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${g.conservar.nombreRemitente} · ${hace(g.conservar.fecha)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${g.mensajes.size} copias · sobran ${g.sobrantes.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val todos = a.duplicados.flatMap { it.sobrantes }
        BarraAccion("Borrar ${todos.size} copias", true) { onBorrar(todos) }
    }
}

@Composable
private fun Pesados(a: AnalisisCorreo, onBorrar: (List<Mensaje>) -> Unit) {
    if (a.pesados.isEmpty()) {
        Vacio(Icons.Rounded.TaskAlt, "No hay correos de más de 5 MB")
        return
    }
    var sel by remember(a.pesados) { mutableStateOf(setOf<Long>()) }
    val elegidos = a.pesados.filter { it.uid in sel }
    Column {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Nota("Correos con archivos adjuntos grandes. Si ya guardaste los adjuntos, puedes borrarlos para liberar espacio de tu cuenta.")
                }
            }
            items(a.pesados, key = { "${it.carpeta}/${it.uid}" }) { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { sel = if (m.uid in sel) sel - m.uid else sel + m.uid }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = m.uid in sel, onCheckedChange = { sel = if (it) sel + m.uid else sel - m.uid })
                    Icon(Icons.Rounded.AttachFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.asunto, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text("${m.nombreRemitente} · ${hace(m.fecha)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(m.tamano.legible(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        BarraAccion(
            if (elegidos.isEmpty()) "Marca los que quieras borrar" else "Mover ${elegidos.size} a la papelera · ${elegidos.sumOf { it.tamano }.legible()}",
            elegidos.isNotEmpty(),
        ) { onBorrar(elegidos) }
    }
}

