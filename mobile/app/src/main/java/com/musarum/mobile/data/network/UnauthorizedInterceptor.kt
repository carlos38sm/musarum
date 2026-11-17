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
        // CLAVE 1: un 401 solo invalida la sesión si la petición LLEVABA token.
        // Si no llevaba, el 401 significa "esta ruta exige auth y tú no
        // mandaste nada" — no implica que el token guardado sea inválido.
        // Sin esta guarda, un backend viejo que devolviese 401 a rutas
        // públicas nuevas (/version, /auth/users, etc.) limpiaba el token
        // aunque el user acabase de loguear → vuelta en bucle al Login.
        val authHeader = request.header("Authorization")
        val hadToken = authHeader != null
        val path = request.url.encodedPath
        // Rutas públicas /auth/** (login, users-picker, …) y /version
        // son permitAll en el backend → un 401 ahí es siempre "backend
        // viejo o roto", no "token caducado", y no deberíamos limpiar la
        // sesión del usuario por eso.
        val isPublicRoute = path.contains("/auth/") || path.endsWith("/version")
        // CLAVE 2: un 401 cuyo Bearer ya NO coincide con el token actual es
        // un "zombi": petición que salió antes del último login con el token
        // viejo (expirado o firmado con otra clave), cuya respuesta llega
        // después de que el usuario haya vuelto a entrar. Sin esta guarda el
        // interceptor limpiaba el NUEVO token y mandaba al usuario al Login
        // justo después de meter la contraseña — bug "igual que la otra vez".
        val requestToken = authHeader?.removePrefix("Bearer ")?.trim()
        val currentToken = tokenManager.currentToken()
        val tokenMatchesCurrent = requestToken != null && requestToken == currentToken
        if (isAuthFailure && hadToken && !isPublicRoute && tokenMatchesCurrent) {
            scope.launch { tokenManager.clearToken() }
        }
        return response
    }
}
