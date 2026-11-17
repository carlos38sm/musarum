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

    private companion object {
        // 10 s de gracia tras un saveToken. Suficiente para absorber peticiones
        // en vuelo de un ciclo anterior o retrasos puntuales del backend sin
        // dejar la sesión "zombi" eternamente si el token nuevo fuese inválido.
        const val GRACE_PERIOD_MS = 10_000L
    }

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
        // CLAVE 3: ventana de gracia tras un login. Si el usuario acaba de
        // meter la contraseña y una 401 cae en los primeros segundos, casi
        // seguro es una petición del ciclo anterior o una sincronización
        // que llegó a destiempo. Un bounce instantáneo al Login es la peor
        // UX posible — si la contraseña fuese mala, el backend lo habría
        // rechazado en /auth/login y nunca habríamos llegado aquí.
        val inGracePeriod = tokenManager.msSinceLastSave() < GRACE_PERIOD_MS
        if (isAuthFailure && hadToken && !isPublicRoute && tokenMatchesCurrent && !inGracePeriod) {
            scope.launch { tokenManager.clearToken() }
        }
        return response
    }
}
