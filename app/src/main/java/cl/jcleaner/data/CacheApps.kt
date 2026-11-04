package cl.jcleaner.data

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.storage.StorageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Mide el caché de cada app. Necesita "Acceso de uso".
 * Android no deja que una app borre el caché de otra: se abre la pantalla de la app para que lo borres tú.
 */
object CacheApps {

    private val iconos = ConcurrentHashMap<String, ImageBitmap>()

    fun estadisticas(ctx: Context, paquete: String): StorageStats? = try {
        val ssm = ctx.getSystemService(StorageStatsManager::class.java)
        ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, paquete, Process.myUserHandle())
    } catch (e: Exception) {
        null
    }

    suspend fun listar(ctx: Context): List<InfoApp> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val ssm = ctx.getSystemService(StorageStatsManager::class.java)
        val usuario = Process.myUserHandle()
        @Suppress("DEPRECATION")
        val apps = pm.getInstalledApplications(0)
        apps.mapNotNull { ai ->
            try {
                val st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, usuario)
                val icono = iconos[ai.packageName] ?: try {
                    pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap().also { iconos[ai.packageName] = it }
                } catch (e: Exception) {
                    null
                }
                val sistema = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                InfoApp(ai.packageName, pm.getApplicationLabel(ai).toString(), icono, st.appBytes, st.dataBytes, st.cacheBytes, sistema)
            } catch (e: Exception) {
                null
            }
        }
    }
}
