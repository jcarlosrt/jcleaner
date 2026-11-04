package cl.jcleaner.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.jcleaner.data.Archivo
import cl.jcleaner.data.Motor
import cl.jcleaner.data.Resultado
import cl.jcleaner.util.Permisos
import cl.jcleaner.util.hace
import cl.jcleaner.util.legible
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Ejecuta [accion] cada vez que vuelves a la app (por ejemplo, después de dar un permiso). */
@Composable
fun AlReanudar(accion: () -> Unit) {
    val actual by rememberUpdatedState(accion)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) actual() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
fun rememberPermiso(chequear: (Context) -> Boolean): Boolean {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(chequear(ctx)) }
    AlReanudar { ok = chequear(ctx) }
    return ok
}

/** Muestra [contenido] solo si hay acceso a los archivos; si no, explica y pide el permiso. */
@Composable
fun ConAccesoArchivos(contenido: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val ok = rememberPermiso(Permisos::accesoArchivos)
    var okAntiguo by remember { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        okAntiguo = r.values.all { it }
    }
    if (ok || okAntiguo) {
        contenido()
    } else {
        AvisoPermiso(
            icono = Icons.Rounded.FolderOpen,
            titulo = "Acceso a tus archivos",
            texto = "JCLEANER necesita permiso para revisar y borrar archivos: descargas, WhatsApp, fotos repetidas y basura.\n\nTodo se hace dentro de tu teléfono. Nada se sube a internet.",
            boton = "Dar permiso",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Permisos.abrirAccesoArchivos(ctx)
            else lanzador.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}

@Composable
fun AvisoPermiso(icono: ImageVector, titulo: String, texto: String, boton: String, onPedir: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icono, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.size(20.dp))
        Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.size(10.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(24.dp))
        Button(onClick = onPedir, modifier = Modifier.fillMaxWidth()) { Text(boton) }
    }
}

@Composable
fun Encabezado(
    titulo: String,
    subtitulo: String? = null,
    onAtras: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onAtras != null) 4.dp else 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAtras != null) {
            IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        acciones()
    }
}

@Composable
fun Cargando(texto: String, onCancelar: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(20.dp))
        Text(texto, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onCancelar != null) {
            Spacer(Modifier.size(12.dp))
            TextButton(onClick = onCancelar) { Text("Detener") }
        }
    }
}

@Composable
fun Vacio(icono: ImageVector, texto: String, boton: String? = null, onBoton: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icono, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(14.dp))
        Text(texto, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        if (boton != null) {
            Spacer(Modifier.size(16.dp))
            Button(onClick = onBoton) { Text(boton) }
        }
    }
}

@Composable
fun Tarjeta(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val colores = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = colores) {
            Column(Modifier.padding(16.dp), content = contenido)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = colores) {
            Column(Modifier.padding(16.dp), content = contenido)
        }
    }
}

@Composable
fun IconoRedondo(icono: ImageVector, fondo: Color = MaterialTheme.colorScheme.primaryContainer, tinte: Color = MaterialTheme.colorScheme.onPrimaryContainer, tam: Dp = 44.dp) {
    Box(Modifier.size(tam).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        Icon(icono, null, Modifier.size(tam * 0.5f), tint = tinte)
    }
}

/** Fila de una categoría: casilla opcional, ícono, textos y tamaño. */
@Composable
fun FilaCategoria(
    icono: ImageVector,
    titulo: String,
    descripcion: String,
    detalle: String,
    marcado: Boolean? = null,
    onMarcar: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Tarjeta(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (marcado != null) {
                Checkbox(checked = marcado, onCheckedChange = onMarcar, enabled = onMarcar != null)
            }
            IconoRedondo(icono)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detalle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Opcion(texto: String, seleccionada: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (seleccionada) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(texto, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BarraAccion(texto: String, habilitado: Boolean, icono: ImageVector = Icons.Rounded.Delete, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Button(
            onClick = onClick,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(icono, null)
            Spacer(Modifier.width(8.dp))
            Text(texto, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun ResumenGrande(bytes: Long, texto: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(bytes.legible(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Anillo(fraccion: Float, color: Color, modifier: Modifier = Modifier, grosor: Dp = 14.dp) {
    val pista = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val s = grosor.toPx()
        val tam = androidx.compose.ui.geometry.Size(size.width - s, size.height - s)
        val o = Offset(s / 2, s / 2)
        drawArc(color = pista, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = o, size = tam, style = Stroke(width = s, cap = StrokeCap.Round))
        drawArc(color = color, startAngle = 135f, sweepAngle = 270f * fraccion.coerceIn(0f, 1f), useCenter = false, topLeft = o, size = tam, style = Stroke(width = s, cap = StrokeCap.Round))
    }
}

@Composable
fun colorSegun(fraccionMala: Float): Color = when {
    fraccionMala > 0.9f -> Rojo
    fraccionMala > 0.75f -> Ambar
    else -> MaterialTheme.colorScheme.primary
}

fun iconoArchivo(a: Archivo): ImageVector = when {
    a.esCarpeta -> Icons.Rounded.FolderOff
    a.esImagen -> Icons.Rounded.Image
    a.esVideo -> Icons.Rounded.Movie
    a.esAudio -> Icons.Rounded.AudioFile
    a.ext == "apk" -> Icons.Rounded.Android
    a.ext == "pdf" -> Icons.Rounded.PictureAsPdf
    a.ext in setOf("zip", "rar", "7z") -> Icons.Rounded.FolderZip
    else -> Icons.Rounded.Description
}

@Composable
fun Miniatura(a: Archivo, modifier: Modifier = Modifier) {
    if (a.esMedia) {
        AsyncImage(model = a.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(iconoArchivo(a), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Vista ampliada de una foto o archivo. */
@Composable
fun VistaPrevia(a: Archivo?, onCerrar: () -> Unit) {
    if (a == null) return
    Dialog(onDismissRequest = onCerrar) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column {
                if (a.esMedia) {
                    AsyncImage(
                        model = a.file, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).background(Color.Black),
                    )
                }
                Column(Modifier.padding(16.dp)) {
                    Text(a.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val dims = if (a.ancho > 0) " · ${a.ancho}×${a.alto}" else ""
                    Text("${a.tamano.legible()}$dims · ${hace(a.fecha)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(a.file.parent ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = onCerrar, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
                }
            }
        }
    }
}

/** Confirma y borra. Llama a [onTerminar] con el resultado (o null si se canceló). */
@Composable
fun FlujoBorrar(pendientes: List<Archivo>?, aviso: String? = null, onTerminar: (Resultado?) -> Unit) {
    if (pendientes == null) return
    val ctx = LocalContext.current
    var borrando by remember(pendientes) { mutableStateOf(false) }
    val total = remember(pendientes) { pendientes.sumOf { it.tamano } }
    AlertDialog(
        onDismissRequest = { if (!borrando) onTerminar(null) },
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text(if (borrando) "Borrando…" else "¿Borrar ${pendientes.size} ${if (pendientes.size == 1) "elemento" else "elementos"}?") },
        text = {
            if (borrando) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Column {
                    Text("Se liberarán ${total.legible()}. Esta acción no se puede deshacer.")
                    if (aviso != null) Text(aviso, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            if (!borrando) {
                Button(
                    onClick = {
                        borrando = true
                        Motor.scope.launch {
                            val r = Motor.borrar(ctx.applicationContext, pendientes)
                            val extra = if (r.fallidos > 0) " · ${r.fallidos} no se pudieron borrar" else ""
                            Toast.makeText(ctx, "Liberaste ${r.bytes.legible()}$extra", Toast.LENGTH_LONG).show()
                            onTerminar(r)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Borrar") }
            }
        },
        dismissButton = {
            if (!borrando) TextButton(onClick = { onTerminar(null) }) { Text("Cancelar") }
        },
    )
}

/** Fila de una lista de archivos con casilla. */
@Composable
fun FilaArchivo(a: Archivo, marcado: Boolean, onToggle: () -> Unit, onVer: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = marcado, onCheckedChange = { onToggle() })
        Miniatura(a, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val partes = listOfNotNull(a.tamano.takeIf { !a.esCarpeta }?.legible(), hace(a.fecha), a.etiqueta)
            Text(partes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                a.file.parent?.removePrefix("/storage/emulated/0") ?: "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (onVer != null && a.esMedia) {
            IconButton(onClick = onVer) { Icon(Icons.Rounded.ZoomIn, "Ver") }
        }
    }
}

/** Celda cuadrada de una galería con casilla y botón para ampliar. */
@Composable
fun CeldaMedia(a: Archivo, marcado: Boolean, onToggle: () -> Unit, onVer: () -> Unit, modifier: Modifier = Modifier, insignia: String? = null) {
    val primario = MaterialTheme.colorScheme.primary
    Box(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle)) {
        Miniatura(a, Modifier.fillMaxSize())
        if (marcado) Box(Modifier.fillMaxSize().background(primario.copy(alpha = 0.28f)))
        if (a.esVideo) {
            Icon(Icons.Rounded.PlayCircle, null, Modifier.align(Alignment.Center).size(34.dp), tint = Color.White)
        }
        Icon(
            if (marcado) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp).clip(CircleShape)
                .background(if (marcado) Color.White else Color.Black.copy(alpha = 0.25f)),
            tint = if (marcado) primario else Color.White,
        )
        Box(
            Modifier.align(Alignment.BottomStart).padding(5.dp).size(28.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onVer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ZoomIn, "Ver", Modifier.size(18.dp), tint = Color.White)
        }
        Text(
            a.tamano.legible(),
            Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 5.dp, vertical = 1.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall,
        )
        if (insignia != null) {
            Text(
                insignia,
                Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(6.dp))
                    .background(primario).padding(horizontal = 6.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun MarcoInferior(contenido: @Composable () -> Unit) {
    Box(Modifier.navigationBarsPadding()) { contenido() }
}
