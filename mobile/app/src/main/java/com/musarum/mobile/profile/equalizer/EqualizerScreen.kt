package com.musarum.mobile.profile.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musarum.mobile.LocalAppContainer

/**
 * Pantalla de Ecualizador. 5 bandas verticales (sliders) con los centros
 * 60 Hz / 230 Hz / 910 Hz / 3.6 kHz / 14 kHz — distribución estándar que
 * cubre graves, medios y agudos. Rango ±15 dB en milibelios (−1500..+1500).
 *
 * El EQ real vive en PlayerService (enganchado al audio session del ExoPlayer).
 * Esta pantalla solo escribe en PlayerPreferences; el service observa el
 * flow y re-aplica las bandas en caliente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.playerPreferences
    val enabled by prefs.equalizerEnabled.collectAsState()
    val bands by prefs.equalizerBands.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ecualizador") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Activado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Ajusta graves, medios y agudos por banda.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { prefs.setEqualizerEnabled(it) })
            }

            // 5 sliders horizontales — más legibles que verticales rotados.
            // Cada uno con etiqueta de frecuencia a la izquierda y dB actual
            // a la derecha, rango ±15 dB.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BAND_LABELS.forEachIndexed { i, label ->
                    BandSlider(
                        label = label,
                        valueMb = bands.getOrNull(i) ?: 0,
                        enabled = enabled,
                        onChange = { newMb ->
                            val next = bands.toMutableList().apply { set(i, newMb) }
                            prefs.setEqualizerBands(next)
                        }
                    )
                }
            }

            // Reset rápido a flat (0 dB en todas).
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { prefs.setEqualizerBands(List(5) { 0 }) }) {
                    Text("Restablecer plano")
                }
            }
        }
    }
}

@Composable
private fun BandSlider(
    label: String,
    valueMb: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    val db = valueMb / 100f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End
        )
        Spacer(Modifier.width(8.dp))
        Slider(
            value = valueMb.toFloat(),
            valueRange = -1500f..1500f,
            enabled = enabled,
            onValueChange = { onChange(it.toInt()) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (db >= 0) "+%.1f dB".format(db) else "%.1f dB".format(db),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
    }
}

// Centros estándar aproximados cubriendo graves/medios/agudos. El Equalizer
// de Android se encarga de mapear la banda i al rango real que soporta el
// device — no todos exponen 5 bandas con estos centros exactos.
private val BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
