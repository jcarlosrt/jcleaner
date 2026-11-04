package cl.jcleaner.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cl.jcleaner.MainActivity
import cl.jcleaner.R
import cl.jcleaner.util.Ajustes
import cl.jcleaner.util.Permisos
import cl.jcleaner.util.legible
import java.io.File
import java.util.concurrent.TimeUnit

// ------------------------------------------------------------------ historial de batería

data class MuestraBateria(val fecha: Long, val nivel: Int, val temperatura: Float, val cargando: Boolean)

/** Guarda nivel y temperatura de la batería cada ~15 minutos (solo en el teléfono, hasta 7 días). */
object RegistroBateria {
    private const val DIAS = 7
    private fun archivo(ctx: Context) = File(ctx.filesDir, "bateria.tsv")

    fun anotar(ctx: Context) {
        try {
            val i = ContextCompat.registerReceiver(ctx, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            val b = Salud.bateria(ctx, i)
            val f = archivo(ctx)
            f.appendText("${System.currentTimeMillis()}\t${b.nivel}\t${b.temperatura}\t${if (b.cargando) 1 else 0}\n")
            // recorta lo antiguo de vez en cuando
            if (f.length() > 64 * 1024) {
                val limite = System.currentTimeMillis() - DIAS * 86_400_000L
                val quedan = f.readLines().filter { (it.substringBefore('\t').toLongOrNull() ?: 0L) >= limite }
                f.writeText(quedan.joinToString("") { it + "\n" })
            }
        } catch (_: Exception) {
        }
    }

    fun leer(ctx: Context, desde: Long): List<MuestraBateria> = try {
        archivo(ctx).takeIf { it.exists() }?.readLines()?.mapNotNull { l ->
            val p = l.split('\t')
            if (p.size < 4) return@mapNotNull null
            val t = p[0].toLongOrNull() ?: return@mapNotNull null
            if (t < desde) null else MuestraBateria(t, p[1].toIntOrNull() ?: 0, p[2].toFloatOrNull() ?: 0f, p[3] == "1")
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Porcentaje promedio que baja por hora mientras no está cargando (null si hay pocos datos). */
    fun consumoPorHora(m: List<MuestraBateria>): Float? {
        var bajada = 0
        var horas = 0.0
        for (k in 1 until m.size) {
            val a = m[k - 1]
            val b = m[k]
            val dh = (b.fecha - a.fecha) / 3_600_000.0
            if (!a.cargando && !b.cargando && dh in 0.05..2.0 && b.nivel <= a.nivel) {
                bajada += a.nivel - b.nivel
                horas += dh
            }
        }
        return if (horas >= 2.0) (bajada / horas).toFloat() else null
    }
}

// ------------------------------------------------------------------ notificaciones

object Notificaciones {
    private const val CANAL = "jcleaner_recordatorio"

    fun mostrar(ctx: Context, titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CANAL, "Recordatorio de limpieza", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val abrir = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1, n)
        } catch (_: SecurityException) {
        }
    }
}

// ------------------------------------------------------------------ tareas programadas

/** Una vez por semana: vacía la papelera vencida y avisa si hay mucho por limpiar. */
class TrabajoRecordatorio(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        try {
            Papelera.purgarVencidas(c)
        } catch (_: Exception) {
        }
        if (!Ajustes.recordatorio(c) || !Permisos.accesoArchivos(c)) return Result.success()
        return try {
            val bytes = Escaner.basura { }.filter { it.preseleccionada }.sumOf { it.tamano }
            if (bytes >= 100L * 1024 * 1024) {
                Notificaciones.mostrar(c, "Puedes liberar ${bytes.legible()}", "JCLEANER encontró archivos basura. Toca para revisarlos.")
            }
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }
}

class TrabajoBateria(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (Ajustes.registroBateria(applicationContext)) RegistroBateria.anotar(applicationContext)
        return Result.success()
    }
}

object Programador {
    fun programar(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (Ajustes.recordatorio(ctx)) {
            val pedido = PeriodicWorkRequestBuilder<TrabajoRecordatorio>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
            wm.enqueueUniquePeriodicWork("recordatorio", ExistingPeriodicWorkPolicy.KEEP, pedido)
        } else {
            wm.cancelUniqueWork("recordatorio")
        }
        if (Ajustes.registroBateria(ctx)) {
            wm.enqueueUniquePeriodicWork(
                "bateria", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TrabajoBateria>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            wm.cancelUniqueWork("bateria")
        }
    }
}
