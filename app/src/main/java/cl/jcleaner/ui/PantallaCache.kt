package cl.jcleaner.ui

import android.content.Context
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

    fun abrir(a: InfoApp) {
        if (prefs.getBoolean("ayuda_vista", false)) Permisos.abrirInfoApp(ctx, a.paquete) else ayuda = a
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Caché de apps", "Cuánto ocupa el caché de cada app") {
            IconButton(onClick = { Motor.cargarApps(ctx.applicationContext) }, enabled = prog == null) {
                Icon(Icons.Rounded.Refresh, "Actualizar")
            }
        }
        when {
            prog != null -> Cargando(prog)
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
