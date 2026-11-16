package cl.jcleaner.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda secretos (contraseñas de aplicación, sesiones de Microsoft) cifrados con una clave
 * del almacén seguro de Android. La clave nunca sale del teléfono y no se incluye en respaldos.
 */
object Boveda {
    private const val ALIAS = "jcleaner_boveda"
    private const val PREFS = "jcleaner_boveda"

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun guardar(ctx: Context, nombre: String, valor: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, clave())
        val datos = c.iv + c.doFinal(valor.toByteArray(Charsets.UTF_8))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(nombre, Base64.encodeToString(datos, Base64.NO_WRAP)).apply()
    }

    fun leer(ctx: Context, nombre: String): String? = try {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(nombre, null)?.let { b64 ->
            val datos = Base64.decode(b64, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, datos, 0, 12))
            String(c.doFinal(datos, 12, datos.size - 12), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    fun borrar(ctx: Context, nombre: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(nombre).apply()
    }
}
