package com.musarum.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import com.musarum.mobile.navigation.MusarumNavHost
import com.musarum.mobile.ui.theme.MusarumTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    // Nota ciclo de vida (nov 2026): antes conectábamos en onStart y
    // desconectábamos en onStop. Al bloquear la pantalla eso tiraba el
    // MediaController cliente y al desbloquear había que reconstruirlo con
    // un buildAsync asíncrono — durante esos ms, los taps al play/pause se
    // encolaban pero si la reconexión fallaba (service ocupado) la acción
    // se perdía y "el botón no hacía nada". Ahora conectamos UNA vez en
    // onCreate y mantenemos la conexión viva mientras la Activity exista;
    // sincronizamos en onStart por si volvemos de un stop con cambios en
    // el service, y solo desconectamos del todo en onDestroy. El service
    // sigue siendo foreground y no se para, así que el canal queda OK.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val container = (application as MusarumApplication).container
        container.playerController.connect()
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                MusarumTheme {
                    MusarumNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Red de seguridad por si Android reactivó la Activity con la
        // conexión muerta (p. ej. restore tras OOM): PlayerController hace
        // no-op si ya está conectado al service.
        (application as MusarumApplication).container.playerController.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Solo soltamos la conexión al servicio cuando la Activity muere
        // de verdad. Barrer la app → onDestroy; el service se cierra por
        // su propio onTaskRemoved (ver PlayerService).
        (application as MusarumApplication).container.playerController.disconnect()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
