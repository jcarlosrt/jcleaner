package cl.jcleaner.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.AppChat
import cl.jcleaner.data.CacheApps
import cl.jcleaner.data.Motor
import cl.jcleaner.util.Permisos
import cl.jcleaner.util.legible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun iconoChat(id: String): ImageVector = when (id) {
    "imagenes" -> Icons.Rounded.Image
    "videos" -> Icons.Rounded.Movie
    "voz" -> Icons.Rounded.Mic
    "audios" -> Icons.Rounded.MusicNote
    "documentos" -> Icons.Rounded.Description
    "videonotas" -> Icons.Rounded.Videocam
    "gifs" -> Icons.Rounded.Gif
    "stickers" -> Icons.Rounded.EmojiEmotions
    "estados" -> Icons.Rounded.Visibility
    "perfil" -> Icons.Rounded.Person
    else -> Icons.Rounded.GraphicEq
}

@Composable
fun PantallaChats(onVer: (Pantalla) -> Unit) = ConAccesoArchivos {
    val ctx = LocalContext.current
    val chats = Motor.chats
    val prog = Motor.progChats
    LaunchedEffect(Unit) { if (Motor.chats == null && Motor.progChats == null) Motor.analizarChats(ctx.applicationContext) }

    Column(Modifier.fillMaxSize()) {
        Encabezado("WhatsApp y Telegram", "Archivos que se acumulan en chats y grupos") {
            IconButton(onClick = { Motor.analizarChats(ctx.applicationContext) }, enabled = prog == null) {
                Icon(Icons.Rounded.Refresh, "Volver a analizar")
            }
        }
        when {
            prog != null -> Cargando(prog) { Motor.cancelar("chats") }
            chats == null -> Vacio(Icons.Rounded.Forum, "Revisa los archivos de tus chats", "Analizar") { Motor.analizarChats(ctx.applicationContext) }
            chats.isEmpty() -> Vacio(Icons.Rounded.Forum, "No encontré WhatsApp ni Telegram en este teléfono")
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ResumenGrande(chats.sumOf { it.tamano }, "en archivos de chats") }
                for (app in chats) {
                    item(key = "t-" + app.paquete) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(app.tamano.legible(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (app.categorias.isEmpty()) {
                        item(key = "v-" + app.paquete) {
                            Text(
                                if (app.instalada) "No hay archivos para limpiar." else "No está instalada.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    items(app.categorias, key = { app.paquete + "/" + it.id }) { c ->
                        FilaCategoria(
                            icono = iconoChat(c.id),
                            titulo = c.titulo,
                            descripcion = c.descripcion,
                            detalle = "${c.cantidad} archivos · ${c.tamano.legible()}",
                            onClick = {
                                onVer(Pantalla.Archivos("${app.nombre}: ${c.titulo}", c.descripcion) {
                                    Motor.chats?.find { it.paquete == app.paquete }?.categorias?.find { it.id == c.id }?.archivos ?: emptyList()
                                })
                            },
                        )
                    }
                    if (app.nombre == "Telegram" && app.instalada) {
                        item(key = "i-" + app.paquete) { TelegramInterno(app) }
                    }
                }
                item(key = "nota") {
                    Row(Modifier.padding(4.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "WhatsApp guarda juntos los archivos de todos los chats y grupos, separados solo por tipo. " +
                                "Dentro de cada tipo puedes filtrar por antigüedad (por ejemplo, más de 3 meses) y elegir qué borrar. " +
                                "Borrar aquí no borra los mensajes, solo el archivo descargado.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Telegram guarda casi todo en una carpeta privada: se muestra cuánto pesa y cómo borrarlo. */
@Composable
private fun TelegramInterno(app: AppChat) {
    val ctx = LocalContext.current
    val conUso = rememberPermiso(Permisos::accesoUso)
    var datos by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(conUso) {
        if (conUso) {
            datos = withContext(Dispatchers.IO) {
                CacheApps.estadisticas(ctx, app.paquete)?.let { it.dataBytes to it.cacheBytes }
            }
        }
    }
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoRedondo(Icons.Rounded.Lock, fondo = MaterialTheme.colorScheme.secondaryContainer, tinte = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Archivos internos de Telegram", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val d = datos
                Text(
                    when {
                        d != null -> "Telegram ocupa ${d.first.legible()} en su carpeta privada (caché: ${d.second.legible()})."
                        conUso -> "Midiendo…"
                        else -> "Activa el \"Acceso de uso\" en la pestaña Caché para ver cuánto ocupa."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "Android no deja que otras apps entren a esa carpeta. Para limpiarla: abre Telegram → Ajustes → Datos y almacenamiento → Uso del almacenamiento → Borrar caché.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                if (!Permisos.abrirApp(ctx, app.paquete)) Toast.makeText(ctx, "No pude abrir Telegram", Toast.LENGTH_SHORT).show()
            }) { Text("Abrir Telegram") }
            OutlinedButton(onClick = { Permisos.abrirInfoApp(ctx, app.paquete) }) { Text("Info de la app") }
        }
    }
}
