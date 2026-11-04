package cl.jcleaner.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.Locale

fun Long.legible(): String {
    if (this < 1024) return "$this B"
    val unidades = arrayOf("KB", "MB", "GB", "TB")
    var v = this / 1024.0
    var i = 0
    while (v >= 1024 && i < unidades.size - 1) { v /= 1024; i++ }
    return if (v >= 100) String.format(Locale.getDefault(), "%.0f %s", v, unidades[i])
    else String.format(Locale.getDefault(), "%.1f %s", v, unidades[i])
}

fun hace(ms: Long): String {
    val dias = (System.currentTimeMillis() - ms) / 86_400_000L
    return when {
        dias < 1 -> "hoy"
        dias == 1L -> "ayer"
        dias < 30 -> "hace $dias días"
        dias < 365 -> "hace ${dias / 30} ${if (dias / 30 == 1L) "mes" else "meses"}"
        else -> "hace ${dias / 365} ${if (dias / 365 == 1L) "año" else "años"}"
    }
}

fun duracion(ms: Long): String {
    val h = ms / 3_600_000
    return if (h >= 24) "${h / 24} d ${h % 24} h" else "$h h ${(ms / 60_000) % 60} min"
}

object Permisos {

    fun accesoArchivos(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun abrirAccesoArchivos(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        } catch (e: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    fun accesoUso(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val modo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return if (modo == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            modo == AppOpsManager.MODE_ALLOWED
        }
    }

    fun abrirAccesoUso(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        } catch (e: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    /** Abre la pantalla "Información de la app", donde está Almacenamiento → Borrar caché. */
    fun abrirInfoApp(ctx: Context, paquete: String) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", paquete, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun abrirApp(ctx: Context, paquete: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(paquete) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

/** Preferencias de la app. */
object Ajustes {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("jcleaner", Context.MODE_PRIVATE)
    fun usarPapelera(ctx: Context) = prefs(ctx).getBoolean("usar_papelera", true)
    fun setUsarPapelera(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("usar_papelera", v).apply()
    fun recordatorio(ctx: Context) = prefs(ctx).getBoolean("recordatorio", true)
    fun setRecordatorio(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("recordatorio", v).apply()
    fun registroBateria(ctx: Context) = prefs(ctx).getBoolean("registro_bateria", true)
    fun setRegistroBateria(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("registro_bateria", v).apply()
}
