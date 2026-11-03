package emendatium.backend.analysis;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Detecta la tonalidad correlando el chroma del audio con los perfiles de
 * Krumhansl-Schmuckler (24 tonalidades). Solo devuelve valor si la mejor
 * correlación supera un mínimo; si el audio es ambiguo/atonal, devuelve null.
 */
@Component
public class KeyDetector {

    private static final String[] NOTES = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
    private static final double[] PROFILE_MAJOR =
            {6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88};
    private static final double[] PROFILE_MINOR =
            {6.33,2.68,3.52,5.38,2.60,3.53,2.54,4.75,3.98,2.69,3.34,3.17};

    private static final int FRAME = 8192;
    private static final int HOP = 4096;
    private static final double CONFIDENCE_MIN = 0.55; // correlación mínima para fiarnos

    public String detect(float[] samples, int sampleRate) {
        double[] chroma = new double[12];
        double[] hanning = new double[FRAME];
        for (int i = 0; i < FRAME; i++) hanning[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (FRAME - 1)));

        int maxSamples = Math.min(samples.length, sampleRate * 60);
        int frames = 0;
        for (int start = 0; start + FRAME <= maxSamples; start += HOP) {
            double[] re = new double[FRAME], im = new double[FRAME];
            for (int j = 0; j < FRAME; j++) re[j] = samples[start + j] * hanning[j];
            Fft.transform(re, im);
            for (int bin = 1; bin < FRAME / 2; bin++) {
                double freq = (double) bin * sampleRate / FRAME;
                if (freq < 65.41 || freq > 4186.0) continue;
                double power = re[bin] * re[bin] + im[bin] * im[bin];
                if (power < 1e-10) continue;
                int cls = ((int) Math.round(12.0 * Math.log(freq / 440.0) / Math.log(2.0) + 69.0) % 12 + 12) % 12;
                chroma[cls] += power;
            }
            frames++;
        }
        if (frames == 0) return null;

        double sum = Arrays.stream(chroma).sum();
        if (sum == 0) return null;
        for (int i = 0; i < 12; i++) chroma[i] /= sum;

        double best = Double.NEGATIVE_INFINITY;
        int bestTonic = 0;
        boolean bestMajor = true;
        for (int t = 0; t < 12; t++) {
            double corrMajor = pearson(chroma, rotate(PROFILE_MAJOR, t));
            double corrMinor = pearson(chroma, rotate(PROFILE_MINOR, t));
            if (corrMajor > best) { best = corrMajor; bestTonic = t; bestMajor = true; }
            if (corrMinor > best) { best = corrMinor; bestTonic = t; bestMajor = false; }
        }

        if (best < CONFIDENCE_MIN) return null; // ambiguo → mejor vacío

        return NOTES[bestTonic] + (bestMajor ? " major" : " minor");
    }

    private double pearson(double[] a, double[] b) {
        double ma = Arrays.stream(a).average().orElse(0);
        double mb = Arrays.stream(b).average().orElse(0);
        double num = 0, da = 0, db = 0;
        for (int i = 0; i < a.length; i++) {
            num += (a[i] - ma) * (b[i] - mb);
            da += (a[i] - ma) * (a[i] - ma);
            db += (b[i] - mb) * (b[i] - mb);
        }
        double d = Math.sqrt(da * db);
        return d == 0 ? 0 : num / d;
    }

    private double[] rotate(double[] p, int n) {
        double[] r = new double[12];
        for (int i = 0; i < 12; i++) r[(i + n) % 12] = p[i];
        return r;
    }
}