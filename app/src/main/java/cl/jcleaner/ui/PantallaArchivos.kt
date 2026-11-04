package cl.jcleaner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.Archivo
import cl.jcleaner.util.legible

private const val DIA = 86_400_000L

@Composable
fun PantallaArchivos(p: Pantalla.Archivos, onAtras: () -> Unit) {
    val todos = p.obtener()
    var edad by remember { mutableIntStateOf(0) }
    var orden by remember { mutableIntStateOf(0) }
    var soloEnviados by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var pendientes by remember { mutableStateOf<List<Archivo>?>(null) }
    var vista by remember { mutableStateOf<Archivo?>(null) }

    val ahora = remember { System.currentTimeMillis() }
    val hayEnviados = remember(todos) { todos.any { it.etiqueta == "Enviado" } }
    val esGaleria = remember(todos) { todos.isNotEmpty() && todos.count { it.esMedia } * 2 > todos.size }
    val lista = remember(todos, edad, orden, soloEnviados) {
        todos.filter { (edad == 0 || it.fecha < ahora - edad * DIA) && (!soloEnviados || it.etiqueta == "Enviado") }
            .let { l ->
                when (orden) {
                    0 -> l.sortedByDescending { it.tamano }
                    1 -> l.sortedBy { it.fecha }
                    else -> l.sortedByDescending { it.fecha }
                }
            }
    }
    val elegidos = lista.filter { it.ruta in sel }
    val todosMarcados = lista.isNotEmpty() && elegidos.size == lista.size

    Column(Modifier.fillMaxSize()) {
        Encabezado(p.titulo, "${lista.size} archivos · ${lista.sumOf { it.tamano }.legible()}", onAtras)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Opcion("Todos", edad == 0) { edad = 0 }
            Opcion("+1 mes", edad == 30) { edad = 30 }
            Opcion("+3 meses", edad == 90) { edad = 90 }
            Opcion("+6 meses", edad == 180) { edad = 180 }
            Opcion("+1 año", edad == 365) { edad = 365 }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Opcion("Más pesados", orden == 0) { orden = 0 }
            Opcion("Más antiguos", orden == 1) { orden = 1 }
            Opcion("Más recientes", orden == 2) { orden = 2 }
            if (hayEnviados) Opcion("Solo enviados por mí", soloEnviados) { soloEnviados = !soloEnviados }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { sel = if (todosMarcados) emptySet() else sel + lista.map { it.ruta } }) {
                Text(if (todosMarcados) "Quitar selección" else "Seleccionar todo")
            }
            Spacer(Modifier.weight(1f))
            if (elegidos.isNotEmpty()) {
                Text("${elegidos.size} marcados", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
            }
        }

        if (lista.isEmpty()) {
            Column(Modifier.weight(1f)) { Vacio(Icons.Rounded.TaskAlt, "No hay archivos con este filtro") }
        } else if (esGaleria) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(106.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(lista.size, key = { lista[it].ruta }) { i ->
                    val a = lista[i]
                    CeldaMedia(
                        a, a.ruta in sel,
                        onToggle = { sel = if (a.ruta in sel) sel - a.ruta else sel + a.ruta },
                        onVer = { vista = a },
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(lista, key = { it.ruta }) { a ->
                    FilaArchivo(a, a.ruta in sel, onToggle = { sel = if (a.ruta in sel) sel - a.ruta else sel + a.ruta }, onVer = { vista = a })
                }
            }
        }
        BarraAccion(
            texto = if (elegidos.isEmpty()) "Marca lo que quieras borrar" else "Borrar ${elegidos.size} · ${elegidos.sumOf { it.tamano }.legible()}",
            habilitado = elegidos.isNotEmpty(),
        ) { pendientes = elegidos }
    }
    FlujoBorrar(pendientes) { r ->
        pendientes = null
        if (r != null) sel = sel - r.rutas
    }
    VistaPrevia(vista) { vista = null }
}
