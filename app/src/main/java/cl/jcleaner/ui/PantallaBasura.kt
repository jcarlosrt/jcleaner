package cl.jcleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.Archivo
import cl.jcleaner.data.Motor
import cl.jcleaner.util.legible

fun iconoBasura(id: String): ImageVector = when (id) {
    "apk" -> Icons.Rounded.Android
    "miniaturas" -> Icons.Rounded.BrokenImage
    "temporales" -> Icons.Rounded.Timer
    "vacias" -> Icons.Rounded.FolderOff
    "descargas" -> Icons.Rounded.Download
    else -> Icons.Rounded.Storage
}

@Composable
fun PantallaBasura(onVer: (Pantalla) -> Unit) = ConAccesoArchivos {
    val cats = Motor.basura
    val prog = Motor.progBasura
    var sel by remember { mutableStateOf<Set<String>?>(null) }
    var pendientes by remember { mutableStateOf<List<Archivo>?>(null) }

    LaunchedEffect(Unit) { if (Motor.basura == null && Motor.progBasura == null) Motor.analizarBasura() }
    LaunchedEffect(cats) { if (cats != null && sel == null) sel = cats.filter { it.preseleccionada }.map { it.id }.toSet() }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Limpiar", "Archivos basura y espacio desperdiciado") {
            IconButton(onClick = { sel = null; Motor.analizarBasura() }, enabled = prog == null) {
                Icon(Icons.Rounded.Refresh, "Volver a analizar")
            }
        }
        when {
            prog != null -> Cargando(prog) { Motor.cancelar("basura") }
            cats == null -> Vacio(Icons.Rounded.CleaningServices, "Analiza tu teléfono para encontrar basura", "Analizar") { Motor.analizarBasura() }
            else -> {
                val s = sel ?: emptySet()
                val elegidos = cats.filter { it.id in s && it.cantidad > 0 }
                val total = elegidos.sumOf { it.tamano }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { ResumenGrande(total, "seleccionado para limpiar") }
                    items(cats, key = { it.id }) { c ->
                        FilaCategoria(
                            icono = iconoBasura(c.id),
                            titulo = c.titulo,
                            descripcion = c.descripcion,
                            detalle = if (c.cantidad == 0) "Nada que limpiar"
                            else if (c.id == "vacias") "${c.cantidad} carpetas"
                            else "${c.cantidad} archivos · ${c.tamano.legible()}",
                            marcado = c.id in s && c.cantidad > 0,
                            onMarcar = if (c.cantidad > 0) ({ v: Boolean -> sel = if (v) s + c.id else s - c.id }) else null,
                            onClick = if (c.cantidad > 0) ({
                                onVer(Pantalla.Archivos(c.titulo, c.descripcion) {
                                    Motor.basura?.find { it.id == c.id }?.archivos ?: emptyList()
                                })
                            }) else null,
                        )
                    }
                }
                BarraAccion(
                    texto = if (elegidos.isEmpty()) "Nada seleccionado" else "Limpiar ${total.legible()}",
                    habilitado = elegidos.isNotEmpty(),
                    icono = Icons.Rounded.CleaningServices,
                ) { pendientes = elegidos.flatMap { it.archivos } }
            }
        }
    }
    FlujoBorrar(pendientes) { pendientes = null }
}
