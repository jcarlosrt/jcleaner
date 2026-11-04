package cl.jcleaner.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.Archivo
import cl.jcleaner.data.GrupoDuplicados
import cl.jcleaner.data.Motor
import cl.jcleaner.util.legible

@Composable
fun PantallaDuplicados() = ConAccesoArchivos {
    val ctx = LocalContext.current
    var modo by remember { mutableIntStateOf(0) } // 0 = fotos, 1 = cualquier archivo
    var umbral by remember { mutableIntStateOf(6) }
    var vista by remember { mutableStateOf<Archivo?>(null) }
    var pendientes by remember { mutableStateOf<List<Archivo>?>(null) }

    val grupos = if (modo == 0) Motor.gruposFotos else Motor.gruposArchivos
    val prog = if (modo == 0) Motor.progFotos else Motor.progArchivos
    // por defecto se marcan todas las copias menos la mejor de cada grupo
    var sel by remember(grupos) { mutableStateOf(grupos.orEmpty().flatMap { g -> g.archivos.drop(1).map { it.ruta } }.toSet()) }
    val buscar = { if (modo == 0) Motor.buscarFotos(ctx.applicationContext, umbral) else Motor.buscarArchivos() }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Repetidos", "Fotos iguales o muy parecidas, y archivos duplicados")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcion("Fotos", modo == 0) { modo = 0 }
            Opcion("Todos los archivos", modo == 1) { modo = 1 }
        }
        if (modo == 0) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Similitud:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Opcion("Iguales", umbral == 0) { umbral = 0 }
                Opcion("Muy parecidas", umbral == 6) { umbral = 6 }
                Opcion("Parecidas", umbral == 11) { umbral = 11 }
            }
        }
        when {
            prog != null -> Cargando(prog) { Motor.cancelar(if (modo == 0) "fotos" else "archivos") }
            grupos == null -> Vacio(
                if (modo == 0) Icons.Rounded.PhotoLibrary else Icons.Rounded.FileCopy,
                if (modo == 0) "Busca fotos repetidas: la misma foto guardada dos veces, reenviada por WhatsApp, o en otro tamaño."
                else "Busca archivos con exactamente el mismo contenido, aunque tengan otro nombre o estén en otra carpeta.",
                "Buscar",
            ) { buscar() }
            grupos.isEmpty() -> Vacio(Icons.Rounded.TaskAlt, "¡No encontré repetidos!", "Buscar de nuevo") { buscar() }
            else -> {
                val elegidos = grupos.flatMap { it.archivos }.filter { it.ruta in sel }
                val completos = grupos.count { g -> g.archivos.all { it.ruta in sel } }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${grupos.size} grupos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Puedes liberar ${grupos.sumOf { it.recuperable }.legible()}", color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { buscar() }) { Text("Buscar de nuevo") }
                        }
                        Text(
                            "Ya marqué las copias. Se conserva la de mejor calidad de cada grupo (la que dice \"Conservar\"). Toca una foto para cambiar la selección.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(grupos, key = { it.conservar.ruta }) { g ->
                        val alternar = { a: Archivo -> sel = if (a.ruta in sel) sel - a.ruta else sel + a.ruta }
                        if (modo == 0) GrupoFotos(g, sel, alternar) { vista = it } else GrupoArchivos(g, sel, alternar)
                    }
                }
                BarraAccion(
                    texto = if (elegidos.isEmpty()) "Nada marcado" else "Borrar ${elegidos.size} copias · ${elegidos.sumOf { it.tamano }.legible()}",
                    habilitado = elegidos.isNotEmpty(),
                ) { pendientes = elegidos }
                if (pendientes != null) {
                    FlujoBorrar(
                        pendientes,
                        aviso = if (completos > 0) "Ojo: en $completos ${if (completos == 1) "grupo marcaste" else "grupos marcaste"} todas las copias, no quedará ninguna." else null,
                    ) { pendientes = null }
                }
            }
        }
    }
    VistaPrevia(vista) { vista = null }
}

@Composable
private fun GrupoFotos(g: GrupoDuplicados, sel: Set<String>, onToggle: (Archivo) -> Unit, onVer: (Archivo) -> Unit) {
    Tarjeta {
        Text(
            "${if (g.identicos) "Idénticas" else "Parecidas"} · ${g.archivos.size} fotos · ${g.recuperable.legible()}",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(g.archivos, key = { it.ruta }) { a ->
                Column(Modifier.width(112.dp)) {
                    CeldaMedia(
                        a, a.ruta in sel, onToggle = { onToggle(a) }, onVer = { onVer(a) },
                        modifier = Modifier.size(112.dp),
                        insignia = if (a == g.conservar) "Conservar" else null,
                    )
                    Text(
                        if (a.ancho > 0) "${a.ancho}×${a.alto}" else a.nombre,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Text(
                        a.file.parentFile?.name ?: "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun GrupoArchivos(g: GrupoDuplicados, sel: Set<String>, onToggle: (Archivo) -> Unit) {
    Tarjeta {
        Text(
            "${g.archivos.size} copias de ${g.conservar.nombre} · ${g.conservar.tamano.legible()} cada una",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        g.archivos.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FilaArchivo(a, a.ruta in sel, onToggle = { onToggle(a) })
                }
                if (a == g.conservar) {
                    Text("Original", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
