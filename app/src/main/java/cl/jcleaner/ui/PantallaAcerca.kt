package cl.jcleaner.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

const val AUTOR = "jcarlosrt"
const val REPO = "https://github.com/jcarlosrt/jcleaner"

private fun abrirWeb(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, url, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun PantallaAcerca(onAtras: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Acerca de", onAtras = onAtras)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("J", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.size(14.dp))
                    Text("JCLEANER", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("Versión $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(10.dp))
                    Text("Creado por", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(AUTOR, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Tarjeta {
                    Punto(Icons.Rounded.Shield, "Hace lo que dice, y nada más",
                        "Sin anuncios, sin cuentas y sin permiso de internet: la app no puede enviar tus datos a ningún lado. " +
                            "Nada se borra sin que lo confirmes, y te dice con honestidad lo que Android no permite hacer.")
                    Spacer(Modifier.size(14.dp))
                    Punto(Icons.Rounded.Code, "Código abierto",
                        "Muchas apps \"limpiadoras\" muestran números inventados. El código de JCLEANER es público: " +
                            "cualquiera puede revisar cómo funciona y ayudar a mejorarlo.")
                    Spacer(Modifier.size(14.dp))
                    Punto(Icons.Rounded.Gavel, "Licencia GPL-3.0",
                        "Puedes usarla, copiarla y modificarla. Quien publique una versión modificada debe compartir también su código " +
                            "bajo la misma licencia y mantener el crédito del autor.")
                }
            }
            item {
                Button(onClick = { abrirWeb(ctx, REPO) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.Code, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ver el código en GitHub")
                }
            }
            item {
                OutlinedButton(onClick = { abrirWeb(ctx, "$REPO/issues") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.BugReport, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reportar un problema o sugerir una mejora")
                }
            }
            item {
                Text(
                    "© 2026 $AUTOR. Distribuido bajo la licencia GNU GPL v3.0, sin ninguna garantía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Punto(icono: ImageVector, titulo: String, texto: String) {
    Row {
        Icon(icono, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
