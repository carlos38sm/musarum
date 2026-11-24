package com.musarum.mobile.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musarum.mobile.LocalAppContainer

/**
 * BottomSheet in-app con la lista de dispositivos de salida de audio.
 * Reemplaza al picker del sistema que Carlos no quería (pedido nov-24-pm).
 *
 *  - Lee AudioManager.getDevices(GET_DEVICES_OUTPUTS) y filtra los que
 *    no sirven como "destino" (telephony, tv-tuner, docks sin audio…).
 *  - La selección se escribe en {@code AppContainer.preferredAudioDeviceId}
 *    y el PlayerService llama ExoPlayer.setPreferredAudioDevice al que
 *    coincida.
 *  - Se auto-refresca cuando conectas/desconectas un dispositivo gracias
 *    al AudioDeviceCallback (cuelga del DisposableEffect).
 *
 * Nota: Android NO permite a apps no-system forzar la salida del sistema
 * entero; solo elegir el destino PREFERIDO de un AudioTrack concreto (el
 * del ExoPlayer de Musarum). Con este botón, la música de Musarum va al
 * dispositivo elegido; el resto de audios del móvil mantiene su routing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputSheet(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedId by container.preferredAudioDeviceId.collectAsState()

    var devices by remember { mutableStateOf(listDevices(ctx)) }
    DisposableEffect(ctx) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val callback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                devices = listDevices(ctx)
            }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                devices = listDevices(ctx)
            }
        }
        am.registerAudioDeviceCallback(callback, null)
        onDispose { am.unregisterAudioDeviceCallback(callback) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Salida de audio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            // Opción "Automático" — pasa null al Service → vuelve al routing
            // por defecto de Android.
            DeviceRow(
                label = "Automático",
                sublabel = "Deja decidir al sistema",
                icon = Icons.Filled.PhoneAndroid,
                selected = selectedId == null,
                onClick = {
                    container.preferredAudioDeviceId.value = null
                    onDismiss()
                }
            )
            devices.forEach { d ->
                DeviceRow(
                    label = displayName(d),
                    sublabel = sublabelFor(d),
                    icon = iconFor(d),
                    selected = selectedId == d.id,
                    onClick = {
                        container.preferredAudioDeviceId.value = d.id
                        onDismiss()
                    }
                )
            }
            if (devices.isEmpty()) {
                Text(
                    "Conecta unos auriculares o enciende Bluetooth para ver más dispositivos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    label: String,
    sublabel: String?,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
             else androidx.compose.ui.graphics.Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!sublabel.isNullOrBlank()) {
                Text(
                    sublabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Activo",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    // Tintamos la fila entera si está seleccionada (pintamos un Box detrás
    // con el color del bg). El `bg` ya se calculó pero Compose aplica a
    // los modifiers del Row — no al ancho total. Overlay fino para el efecto.
    if (bg != androidx.compose.ui.graphics.Color.Transparent) {
        // No-op: la indicación la da el círculo del icono + check, bg
        // queda reservado por si lo queremos usar más adelante.
    }
}

private fun listDevices(context: Context): List<AudioDeviceInfo> {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { isUserFacing(it) }
        // Deduplicamos por nombre + tipo: algunos vendors muestran el
        // mismo altavoz interno dos veces (speaker + media speaker).
        .distinctBy { "${it.type}-${it.productName}" }
        // Orden: altavoz móvil arriba, auriculares, luego BT/Cast.
        .sortedBy { priorityOf(it) }
}

private fun isUserFacing(d: AudioDeviceInfo): Boolean {
    val keep = setOf(
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_USB_DEVICE
    )
    return d.type in keep
}

private fun priorityOf(d: AudioDeviceInfo): Int = when (d.type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 0
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> 1
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE -> 2
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> 3
    else -> 4
}

private fun displayName(d: AudioDeviceInfo): String {
    // productName suele ser el nombre comercial del dispositivo (p. ej.
    // "Alexa", "WH-1000XM4", "Galaxy Buds"). Si viene vacío, caemos al
    // tipo humano. Convertimos CharSequence? → String seguro.
    val product = d.productName?.toString()?.trim().orEmpty()
    if (product.isNotBlank() && !product.equals("android", ignoreCase = true)) {
        return product
    }
    return when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz del móvil"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Auriculares con cable"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Auriculares con mic"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "Auriculares USB"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "Dispositivo USB"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (llamada)"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Auriculares BLE"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Altavoz BLE"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI"
        AudioDeviceInfo.TYPE_AUX_LINE -> "Auxiliar"
        else -> "Salida de audio"
    }
}

private fun sublabelFor(d: AudioDeviceInfo): String? = when (d.type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> null
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Cable"
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI"
    else -> null
}

private fun iconFor(d: AudioDeviceInfo): ImageVector = when (d.type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Icons.Filled.PhoneAndroid
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET -> Icons.Filled.Headset
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> Icons.Filled.Bluetooth
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> Icons.Filled.Cast
    else -> Icons.Filled.Speaker
}
