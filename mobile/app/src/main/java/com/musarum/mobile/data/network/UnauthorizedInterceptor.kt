package com.musarum.mobile.data.network

import com.musarum.mobile.data.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

class UnauthorizedInterceptor(
    private val tokenManager: TokenManager,
    private val scope: CoroutineScope
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 403 puede venir por content negotiation / permisos y NO significa
        // token inválido. Solo 401 Unauthorized dispara logout automático.
        val isAuthFailure = response.code == 401
        // CLAVE: un 401 solo invalida la sesión si la petición LLEVABA token.
        // Si no llevaba, el 401 significa "esta ruta exige auth y tú no
        // mandaste nada" — no implica que el token guardado sea inválido.
        // Sin esta guarda, un backend viejo que devolviese 401 a rutas
        // públicas nuevas (/version, /auth/users, etc.) limpiaba el token
        // aunque el user acabase de loguear → vuelta en bucle al Login.
        val hadToken = request.header("Authorization") != null
        val path = request.url.encodedPath
        // Rutas públicas /auth/** (login, users-picker, …) y /version
        // son permitAll en el backend → un 401 ahí es siempre "backend
        // viejo o roto", no "token caducado", y no deberíamos limpiar la
        // sesión del usuario por eso.
        val isPublicRoute = path.contains("/auth/") || path.endsWith("/version")
        if (isAuthFailure && hadToken && !isPublicRoute) {
            scope.launch { tokenManager.clearToken() }
        }
        return response
    }
}
