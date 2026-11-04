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
import cl.jcleaner.data.Salud
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
