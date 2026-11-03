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
        val isLogin = request.url.encodedPath.endsWith("/auth/login")
        if (isAuthFailure && !isLogin) {
            scope.launch { tokenManager.clearToken() }
        }
        return response
    }
}
