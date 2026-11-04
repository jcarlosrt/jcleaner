package cl.jcleaner.data

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import kotlin.math.abs

data class Bateria(
    val nivel: Int,
    val estado: String,
    val cargando: Boolean,
    val salud: String,
    val saludBuena: Boolean,
    val temperatura: Float,
    val voltaje: Float,
    val tecnologia: String,
    val fuente: String,
    val ciclos: Int?,
    val corrienteMa: Int?,
    val cargaActualMah: Int?,
    val capacidadEstimadaMah: Int?,
    val capacidadDisenoMah: Int?,
) {
    /** Capacidad real estimada respecto de la original de fábrica (aproximado). */
    val saludPorcentaje: Int?
        get() = if (capacidadEstimadaMah != null && capacidadDisenoMah != null && capacidadDisenoMah > 0)
            (capacidadEstimadaMah * 100 / capacidadDisenoMah).coerceIn(1, 100) else null
}

data class Equipo(
    val fabricante: String,
    val modelo: String,
    val android: String,
    val sdk: Int,
    val ramTotal: Long,
    val ramLibre: Long,
    val almTotal: Long,
    val almLibre: Long,
    val nucleos: Int,
    val encendidoMs: Long,
    val termico: String?,
) {
    val almUsado: Long get() = almTotal - almLibre
    val fraccionAlm: Float get() = if (almTotal > 0) almUsado.toFloat() / almTotal else 0f
    val fraccionRam: Float get() = if (ramTotal > 0) (ramTotal - ramLibre).toFloat() / ramTotal else 0f
}

object Salud {

    fun bateria(ctx: Context, i: Intent?): Bateria {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val nivel = i?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) l * 100 / s else null
        } ?: bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val estado = when (st) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
            BatteryManager.BATTERY_STATUS_FULL -> "Carga completa"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Conectada, sin cargar"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "En uso"
            else -> "Desconocido"
        }
        val h = i?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val salud = when (h) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dañada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobrevoltaje"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Con falla"
            BatteryManager.BATTERY_HEALTH_COLD -> "Muy fría"
            else -> "Desconocida"
        }
        val fuente = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Cargador"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Inalámbrico"
            0 -> "Desconectado"
            else -> "Conectado"
        }

        // algunos equipos informan µAh y otros mAh
        val contador = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeIf { it > 0 && it != Int.MIN_VALUE }
        val cargaMah = contador?.let { if (it > 100_000) it / 1000 else it }
        val estimada = if (cargaMah != null && nivel in 15..100) cargaMah * 100 / nivel else null
        val corriente = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != 0 && it != Int.MIN_VALUE && it != Int.MAX_VALUE }
            ?.let { if (abs(it) > 20_000) it / 1000 else it }
        // EXTRA_CYCLE_COUNT existe desde Android 14
        val ciclos = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it >= 0 }

        return Bateria(
            nivel = nivel.coerceIn(0, 100),
            estado = estado,
            cargando = st == BatteryManager.BATTERY_STATUS_CHARGING,
            salud = salud,
            saludBuena = h == BatteryManager.BATTERY_HEALTH_GOOD || h == -1 || h == BatteryManager.BATTERY_HEALTH_UNKNOWN,
            temperatura = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltaje = (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f,
            tecnologia = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "—",
            fuente = fuente,
            ciclos = ciclos,
            corrienteMa = corriente,
            cargaActualMah = cargaMah,
            capacidadEstimadaMah = estimada,
            capacidadDisenoMah = capacidadDiseno(ctx),
        )
    }

    private var disenoCache: Int? = null
    private var disenoBuscado = false

    /** Capacidad de fábrica. Android no la expone oficialmente; se lee del perfil de energía del sistema si está. */
    private fun capacidadDiseno(ctx: Context): Int? {
        if (disenoBuscado) return disenoCache
        disenoBuscado = true
        disenoCache = try {
            val c = Class.forName("com.android.internal.os.PowerProfile")
            val perfil = c.getConstructor(Context::class.java).newInstance(ctx)
            (c.getMethod("getBatteryCapacity").invoke(perfil) as Double).toInt().takeIf { it in 800..20_000 }
        } catch (e: Throwable) {
            null
        }
        return disenoCache
    }

    fun equipo(ctx: Context): Equipo {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        var total: Long
        var libre: Long
        try {
            val ssm = ctx.getSystemService(StorageStatsManager::class.java)
            total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            libre = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
        } catch (e: Exception) {
            val sf = StatFs(Environment.getDataDirectory().path)
            total = sf.totalBytes
            libre = sf.availableBytes
        }
        val termico = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (ctx.getSystemService(PowerManager::class.java).currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Tibio"
                PowerManager.THERMAL_STATUS_MODERATE -> "Caliente"
                PowerManager.THERMAL_STATUS_SEVERE -> "Muy caliente"
                else -> "Crítico"
            }
        } else null
        return Equipo(
            fabricante = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            modelo = Build.MODEL,
            android = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            ramTotal = mi.totalMem,
            ramLibre = mi.availMem,
            almTotal = total,
            almLibre = libre,
            nucleos = Runtime.getRuntime().availableProcessors(),
            encendidoMs = SystemClock.elapsedRealtime(),
            termico = termico,
        )
    }

    /** Nota simple de 0 a 100 para mostrar en la portada. */
    fun puntaje(b: Bateria, e: Equipo): Int {
        var p = 100
        val alm = e.fraccionAlm
        p -= when {
            alm > 0.95f -> 25
            alm > 0.85f -> 12
            alm > 0.75f -> 5
            else -> 0
        }
        val ramLibre = if (e.ramTotal > 0) e.ramLibre.toFloat() / e.ramTotal else 1f
        p -= when {
            ramLibre < 0.10f -> 10
            ramLibre < 0.20f -> 4
            else -> 0
        }
        if (!b.saludBuena) p -= 25
        p -= when {
            b.temperatura > 45 -> 15
            b.temperatura > 40 -> 6
            else -> 0
        }
        b.saludPorcentaje?.let {
            p -= when {
                it < 70 -> 15
                it < 80 -> 8
                else -> 0
            }
        }
        return p.coerceIn(0, 100)
    }
}
