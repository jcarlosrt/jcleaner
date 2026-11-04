package cl.jcleaner.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import cl.jcleaner.data.Archivo
import cl.jcleaner.data.Motor

sealed interface Pantalla {
    data object Inicio : Pantalla
    data object Basura : Pantalla
    data object Chats : Pantalla
    data object Duplicados : Pantalla
    data object Cache : Pantalla
    data object Salud : Pantalla

    /** Lista de archivos de una categoría; [obtener] lee siempre la versión actual (después de borrar). */
    class Archivos(val titulo: String, val descripcion: String?, val obtener: () -> List<Archivo>) : Pantalla
}

private class Pestana(val pantalla: Pantalla, val texto: String, val icono: ImageVector)

@Composable
fun App() {
    val ctx = LocalContext.current
    val pila = remember { mutableStateListOf<Pantalla>(Pantalla.Inicio) }
    val actual = pila.last()

    fun ir(p: Pantalla) { pila.add(p) }
    fun pestana(p: Pantalla) { pila.clear(); pila.add(p) }
    val atras: () -> Unit = { if (pila.size > 1) pila.removeAt(pila.lastIndex) }

    BackHandler(enabled = pila.size > 1) { atras() }

    LaunchedEffect(Motor.error) {
        Motor.error?.let {
            Toast.makeText(ctx, "Ocurrió un problema: $it", Toast.LENGTH_LONG).show()
            Motor.error = null
        }
    }

    val pestanas = listOf(
        Pestana(Pantalla.Inicio, "Inicio", Icons.Rounded.Home),
        Pestana(Pantalla.Basura, "Limpiar", Icons.Rounded.CleaningServices),
        Pestana(Pantalla.Chats, "Chats", Icons.Rounded.Forum),
        Pestana(Pantalla.Duplicados, "Repetidos", Icons.Rounded.PhotoLibrary),
        Pestana(Pantalla.Cache, "Caché", Icons.Rounded.Cached),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                pestanas.forEach { t ->
                    NavigationBarItem(
                        selected = pila.first() == t.pantalla,
                        onClick = { pestana(t.pantalla) },
                        icon = { Icon(t.icono, null) },
                        label = { Text(t.texto, maxLines = 1) },
                    )
                }
            }
        },
    ) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize()) {
            when (val p = actual) {
                Pantalla.Inicio -> PantallaInicio(onIr = { ir(it) }, onPestana = { pestana(it) })
                Pantalla.Basura -> PantallaBasura(onVer = { ir(it) })
                Pantalla.Chats -> PantallaChats(onVer = { ir(it) })
                Pantalla.Duplicados -> PantallaDuplicados()
                Pantalla.Cache -> PantallaCache()
                Pantalla.Salud -> PantallaSalud(onAtras = atras)
                is Pantalla.Archivos -> PantallaArchivos(p, onAtras = atras)
            }
        }
    }
}
