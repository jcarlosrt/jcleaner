package cl.jcleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.jcleaner.data.MuestraBateria
import cl.jcleaner.data.RegistroBateria
import cl.jcleaner.data.Salud
import cl.jcleaner.util.Ajustes
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cl.jcleaner.util.duracion
import cl.jcleaner.util.legible
import kotlinx.coroutines.delay

@Composable
fun PantallaSalud(onAtras: () -> Unit) {
    val ctx = LocalContext.current
    val b = rememberBateria()
    var e by remember { mutableStateOf(Salud.equipo(ctx)) }
    LaunchedEffect(Unit) {
        while (true) { delay(3000); e = Salud.equipo(ctx) }
    }
    val puntaje = Salud.puntaje(b, e)

    Column(Modifier.fillMaxSize()) {
        Encabezado("Salud del equipo", "Puntaje general: $puntaje/100", onAtras)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Tarjeta {
                    Titulo(Icons.Rounded.BatteryChargingFull, "Batería")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                            Anillo(b.nivel / 100f, colorSegun(1f - b.nivel / 100f), Modifier.size(110.dp), grosor = 12.dp)
                            Text("${b.nivel}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(b.estado, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Fuente: ${b.fuente}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            b.corrienteMa?.let {
                                Text(
                                    if (it > 0) "Entrando $it mA" else "Consumiendo ${-it} mA",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Fila("Estado de salud", b.salud, if (b.saludBuena) null else Rojo)
                    b.saludPorcentaje?.let { p ->
                        Fila("Capacidad restante (aprox.)", "$p%", if (p < 80) Ambar else null)
                    }
                    b.capacidadEstimadaMah?.let { Fila("Capacidad actual estimada", "$it mAh") }
                    b.capacidadDisenoMah?.let { Fila("Capacidad de fábrica", "$it mAh") }
                    Fila("Ciclos de carga", b.ciclos?.toString() ?: "No disponible en tu Android")
                    Fila(
                        "Temperatura", "${b.temperatura} °C",
                        when {
                            b.temperatura > 45 -> Rojo
                            b.temperatura > 40 -> Ambar
                            else -> null
                        },
                    )
                    Fila("Voltaje", "${b.voltaje} V")
                    Fila("Tecnología", b.tecnologia)
                    if (b.saludPorcentaje != null) {
                        Text(
                            "La capacidad se estima con lo que informa el teléfono; tómala como referencia. Es más precisa con la batería sobre 50%.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item { HistorialBateria() }
            item {
                Tarjeta {
                    Titulo(Icons.Rounded.SdStorage, "Almacenamiento")
                    Barra(e.fraccionAlm)
                    Fila("Usado", "${e.almUsado.legible()} de ${e.almTotal.legible()}")
                    Fila("Libre", e.almLibre.legible(), if (e.fraccionAlm > 0.9f) Rojo else null)
                }
            }
            item {
                Tarjeta {
                    Titulo(Icons.Rounded.Memory, "Memoria RAM")
                    Barra(e.fraccionRam)
                    Fila("En uso", "${(e.ramTotal - e.ramLibre).legible()} de ${e.ramTotal.legible()}")
                    Fila("Disponible", e.ramLibre.legible())
                }
            }
            item {
                Tarjeta {
                    Titulo(Icons.Rounded.PhoneAndroid, "Equipo")
                    Fila("Modelo", "${e.fabricante} ${e.modelo}")
                    Fila("Android", "${e.android} (API ${e.sdk})")
                    Fila("Procesador", "${e.nucleos} núcleos")
                    e.termico?.let { Fila("Temperatura del sistema", it, if (it != "Normal" && it != "Tibio") Ambar else null) }
                    Fila("Encendido hace", duracion(e.encendidoMs))
                }
            }
            item {
                val consejos = buildList {
                    if (e.fraccionAlm > 0.85f) add("Tu almacenamiento está casi lleno. Revisa Limpiar, Chats y Repetidos para liberar espacio.")
                    if (b.temperatura > 40) add("La batería está caliente. Evita usar el teléfono mientras carga y quítale la funda si se calienta mucho.")
                    if ((b.saludPorcentaje ?: 100) < 80) add("La batería perdió capacidad. Si dura poco, considera cambiarla.")
                    if (e.encendidoMs > 7L * 24 * 3_600_000) add("Hace más de una semana que no reinicias el teléfono. Reiniciar de vez en cuando lo mantiene más fluido.")
                    if (isEmpty()) add("Todo se ve bien. Para cuidar la batería, intenta mantenerla entre 20% y 80%.")
                }
                Tarjeta {
                    Titulo(Icons.Rounded.Lightbulb, "Consejos")
                    consejos.forEach { Text("• $it", modifier = Modifier.padding(vertical = 3.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Titulo(icono: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Icon(icono, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String, color: Color? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(etiqueta, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun Barra(fraccion: Float) {
    LinearProgressIndicator(
        progress = { fraccion },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(10.dp),
        color = colorSegun(fraccion),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
    )
}

/** Gráfico de las últimas 48 horas: nivel (línea) y momentos de carga (franjas). */
@Composable
private fun HistorialBateria() {
    val ctx = LocalContext.current
    var muestras by remember { mutableStateOf<List<MuestraBateria>>(emptyList()) }
    var semana by remember { mutableStateOf<List<MuestraBateria>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            val ahora = System.currentTimeMillis()
            semana = withContext(Dispatchers.IO) { RegistroBateria.leer(ctx, ahora - 7 * 86_400_000L) }
            muestras = semana.filter { it.fecha >= ahora - 48 * 3_600_000L }
            delay(60_000)
        }
    }
    Tarjeta {
        Titulo(Icons.Rounded.Timeline, "Historial de batería (48 horas)")
        if (!Ajustes.registroBateria(ctx)) {
            Text("El registro está apagado. Actívalo en Ajustes (ícono ⓘ en Inicio).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Tarjeta
        }
        if (muestras.size < 3) {
            Text(
                "Todavía hay pocos datos. JCLEANER anota el nivel de batería cada 15 minutos; vuelve en unas horas para ver el gráfico.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Tarjeta
        }
        val primario = MaterialTheme.colorScheme.primary
        val carga = MaterialTheme.colorScheme.secondaryContainer
        val rejilla = MaterialTheme.colorScheme.surfaceVariant
        val calor = Rojo
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)) {
            val t0 = muestras.first().fecha.toFloat()
            val t1 = maxOf(muestras.last().fecha.toFloat(), t0 + 1f)
            fun x(t: Long) = (t - t0) / (t1 - t0) * size.width
            fun y(nivel: Float) = size.height * (1f - nivel / 100f)
            for (k in 0..4) {
                val yy = size.height * k / 4f
                drawLine(rejilla, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
            }
            for (k in 1 until muestras.size) {
                val a = muestras[k - 1]
                if (a.cargando) drawRect(carga, topLeft = Offset(x(a.fecha), 0f), size = androidx.compose.ui.geometry.Size(x(muestras[k].fecha) - x(a.fecha), size.height))
            }
            val linea = Path()
            muestras.forEachIndexed { k, m -> if (k == 0) linea.moveTo(x(m.fecha), y(m.nivel.toFloat())) else linea.lineTo(x(m.fecha), y(m.nivel.toFloat())) }
            drawPath(linea, primario, style = Stroke(width = 3.dp.toPx()))
            // temperatura sobre 40 °C marcada en rojo
            muestras.filter { it.temperatura > 40f }.forEach { m -> drawCircle(calor, radius = 3.dp.toPx(), center = Offset(x(m.fecha), y(m.nivel.toFloat()))) }
        }
        Text("Línea: nivel de carga · franjas: cargando · puntos rojos: sobre 40 °C", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val consumo = RegistroBateria.consumoPorHora(semana)
        if (consumo != null) {
            Fila("Consumo promedio en uso", "${"%.1f".format(consumo)}% por hora")
            if (consumo > 0.1f) Fila("Duración estimada (de 100% a 0%)", "${(100f / consumo).toInt()} horas")
        }
        Fila("Temperatura máxima (7 días)", "${semana.maxOfOrNull { it.temperatura } ?: 0f} °C", if ((semana.maxOfOrNull { it.temperatura } ?: 0f) > 45f) Rojo else null)
    }
}
