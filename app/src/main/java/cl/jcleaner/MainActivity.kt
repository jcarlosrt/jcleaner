package cl.jcleaner

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.jcleaner.data.Motor
import cl.jcleaner.data.Programador
import cl.jcleaner.data.RegistroBateria
import cl.jcleaner.ui.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import cl.jcleaner.ui.JCleanerTheme
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class JCleanerApp : Application(), ImageLoaderFactory {
    // miniaturas de fotos y videos
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Motor.alIniciar(applicationContext)
        Programador.programar(applicationContext)
        Motor.scope.launch(Dispatchers.IO) { RegistroBateria.anotar(applicationContext) }
        setContent {
            JCleanerTheme {
                App()
            }
        }
    }
}
