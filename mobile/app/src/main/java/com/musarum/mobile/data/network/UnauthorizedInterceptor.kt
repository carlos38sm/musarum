package com.musarum.mobile.data.network

import android.util.Log
import com.musarum.mobile.data.TokenManager
import kotlinx.coroutines.CoroutineScope
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Observa respuestas 401 solo para fines de log/diagnóstico.
 *
 * HISTÓRICO: antes limpiaba el token automáticamente ante cualquier 401 →
 * cualquier endpoint flaky (un cover que tarda, una ruta recién añadida
 * que el backend viejo no tiene, un race) sacaba al usuario al Login nada
 * más meter la contraseña. Añadimos tres guardas sucesivas (hadToken,
 * tokenMatchesCurrent, ventana de gracia de 10 s) y aun así Carlos seguía
 * cayendo al Login a los pocos segundos: siempre quedaba algún edge-case.
 *
 * POLÍTICA ACTUAL: NO hay logout automático. Si el JWT realmente expira o
 * el backend cambia la clave, el usuario verá errores de carga en Home y
 * puede volver a autenticar desde Perfil → Cerrar sesión. La ÚNICA ruta
 * para que {@code isAuthenticated} pase a false es que el usuario pulse
 * logout explícitamente. Mal menor: puede quedarse unos segundos viendo
 * "no se pudo cargar"; bien mayor: nunca más vuelve al Login sin querer.
 *
 * Mantenemos el interceptor como punto único donde loggear 401s para
 * depurar — no afecta al estado de la sesión en absoluto.
 */
class UnauthorizedInterceptor(
    @Suppress("UNUSED_PARAMETER") tokenManager: TokenManager,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401) {
            Log.w("Musarum", "401 en ${request.method} ${request.url.encodedPath} — no se limpia sesión")
        }
        return response
    }
}
