package emendatium.backend.analysis;

import org.springframework.stereotype.Component;

/**
 * Detecta BPM por flujo espectral + autocorrelación, buscando en el rango
 * musical 70..180. Solo devuelve valor si la periodicidad destaca con claridad;
 * si hay duda, devuelve null (mejor un vacío que un BPM inventado). Es respaldo
 * de Deezer.
 */
@Component
public class BpmDetector {

    private static final int FRAME = 2048;
    private static final int HOP = 512;
    private static final int SEARCH_BPM_MIN = 70;
    private static final int SEARCH_BPM_MAX = 180;
    private static final double CONFIDENCE_MIN = 2.2; // pico/media mínimo para fiarnos

    public Integer detect(float[] samples, int sampleRate) {
        int nFrames = (samples.length - FRAME) / HOP;
        if (nFrames < 40) return null;

        double[] hann = new double[FRAME];
        for (int i = 0; i < FRAME; i++) hann[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (FRAME - 1)));

        int analyzed = Math.min(nFrames, (int) (40.0 * sampleRate / HOP)); // primeros ~40 s
        double[] prevMag = new double[FRAME / 2];
        double[] flux = new double[analyzed];
        double[] re = new double[FRAME], im = new double[FRAME];

        // 1) Flujo espectral: suma de incrementos de magnitud entre frames (onsets)
        for (int f = 0; f < analyzed; f++) {
            int start = f * HOP;
            for (int j = 0; j < FRAME; j++) { re[j] = samples[start + j] * hann[j]; im[j] = 0; }
            Fft.transform(re, im);
            double sum = 0;
            for (int bin = 0; bin < FRAME / 2; bin++) {
                double mag = Math.sqrt(re[bin] * re[bin] + im[bin] * im[bin]);
                double diff = mag - prevMag[bin];
                if (diff > 0) sum += diff;
                prevMag[bin] = mag;
            }
            flux[f] = sum;
        }

        // 2) Centrar la envolvente (restar media) para autocorrelación limpia
        double mean = 0;
        for (double v : flux) mean += v;
        mean /= analyzed;
        for (int i = 0; i < analyzed; i++) flux[i] = Math.max(0, flux[i] - mean);

        // 3) Autocorrelación en los lags del rango 70..180 BPM
        double fps = (double) sampleRate / HOP;
        int lagMin = (int) Math.floor(fps * 60.0 / SEARCH_BPM_MAX);
        int lagMax = (int) Math.ceil(fps * 60.0 / SEARCH_BPM_MIN);
        lagMax = Math.min(lagMax, analyzed - 1);
        if (lagMin < 1 || lagMax <= lagMin) return null;

        double bestCorr = -1, sumCorr = 0;
        int bestLag = -1, count = 0;
        for (int lag = lagMin; lag <= lagMax; lag++) {
            double corr = 0;
            for (int i = 0; i < analyzed - lag; i++) corr += flux[i] * flux[i + lag];
            corr /= (analyzed - lag); // normalizado por nº de términos
            sumCorr += corr;
            count++;
            if (corr > bestCorr) { bestCorr = corr; bestLag = lag; }
        }
        if (bestLag < 1 || count == 0) return null;

        // 4) Gate de confianza: el pico debe destacar sobre la media
        double meanCorr = sumCorr / count;
        if (meanCorr <= 0 || bestCorr / meanCorr < CONFIDENCE_MIN) {
            return null;
        }

        return (int) Math.round(fps * 60.0 / bestLag);
    }
}