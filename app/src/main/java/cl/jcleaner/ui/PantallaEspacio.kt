package cl.jcleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.Archivo
import cl.jcleaner.data.Escaner
import cl.jcleaner.data.Motor
import cl.jcleaner.data.Salud
import cl.jcleaner.util.legible
import java.io.File

private class Entrada(val file: File, val tamano: Long, val carpeta: Boolean)

@Composable
fun PantallaEspacio(onAtras: () -> Unit) = ConAccesoArchivos {
    val ctx = LocalContext.current
    val raiz = remember { Escaner.raiz.absolutePath }
    var ruta by remember { mutableStateOf(raiz) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var pendientes by remember { mutableStateOf<List<Archivo>?>(null) }
    val mapa = Motor.espacio
    val prog = Motor.progEspacio

    LaunchedEffect(Unit) { if (Motor.espacio == null && Motor.progEspacio == null) Motor.analizarEspacio() }

    val subir = { ruta = File(ruta).parent ?: raiz; sel = emptySet() }
    BackHandler(enabled = ruta != raiz) { subir() }

    val entradas = remember(ruta, mapa) {
        if (mapa == null) emptyList()
        else File(ruta).listFiles().orEmpty()
            .filter { it.name != Escaner.PAPELERA_DIR }
            .map { f -> if (f.isDirectory) Entrada(f, mapa.porCarpeta[f.absolutePath] ?: 0L, true) else Entrada(f, f.length(), false) }
            .filter { it.tamano > 0 }
            .sortedByDescending { it.tamano }
    }
    val totalAqui = mapa?.porCarpeta?.get(ruta) ?: entradas.sumOf { it.tamano }
    val elegidos = entradas.filter { !it.carpeta && it.file.absolutePath in sel }

    Column(Modifier.fillMaxSize()) {
        Encabezado(
            "¿Qué ocupa mi espacio?",
            if (ruta == raiz) "Almacenamiento interno" else ruta.removePrefix(raiz).trimStart('/'),
            onAtras = { if (ruta != raiz) subir() else onAtras() },
        ) {
            IconButton(onClick = { Motor.analizarEspacio() }, enabled = prog == null) { Icon(Icons.Rounded.Refresh, "Volver a medir") }
        }
        when {
            prog != null -> Cargando(prog) { Motor.cancelar("espacio") }
            mapa == null -> Vacio(Icons.Rounded.PieChart, "Mide qué carpetas ocupan más espacio", "Medir") { Motor.analizarEspacio() }
            else -> {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (ruta == raiz) {
                        item {
                            val equipo = remember { Salud.equipo(ctx) }
                            val sistema = (equipo.almUsado - mapa.totalArchivos).coerceAtLeast(0)
                            val filas = mapa.categorias.entries.filter { it.value > 0 }.map { it.key to it.value } +
                                listOf("Apps y sistema" to sistema)
                            Tarjeta(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text("Por tipo de archivo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.size(6.dp))
                                filas.sortedByDescending { it.second }.forEach { (nombre, bytes) ->
                                    Barrita(nombre, bytes, if (equipo.almUsado > 0) bytes.toFloat() / equipo.almUsado else 0f)
                                }
                                Text(
                                    "\"Apps y sistema\" incluye las apps instaladas, sus datos y Android. Revisa la pestaña Caché para ver cuánto ocupa cada app.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Text(
                                "Carpetas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(entradas, key = { it.file.absolutePath }) { e ->
                        val fraccion = if (totalAqui > 0) e.tamano.toFloat() / totalAqui else 0f
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (e.carpeta) { ruta = e.file.absolutePath; sel = emptySet() }
                                    else { val r = e.file.absolutePath; sel = if (r in sel) sel - r else sel + r }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (e.carpeta) {
                                IconoRedondo(Icons.Rounded.Folder, tam = 40.dp)
                            } else {
                                Checkbox(checked = e.file.absolutePath in sel, onCheckedChange = { v ->
                                    val r = e.file.absolutePath; sel = if (v) sel + r else sel - r
                                })
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                    Text(e.tamano.legible(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                LinearProgressIndicator(
                                    progress = { fraccion },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = colorSegun(fraccion),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round,
                                )
                            }
                        }
                    }
                }
                if (elegidos.isNotEmpty()) {
                    BarraAccion("Borrar ${elegidos.size} · ${elegidos.sumOf { it.tamano }.legible()}", true) {
                        pendientes = elegidos.map { Archivo(it.file, it.tamano, it.file.lastModified()) }
                    }
                }
            }
        }
    }
    FlujoBorrar(pendientes) { r ->
        pendientes = null
        if (r != null) sel = sel - r.rutas
    }
}

@Composable
private fun Barrita(nombre: String, bytes: Long, fraccion: Float) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(nombre, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${bytes.legible()} · ${(fraccion * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { fraccion },
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}
