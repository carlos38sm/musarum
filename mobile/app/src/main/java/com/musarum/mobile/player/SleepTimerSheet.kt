package com.musarum.mobile.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * BottomSheet del temporizador de apagado. Si ya hay uno activo, muestra el
 * tiempo restante en vivo arriba y una opción "Cancelar"; si no, lista los
 * presets habituales (15/30/45/60/90 min) y la opción "Al terminar la
 * canción actual". `activeAt` es el timestamp absoluto al que apagará el
 * player (o null si no hay temporizador).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    activeAt: Long?,
    onDismiss: () -> Unit,
    onPickMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Temporizador de apagado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (activeAt != null) {
                ActiveTimerRow(activeAt = activeAt, onCancel = onCancel)
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "Nuevo temporizador",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            listOf(15, 30, 45, 60, 90).forEach { mins ->
                SheetAction(
                    icon = Icons.Filled.Timer,
                    label = "$mins minutos",
                    onClick = { onPickMinutes(mins) }
                )
            }
            SheetAction(
                icon = Icons.Filled.Timer,
                label = "Al terminar la canción",
                onClick = onEndOfTrack
            )
        }
    }
}

@Composable
private fun ActiveTimerRow(activeAt: Long, onCancel: () -> Unit) {
    // Ticker local a 1 Hz para pintar los minutos/segundos restantes sin
    // depender del progress ticker del PlayerController.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remainingSec = ((activeAt - now) / 1000L).coerceAtLeast(0L)
    val m = remainingSec / 60
    val s = remainingSec % 60

    // Dos filas en vez de una. Mezclar aquí un SheetAction (fillMaxWidth)
    // con una Column(weight 1f) aplasta el texto a un carácter por línea.
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                "Dormirá en %d:%02d".format(m, s),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "La música bajará volumen y pausará al llegar a 0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SheetAction(
            icon = Icons.Filled.AlarmOff,
            label = "Cancelar temporizador",
            destructive = true,
            onClick = onCancel
        )
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
