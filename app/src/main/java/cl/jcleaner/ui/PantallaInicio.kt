package cl.jcleaner.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import cl.jcleaner.data.Bateria
import cl.jcleaner.data.Motor
import cl.jcleaner.data.Salud
import cl.jcleaner.util.legible

/** Estado de la batería, actualizado en vivo. */
@Composable
fun rememberBateria(): Bateria {
    val ctx = LocalContext.current
    var b by remember {
        mutableStateOf(
            Salud.bateria(ctx, ContextCompat.registerReceiver(ctx, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        )
    }
    DisposableEffect(Unit) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { b = Salud.bateria(c, i) }
        }
        ContextCompat.registerReceiver(ctx, receptor, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(receptor) }
    }
    return b
}

@Composable
fun PantallaInicio(onIr: (Pantalla) -> Unit, onPestana: (Pantalla) -> Unit) {
    val ctx = LocalContext.current
    var equipo by remember { mutableStateOf(Salud.equipo(ctx)) }
    AlReanudar { equipo = Salud.equipo(ctx) }
    val bateria = rememberBateria()
    val puntaje = Salud.puntaje(bateria, equipo)

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Text("JCLEANER", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("${equipo.fabricante} ${equipo.modelo} · Android ${equipo.android}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                        Anillo(equipo.fraccionAlm, colorSegun(equipo.fraccionAlm), Modifier.size(132.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(equipo.fraccionAlm * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("usado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Almacenamiento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${equipo.almUsado.legible()} de ${equipo.almTotal.legible()}")
                        Text("Libre: ${equipo.almLibre.legible()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text("RAM: ${(equipo.fraccionRam * 100).toInt()}% en uso", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        if (Motor.liberadoTotal > 0) {
                            Text("Liberado hoy: ${Motor.liberadoTotal.legible()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        item {
            Tarjeta(onClick = { onIr(Pantalla.Salud) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoRedondo(
                        Icons.Rounded.MonitorHeart,
                        fondo = colorSegun(1f - puntaje / 100f).copy(alpha = 0.18f),
                        tinte = colorSegun(1f - puntaje / 100f),
                        tam = 52.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Salud del equipo: $puntaje/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                puntaje >= 85 -> "Tu teléfono está en buen estado"
                                puntaje >= 65 -> "Hay algunas cosas que mejorar"
                                else -> "Tu teléfono necesita atención"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Dato(if (bateria.cargando) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd, "${bateria.nivel}%", "Batería")
                    Dato(null, "${bateria.temperatura}°C", "Temperatura")
                    Dato(null, bateria.saludPorcentaje?.let { "$it%" } ?: bateria.salud, "Salud batería")
                }
            }
        }
        item {
            Text("Herramientas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        item {
            val b = Motor.basura
            Herramienta(Icons.Rounded.CleaningServices, "Archivos basura",
                if (b != null) "${b.filter { it.preseleccionada }.sumOf { it.tamano }.legible()} para limpiar" else "APK, temporales, carpetas vacías y archivos grandes") {
                onPestana(Pantalla.Basura)
            }
        }
        item {
            val c = Motor.chats
            Herramienta(Icons.Rounded.Forum, "WhatsApp y Telegram",
                if (c != null) "${c.sumOf { it.tamano }.legible()} en archivos de chats y grupos" else "Fotos, videos, audios y documentos de los chats") {
                onPestana(Pantalla.Chats)
            }
        }
        item {
            val g = Motor.gruposFotos
            Herramienta(Icons.Rounded.PhotoLibrary, "Fotos repetidas",
                if (g != null) "${g.sumOf { it.recuperable }.legible()} en ${g.size} grupos de repetidas" else "Fotos idénticas o muy parecidas, y archivos duplicados") {
                onPestana(Pantalla.Duplicados)
            }
        }
        item {
            val a = Motor.apps
            Herramienta(Icons.Rounded.Cached, "Caché de apps",
                if (a != null) "${a.sumOf { it.cache }.legible()} de caché en total" else "Mira cuánto caché tiene cada app y bórralo") {
                onPestana(Pantalla.Cache)
            }
        }
    }
}

@Composable
private fun Dato(icono: ImageVector?, valor: String, etiqueta: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Herramienta(icono: ImageVector, titulo: String, texto: String, onClick: () -> Unit) {
    Tarjeta(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoRedondo(icono)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
