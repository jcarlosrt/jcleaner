package cl.jcleaner.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import cl.jcleaner.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Inicio de sesión con la página oficial de Microsoft (OAuth 2.0 con PKCE), necesario para Outlook/Hotmail.
 * JCLEANER nunca ve tu contraseña de Microsoft: solo recibe un permiso para leer y ordenar tu correo por IMAP.
 */
object MicrosoftAuth {
    private val CONFIG = AuthorizationServiceConfiguration(
        Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
        Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/token"),
    )
    const val REDIRECCION = "cl.jcleaner://oauth2redirect"
    private val PERMISOS = listOf("https://outlook.office.com/IMAP.AccessAsUser.All", "offline_access", "openid", "email", "profile")

    val disponible: Boolean get() = BuildConfig.MS_CLIENT_ID.isNotBlank()

    fun intentInicio(ctx: Context): Intent {
        val pedido = AuthorizationRequest.Builder(CONFIG, BuildConfig.MS_CLIENT_ID, ResponseTypeValues.CODE, Uri.parse(REDIRECCION))
            .setScopes(PERMISOS)
            .setPrompt("select_account")
            .build()
        val servicio = AuthorizationService(ctx)
        try {
            return servicio.getAuthorizationRequestIntent(pedido)
        } finally {
            servicio.dispose()
        }
    }

    /** Termina el inicio de sesión. Devuelve el correo de la cuenta y la sesión (JSON) para guardarla cifrada. */
    suspend fun completar(ctx: Context, datos: Intent?): Pair<String, String> {
        if (datos == null) throw ErrorCorreo("Se canceló el inicio de sesión.")
        val respuesta = AuthorizationResponse.fromIntent(datos)
        val error = AuthorizationException.fromIntent(datos)
        if (respuesta == null) throw ErrorCorreo("Microsoft no completó el inicio de sesión. ${error?.errorDescription ?: ""}".trim())
        val estado = AuthState(respuesta, error)
        val servicio = AuthorizationService(ctx)
        try {
            val token = suspendCancellableCoroutine { cont ->
                servicio.performTokenRequest(respuesta.createTokenExchangeRequest()) { r, e ->
                    if (r != null) cont.resume(r) else cont.resumeWithException(ErrorCorreo("Microsoft rechazó el acceso: ${e?.errorDescription ?: e?.message}"))
                }
            }
            estado.update(token, null)
            val correo = correoDe(token.idToken) ?: throw ErrorCorreo("No pude saber qué correo es esta cuenta de Microsoft.")
            return correo to estado.jsonSerializeString()
        } finally {
            servicio.dispose()
        }
    }

    private fun correoDe(idToken: String?): String? = try {
        val partes = idToken!!.split('.')
        val o = JSONObject(String(Base64.decode(partes[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
        (o.optString("email").ifBlank { o.optString("preferred_username") }).lowercase().takeIf { "@" in it }
    } catch (e: Exception) {
        null
    }

    /** Token de acceso vigente (lo renueva solo cuando vence) para entrar por IMAP. */
    suspend fun tokenVigente(ctx: Context, c: CuentaCorreo): String {
        val json = Boveda.leer(ctx, c.claveSecreto) ?: throw ErrorCorreo("No encontré la sesión de Microsoft. Quita la cuenta y vuelve a agregarla.")
        val estado = AuthState.jsonDeserialize(json)
        val servicio = AuthorizationService(ctx)
        try {
            val token = suspendCancellableCoroutine { cont ->
                estado.performActionWithFreshTokens(servicio) { acceso, _, e ->
                    if (acceso != null) cont.resume(acceso)
                    else cont.resumeWithException(ErrorCorreo("La sesión de Microsoft venció. Quita la cuenta y vuelve a iniciar sesión. (${e?.errorDescription ?: e?.message})"))
                }
            }
            Boveda.guardar(ctx, c.claveSecreto, estado.jsonSerializeString())
            return token
        } finally {
            servicio.dispose()
        }
    }
}
