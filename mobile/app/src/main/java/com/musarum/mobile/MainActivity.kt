package com.musarum.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.musarum.mobile.navigation.MusarumNavHost
import com.musarum.mobile.ui.theme.MusarumTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    // State observable por Compose con el shortcut pendiente de procesar.
    // El launcher (long-press icono Musarum) lanza esta Activity con un
    // extra "musarum.shortcut" cuyo valor ("favorites", "downloads", …)
    // decide a qué ruta navegar. El NavHost lo lee y lo consume para que
    // una recomposición posterior no re-navegue.
    private var pendingShortcut by mutableStateOf<String?>(null)

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
        pendingShortcut = intent?.getStringExtra(EXTRA_SHORTCUT)
        val container = (application as MusarumApplication).container
        container.playerController.connect()
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                MusarumTheme {
                    MusarumNavHost(
                        pendingShortcut = pendingShortcut,
                        onShortcutConsumed = { pendingShortcut = null }
                    )
                }
            }
        }
    }

    // Launcher con launchMode=singleTop: si Musarum ya está en el top del
    // stack cuando el usuario toca un shortcut, Android reusa esta instancia
    // y entrega el nuevo intent aquí (no se crea otra Activity). Guardamos
    // el intent y actualizamos el state para que el NavHost reaccione.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShortcut = intent.getStringExtra(EXTRA_SHORTCUT)
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

    companion object {
        // Clave del extra que llevan los shortcuts definidos en res/xml/shortcuts.xml.
        const val EXTRA_SHORTCUT = "musarum.shortcut"
    }
}
