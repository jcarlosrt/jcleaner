package cl.jcleaner.ui

import android.media.MediaScannerConnection
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.EnPapelera
import cl.jcleaner.data.Historial
import cl.jcleaner.data.Motor
import cl.jcleaner.data.Papelera
import cl.jcleaner.util.hace
import cl.jcleaner.util.legible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaPapelera(onAtras: () -> Unit) = ConAccesoArchivos {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<EnPapelera>?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var vista by remember { mutableStateOf<EnPapelera?>(null) }
    var eliminar by remember { mutableStateOf<List<EnPapelera>?>(null) }
    var trabajando by remember { mutableStateOf(false) }

    LaunchedEffect(recarga) {
        items = withContext(Dispatchers.IO) { Papelera.listar() }.sortedByDescending { it.fecha }
        sel = sel.filter { id -> items.orEmpty().any { it.id == id } }.toSet()
        Motor.refrescarPapelera()
    }

    val lista = items
    val elegidos = lista.orEmpty().filter { it.id in sel }

    Column(Modifier.fillMaxSize()) {
        Encabezado(
            "Papelera",
            if (lista.isNullOrEmpty()) "Lo que borres queda aquí ${Papelera.DIAS} días"
            else "${lista.size} archivos · ${lista.sumOf { it.tamano }.legible()} · se borran solos a los ${Papelera.DIAS} días",
            onAtras,
        ) {
            if (!lista.isNullOrEmpty()) {
                IconButton(onClick = { eliminar = lista }, enabled = !trabajando) { Icon(Icons.Rounded.DeleteForever, "Vaciar papelera") }
            }
        }
        when {
            lista == null || trabajando -> Cargando("Un momento…")
            lista.isEmpty() -> Vacio(Icons.Rounded.DeleteOutline, "La papelera está vacía")
            else -> {
                TextButton(
                    onClick = { sel = if (sel.size == lista.size) emptySet() else lista.map { it.id }.toSet() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(if (sel.size == lista.size) "Quitar selección" else "Seleccionar todo") }
                LazyColumn(Modifier.weight(1f)) {
                    items(lista, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { sel = if (e.id in sel) sel - e.id else sel + e.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = e.id in sel, onCheckedChange = { sel = if (it) sel + e.id else sel - e.id })
                            Miniatura(e.comoArchivo(), Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).clickable { vista = e })
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                Text("${e.tamano.legible()} · borrado ${hace(e.fecha)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    e.original.substringBeforeLast('/').removePrefix("/storage/emulated/0"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    if (e.diasRestantes <= 1) "Hoy" else "${e.diasRestantes} días",
                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            trabajando = true
                            val aRestaurar = elegidos
                            Motor.scope.launch {
                                val rutas = withContext(Dispatchers.IO) { Papelera.restaurar(aRestaurar) }
                                if (rutas.isNotEmpty()) MediaScannerConnection.scanFile(ctx.applicationContext, rutas.toTypedArray(), null, null)
                                Toast.makeText(ctx, "Se restauraron ${rutas.size} archivos a su carpeta original", Toast.LENGTH_LONG).show()
                                trabajando = false
                                sel = emptySet()
                                recarga++
                            }
                        },
                        enabled = elegidos.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Restore, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Restaurar")
                    }
                    Button(
                        onClick = { eliminar = elegidos },
                        enabled = elegidos.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.DeleteForever, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Borrar")
                    }
                }
            }
        }
    }

    eliminar?.let { l ->
        AlertDialog(
            onDismissRequest = { eliminar = null },
            title = { Text(if (l.size == lista?.size) "¿Vaciar la papelera?" else "¿Borrar ${l.size} archivos para siempre?") },
            text = { Text("Se liberarán ${l.sumOf { it.tamano }.legible()}. Ya no se podrán recuperar.") },
            confirmButton = {
                Button(
                    onClick = {
                        eliminar = null
                        trabajando = true
                        Motor.scope.launch {
                            val bytes = withContext(Dispatchers.IO) { Papelera.eliminar(l) }
                            Historial.registrar(ctx.applicationContext, bytes, l.size, "Papelera vaciada")
                            Motor.liberadoTotal += bytes
                            Toast.makeText(ctx, "Liberaste ${bytes.legible()}", Toast.LENGTH_LONG).show()
                            trabajando = false
                            sel = emptySet()
                            recarga++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Borrar para siempre") }
            },
            dismissButton = { TextButton(onClick = { eliminar = null }) { Text("Cancelar") } },
        )
    }
    VistaPrevia(vista?.comoArchivo()) { vista = null }
}
