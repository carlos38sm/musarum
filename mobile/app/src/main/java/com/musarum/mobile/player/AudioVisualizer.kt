package com.musarum.mobile.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Visualizador FFT estilo "Ares" dibujado encima de la portada. 32 barras
 * verticales que saltan con la energ&iacute;a de cada banda de frecuencia; cada
 * barra lleva un "sombrerito" (cap) que cae con delay para dar el toque
 * ret-aerobic del Winamp / Ares. Se engancha al audioSessionId expuesto por
 * el {@link com.musarum.mobile.AppContainer#audioSessionIdFlow}.
 *
 * Android requiere RECORD_AUDIO para instanciar {@link Visualizer} aunque
 * no se grabe nada: lo pedimos en runtime la primera vez. Si el usuario lo
 * deniega, el visualizador no dibuja (y la UI padre decide si mostrar
 * fallback o quitarse en silencio).
 *
 * El visualizador no pisa la portada: se dibuja con bars + caps
 * semi-transparentes sobre el gradient que tengamos debajo.
 */
@Composable
fun AudioVisualizer(
    sessionId: Int,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White,
    capColor: Color = Color.White
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Pedimos permiso solo si aún no lo tenemos y sí hay sessionId activo.
    LaunchedEffect(sessionId) {
        if (!hasPermission && sessionId > 0) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // El número de barras es constante — buffer mutable que reutilizamos
    // entre callbacks del Visualizer para no hacer allocation por frame.
    val bars = remember { FloatArray(BAR_COUNT) }
    val caps = remember { FloatArray(BAR_COUNT) }

    // Trigger recomposición: cada vez que el Visualizer entregue FFT, lo
    // guardamos en este state que fuerza a Canvas a repintar.
    var tick by remember { mutableStateOf(0) }

    DisposableEffect(sessionId, hasPermission) {
        if (!hasPermission || sessionId <= 0) {
            return@DisposableEffect onDispose { }
        }
        val visualizer = runCatching {
            Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                    .coerceAtMost(1024)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?, waveform: ByteArray?, samplingRate: Int
                        ) { /* no usamos waveform */ }

                        override fun onFftDataCapture(
                            v: Visualizer?, fft: ByteArray?, samplingRate: Int
                        ) {
                            if (fft == null) return
                            computeBars(fft, bars)
                            // caps caen con gravedad; cada tick restamos.
                            for (i in caps.indices) {
                                caps[i] = (caps[i] - CAP_GRAVITY).coerceAtLeast(bars[i])
                                if (bars[i] > caps[i]) caps[i] = bars[i]
                            }
                            tick++
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,  // waveform
                    true    // fft
                )
                enabled = true
            }
        }.getOrNull()

        onDispose {
            runCatching {
                visualizer?.enabled = false
                visualizer?.release()
            }
        }
    }

    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas
        // Fuerza la dependencia de tick para repintar en cada captura.
        @Suppress("UNUSED_VARIABLE") val t = tick
        drawBars(bars, caps, size, barColor, capColor)
    }
}

private const val BAR_COUNT = 32
private const val CAP_GRAVITY = 0.012f
private const val BAR_MIN = 0.02f

/**
 * Convierte el array FFT crudo de {@link Visualizer} (pares real/imag) en
 * magnitudes por banda, promediando los bins contiguos hasta reducir a
 * {@link #BAR_COUNT} barras. Normaliza con {@code sqrt} para que el eje Y
 * se parezca a dB y las bajas no reviente la escala.
 */
private fun computeBars(fft: ByteArray, out: FloatArray) {
    // fft[0] es DC, fft[1] es Nyquist. Saltamos DC — nunca aporta.
    val binCount = (fft.size / 2) - 1
    val perBar = binCount / out.size
    if (perBar <= 0) return
    var binIdx = 2
    val scale = 1f / 128f
    for (i in out.indices) {
        var acc = 0f
        for (k in 0 until perBar) {
            val re = fft[binIdx].toInt()
            val im = fft[binIdx + 1].toInt()
            val mag = hypot(re.toFloat(), im.toFloat()) * scale
            acc += mag
            binIdx += 2
        }
        val avg = acc / perBar
        val norm = sqrt(avg.coerceAtLeast(0f)).coerceIn(0f, 1f)
        // Smoothing: mezcla 70% anterior + 30% actual para que no parpadee.
        out[i] = (out[i] * 0.3f + norm * 0.7f).coerceAtLeast(BAR_MIN)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(
    bars: FloatArray,
    caps: FloatArray,
    canvas: Size,
    barColor: Color,
    capColor: Color
) {
    val gap = 2.dp.toPx()
    val totalGap = gap * (bars.size + 1)
    val barWidth = ((canvas.width - totalGap) / bars.size).coerceAtLeast(2f)
    val maxBarHeight = canvas.height
    val capHeight = 3.dp.toPx()
    var x = gap
    for (i in bars.indices) {
        val h = min(maxBarHeight, bars[i] * maxBarHeight)
        val top = canvas.height - h
        drawRect(
            color = barColor.copy(alpha = 0.72f),
            topLeft = Offset(x, top),
            size = Size(barWidth, h)
        )
        val capY = canvas.height - (caps[i] * maxBarHeight) - capHeight
        drawRect(
            color = capColor.copy(alpha = 0.95f),
            topLeft = Offset(x, capY.coerceAtLeast(0f)),
            size = Size(barWidth, capHeight)
        )
        x += barWidth + gap
    }
}
