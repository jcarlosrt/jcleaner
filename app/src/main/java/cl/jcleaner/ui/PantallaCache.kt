package cl.jcleaner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.InfoApp
import cl.jcleaner.data.Motor
import cl.jcleaner.util.Permisos
import cl.jcleaner.util.hace
import cl.jcleaner.util.legible

private const val MB = 1024L * 1024

@Composable
fun PantallaCache() {
    val ctx = LocalContext.current
    val permiso = rememberPermiso(Permisos::accesoUso)
    if (!permiso) {
        AvisoPermiso(
            icono = Icons.Rounded.QueryStats,
            titulo = "Acceso de uso",
            texto = "Para medir cuánto caché tiene cada app, Android pide activar \"Acceso de uso\" para JCLEANER.\n\nEn la lista que se abrirá, busca JCLEANER y actívalo.",
            boton = "Activar",
        ) { Permisos.abrirAccesoUso(ctx) }
        return
    }

    val prefs = remember { ctx.getSharedPreferences("jcleaner", Context.MODE_PRIVATE) }
    var sistema by remember { mutableStateOf(false) }
    var orden by remember { mutableIntStateOf(0) }
    var ayuda by remember { mutableStateOf<InfoApp?>(null) }
    var vista by remember { mutableIntStateOf(0) } // 0 = caché, 1 = apps sin usar
    val ahora = remember { System.currentTimeMillis() }
    val limite = ahora - 30L * 86_400_000L

    LaunchedEffect(Unit) { if (Motor.apps == null && Motor.progApps == null) Motor.cargarApps(ctx.applicationContext) }
    // al volver de la pantalla de la app, se actualizan los tamaños
    AlReanudar { if (Motor.apps != null && Motor.progApps == null) Motor.cargarApps(ctx.applicationContext, silencioso = true) }

    val prog = Motor.progApps
    val apps = Motor.apps?.filter { sistema || !it.sistema }?.let { l ->
        when (orden) {
            0 -> l.sortedByDescending { it.cache }
            else -> l.sortedByDescending { it.apk + it.datos }
        }
    }

    // apps instaladas por ti que no se abren hace más de un mes
    val sinUsar = Motor.apps?.filter {
        !it.sistema && it.paquete != ctx.packageName &&
            (if (it.ultimoUso > 0) it.ultimoUso < limite else it.instaladaEl in 1 until limite)
    }?.sortedByDescending { it.apk + it.datos }

    fun abrir(a: InfoApp) {
        if (prefs.getBoolean("ayuda_vista", false)) Permisos.abrirInfoApp(ctx, a.paquete) else ayuda = a
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Apps", if (vista == 0) "Cuánto ocupa el caché de cada app" else "Apps que no abres hace más de un mes") {
            IconButton(onClick = { Motor.cargarApps(ctx.applicationContext) }, enabled = prog == null) {
                Icon(Icons.Rounded.Refresh, "Actualizar")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcion("Caché", vista == 0) { vista = 0 }
            Opcion("Apps sin usar${sinUsar?.let { " (${it.size})" } ?: ""}", vista == 1) { vista = 1 }
        }
        when {
            prog != null -> Cargando(prog)
            vista == 1 && sinUsar != null -> {
                if (sinUsar.isEmpty()) {
                    Vacio(Icons.Rounded.Apps, "Usas todas tus apps. ¡Bien!")
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            ResumenGrande(sinUsar.sumOf { it.apk + it.datos }, "ocupan ${sinUsar.size} apps que no usas")
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Según el registro de uso de Android. Desinstalar borra la app y sus datos; siempre puedes volver a instalarla desde Play Store.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(sinUsar, key = { "u-" + it.paquete }) { a -> FilaSinUsar(a) { desinstalar(ctx, a.paquete) } }
                    }
                }
            }
            apps == null -> Vacio(Icons.Rounded.Apps, "Mide el caché de tus apps", "Medir") { Motor.cargarApps(ctx.applicationContext) }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ResumenGrande(apps.sumOf { it.cache }, "de caché en ${apps.count { it.cache > 0 }} apps")
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Android no permite que una app borre el caché de otras. Toca \"Borrar\" y JCLEANER te lleva directo a la pantalla de esa app: ahí entra a Almacenamiento → Borrar caché. Al volver, el tamaño se actualiza.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Opcion("Más caché", orden == 0) { orden = 0 }
                        Spacer(Modifier.width(8.dp))
                        Opcion("Más pesadas", orden == 1) { orden = 1 }
                        Spacer(Modifier.weight(1f))
                        Text("Sistema", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(6.dp))
                        Switch(checked = sistema, onCheckedChange = { sistema = it })
                    }
                }
                items(apps, key = { it.paquete }) { a -> FilaApp(a) { abrir(a) } }
            }
        }
    }

    ayuda?.let { a ->
        AlertDialog(
            onDismissRequest = { ayuda = null },
            title = { Text("Borrar caché de ${a.nombre}") },
            text = {
                Text("Se abrirá la información de la app. Ahí toca:\n\n1. Almacenamiento (o \"Almacenamiento y caché\")\n2. Borrar caché\n\nNo toques \"Borrar datos\": eso borra tu sesión y configuración de la app.\n\nDespués vuelve a JCLEANER.")
            },
            confirmButton = {
                Button(onClick = {
                    prefs.edit().putBoolean("ayuda_vista", true).apply()
                    ayuda = null
                    Permisos.abrirInfoApp(ctx, a.paquete)
                }) { Text("Entendido") }
            },
            dismissButton = { TextButton(onClick = { ayuda = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FilaApp(a: InfoApp, onBorrar: () -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icono = a.icono
            if (icono != null) {
                Image(icono, null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                IconoRedondo(Icons.Rounded.Apps, tam = 42.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "App ${a.apk.legible()} · Datos ${a.datosSinCache.legible()}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Caché: ${a.cache.legible()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        a.cache > 500 * MB -> Rojo
                        a.cache > 100 * MB -> Ambar
                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            FilledTonalButton(onClick = onBorrar, enabled = a.cache > 0) { Text("Borrar") }
        }
    }
}

private fun desinstalar(ctx: Context, paquete: String) {
    try {
        @Suppress("DEPRECATION")
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$paquete")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Permisos.abrirInfoApp(ctx, paquete)
    }
}

@Composable
private fun FilaSinUsar(a: InfoApp, onDesinstalar: () -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icono = a.icono
            if (icono != null) Image(icono, null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
            else IconoRedondo(Icons.Rounded.Apps, tam = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (a.ultimoUso > 0) "Abierta por última vez ${hace(a.ultimoUso)}" else "Sin uso registrado desde que se instaló",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Ocupa ${(a.apk + a.datos).legible()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(onClick = onDesinstalar) { Text("Desinstalar") }
        }
    }
}
